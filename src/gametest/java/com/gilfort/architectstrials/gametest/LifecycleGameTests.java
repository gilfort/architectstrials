package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.ChallengeClock;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceLifecycle;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.scroll.ScrollActivation;
import com.gilfort.architectstrials.slot.SlotManager;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-11 (instance lifecycle, time limit and cleanup).
 * <p>
 * Deadlines are moved per instance instead of advancing the global {@link ChallengeClock}, so parallel tests are
 * not affected.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class LifecycleGameTests {

    private static final int PLATFORM_TIER = 2;

    private LifecycleGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "lifecycle_time_limit_expiry", LifecycleGameTests::timeLimitExpiry);
            register(helper, "lifecycle_cleanup_and_offline_participants", LifecycleGameTests::cleanupAndOfflineParticipants);
            register(helper, "lifecycle_portal_grace", LifecycleGameTests::portalGrace);
            register(helper, "lifecycle_scroll_time_limit", LifecycleGameTests::scrollTimeLimit);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Joining makes the player a participant (login keeps them inside); when the time limit expires the player
     * is sent back without completing the run and the instance is removed.
     */
    private static void timeLimitExpiry(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengeInstance instance = create(nether);
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        InstanceManager.join(player, nether, instance);
        helper.assertTrue(InstanceManager.isParticipant(player), "Joined player is not a participant");

        ChallengeInstance joined = InstanceManager.data(nether).get(instance.id()).orElseThrow();
        InstanceManager.update(nether, joined.withDeadline(ChallengeClock.now(server)));
        InstanceLifecycle.update(server);

        helper.assertTrue(player.level().dimension() == Level.OVERWORLD, "Player was not sent back when time ran out");
        helper.assertTrue(player.getData(ModAttachments.RUN_STATISTICS).completed(Level.NETHER.identifier(), PLATFORM_TIER) == 0,
                "Time limit expiry counted as completed run");
        helper.assertTrue(InstanceManager.data(nether).get(instance.id()).isEmpty(), "Expired instance was not removed");
        helper.assertTrue(SlotManager.isClearing(nether, instance.slot()), "Slot of the expired instance is not being cleared");
        helper.assertFalse(InstanceManager.isParticipant(player), "Player still counts as participant of a removed instance");
        TestPlayers.finish(helper, player);
    }

    /**
     * Offline participants keep an instance alive after its portal closed; once nobody belongs to it anymore,
     * the instance is cleaned up.
     */
    private static void cleanupAndOfflineParticipants(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengeInstance instance = create(nether);
        UUID offlinePlayer = UUID.randomUUID();
        InstanceManager.update(nether, instance.withParticipants(List.of(offlinePlayer)));
        InstanceManager.closePortal(nether, instance.id());

        InstanceLifecycle.update(server);
        ChallengeInstance kept = InstanceManager.data(nether).get(instance.id()).orElse(null);
        helper.assertTrue(kept != null && kept.state() == ChallengeInstance.LifecycleState.ACTIVE,
                "Instance with an offline participant was not kept active");

        InstanceManager.update(nether, kept.withParticipants(List.of()));
        InstanceLifecycle.update(server);
        helper.assertTrue(InstanceManager.data(nether).get(instance.id()).isEmpty(), "Instance without participants and portal was not cleaned up");
        helper.succeed();
    }

    /**
     * An open portal that was never used (e.g. its chunk unloaded) counts as closed after its deadline plus
     * grace, and the empty instance is cleaned up; before that, it is kept.
     */
    private static void portalGrace(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengeInstance instance = create(nether);

        InstanceLifecycle.update(server);
        helper.assertTrue(InstanceManager.data(nether).get(instance.id()).map(ChallengeInstance::state)
                .orElse(null) == ChallengeInstance.LifecycleState.IDLE, "Fresh instance with open portal is not idle");

        InstanceManager.update(nether, instance.withPortalDeadline(ChallengeClock.now(server) - 1000L));
        InstanceLifecycle.update(server);
        helper.assertTrue(InstanceManager.data(nether).get(instance.id()).isEmpty(), "Expired unused portal did not lead to cleanup");
        helper.succeed();
    }

    /**
     * The time limit comes from the scroll's {@code architectstrials:time_limit} component.
     */
    private static void scrollTimeLimit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = ScrollPortalGameTests.qualified(TestPlayers.atStart(helper, GameType.SURVIVAL), PLATFORM_TIER);
        ItemStack scroll = ScrollPortalGameTests.scroll(Level.NETHER.identifier(), PLATFORM_TIER);
        scroll.set(ModDataComponents.TIME_LIMIT.get(), 2);
        ScrollPortalGameTests.clearPortalSpace(helper);

        ScrollActivation.Result result = ScrollActivation.activate(player, scroll, helper.absolutePos(ScrollPortalGameTests.PORTAL), 0.0F);
        helper.assertTrue(result.succeeded(), "Scroll activation failed");
        ChallengeInstance instance = InstanceManager.data(TestPlayers.challengeLevel(helper)).get(result.portal().orElseThrow().instanceId()).orElseThrow();
        long limit = instance.deadline() - ChallengeClock.now(server);
        long expected = 2L * 60L * ChallengeClock.TICKS_PER_SECOND;
        helper.assertTrue(limit <= expected && limit > expected - 40, "Time limit does not match the scroll component: " + limit);
        result.portal().ifPresent(portal -> portal.discard());
        TestPlayers.finish(helper, player);
    }

    private static ChallengeInstance create(ServerLevel nether) {
        InstanceCreation result = InstanceManager.create(nether, ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow(),
                PLATFORM_TIER, nether.getRandom(), InstanceManager.defaultTimeLimitTicks());
        if (result instanceof InstanceCreation.Success(ChallengeInstance instance)) {
            return instance;
        }
        throw new IllegalStateException("Instance creation failed: " + ((InstanceCreation.Failure) result).reason().getString());
    }
}
