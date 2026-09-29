package com.gilfort.architectstrials.theme;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A challenge theme, e.g. "nether" or "magic".
 * <p>
 * A theme <em>is</em> a dimension (1:1): the theme id equals the id of the dimension that hosts all
 * challenge instances of this theme. The dimension itself defines the thematic environment (sky, fog,
 * lighting, biome ambience).
 *
 * @param id the theme id, identical to the dimension id
 */
public record ChallengeTheme(Identifier id) {

    /**
     * Returns the key of the dimension backing this theme.
     *
     * @return the dimension key
     */
    public ResourceKey<Level> dimension() {
        return ResourceKey.create(Registries.DIMENSION, this.id);
    }

    /**
     * Returns the player-facing name of this theme.
     * <p>
     * Resolved from the lang key {@code dimension.<namespace>.<path>}. Because themes are defined by
     * datapacks, which cannot ship lang files, the theme id is used as a fallback when no resource pack
     * provides a translation.
     *
     * @return the translatable display name
     */
    public Component displayName() {
        return Component.translatableWithFallback(this.id.toLanguageKey("dimension"), this.id.toString());
    }
}
