package com.gilfort.architectstrials.block;

import java.util.EnumMap;
import java.util.Map;

import com.gilfort.architectstrials.menu.MarkerSlot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Base block entity of the mob markers: a container of {@link MarkerSlot marker rows}, each holding one spawn egg
 * stack (entity type and count) and six equipment items. Saved with the structure and read by the marker
 * resolvers.
 */
public abstract class MobMarkerBlockEntity extends BaseContainerBlockEntity {

    private final int rows;
    private NonNullList<ItemStack> items;

    /**
     * Creates the block entity.
     *
     * @param type  the block entity type
     * @param pos   the position
     * @param state the block state
     * @param rows  the number of marker rows
     */
    protected MobMarkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int rows) {
        super(type, pos, state);
        this.rows = rows;
        this.items = NonNullList.withSize(rows * MarkerSlot.ROW_SIZE, ItemStack.EMPTY);
    }

    /** @return the number of marker rows */
    public int rows() {
        return this.rows;
    }

    /**
     * Returns the spawn egg stack of a row.
     *
     * @param row the row
     * @return the stack (possibly empty)
     */
    public ItemStack egg(int row) {
        return this.items.get(row * MarkerSlot.ROW_SIZE);
    }

    /**
     * Returns the entity type of a row's spawn egg.
     *
     * @param row the row
     * @return the entity type, or {@code null} if the row holds no spawn egg
     */
    public @Nullable EntityType<?> entityType(int row) {
        ItemStack egg = this.egg(row);
        return egg.getItem() instanceof SpawnEggItem ? SpawnEggItem.getType(egg) : null;
    }

    /**
     * Returns the equipment of a row.
     *
     * @param row the row
     * @return copies of the non-empty equipment items by slot
     */
    public Map<EquipmentSlot, ItemStack> equipment(int row) {
        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : MarkerSlot.EQUIPMENT_SLOTS) {
            ItemStack stack = this.items.get(MarkerSlot.indexOf(row, slot));
            if (!stack.isEmpty()) {
                equipment.put(slot, stack.copy());
            }
        }
        return equipment;
    }

    /** @return {@code true} if at least one row holds a spawn egg */
    public boolean hasAnyEgg() {
        for (int row = 0; row < this.rows; row++) {
            if (this.entityType(row) != null) {
                return true;
            }
        }
        return false;
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
        return this.items.size();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return MarkerSlot.accepts(slot, stack);
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
        this.items = NonNullList.withSize(this.rows * MarkerSlot.ROW_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
    }
}
