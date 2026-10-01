package com.gilfort.architectstrials.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.loot.LootEntry;
import com.gilfort.architectstrials.loot.LootGroup;
import com.gilfort.architectstrials.loot.LootNetwork;
import com.gilfort.architectstrials.loot.LootSetup;
import com.gilfort.architectstrials.loot.LootSetupMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Screen of the {@link LootSetupMenu}. Tabs switch between the five groups and the consolation list (and, for trial
 * spawner markers, between the normal and the ominous setup). Each of the nine cells has an item slot (fixed item)
 * or a loot table button, a chance field (not on the consolation page) and a roll range. The loot table button
 * opens a searchable list of all loot tables inside the screen.
 */
public class LootSetupScreen extends AbstractContainerScreen<LootSetupMenu> {

    private static final Identifier TEXTURE = ArchitectsTrials.id("textures/gui/container/loot_setup.png");
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 272;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int LIST_ROWS = 12;
    private static final int LIST_ROW_HEIGHT = 11;

    private final List<Button> tableButtons = new ArrayList<>();
    private final List<EditBox> chanceFields = new ArrayList<>();
    private final List<EditBox> minFields = new ArrayList<>();
    private final List<EditBox> maxFields = new ArrayList<>();
    private final List<Button> tabs = new ArrayList<>();
    private Button variantButton;
    private EditBox search;
    private Button cancelPicking;
    private int pickingPosition = -1;
    private int listOffset;

    /**
     * Creates the screen.
     *
     * @param menu      the menu
     * @param inventory the player inventory
     * @param title     the title (names the loot source)
     */
    public LootSetupScreen(LootSetupMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TEXTURE_WIDTH, TEXTURE_HEIGHT - 4);
        this.inventoryLabelX = LootSetupMenu.INVENTORY_X;
        this.inventoryLabelY = LootSetupMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        CursorMemory.restore();
        this.tableButtons.clear();
        this.chanceFields.clear();
        this.minFields.clear();
        this.maxFields.clear();
        this.tabs.clear();
        for (int page = 0; page <= LootSetup.MAX_GROUPS; page++) {
            int buttonPage = page;
            boolean consolation = page == LootSetup.MAX_GROUPS;
            Component label = consolation ? Component.translatable("gui.architectstrials.loot_setup.consolation")
                    : Component.translatable("gui.architectstrials.loot_setup.group", page + 1);
            this.tabs.add(this.addRenderableWidget(Button.builder(label, button -> this.click(LootSetupMenu.BUTTON_PAGE + buttonPage))
                    .bounds(this.leftPos + 8 + page * 22, this.topPos + 17, consolation ? 72 : 20, 14).build()));
        }
        this.variantButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.click(LootSetupMenu.BUTTON_VARIANT))
                .bounds(this.leftPos + this.imageWidth - 64, this.topPos + 17, 56, 14).build());
        for (int position = 0; position < LootGroup.MAX_ENTRIES; position++) {
            int cellX = this.leftPos + LootSetupMenu.GRID_X + (position % 3) * LootSetupMenu.CELL_WIDTH;
            int cellY = this.topPos + LootSetupMenu.GRID_Y + (position / 3) * LootSetupMenu.CELL_HEIGHT;
            int buttonPosition = position;
            this.tableButtons.add(this.addRenderableWidget(Button.builder(Component.empty(), button -> this.startPicking(buttonPosition))
                    .bounds(cellX + 20, cellY + 1, 60, 16).build()));
            this.chanceFields.add(this.field(cellX + 1, cellY + 22, 26, position));
            this.minFields.add(this.field(cellX + 42, cellY + 22, 16, position));
            this.maxFields.add(this.field(cellX + 63, cellY + 22, 16, position));
        }
        this.search = this.addRenderableWidget(new EditBox(this.font, this.leftPos + 8, this.topPos + 34, this.imageWidth - 80, 14,
                Component.translatable("gui.architectstrials.loot_setup.search")));
        this.search.setResponder(value -> this.listOffset = 0);
        this.cancelPicking = this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.stopPicking())
                .bounds(this.leftPos + this.imageWidth - 68, this.topPos + 34, 60, 14).build());
        this.refreshWidgets();
    }

    private EditBox field(int x, int y, int width, int position) {
        EditBox field = new EditBox(this.font, x, y, width, 14, Component.translatable("gui.architectstrials.loot_setup.value"));
        field.setMaxLength(5);
        field.setResponder(value -> this.sendValues(position));
        return this.addRenderableWidget(field);
    }

    private void click(int buttonId) {
        if (this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    private LootGroup group() {
        return this.menu.setup().page(this.menu.page());
    }

    private void sendValues(int position) {
        Optional<LootEntry> entry = this.group().at(position);
        if (entry.isEmpty()) {
            return;
        }
        int chance = this.menu.consolationPage() ? entry.get().chance() : parsePercent(this.chanceFields.get(position).getValue()).orElse(-1);
        int min = parseInt(this.minFields.get(position).getValue()).orElse(-1);
        int max = parseInt(this.maxFields.get(position).getValue()).orElse(-1);
        if (chance < 0 || min < 1 || max < 1) {
            return;
        }
        if (chance != entry.get().chance() || min != entry.get().rollsMin() || max != entry.get().rollsMax()) {
            ClientPacketDistributor.sendToServer(new LootNetwork.EditValues(this.menu.containerId, position, chance, min, max));
        }
    }

    private static Optional<Integer> parsePercent(String value) {
        try {
            return Optional.of((int) Math.round(Math.clamp(Double.parseDouble(value.trim().replace(',', '.')), 0.0, 100.0) * 10.0));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> parseInt(String value) {
        try {
            return Optional.of(Math.clamp(Integer.parseInt(value.trim()), 1, LootEntry.MAX_ROLLS));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String formatPercent(int chance) {
        return chance % 10 == 0 ? Integer.toString(chance / 10) : String.format(Locale.ROOT, "%.1f", chance / 10.0);
    }

    private void startPicking(int position) {
        this.pickingPosition = position;
        this.listOffset = 0;
        this.search.setValue("");
        this.setFocused(this.search);
        this.refreshWidgets();
    }

    private void stopPicking() {
        this.pickingPosition = -1;
        this.refreshWidgets();
    }

    private List<Identifier> filteredTables() {
        String filter = this.search.getValue().trim().toLowerCase(Locale.ROOT);
        return this.menu.lootTables().stream().filter(id -> filter.isEmpty() || id.toString().contains(filter)).toList();
    }

    /** Shows the widgets of the current mode and page and fills the fields with the stored values. */
    private void refreshWidgets() {
        boolean picking = this.pickingPosition >= 0;
        LootGroup group = this.group();
        for (int page = 0; page < this.tabs.size(); page++) {
            this.tabs.get(page).visible = !picking;
            this.tabs.get(page).active = page != this.menu.page();
        }
        this.variantButton.visible = !picking && this.menu.hasOminousVariant();
        this.variantButton.setMessage(Component.translatable(this.menu.ominous()
                ? "gui.architectstrials.loot_setup.ominous" : "gui.architectstrials.loot_setup.normal"));
        for (int position = 0; position < LootGroup.MAX_ENTRIES; position++) {
            Optional<LootEntry> entry = group.at(position);
            boolean hasItem = entry.isPresent() && !entry.get().item().isEmpty();
            Button tableButton = this.tableButtons.get(position);
            tableButton.visible = !picking && !hasItem;
            tableButton.setMessage(entry.flatMap(LootEntry::table)
                    .map(key -> Component.literal(shortName(key.identifier())))
                    .orElseGet(() -> Component.translatable("gui.architectstrials.loot_setup.pick_table")));
            boolean showValues = !picking && entry.isPresent();
            sync(this.chanceFields.get(position), showValues && !this.menu.consolationPage(), entry.map(e -> formatPercent(e.chance())));
            sync(this.minFields.get(position), showValues, entry.map(e -> Integer.toString(e.rollsMin())));
            sync(this.maxFields.get(position), showValues, entry.map(e -> Integer.toString(e.rollsMax())));
        }
        this.search.visible = picking;
        this.cancelPicking.visible = picking;
    }

    private static void sync(EditBox field, boolean visible, Optional<String> stored) {
        field.visible = visible;
        if (visible && !field.isFocused() && stored.isPresent() && !stored.get().equals(field.getValue())) {
            field.setValue(stored.get());
        }
    }

    private static String shortName(Identifier id) {
        String path = id.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.length() > 10 ? name.substring(0, 9) + "…" : name;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.refreshWidgets();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.pickingPosition >= 0) {
            int row = (int) ((event.y() - (this.topPos + 52)) / LIST_ROW_HEIGHT);
            int x = (int) event.x() - this.leftPos;
            if (row >= 0 && row < LIST_ROWS && x >= 8 && x < this.imageWidth - 8) {
                List<Identifier> tables = this.filteredTables();
                int index = this.listOffset + row - 1;
                if (row == 0) {
                    this.pick(Optional.empty());
                    return true;
                }
                if (index >= 0 && index < tables.size()) {
                    this.pick(Optional.of(tables.get(index)));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void pick(Optional<Identifier> table) {
        ClientPacketDistributor.sendToServer(new LootNetwork.EditTable(this.menu.containerId, this.pickingPosition, table));
        this.stopPicking();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.pickingPosition >= 0) {
            int max = Math.max(0, this.filteredTables().size() - (LIST_ROWS - 1));
            this.listOffset = Math.clamp(this.listOffset - (int) Math.signum(scrollY) * 3, 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        if (this.pickingPosition >= 0) {
            return;
        }
        LootGroup group = this.group();
        Component header = this.menu.consolationPage() ? Component.translatable("gui.architectstrials.loot_setup.consolation_hint")
                : Component.translatable("gui.architectstrials.loot_setup.nothing", formatPercent(LootGroup.TOTAL - group.totalChance()));
        graphics.text(this.font, header, 8, 35, TEXT_COLOR, false);
        for (int position = 0; position < LootGroup.MAX_ENTRIES; position++) {
            if (group.at(position).isEmpty()) {
                continue;
            }
            int cellX = LootSetupMenu.GRID_X + (position % 3) * LootSetupMenu.CELL_WIDTH;
            int cellY = LootSetupMenu.GRID_Y + (position / 3) * LootSetupMenu.CELL_HEIGHT;
            if (!this.menu.consolationPage()) {
                graphics.text(this.font, Component.translatable("gui.architectstrials.equipment_list.percent"), cellX + 29, cellY + 25, TEXT_COLOR, false);
            }
            graphics.text(this.font, Component.translatable("gui.architectstrials.loot_setup.rolls"), cellX + 35, cellY + 25, TEXT_COLOR, false);
            graphics.text(this.font, Component.literal("–"), cellX + 59, cellY + 25, TEXT_COLOR, false);
        }
    }

    /**
     * Draws the loot table list on top of the slots while a table is being picked.
     */
    @Override
    protected void extractSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractSlots(graphics, mouseX, mouseY);
        if (this.pickingPosition >= 0) {
            graphics.nextStratum();
            this.extractPicker(graphics, mouseX, mouseY);
        }
    }

    private void extractPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(6, 50, this.imageWidth - 6, 52 + LIST_ROWS * LIST_ROW_HEIGHT, 0xFF202020);
        List<Identifier> tables = this.filteredTables();
        for (int row = 0; row < LIST_ROWS; row++) {
            int y = 53 + row * LIST_ROW_HEIGHT;
            boolean hovered = mouseY - this.topPos >= y - 1 && mouseY - this.topPos < y - 1 + LIST_ROW_HEIGHT
                    && mouseX - this.leftPos >= 8 && mouseX - this.leftPos < this.imageWidth - 8;
            Component text;
            if (row == 0) {
                text = Component.translatable("gui.architectstrials.loot_setup.no_table");
            } else {
                int index = this.listOffset + row - 1;
                if (index >= tables.size()) {
                    break;
                }
                text = Component.literal(tables.get(index).toString());
            }
            graphics.text(this.font, text, 10, y, hovered ? 0xFFFFFF55 : 0xFFE0E0E0, false);
        }
        graphics.text(this.font, Component.translatable("gui.architectstrials.loot_setup.matches", tables.size()), 8, 20, TEXT_COLOR, false);
    }

    @Override
    public void removed() {
        CursorMemory.remember();
        super.removed();
    }
}
