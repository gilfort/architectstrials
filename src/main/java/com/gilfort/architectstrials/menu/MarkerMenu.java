package com.gilfort.architectstrials.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Base menu of the mob markers: a marker container made of {@link MarkerSlot marker rows} followed by the
 * player inventory, with shift-click moving between both.
 */
public abstract class MarkerMenu extends AbstractContainerMenu {

    private final Container container;

    /**
     * Creates the menu and adds one marker row per {@code rowYs} entry: the spawn egg slot at {@code eggX}, the
     * six equipment slots at {@code equipmentXs}, {@code equipmentDy} below the egg slot.
     *
     * @param type        the menu type
     * @param containerId the container id
     * @param inventory   the player inventory
     * @param container   the marker container
     * @param eggX        x position of the spawn egg slots
     * @param equipmentXs x positions of the six equipment slots
     * @param equipmentDy vertical offset of the equipment slots relative to the egg slot
     * @param rowYs       y positions of the rows
     * @param inventoryY  y position of the player inventory
     */
    protected MarkerMenu(MenuType<?> type, int containerId, Inventory inventory, Container container, int eggX, int[] equipmentXs,
            int equipmentDy, int[] rowYs, int inventoryY) {
        super(type, containerId);
        checkContainerSize(container, rowYs.length * MarkerSlot.ROW_SIZE);
        this.container = container;
        container.startOpen(inventory.player);
        for (int row = 0; row < rowYs.length; row++) {
            int base = row * MarkerSlot.ROW_SIZE;
            this.addSlot(this.createSlot(container, base, eggX, rowYs[row]));
            for (int i = 0; i < MarkerSlot.EQUIPMENT_SLOTS.size(); i++) {
                this.addSlot(this.createSlot(container, base + 1 + i, equipmentXs[i], rowYs[row] + equipmentDy));
            }
        }
        this.addStandardInventorySlots(inventory, 8, inventoryY);
    }

    /**
     * Creates a marker slot. Called from the constructor, before subclass fields are initialized.
     *
     * @param container the marker container
     * @param index     the container index
     * @param x         the x position in the screen
     * @param y         the y position in the screen
     * @return the slot
     */
    protected MarkerSlot createSlot(Container container, int index, int x, int y) {
        return new MarkerSlot(container, index, x, y);
    }

    /** @return the marker container */
    protected Container container() {
        return this.container;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int size = this.container.getContainerSize();
        boolean moved = slotIndex < size
                ? this.moveItemStackTo(stack, size, this.slots.size(), true)
                : this.moveItemStackTo(stack, 0, size, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
