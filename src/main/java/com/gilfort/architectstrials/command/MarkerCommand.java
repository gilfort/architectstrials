package com.gilfort.architectstrials.command;

import java.util.Optional;

import com.gilfort.architectstrials.block.LootTableReference;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Commands that configure the marker the executing player is looking at:
 * <ul>
 * <li>{@code /architectstrials marker loot_table <id>} — sets the loot table of the marker</li>
 * <li>{@code /architectstrials marker loot_table clear} — removes the loot table</li>
 * </ul>
 * Supported by every block whose block entity is a {@link LootTableReference} (currently the exit marker, whose
 * loot table overrides the completion bonus).
 */
final class MarkerCommand {

    private static final SuggestionProvider<CommandSourceStack> LOOT_TABLE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(context.getSource().getServer().reloadableRegistries().lookup()
                    .lookupOrThrow(Registries.LOOT_TABLE).listElementIds().map(ResourceKey::identifier), builder);

    private MarkerCommand() {
    }

    /**
     * Builds the {@code marker} sub command tree.
     *
     * @return the literal builder for {@code marker}
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("marker")
                .then(Commands.literal("loot_table")
                        .then(Commands.literal("clear").executes(context -> setLootTable(context, Optional.empty())))
                        .then(Commands.argument("id", IdentifierArgument.id())
                                .suggests(LOOT_TABLE_SUGGESTIONS)
                                .executes(context -> setLootTable(context, Optional.of(IdentifierArgument.getId(context, "id"))))));
    }

    private static int setLootTable(CommandContext<CommandSourceStack> context, Optional<Identifier> id) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                || !(player.level().getBlockEntity(blockHit.getBlockPos()) instanceof LootTableReference reference)) {
            source.sendFailure(Component.translatable("commands.architectstrials.marker.no_target"));
            return 0;
        }
        Optional<ResourceKey<LootTable>> key = id.map(value -> ResourceKey.create(Registries.LOOT_TABLE, value));
        reference.setLootTableReference(key);
        source.sendSuccess(() -> key
                .map(value -> Component.translatable("commands.architectstrials.marker.loot_table.set", value.identifier().toString()))
                .orElseGet(() -> Component.translatable("commands.architectstrials.marker.loot_table.cleared")), false);
        return 1;
    }
}
