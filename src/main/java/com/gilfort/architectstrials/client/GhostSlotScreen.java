package com.gilfort.architectstrials.client;

import com.gilfort.architectstrials.menu.GhostSlot;
import com.gilfort.architectstrials.menu.GhostSlots;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * A screen with {@link GhostSlot ghost slots} (US-39). Gives the JEI integration the screen position and offers the
 * client side of the ghost slot input that does not go through vanilla clicks (mouse wheel, drag &amp; drop).
 */
public interface GhostSlotScreen {

    /** @return the x position of the menu background (slot positions are relative to it) */
    int ghostLeft();

    /** @return the y position of the menu background (slot positions are relative to it) */
    int ghostTop();

    /** @return the menu of the screen */
    AbstractContainerMenu ghostMenu();

    /**
     * Changes the count of a countable ghost slot under the mouse by one per wheel step.
     *
     * @param screen  the screen
     * @param hovered the slot under the mouse
     * @param scrollY the vertical wheel movement
     * @return {@code true} if the wheel was used for a ghost slot
     */
    static boolean scroll(AbstractContainerScreen<?> screen, @Nullable Slot hovered, double scrollY) {
        if (!(hovered instanceof GhostSlot slot) || !slot.countable() || !slot.hasItem() || scrollY == 0) {
            return false;
        }
        ItemStack current = slot.getItem();
        int count = Math.clamp(current.getCount() + (scrollY > 0 ? 1 : -1), 1, slot.maxCount(current));
        send(screen.getMenu(), slot, current.copyWithCount(count));
        return true;
    }

    /**
     * Sets a ghost slot on the server and predicts the result on the client.
     *
     * @param menu  the menu
     * @param slot  the ghost slot
     * @param stack the item, or empty to clear
     */
    static void send(AbstractContainerMenu menu, GhostSlot slot, ItemStack stack) {
        if (stack.isEmpty() || slot.accepts(stack)) {
            slot.setGhost(stack);
            ClientPacketDistributor.sendToServer(new GhostSlots.SetGhost(menu.containerId, slot.index, stack));
        }
    }
}
