package com.gilfort.architectstrials.block;

import java.util.Optional;

import com.gilfort.architectstrials.loot.LootSetup;
import com.gilfort.architectstrials.loot.LootSetupHolder;
import com.gilfort.architectstrials.menu.ExitMarkerMenu;
import com.gilfort.architectstrials.registry.ModBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.model.data.ModelData;

/**
 * Block entity of the {@link ExitMarkerBlock}: stores an optional completion bonus loot table that overrides
 * the theme/tier convention, the "requires required mobs" setting (US-30) and an optional camouflage (US-34).
 * Saved with the structure and handed to the functional exit on placement.
 */
public class ExitMarkerBlockEntity extends BlockEntity implements LootTableReference, LootSetupHolder, ExitCamouflage.Holder, MenuProvider {

    private static final String SETUP_TAG = "loot_setup";
    private static final String REQUIRES_MOBS_TAG = "requires_mobs";

    private Optional<ResourceKey<LootTable>> lootTable = Optional.empty();
    private LootSetup lootSetup = LootSetup.EMPTY;
    private boolean requiresMobs;
    private Optional<BlockState> camouflage = Optional.empty();

    /**
     * Creates the block entity.
     *
     * @param pos   the position
     * @param state the block state
     */
    public ExitMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.EXIT_MARKER.get(), pos, state);
    }

    @Override
    public Optional<ResourceKey<LootTable>> lootTableReference() {
        return this.lootTable;
    }

    @Override
    public void setLootTableReference(Optional<ResourceKey<LootTable>> lootTable) {
        this.lootTable = lootTable;
        this.setChanged();
    }

    @Override
    public LootSetup lootSetup(boolean ominous) {
        return this.lootSetup;
    }

    @Override
    public void setLootSetup(boolean ominous, LootSetup setup) {
        this.lootSetup = setup;
        this.setChanged();
    }

    /** @return {@code true} if this exit stays sealed until all required mobs of the instance are defeated (US-30) */
    public boolean requiresMobs() {
        return this.requiresMobs;
    }

    /**
     * Sets whether this exit stays sealed until all required mobs of the instance are defeated.
     *
     * @param requiresMobs the setting
     */
    public void setRequiresMobs(boolean requiresMobs) {
        this.requiresMobs = requiresMobs;
        this.setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(this.getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ExitMarkerMenu(containerId, ContainerLevelAccess.create(this.level, this.worldPosition), new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case ExitMarkerMenu.DATA_REQUIRES_MOBS -> ExitMarkerBlockEntity.this.requiresMobs ? 1 : 0;
                    default -> ExitMarkerMenu.camouflagePart(ExitMarkerBlockEntity.this.camouflage, index);
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == ExitMarkerMenu.DATA_REQUIRES_MOBS) {
                    ExitMarkerBlockEntity.this.setRequiresMobs(value != 0);
                }
            }

            @Override
            public int getCount() {
                return ExitMarkerMenu.DATA_COUNT;
            }
        });
    }

    @Override
    public Optional<BlockState> camouflage() {
        return this.camouflage;
    }

    @Override
    public void setCamouflage(Optional<BlockState> camouflage) {
        this.camouflage = camouflage;
        ExitCamouflage.changed(this, camouflage.isPresent());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return ExitCamouflage.syncTag(this.camouflage);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public ModelData getModelData() {
        return this.camouflage.map(state -> ModelData.of(ExitCamouflage.MODEL_PROPERTY, state)).orElse(ModelData.EMPTY);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.camouflage = ExitCamouflage.read(input);
        this.requiresMobs = input.getBooleanOr(REQUIRES_MOBS_TAG, false);
        this.lootTable = LootTableReference.read(input);
        this.lootSetup = input.read(SETUP_TAG, LootSetup.CODEC).orElse(LootSetup.EMPTY);
        ExitCamouflage.loaded(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        LootTableReference.write(output, this.lootTable);
        ExitCamouflage.write(output, this.camouflage);
        if (this.requiresMobs) {
            output.putBoolean(REQUIRES_MOBS_TAG, true);
        }
        if (!this.lootSetup.isEmpty()) {
            output.store(SETUP_TAG, LootSetup.CODEC, this.lootSetup);
        }
    }
}
