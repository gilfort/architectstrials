package com.gilfort.architectstrials.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.editor.Selection;
import com.gilfort.architectstrials.editor.SelectionEditPayload;
import com.gilfort.architectstrials.slot.Slot;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Screen for editing the two corners of a selection by coordinates (shift + right click with the selection tool).
 * Each coordinate has a text field and -/+ buttons; "Here" sets a corner to the player's position. The size is
 * shown live, red if the selection exceeds the maximum footprint. "Done" sends the corners to the server.
 */
public class SelectionEditScreen extends Screen {

    private static final int FIELD_WIDTH = 46;
    private static final int BUTTON_SIZE = 16;
    private static final int ROW_HEIGHT = 26;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int ERROR_COLOR = 0xFFFF5555;
    private static final String[] AXES = {"x", "y", "z"};

    private final InteractionHand hand;
    private final BlockPos playerPos;
    private final BlockPos initialFirst;
    private final BlockPos initialSecond;
    private final List<EditBox> fields = new ArrayList<>();
    private Button done;

    /**
     * Creates the screen.
     *
     * @param hand      the hand holding the tool
     * @param selection the current selection, or {@code null}
     * @param playerPos the player's block position (default for missing corners)
     */
    public SelectionEditScreen(InteractionHand hand, @Nullable Selection selection, BlockPos playerPos) {
        super(Component.translatable("gui.architectstrials.selection_editor.title"));
        this.hand = hand;
        this.playerPos = playerPos;
        this.initialFirst = selection == null ? playerPos : selection.first().orElse(playerPos);
        this.initialSecond = selection == null ? playerPos : selection.second().orElse(playerPos);
    }

    @Override
    protected void init() {
        this.fields.clear();
        int rowWidth = 3 * (FIELD_WIDTH + 2 * BUTTON_SIZE + 8) + 50;
        int left = (this.width - rowWidth) / 2 + 50;
        int top = this.height / 2 - 40;
        for (int corner = 0; corner < 2; corner++) {
            BlockPos pos = corner == 0 ? this.initialFirst : this.initialSecond;
            int y = top + corner * ROW_HEIGHT;
            int[] values = {pos.getX(), pos.getY(), pos.getZ()};
            for (int axis = 0; axis < 3; axis++) {
                int x = left + axis * (FIELD_WIDTH + 2 * BUTTON_SIZE + 8);
                EditBox field = new EditBox(this.font, x + BUTTON_SIZE, y, FIELD_WIDTH, BUTTON_SIZE,
                        Component.translatable("gui.architectstrials.selection_editor." + AXES[axis]));
                field.setMaxLength(9);
                field.setValue(Integer.toString(values[axis]));
                field.setResponder(value -> this.updateDone());
                this.fields.add(field);
                this.addRenderableWidget(Button.builder(Component.translatable("gui.architectstrials.selection_editor.decrease"),
                        button -> this.adjust(field, -1)).bounds(x, y, BUTTON_SIZE, BUTTON_SIZE).build());
                this.addRenderableWidget(field);
                this.addRenderableWidget(Button.builder(Component.translatable("gui.architectstrials.selection_editor.increase"),
                        button -> this.adjust(field, 1)).bounds(x + BUTTON_SIZE + FIELD_WIDTH, y, BUTTON_SIZE, BUTTON_SIZE).build());
            }
            int cornerIndex = corner;
            this.addRenderableWidget(Button.builder(Component.translatable("gui.architectstrials.selection_editor.here"),
                    button -> this.setCorner(cornerIndex, this.playerPos))
                    .bounds(left + 3 * (FIELD_WIDTH + 2 * BUTTON_SIZE + 8), y, 40, BUTTON_SIZE).build());
        }
        int buttonsY = top + 2 * ROW_HEIGHT + 24;
        this.done = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.save())
                .bounds(this.width / 2 - 104, buttonsY, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(this.width / 2 + 4, buttonsY, 100, 20).build());
        this.updateDone();
    }

    private void adjust(EditBox field, int delta) {
        parse(field).ifPresent(value -> field.setValue(Integer.toString(value + delta)));
    }

    private void setCorner(int corner, BlockPos pos) {
        int[] values = {pos.getX(), pos.getY(), pos.getZ()};
        for (int axis = 0; axis < 3; axis++) {
            this.fields.get(corner * 3 + axis).setValue(Integer.toString(values[axis]));
        }
    }

    private Optional<BlockPos> corner(int corner) {
        Optional<Integer> x = parse(this.fields.get(corner * 3));
        Optional<Integer> y = parse(this.fields.get(corner * 3 + 1));
        Optional<Integer> z = parse(this.fields.get(corner * 3 + 2));
        return x.isPresent() && y.isPresent() && z.isPresent() ? Optional.of(new BlockPos(x.get(), y.get(), z.get())) : Optional.empty();
    }

    private static Optional<Integer> parse(EditBox field) {
        try {
            return Optional.of(Integer.parseInt(field.getValue().trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void updateDone() {
        if (this.done != null && this.fields.size() == 6) {
            this.done.active = this.corner(0).isPresent() && this.corner(1).isPresent();
        }
    }

    private void save() {
        Optional<BlockPos> first = this.corner(0);
        Optional<BlockPos> second = this.corner(1);
        if (first.isPresent() && second.isPresent()) {
            ClientPacketDistributor.sendToServer(new SelectionEditPayload(this.hand == InteractionHand.MAIN_HAND, first.get(), second.get()));
        }
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int top = this.height / 2 - 40;
        int rowWidth = 3 * (FIELD_WIDTH + 2 * BUTTON_SIZE + 8) + 50;
        int labelX = (this.width - rowWidth) / 2;
        graphics.centeredText(this.font, this.title, this.width / 2, top - 30, LABEL_COLOR);
        for (int corner = 0; corner < 2; corner++) {
            graphics.text(this.font, Component.translatable("gui.architectstrials.selection_editor.corner", corner + 1), labelX,
                    top + corner * ROW_HEIGHT + 4, LABEL_COLOR, true);
        }
        Optional<BlockPos> first = this.corner(0);
        Optional<BlockPos> second = this.corner(1);
        Component size;
        int color = LABEL_COLOR;
        if (first.isPresent() && second.isPresent()) {
            int sizeX = Math.abs(first.get().getX() - second.get().getX()) + 1;
            int sizeY = Math.abs(first.get().getY() - second.get().getY()) + 1;
            int sizeZ = Math.abs(first.get().getZ() - second.get().getZ()) + 1;
            size = Component.translatable("gui.architectstrials.selection_editor.size", sizeX, sizeY, sizeZ);
            if (sizeX > Slot.MAX_STRUCTURE_SIZE || sizeZ > Slot.MAX_STRUCTURE_SIZE) {
                color = ERROR_COLOR;
            }
        } else {
            size = Component.translatable("gui.architectstrials.selection_editor.invalid");
            color = ERROR_COLOR;
        }
        graphics.centeredText(this.font, size, this.width / 2, top + 2 * ROW_HEIGHT + 6, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
