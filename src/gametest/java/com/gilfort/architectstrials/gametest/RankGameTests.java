package com.gilfort.architectstrials.gametest;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.config.ArchitectsTrialsConfig;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.scroll.ScrollActivation;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-10 (rank gates scroll tiers). Scrolls target {@code minecraft:the_nether} tier 2.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class RankGameTests {

    private static final int TIER = 2;

    private RankGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "rank_gates_scroll_tiers", RankGameTests::gatesScrollTiers);
            register(helper, "rank_starting_level_zero", RankGameTests::startingLevelZero);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Players start at level 1: a tier-2 scroll is rejected and kept; raising the level via command (as a datapack
     * function would) allows it; {@code add} with a negative amount lowers it again; {@code get} returns the level.
     */
    private static void gatesScrollTiers(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        helper.assertTrue(player.getData(ModAttachments.RANK).level(Level.NETHER.identifier()) == 1, "Players do not start at level 1");

        ItemStack scroll = ScrollPortalGameTests.scroll(Level.NETHER.identifier(), TIER);
        ScrollPortalGameTests.clearPortalSpace(helper);
        ScrollActivation.Result rejected = ScrollActivation.activate(player, scroll, helper.absolutePos(ScrollPortalGameTests.PORTAL), 0.0F);
        helper.assertFalse(rejected.succeeded(), "Scroll above the player's level was accepted");
        helper.assertTrue(scroll.getCount() == 1, "Rejected scroll was consumed");

        run(helper, player, "architectstrials rank @s minecraft:the_nether set 2");
        helper.assertTrue(run(helper, player, "architectstrials rank @s minecraft:the_nether get") == 2, "get does not return the level");
        helper.assertTrue(player.getData(ModAttachments.RANK).allows(Level.NETHER.identifier(), TIER), "set did not raise the level");

        run(helper, player, "architectstrials rank @s minecraft:the_nether add -5");
        helper.assertTrue(player.getData(ModAttachments.RANK).level(Level.NETHER.identifier()) == 0, "add did not clamp at level 0");
        helper.assertFalse(player.getData(ModAttachments.RANK).allows(Level.NETHER.identifier(), 1), "Level 0 allows scrolls");

        run(helper, player, "architectstrials rank @s minecraft:the_nether set 2");
        ScrollActivation.Result accepted = ScrollActivation.activate(player, scroll, helper.absolutePos(ScrollPortalGameTests.PORTAL), 0.0F);
        helper.assertTrue(accepted.succeeded(), "Scroll was rejected after raising the level: "
                + accepted.message().map(message -> message.getString()).orElse(""));
        accepted.portal().ifPresent(portal -> portal.discard());
        TestPlayers.finish(helper, player);
    }

    /**
     * With {@code startingRank = 0}, players without an explicit level cannot use any scroll.
     */
    private static void startingLevelZero(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        int previous = ArchitectsTrialsConfig.STARTING_RANK.getAsInt();
        ArchitectsTrialsConfig.STARTING_RANK.set(0);
        try {
            helper.assertFalse(player.getData(ModAttachments.RANK).allows(Level.NETHER.identifier(), 1),
                    "Starting rank 0 did not block tier-1 scrolls");
        } finally {
            ArchitectsTrialsConfig.STARTING_RANK.set(previous);
        }
        TestPlayers.finish(helper, player);
    }

    /**
     * Runs a command as the player with operator permissions, like an advancement reward function does.
     */
    private static int run(GameTestHelper helper, ServerPlayer player, String command) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandSourceStack source = server.createCommandSourceStack().withEntity(player).withSuppressedOutput();
        try {
            return server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("Command failed: " + command + " — " + e.getMessage(), e);
        }
    }
}
