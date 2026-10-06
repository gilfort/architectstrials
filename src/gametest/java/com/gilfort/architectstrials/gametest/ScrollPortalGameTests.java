package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.portal.ChallengePortal;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.registry.ModItems;
import com.gilfort.architectstrials.scroll.ScrollActivation;
import com.gilfort.architectstrials.scroll.ScrollTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-07 (base scroll and entry portal, solo default).
 * <p>
 * Scrolls target {@code minecraft:the_nether} tier 2 (the spawn platform). Portals are ticked manually to
 * make state changes deterministic.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ScrollPortalGameTests {

    private static final int PLATFORM_TIER = 2;
    static final BlockPos PORTAL = new BlockPos(3, 1, 1);

    private ScrollPortalGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "scroll_opens_portal_and_owner_enters", ScrollPortalGameTests::opensPortalAndOwnerEnters);
            register(helper, "scroll_failures_keep_scroll", ScrollPortalGameTests::failuresKeepScroll);
            register(helper, "portal_expires_unused", ScrollPortalGameTests::expiresUnused);
            register(helper, "portal_expired_scroll_drop_chance", ScrollPortalGameTests::expiredScrollDropChance);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * A scroll opens a forming portal and is consumed; the portal becomes active; other players cannot enter;
     * the owner enters, lands in the challenge and the portal closes behind them.
     */
    private static void opensPortalAndOwnerEnters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = qualified(TestPlayers.atStart(helper, GameType.SURVIVAL), PLATFORM_TIER);
        ServerPlayer stranger = TestPlayers.atStart(helper, GameType.SURVIVAL);
        ItemStack scroll = scroll(Level.NETHER.identifier(), PLATFORM_TIER);
        clearPortalSpace(helper);

        ScrollActivation.Result result = ScrollActivation.activate(owner, scroll, helper.absolutePos(PORTAL), 0.0F);
        helper.assertTrue(result.succeeded(), "Activation failed: " + result.message().map(m -> m.getString()).orElse(""));
        helper.assertTrue(scroll.isEmpty(), "Scroll was not consumed");
        ChallengePortal portal = result.portal().orElseThrow();
        helper.assertFalse(portal.isActive(), "Portal must start in the forming state");
        helper.assertTrue(InstanceManager.data(TestPlayers.challengeLevel(helper)).get(portal.instanceId()).isPresent(), "Instance was not created");

        for (int i = 0; i <= ChallengePortal.FORMING_TICKS; i++) {
            portal.tick();
        }
        helper.assertTrue(portal.isActive(), "Portal did not become active");

        Vec3 inside = portal.position();
        TestPlayers.teleport(stranger, level, inside);
        portal.tick();
        helper.assertTrue(stranger.level() == level, "A foreign player entered a solo portal");

        TestPlayers.teleport(owner, level, inside);
        portal.tick();
        helper.assertTrue(owner.level().dimension() == Level.NETHER, "Owner did not enter the challenge");
        helper.assertTrue(portal.isRemoved(), "Solo portal did not close after the first pass-through");

        TestPlayers.finish(helper, stranger);
        TestPlayers.finish(helper, owner);
    }

    /**
     * Every failure leaves the scroll untouched and opens no portal.
     */
    private static void failuresKeepScroll(GameTestHelper helper) {
        ServerPlayer player = qualified(TestPlayers.atStart(helper, GameType.SURVIVAL), 99);
        BlockPos portalPos = helper.absolutePos(PORTAL);

        ItemStack blank = new ItemStack(ModItems.CHALLENGE_SCROLL.get());
        assertFailureKeeps(helper, player, blank, portalPos, "blank scroll");

        ItemStack emptyPool = scroll(Level.NETHER.identifier(), 99);
        clearPortalSpace(helper);
        assertFailureKeeps(helper, player, emptyPool, portalPos, "empty pool");

        ItemStack unknownTheme = scroll(ArchitectsTrials.id("gametest_missing"), 1);
        assertFailureKeeps(helper, player, unknownTheme, portalPos, "unknown theme");

        ItemStack valid = scroll(Level.NETHER.identifier(), PLATFORM_TIER);
        helper.setBlock(PORTAL.above(), Blocks.STONE);
        assertFailureKeeps(helper, player, valid, portalPos, "missing 1x2 space");

        TestPlayers.teleport(player, TestPlayers.challengeLevel(helper), TestPlayers.CHALLENGE_POSITION);
        assertFailureKeeps(helper, player, valid, BlockPos.containing(TestPlayers.CHALLENGE_POSITION), "inside a challenge dimension");

        helper.assertTrue(portalsNear(helper).isEmpty(), "A portal was opened despite a failure");
        TestPlayers.finish(helper, player);
    }

    /**
     * An active portal nobody enters collapses after the timeout and cleans up its instance.
     */
    private static void expiresUnused(GameTestHelper helper) {
        ServerPlayer owner = qualified(TestPlayers.atStart(helper, GameType.SURVIVAL), PLATFORM_TIER);
        clearPortalSpace(helper);
        ChallengePortal portal = ScrollActivation.activate(owner, scroll(Level.NETHER.identifier(), PLATFORM_TIER),
                helper.absolutePos(PORTAL), 0.0F).portal().orElseThrow();
        ServerLevel challenge = TestPlayers.challengeLevel(helper);

        int previousTimeout = ArchitectsTrialsConfig.PORTAL_TIMEOUT_SECONDS.getAsInt();
        ArchitectsTrialsConfig.PORTAL_TIMEOUT_SECONDS.set(1);
        try {
            for (int i = 0; i <= ChallengePortal.FORMING_TICKS + 25 && !portal.isRemoved(); i++) {
                portal.tick();
            }
        } finally {
            ArchitectsTrialsConfig.PORTAL_TIMEOUT_SECONDS.set(previousTimeout);
        }
        helper.assertTrue(portal.isRemoved(), "Unused portal did not collapse");
        helper.assertTrue(InstanceManager.data(challenge).get(portal.instanceId()).isEmpty(), "Instance of the expired portal was not cleaned up");
        TestPlayers.finish(helper, owner);
    }

    /**
     * The scroll of an unused portal drops back according to {@code unusedPortalScrollDropChance}: never at 0.0,
     * always at 1.0.
     */
    private static void expiredScrollDropChance(GameTestHelper helper) {
        ServerPlayer owner = qualified(TestPlayers.atStart(helper, GameType.SURVIVAL), PLATFORM_TIER);
        double previousChance = ArchitectsTrialsConfig.UNUSED_PORTAL_SCROLL_DROP_CHANCE.getAsDouble();
        int previousTimeout = ArchitectsTrialsConfig.PORTAL_TIMEOUT_SECONDS.getAsInt();
        ArchitectsTrialsConfig.PORTAL_TIMEOUT_SECONDS.set(1);
        try {
            ArchitectsTrialsConfig.UNUSED_PORTAL_SCROLL_DROP_CHANCE.set(0.0);
            helper.assertTrue(droppedScrollsAfterExpiry(helper, owner) == 0, "Scroll dropped although the chance is 0");
            ArchitectsTrialsConfig.UNUSED_PORTAL_SCROLL_DROP_CHANCE.set(1.0);
            helper.assertTrue(droppedScrollsAfterExpiry(helper, owner) == 1, "Scroll did not drop although the chance is 1");
        } finally {
            ArchitectsTrialsConfig.UNUSED_PORTAL_SCROLL_DROP_CHANCE.set(previousChance);
            ArchitectsTrialsConfig.PORTAL_TIMEOUT_SECONDS.set(previousTimeout);
        }
        TestPlayers.finish(helper, owner);
    }

    /**
     * Opens a portal, lets it expire unused and returns the number of scroll item entities dropped around it.
     * Removes the dropped items again afterwards.
     */
    private static int droppedScrollsAfterExpiry(GameTestHelper helper, ServerPlayer owner) {
        clearPortalSpace(helper);
        ChallengePortal portal = ScrollActivation.activate(owner, scroll(Level.NETHER.identifier(), PLATFORM_TIER),
                helper.absolutePos(PORTAL), 0.0F).portal().orElseThrow();
        for (int i = 0; i <= ChallengePortal.FORMING_TICKS + 25 && !portal.isRemoved(); i++) {
            portal.tick();
        }
        helper.assertTrue(portal.isRemoved(), "Unused portal did not collapse");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(PORTAL)).inflate(3),
                item -> item.getItem().is(ModItems.CHALLENGE_SCROLL.get()));
        drops.forEach(ItemEntity::discard);
        return drops.size();
    }

    private static void assertFailureKeeps(GameTestHelper helper, ServerPlayer player, ItemStack scroll, BlockPos pos, String scenario) {
        int count = scroll.getCount();
        ScrollActivation.Result result = ScrollActivation.activate(player, scroll, pos, 0.0F);
        helper.assertFalse(result.succeeded(), "Activation succeeded for " + scenario);
        helper.assertTrue(result.message().isPresent(), "No failure message for " + scenario);
        helper.assertTrue(scroll.getCount() == count, "Scroll was consumed for " + scenario);
    }

    /**
     * Raises the player's nether rank so scrolls up to {@code level} may be used.
     */
    static ServerPlayer qualified(ServerPlayer player, int level) {
        player.setData(ModAttachments.RANK, player.getData(ModAttachments.RANK).withLevel(Level.NETHER.identifier(), level));
        return player;
    }

    static void clearPortalSpace(GameTestHelper helper) {
        helper.setBlock(PORTAL.below(), Blocks.STONE);
        helper.setBlock(PORTAL, Blocks.AIR);
        helper.setBlock(PORTAL.above(), Blocks.AIR);
    }

    static ItemStack scroll(Identifier theme, int tier) {
        ItemStack scroll = new ItemStack(ModItems.CHALLENGE_SCROLL.get());
        scroll.set(ModDataComponents.SCROLL_TARGET.get(), new ScrollTarget(theme, tier));
        return scroll;
    }

    private static List<ChallengePortal> portalsNear(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ChallengePortal.class, new AABB(helper.absolutePos(PORTAL)).inflate(2));
    }
}
