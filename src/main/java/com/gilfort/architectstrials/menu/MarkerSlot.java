package com.gilfort.architectstrials.menu;

import java.util.List;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.Equippable;

/**
 * A slot of a mob marker row. Every row consists of {@link #ROW_SIZE} container slots: one spawn egg slot
 * (entity type; the stack size is the count) followed by one slot per {@link #EQUIPMENT_SLOTS equipment slot}.
 * Equipment slots hold a single item; armor slots only accept items equippable in that slot.
 */
public class MarkerSlot extends Slot {

    /** Sprite shown in an empty spawn egg slot. */
    public static final Identifier EMPTY_SPAWN_EGG_SLOT = ArchitectsTrials.id("container/slot/spawn_egg");

    /** Equipment slots of a row in container order, following the spawn egg slot. */
    public static final List<EquipmentSlot> EQUIPMENT_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);

    /** Number of container slots per row. */
    public static final int ROW_SIZE = 1 + EQUIPMENT_SLOTS.size();

    private static final Identifier[] EMPTY_EQUIPMENT_SLOTS = {
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, Identifier.withDefaultNamespace("container/slot/sword"),
            InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD};

    /**
     * Creates a slot.
     *
     * @param container the marker container
     * @param index     the container index
     * @param x         the x position in the screen
     * @param y         the y position in the screen
     */
    public MarkerSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    /**
     * Returns the container index of an equipment slot within a row.
     *
     * @param row  the row
     * @param slot the equipment slot
     * @return the container index
     */
    public static int indexOf(int row, EquipmentSlot slot) {
        return row * ROW_SIZE + 1 + EQUIPMENT_SLOTS.indexOf(slot);
    }

    /**
     * Checks whether an item may be put into a marker container slot: spawn egg slots take spawn eggs only, armor
     * slots take items equippable in that slot, hand slots take anything.
     *
     * @param index the container index
     * @param stack the item
     * @return {@code true} if the item fits
     */
    public static boolean accepts(int index, ItemStack stack) {
        int column = index % ROW_SIZE;
        if (column == 0) {
            return stack.getItem() instanceof SpawnEggItem && SpawnEggItem.getType(stack) != null;
        }
        EquipmentSlot slot = EQUIPMENT_SLOTS.get(column - 1);
        if (slot.getType() == EquipmentSlot.Type.HAND) {
            return true;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == slot;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return accepts(this.getContainerSlot(), stack);
    }

    @Override
    public int getMaxStackSize() {
        return this.getContainerSlot() % ROW_SIZE == 0 ? super.getMaxStackSize() : 1;
    }

    @Override
    public Identifier getNoItemIcon() {
        int column = this.getContainerSlot() % ROW_SIZE;
        return column == 0 ? EMPTY_SPAWN_EGG_SLOT : EMPTY_EQUIPMENT_SLOTS[column - 1];
    }
}
