package com.gilfort.architectstrials.client;

import java.util.Optional;

import com.gilfort.architectstrials.menu.ExitMarkerMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Screen of the {@link ExitMarkerMenu}: a small panel with the exit's settings and a preview of its camouflage.
 */
public class ExitMarkerScreen extends AbstractContainerScreen<ExitMarkerMenu> {

    private static final int BACKGROUND = 0xFFC6C6C6;
    private static final int BORDER = 0xFF555555;

    private Button requiresMobsButton;
    private Button removeCamouflageButton;

    /**
     * Creates the screen.
     *
     * @param menu      the menu
     * @param inventory the player inventory
     * @param title     the title (the marker name)
     */
    public ExitMarkerScreen(ExitMarkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 86);
    }

    @Override
    protected void init() {
        super.init();
        this.requiresMobsButton = this.addRenderableWidget(Button.builder(this.requiresMobsLabel(), button -> {
                    if (this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ExitMarkerMenu.BUTTON_TOGGLE_REQUIRES_MOBS);
                    }
                })
                .bounds(this.leftPos + 8, this.topPos + 20, this.imageWidth - 16, 16)
                .build());
        this.removeCamouflageButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.architectstrials.exit_marker.remove_camouflage"), button -> {
                            if (this.minecraft.gameMode != null) {
                                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ExitMarkerMenu.BUTTON_REMOVE_CAMOUFLAGE);
                            }
                        })
                .bounds(this.leftPos + 8, this.topPos + 62, this.imageWidth - 16, 16)
                .build());
        this.removeCamouflageButton.active = this.menu.camouflage().isPresent();
    }

    private Component requiresMobsLabel() {
        return Component.translatable(this.menu.requiresMobs() ? "gui.architectstrials.exit_marker.requires_mobs.on"
                : "gui.architectstrials.exit_marker.requires_mobs.off");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.requiresMobsButton.setMessage(this.requiresMobsLabel());
        this.removeCamouflageButton.active = this.menu.camouflage().isPresent();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, BORDER);
        graphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.imageWidth - 1, this.topPos + this.imageHeight - 1, BACKGROUND);
    }

    /**
     * Draws the title and the camouflage preview; the menu has no player inventory.
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF404040, false);
        Optional<BlockState> camouflage = this.menu.camouflage();
        camouflage.ifPresent(state -> graphics.item(new ItemStack(state.getBlock()), 8, 41));
        Component label = camouflage
                .map(state -> Component.translatable("gui.architectstrials.exit_marker.camouflage", state.getBlock().getName()))
                .orElseGet(() -> Component.translatable("gui.architectstrials.exit_marker.camouflage.none"));
        int x = camouflage.isPresent() ? 28 : 8;
        graphics.text(this.font, this.font.substrByWidth(label, this.imageWidth - x - 6).getString(), x, 45, 0xFF404040, false);
    }
}
