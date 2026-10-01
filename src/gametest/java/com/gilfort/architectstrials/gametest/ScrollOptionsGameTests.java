package com.gilfort.architectstrials.gametest;

import java.util.Optional;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.ChallengeClock;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.portal.ChallengePortal;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.run.RunCompletion;
import com.gilfort.architectstrials.scroll.ScrollActivation;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.travel.ChallengeTravel;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-12 (scroll options: group size, portal lifetime, re-entry, late join). Scrolls target
 * {@code minecraft:the_nether} tier 2; portals are ticked manually.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ScrollOptionsGameTests {

    private static final int TIER = 2;

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
            register(helper, "options_time_limit_covers_portal", ScrollOptionsGameTests::timeLimitCoversPortal);
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
        ChallengePortal portal = openActivePortal(helper, owner, new ScrollOptions(2, 60, false));
        ServerLevel nether = TestPlayers.challengeLevel(helper);

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
        ChallengePortal portal = openActivePortal(helper, owner, new ScrollOptions(2, 60, true));
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
        ChallengePortal portal = openActivePortal(helper, owner, options);
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
     * The time limit is raised so it is never shorter than the portal's open duration.
     */
    private static void timeLimitCoversPortal(GameTestHelper helper) {
        ServerPlayer owner = ScrollPortalGameTests.qualified(TestPlayers.atStart(helper, GameType.SURVIVAL), TIER);
        ItemStack scroll = ScrollPortalGameTests.scroll(Level.NETHER.identifier(), TIER);
        scroll.set(ModDataComponents.SCROLL_OPTIONS.get(), new ScrollOptions(4, 600, false));
        scroll.set(ModDataComponents.TIME_LIMIT.get(), 1);
        ScrollPortalGameTests.clearPortalSpace(helper);
        ChallengePortal portal = ScrollActivation.activate(owner, scroll, helper.absolutePos(ScrollPortalGameTests.PORTAL), 0.0F)
                .portal().orElseThrow();
        ChallengeInstance instance = InstanceManager.data(TestPlayers.challengeLevel(helper)).get(portal.instanceId()).orElseThrow();
        helper.assertTrue(instance.options().equals(new ScrollOptions(4, 600, false)), "Scroll options were not fixed into the instance");
        helper.assertTrue(instance.timeLimit() >= 600L * ChallengeClock.TICKS_PER_SECOND,
                "Time limit is shorter than the portal open duration: " + instance.timeLimit());
        portal.discard();
        TestPlayers.finish(helper, owner);
    }

    private static ChallengePortal openActivePortal(GameTestHelper helper, ServerPlayer owner, ScrollOptions options) {
        ItemStack scroll = ScrollPortalGameTests.scroll(Level.NETHER.identifier(), TIER);
        scroll.set(ModDataComponents.SCROLL_OPTIONS.get(), options);
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
        portal.tick();
        player.hasChangedDimension();
    }
}
