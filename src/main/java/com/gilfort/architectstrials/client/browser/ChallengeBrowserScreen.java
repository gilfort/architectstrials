package com.gilfort.architectstrials.client.browser;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import com.gilfort.architectstrials.browser.BrowserNetwork;
import com.gilfort.architectstrials.browser.BrowserSnapshot;
import com.gilfort.architectstrials.browser.ChallengeEdits;
import com.gilfort.architectstrials.browser.StructureStats;
import com.gilfort.architectstrials.editor.EditorState;
import com.gilfort.architectstrials.structure.ChallengeAttribute;
import com.gilfort.architectstrials.structure.ChallengeEffect;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.structure.ChallengeRunSettings;
import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.structure.OreGeneration;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The challenge browser (US-40), opened with {@code /at gui}. Three areas from left to right:
 * <ol>
 * <li>themes plus an entry for the sub structures, each with a problem counter,</li>
 * <li>the challenges of the selected theme grouped by tier (or all sub structures), with a search field,</li>
 * <li>the details of the selected entry, with the action buttons below them.</li>
 * </ol>
 * Selecting a theme or an entry collapses its column to a narrow strip with an arrow, which expands it again, so the
 * details get as much room as possible. The data is a {@link BrowserSnapshot} from the server, refreshed with the
 * refresh button and pushed by the server after every reload.
 * <p>
 * Challenges of the managed datapack are edited in place (US-42): the details become a form, changes stay a local
 * draft until "Save", and leaving the entry with unsaved changes asks first.
 */
public class ChallengeBrowserScreen extends Screen implements ChallengeEditor.Host {

    private static final int PAD = 6;
    private static final int HEADER = 26;
    private static final int STRIP = 14;
    private static final int THEMES_WIDTH = 130;
    private static final int ENTRIES_WIDTH = 180;
    private static final int ROW = 12;
    private static final int SEARCH_HEIGHT = 16;
    private static final int FOOTER = 28;
    private static final int LOCK_SIZE = 9;

    private static final int PANEL = 0xC0101010;
    private static final int BORDER = 0xFF555555;
    private static final int SELECTED = 0x60FFFFFF;
    private static final int HOVER = 0x30FFFFFF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFAAAAAA;
    private static final int RED = 0xFFFF5555;
    private static final int GOLD = 0xFFFFD866;
    private static final int LINK = 0xFF55FFFF;

    private static final Identifier LOCK_SPRITE = Identifier.withDefaultNamespace("widget/locked_button");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault());

    /** The browser currently shown (also while one of its dialogs is open), or {@code null}. */
    static @Nullable ChallengeBrowserScreen current;

    private static @Nullable Identifier rememberedTheme;
    private static boolean rememberedSubs;
    private static @Nullable Identifier rememberedEntry;

    private BrowserSnapshot snapshot;
    private @Nullable Identifier theme;
    private boolean subs;
    private @Nullable Identifier entry;
    private boolean themesCollapsed;
    private boolean entriesCollapsed;
    private final Set<Integer> collapsedTiers = new HashSet<>();
    private String query = "";
    private int themeScroll;
    private int entryScroll;
    private int detailScroll;
    private final List<Hit> hits = new ArrayList<>();
    private @Nullable EditBox search;
    private @Nullable ChallengeEditor editor;
    private @Nullable ChallengeEditList editList;
    private @Nullable Button saveButton;
    private @Nullable Button discardButton;

    /**
     * Creates the browser, restoring the selection of the last time it was open.
     *
     * @param snapshot the data from the server
     */
    public ChallengeBrowserScreen(BrowserSnapshot snapshot) {
        super(Component.translatable("gui.architectstrials.browser.title"));
        this.snapshot = snapshot;
        this.theme = rememberedTheme;
        this.subs = rememberedSubs;
        this.entry = rememberedEntry;
        this.validateSelection();
        this.themesCollapsed = this.theme != null || this.subs;
        this.entriesCollapsed = this.entry != null;
        current = this;
    }

    /**
     * Replaces the data, keeping the selection where the selected entries still exist.
     *
     * @param snapshot the new data
     */
    public void update(BrowserSnapshot snapshot) {
        this.snapshot = snapshot;
        this.validateSelection();
        if (this.entry == null) {
            this.entriesCollapsed = false;
        }
        if (this.editor != null) {
            BrowserSnapshot.ChallengeEntry fresh = this.entry == null || this.subs ? null : this.challengeEntry(this.entry);
            boolean sameEntry = fresh != null && fresh.id().equals(this.editor.entry().id()) && fresh.editable();
            // A saved draft (or one without changes) follows the new metadata; unsaved changes are kept.
            if (!sameEntry || this.editor.saving() || !this.editor.dirty()) {
                this.editor = null;
            }
        }
        this.rebuildWidgets();
    }

    /**
     * Shows the server's answer to a save.
     *
     * @param result the answer
     */
    public void onSaveResult(BrowserNetwork.SaveResult result) {
        if (this.editor == null || !this.editor.entry().id().equals(result.id())) {
            return;
        }
        this.editor.onSaveResult(result.status(), result.errors());
        if (result.status() == ChallengeEdits.SaveStatus.CONFLICT) {
            this.minecraft.gui.setScreen(new ConfirmScreen(reload -> {
                if (reload) {
                    this.editor = null;
                    ClientPacketDistributor.sendToServer(new BrowserNetwork.Refresh());
                }
                this.minecraft.gui.setScreen(this);
            }, Component.translatable("gui.architectstrials.browser.edit.conflict.title"),
                    Component.translatable("gui.architectstrials.browser.edit.conflict.message"),
                    Component.translatable("gui.architectstrials.browser.edit.conflict.reload"), Component.translatable("gui.cancel")));
        }
    }

    /**
     * Runs an action that leaves the edited entry; with unsaved changes it asks first.
     *
     * @param action the action
     */
    private void leaveDraft(Runnable action) {
        if (this.editor == null || !this.editor.dirty()) {
            this.editor = null;
            action.run();
            return;
        }
        this.minecraft.gui.setScreen(new ConfirmScreen(discard -> {
            this.minecraft.gui.setScreen(this);
            if (discard) {
                this.editor = null;
                action.run();
            }
        }, Component.translatable("gui.architectstrials.browser.edit.discard.title"),
                Component.translatable("gui.architectstrials.browser.edit.discard.message"),
                Component.translatable("gui.architectstrials.browser.edit.discard"), Component.translatable("gui.cancel")));
    }

    @Override
    public void rebuildForm() {
        this.rebuildWidgets();
    }

    @Override
    public void openPicker(Function<Screen, RegistryPickerScreen<?>> picker) {
        this.minecraft.gui.setScreen(picker.apply(this));
    }

    @Override
    public String drawChance(int weight) {
        if (this.editor == null) {
            return "-";
        }
        ChallengeStructure edited = this.editor.entry().metadata();
        BrowserSnapshot.ThemeEntry theme = this.themeEntry(edited.theme());
        int others = theme == null ? 0 : theme.challenges().stream()
                .filter(other -> other.metadata().tier() == edited.tier() && !other.id().equals(this.editor.entry().id()))
                .mapToInt(other -> other.metadata().weight()).sum();
        return weight < 1 ? "-" : formatNumber(100.0 * weight / (others + weight));
    }

    /**
     * Asks whether the non-empty editor may be cleared to load a structure.
     *
     * @param busy the server's answer to a load request
     */
    public void confirmClear(BrowserNetwork.Busy busy) {
        MutableComponent message = Component.translatable("gui.architectstrials.browser.load.not_empty");
        message.append("\n\n").append(busy.builders().isEmpty()
                ? Component.translatable("gui.architectstrials.browser.load.no_builders")
                : Component.translatable("gui.architectstrials.browser.load.builders", String.join(", ", busy.builders())));
        message.append("\n").append(busy.last()
                .map(last -> Component.translatable("gui.architectstrials.browser.load.last", refLabel(last)))
                .orElse(Component.translatable("gui.architectstrials.browser.load.last_unknown")));
        this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                ClientPacketDistributor.sendToServer(new BrowserNetwork.Load(busy.structure(), true));
            }
            this.minecraft.gui.setScreen(this);
        }, Component.translatable("gui.architectstrials.browser.load.confirm_title", refLabel(busy.structure())), message,
                Component.translatable("gui.architectstrials.browser.load.clear_and_load"), Component.translatable("gui.cancel")));
    }

    private void validateSelection() {
        if (this.theme != null && this.themeEntry(this.theme) == null) {
            this.theme = null;
        }
        if (this.entry != null && (this.subs ? this.subEntry(this.entry) == null : this.challengeEntry(this.entry) == null)) {
            this.entry = null;
        }
        if (this.theme == null && !this.subs) {
            this.entry = null;
        }
    }

    // ---------------------------------------------------------------- layout

    private int themesX() {
        return PAD;
    }

    private int themesWidth() {
        return this.themesCollapsed ? STRIP : THEMES_WIDTH;
    }

    private int entriesX() {
        return this.themesX() + this.themesWidth() + PAD;
    }

    private boolean entriesVisible() {
        return this.theme != null || this.subs;
    }

    private int entriesWidth() {
        if (!this.entriesVisible()) {
            return 0;
        }
        return this.entriesCollapsed ? STRIP : ENTRIES_WIDTH;
    }

    private int detailsX() {
        return this.entriesVisible() ? this.entriesX() + this.entriesWidth() + PAD : this.entriesX();
    }

    private int detailsWidth() {
        return this.width - PAD - this.detailsX();
    }

    private int top() {
        return HEADER;
    }

    private int bottom() {
        return this.height - PAD;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.architectstrials.browser.refresh"),
                        button -> ClientPacketDistributor.sendToServer(new BrowserNetwork.Refresh()))
                .bounds(this.width - PAD - 70, 4, 70, 18).build());
        this.search = null;
        if (this.entriesVisible() && !this.entriesCollapsed) {
            this.search = new EditBox(this.font, this.entriesX() + 2, this.top() + 2, this.entriesWidth() - 4, SEARCH_HEIGHT,
                    Component.translatable("gui.architectstrials.browser.search"));
            this.search.setHint(Component.translatable("gui.architectstrials.browser.search"));
            this.search.setValue(this.query);
            this.search.setResponder(value -> {
                this.query = value;
                this.entryScroll = 0;
            });
            this.addRenderableWidget(this.search);
        }
        // Rebuilding the form (portal mode, added effect, …) must not jump back to the top of the same challenge.
        Identifier previousEdited = this.editor == null ? null : this.editor.entry().id();
        double previousScroll = this.editList == null ? 0.0 : this.editList.scrollAmount();
        this.editList = null;
        this.saveButton = null;
        this.discardButton = null;
        Optional<EditorState.StructureRef> selected = this.selectedRef();
        if (selected.isEmpty()) {
            return;
        }
        BrowserSnapshot.ChallengeEntry challenge = this.subs ? null : this.challengeEntry(selected.get().id());
        BrowserSnapshot.SubEntry sub = this.subs ? this.subEntry(selected.get().id()) : null;
        if (challenge != null && challenge.editable()) {
            if (this.editor == null || !this.editor.entry().id().equals(challenge.id())) {
                this.editor = new ChallengeEditor(challenge, this);
            }
            this.editList = new ChallengeEditList(this.minecraft, this.detailsX() + 1, this.top() + 2, this.detailsWidth() - 2,
                    this.detailContentHeight(), this.editor::changed, this.editor::errors);
            List<ChallengeEditList.Row> rows = new ArrayList<>(this.editor.rows(this.editList, this.font));
            List<Line> template = new ArrayList<>();
            template.add(Line.BLANK);
            this.statsLines(template, challenge.stats());
            this.problemLines(template, challenge.problems());
            for (Line line : template) {
                if (line.text() == null) {
                    rows.add(this.editList.text(Component.empty(), WHITE, 0));
                    continue;
                }
                for (FormattedCharSequence part : this.font.split(line.text(), Math.max(20, this.editList.textWidth() - line.indent()))) {
                    rows.add(this.editList.text(Component.literal(plain(part)), line.color(), line.indent()));
                }
            }
            this.editList.setRows(rows);
            if (challenge.id().equals(previousEdited)) {
                this.editList.setScrollAmount(previousScroll);
            }
            this.addRenderableWidget(this.editList);
        } else {
            this.editor = null;
        }

        int x = this.detailsX() + 4;
        int y = this.bottom() - FOOTER + 4;
        if (this.editor != null) {
            ChallengeEditor current = this.editor;
            this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.architectstrials.browser.edit.save"),
                    button -> current.save()).bounds(x, y, 56, 20).build());
            this.discardButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.architectstrials.browser.edit.discard"),
                    button -> {
                        this.editor = null;
                        this.rebuildWidgets();
                    }).bounds(x + 58, y, 62, 20).build());
            x += 128;
        }
        Button load = Button.builder(Component.translatable("gui.architectstrials.browser.load"),
                        button -> this.leaveDraft(() -> ClientPacketDistributor.sendToServer(new BrowserNetwork.Load(selected.get(), false))))
                .bounds(x, y, 100, 20).build();
        load.active = this.selectedStats().map(StructureStats::found).orElse(false);
        load.setTooltip(Tooltip.create(Component.translatable("gui.architectstrials.browser.load.tooltip")));
        this.addRenderableWidget(load);
        x += 102;
        if (challenge != null) {
            Button testRun = Button.builder(Component.translatable("gui.architectstrials.browser.test_run"),
                            button -> this.leaveDraft(() -> ClientPacketDistributor.sendToServer(new BrowserNetwork.TestRun(challenge.id()))))
                    .bounds(x, y, 70, 20).build();
            testRun.active = challenge.stats().found() && this.themeEntry(challenge.metadata().theme()) != null
                    && this.themeEntry(challenge.metadata().theme()).registered();
            testRun.setTooltip(Tooltip.create(Component.translatable("gui.architectstrials.browser.test_run.tooltip")));
            this.addRenderableWidget(testRun);
            x += 72;
        }
        boolean deletable = challenge != null ? challenge.editable() : sub != null && sub.editable();
        if (deletable) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.architectstrials.browser.delete"),
                    button -> this.confirmDelete(selected.get())).bounds(x, y, 56, 20).build());
        }
    }

    /** Asks before deleting a structure, naming what is affected. */
    private void confirmDelete(EditorState.StructureRef ref) {
        MutableComponent message = Component.translatable("gui.architectstrials.browser.delete.message", refLabel(ref));
        if (ref.sub()) {
            BrowserSnapshot.SubEntry sub = this.subEntry(ref.id());
            if (sub != null && !sub.usedBy().isEmpty()) {
                message.append("\n\n").append(Component.translatable("gui.architectstrials.browser.delete.used_by",
                        String.join(", ", sub.usedBy().stream().map(Identifier::toString).toList())));
            }
        } else {
            BrowserSnapshot.ChallengeEntry challenge = this.challengeEntry(ref.id());
            BrowserSnapshot.ThemeEntry theme = challenge == null ? null : this.themeEntry(challenge.metadata().theme());
            if (challenge != null && theme != null
                    && theme.challenges().stream().filter(other -> other.metadata().tier() == challenge.metadata().tier()).count() == 1) {
                message.append("\n\n").append(Component.translatable("gui.architectstrials.browser.delete.last_in_tier",
                        challenge.metadata().tier(), themeName(theme.id())));
            }
        }
        this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                this.editor = null;
                ClientPacketDistributor.sendToServer(new BrowserNetwork.Delete(ref));
            }
            this.minecraft.gui.setScreen(this);
        }, Component.translatable("gui.architectstrials.browser.delete.title"), message,
                Component.translatable("gui.architectstrials.browser.delete"), Component.translatable("gui.cancel")));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.saveButton != null && this.editor != null) {
            this.saveButton.active = this.editor.canSave();
        }
        if (this.discardButton != null && this.editor != null) {
            this.discardButton.active = this.editor.dirty();
        }
    }

    // ---------------------------------------------------------------- selection

    private void selectTheme(Identifier id) {
        if (this.editor != null && this.editor.dirty() && !this.isEdited(id)) {
            this.leaveDraft(() -> this.selectTheme(id));
            return;
        }
        this.theme = id;
        this.subs = false;
        this.entry = null;
        this.themesCollapsed = true;
        this.entriesCollapsed = false;
        this.entryScroll = 0;
        this.detailScroll = 0;
        this.collapsedTiers.clear();
        this.remember();
    }

    private void selectSubs() {
        if (this.editor != null && this.editor.dirty() && !this.isEdited(null)) {
            this.leaveDraft(() -> this.selectSubs());
            return;
        }
        this.theme = null;
        this.subs = true;
        this.entry = null;
        this.themesCollapsed = true;
        this.entriesCollapsed = false;
        this.entryScroll = 0;
        this.detailScroll = 0;
        this.remember();
    }

    private void selectChallenge(Identifier id) {
        if (this.editor != null && this.editor.dirty() && !this.isEdited(id)) {
            this.leaveDraft(() -> this.selectChallenge(id));
            return;
        }
        BrowserSnapshot.ChallengeEntry challenge = this.challengeEntry(id);
        if (challenge == null) {
            return;
        }
        this.theme = challenge.metadata().theme();
        this.subs = false;
        this.entry = id;
        this.themesCollapsed = true;
        this.entriesCollapsed = true;
        this.detailScroll = 0;
        this.remember();
    }

    private void selectSub(Identifier id) {
        if (this.editor != null && this.editor.dirty() && !this.isEdited(id)) {
            this.leaveDraft(() -> this.selectSub(id));
            return;
        }
        if (this.subEntry(id) == null) {
            return;
        }
        this.theme = null;
        this.subs = true;
        this.entry = id;
        this.themesCollapsed = true;
        this.entriesCollapsed = true;
        this.detailScroll = 0;
        this.remember();
    }

    private boolean isEdited(@Nullable Identifier id) {
        return this.editor != null && id != null && this.editor.entry().id().equals(id);
    }

    private void remember() {
        rememberedTheme = this.theme;
        rememberedSubs = this.subs;
        rememberedEntry = this.entry;
        this.rebuildWidgets();
    }

    private Optional<EditorState.StructureRef> selectedRef() {
        return this.entry == null ? Optional.empty() : Optional.of(new EditorState.StructureRef(this.subs, this.entry));
    }

    private Optional<StructureStats> selectedStats() {
        if (this.entry == null) {
            return Optional.empty();
        }
        if (this.subs) {
            return Optional.ofNullable(this.subEntry(this.entry)).map(BrowserSnapshot.SubEntry::stats);
        }
        return Optional.ofNullable(this.challengeEntry(this.entry)).map(BrowserSnapshot.ChallengeEntry::stats);
    }

    private BrowserSnapshot.@Nullable ThemeEntry themeEntry(Identifier id) {
        return this.snapshot.themes().stream().filter(theme -> theme.id().equals(id)).findFirst().orElse(null);
    }

    private BrowserSnapshot.@Nullable ChallengeEntry challengeEntry(Identifier id) {
        return this.snapshot.themes().stream().flatMap(theme -> theme.challenges().stream())
                .filter(challenge -> challenge.id().equals(id)).findFirst().orElse(null);
    }

    private BrowserSnapshot.@Nullable SubEntry subEntry(Identifier id) {
        return this.snapshot.subStructures().stream().filter(sub -> sub.id().equals(id)).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.hits.clear();
        graphics.text(this.font, this.title, PAD, 9, WHITE, true);
        this.renderThemes(graphics, mouseX, mouseY);
        if (this.entriesVisible()) {
            this.renderEntries(graphics, mouseX, mouseY);
        }
        this.renderDetails(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        for (Hit hit : this.hits) {
            if (hit.contains(mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(this.font, hit.tooltip(), mouseX, mouseY);
            }
        }
    }

    private void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, PANEL);
        graphics.outline(x, y, w, h, BORDER);
    }

    /** Draws a collapsed column: a strip with an arrow that expands it again. */
    private void strip(GuiGraphicsExtractor graphics, int x, int mouseX, int mouseY, Component tooltip) {
        int y = this.top();
        int h = this.bottom() - y;
        this.panel(graphics, x, y, STRIP, h);
        if (mouseX >= x && mouseX < x + STRIP && mouseY >= y && mouseY < y + h) {
            graphics.fill(x + 1, y + 1, x + STRIP - 1, y + h - 1, HOVER);
        }
        graphics.centeredText(this.font, Component.translatable("gui.architectstrials.browser.expand"), x + STRIP / 2, y + 4, WHITE);
        this.hits.add(new Hit(x, y, x + STRIP, y + h, List.of(tooltip)));
    }

    private void renderThemes(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.themesX();
        if (this.themesCollapsed) {
            this.strip(graphics, x, mouseX, mouseY, Component.translatable("gui.architectstrials.browser.themes"));
            return;
        }
        int y = this.top();
        this.panel(graphics, x, y, this.themesWidth(), this.bottom() - y);
        graphics.text(this.font, Component.translatable("gui.architectstrials.browser.themes"), x + 4, y + 4, GOLD, true);
        this.themeScroll = this.renderRows(graphics, this.themeRows(), x, this.themeListTop(), this.themesWidth(),
                this.bottom() - this.themeListTop(), this.themeScroll, mouseX, mouseY);
    }

    private int themeListTop() {
        return this.top() + 16;
    }

    private int entryListTop() {
        return this.top() + SEARCH_HEIGHT + 6;
    }

    /** @return the rows of the theme column */
    private List<ListRow> themeRows() {
        List<ListRow> rows = new ArrayList<>();
        for (BrowserSnapshot.ThemeEntry entry : this.snapshot.themes()) {
            rows.add(new ListRow(themeName(entry.id()), entry.registered() ? WHITE : RED, 0,
                    entry.id().equals(this.theme) && !this.subs, false, entry.problemCount(),
                    Component.translatable("gui.architectstrials.browser.challenge_count", entry.challenges().size()),
                    () -> this.selectTheme(entry.id()), List.of(Component.literal(entry.id().toString()))));
        }
        int subProblems = (int) this.snapshot.subStructures().stream().filter(sub -> !sub.problems().isEmpty()).count();
        rows.add(ListRow.SEPARATOR);
        rows.add(new ListRow(Component.translatable("gui.architectstrials.browser.sub_structures"), WHITE, 0, this.subs, false, subProblems,
                Component.translatable("gui.architectstrials.browser.challenge_count", this.snapshot.subStructures().size()),
                this::selectSubs, List.of()));
        return rows;
    }

    /**
     * Returns the display name of a theme: its translation, or only the path of its id if the resource packs
     * provide none (the full id is in the tooltip).
     */
    private static Component themeName(Identifier id) {
        String key = id.toLanguageKey("dimension");
        return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(id.getPath());
    }

    private void renderEntries(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.entriesX();
        if (this.entriesCollapsed) {
            this.strip(graphics, x, mouseX, mouseY, this.subs ? Component.translatable("gui.architectstrials.browser.sub_structures")
                    : Component.translatable("gui.architectstrials.browser.challenges"));
            return;
        }
        int y = this.top();
        this.panel(graphics, x, y, this.entriesWidth(), this.bottom() - y);
        this.entryScroll = this.renderRows(graphics, this.entryRows(), x, this.entryListTop(), this.entriesWidth(),
                this.bottom() - this.entryListTop(), this.entryScroll, mouseX, mouseY);
    }

    /** @return the rows of the entry column: the challenges of the selected theme by tier, or the sub structures */
    private List<ListRow> entryRows() {
        String filter = this.query.trim().toLowerCase(Locale.ROOT);
        List<ListRow> rows = new ArrayList<>();
        if (this.subs) {
            for (BrowserSnapshot.SubEntry sub : this.snapshot.subStructures()) {
                Component label = sub.metadata().name().<Component>map(Component::literal).orElse(Component.literal(sub.id().toString()));
                if (matches(filter, label, sub.id())) {
                    rows.add(new ListRow(label, WHITE, 0, sub.id().equals(this.entry), !sub.editable(), sub.problems().isEmpty() ? 0 : 1,
                            Component.empty(), () -> this.selectSub(sub.id()), List.of(Component.literal(sub.id().toString()))));
                }
            }
            return rows;
        }
        BrowserSnapshot.ThemeEntry theme = this.theme == null ? null : this.themeEntry(this.theme);
        Map<Integer, List<BrowserSnapshot.ChallengeEntry>> tiers = new TreeMap<>();
        if (theme != null) {
            for (BrowserSnapshot.ChallengeEntry challenge : theme.challenges()) {
                if (matches(filter, label(challenge), challenge.id())) {
                    tiers.computeIfAbsent(challenge.metadata().tier(), tier -> new ArrayList<>()).add(challenge);
                }
            }
        }
        tiers.forEach((tier, challenges) -> {
            boolean collapsed = this.collapsedTiers.contains(tier);
            rows.add(new ListRow(Component.translatable(collapsed ? "gui.architectstrials.browser.tier.collapsed"
                    : "gui.architectstrials.browser.tier.expanded", tier, challenges.size()), GOLD, 0, false, false, 0, Component.empty(), () -> {
                        if (!this.collapsedTiers.remove(tier)) {
                            this.collapsedTiers.add(tier);
                        }
                    }, List.of()));
            if (!collapsed) {
                for (BrowserSnapshot.ChallengeEntry challenge : challenges) {
                    rows.add(new ListRow(label(challenge), WHITE, 8, challenge.id().equals(this.entry), !challenge.editable(),
                            challenge.problems().isEmpty() ? 0 : 1, Component.empty(), () -> this.selectChallenge(challenge.id()),
                            List.of(Component.literal(challenge.id().toString()))));
                }
            }
        });
        if (tiers.isEmpty()) {
            rows.add(new ListRow(Component.translatable("gui.architectstrials.browser.no_challenges"), GREY, 0, false, false, 0,
                    Component.empty(), null, List.of()));
        }
        return rows;
    }

    private static boolean matches(String filter, Component label, Identifier id) {
        return filter.isEmpty() || label.getString().toLowerCase(Locale.ROOT).contains(filter) || id.toString().contains(filter);
    }

    private static Component label(BrowserSnapshot.ChallengeEntry challenge) {
        String path = challenge.id().getPath();
        return Component.literal(challenge.metadata().name().orElse(path.substring(path.lastIndexOf('/') + 1)));
    }

    /**
     * Draws a scrollable list of rows and registers their click areas.
     *
     * @return the scroll offset, clamped to the content
     */
    private int renderRows(GuiGraphicsExtractor graphics, List<ListRow> rows, int x, int y, int w, int h, int scroll, int mouseX, int mouseY) {
        int max = Math.max(0, rows.size() * ROW - h);
        scroll = Math.clamp(scroll, 0, max);
        graphics.enableScissor(x + 1, y, x + w - 1, y + h);
        for (int i = 0; i < rows.size(); i++) {
            ListRow row = rows.get(i);
            int rowY = y + i * ROW - scroll;
            if (rowY + ROW < y || rowY > y + h) {
                continue;
            }
            if (row == ListRow.SEPARATOR) {
                graphics.horizontalLine(x + 4, x + w - 5, rowY + ROW / 2, BORDER);
                continue;
            }
            boolean hovered = row.action() != null && mouseX >= x && mouseX < x + w && mouseY >= Math.max(y, rowY)
                    && mouseY < Math.min(y + h, rowY + ROW);
            if (row.selected()) {
                graphics.fill(x + 1, rowY, x + w - 1, rowY + ROW, SELECTED);
            } else if (hovered) {
                graphics.fill(x + 1, rowY, x + w - 1, rowY + ROW, HOVER);
            }
            int right = x + w - 4;
            if (row.problems() > 0) {
                Component problems = Component.translatable("gui.architectstrials.browser.problem_marker", row.problems());
                right -= this.font.width(problems);
                graphics.text(this.font, problems, right, rowY + 2, RED, false);
                right -= 3;
            }
            if (row.locked()) {
                right -= LOCK_SIZE;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCK_SPRITE, right, rowY + 1, LOCK_SIZE, LOCK_SIZE);
                right -= 3;
            }
            if (!row.extra().getString().isEmpty()) {
                right -= this.font.width(row.extra());
                graphics.text(this.font, row.extra(), right, rowY + 2, GREY, false);
                right -= 3;
            }
            int textX = x + 4 + row.indent();
            FormattedCharSequence text = clip(this.font, row.text(), right - textX);
            graphics.text(this.font, text, textX, rowY + 2, row.color(), false);
            List<Component> tooltip = new ArrayList<>(row.tooltip());
            if (row.locked()) {
                tooltip.add(Component.translatable("gui.architectstrials.browser.read_only"));
            }
            if (!tooltip.isEmpty()) {
                this.hits.add(new Hit(x, Math.max(y, rowY), x + w, Math.min(y + h, rowY + ROW), tooltip));
            }
        }
        graphics.disableScissor();
        return scroll;
    }

    private static FormattedCharSequence clip(net.minecraft.client.gui.Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        List<FormattedCharSequence> lines = font.split(text, Math.max(10, width));
        return lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.getFirst();
    }

    private int detailContentHeight() {
        return this.bottom() - this.top() - (this.entry != null ? FOOTER : 0) - 4;
    }

    /** @return the detail lines, wrapped to the width of the details area */
    private List<WrappedLine> detailWrapped() {
        int w = this.detailsWidth();
        List<WrappedLine> wrapped = new ArrayList<>();
        for (Line line : this.detailLines()) {
            if (line.text() == null) {
                wrapped.add(new WrappedLine(FormattedCharSequence.EMPTY, line, 0));
                continue;
            }
            for (FormattedCharSequence part : this.font.split(line.text(), Math.max(20, w - 12 - line.indent()))) {
                wrapped.add(new WrappedLine(part, line, this.font.width(part)));
            }
        }
        return wrapped;
    }

    private int detailLineY(int index) {
        return this.top() + 4 + index * (ROW - 1) - this.detailScroll;
    }

    private void renderDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.detailsX();
        int y = this.top();
        int w = this.detailsWidth();
        this.panel(graphics, x, y, w, this.bottom() - y);
        if (this.editList != null) {
            graphics.horizontalLine(x + 1, x + w - 2, this.bottom() - FOOTER, BORDER);
            return;
        }
        int contentHeight = this.detailContentHeight();
        List<WrappedLine> wrapped = this.detailWrapped();
        this.detailScroll = Math.clamp(this.detailScroll, 0, Math.max(0, wrapped.size() * (ROW - 1) - contentHeight));
        graphics.enableScissor(x + 1, y + 2, x + w - 1, y + 2 + contentHeight);
        for (int i = 0; i < wrapped.size(); i++) {
            WrappedLine line = wrapped.get(i);
            int lineY = this.detailLineY(i);
            if (lineY + ROW < y || lineY > y + contentHeight) {
                continue;
            }
            int lineX = x + 6 + line.line().indent();
            boolean hovered = line.line().action() != null && mouseX >= lineX && mouseX < lineX + line.width() && mouseY >= lineY
                    && mouseY < lineY + ROW - 1;
            graphics.text(this.font, line.text(), lineX, lineY, hovered ? WHITE : line.line().color(), false);
            if (!line.line().tooltip().isEmpty()) {
                this.hits.add(new Hit(lineX, Math.max(y, lineY), lineX + line.width(), Math.min(y + contentHeight, lineY + ROW - 1),
                        line.line().tooltip()));
            }
        }
        graphics.disableScissor();
        if (this.entry != null) {
            graphics.horizontalLine(x + 1, x + w - 2, this.bottom() - FOOTER, BORDER);
        }
    }

    // ---------------------------------------------------------------- details

    private List<Line> detailLines() {
        List<Line> lines = new ArrayList<>();
        if (this.entry != null && this.subs) {
            BrowserSnapshot.SubEntry sub = this.subEntry(this.entry);
            if (sub != null) {
                this.subDetails(lines, sub);
            }
        } else if (this.entry != null) {
            BrowserSnapshot.ChallengeEntry challenge = this.challengeEntry(this.entry);
            if (challenge != null) {
                this.challengeDetails(lines, challenge);
            }
        } else if (this.theme != null) {
            BrowserSnapshot.ThemeEntry theme = this.themeEntry(this.theme);
            if (theme != null) {
                this.themeDetails(lines, theme);
            }
        } else if (this.subs) {
            lines.add(Line.of(Component.translatable("gui.architectstrials.browser.sub_structures"), GOLD));
            lines.add(Line.of(Component.translatable("gui.architectstrials.browser.select_entry"), GREY));
        } else {
            lines.add(Line.of(Component.translatable("gui.architectstrials.browser.select_theme"), GREY));
        }
        return lines;
    }

    private void themeDetails(List<Line> lines, BrowserSnapshot.ThemeEntry theme) {
        lines.add(Line.of(new ChallengeTheme(theme.id()).displayName().copy().withStyle(style -> style.withBold(true)), GOLD));
        lines.add(Line.of(Component.literal(theme.id().toString()), GREY));
        if (!theme.registered()) {
            lines.add(Line.of(Component.translatable("gui.architectstrials.browser.theme.unregistered"), RED));
        }
        lines.add(Line.BLANK);
        Map<Integer, Integer> tiers = new TreeMap<>();
        theme.challenges().forEach(challenge -> tiers.merge(challenge.metadata().tier(), 1, Integer::sum));
        if (tiers.isEmpty()) {
            lines.add(Line.of(Component.translatable("gui.architectstrials.browser.no_challenges"), GREY));
        }
        tiers.forEach((tier, count) -> lines.add(Line.of(Component.translatable("gui.architectstrials.browser.theme.tier", tier, count), WHITE)));
        lines.add(Line.BLANK);
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.select_entry"), GREY));
    }

    private void challengeDetails(List<Line> lines, BrowserSnapshot.ChallengeEntry challenge) {
        ChallengeStructure metadata = challenge.metadata();
        lines.add(Line.of(label(challenge).copy().withStyle(style -> style.withBold(true)), GOLD));
        lines.add(Line.of(Component.literal(challenge.id().toString()), GREY));
        this.sourceLine(lines, challenge.source(), challenge.editable());
        lines.add(Line.BLANK);
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.theme", new ChallengeTheme(metadata.theme()).displayName()), WHITE));
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.tier", metadata.tier()), WHITE));
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.weight", metadata.weight(), this.drawChance(challenge),
                metadata.tier()), WHITE));
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.rotation",
                Component.translatable(metadata.rotation() ? "options.on" : "options.off")), WHITE));
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.game_mode", metadata.gameMode().getLongDisplayName()), WHITE));
        ChallengeRunSettings run = metadata.run();
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.time_limit", run.timeLimitSeconds()
                .<Component>map(seconds -> Component.literal(StringUtil.formatTickDuration(seconds * 20, 20.0F)))
                .orElse(Component.translatable("gui.architectstrials.browser.detail.time_limit.default"))), WHITE));
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.max_players", run.maxPlayers() == 0
                ? Component.translatable("gui.architectstrials.browser.detail.max_players.unlimited")
                : Component.literal(Integer.toString(run.maxPlayers()))), WHITE));
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.portal", switch (run.portalOpenSeconds()) {
            case 0 -> Component.translatable("gui.architectstrials.browser.detail.portal.first");
            case ScrollOptions.OPEN_UNTIL_TIME_LIMIT -> Component.translatable("gui.architectstrials.browser.detail.portal.time_limit");
            default -> Component.translatable("gui.architectstrials.browser.detail.portal.seconds", run.portalOpenSeconds());
        }), WHITE));
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.reentry",
                Component.translatable(run.allowReentry() ? "options.on" : "options.off")), WHITE));
        metadata.author().ifPresent(author -> lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.author", author), WHITE)));
        metadata.created().ifPresent(created -> lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.created",
                DATE_FORMAT.format(Instant.ofEpochMilli(created))), WHITE)));
        lines.add(Line.BLANK);
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.player_effects"), GOLD));
        if (metadata.playerEffects().isEmpty()) {
            lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.none"), GREY));
        }
        for (ChallengeEffect effect : metadata.playerEffects()) {
            lines.add(Line.indented(effectText(effect), WHITE));
        }
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.mob_effects"), GOLD));
        if (metadata.mobEffects().isEmpty()) {
            lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.none"), GREY));
        }
        for (ChallengeEffect effect : metadata.mobEffects()) {
            lines.add(Line.indented(effectText(effect), WHITE));
        }
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.player_attributes"), GOLD));
        if (metadata.playerAttributes().isEmpty()) {
            lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.none"), GREY));
        }
        for (ChallengeAttribute attribute : metadata.playerAttributes()) {
            lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.detail.attribute",
                    Component.translatable(attribute.attribute().value().getDescriptionId()), formatNumber(attribute.amount()),
                    attribute.operation().getSerializedName()), WHITE));
        }
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.ore_generation"), GOLD));
        Optional<OreGeneration> ores = metadata.oreGeneration();
        if (ores.isEmpty()) {
            lines.add(Line.indented(Component.translatable("options.off"), GREY));
        } else {
            OreGeneration ore = ores.get();
            Component auto = Component.translatable("gui.architectstrials.browser.detail.ores.default");
            lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.detail.ores.biome", ore.biome().identifier().toString()), WHITE));
            lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.detail.ores.height",
                    ore.minY().<Component>map(value -> Component.literal(Integer.toString(value))).orElse(auto),
                    ore.maxY().<Component>map(value -> Component.literal(Integer.toString(value))).orElse(auto)), WHITE));
            lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.detail.ores.density",
                    ore.density().<Component>map(value -> Component.literal(formatNumber(value))).orElse(auto)), WHITE));
        }
        lines.add(Line.BLANK);
        this.statsLines(lines, challenge.stats());
        this.problemLines(lines, challenge.problems());
    }

    private void subDetails(List<Line> lines, BrowserSnapshot.SubEntry sub) {
        lines.add(Line.of(Component.literal(sub.metadata().name().orElse(sub.id().toString())).withStyle(style -> style.withBold(true)), GOLD));
        lines.add(Line.of(Component.literal(sub.id().toString()), GREY));
        this.sourceLine(lines, sub.source(), sub.editable());
        sub.metadata().author().ifPresent(author -> lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.author", author), WHITE)));
        sub.metadata().created().ifPresent(created -> lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.created",
                DATE_FORMAT.format(Instant.ofEpochMilli(created))), WHITE)));
        lines.add(Line.BLANK);
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.used_by", sub.usedBy().size()), GOLD));
        if (sub.usedBy().isEmpty()) {
            lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.none"), GREY));
        }
        for (Identifier user : sub.usedBy()) {
            lines.add(Line.link(Component.literal(user.toString()), () -> this.selectChallenge(user)));
        }
        lines.add(Line.BLANK);
        this.statsLines(lines, sub.stats());
        this.problemLines(lines, sub.problems());
    }

    private void sourceLine(List<Line> lines, String source, boolean editable) {
        Component pack = Component.literal(source.isEmpty() ? "?" : source);
        if (editable) {
            lines.add(Line.of(Component.translatable("gui.architectstrials.browser.detail.source", pack), WHITE));
        } else {
            lines.add(new Line(Component.translatable("gui.architectstrials.browser.detail.source_read_only", pack), GREY, 0, null,
                    List.of(Component.translatable("gui.architectstrials.browser.read_only.tooltip", pack))));
        }
    }

    private void statsLines(List<Line> lines, StructureStats stats) {
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.stats"), GOLD));
        if (!stats.found()) {
            lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.stats.missing"), RED));
            return;
        }
        lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.stats.size", stats.sizeX(), stats.sizeY(), stats.sizeZ()), WHITE));
        lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.stats.player_spawns", stats.playerSpawns()),
                stats.playerSpawns() == 0 ? RED : WHITE));
        lines.add(Line.indented(stats.exitsNeedMobs() > 0
                ? Component.translatable("gui.architectstrials.browser.stats.exits_need_mobs", stats.exits(), stats.exitsNeedMobs())
                : Component.translatable("gui.architectstrials.browser.stats.exits", stats.exits()), WHITE));
        lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.stats.mobs"), WHITE));
        if (stats.mobs().isEmpty()) {
            lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.none"), GREY, 16));
        }
        for (StructureStats.MobCount mob : stats.mobs()) {
            Component name = BuiltInRegistries.ENTITY_TYPE.getOptional(mob.entity()).<Component>map(EntityType::getDescription)
                    .orElse(Component.literal(mob.entity().toString()));
            MutableComponent text = Component.translatable("gui.architectstrials.browser.stats.mob", name, mob.count(),
                    Component.translatable("gui.architectstrials.browser.marker." + mob.kind().getSerializedName()));
            if (mob.required()) {
                text.append(Component.translatable("gui.architectstrials.browser.stats.required"));
            }
            lines.add(Line.indented(text, WHITE, 16));
        }
        lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.stats.loot"), WHITE));
        if (stats.loot().isEmpty()) {
            lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.none"), GREY, 16));
        }
        for (StructureStats.LootCount loot : stats.loot()) {
            Component text = switch (loot.kind()) {
                case TABLE -> Component.translatable("gui.architectstrials.browser.stats.loot.table", loot.table().map(Identifier::toString).orElse("?"),
                        loot.count());
                case SETUP -> Component.translatable("gui.architectstrials.browser.stats.loot.setup", loot.count());
                case FILLED -> Component.translatable("gui.architectstrials.browser.stats.loot.filled", loot.count());
            };
            lines.add(Line.indented(text, WHITE, 16));
        }
        if (stats.vaults() > 0) {
            lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.stats.vaults", stats.vaults()), WHITE));
        }
        if (!stats.subStructures().isEmpty()) {
            lines.add(Line.indented(Component.translatable("gui.architectstrials.browser.stats.sub_structures"), WHITE));
            for (Identifier sub : stats.subStructures()) {
                if (this.subEntry(sub) != null) {
                    lines.add(new Line(Component.literal(sub.toString()), LINK, 16, () -> this.selectSub(sub), List.of()));
                } else {
                    lines.add(Line.indented(Component.literal(sub.toString()), RED, 16));
                }
            }
        }
    }

    private void problemLines(List<Line> lines, Map<String, Integer> problems) {
        lines.add(Line.BLANK);
        if (problems.isEmpty()) {
            lines.add(Line.of(Component.translatable("gui.architectstrials.browser.problems.none"), 0xFF55FF55));
            return;
        }
        lines.add(Line.of(Component.translatable("gui.architectstrials.browser.problems", problems.size()), RED));
        new TreeMap<>(problems).forEach((problem, count) -> lines.add(Line.indented(
                Component.translatable("commands.architectstrials.structure.validate.problem", problem, count), RED)));
    }

    /** @return the draw chance of a challenge within its tier pool, formatted in percent */
    private String drawChance(BrowserSnapshot.ChallengeEntry challenge) {
        BrowserSnapshot.ThemeEntry theme = this.themeEntry(challenge.metadata().theme());
        int total = theme == null ? 0 : theme.challenges().stream().filter(other -> other.metadata().tier() == challenge.metadata().tier())
                .mapToInt(other -> other.metadata().weight()).sum();
        return total <= 0 ? "-" : formatNumber(100.0 * challenge.metadata().weight() / total);
    }

    private static Component effectText(ChallengeEffect effect) {
        MutableComponent name = effect.effect().value().getDisplayName().copy();
        if (effect.amplifier() > 0) {
            name.append(" ").append(Component.translatable("potion.potency." + effect.amplifier()));
        }
        Component duration = effect.duration() == MobEffectInstance.INFINITE_DURATION
                ? Component.translatable("gui.architectstrials.browser.detail.whole_stay")
                : Component.literal(StringUtil.formatTickDuration(effect.duration(), 20.0F));
        return Component.translatable("gui.architectstrials.browser.detail.effect", name, duration);
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "");
    }

    private static Component refLabel(EditorState.StructureRef ref) {
        return Component.translatable(ref.sub() ? "gui.architectstrials.browser.ref.sub" : "gui.architectstrials.browser.ref.challenge",
                ref.id().toString());
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        Runnable action = this.actionAt(event.x(), event.y());
        if (action != null) {
            action.run();
            return true;
        }
        return false;
    }

    /**
     * Finds what a click at a position does, computed from the same layout the screen is drawn with.
     *
     * @return the action, or {@code null} if nothing is clickable there
     */
    private @Nullable Runnable actionAt(double mouseX, double mouseY) {
        if (mouseY < this.top() || mouseY >= this.bottom()) {
            return null;
        }
        if (mouseX >= this.themesX() && mouseX < this.themesX() + this.themesWidth()) {
            if (this.themesCollapsed) {
                return () -> {
                    this.themesCollapsed = false;
                    this.rebuildWidgets();
                };
            }
            return rowAction(this.themeRows(), mouseY - this.themeListTop() + this.themeScroll);
        }
        if (this.entriesVisible() && mouseX >= this.entriesX() && mouseX < this.entriesX() + this.entriesWidth()) {
            if (this.entriesCollapsed) {
                return () -> {
                    this.entriesCollapsed = false;
                    this.rebuildWidgets();
                };
            }
            return rowAction(this.entryRows(), mouseY - this.entryListTop() + this.entryScroll);
        }
        if (this.editList == null && mouseX >= this.detailsX() && mouseY < this.top() + 2 + this.detailContentHeight()) {
            List<WrappedLine> wrapped = this.detailWrapped();
            for (int i = 0; i < wrapped.size(); i++) {
                WrappedLine line = wrapped.get(i);
                int lineY = this.detailLineY(i);
                int lineX = this.detailsX() + 6 + line.line().indent();
                if (line.line().action() != null && mouseX >= lineX && mouseX < lineX + line.width() && mouseY >= lineY
                        && mouseY < lineY + ROW - 1) {
                    return line.line().action();
                }
            }
        }
        return null;
    }

    private static @Nullable Runnable rowAction(List<ListRow> rows, double offset) {
        if (offset < 0) {
            return null;
        }
        int index = (int) (offset / ROW);
        return index < rows.size() ? rows.get(index).action() : null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = (int) (-scrollY * ROW * 2);
        if (!this.themesCollapsed && mouseX >= this.themesX() && mouseX < this.themesX() + this.themesWidth()) {
            this.themeScroll += delta;
        } else if (this.entriesVisible() && !this.entriesCollapsed && mouseX >= this.entriesX() && mouseX < this.entriesX() + this.entriesWidth()) {
            this.entryScroll += delta;
        } else if (mouseX >= this.detailsX() && this.editList != null) {
            return this.editList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        } else if (mouseX >= this.detailsX()) {
            this.detailScroll += delta;
        } else {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return true;
    }

    @Override
    public void onClose() {
        this.leaveDraft(() -> {
            current = null;
            ClientPacketDistributor.sendToServer(new BrowserNetwork.Closed());
            super.onClose();
        });
    }

    private static String plain(FormattedCharSequence sequence) {
        StringBuilder builder = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- helper records

    /** An area with a tooltip, registered while rendering. */
    private record Hit(double x0, double y0, double x1, double y1, List<Component> tooltip) {

        boolean contains(double x, double y) {
            return x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
        }
    }

    /** One row of a list column. */
    private record ListRow(Component text, int color, int indent, boolean selected, boolean locked, int problems, Component extra,
            @Nullable Runnable action, List<Component> tooltip) {

        static final ListRow SEPARATOR = new ListRow(Component.empty(), 0, 0, false, false, 0, Component.empty(), null, List.of());
    }

    /** One logical line of the details, wrapped when drawn. A {@code null} text is an empty line. */
    private record Line(@Nullable Component text, int color, int indent, @Nullable Runnable action, List<Component> tooltip) {

        static final Line BLANK = new Line(null, 0, 0, null, List.of());

        static Line of(Component text, int color) {
            return new Line(text, color, 0, null, List.of());
        }

        static Line indented(Component text, int color) {
            return indented(text, color, 8);
        }

        static Line indented(Component text, int color, int indent) {
            return new Line(text, color, indent, null, List.of());
        }

        static Line link(Component text, Runnable action) {
            return new Line(text, LINK, 8, action, List.of());
        }
    }

    /** A wrapped part of a {@link Line}. */
    private record WrappedLine(FormattedCharSequence text, Line line, int width) {
    }
}
