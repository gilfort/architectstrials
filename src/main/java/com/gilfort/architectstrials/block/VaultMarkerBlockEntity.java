package com.gilfort.architectstrials.block;

import java.util.Optional;

import com.gilfort.architectstrials.loot.LootSetup;
import com.gilfort.architectstrials.loot.LootSetupHolder;
import com.gilfort.architectstrials.menu.VaultMarkerMenu;
import com.gilfort.architectstrials.registry.ModBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Block entity of the {@link VaultMarkerBlock} (US-26): the key slot (any item; count and components are part of
 * the key), the variant (normal / ominous) and the reward (loot setup, or a loot table as fallback). Saved with the
 * structure.
 */
public class VaultMarkerBlockEntity extends BaseContainerBlockEntity implements LootSetupHolder, LootTableReference {

    /** Container index of the key slot. */
    public static final int KEY_SLOT = 0;

    /** Number of menu data values (the variant). */
    public static final int DATA_COUNT = 1;

    private static final String OMINOUS_TAG = "ominous";
    private static final String SETUP_TAG = "loot_setup";

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private boolean ominous;
    private LootSetup lootSetup = LootSetup.EMPTY;
    private Optional<ResourceKey<LootTable>> lootTable = Optional.empty();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return VaultMarkerBlockEntity.this.ominous ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {
            VaultMarkerBlockEntity.this.setOminous(value != 0);
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    /**
     * Creates the block entity.
     *
     * @param pos   the position
     * @param state the block state
     */
    public VaultMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.VAULT_MARKER.get(), pos, state);
    }

    /** @return {@code true} for an ominous vault */
    public boolean ominous() {
        return this.ominous;
    }

    /**
     * Sets the variant.
     *
     * @param ominous {@code true} for an ominous vault
     */
    public void setOminous(boolean ominous) {
        this.ominous = ominous;
        this.setChanged();
    }

    /** @return a copy of the key, or an empty stack for the vanilla key of the variant */
    public ItemStack key() {
        return this.items.get(KEY_SLOT).copy();
    }

    /**
     * Sets the key.
     *
     * @param key the key (empty for the vanilla key of the variant)
     */
    public void setKey(ItemStack key) {
        this.items.set(KEY_SLOT, key.copy());
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
    protected Component getDefaultName() {
        return Component.translatable(this.getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    /**
     * The key is an editor setting filled from the creative inventory: it is never dropped when the marker is
     * broken or resolved.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new VaultMarkerMenu(containerId, inventory, this, this.data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.ominous = input.getBooleanOr(OMINOUS_TAG, false);
        this.lootSetup = input.read(SETUP_TAG, LootSetup.CODEC).orElse(LootSetup.EMPTY);
        this.lootTable = LootTableReference.read(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        if (this.ominous) {
            output.putBoolean(OMINOUS_TAG, true);
        }
        if (!this.lootSetup.isEmpty()) {
            output.store(SETUP_TAG, LootSetup.CODEC, this.lootSetup);
        }
        LootTableReference.write(output, this.lootTable);
    }
}
