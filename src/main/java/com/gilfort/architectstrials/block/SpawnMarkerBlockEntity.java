package com.gilfort.architectstrials.block;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.gilfort.architectstrials.menu.SpawnMarkerMenu;
import com.gilfort.architectstrials.registry.ModBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Block entity shared by the {@link SpawnMarkerBlock}s: a small container holding one spawn egg stack (entity
 * type and count) and six equipment items. Saved with the structure and read by the marker resolvers.
 */
public class SpawnMarkerBlockEntity extends BaseContainerBlockEntity {

    /** Container index of the spawn egg slot. */
    public static final int EGG_SLOT = 0;

    /** Equipment slots in container order, starting at index 1. */
    public static final List<EquipmentSlot> EQUIPMENT_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);

    /** Total number of container slots. */
    public static final int SIZE = 1 + EQUIPMENT_SLOTS.size();

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    /**
     * Creates the block entity.
     *
     * @param pos   the position
     * @param state the block state
     */
    public SpawnMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SPAWN_MARKER.get(), pos, state);
    }

    /**
     * Returns the container index of an equipment slot.
     *
     * @param slot the equipment slot
     * @return the container index
     */
    public static int indexOf(EquipmentSlot slot) {
        return 1 + EQUIPMENT_SLOTS.indexOf(slot);
    }

    /** @return the spawn egg stack (possibly empty) */
    public ItemStack egg() {
        return this.items.get(EGG_SLOT);
    }

    /**
     * Returns the entity type of the spawn egg.
     *
     * @return the entity type, or {@code null} if the slot holds no spawn egg
     */
    public @Nullable EntityType<?> entityType() {
        ItemStack egg = this.egg();
        return egg.getItem() instanceof SpawnEggItem ? SpawnEggItem.getType(egg) : null;
    }

    /** @return copies of the non-empty equipment items by slot */
    public Map<EquipmentSlot, ItemStack> equipment() {
        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
            ItemStack stack = this.items.get(indexOf(slot));
            if (!stack.isEmpty()) {
                equipment.put(slot, stack.copy());
            }
        }
        return equipment;
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
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new SpawnMarkerMenu(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return SpawnMarkerMenu.accepts(slot, stack);
    }

    /**
     * Markers are editor tools filled from the creative inventory: their contents are never dropped when the
     * marker is broken or resolved.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
    }
}
