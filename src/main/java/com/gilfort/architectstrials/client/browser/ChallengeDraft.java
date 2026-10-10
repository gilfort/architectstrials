package com.gilfort.architectstrials.client.browser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.structure.ChallengeAttribute;
import com.gilfort.architectstrials.structure.ChallengeEffect;
import com.gilfort.architectstrials.structure.ChallengeRunSettings;
import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.structure.OreGeneration;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * The client-side draft of the editable settings of a challenge structure (US-42). Text fields are kept as typed;
 * {@link #build} turns the draft into metadata and reports fields that cannot be parsed.
 */
public final class ChallengeDraft {

    /** Field keys, shared with the server's validation errors. */
    public static final String NAME = "name";
    /** Field key of the weight. */
    public static final String WEIGHT = "weight";
    /** Field key of the rotation flag. */
    public static final String ROTATION = "rotation";
    /** Field key of the game mode. */
    public static final String GAME_MODE = "game_mode";
    /** Field key of the time limit. */
    public static final String TIME_LIMIT = "time_limit";
    /** Field key of the player limit. */
    public static final String MAX_PLAYERS = "max_players";
    /** Field key of the portal open time. */
    public static final String PORTAL = "portal_open_seconds";
    /** Field key of the re-entry flag. */
    public static final String REENTRY = "allow_reentry";
    /** Field key of the player effects. */
    public static final String PLAYER_EFFECTS = "player_effects";
    /** Field key of the mob effects. */
    public static final String MOB_EFFECTS = "mob_effects";
    /** Field key of the player attributes. */
    public static final String ATTRIBUTES = "player_attributes";
    /** Field key of the ore generation. */
    public static final String ORES = "ore_generation";

    /** How long the entry portal stays open. */
    public enum PortalMode {
        /** Closes after the first pass-through. */
        FIRST,
        /** Open for a number of seconds. */
        SECONDS,
        /** Open until the time limit expires. */
        UNTIL_TIME_LIMIT
    }

    /** One effect of the draft. */
    public static final class EffectDraft {
        /** The effect type. */
        public Holder<MobEffect> effect;
        /** The amplifier ({@code 0} = level I). */
        public int amplifier;
        /** Whether the effect lasts the whole stay (players) or forever (mobs). */
        public boolean infinite;
        /** The duration as typed ({@code m:ss} or seconds). */
        public String duration;

        EffectDraft(Holder<MobEffect> effect, int amplifier, boolean infinite, String duration) {
            this.effect = effect;
            this.amplifier = amplifier;
            this.infinite = infinite;
            this.duration = duration;
        }

        static EffectDraft of(ChallengeEffect effect) {
            boolean infinite = effect.duration() == MobEffectInstance.INFINITE_DURATION;
            return new EffectDraft(effect.effect(), effect.amplifier(), infinite, infinite ? "1:00" : formatSeconds(effect.duration() / 20));
        }

        /**
         * Creates a new effect entry: level I, whole stay.
         *
         * @param effect the effect type
         * @return the entry
         */
        public static EffectDraft added(Holder<MobEffect> effect) {
            return new EffectDraft(effect, 0, true, "1:00");
        }
    }

    /** One attribute modifier of the draft. */
    public static final class AttributeDraft {
        /** The attribute. */
        public Holder<Attribute> attribute;
        /** The amount as typed. */
        public String amount;
        /** The operation. */
        public AttributeModifier.Operation operation;

        AttributeDraft(Holder<Attribute> attribute, String amount, AttributeModifier.Operation operation) {
            this.attribute = attribute;
            this.amount = amount;
            this.operation = operation;
        }

        /**
         * Creates a new attribute entry: {@code add_value 0}.
         *
         * @param attribute the attribute
         * @return the entry
         */
        public static AttributeDraft added(Holder<Attribute> attribute) {
            return new AttributeDraft(attribute, "0", AttributeModifier.Operation.ADD_VALUE);
        }
    }

    /** The display name ({@code ""} = none). */
    public String name;
    /** The draw weight. */
    public int weight;
    /** Random rotation and mirroring. */
    public boolean rotation;
    /** The game mode. */
    public GameType gameMode;
    /** The time limit as typed ({@code ""} = the configured default). */
    public String timeLimit;
    /** The player limit ({@code 0} = unlimited). */
    public int maxPlayers;
    /** The portal mode. */
    public PortalMode portalMode;
    /** The portal open time as typed ({@link PortalMode#SECONDS} only). */
    public String portalSeconds;
    /** Re-entry after leaving without completing. */
    public boolean reentry;
    /** Effects every player gets on entry. */
    public final List<EffectDraft> playerEffects = new ArrayList<>();
    /** Effects every mob gets. */
    public final List<EffectDraft> mobEffects = new ArrayList<>();
    /** Attribute modifiers of the players. */
    public final List<AttributeDraft> attributes = new ArrayList<>();
    /** Whether natural ores are generated. */
    public boolean ores;
    /** The biome of the ore features. */
    public @Nullable ResourceKey<Biome> oreBiome;
    /** The simulated height of the lowest layer as typed ({@code ""} = default). */
    public String oreMinY;
    /** The simulated height of the highest layer as typed ({@code ""} = default). */
    public String oreMaxY;
    /** The ore density factor, or {@code null} for the vanilla density. */
    public @Nullable Float oreDensity;

    private final ChallengeStructure original;
    private final String revision;

    /**
     * Creates a draft of stored metadata.
     *
     * @param original the stored metadata
     * @param revision its revision
     */
    public ChallengeDraft(ChallengeStructure original, String revision) {
        this.original = original;
        this.revision = revision;
        this.name = original.name().orElse("");
        this.weight = original.weight();
        this.rotation = original.rotation();
        this.gameMode = original.gameMode();
        ChallengeRunSettings run = original.run();
        this.timeLimit = run.timeLimitSeconds().map(ChallengeDraft::formatSeconds).orElse("");
        this.maxPlayers = run.maxPlayers();
        this.portalMode = switch (run.portalOpenSeconds()) {
            case 0 -> PortalMode.FIRST;
            case ScrollOptions.OPEN_UNTIL_TIME_LIMIT -> PortalMode.UNTIL_TIME_LIMIT;
            default -> PortalMode.SECONDS;
        };
        this.portalSeconds = run.portalOpenSeconds() > 0 ? formatSeconds(run.portalOpenSeconds()) : "1:00";
        this.reentry = run.allowReentry();
        original.playerEffects().forEach(effect -> this.playerEffects.add(EffectDraft.of(effect)));
        original.mobEffects().forEach(effect -> this.mobEffects.add(EffectDraft.of(effect)));
        original.playerAttributes().forEach(attribute -> this.attributes.add(new AttributeDraft(attribute.attribute(),
                formatNumber(attribute.amount()), attribute.operation())));
        this.ores = original.oreGeneration().isPresent();
        this.oreBiome = original.oreGeneration().map(OreGeneration::biome).orElse(null);
        this.oreMinY = original.oreGeneration().flatMap(OreGeneration::minY).map(String::valueOf).orElse("");
        this.oreMaxY = original.oreGeneration().flatMap(OreGeneration::maxY).map(String::valueOf).orElse("");
        this.oreDensity = original.oreGeneration().flatMap(OreGeneration::density).orElse(null);
    }

    /** @return the stored metadata this draft started from */
    public ChallengeStructure original() {
        return this.original;
    }

    /** @return the revision of the stored metadata */
    public String revision() {
        return this.revision;
    }

    /**
     * Builds the metadata of this draft.
     *
     * @param invalid receives the keys of fields that cannot be parsed
     * @return the metadata, or empty if a field is invalid
     */
    public Optional<ChallengeStructure> build(Set<String> invalid) {
        Optional<Integer> time = Optional.empty();
        if (!this.timeLimit.isBlank()) {
            time = parseSeconds(this.timeLimit).filter(seconds -> seconds > 0);
            if (time.isEmpty()) {
                invalid.add(TIME_LIMIT);
            }
        }
        int portal = switch (this.portalMode) {
            case FIRST -> 0;
            case UNTIL_TIME_LIMIT -> ScrollOptions.OPEN_UNTIL_TIME_LIMIT;
            case SECONDS -> parseSeconds(this.portalSeconds).filter(seconds -> seconds > 0).orElse(-2);
        };
        if (portal == -2) {
            invalid.add(PORTAL);
        }
        List<ChallengeEffect> players = this.effects(this.playerEffects, PLAYER_EFFECTS, invalid);
        List<ChallengeEffect> mobs = this.effects(this.mobEffects, MOB_EFFECTS, invalid);
        List<ChallengeAttribute> attributeList = new ArrayList<>();
        for (AttributeDraft attribute : this.attributes) {
            Optional<Double> amount = parseDouble(attribute.amount);
            if (amount.isEmpty()) {
                invalid.add(ATTRIBUTES);
            } else {
                attributeList.add(new ChallengeAttribute(attribute.attribute, amount.get(), attribute.operation));
            }
        }
        Optional<OreGeneration> oreGeneration = Optional.empty();
        if (this.ores) {
            Optional<Integer> min = this.oreMinY.isBlank() ? Optional.empty() : parseInt(this.oreMinY);
            Optional<Integer> max = this.oreMaxY.isBlank() ? Optional.empty() : parseInt(this.oreMaxY);
            boolean badMin = !this.oreMinY.isBlank() && min.isEmpty();
            boolean badMax = !this.oreMaxY.isBlank() && max.isEmpty();
            if (this.oreBiome == null || badMin || badMax || (min.isPresent() && max.isPresent() && min.get() >= max.get())) {
                invalid.add(ORES);
            } else {
                oreGeneration = Optional.of(new OreGeneration(this.oreBiome, min, max, Optional.ofNullable(this.oreDensity)));
            }
        }
        if (this.weight < 1) {
            invalid.add(WEIGHT);
        }
        if (!invalid.isEmpty()) {
            return Optional.empty();
        }
        String trimmed = this.name.trim();
        ChallengeStructure o = this.original;
        return Optional.of(new ChallengeStructure(o.theme(), o.tier(), o.structure(), trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed),
                o.author(), o.created(), this.weight, this.rotation, this.gameMode, players, attributeList, oreGeneration,
                new ChallengeRunSettings(time, this.maxPlayers, this.reentry, portal), mobs));
    }

    private List<ChallengeEffect> effects(List<EffectDraft> drafts, String field, Set<String> invalid) {
        List<ChallengeEffect> effects = new ArrayList<>();
        for (EffectDraft draft : drafts) {
            Optional<Integer> seconds = draft.infinite ? Optional.of(0) : parseSeconds(draft.duration).filter(value -> value > 0);
            if (seconds.isEmpty()) {
                invalid.add(field);
                continue;
            }
            effects.add(new ChallengeEffect(draft.effect, draft.amplifier, draft.infinite ? MobEffectInstance.INFINITE_DURATION : seconds.get() * 20));
        }
        return effects;
    }

    /**
     * Returns the fields that differ from the stored metadata.
     *
     * @return the changed field keys
     */
    public Set<String> changedFields() {
        Set<String> invalid = new LinkedHashSet<>();
        Optional<ChallengeStructure> built = this.build(invalid);
        Set<String> changed = new LinkedHashSet<>(invalid);
        if (built.isEmpty()) {
            return changed;
        }
        ChallengeStructure b = built.get();
        ChallengeStructure o = this.original;
        mark(changed, NAME, b.name(), o.name());
        mark(changed, WEIGHT, b.weight(), o.weight());
        mark(changed, ROTATION, b.rotation(), o.rotation());
        mark(changed, GAME_MODE, b.gameMode(), o.gameMode());
        mark(changed, TIME_LIMIT, b.run().timeLimitSeconds(), o.run().timeLimitSeconds());
        mark(changed, MAX_PLAYERS, b.run().maxPlayers(), o.run().maxPlayers());
        mark(changed, PORTAL, b.run().portalOpenSeconds(), o.run().portalOpenSeconds());
        mark(changed, REENTRY, b.run().allowReentry(), o.run().allowReentry());
        mark(changed, PLAYER_EFFECTS, b.playerEffects(), o.playerEffects());
        mark(changed, MOB_EFFECTS, b.mobEffects(), o.mobEffects());
        mark(changed, ATTRIBUTES, b.playerAttributes(), o.playerAttributes());
        mark(changed, ORES, b.oreGeneration(), o.oreGeneration());
        return changed;
    }

    private static void mark(Set<String> changed, String field, Object value, Object original) {
        if (!Objects.equals(value, original)) {
            changed.add(field);
        }
    }

    /** @return {@code true} if anything differs from the stored metadata */
    public boolean dirty() {
        return !this.changedFields().isEmpty();
    }

    /**
     * Parses {@code m:ss}, {@code h:mm:ss} or plain seconds.
     *
     * @param text the text
     * @return the seconds, or empty if the text is no duration
     */
    public static Optional<Integer> parseSeconds(String text) {
        String[] parts = text.trim().split(":");
        if (parts.length == 0 || parts.length > 3) {
            return Optional.empty();
        }
        int seconds = 0;
        for (String part : parts) {
            Optional<Integer> value = parseInt(part);
            if (value.isEmpty() || value.get() < 0) {
                return Optional.empty();
            }
            seconds = seconds * 60 + value.get();
        }
        return Optional.of(seconds);
    }

    /**
     * Formats seconds as {@code m:ss}.
     *
     * @param seconds the seconds
     * @return the text
     */
    public static String formatSeconds(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Formats a number without needless decimals.
     *
     * @param value the value
     * @return the text
     */
    public static String formatNumber(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1.0E9 ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /**
     * Parses an integer.
     *
     * @param text the text
     * @return the number, or empty
     */
    public static Optional<Integer> parseInt(String text) {
        try {
            return Optional.of(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses a decimal number (dot or comma).
     *
     * @param text the text
     * @return the number, or empty
     */
    public static Optional<Double> parseDouble(String text) {
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
