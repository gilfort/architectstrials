package com.gilfort.architectstrials.client;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.menu.TrialSpawnerMarkerMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen of the {@link TrialSpawnerMarkerMenu}: three mob rows (spawn egg + equipment) of the current page, the
 * page's total mob count and simultaneous mobs with +/- buttons, a page switch (normal / ominous) and the
 * ominous toggle.
 */
public class TrialSpawnerMarkerScreen extends AbstractContainerScreen<TrialSpawnerMarkerMenu> {

    private static final Identifier TEXTURE = ArchitectsTrials.id("textures/gui/container/trial_spawner_marker.png");

    private static final int CONTROL_Y = 78;
    private static final int TOGGLE_Y = 94;
    private static final int BUTTON_SIZE = 14;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int OMINOUS_COLOR = 0xFF8A2BE2;

    private Button pageButton;
    private Button ominousButton;

    /**
     * Creates the screen.
     *
     * @param menu      the menu
     * @param inventory the player inventory
     * @param title     the title (the marker name)
     */
    public TrialSpawnerMarkerScreen(TrialSpawnerMarkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 202);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.architectstrials.trial_spawner_marker.decrease"),
                        button -> this.click(TrialSpawnerMarkerMenu.BUTTON_DECREASE))
                .bounds(this.leftPos + 134, this.topPos + CONTROL_Y - 3, BUTTON_SIZE, BUTTON_SIZE)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.architectstrials.trial_spawner_marker.increase"),
                        button -> this.click(TrialSpawnerMarkerMenu.BUTTON_INCREASE))
                .bounds(this.leftPos + 152, this.topPos + CONTROL_Y - 3, BUTTON_SIZE, BUTTON_SIZE)
                .build());
        this.pageButton = this.addRenderableWidget(Button.builder(this.pageLabel(), button -> this.click(TrialSpawnerMarkerMenu.BUTTON_SWITCH_PAGE))
                .bounds(this.leftPos + 7, this.topPos + TOGGLE_Y, 78, BUTTON_SIZE)
                .build());
        this.ominousButton = this.addRenderableWidget(Button.builder(this.ominousLabel(),
                        button -> this.click(TrialSpawnerMarkerMenu.BUTTON_TOGGLE_OMINOUS))
                .bounds(this.leftPos + 91, this.topPos + TOGGLE_Y, 78, BUTTON_SIZE)
                .build());
    }

    private Component pageLabel() {
        return Component.translatable(this.menu.ominousPage() ? "gui.architectstrials.trial_spawner_marker.page.ominous"
                : "gui.architectstrials.trial_spawner_marker.page.normal");
    }

    private Component ominousLabel() {
        return Component.translatable(this.menu.ominousAllowed() ? "gui.architectstrials.trial_spawner_marker.ominous.allowed"
                : "gui.architectstrials.trial_spawner_marker.ominous.blocked");
    }

    private void click(int buttonId) {
        if (this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.pageButton.setMessage(this.pageLabel());
        this.ominousButton.setMessage(this.ominousLabel());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        MarkerSlotOverlay.extract(graphics, this.menu, this.font);
        int color = this.menu.ominousPage() ? OMINOUS_COLOR : TEXT_COLOR;
        Component total = Component.translatable("gui.architectstrials.trial_spawner_marker.total", this.menu.totalMobs());
        graphics.text(this.font, total, 8, CONTROL_Y, color, false);
        Component simultaneous = Component.translatable("gui.architectstrials.trial_spawner_marker.simultaneous", this.menu.simultaneousMobs());
        graphics.text(this.font, simultaneous, 130 - this.font.width(simultaneous), CONTROL_Y, color, false);
    }
}
