package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-21 (the {@code /at} command alias).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class CommandAliasGameTests {

    private CommandAliasGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> helper.register(
                ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id("command_alias_at")),
                (Consumer<GameTestHelper>) CommandAliasGameTests::alias));
    }

    /**
     * {@code /at} has the same sub commands, results and tab completion as {@code /architectstrials}.
     */
    private static void alias(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("architectstrials");
        CommandNode<CommandSourceStack> alias = dispatcher.getRoot().getChild("at");
        helper.assertTrue(root != null && alias != null, "Root command or alias is missing");
        List<String> rootChildren = root.getChildren().stream().map(CommandNode::getName).sorted().toList();
        List<String> aliasChildren = alias.getChildren().stream().map(CommandNode::getName).sorted().toList();
        helper.assertTrue(rootChildren.equals(aliasChildren), "Alias sub commands differ: " + aliasChildren + " vs " + rootChildren);
        try {
            int viaRoot = dispatcher.execute("architectstrials theme list", source);
            int viaAlias = dispatcher.execute("at theme list", source);
            helper.assertTrue(viaRoot == viaAlias && viaAlias > 0, "Alias result differs: " + viaAlias + " vs " + viaRoot);
        } catch (CommandSyntaxException e) {
            helper.fail("Command failed: " + e.getMessage());
            return;
        }
        List<String> suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("at theme ", source)).join().getList().stream()
                .map(Suggestion::getText).toList();
        helper.assertTrue(suggestions.contains("list"), "Alias has no tab completion: " + suggestions);
        helper.succeed();
    }
}
