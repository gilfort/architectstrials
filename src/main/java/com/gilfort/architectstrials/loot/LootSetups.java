package com.gilfort.architectstrials.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Access to the loot setups of every loot source (US-25).
 * <ul>
 * <li>{@link LootSetupHolder}s (Exit Marker, Trial Spawner Marker, …) keep their setups themselves.</li>
 * <li>Vanilla lootable containers keep theirs in the persistent block entity data; their vanilla loot table is
 * set to {@link #PLACEHOLDER}, whose entry ({@link LootSetupEntry}) rolls the setup. So vanilla still decides when
 * loot is rolled (first opening, with the player's luck, hoppers, breaking) and keeps hand-placed items.</li>
 * <li>Placed trial spawners keep the setups of their marker in the persistent block entity data and eject them
 * instead of {@link #PLACEHOLDER} (see {@link #ejectTrialReward}).</li>
 * </ul>
 */
public final class LootSetups {

    /** Loot table that rolls the setup stored at the loot origin. */
    public static final ResourceKey<LootTable> PLACEHOLDER = ResourceKey.create(Registries.LOOT_TABLE, ArchitectsTrials.id("loot_setup"));

    /** Key of the normal setup in persistent block entity data. */
    public static final String SETUP_KEY = ArchitectsTrials.MOD_ID + ":loot_setup";

    /** Key of the ominous setup in persistent block entity data. */
    public static final String OMINOUS_SETUP_KEY = ArchitectsTrials.MOD_ID + ":ominous_loot_setup";

    private LootSetups() {
    }

    /**
     * Checks whether a block entity can hold a loot setup.
     *
     * @param blockEntity the block entity
     * @return {@code true} for setup holders and lootable containers
     */
    public static boolean supports(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof LootSetupHolder || blockEntity instanceof RandomizableContainer;
    }

    /**
     * Reads the setup of a loot source.
     *
     * @param blockEntity the loot source
     * @param ominous     {@code true} for the ominous variant
     * @return the setup (possibly empty)
     */
    public static LootSetup get(BlockEntity blockEntity, boolean ominous) {
        if (blockEntity instanceof LootSetupHolder holder) {
            return holder.lootSetup(ominous);
        }
        return read(blockEntity, ominous ? OMINOUS_SETUP_KEY : SETUP_KEY);
    }

    /**
     * Stores the setup of a loot source. Lootable containers get the {@link #PLACEHOLDER} loot table while they
     * have a setup.
     *
     * @param blockEntity the loot source
     * @param ominous     {@code true} for the ominous variant
     * @param setup       the setup
     */
    public static void set(BlockEntity blockEntity, boolean ominous, LootSetup setup) {
        if (blockEntity instanceof LootSetupHolder holder) {
            holder.setLootSetup(ominous, setup);
            return;
        }
        write(blockEntity, ominous ? OMINOUS_SETUP_KEY : SETUP_KEY, setup);
        if (blockEntity instanceof RandomizableContainer container && !ominous) {
            if (!setup.isEmpty()) {
                container.setLootTable(PLACEHOLDER, 0L);
            } else if (PLACEHOLDER.equals(container.getLootTable())) {
                container.setLootTable(null);
            }
        }
        blockEntity.setChanged();
    }

    /**
     * Reads a setup from persistent block entity data.
     *
     * @param blockEntity the block entity
     * @param key         the key
     * @return the setup (possibly empty)
     */
    public static LootSetup read(BlockEntity blockEntity, String key) {
        CompoundTag data = blockEntity.getPersistentData();
        if (!data.contains(key) || blockEntity.getLevel() == null) {
            return LootSetup.EMPTY;
        }
        return data.read(key, LootSetup.CODEC, blockEntity.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE))
                .orElse(LootSetup.EMPTY);
    }

    /**
     * Writes a setup into persistent block entity data (an empty setup removes it).
     *
     * @param blockEntity the block entity
     * @param key         the key
     * @param setup       the setup
     */
    public static void write(BlockEntity blockEntity, String key, LootSetup setup) {
        CompoundTag data = blockEntity.getPersistentData();
        if (setup.isEmpty() || blockEntity.getLevel() == null) {
            data.remove(key);
        } else {
            data.store(key, LootSetup.CODEC, blockEntity.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), setup);
        }
        blockEntity.setChanged();
    }

    /**
     * Rolls a setup with a player as loot context (luck applies to the loot tables), e.g. for the completion
     * bonus.
     *
     * @param level  the level
     * @param setup  the setup
     * @param origin the loot origin
     * @param player the player, or {@code null}
     * @return the items
     */
    public static List<ItemStack> roll(ServerLevel level, LootSetup setup, Vec3 origin, @Nullable ServerPlayer player) {
        LootParams.Builder builder = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin);
        if (player != null) {
            builder.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
        }
        LootParams params = builder.create(LootContextParamSets.CHEST);
        List<ItemStack> items = new ArrayList<>();
        setup.roll(level.getRandom(), (key, output) -> {
            if (!PLACEHOLDER.equals(key)) {
                level.getServer().reloadableRegistries().getLootTable(key).getRandomItems(params, output);
            }
        }, items::add);
        return items;
    }

    /**
     * Ejects the reward of a trial spawner created from a marker with a loot setup, like vanilla ejects a loot
     * table: items pop out of the top, with the vanilla sound. Called instead of the vanilla reward when the
     * spawner ejects {@link #PLACEHOLDER}.
     *
     * @param level   the level
     * @param pos     the trial spawner position
     * @param ominous {@code true} if the spawner is ominous
     */
    public static void ejectTrialReward(ServerLevel level, BlockPos pos, boolean ominous) {
        if (!(level.getBlockEntity(pos) instanceof TrialSpawnerBlockEntity spawner)) {
            return;
        }
        LootSetup setup = Optional.of(read(spawner, OMINOUS_SETUP_KEY)).filter(s -> ominous && !s.isEmpty())
                .orElseGet(() -> read(spawner, SETUP_KEY));
        List<ItemStack> items = roll(level, setup, Vec3.atCenterOf(pos), null);
        if (items.isEmpty()) {
            return;
        }
        for (ItemStack item : items) {
            DefaultDispenseItemBehavior.spawnItem(level, item, 2, Direction.UP, Vec3.atBottomCenterOf(pos).relative(Direction.UP, 1.2));
        }
        level.levelEvent(3014, pos, 0);
    }
}
