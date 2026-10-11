package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.browser.ChallengeEdits;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.instance.InstancePlacements;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollModifiers;
import com.gilfort.architectstrials.structure.ChallengeRunSettings;
import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.structure.OreGeneration;
import com.gilfort.architectstrials.theme.ChallengeTheme;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-42 (editing challenge settings in the browser): merging an edit into the stored metadata with
 * revision check and validation, rejection of structures outside the managed pack and test runs of a forced
 * structure.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class BrowserEditGameTests {

    private static final Identifier SPAWN_PLATFORM = ArchitectsTrials.id("the_nether/tier_2/spawn_platform");

    private BrowserEditGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "browser_edit_merge", BrowserEditGameTests::merge);
            register(helper, "browser_edit_not_editable", BrowserEditGameTests::notEditable);
            register(helper, "browser_test_run_forced_structure", BrowserEditGameTests::forcedStructure);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * An edit is merged into the stored metadata: editable fields are taken, theme, tier, template and author are
     * kept; a stale revision is a conflict; broken JSON, an unknown biome and a too long name are rejected.
     */
    private static void merge(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ChallengeStructure stored = ChallengeStructures.get(SPAWN_PLATFORM).orElseThrow();
        String revision = ChallengeEdits.revision(server, stored);
        ChallengeStructure edited = new ChallengeStructure(ArchitectsTrials.id("other_theme"), 9, ArchitectsTrials.id("other/template"),
                Optional.of("  Edited  "), Optional.of("someone else"), Optional.of(1L), 5, true, GameType.SURVIVAL, List.of(), List.of(),
                Optional.empty(), new ChallengeRunSettings(Optional.of(90), 3, true, 45), List.of());

        ChallengeEdits.Merge saved = ChallengeEdits.merge(server, stored, revision, json(server, edited));
        helper.assertTrue(saved.result().status() == ChallengeEdits.SaveStatus.SAVED, "Valid edit was not accepted: " + saved.result());
        ChallengeStructure merged = saved.merged().orElseThrow();
        helper.assertTrue(merged.theme().equals(stored.theme()) && merged.tier() == stored.tier() && merged.structure().equals(stored.structure())
                && merged.author().equals(stored.author()) && merged.created().equals(stored.created()),
                "Theme, tier, template, author or creation time were taken from the edit: " + merged);
        helper.assertTrue(merged.name().equals(Optional.of("Edited")) && merged.weight() == 5 && merged.rotation()
                && merged.gameMode() == GameType.SURVIVAL && merged.run().equals(edited.run()), "Editable fields were not taken: " + merged);
        helper.assertFalse(ChallengeEdits.revision(server, merged).equals(revision), "Revision did not change with the metadata");

        helper.assertTrue(ChallengeEdits.merge(server, stored, "stale", json(server, edited)).result().status() == ChallengeEdits.SaveStatus.CONFLICT,
                "Stale revision was not a conflict");
        helper.assertTrue(ChallengeEdits.merge(server, stored, revision, "{ broken").result().status() == ChallengeEdits.SaveStatus.INVALID,
                "Broken JSON was accepted");

        ChallengeStructure unknownBiome = new ChallengeStructure(stored.theme(), stored.tier(), stored.structure(), stored.name(), stored.author(), stored.created(),
                stored.weight(), stored.rotation(), stored.gameMode(), List.of(), List.of(),
                Optional.of(new OreGeneration(ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("missingmod", "nowhere")),
                        Optional.empty(), Optional.empty(), Optional.empty())), stored.run(), List.of());
        ChallengeEdits.Merge biome = ChallengeEdits.merge(server, stored, revision, json(server, unknownBiome));
        helper.assertTrue(biome.result().status() == ChallengeEdits.SaveStatus.INVALID
                && biome.result().errors().stream().anyMatch(error -> error.field().equals("ore_generation")), "Unknown biome was accepted");

        ChallengeStructure longName = stored.with(Optional.of("x".repeat(ChallengeEdits.MAX_NAME_LENGTH + 1)), stored.weight(), stored.rotation(),
                stored.gameMode());
        ChallengeEdits.Merge name = ChallengeEdits.merge(server, stored, revision, json(server, longName));
        helper.assertTrue(name.result().errors().stream().anyMatch(error -> error.field().equals("name")), "Too long name was accepted");
        helper.succeed();
    }

    /** Structures outside the managed datapack cannot be changed. */
    private static void notEditable(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ChallengeStructure stored = ChallengeStructures.get(SPAWN_PLATFORM).orElseThrow();
        ChallengeEdits.SaveResult result = ChallengeEdits.save(server, SPAWN_PLATFORM, ChallengeEdits.revision(server, stored), json(server, stored));
        helper.assertTrue(result.status() == ChallengeEdits.SaveStatus.NOT_EDITABLE, "Structure of the test datapack was editable: " + result);
        helper.succeed();
    }

    /** A test run places exactly the requested structure; a structure of another tier is rejected. */
    private static void forcedStructure(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengeTheme theme = ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow();
        InstanceCreation wrongTier = InstanceManager.create(nether, theme, 3, nether.getRandom(), Optional.of(SPAWN_PLATFORM),
                ScrollModifiers.NONE, ScrollEffects.NONE);
        helper.assertTrue(wrongTier instanceof InstanceCreation.Failure, "A structure of another tier was placed");
        InstanceCreation created = InstanceManager.create(nether, theme, 2, nether.getRandom(), Optional.of(SPAWN_PLATFORM),
                ScrollModifiers.NONE, ScrollEffects.NONE);
        if (!(created instanceof InstanceCreation.Success(ChallengeInstance instance))) {
            helper.fail("Forced structure was not placed: " + ((InstanceCreation.Failure) created).reason().getString());
            return;
        }
        helper.assertTrue(instance.structure().equals(SPAWN_PLATFORM), "Another structure was placed: " + instance.structure());
        InstancePlacements.whenReady(nether, instance.id(), ready -> {
            InstanceManager.close(nether, ready.id());
            helper.succeed();
        });
    }

    private static String json(MinecraftServer server, ChallengeStructure structure) {
        return ChallengeStructure.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), structure)
                .getOrThrow().toString();
    }
}
