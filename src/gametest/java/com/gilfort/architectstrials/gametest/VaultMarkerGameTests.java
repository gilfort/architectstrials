package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.VaultKeyHint;
import com.gilfort.architectstrials.block.VaultMarkerBlock;
import com.gilfort.architectstrials.block.VaultMarkerBlockEntity;
import com.gilfort.architectstrials.editor.WorldImport;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceRoster;
import com.gilfort.architectstrials.loot.LootEntry;
import com.gilfort.architectstrials.loot.LootGroup;
import com.gilfort.architectstrials.loot.LootSetup;
import com.gilfort.architectstrials.loot.LootSetups;
import com.gilfort.architectstrials.marker.MarkerContext;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollOptions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-26 (Vault Marker).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class VaultMarkerGameTests {

    private VaultMarkerGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "vault_marker_resolves", VaultMarkerGameTests::resolves);
            register(helper, "vault_marker_default_key_and_hint", VaultMarkerGameTests::defaultKeyAndHint);
            register(helper, "vault_marker_world_import", VaultMarkerGameTests::worldImport);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * An ominous marker with a custom key and a loot setup becomes an ominous vault with that key; its reward rolls
     * the setup; the key unlocks once per player.
     */
    private static void resolves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, ModBlocks.VAULT_MARKER.get().defaultBlockState().setValue(VaultMarkerBlock.FACING, Direction.EAST), 3);
        VaultMarkerBlockEntity marker = (VaultMarkerBlockEntity) level.getBlockEntity(pos);
        ItemStack key = new ItemStack(Items.DIAMOND, 2);
        key.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Crypt Key"));
        marker.setKey(key);
        marker.setOminous(true);
        marker.setLootSetup(false, new LootSetup(List.of(new LootGroup(List.of(
                new LootEntry(0, Optional.empty(), new ItemStack(Items.EMERALD, 3), LootGroup.TOTAL, 1, 1)))), LootGroup.EMPTY));

        VaultMarkerBlock.resolve(context(level), pos);
        BlockState state = level.getBlockState(pos);
        helper.assertTrue(state.is(Blocks.VAULT) && state.getValue(VaultBlock.OMINOUS) && state.getValue(VaultBlock.FACING) == Direction.EAST,
                "Marker did not become an ominous vault facing east: " + state);
        VaultBlockEntity vault = (VaultBlockEntity) level.getBlockEntity(pos);
        VaultConfig config = vault.getConfig();
        helper.assertTrue(ItemStack.isSameItemAndComponents(config.keyItem(), key) && config.keyItem().getCount() == 2, "Key not taken over: " + config.keyItem());
        helper.assertTrue(LootSetups.PLACEHOLDER.equals(config.lootTable()), "Vault does not roll the loot setup");

        List<ItemStack> rolled = level.getServer().reloadableRegistries().getLootTable(config.lootTable()).getRandomItems(
                new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).create(LootContextParamSets.CHEST));
        helper.assertTrue(rolled.size() == 1 && rolled.getFirst().is(Items.EMERALD) && rolled.getFirst().getCount() == 3,
                "Vault reward does not come from the loot setup: " + rolled);

        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        level.setBlock(pos, state.setValue(VaultBlock.STATE, VaultState.ACTIVE), 3);
        ItemStack held = key.copyWithCount(3);
        BlockState active = level.getBlockState(pos);
        VaultBlockEntity.Server.tryInsertKey(level, pos, active, vault.getConfig(), vault.getServerData(), vault.getSharedData(), player, held);
        helper.assertTrue(held.getCount() == 1, "The key was not consumed with its count: " + held.getCount());
        level.setBlock(pos, active, 3);
        ItemStack again = key.copyWithCount(2);
        VaultBlockEntity.Server.tryInsertKey(level, pos, level.getBlockState(pos), vault.getConfig(), vault.getServerData(), vault.getSharedData(), player, again);
        helper.assertTrue(again.getCount() == 2, "The same player could unlock the vault twice");
        TestPlayers.finish(helper, player);
    }

    /**
     * Without a key the vault opens with the vanilla key of its variant; without a setup it uses the vanilla reward.
     * The hint names count and key.
     */
    private static void defaultKeyAndHint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos normalPos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos ominousPos = helper.absolutePos(new BlockPos(3, 1, 1));
        level.setBlock(normalPos, ModBlocks.VAULT_MARKER.get().defaultBlockState(), 3);
        level.setBlock(ominousPos, ModBlocks.VAULT_MARKER.get().defaultBlockState(), 3);
        ((VaultMarkerBlockEntity) level.getBlockEntity(ominousPos)).setOminous(true);
        VaultMarkerBlock.resolve(context(level), normalPos);
        VaultMarkerBlock.resolve(context(level), ominousPos);

        VaultConfig normal = ((VaultBlockEntity) level.getBlockEntity(normalPos)).getConfig();
        VaultConfig ominous = ((VaultBlockEntity) level.getBlockEntity(ominousPos)).getConfig();
        helper.assertTrue(normal.keyItem().is(Items.TRIAL_KEY) && BuiltInLootTables.TRIAL_CHAMBERS_REWARD.equals(normal.lootTable()),
                "Normal vault does not use the trial key and the trial chamber reward");
        helper.assertTrue(ominous.keyItem().is(Items.OMINOUS_TRIAL_KEY) && BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS.equals(ominous.lootTable()),
                "Ominous vault does not use the ominous trial key and reward");

        ItemStack key = new ItemStack(Items.GOLD_INGOT, 2);
        helper.assertFalse(VaultKeyHint.opens(key, ItemStack.EMPTY), "An empty hand opens the vault");
        helper.assertFalse(VaultKeyHint.opens(key, new ItemStack(Items.GOLD_INGOT, 1)), "Too few keys open the vault");
        helper.assertFalse(VaultKeyHint.opens(key, new ItemStack(Items.IRON_INGOT, 2)), "A wrong item opens the vault");
        helper.assertTrue(VaultKeyHint.opens(key, new ItemStack(Items.GOLD_INGOT, 5)), "The right key does not open the vault");
        String hint = VaultKeyHint.hint(key).getString();
        helper.assertTrue(hint.contains("2") && hint.contains(new ItemStack(Items.GOLD_INGOT).getHoverName().getString()), "Hint does not name the key: " + hint);
        helper.succeed();
    }

    /**
     * Importing a world area converts a vanilla vault into a Vault Marker with variant, key and loot table.
     */
    private static void worldImport(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(pos, Blocks.VAULT.defaultBlockState().setValue(VaultBlock.OMINOUS, true).setValue(VaultBlock.FACING, Direction.SOUTH), 3);
        VaultBlockEntity vault = (VaultBlockEntity) level.getBlockEntity(pos);
        VaultConfig defaults = VaultConfig.DEFAULT;
        vault.setConfig(new VaultConfig(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_RARE, defaults.activationRange(), defaults.deactivationRange(),
                new ItemStack(Items.EMERALD), Optional.empty()));

        ServerLevel target = level.getServer().getLevel(Level.END);
        WorldImport.Result result = WorldImport.importInto(level, new BoundingBox(pos), target, 4000, 0);
        BlockPos placed = new BlockPos(result.placed().minX(), result.placed().minY(), result.placed().minZ());
        helper.assertTrue(result.vaults() == 1, "Vault was not counted: " + result);
        helper.assertTrue(target.getBlockEntity(placed) instanceof VaultMarkerBlockEntity marker && marker.ominous()
                && marker.key().is(Items.EMERALD) && Optional.of(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_RARE).equals(marker.lootTableReference())
                && target.getBlockState(placed).getValue(VaultMarkerBlock.FACING) == Direction.SOUTH,
                "Vault was not converted with variant, key, loot table and facing");
        target.setBlock(placed, Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
    }

    private static MarkerContext context(ServerLevel level) {
        ChallengeInstance dummy = new ChallengeInstance(UUID.randomUUID(), Level.NETHER.identifier(), 1, ArchitectsTrials.id("dummy"), 0,
                BlockPos.ZERO, Rotation.NONE, Mirror.NONE, List.of(), List.of(), 0L, 0L, -1L, ScrollOptions.DEFAULT, ScrollEffects.NONE,
                InstanceRoster.EMPTY);
        return new MarkerContext(level, dummy, level.getRandom());
    }
}
