package com.gilfort.architectstrials.client.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Scrollable form in the details area of the challenge browser (US-42). Each row has a label on the left and widgets
 * or live text on the right; rows of changed fields show a marker, rows with validation errors are red and show the
 * error as tooltip. Plain text rows show the read-only information below the form.
 */
public class ChallengeEditList extends ContainerObjectSelectionList<ChallengeEditList.Row> {

    /** Height of a row. */
    public static final int ROW_HEIGHT = 22;
    /** Height of a plain text row. */
    public static final int TEXT_HEIGHT = 13;
    /** Width of the label column. */
    public static final int LABEL_WIDTH = 104;
    private static final int GAP = 3;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFD866;
    private static final int RED = 0xFFFF5555;
    private static final int CHANGED = 0xFFFFAA00;

    private final Supplier<Set<String>> changed;
    private final Supplier<Map<String, Component>> errors;

    /**
     * Creates the list.
     *
     * @param minecraft the client
     * @param x         the left edge
     * @param y         the top edge
     * @param width     the width
     * @param height    the height
     * @param changed   the keys of changed fields
     * @param errors    validation errors by field key
     */
    public ChallengeEditList(Minecraft minecraft, int x, int y, int width, int height, Supplier<Set<String>> changed,
            Supplier<Map<String, Component>> errors) {
        super(minecraft, width, height, y, ROW_HEIGHT);
        this.setX(x);
        this.changed = changed;
        this.errors = errors;
    }

    /**
     * Replaces all rows.
     *
     * @param rows the rows
     */
    public void setRows(List<Row> rows) {
        double scroll = this.scrollAmount();
        this.clearEntries();
        // Plain text rows are compact; rows with widgets need room for buttons and text fields.
        rows.forEach(row -> this.addEntry(row, row.parts.isEmpty() ? TEXT_HEIGHT : ROW_HEIGHT));
        this.setScrollAmount(scroll);
    }

    /** @return the width available for a full-width text row */
    public int textWidth() {
        return this.getRowWidth() - 8;
    }

    @Override
    public int getRowWidth() {
        return this.getWidth() - 14;
    }

    @Override
    public int getRowLeft() {
        return this.getX() + 4;
    }

    @Override
    protected int scrollBarX() {
        return this.getRight() - 6;
    }

    @Override
    protected void extractListBackground(GuiGraphicsExtractor graphics) {
    }

    @Override
    protected void extractListSeparators(GuiGraphicsExtractor graphics) {
    }

    /** A part on the right side of a row: a widget or live text. */
    public sealed interface Part {
    }

    /**
     * A widget of a row.
     *
     * @param widget the widget; its width is kept
     */
    public record WidgetPart(AbstractWidget widget) implements Part {
    }

    /**
     * Live text of a row.
     *
     * @param text  the text, read every frame
     * @param color the color
     * @param width the reserved width
     */
    public record TextPart(Supplier<Component> text, int color, int width) implements Part {
    }

    /**
     * An icon of a row.
     *
     * @param sprite the sprite
     */
    public record IconPart(Identifier sprite) implements Part {
    }

    /** One row: label (optional) plus parts, or a full-width text line. */
    public final class Row extends ContainerObjectSelectionList.Entry<Row> {

        private final @Nullable Component label;
        private final @Nullable String field;
        private final List<Part> parts;
        private final int color;
        private final int indent;
        private final List<AbstractWidget> widgets = new ArrayList<>();

        /**
         * Creates a row.
         *
         * @param label  the label, or {@code null}
         * @param field  the field key for change and error markers, or {@code null}
         * @param color  the label color
         * @param indent the indent of the label
         * @param parts  the parts on the right
         */
        public Row(@Nullable Component label, @Nullable String field, int color, int indent, List<Part> parts) {
            this.label = label;
            this.field = field;
            this.color = color;
            this.indent = indent;
            this.parts = List.copyOf(parts);
            for (Part part : parts) {
                if (part instanceof WidgetPart(AbstractWidget widget)) {
                    this.widgets.add(widget);
                }
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ChallengeEditList.this.minecraft.font;
            int x = this.getContentX() + this.indent;
            int y = this.getContentY();
            int middle = this.getContentYMiddle() - font.lineHeight / 2;
            Component error = this.field == null ? null : ChallengeEditList.this.errors.get().get(this.field);
            boolean changedField = this.field != null && ChallengeEditList.this.changed.get().contains(this.field);
            int labelWidth = this.parts.isEmpty() ? this.getContentWidth() - this.indent : LABEL_WIDTH - this.indent;
            if (this.label != null) {
                if (changedField) {
                    graphics.text(font, "●", x - 7 < this.getContentX() ? x : x - 7, middle, CHANGED, false);
                }
                int labelX = changedField && x - 7 < this.getContentX() ? x + 7 : x;
                List<FormattedCharSequence> lines = font.split(this.label, Math.max(20, labelWidth - (labelX - x)));
                FormattedCharSequence text = lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.getFirst();
                graphics.text(font, text, labelX, middle, error != null ? RED : this.color, false);
                if (error != null && mouseX >= this.getContentX() && mouseX < this.getContentX() + LABEL_WIDTH && mouseY >= y
                        && mouseY < y + this.getContentHeight()) {
                    graphics.setTooltipForNextFrame(font, error, mouseX, mouseY);
                }
            }
            int partX = this.getContentX() + LABEL_WIDTH;
            for (Part part : this.parts) {
                switch (part) {
                    case WidgetPart(AbstractWidget widget) -> {
                        widget.setPosition(partX, this.getContentYMiddle() - widget.getHeight() / 2);
                        widget.extractRenderState(graphics, mouseX, mouseY, a);
                        partX += widget.getWidth() + GAP;
                    }
                    case TextPart(Supplier<Component> text, int textColor, int width) -> {
                        graphics.text(font, text.get(), partX, middle, textColor, false);
                        partX += width + GAP;
                    }
                    case IconPart(Identifier sprite) -> {
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, partX, this.getContentYMiddle() - 9, 18, 18);
                        partX += 18 + GAP;
                    }
                }
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.widgets;
        }
    }

    /**
     * Creates a section header row.
     *
     * @param title the title
     * @param parts optional widgets (e.g. an add button)
     * @return the row
     */
    public Row header(Component title, Part... parts) {
        return new Row(title, null, GOLD, 0, List.of(parts));
    }

    /**
     * Creates a field row.
     *
     * @param label the label
     * @param field the field key
     * @param parts the widgets and texts
     * @return the row
     */
    public Row field(Component label, String field, Part... parts) {
        return new Row(label, field, WHITE, 0, List.of(parts));
    }

    /**
     * Creates an indented entry row of a list field (one effect, one attribute).
     *
     * @param label the label
     * @param field the field key of the list
     * @param parts the widgets and texts
     * @return the row
     */
    public Row entry(Component label, @Nullable String field, Part... parts) {
        return new Row(label, field, WHITE, 8, List.of(parts));
    }

    /**
     * Creates a text line spanning the whole row.
     *
     * @param text   the text
     * @param color  the color
     * @param indent the indent
     * @return the row
     */
    public Row text(Component text, int color, int indent) {
        return new Row(text, null, color, indent, List.of());
    }
}
