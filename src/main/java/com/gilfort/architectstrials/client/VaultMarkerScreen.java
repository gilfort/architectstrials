package com.gilfort.architectstrials.client;

import com.gilfort.architectstrials.menu.VaultMarkerMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Screen of the {@link VaultMarkerMenu}: the key slot with a hint, the normal / ominous switch and the player
 * inventory, drawn as a vanilla-style panel.
 */
public class VaultMarkerScreen extends AbstractContainerScreen<VaultMarkerMenu> implements GhostSlotScreen {

    private static final int BACKGROUND = 0xFFC6C6C6;
    private static final int BORDER = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int TEXT_COLOR = 0xFF404040;

    private Button ominousButton;

    /**
     * Creates the screen.
     *
     * @param menu      the menu
     * @param inventory the player inventory
     * @param title     the title (the marker name)
     */
    public VaultMarkerScreen(VaultMarkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.ominousButton = this.addRenderableWidget(Button.builder(this.ominousLabel(), button -> {
                    if (this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, VaultMarkerMenu.BUTTON_TOGGLE_OMINOUS);
                    }
                })
                .bounds(this.leftPos + this.imageWidth - 7 - 70, this.topPos + 3, 70, 12)
                .build());
    }

    private Component ominousLabel() {
        return Component.translatable(this.menu.ominous() ? "gui.architectstrials.vault_marker.ominous" : "gui.architectstrials.vault_marker.normal");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.ominousButton.setMessage(this.ominousLabel());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, BORDER);
        graphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.imageWidth - 1, this.topPos + this.imageHeight - 1, BACKGROUND);
        for (Slot slot : this.menu.slots) {
            int x = this.leftPos + slot.x - 1;
            int y = this.topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, SLOT_DARK);
            graphics.fill(x + 1, y + 1, x + 18, y + 18, SLOT_LIGHT);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component hint = Component.translatable("gui.architectstrials.vault_marker.key");
        graphics.text(this.font, hint, VaultMarkerMenu.KEY_X - 4 - this.font.width(hint), VaultMarkerMenu.KEY_Y + 4, TEXT_COLOR, false);
    }

    @Override
    public int ghostLeft() {
        return this.leftPos;
    }

    @Override
    public int ghostTop() {
        return this.topPos;
    }

    @Override
    public AbstractContainerMenu ghostMenu() {
        return this.menu;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return GhostSlotScreen.scroll(this, this.hoveredSlot, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
