package com.gilfort.architectstrials.command;

import java.util.Optional;

import com.gilfort.architectstrials.block.LootTableReference;
import com.gilfort.architectstrials.block.MobMarkerBlockEntity;
import com.gilfort.architectstrials.block.TrialSpawnerMarkerBlockEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Commands that configure the marker or container the executing player is looking at:
 * <ul>
 * <li>{@code /architectstrials marker loot_table <id>} — sets the loot table</li>
 * <li>{@code /architectstrials marker loot_table clear} — removes the loot table</li>
 * <li>{@code /architectstrials marker ominous_loot_table <id>|clear} — reward of the ominous variant of a Trial
 * Spawner Marker</li>
 * <li>{@code /architectstrials marker equipment_table <row> <id>|clear} — equipment loot table of a mob marker row
 * (rows are numbered from 1; on the Trial Spawner Marker 4–6 are the ominous page)</li>
 * <li>{@code /architectstrials marker info} — shows the loot table(s) and, for Trial Spawner Markers, the ominous
 * setting (otherwise invisible in the editor)</li>
 * </ul>
 * Supported targets: every block whose block entity is a {@link LootTableReference} — the exit marker (overrides
 * the completion bonus) and the trial spawner marker (wave reward) — and every vanilla lootable container
 * (chest, barrel, shulker box, dispenser, …; pool loot rolled on first opening).
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
                .then(Commands.literal("info").executes(MarkerCommand::info))
                .then(Commands.literal("equipment_table")
                        .then(Commands.argument("row", IntegerArgumentType.integer(1))
                                .then(Commands.literal("clear").executes(context -> setEquipmentTable(context, Optional.empty())))
                                .then(Commands.argument("id", IdentifierArgument.id())
                                        .suggests(LOOT_TABLE_SUGGESTIONS)
                                        .executes(context -> setEquipmentTable(context, Optional.of(IdentifierArgument.getId(context, "id")))))))
                .then(Commands.literal("ominous_loot_table")
                        .then(Commands.literal("clear").executes(context -> setOminousLootTable(context, Optional.empty())))
                        .then(Commands.argument("id", IdentifierArgument.id())
                                .suggests(LOOT_TABLE_SUGGESTIONS)
                                .executes(context -> setOminousLootTable(context, Optional.of(IdentifierArgument.getId(context, "id"))))))
                .then(Commands.literal("loot_table")
                        .then(Commands.literal("clear").executes(context -> setLootTable(context, Optional.empty())))
                        .then(Commands.argument("id", IdentifierArgument.id())
                                .suggests(LOOT_TABLE_SUGGESTIONS)
                                .executes(context -> setLootTable(context, Optional.of(IdentifierArgument.getId(context, "id"))))));
    }

    private static int setLootTable(CommandContext<CommandSourceStack> context, Optional<Identifier> id) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Optional<BlockEntity> target = target(source.getPlayerOrException());
        Optional<ResourceKey<LootTable>> key = id.map(value -> ResourceKey.create(Registries.LOOT_TABLE, value));
        if (target.isPresent() && target.get() instanceof LootTableReference reference) {
            reference.setLootTableReference(key);
        } else if (target.isPresent() && target.get() instanceof RandomizableContainer container) {
            // Seed 0 = random seed when the loot is rolled; placement assigns a fresh seed per instance anyway.
            container.setLootTable(key.orElse(null), 0L);
            target.get().setChanged();
        } else {
            source.sendFailure(Component.translatable("commands.architectstrials.marker.no_target"));
            return 0;
        }
        source.sendSuccess(() -> key
                .map(value -> Component.translatable("commands.architectstrials.marker.loot_table.set", value.identifier().toString()))
                .orElseGet(() -> Component.translatable("commands.architectstrials.marker.loot_table.cleared")), false);
        return 1;
    }

    private static int setOminousLootTable(CommandContext<CommandSourceStack> context, Optional<Identifier> id)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!(target(source.getPlayerOrException()).orElse(null) instanceof TrialSpawnerMarkerBlockEntity marker)) {
            source.sendFailure(Component.translatable("commands.architectstrials.marker.no_trial_target"));
            return 0;
        }
        Optional<ResourceKey<LootTable>> key = id.map(value -> ResourceKey.create(Registries.LOOT_TABLE, value));
        marker.setOminousLootTable(key);
        source.sendSuccess(() -> key
                .map(value -> Component.translatable("commands.architectstrials.marker.ominous_loot_table.set", value.identifier().toString()))
                .orElseGet(() -> Component.translatable("commands.architectstrials.marker.ominous_loot_table.cleared")), false);
        return 1;
    }

    private static int setEquipmentTable(CommandContext<CommandSourceStack> context, Optional<Identifier> id) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!(target(source.getPlayerOrException()).orElse(null) instanceof MobMarkerBlockEntity marker)) {
            source.sendFailure(Component.translatable("commands.architectstrials.marker.no_mob_target"));
            return 0;
        }
        int row = IntegerArgumentType.getInteger(context, "row");
        if (row > marker.rows()) {
            source.sendFailure(Component.translatable("commands.architectstrials.marker.equipment_table.no_row", row, marker.rows()));
            return 0;
        }
        Optional<ResourceKey<LootTable>> key = id.map(value -> ResourceKey.create(Registries.LOOT_TABLE, value));
        marker.setEquipmentTable(row - 1, key);
        source.sendSuccess(() -> key
                .map(value -> Component.translatable("commands.architectstrials.marker.equipment_table.set", row, value.identifier().toString()))
                .orElseGet(() -> Component.translatable("commands.architectstrials.marker.equipment_table.cleared", row)), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Optional<BlockEntity> target = target(source.getPlayerOrException());
        Optional<ResourceKey<LootTable>> key;
        if (target.isPresent() && target.get() instanceof LootTableReference reference) {
            key = reference.lootTableReference();
        } else if (target.isPresent() && target.get() instanceof RandomizableContainer container) {
            key = Optional.ofNullable(container.getLootTable());
        } else if (target.isPresent() && target.get() instanceof MobMarkerBlockEntity) {
            key = Optional.empty();
        } else {
            source.sendFailure(Component.translatable("commands.architectstrials.marker.no_target"));
            return 0;
        }
        Component name = target.get().getBlockState().getBlock().getName();
        if (!(target.get() instanceof MobMarkerBlockEntity) || target.get() instanceof LootTableReference) {
            source.sendSuccess(() -> key
                    .map(value -> Component.translatable("commands.architectstrials.marker.info.loot_table", name, value.identifier().toString()))
                    .orElseGet(() -> Component.translatable("commands.architectstrials.marker.info.none", name)), false);
        }
        if (target.get() instanceof MobMarkerBlockEntity mobMarker) {
            for (int row = 0; row < mobMarker.rows(); row++) {
                int number = row + 1;
                mobMarker.equipmentTable(row).ifPresent(table -> source.sendSuccess(() -> Component.translatable(
                        "commands.architectstrials.marker.info.equipment_table", number, table.identifier().toString()), false));
            }
        }
        if (target.get() instanceof TrialSpawnerMarkerBlockEntity marker) {
            Component ominous = Component.translatable(marker.ominousAllowed()
                    ? "commands.architectstrials.marker.info.ominous_allowed" : "commands.architectstrials.marker.info.ominous_blocked");
            Component reward = marker.ominousLootTable()
                    .map(value -> Component.translatable("commands.architectstrials.marker.info.ominous_loot_table", value.identifier().toString()))
                    .orElseGet(() -> Component.translatable("commands.architectstrials.marker.info.ominous_loot_table_default"));
            source.sendSuccess(() -> Component.translatable("commands.architectstrials.marker.info.ominous", ominous, reward), false);
        }
        return key.isPresent() ? 1 : 0;
    }

    /**
     * Returns the block entity the player is looking at, if it supports a loot table.
     */
    private static Optional<BlockEntity> target(ServerPlayer player) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        BlockEntity blockEntity = player.level().getBlockEntity(blockHit.getBlockPos());
        return blockEntity instanceof LootTableReference || blockEntity instanceof RandomizableContainer
                || blockEntity instanceof MobMarkerBlockEntity ? Optional.of(blockEntity) : Optional.empty();
    }
}
