package com.gilfort.architectstrials.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.marker.EquipmentList;
import com.gilfort.architectstrials.menu.EquipmentListMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen of the {@link EquipmentListMenu}: 3 × 3 item slots, each with a chance field in percent (one decimal),
 * the remaining "nothing" chance and a button back to the marker. Typed chances are sent to the server, which
 * clamps them so the list stays at or below 100 %; fields show the stored value while not being edited.
 */
public class EquipmentListScreen extends AbstractContainerScreen<EquipmentListMenu> {

    private static final Identifier TEXTURE = ArchitectsTrials.id("textures/gui/container/equipment_list.png");
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int FIELD_WIDTH = 34;

    private final List<EditBox> fields = new ArrayList<>();

    /**
     * Creates the screen.
     *
     * @param menu      the menu
     * @param inventory the player inventory
     * @param title     the title (names the equipment slot)
     */
    public EquipmentListScreen(EquipmentListMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 200, 194);
        this.inventoryLabelX = 19;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        CursorMemory.restore();
        this.fields.clear();
        for (int position = 0; position < EquipmentList.MAX_ENTRIES; position++) {
            int x = this.leftPos + EquipmentListMenu.GRID_X + (position % 3) * EquipmentListMenu.CELL_WIDTH + 19;
            int y = this.topPos + EquipmentListMenu.GRID_Y + (position / 3) * EquipmentListMenu.CELL_HEIGHT;
            EditBox field = new EditBox(this.font, x, y, FIELD_WIDTH, 16, Component.translatable("gui.architectstrials.equipment_list.chance"));
            field.setMaxLength(5);
            int fieldPosition = position;
            field.setResponder(value -> parse(value).ifPresent(chance -> this.sendChance(fieldPosition, chance)));
            this.fields.add(this.addRenderableWidget(field));
        }
        this.addRenderableWidget(Button.builder(Component.translatable("gui.architectstrials.equipment_list.back"),
                        button -> this.click(EquipmentListMenu.BUTTON_BACK))
                .bounds(this.leftPos + this.imageWidth - 58, this.topPos + 88, 50, 14)
                .build());
        this.syncFields();
    }

    private static Optional<Integer> parse(String value) {
        try {
            double percent = Double.parseDouble(value.trim().replace(',', '.'));
            return Optional.of((int) Math.round(Math.clamp(percent, 0.0, 100.0) * 10.0));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String format(int chance) {
        return chance % 10 == 0 ? Integer.toString(chance / 10) : String.format(Locale.ROOT, "%.1f", chance / 10.0);
    }

    private void sendChance(int position, int chance) {
        if (this.menu.hasItem(position) && chance != this.menu.chance(position)) {
            this.click(position * EquipmentListMenu.CHANCE_STRIDE + chance);
        }
    }

    private void click(int buttonId) {
        if (this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    /** Shows the stored chances in all fields that are not being edited; hides the fields of empty positions. */
    private void syncFields() {
        for (int position = 0; position < this.fields.size(); position++) {
            EditBox field = this.fields.get(position);
            boolean hasItem = this.menu.hasItem(position);
            field.visible = hasItem;
            if (hasItem && !field.isFocused()) {
                String stored = format(this.menu.chance(position));
                if (!stored.equals(field.getValue())) {
                    field.setValue(stored);
                }
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.syncFields();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        for (int position = 0; position < EquipmentList.MAX_ENTRIES; position++) {
            if (this.menu.hasItem(position)) {
                int x = EquipmentListMenu.GRID_X + (position % 3) * EquipmentListMenu.CELL_WIDTH + 19 + FIELD_WIDTH + 2;
                int y = EquipmentListMenu.GRID_Y + (position / 3) * EquipmentListMenu.CELL_HEIGHT + 4;
                graphics.text(this.font, Component.translatable("gui.architectstrials.equipment_list.percent"), x, y, TEXT_COLOR, false);
            }
        }
        graphics.text(this.font, Component.translatable("gui.architectstrials.equipment_list.nothing", format(this.menu.remainder())),
                8, 91, TEXT_COLOR, false);
    }

    @Override
    public void removed() {
        CursorMemory.remember();
        super.removed();
    }

    /**
     * Lets a focused text field consume key presses, so typing (e.g. the inventory key "e" or the hotbar number
     * keys) does not close the screen or move items. Escape still closes the screen.
     *
     * @param event the key event
     * @return {@code true} if the key was handled
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!event.isEscape() && this.getFocused() instanceof EditBox field && field.isVisible() && field.canConsumeInput()) {
            field.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }
}
