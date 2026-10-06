package com.gilfort.architectstrials.menu;

import com.gilfort.architectstrials.block.VaultMarkerBlockEntity;
import com.gilfort.architectstrials.registry.ModMenuTypes;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu of the Vault Marker (US-26): the key slot, a normal / ominous switch and the player inventory.
 */
public class VaultMarkerMenu extends AbstractContainerMenu {

    /** Button id that switches between normal and ominous. */
    public static final int BUTTON_TOGGLE_OMINOUS = 0;

    /** Position of the key slot. */
    public static final int KEY_X = 80;

    /** Position of the key slot. */
    public static final int KEY_Y = 35;

    /** Y position of the player inventory. */
    public static final int INVENTORY_Y = 84;

    private final Container container;
    private final ContainerData data;

    /**
     * Creates the client-side menu.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     */
    public VaultMarkerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(1), new SimpleContainerData(VaultMarkerBlockEntity.DATA_COUNT));
    }

    /**
     * Creates the server-side menu for a marker.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     * @param container   the marker's key container
     * @param data        the marker's variant
     */
    public VaultMarkerMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenuTypes.VAULT_MARKER.get(), containerId);
        checkContainerSize(container, 1);
        checkContainerDataCount(data, VaultMarkerBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        container.startOpen(inventory.player);
        this.addSlot(new Slot(container, VaultMarkerBlockEntity.KEY_SLOT, KEY_X, KEY_Y));
        this.addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        this.addDataSlots(data);
    }

    /** @return {@code true} if the vault is ominous */
    public boolean ominous() {
        return this.data.get(0) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_TOGGLE_OMINOUS) {
            this.data.set(0, this.ominous() ? 0 : 1);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = slotIndex == 0 ? this.moveItemStackTo(stack, 1, this.slots.size(), true) : this.moveItemStackTo(stack, 0, 1, false);
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
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
