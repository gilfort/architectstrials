package com.gilfort.architectstrials.client.browser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.gilfort.architectstrials.browser.BrowserNetwork;
import com.gilfort.architectstrials.browser.BrowserSnapshot;
import com.gilfort.architectstrials.browser.ChallengeEdits;
import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Edits the settings of one challenge structure of the managed datapack in the challenge browser (US-42): keeps the
 * {@link ChallengeDraft}, builds the form rows, tracks changed and invalid fields and the server's answer to a save.
 */
public final class ChallengeEditor {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFAAAAAA;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55FF55;
    private static final int SMALL = 16;

    /** Callbacks into the browser screen. */
    public interface Host {

        /** Rebuilds the form, e.g. after an entry was added or removed. */
        void rebuildForm();

        /**
         * Opens a picker and returns to the browser afterwards.
         *
         * @param picker the picker screen factory, given the browser as parent
         */
        void openPicker(Function<Screen, RegistryPickerScreen<?>> picker);

        /**
         * Returns the draw chance of the structure with a different weight.
         *
         * @param weight the weight
         * @return the chance in percent, formatted
         */
        String drawChance(int weight);
    }

    private final BrowserSnapshot.ChallengeEntry entry;
    private final ChallengeDraft draft;
    private final Host host;
    private Set<String> changed = Set.of();
    private Set<String> invalid = Set.of();
    private Map<String, Component> serverErrors = Map.of();
    private @Nullable Component status;
    private int statusColor = WHITE;
    private boolean saving;

    /**
     * Creates the editor of a challenge entry.
     *
     * @param entry the entry from the snapshot
     * @param host  the browser screen
     */
    public ChallengeEditor(BrowserSnapshot.ChallengeEntry entry, Host host) {
        this.entry = entry;
        this.draft = new ChallengeDraft(entry.metadata(), entry.revision());
        this.host = host;
        this.refresh();
    }

    /** @return the entry being edited */
    public BrowserSnapshot.ChallengeEntry entry() {
        return this.entry;
    }

    /** @return {@code true} if the draft differs from the stored metadata */
    public boolean dirty() {
        return !this.changed.isEmpty();
    }

    /** @return {@code true} if the draft can be saved: changed and every field valid */
    public boolean canSave() {
        return this.dirty() && this.invalid.isEmpty() && !this.saving;
    }

    /** @return {@code true} while a save is on its way */
    public boolean saving() {
        return this.saving;
    }

    /** Recomputes changed and invalid fields after an edit. */
    public void refresh() {
        Set<String> parseErrors = new LinkedHashSet<>();
        this.draft.build(parseErrors);
        this.invalid = parseErrors;
        this.changed = this.draft.changedFields();
    }

    /** @return the keys of changed fields */
    public Set<String> changed() {
        return this.changed;
    }

    /** @return error messages by field key: fields that cannot be parsed and the server's validation errors */
    public Map<String, Component> errors() {
        Map<String, Component> errors = new HashMap<>(this.serverErrors);
        for (String field : this.invalid) {
            errors.putIfAbsent(field, Component.translatable("gui.architectstrials.browser.edit.invalid"));
        }
        return errors;
    }

    /** Sends the draft to the server. */
    public void save() {
        Set<String> parseErrors = new LinkedHashSet<>();
        ChallengeStructure built = this.draft.build(parseErrors).orElse(null);
        if (built == null || Minecraft.getInstance().level == null) {
            return;
        }
        JsonElement json = ChallengeStructure.CODEC.encodeStart(Minecraft.getInstance().level.registryAccess()
                .createSerializationContext(JsonOps.INSTANCE), built).getOrThrow();
        this.saving = true;
        this.serverErrors = Map.of();
        this.setStatus(Component.translatable("gui.architectstrials.browser.edit.saving"), GREY);
        ClientPacketDistributor.sendToServer(new BrowserNetwork.Save(this.entry.id(), this.draft.revision(), json.toString()));
    }

    /**
     * Shows the server's answer to a save.
     *
     * @param status the status
     * @param errors the field errors
     */
    public void onSaveResult(ChallengeEdits.SaveStatus status, List<ChallengeEdits.FieldError> errors) {
        this.saving = status == ChallengeEdits.SaveStatus.SAVED;
        Map<String, Component> byField = new HashMap<>();
        errors.forEach(error -> byField.merge(error.field(), error.message(), (first, second) -> first));
        this.serverErrors = byField;
        switch (status) {
            case SAVED -> this.setStatus(Component.translatable("gui.architectstrials.browser.edit.saved"), GREEN);
            case INVALID -> this.setStatus(byField.getOrDefault("", Component.translatable("gui.architectstrials.browser.edit.rejected")), RED);
            case NOT_EDITABLE -> this.setStatus(Component.translatable("gui.architectstrials.browser.edit.not_editable"), RED);
            case CONFLICT -> this.setStatus(Component.translatable("gui.architectstrials.browser.edit.conflict"), RED);
        }
        this.host.rebuildForm();
    }

    private void setStatus(Component message, int color) {
        this.status = message;
        this.statusColor = color;
    }

    // ---------------------------------------------------------------- rows

    /**
     * Builds the form rows.
     *
     * @param list the list the rows belong to
     * @param font the font, for wrapping text rows
     * @return the rows
     */
    public List<ChallengeEditList.Row> rows(ChallengeEditList list, Font font) {
        List<ChallengeEditList.Row> rows = new ArrayList<>();
        if (this.status != null) {
            for (FormattedCharSequence line : font.split(this.status, list.textWidth())) {
                rows.add(list.text(Component.literal(asString(line)), this.statusColor, 0));
            }
        }
        rows.add(list.header(Component.translatable("gui.architectstrials.browser.edit.settings")));
        rows.add(list.field(label("name"), ChallengeDraft.NAME, widget(this.textField(160, this.draft.name, ChallengeEdits.MAX_NAME_LENGTH,
                Component.translatable("gui.architectstrials.browser.edit.name.hint"), value -> this.draft.name = value))));

        EditBox weight = this.textField(34, Integer.toString(this.draft.weight), 6, Component.empty(),
                value -> this.draft.weight = ChallengeDraft.parseInt(value).orElse(0));
        rows.add(list.field(label("weight"), ChallengeDraft.WEIGHT,
                widget(this.step(weight, -1, 1)), widget(weight), widget(this.step(weight, 1, 1)),
                text(() -> Component.translatable("gui.architectstrials.browser.edit.weight.chance", this.host.drawChance(this.draft.weight)), GREY, 120)));
        rows.add(list.field(label("rotation"), ChallengeDraft.ROTATION, widget(this.toggle(80, () -> this.draft.rotation,
                value -> this.draft.rotation = value))));
        rows.add(list.field(label("game_mode"), ChallengeDraft.GAME_MODE, widget(this.cycle(120,
                () -> this.draft.gameMode.getLongDisplayName(),
                () -> this.draft.gameMode = this.draft.gameMode == GameType.ADVENTURE ? GameType.SURVIVAL : GameType.ADVENTURE))));
        rows.add(list.field(label("time_limit"), ChallengeDraft.TIME_LIMIT, widget(this.textField(56, this.draft.timeLimit, 9,
                        Component.translatable("gui.architectstrials.browser.edit.default"), value -> this.draft.timeLimit = value)),
                text(() -> Component.translatable("gui.architectstrials.browser.edit.time.hint"), GREY, 140)));

        EditBox players = this.textField(34, Integer.toString(this.draft.maxPlayers), 4, Component.empty(),
                value -> this.draft.maxPlayers = Math.max(0, ChallengeDraft.parseInt(value).orElse(1)));
        rows.add(list.field(label("max_players"), ChallengeDraft.MAX_PLAYERS,
                widget(this.step(players, -1, 0)), widget(players), widget(this.step(players, 1, 0)),
                text(() -> this.draft.maxPlayers == 0 ? Component.translatable("gui.architectstrials.browser.detail.max_players.unlimited")
                        : Component.empty(), GREY, 60)));

        List<ChallengeEditList.Part> portal = new ArrayList<>();
        portal.add(widget(this.cycle(170, () -> Component.translatable("gui.architectstrials.browser.edit.portal."
                + this.draft.portalMode.name().toLowerCase(Locale.ROOT)), () -> {
                    ChallengeDraft.PortalMode[] modes = ChallengeDraft.PortalMode.values();
                    this.draft.portalMode = modes[(this.draft.portalMode.ordinal() + 1) % modes.length];
                    this.host.rebuildForm();
                })));
        if (this.draft.portalMode == ChallengeDraft.PortalMode.SECONDS) {
            portal.add(widget(this.textField(56, this.draft.portalSeconds, 9, Component.empty(), value -> this.draft.portalSeconds = value)));
        }
        rows.add(list.field(label("portal"), ChallengeDraft.PORTAL, portal.toArray(ChallengeEditList.Part[]::new)));
        rows.add(list.field(label("reentry"), ChallengeDraft.REENTRY, widget(this.toggle(80, () -> this.draft.reentry,
                value -> this.draft.reentry = value))));

        this.effectRows(list, rows, "player_effects", ChallengeDraft.PLAYER_EFFECTS, this.draft.playerEffects);
        this.effectRows(list, rows, "mob_effects", ChallengeDraft.MOB_EFFECTS, this.draft.mobEffects);
        this.attributeRows(list, rows);
        this.oreRows(list, rows);
        return rows;
    }

    private void effectRows(ChallengeEditList list, List<ChallengeEditList.Row> rows, String key, String field, List<ChallengeDraft.EffectDraft> effects) {
        rows.add(list.header(Component.translatable("gui.architectstrials.browser.edit." + key), widget(Button.builder(
                Component.translatable("gui.architectstrials.browser.edit.add_effect"), button -> this.host.openPicker(parent ->
                        new RegistryPickerScreen<>(parent, Component.translatable("gui.architectstrials.browser.edit.pick_effect"), effectOptions(), effect -> {
                            effects.add(ChallengeDraft.EffectDraft.added(effect));
                            this.edited();
                            this.host.rebuildForm();
                        }))).bounds(0, 0, 80, SMALL).build())));
        if (effects.isEmpty()) {
            rows.add(list.text(Component.translatable("gui.architectstrials.browser.none"), GREY, 8));
        }
        for (ChallengeDraft.EffectDraft effect : effects) {
            EditBox duration = this.textField(44, effect.duration, 9, Component.empty(), value -> effect.duration = value);
            duration.active = !effect.infinite;
            duration.setEditable(!effect.infinite);
            rows.add(list.entry(effect.effect.value().getDisplayName(), field,
                    new ChallengeEditList.IconPart(Hud.getMobEffectSprite(effect.effect)),
                    widget(this.small(Component.translatable("gui.architectstrials.browser.edit.decrease"), () -> effect.amplifier = Math.max(0, effect.amplifier - 1))),
                    text(() -> level(effect.amplifier), WHITE, 22),
                    widget(this.small(Component.translatable("gui.architectstrials.browser.edit.increase"), () -> effect.amplifier = Math.min(255, effect.amplifier + 1))),
                    widget(duration),
                    widget(this.cycle(70, () -> Component.translatable(effect.infinite ? "gui.architectstrials.browser.edit.infinite.on"
                            : "gui.architectstrials.browser.edit.infinite.off"), () -> {
                                effect.infinite = !effect.infinite;
                                this.host.rebuildForm();
                            })),
                    widget(this.remove(() -> effects.remove(effect)))));
        }
    }

    private void attributeRows(ChallengeEditList list, List<ChallengeEditList.Row> rows) {
        rows.add(list.header(Component.translatable("gui.architectstrials.browser.edit.player_attributes"), widget(Button.builder(
                Component.translatable("gui.architectstrials.browser.edit.add_attribute"), button -> this.host.openPicker(parent ->
                        new RegistryPickerScreen<>(parent, Component.translatable("gui.architectstrials.browser.edit.pick_attribute"), attributeOptions(),
                                attribute -> {
                                    this.draft.attributes.add(ChallengeDraft.AttributeDraft.added(attribute));
                                    this.edited();
                                    this.host.rebuildForm();
                                }))).bounds(0, 0, 80, SMALL).build())));
        if (this.draft.attributes.isEmpty()) {
            rows.add(list.text(Component.translatable("gui.architectstrials.browser.none"), GREY, 8));
        }
        for (ChallengeDraft.AttributeDraft attribute : this.draft.attributes) {
            EditBox amount = this.textField(52, attribute.amount, 12, Component.empty(), value -> attribute.amount = value);
            rows.add(list.entry(Component.translatable(attribute.attribute.value().getDescriptionId()), ChallengeDraft.ATTRIBUTES,
                    widget(this.decimalStep(amount, attribute, -1)), widget(amount), widget(this.decimalStep(amount, attribute, 1)),
                    widget(this.cycle(130, () -> Component.translatable("gui.architectstrials.browser.edit.operation."
                            + attribute.operation.getSerializedName()), () -> {
                                AttributeModifier.Operation[] operations = AttributeModifier.Operation.values();
                                attribute.operation = operations[(attribute.operation.ordinal() + 1) % operations.length];
                            })),
                    widget(this.remove(() -> this.draft.attributes.remove(attribute)))));
            rows.add(list.entry(Component.empty(), null, text(() -> attributeHint(attribute), GREY, 200)));
        }
    }

    private void oreRows(ChallengeEditList list, List<ChallengeEditList.Row> rows) {
        rows.add(list.header(Component.translatable("gui.architectstrials.browser.detail.ore_generation"), widget(this.toggle(80,
                () -> this.draft.ores, value -> {
                    this.draft.ores = value;
                    this.host.rebuildForm();
                }))));
        if (!this.draft.ores) {
            return;
        }
        rows.add(list.entry(label("ores.biome"), ChallengeDraft.ORES, widget(Button.builder(this.draft.oreBiome == null
                ? Component.translatable("gui.architectstrials.browser.edit.pick_biome")
                : Component.literal(this.draft.oreBiome.identifier().toString()), button -> this.host.openPicker(parent ->
                        new RegistryPickerScreen<>(parent, Component.translatable("gui.architectstrials.browser.edit.pick_biome"), biomeOptions(), biome -> {
                            this.draft.oreBiome = biome;
                            this.edited();
                            this.host.rebuildForm();
                        }))).bounds(0, 0, 180, 18).build())));
        rows.add(list.entry(label("ores.min_y"), ChallengeDraft.ORES, widget(this.textField(50, this.draft.oreMinY, 6,
                Component.translatable("gui.architectstrials.browser.edit.default"), value -> this.draft.oreMinY = value))));
        rows.add(list.entry(label("ores.max_y"), ChallengeDraft.ORES, widget(this.textField(50, this.draft.oreMaxY, 6,
                Component.translatable("gui.architectstrials.browser.edit.default"), value -> this.draft.oreMaxY = value))));
        rows.add(list.entry(label("ores.density"), ChallengeDraft.ORES,
                widget(this.small(Component.translatable("gui.architectstrials.browser.edit.decrease"), () -> this.draft.oreDensity =
                        Math.max(0.1F, Math.round(((this.draft.oreDensity == null ? 1.0F : this.draft.oreDensity) - 0.1F) * 10.0F) / 10.0F))),
                text(() -> this.draft.oreDensity == null ? Component.translatable("gui.architectstrials.browser.edit.default")
                        : Component.literal(ChallengeDraft.formatNumber(this.draft.oreDensity)), WHITE, 44),
                widget(this.small(Component.translatable("gui.architectstrials.browser.edit.increase"), () -> this.draft.oreDensity =
                        Math.round(((this.draft.oreDensity == null ? 1.0F : this.draft.oreDensity) + 0.1F) * 10.0F) / 10.0F)),
                widget(this.small(Component.translatable("gui.architectstrials.browser.edit.reset"), () -> this.draft.oreDensity = null))));
    }

    // ---------------------------------------------------------------- widgets

    private void edited() {
        this.refresh();
    }

    private EditBox textField(int width, String value, int maxLength, Component hint, Consumer<String> onChange) {
        EditBox box = new EditBox(Minecraft.getInstance().font, 0, 0, width, 18, Component.empty());
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setHint(hint);
        box.setResponder(text -> {
            onChange.accept(text);
            this.edited();
        });
        return box;
    }

    private Button step(EditBox box, int delta, int minimum) {
        return this.small(Component.translatable(delta < 0 ? "gui.architectstrials.browser.edit.decrease" : "gui.architectstrials.browser.edit.increase"),
                () -> box.setValue(Integer.toString(Math.max(minimum, ChallengeDraft.parseInt(box.getValue()).orElse(minimum) + delta))));
    }

    private Button decimalStep(EditBox box, ChallengeDraft.AttributeDraft attribute, int direction) {
        return this.small(Component.translatable(direction < 0 ? "gui.architectstrials.browser.edit.decrease" : "gui.architectstrials.browser.edit.increase"),
                () -> {
                    double value = ChallengeDraft.parseDouble(box.getValue()).orElse(0.0);
                    double base = attribute.attribute.value().getDefaultValue();
                    double step = attribute.operation == AttributeModifier.Operation.ADD_VALUE && Math.abs(base) >= 1.0 ? 1.0 : 0.01;
                    box.setValue(ChallengeDraft.formatNumber(Math.round((value + direction * step) * 100.0) / 100.0));
                });
    }

    private Button small(Component label, Runnable action) {
        return Button.builder(label, button -> {
            action.run();
            this.edited();
        }).bounds(0, 0, SMALL, SMALL).build();
    }

    private Button remove(Runnable action) {
        Button button = Button.builder(Component.translatable("gui.architectstrials.browser.edit.remove"), pressed -> {
            action.run();
            this.edited();
            this.host.rebuildForm();
        }).bounds(0, 0, SMALL, SMALL).build();
        button.setTooltip(Tooltip.create(Component.translatable("gui.architectstrials.browser.edit.remove.tooltip")));
        return button;
    }

    private Button toggle(int width, Supplier<Boolean> value, Consumer<Boolean> set) {
        return this.cycle(width, () -> Component.translatable(value.get() ? "options.on" : "options.off"), () -> set.accept(!value.get()));
    }

    private Button cycle(int width, Supplier<Component> label, Runnable next) {
        return Button.builder(label.get(), button -> {
            next.run();
            this.edited();
            button.setMessage(label.get());
        }).bounds(0, 0, width, 18).build();
    }

    private static ChallengeEditList.WidgetPart widget(AbstractWidget widget) {
        return new ChallengeEditList.WidgetPart(widget);
    }

    private static ChallengeEditList.TextPart text(Supplier<Component> text, int color, int width) {
        return new ChallengeEditList.TextPart(text, color, width);
    }

    private static Component label(String key) {
        return Component.translatable("gui.architectstrials.browser.edit.field." + key);
    }

    private static Component level(int amplifier) {
        return amplifier <= 9 ? Component.translatable("enchantment.level." + (amplifier + 1)) : Component.literal(Integer.toString(amplifier + 1));
    }

    private static Component attributeHint(ChallengeDraft.AttributeDraft attribute) {
        double base = attribute.attribute.value().getDefaultValue();
        double amount = ChallengeDraft.parseDouble(attribute.amount).orElse(0.0);
        double result = switch (attribute.operation) {
            case ADD_VALUE -> base + amount;
            case ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL -> base * (1.0 + amount);
        };
        MutableComponent hint = Component.translatable("gui.architectstrials.browser.edit.attribute_hint", ChallengeDraft.formatNumber(base),
                ChallengeDraft.formatNumber(result));
        if (base != 0.0) {
            double percent = (result - base) / Math.abs(base) * 100.0;
            hint.append(Component.translatable("gui.architectstrials.browser.edit.attribute_hint.percent",
                    (percent > 0 ? "+" : "") + ChallengeDraft.formatNumber(Math.round(percent * 10.0) / 10.0)));
        }
        return hint;
    }

    private static String asString(FormattedCharSequence sequence) {
        StringBuilder builder = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }

    // ---------------------------------------------------------------- picker options

    private static List<RegistryPickerScreen.Option<Holder<MobEffect>>> effectOptions() {
        return BuiltInRegistries.MOB_EFFECT.listElements().<RegistryPickerScreen.Option<Holder<MobEffect>>>map(holder ->
                new RegistryPickerScreen.Option<>(holder, holder.key().identifier(), holder.value().getDisplayName(), Hud.getMobEffectSprite(holder))).toList();
    }

    private static List<RegistryPickerScreen.Option<Holder<Attribute>>> attributeOptions() {
        return BuiltInRegistries.ATTRIBUTE.listElements().<RegistryPickerScreen.Option<Holder<Attribute>>>map(holder ->
                new RegistryPickerScreen.Option<>(holder, holder.key().identifier(), Component.translatable(holder.value().getDescriptionId()), null)).toList();
    }

    private static List<RegistryPickerScreen.Option<ResourceKey<Biome>>> biomeOptions() {
        if (Minecraft.getInstance().level == null) {
            return List.of();
        }
        HolderLookup.RegistryLookup<Biome> biomes = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BIOME);
        return biomes.listElements().<RegistryPickerScreen.Option<ResourceKey<Biome>>>map(holder -> {
            Identifier id = holder.key().identifier();
            return new RegistryPickerScreen.Option<>(holder.key(), id, Component.translatableWithFallback(id.toLanguageKey("biome"), id.toString()), null);
        }).toList();
    }
}
