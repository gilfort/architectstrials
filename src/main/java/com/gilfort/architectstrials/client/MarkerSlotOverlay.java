package com.gilfort.architectstrials.client;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.menu.MarkerMenu;
import com.gilfort.architectstrials.menu.MarkerSlot;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the equipment slot hints of the mob marker screens (in screen-relative coordinates, i.e. from
 * {@code extractLabels}):
 * <ul>
 * <li>a fixed item gets a small "100%" at its bottom,</li>
 * <li>a slot with a weighted list shows the list's most likely item plus the dice icon,</li>
 * <li>an empty slot shows the dice icon in its top-right corner — clicking it opens the list menu.</li>
 * </ul>
 */
public final class MarkerSlotOverlay {

    /** Dice icon sprite (8 × 8). */
    public static final Identifier DICE = ArchitectsTrials.id("container/slot/dice");

    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private MarkerSlotOverlay() {
    }

    /**
     * Draws the hints for all active equipment slots of a marker menu.
     *
     * @param graphics the graphics
     * @param menu     the marker menu
     * @param font     the font
     */
    public static void extract(GuiGraphicsExtractor graphics, MarkerMenu menu, Font font) {
        for (Slot slot : menu.slots) {
            if (!(slot instanceof MarkerSlot) || !slot.isActive() || slot.getContainerSlot() % MarkerSlot.ROW_SIZE == 0) {
                continue;
            }
            if (slot.hasItem()) {
                Component label = Component.translatable("gui.architectstrials.equipment_slot.fixed");
                graphics.pose().pushMatrix();
                graphics.pose().translate(slot.x + 16 - font.width(label) * 0.5F, slot.y + 12);
                graphics.pose().scale(0.5F, 0.5F);
                graphics.text(font, label, 0, 0, TEXT_COLOR, true);
                graphics.pose().popMatrix();
                continue;
            }
            ItemStack preview = menu.previewItem(slot.getContainerSlot());
            if (!preview.isEmpty()) {
                graphics.fakeItem(preview, slot.x, slot.y);
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DICE, slot.x + 9, slot.y - 1, 8, 8);
        }
    }
}
