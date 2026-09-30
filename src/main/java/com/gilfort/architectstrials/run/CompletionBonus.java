package com.gilfort.architectstrials.run;

import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.ChallengeInstance;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Rolls and hands out the completion bonus of a run — a loot table separate from the structure's own loot.
 * <p>
 * Resolution: loot table set on the used exit → convention {@code <ns>:architectstrials/completion/<theme>/tier_<n>}
 * (namespace and path of the theme id) → no bonus (debug log only). The loot context contains the player and
 * their luck, so luck effects apply.
 */
public final class CompletionBonus {

    private CompletionBonus() {
    }

    /**
     * Returns the conventional bonus loot table of a theme and tier.
     *
     * @param theme the theme id
     * @param tier  the tier
     * @return the loot table key
     */
    public static ResourceKey<LootTable> conventionFor(Identifier theme, int tier) {
        return ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(theme.getNamespace(), "architectstrials/completion/" + theme.getPath() + "/tier_" + tier));
    }

    /**
     * Rolls the bonus for a player who completed a run and delivers it to their inventory; overflow is dropped
     * at their feet. Must be called after the player has been returned.
     *
     * @param player   the player
     * @param instance the completed instance
     * @param override the loot table set on the used exit, if any
     */
    public static void grant(ServerPlayer player, ChallengeInstance instance, Optional<ResourceKey<LootTable>> override) {
        ResourceKey<LootTable> key = override.orElseGet(() -> conventionFor(instance.theme(), instance.tier()));
        ServerLevel level = player.level();
        boolean exists = level.getServer().reloadableRegistries().lookup().lookup(Registries.LOOT_TABLE)
                .flatMap(registry -> registry.get(key)).isPresent();
        if (!exists) {
            ArchitectsTrials.LOGGER.debug("No completion bonus loot table {} for {} tier {}", key.identifier(), instance.theme(), instance.tier());
            return;
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);
        for (ItemStack stack : table.getRandomItems(params)) {
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.spawnAtLocation(level, stack);
            }
        }
    }
}
