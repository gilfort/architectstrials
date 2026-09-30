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
 * Screen of the {@link TrialSpawnerMarkerMenu}: three mob rows (spawn egg + equipment), the total mob count and
 * the number of simultaneous mobs with +/- buttons.
 */
public class TrialSpawnerMarkerScreen extends AbstractContainerScreen<TrialSpawnerMarkerMenu> {

    private static final Identifier TEXTURE = ArchitectsTrials.id("textures/gui/container/trial_spawner_marker.png");

    private static final int CONTROL_Y = 78;
    private static final int BUTTON_SIZE = 14;
    private static final int TEXT_COLOR = 0xFF404040;

    /**
     * Creates the screen.
     *
     * @param menu      the menu
     * @param inventory the player inventory
     * @param title     the title (the marker name)
     */
    public TrialSpawnerMarkerScreen(TrialSpawnerMarkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 186);
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
    }

    private void click(int buttonId) {
        if (this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component total = Component.translatable("gui.architectstrials.trial_spawner_marker.total", this.menu.totalMobs());
        graphics.text(this.font, total, 8, CONTROL_Y, TEXT_COLOR, false);
        Component simultaneous = Component.translatable("gui.architectstrials.trial_spawner_marker.simultaneous", this.menu.simultaneousMobs());
        graphics.text(this.font, simultaneous, 130 - this.font.width(simultaneous), CONTROL_Y, TEXT_COLOR, false);
    }
}
