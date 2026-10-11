package com.gilfort.architectstrials.browser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.StructureLibrary;
import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.structure.StructureValidation;
import com.gilfort.architectstrials.sub.SubStructure;
import com.gilfort.architectstrials.sub.SubStructures;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of the challenge browser (US-40): builds the {@link BrowserSnapshot}, caches it until the next
 * datapack reload and pushes a fresh one to every player who has the browser open after a reload (which also
 * follows every {@code editor save}, {@code structure set} and {@code structure delete}).
 * <p>
 * Building a snapshot reads every template file once; it only happens when someone actually uses the browser.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ChallengeBrowser {

    private static final Set<UUID> VIEWERS = new HashSet<>();
    private static BrowserSnapshot cached;

    private ChallengeBrowser() {
    }

    /**
     * Checks whether a player may use the browser: operators like for all {@code /at} commands.
     *
     * @param player the player
     * @return {@code true} if the player may browse and load structures
     */
    public static boolean mayUse(ServerPlayer player) {
        return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
    }

    /**
     * Opens the browser for a player.
     *
     * @param player the player
     */
    public static void open(ServerPlayer player) {
        VIEWERS.add(player.getUUID());
        PacketDistributor.sendToPlayer(player, new BrowserNetwork.Open(snapshot(player.level().getServer())));
    }

    /**
     * Sends the current snapshot to a player whose browser is open (refresh button).
     *
     * @param player the player
     */
    public static void refresh(ServerPlayer player) {
        VIEWERS.add(player.getUUID());
        PacketDistributor.sendToPlayer(player, new BrowserNetwork.Update(snapshot(player.level().getServer())));
    }

    /**
     * Forgets a player whose browser was closed.
     *
     * @param player the player
     */
    public static void closed(ServerPlayer player) {
        VIEWERS.remove(player.getUUID());
    }

    /**
     * Returns the snapshot, building it if the cache is empty.
     *
     * @param server the server
     * @return the snapshot
     */
    public static BrowserSnapshot snapshot(MinecraftServer server) {
        if (cached == null) {
            cached = build(server);
        }
        return cached;
    }

    /** Drops the cached snapshot, e.g. after a reload. */
    public static void invalidate() {
        cached = null;
    }

    /**
     * Builds a fresh snapshot of all themes, challenge structures and sub structures.
     *
     * @param server the server
     * @return the snapshot
     */
    public static BrowserSnapshot build(MinecraftServer server) {
        Map<Identifier, List<BrowserSnapshot.ChallengeEntry>> byTheme = new TreeMap<>();
        for (ChallengeTheme theme : ChallengeThemes.all()) {
            byTheme.put(theme.id(), new ArrayList<>());
        }
        Map<Identifier, Set<Identifier>> usedBy = new HashMap<>();
        for (Map.Entry<Identifier, ChallengeStructure> entry : ChallengeStructures.all().entrySet()) {
            ChallengeStructure structure = entry.getValue();
            Optional<CompoundTag> data = StructureValidation.readTemplate(server, structure.structure());
            StructureStats stats = StructureStatistics.compute(server, data);
            Map<String, Integer> problems = new TreeMap<>(StructureValidation.validate(server, structure.structure(), data));
            for (Identifier sub : stats.subStructures()) {
                usedBy.computeIfAbsent(sub, id -> new TreeSet<>()).add(entry.getKey());
                if (SubStructures.get(sub).isEmpty()) {
                    problems.merge("missing sub structure: " + sub, 1, Integer::sum);
                }
            }
            String source = ChallengeStructures.source(entry.getKey()).orElse("");
            byTheme.computeIfAbsent(structure.theme(), theme -> new ArrayList<>()).add(new BrowserSnapshot.ChallengeEntry(entry.getKey(),
                    structure, source, StructureLibrary.PACK_ID.equals(source), ChallengeEdits.revision(server, structure), stats, problems));
        }
        List<BrowserSnapshot.ThemeEntry> themes = new ArrayList<>();
        byTheme.forEach((theme, challenges) -> {
            challenges.sort(Comparator.comparingInt((BrowserSnapshot.ChallengeEntry challenge) -> challenge.metadata().tier())
                    .thenComparing(BrowserSnapshot.ChallengeEntry::id));
            themes.add(new BrowserSnapshot.ThemeEntry(theme, ChallengeThemes.get(theme).isPresent(), challenges));
        });
        List<BrowserSnapshot.SubEntry> subs = new ArrayList<>();
        new TreeMap<>(SubStructures.all()).forEach((id, sub) -> {
            Optional<CompoundTag> data = StructureValidation.readTemplate(server, sub.structure());
            String source = SubStructures.source(id).orElse("");
            subs.add(new BrowserSnapshot.SubEntry(id, sub, source, StructureLibrary.PACK_ID.equals(source), StructureStatistics.compute(server, data),
                    StructureValidation.validate(server, sub.structure(), data), List.copyOf(usedBy.getOrDefault(id, Set.of()))));
        });
        return new BrowserSnapshot(themes, subs);
    }

    /**
     * After a datapack reload (not on a single player's login): drops the cache and pushes a fresh snapshot to every
     * open browser.
     *
     * @param event the datapack sync event
     */
    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            return;
        }
        invalidate();
        List<ServerPlayer> viewers = event.getPlayerList().getPlayers().stream()
                .filter(player -> VIEWERS.contains(player.getUUID()) && mayUse(player))
                .toList();
        if (viewers.isEmpty()) {
            return;
        }
        BrowserNetwork.Update update = new BrowserNetwork.Update(snapshot(event.getPlayerList().getServer()));
        viewers.forEach(player -> PacketDistributor.sendToPlayer(player, update));
    }

    /**
     * Forgets players who log out.
     *
     * @param event the logout event
     */
    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        VIEWERS.remove(event.getEntity().getUUID());
    }

    /**
     * Resets everything when the server stops (singleplayer worlds share the JVM).
     *
     * @param event the server stopped event
     */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        VIEWERS.clear();
        invalidate();
    }
}
