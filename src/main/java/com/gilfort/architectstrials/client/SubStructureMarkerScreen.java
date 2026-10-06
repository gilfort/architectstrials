package com.gilfort.architectstrials.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.sub.SubStructureMarkerBlockEntity;
import com.gilfort.architectstrials.sub.SubStructureMarkerMenu;
import com.gilfort.architectstrials.sub.SubStructureNetwork;
import com.gilfort.architectstrials.sub.SubStructureSetup;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Screen of the {@link SubStructureMarkerMenu} (US-32): the offset area (−X … +Z), up to
 * {@value SubStructureSetup#MAX_ENTRIES} sub structures with their chance in percent, and the fallback. "Save"
 * sends everything to the server, which checks the ids and the sum of the chances.
 */
public class SubStructureMarkerScreen extends AbstractContainerScreen<SubStructureMarkerMenu> {

    private static final int BACKGROUND = 0xFFC6C6C6;
    private static final int BORDER = 0xFF555555;
    private static final int TEXT = 0xFF404040;
    private static final String[] OFFSET_LABELS = {"-X", "+X", "-Y", "+Y", "-Z", "+Z"};
    private static final int ROWS_Y = 62;
    private static final int ROW_HEIGHT = 18;

    private final List<EditBox> offsetFields = new ArrayList<>();
    private final List<EditBox> idFields = new ArrayList<>();
    private final List<EditBox> percentFields = new ArrayList<>();
    private EditBox fallbackField;

    /**
     * Creates the screen.
     *
     * @param menu      the menu
     * @param inventory the player inventory
     * @param title     the title (the marker name)
     */
    public SubStructureMarkerScreen(SubStructureMarkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 260, ROWS_Y + SubStructureSetup.MAX_ENTRIES * ROW_HEIGHT + 34);
    }

    @Override
    protected void init() {
        super.init();
        this.offsetFields.clear();
        this.idFields.clear();
        this.percentFields.clear();
        SubStructureSetup setup = SubStructureSetup.EMPTY;
        int[] offsets = new int[SubStructureMarkerBlockEntity.OFFSETS];
        if (this.minecraft.level != null && this.minecraft.level.getBlockEntity(this.menu.pos()) instanceof SubStructureMarkerBlockEntity marker) {
            setup = marker.setup();
            offsets = marker.offsets();
        }
        for (int i = 0; i < SubStructureMarkerBlockEntity.OFFSETS; i++) {
            EditBox field = this.field(this.leftPos + 8 + i * 36, this.topPos + 30, 28, 3, "gui.architectstrials.sub_structure_marker.offset");
            field.setValue(Integer.toString(offsets[i]));
            this.offsetFields.add(field);
        }
        for (int row = 0; row < SubStructureSetup.MAX_ENTRIES; row++) {
            int y = this.topPos + ROWS_Y + row * ROW_HEIGHT;
            EditBox id = this.field(this.leftPos + 8, y, 172, 128, "gui.architectstrials.sub_structure_marker.structure");
            EditBox percent = this.field(this.leftPos + 186, y, 28, 3, "gui.architectstrials.sub_structure_marker.percent");
            if (row < setup.entries().size()) {
                id.setValue(setup.entries().get(row).structure().toString());
                percent.setValue(Integer.toString(setup.entries().get(row).percent()));
            }
            this.idFields.add(id);
            this.percentFields.add(percent);
        }
        int bottom = this.topPos + ROWS_Y + SubStructureSetup.MAX_ENTRIES * ROW_HEIGHT + 12;
        this.fallbackField = this.field(this.leftPos + 8, bottom, 150, 128, "gui.architectstrials.sub_structure_marker.fallback");
        setup.fallback().ifPresent(id -> this.fallbackField.setValue(id.toString()));
        this.addRenderableWidget(Button.builder(Component.translatable("gui.architectstrials.sub_structure_marker.save"), button -> this.save())
                .bounds(this.leftPos + 164, bottom - 1, 58, 16).build());
    }

    private EditBox field(int x, int y, int width, int maxLength, String narration) {
        EditBox field = new EditBox(this.font, x, y, width, 14, Component.translatable(narration));
        field.setMaxLength(maxLength);
        return this.addRenderableWidget(field);
    }

    private void save() {
        List<SubStructureSetup.Entry> entries = new ArrayList<>();
        for (int row = 0; row < SubStructureSetup.MAX_ENTRIES; row++) {
            Optional<Identifier> id = parseId(this.idFields.get(row).getValue());
            if (id.isPresent()) {
                entries.add(new SubStructureSetup.Entry(id.get(), parseInt(this.percentFields.get(row).getValue())));
            }
        }
        List<Integer> offsets = this.offsetFields.stream().map(field -> parseInt(field.getValue())).toList();
        SubStructureSetup setup = new SubStructureSetup(entries, parseId(this.fallbackField.getValue()));
        ClientPacketDistributor.sendToServer(new SubStructureNetwork.Edit(this.menu.containerId, setup, offsets));
    }

    private static Optional<Identifier> parseId(String value) {
        return value.isBlank() ? Optional.empty() : Optional.ofNullable(Identifier.tryParse(value.trim()));
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, BORDER);
        graphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.imageWidth - 1, this.topPos + this.imageHeight - 1, BACKGROUND);
    }

    /**
     * Draws the title and the column labels; the menu has no player inventory.
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
        for (int i = 0; i < OFFSET_LABELS.length; i++) {
            graphics.text(this.font, OFFSET_LABELS[i], 8 + i * 36 + 8, 20, TEXT, false);
        }
        graphics.text(this.font, Component.translatable("gui.architectstrials.sub_structure_marker.offsets"), 8 + 6 * 36, 33, TEXT, false);
        graphics.text(this.font, Component.translatable("gui.architectstrials.sub_structure_marker.structures"), 8, ROWS_Y - 11, TEXT, false);
        graphics.text(this.font, "%", 186 + 30, ROWS_Y - 11, TEXT, false);
        graphics.text(this.font, Component.translatable("gui.architectstrials.sub_structure_marker.fallback"), 8,
                ROWS_Y + SubStructureSetup.MAX_ENTRIES * ROW_HEIGHT + 2, TEXT, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!event.isEscape() && this.getFocused() instanceof EditBox field && field.isVisible() && field.canConsumeInput()) {
            field.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }
}
