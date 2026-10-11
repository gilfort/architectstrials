package com.gilfort.architectstrials.client.browser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Picks one entry of a registry — mob effects, attributes, biomes, modded entries included — with a search field
 * (US-42). Each row shows an optional icon, the translated name and the id in small print; a click picks the entry
 * and returns to the parent screen.
 *
 * @param <T> the entry type
 */
public class RegistryPickerScreen<T> extends Screen {

    private static final int ROW = 22;
    private static final int WIDTH = 260;
    private static final int ICON = 18;
    private static final int PANEL = 0xE0101010;
    private static final int BORDER = 0xFF555555;
    private static final int HOVER = 0x30FFFFFF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFF888888;

    /**
     * An entry of the picker.
     *
     * @param value  the value handed to the callback
     * @param id     the registry id
     * @param name   the translated name
     * @param sprite an icon sprite, or {@code null}
     * @param <T>    the value type
     */
    public record Option<T>(T value, Identifier id, Component name, @Nullable Identifier sprite) {
    }

    private final Screen parent;
    private final List<Option<T>> options;
    private final Consumer<T> onPick;
    private List<Option<T>> filtered;
    private int scroll;
    private @Nullable EditBox search;

    /**
     * Creates the picker.
     *
     * @param parent  the screen to return to
     * @param title   the title
     * @param options the entries
     * @param onPick  called with the picked value (after returning to the parent)
     */
    public RegistryPickerScreen(Screen parent, Component title, List<Option<T>> options, Consumer<T> onPick) {
        super(title);
        this.parent = parent;
        List<Option<T>> sorted = new ArrayList<>(options);
        sorted.sort(Comparator.comparing(option -> option.name().getString().toLowerCase(Locale.ROOT)));
        this.options = List.copyOf(sorted);
        this.filtered = this.options;
        this.onPick = onPick;
    }

    private int left() {
        return (this.width - WIDTH) / 2;
    }

    private int listTop() {
        return 52;
    }

    private int listBottom() {
        return this.height - 34;
    }

    @Override
    protected void init() {
        String query = this.search == null ? "" : this.search.getValue();
        this.search = new EditBox(this.font, this.left(), 28, WIDTH, 18, Component.translatable("gui.architectstrials.browser.search"));
        this.search.setHint(Component.translatable("gui.architectstrials.browser.search"));
        this.search.setValue(query);
        this.search.setResponder(this::filter);
        this.addRenderableWidget(this.search);
        this.setInitialFocus(this.search);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, 20).build());
        this.filter(query);
    }

    private void filter(String query) {
        String filter = query.trim().toLowerCase(Locale.ROOT);
        this.filtered = filter.isEmpty() ? this.options : this.options.stream()
                .filter(option -> option.name().getString().toLowerCase(Locale.ROOT).contains(filter) || option.id().toString().contains(filter))
                .toList();
        this.scroll = 0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(this.font, this.title, this.width / 2, 12, WHITE);
        int left = this.left();
        int top = this.listTop();
        int bottom = this.listBottom();
        graphics.fill(left, top, left + WIDTH, bottom, PANEL);
        graphics.outline(left, top, WIDTH, bottom - top, BORDER);
        this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.filtered.size() * ROW - (bottom - top)));
        graphics.enableScissor(left + 1, top + 1, left + WIDTH - 1, bottom - 1);
        for (int i = 0; i < this.filtered.size(); i++) {
            int y = top + i * ROW - this.scroll;
            if (y + ROW < top || y > bottom) {
                continue;
            }
            Option<T> option = this.filtered.get(i);
            if (mouseX >= left && mouseX < left + WIDTH && mouseY >= Math.max(top, y) && mouseY < Math.min(bottom, y + ROW)) {
                graphics.fill(left + 1, y, left + WIDTH - 1, y + ROW, HOVER);
            }
            int textX = left + 4;
            if (option.sprite() != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, option.sprite(), left + 3, y + 2, ICON, ICON);
                textX = left + 4 + ICON + 3;
            }
            graphics.text(this.font, option.name(), textX, y + 2, WHITE, false);
            graphics.text(this.font, option.id().toString(), textX, y + 12, GREY, false);
        }
        graphics.disableScissor();
        if (this.filtered.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.architectstrials.browser.picker.nothing"), this.width / 2, top + 8, GREY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int left = this.left();
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || event.x() < left || event.x() >= left + WIDTH
                || event.y() < this.listTop() || event.y() >= this.listBottom()) {
            return false;
        }
        int index = (int) ((event.y() - this.listTop() + this.scroll) / ROW);
        if (index < 0 || index >= this.filtered.size()) {
            return false;
        }
        T value = this.filtered.get(index).value();
        this.onClose();
        this.onPick.accept(value);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll -= (int) (scrollY * ROW * 2);
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
