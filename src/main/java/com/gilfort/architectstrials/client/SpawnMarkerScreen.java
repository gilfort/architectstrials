package com.gilfort.architectstrials.client;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.menu.SpawnMarkerMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen of the {@link SpawnMarkerMenu}: a vanilla-style container with the spawn egg slot on top and the six
 * equipment slots below; Direct Spawn Markers show a "Required" toggle in the title row (US-30).
 */
public class SpawnMarkerScreen extends AbstractContainerScreen<SpawnMarkerMenu> {

    private static final Identifier TEXTURE = ArchitectsTrials.id("textures/gui/container/spawn_marker.png");

    private Button requiredButton;

    /**
     * Creates the screen.
     *
     * @param menu      the menu
     * @param inventory the player inventory
     * @param title     the title (the marker name)
     */
    public SpawnMarkerScreen(SpawnMarkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }


    /**
     * Draws the equipment slot hints on top of the slots (after the slot icons and items).
     */
    @Override
    protected void extractSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractSlots(graphics, mouseX, mouseY);
        MarkerSlotOverlay.extract(graphics, this.menu, this.font);
    }

    @Override
    protected void init() {
        super.init();
        CursorMemory.restore();
        this.requiredButton = this.addRenderableWidget(Button.builder(this.requiredLabel(), button -> {
                    if (this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, SpawnMarkerMenu.BUTTON_TOGGLE_REQUIRED);
                    }
                })
                .bounds(this.leftPos + this.imageWidth - 7 - 76, this.topPos + 3, 76, 12)
                .build());
        this.requiredButton.visible = this.menu.requiredAvailable();
    }

    private Component requiredLabel() {
        return Component.translatable(this.menu.required() ? "gui.architectstrials.spawn_marker.required.on"
                : "gui.architectstrials.spawn_marker.required.off");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.requiredButton.visible = this.menu.requiredAvailable();
        this.requiredButton.setMessage(this.requiredLabel());
    }

    @Override
    public void removed() {
        CursorMemory.remember();
        super.removed();
    }
}
