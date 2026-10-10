package com.gilfort.architectstrials.gametest;

import java.util.Optional;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.instance.ChallengeClock;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceLifecycle;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.portal.ChallengePortal;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.run.RunCompletion;
import com.gilfort.architectstrials.scroll.ScrollActivation;
import com.gilfort.architectstrials.scroll.ScrollModifiers;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.structure.ChallengeRunSettings;
import com.gilfort.architectstrials.travel.ChallengeTravel;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-12 (group size, portal lifetime, re-entry, late join) and US-41 (the challenge defines the run
 * settings, scroll modifiers change them, the time limit starts on the first entry). Scrolls target
 * {@code minecraft:the_nether} tier 13 (time limit 120 s, 2 players, portal open 60 s) or tier 14 (30 s, unlimited
 * players, portal open until the time runs out, resistance II for mobs); portals are ticked manually.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ScrollOptionsGameTests {

    private static final int TIER = 13;
    private static final int UNTIL_TIME_TIER = 14;

    private ScrollOptionsGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "options_group_portal_and_late_join", ScrollOptionsGameTests::groupPortalAndLateJoin);
            register(helper, "options_reentry_rules", ScrollOptionsGameTests::reentryRules);
            register(helper, "options_portal_closes_on_time", ScrollOptionsGameTests::portalClosesOnTime);
            register(helper, "options_from_challenge", ScrollOptionsGameTests::optionsFromChallenge);
            register(helper, "options_open_until_time_limit", ScrollOptionsGameTests::openUntilTimeLimit);
            register(helper, "scroll_modifiers_combine", ScrollOptionsGameTests::modifiersCombine);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * A two-player portal stays open after the first entry, lets a second, unranked player join late, and closes
     * once the maximum is reached; a returned player cannot re-enter without the re-entry option.
     */
    private static void groupPortalAndLateJoin(GameTestHelper helper) {
        ServerPlayer owner = ScrollPortalGameTests.qualified(TestPlayers.atStart(helper, GameType.SURVIVAL), TIER);
        ServerPlayer lateJoiner = TestPlayers.atStart(helper, GameType.SURVIVAL);
        ChallengePortal portal = openActivePortal(helper, owner, TIER, ScrollModifiers.NONE);
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        helper.assertTrue(InstanceManager.data(nether).get(portal.instanceId()).orElseThrow().options().equals(new ScrollOptions(2, 60, false)),
                "Options were not taken from the challenge");

        enter(helper, portal, owner);
        helper.assertTrue(owner.level() == nether, "Owner did not enter the group portal");
        helper.assertFalse(portal.isRemoved(), "Group portal closed after the first player");

        ChallengeTravel.returnToEntryPoint(owner);
        owner.hasChangedDimension();
        owner.setPortalCooldown(0);
        enter(helper, portal, owner);
        helper.assertTrue(owner.level() == helper.getLevel(), "Player re-entered although re-entry is not allowed");

        enter(helper, portal, lateJoiner);
        helper.assertTrue(lateJoiner.level() == nether, "Late joiner below the scroll's rank could not enter");
        helper.assertTrue(portal.isRemoved(), "Portal did not close when the maximum number of players was reached");
        ChallengeInstance instance = InstanceManager.data(nether).get(portal.instanceId()).orElseThrow();
        helper.assertTrue(instance.roster().entrants().size() == 2 && !instance.portalOpen(), "Roster or portal state is wrong");

        TestPlayers.finish(helper, lateJoiner);
        TestPlayers.finish(helper, owner);
    }

    /**
     * With re-entry, a player who left without completing may enter again (not counted twice); a third distinct
     * player is rejected when full; after completing the run the player can never re-enter.
     */
    private static void reentryRules(GameTestHelper helper) {
        ServerPlayer owner = ScrollPortalGameTests.qualified(TestPlayers.atStart(helper, GameType.SURVIVAL), TIER);
        ServerPlayer second = TestPlayers.atStart(helper, GameType.SURVIVAL);
        ServerPlayer third = TestPlayers.atStart(helper, GameType.SURVIVAL);
        ChallengePortal portal = openActivePortal(helper, owner, TIER, new ScrollModifiers(0.0, 0.0, Optional.empty(), true));
        ServerLevel nether = TestPlayers.challengeLevel(helper);

        enter(helper, portal, owner);
        ChallengeTravel.returnToEntryPoint(owner);
        owner.hasChangedDimension();
        owner.setPortalCooldown(0);
        enter(helper, portal, owner);
        helper.assertTrue(owner.level() == nether, "Re-entry was denied although the scroll allows it");
        helper.assertTrue(InstanceManager.data(nether).get(portal.instanceId()).orElseThrow().roster().entrants().size() == 1,
                "Re-entry was counted as a new player");

        enter(helper, portal, second);
        helper.assertTrue(second.level() == nether, "Second player could not enter");
        helper.assertFalse(portal.isRemoved(), "Portal with re-entry closed when the maximum was reached");
        enter(helper, portal, third);
        helper.assertTrue(third.level() == helper.getLevel(), "A third player entered a two-player challenge");

        ChallengeInstance instance = InstanceManager.data(nether).get(portal.instanceId()).orElseThrow();
        RunCompletion.complete(owner, instance, Optional.empty());
        owner.hasChangedDimension();
        owner.setPortalCooldown(0);
        enter(helper, portal, owner);
        helper.assertTrue(owner.level() == helper.getLevel(), "A player who completed the run re-entered");

        portal.discard();
        TestPlayers.finish(helper, third);
        TestPlayers.finish(helper, second);
        TestPlayers.finish(helper, owner);
    }

    /**
     * A timed portal closes when its seconds are over (multiplayer scrolls: at least 15 s, even if a shorter time
     * is configured); the instance continues for its participant.
     */
    private static void portalClosesOnTime(GameTestHelper helper) {
        ServerPlayer owner = ScrollPortalGameTests.qualified(TestPlayers.atStart(helper, GameType.SURVIVAL), TIER);
        ScrollOptions options = new ScrollOptions(3, 1, false);
        helper.assertTrue(options.portalOpenSeconds() == ScrollOptions.MIN_MULTIPLAYER_PORTAL_SECONDS,
                "Multiplayer portal duration was not raised to the minimum");
        ChallengePortal portal = openActivePortal(helper, owner, TIER, new ScrollModifiers(0.0, -99.0, Optional.of(1), false));
        helper.assertTrue(InstanceManager.data(TestPlayers.challengeLevel(helper)).get(portal.instanceId()).orElseThrow().options().equals(options),
                "Modified options are not 3 players with the minimum portal time");
        enter(helper, portal, owner);
        int openTicks = ScrollOptions.MIN_MULTIPLAYER_PORTAL_SECONDS * ChallengeClock.TICKS_PER_SECOND;
        for (int i = 0; i < openTicks - 5; i++) {
            portal.tick();
        }
        helper.assertFalse(portal.isRemoved(), "Multiplayer portal closed before the minimum duration");
        for (int i = 0; i < 10 && !portal.isRemoved(); i++) {
            portal.tick();
        }
        helper.assertTrue(portal.isRemoved(), "Timed portal did not close");
        ChallengeInstance instance = InstanceManager.data(TestPlayers.challengeLevel(helper)).get(portal.instanceId()).orElse(null);
        helper.assertTrue(instance != null && !instance.portalOpen() && instance.participants().contains(owner.getUUID()),
                "Instance did not keep running for its participant after the portal closed");
        TestPlayers.finish(helper, owner);
    }

    /**
     * The challenge defines time limit and options, the scroll scales and extends them; the time limit starts on
     * the first entry, not when the portal opens, and an unstarted instance never expires.
     */
    private static void optionsFromChallenge(GameTestHelper helper) {
        ServerPlayer owner = ScrollPortalGameTests.qualified(TestPlayers.atStart(helper, GameType.SURVIVAL), TIER);
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengePortal portal = openActivePortal(helper, owner, TIER, new ScrollModifiers(-50.0, 100.0, Optional.of(2), false));
        ChallengeInstance instance = InstanceManager.data(nether).get(portal.instanceId()).orElseThrow();
        helper.assertTrue(instance.timeLimit() == 60L * ChallengeClock.TICKS_PER_SECOND, "Time limit is not 120 s -50 %: " + instance.timeLimit());
        helper.assertTrue(instance.options().equals(new ScrollOptions(4, 120, false)), "Options are not 2 + 2 players, 60 s +100 %: " + instance.options());
        helper.assertFalse(instance.started(), "Time limit started before anyone entered");

        // An unstarted instance has no deadline; treating it as one would expire the instance right away.
        InstanceLifecycle.update(nether.getServer());
        helper.assertTrue(InstanceManager.data(nether).get(portal.instanceId()).isPresent(), "Unstarted instance expired");

        enter(helper, portal, owner);
        ChallengeInstance started = InstanceManager.data(nether).get(portal.instanceId()).orElseThrow();
        long now = ChallengeClock.now(nether.getServer());
        helper.assertTrue(started.started() && started.deadline() == now + started.timeLimit(),
                "Time limit did not start on the first entry: " + started.deadline() + " vs " + (now + started.timeLimit()));
        portal.discard();
        TestPlayers.finish(helper, owner);
    }

    /**
     * {@code portal_open_seconds = -1}: before the first entry the portal waits like an unused one, afterwards it
     * stays open until the time runs out; challenge mob effects reach mobs joining the instance.
     */
    private static void openUntilTimeLimit(GameTestHelper helper) {
        ServerPlayer owner = ScrollPortalGameTests.qualified(TestPlayers.atStart(helper, GameType.SURVIVAL), UNTIL_TIME_TIER);
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengePortal portal = openActivePortal(helper, owner, UNTIL_TIME_TIER, ScrollModifiers.NONE);
        ChallengeInstance instance = InstanceManager.data(nether).get(portal.instanceId()).orElseThrow();
        helper.assertTrue(instance.options().equals(new ScrollOptions(0, ScrollOptions.OPEN_UNTIL_TIME_LIMIT, false)),
                "Options are not unlimited players, portal open until the time runs out: " + instance.options());
        long unused = (long) ArchitectsTrialsConfig.PORTAL_TIMEOUT_SECONDS.getAsInt() * ChallengeClock.TICKS_PER_SECOND;
        helper.assertTrue(InstanceManager.activePortalTicks(instance) == unused, "Unstarted portal does not wait the unused timeout");

        enter(helper, portal, owner);
        ChallengeInstance started = InstanceManager.data(nether).get(portal.instanceId()).orElseThrow();
        helper.assertTrue(started.portalDeadline() == started.deadline(), "Portal deadline is not the time limit");
        helper.assertTrue(InstanceManager.activePortalTicks(started) == Long.MAX_VALUE, "Started portal does not stay open until the time runs out");
        for (int i = 0; i < 40; i++) {
            portal.tick();
        }
        helper.assertFalse(portal.isRemoved(), "Portal closed although it stays open until the time runs out");

        Zombie zombie = EntityTypes.ZOMBIE.create(nether, EntitySpawnReason.COMMAND);
        zombie.setPos(owner.position());
        zombie.setNoAi(true);
        nether.addFreshEntity(zombie);
        MobEffectInstance resistance = zombie.getEffect(MobEffects.RESISTANCE);
        helper.assertTrue(resistance != null && resistance.getAmplifier() == 1 && resistance.isInfiniteDuration(),
                "Challenge mob effect was not applied to a joining mob: " + resistance);
        zombie.discard();
        portal.discard();
        TestPlayers.finish(helper, owner);
    }

    /**
     * Modifiers combine multiplicatively, players add up (unlimited wins), re-entry stays on; values of -100 %
     * or less are rejected.
     */
    private static void modifiersCombine(GameTestHelper helper) {
        ScrollModifiers quarter = new ScrollModifiers(25.0, -50.0, Optional.of(1), false);
        ScrollModifiers twice = quarter.combine(quarter);
        helper.assertTrue(Math.abs(twice.timePercent() - 56.25) < 1.0E-9 && Math.abs(twice.portalOpenPercent() + 75.0) < 1.0E-9,
                "Percentages did not multiply: " + twice);
        helper.assertTrue(twice.maxPlayers().equals(Optional.of(2)), "Players did not add up: " + twice);
        ScrollModifiers unlimited = twice.combine(new ScrollModifiers(0.0, 0.0, Optional.of(0), true));
        helper.assertTrue(unlimited.unlimitedPlayers() && unlimited.allowReentry(), "Unlimited players or re-entry got lost: " + unlimited);
        helper.assertTrue(ScrollModifiers.NONE.combine(ScrollModifiers.NONE).isNeutral(), "Neutral modifiers changed something");
        helper.assertTrue(ScrollModifiers.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"time\": -100}")).isError(),
                "-100 % was accepted");
        helper.assertTrue(ScrollModifiers.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"time\": -50, \"allow_reentry\": true}"))
                .getOrThrow().equals(new ScrollModifiers(-50.0, 0.0, Optional.empty(), true)), "Modifiers did not parse");

        ChallengeRunSettings run = new ChallengeRunSettings(Optional.of(90), 1, false, 0);
        helper.assertTrue(new ScrollModifiers(-50.0, 0.0, Optional.empty(), false).timeLimitSeconds(run) == 45, "90 s -50 % is not 45 s");
        helper.assertTrue(new ScrollModifiers(-99.9, 0.0, Optional.empty(), false).timeLimitSeconds(run) == 1, "Time limit fell below one second");
        helper.assertTrue(ScrollModifiers.NONE.options(run).equals(ScrollOptions.DEFAULT), "Solo challenge without modifiers is not the default");
        helper.assertTrue(new ScrollModifiers(0.0, 0.0, Optional.of(3), false).options(run.withMaxPlayers(0)).unlimitedPlayers(),
                "Additional players turned an unlimited challenge into a limited one");
        helper.succeed();
    }

    private static ChallengePortal openActivePortal(GameTestHelper helper, ServerPlayer owner, int tier, ScrollModifiers modifiers) {
        ItemStack scroll = ScrollPortalGameTests.scroll(Level.NETHER.identifier(), tier);
        scroll.set(ModDataComponents.SCROLL_MODIFIERS.get(), modifiers);
        ScrollPortalGameTests.clearPortalSpace(helper);
        ChallengePortal portal = ScrollActivation.activate(owner, scroll, helper.absolutePos(ScrollPortalGameTests.PORTAL), 0.0F)
                .portal().orElseThrow();
        for (int i = 0; i <= ChallengePortal.FORMING_TICKS; i++) {
            portal.tick();
        }
        helper.assertTrue(portal.isActive(), "Portal did not become active");
        return portal;
    }

    private static void enter(GameTestHelper helper, ChallengePortal portal, ServerPlayer player) {
        TestPlayers.teleport(player, helper.getLevel(), portal.position());
        portal.playerTouch(player);
        player.hasChangedDimension();
    }
}
