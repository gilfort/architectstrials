package com.gilfort.architectstrials.theme;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * Server-side registry of all loaded challenge themes.
 * <p>
 * Themes are declared by datapacks ({@link ChallengeDimensionListLoader}) and only become active once
 * validated against the dimensions that actually exist on the running server. Declared dimensions that do
 * not exist are logged and skipped.
 */
public final class ChallengeThemes {

    private static Set<Identifier> declaredDimensions = Set.of();
    private static Map<Identifier, ChallengeTheme> themes = Map.of();

    private ChallengeThemes() {
    }

    /**
     * Stores the dimension ids declared by the datapacks. Called on every datapack (re)load.
     *
     * @param ids the merged dimension ids
     */
    static void setDeclaredDimensions(Set<Identifier> ids) {
        declaredDimensions = Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }

    /**
     * Validates the declared dimensions against the server's existing dimensions and rebuilds the theme
     * registry. Missing dimensions are logged as errors and skipped.
     *
     * @param server the running server
     */
    public static void resolve(MinecraftServer server) {
        Set<ResourceKey<Level>> existing = server.levelKeys();
        Map<Identifier, ChallengeTheme> resolved = new LinkedHashMap<>();
        for (Identifier id : declaredDimensions) {
            ChallengeTheme theme = new ChallengeTheme(id);
            if (existing.contains(theme.dimension())) {
                resolved.put(id, theme);
            } else {
                ArchitectsTrials.LOGGER.error("Challenge theme '{}' references a dimension that does not exist; skipping it. "
                        + "New dimensions require a server restart.", id);
            }
        }
        themes = Collections.unmodifiableMap(resolved);
        ArchitectsTrials.LOGGER.info("Loaded {} challenge theme(s): {}", themes.size(), themes.keySet());
    }

    /**
     * Removes all themes. Called when the server stops.
     */
    public static void clear() {
        themes = Map.of();
    }

    /**
     * Returns all active themes in declaration order.
     *
     * @return an unmodifiable view of the active themes
     */
    public static Collection<ChallengeTheme> all() {
        return themes.values();
    }

    /**
     * Looks up an active theme by id.
     *
     * @param id the theme id
     * @return the theme, or empty if no active theme has this id
     */
    public static Optional<ChallengeTheme> get(Identifier id) {
        return Optional.ofNullable(themes.get(id));
    }

    /**
     * Checks whether the given dimension belongs to an active challenge theme.
     *
     * @param dimension the dimension key
     * @return {@code true} if the dimension is a challenge dimension
     */
    public static boolean isChallengeDimension(ResourceKey<Level> dimension) {
        return themes.containsKey(dimension.identifier());
    }
}
