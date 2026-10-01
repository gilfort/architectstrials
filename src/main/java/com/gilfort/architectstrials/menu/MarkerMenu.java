package com.gilfort.architectstrials.menu;

import com.gilfort.architectstrials.block.MobMarkerBlockEntity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Base menu of the mob markers: a marker container made of {@link MarkerSlot marker rows} followed by the
 * player inventory, with shift-click moving between both.
 * <p>
 * Equipment slots support weighted lists ({@link com.gilfort.architectstrials.marker.EquipmentList}): clicking an
 * empty equipment slot with an empty cursor opens the {@link EquipmentListMenu}. A slot with a list cannot take a
 * fixed item. The most likely item of every list is synchronized through hidden preview slots, so the screen can
 * show it.
 */
public abstract class MarkerMenu extends AbstractContainerMenu {

    private final Container container;
    private final Container previews;
    private final int inventoryEnd;

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
        this.previews = container instanceof MobMarkerBlockEntity marker ? marker.previewContainer()
                : new SimpleContainer(container.getContainerSize());
        container.startOpen(inventory.player);
        for (int row = 0; row < rowYs.length; row++) {
            int base = row * MarkerSlot.ROW_SIZE;
            this.addSlot(this.createSlot(container, base, eggX, rowYs[row]));
            for (int i = 0; i < MarkerSlot.EQUIPMENT_SLOTS.size(); i++) {
                this.addSlot(this.createSlot(container, base + 1 + i, equipmentXs[i], rowYs[row] + equipmentDy));
            }
        }
        this.addStandardInventorySlots(inventory, 8, inventoryY);
        this.inventoryEnd = this.slots.size();
        for (int i = 0; i < container.getContainerSize(); i++) {
            this.addSlot(new PreviewSlot(this.previews, i));
        }
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
        return new MarkerSlot(container, index, x, y) {
            @Override
            public boolean isActive() {
                return MarkerMenu.this.isSlotActive(index);
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return super.mayPlace(stack) && !MarkerMenu.this.hasList(index);
            }
        };
    }

    /**
     * Checks whether a marker slot is currently shown (subclasses with pages hide the other pages). Called while
     * rendering and clicking, never during construction.
     *
     * @param index the container index
     * @return {@code true} if the slot is active
     */
    protected boolean isSlotActive(int index) {
        return true;
    }

    /** @return the marker container */
    protected Container container() {
        return this.container;
    }

    /** @return the menu slot index right after the player inventory */
    protected int inventoryEnd() {
        return this.inventoryEnd;
    }

    /**
     * Returns the most likely item of an equipment slot's weighted list.
     *
     * @param index the container index
     * @return the item, or {@link ItemStack#EMPTY} if the slot has no list
     */
    public ItemStack previewItem(int index) {
        return this.previews == null ? ItemStack.EMPTY : this.previews.getItem(index);
    }

    /**
     * Checks whether an equipment slot has a weighted list.
     *
     * @param index the container index
     * @return {@code true} if a list exists
     */
    public boolean hasList(int index) {
        return !this.previewItem(index).isEmpty();
    }

    /**
     * Opens the list menu when an empty equipment slot is clicked with an empty cursor.
     */
    @Override
    public void clicked(int slotIndex, int button, ContainerInput clickType, Player player) {
        if (slotIndex >= 0 && slotIndex < this.container.getContainerSize() && clickType == ContainerInput.PICKUP
                && this.getCarried().isEmpty()) {
            Slot slot = this.slots.get(slotIndex);
            int index = slot.getContainerSlot();
            if (index % MarkerSlot.ROW_SIZE != 0 && slot.isActive() && !slot.hasItem()) {
                if (player instanceof ServerPlayer serverPlayer && this.container instanceof MobMarkerBlockEntity marker) {
                    openList(serverPlayer, marker, index);
                }
                return;
            }
        }
        super.clicked(slotIndex, button, clickType, player);
    }

    /**
     * Opens the weighted list menu of an equipment slot.
     *
     * @param player the player
     * @param marker the marker
     * @param index  the container index of the equipment slot
     */
    static void openList(ServerPlayer player, MobMarkerBlockEntity marker, int index) {
        String slotName = MarkerSlot.EQUIPMENT_SLOTS.get(index % MarkerSlot.ROW_SIZE - 1).getName();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new EquipmentListMenu(id, inventory, marker, index),
                Component.translatable("gui.architectstrials.equipment_list.title",
                        Component.translatable("gui.architectstrials.equipment_slot." + slotName))));
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
                ? this.moveItemStackTo(stack, size, this.inventoryEnd, true)
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

    /** Hidden, read-only slot that synchronizes the preview of a weighted list. */
    private static final class PreviewSlot extends Slot {

        PreviewSlot(Container previews, int index) {
            super(previews, index, -10000, -10000);
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
