package com.gilfort.architectstrials.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A configuration slot that only holds a copy of an item (US-39). Vanilla can neither put items in nor take them
 * out ({@link #mayPlace} / {@link #mayPickup} are always {@code false}); menus route clicks on ghost slots to
 * {@link GhostSlots#click}, which copies the cursor item without consuming it. Middle click in Creative still clones
 * the content into the cursor (vanilla).
 */
public class GhostSlot extends Slot {

    /**
     * Creates a ghost slot.
     *
     * @param container the container holding the configured items
     * @param index     the container index
     * @param x         the x position in the screen
     * @param y         the y position in the screen
     */
    public GhostSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    /**
     * Checks whether a copy of an item may be put into this slot. Subclasses restrict the accepted items.
     *
     * @param stack the item (never empty)
     * @return {@code true} if the item fits
     */
    public boolean accepts(ItemStack stack) {
        return true;
    }

    /**
     * Whether the count of this slot matters (spawn eggs, loot entries, keys). Countable slots add to the count on
     * clicks; other slots always hold exactly one item.
     *
     * @return {@code true} if the slot is countable
     */
    public boolean countable() {
        return true;
    }

    /**
     * Returns the highest count this slot holds of an item.
     *
     * @param stack the item
     * @return the maximum count (at least 1)
     */
    public int maxCount(ItemStack stack) {
        return this.countable() ? Math.max(1, this.getMaxStackSize(stack)) : 1;
    }

    /**
     * Sets the configured item: a copy of the stack, clamped to {@link #maxCount}; an empty stack clears the slot.
     *
     * @param stack the item (not modified)
     */
    public void setGhost(ItemStack stack) {
        if (stack.isEmpty()) {
            this.set(ItemStack.EMPTY);
        } else {
            this.set(stack.copyWithCount(Math.clamp(stack.getCount(), 1, this.maxCount(stack))));
        }
    }

    @Override
    public final boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public final boolean mayPickup(Player player) {
        return false;
    }
}
