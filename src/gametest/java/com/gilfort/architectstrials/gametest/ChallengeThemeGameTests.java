package com.gilfort.architectstrials.gametest;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-01 (challenge themes and theme dimensions).
 * <p>
 * The vanilla GameTest server never loads datapack dimensions. The test datapack in
 * {@code src/gametest/resources} therefore declares the vanilla {@code minecraft:the_nether} as a theme to
 * exercise theme logic, plus {@code architectstrials:gametest_missing} (no dimension). The datapack
 * dimension {@code architectstrials:gametest_theme} is only available in dev client/server runs for manual
 * testing. Test instances are defined as JSON under {@code data/architectstrials/test_instance}.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ChallengeThemeGameTests {

    private static final Identifier NETHER_THEME = Level.NETHER.identifier();
    private static final Identifier MISSING_THEME = ArchitectsTrials.id("gametest_missing");
    private static final ResourceKey<DimensionType> CHALLENGE_DIMENSION_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, ArchitectsTrials.id("challenge"));

    private ChallengeThemeGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "theme_registration", ChallengeThemeGameTests::themeRegistration);
            register(helper, "challenge_dimension_rules", ChallengeThemeGameTests::challengeDimensionRules);
            register(helper, "challenge_dimension_type", ChallengeThemeGameTests::challengeDimensionType);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Declared themes with an existing dimension are loaded; themes without a dimension are skipped.
     *
     * @param helper the test helper
     */
    private static void themeRegistration(GameTestHelper helper) {
        helper.assertTrue(ChallengeThemes.get(NETHER_THEME).isPresent(), "Theme with existing dimension was not loaded");
        helper.assertTrue(ChallengeThemes.get(MISSING_THEME).isEmpty(), "Theme without dimension was not skipped");
        helper.assertTrue(ChallengeThemes.isChallengeDimension(Level.NETHER), "Nether is not recognised as challenge dimension");
        helper.assertFalse(ChallengeThemes.isChallengeDimension(Level.OVERWORLD), "Overworld must not be a challenge dimension");
        helper.succeed();
    }

    /**
     * Natural spawning is blocked inside challenge dimensions, while spawner spawns are left to vanilla
     * rules. Runs inside {@code minecraft:the_nether}, declared as a theme by the test datapack.
     *
     * @param helper the test helper
     */
    private static void challengeDimensionRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.assertTrue(ChallengeThemes.isChallengeDimension(level.dimension()), "Test does not run in a challenge dimension");
        helper.assertFalse(EventHooks.checkSpawnPlacements(EntityTypes.ZOMBIE, level, EntitySpawnReason.NATURAL, pos, level.getRandom(), true),
                "Natural spawning must be blocked");
        helper.assertFalse(EventHooks.checkSpawnPlacements(EntityTypes.ZOMBIE, level, EntitySpawnReason.CHUNK_GENERATION, pos, level.getRandom(), true),
                "Chunk generation spawning must be blocked");
        helper.assertTrue(EventHooks.checkSpawnPlacements(EntityTypes.ZOMBIE, level, EntitySpawnReason.SPAWNER, pos, level.getRandom(), true),
                "Spawner spawning must not be blocked");
        helper.succeed();
    }

    /**
     * The mod's default dimension type {@code architectstrials:challenge} loads and disables weather
     * (ceiling) and the day cycle (fixed time) while keeping sky light.
     *
     * @param helper the test helper
     */
    private static void challengeDimensionType(GameTestHelper helper) {
        DimensionType type = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.DIMENSION_TYPE)
                .get(CHALLENGE_DIMENSION_TYPE)
                .orElseThrow(() -> new IllegalStateException("Dimension type architectstrials:challenge is missing"))
                .value();
        helper.assertTrue(type.hasCeiling(), "Challenge dimension type must have a ceiling (disables weather)");
        helper.assertTrue(type.hasFixedTime(), "Challenge dimension type must have a fixed time");
        helper.assertTrue(type.hasSkyLight(), "Challenge dimension type must keep sky light");
        helper.succeed();
    }
}
