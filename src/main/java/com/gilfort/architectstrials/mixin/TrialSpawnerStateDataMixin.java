package com.gilfort.architectstrials.mixin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.gilfort.architectstrials.marker.TrialSpawnerMarkerResolver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerStateData;

/**
 * Keeps trial spawners created from Trial Spawner Markers from ever turning ominous: for them, the search for a
 * player with Bad Omen or Trial Omen finds nobody, so the omen is neither converted nor consumed and the spawner
 * keeps the builder's balancing.
 */
@Mixin(TrialSpawnerStateData.class)
public abstract class TrialSpawnerStateDataMixin {

    /**
     * Skips the ominous player search for marker-created trial spawners.
     *
     * @param level    the level
     * @param players  the players in line of sight
     * @param original the original search
     * @param pos      the trial spawner position
     * @return no player for marker-created spawners, otherwise the original result
     */
    @WrapOperation(method = "tryDetectPlayers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData;findPlayerWithOminousEffect(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)Ljava/util/Optional;"))
    private Optional<Pair<Player, Holder<MobEffect>>> architectstrials$skipOminousSearch(ServerLevel level, List<UUID> players,
            Operation<Optional<Pair<Player, Holder<MobEffect>>>> original, @Local(argsOnly = true) BlockPos pos) {
        if (TrialSpawnerMarkerResolver.isOminousBlocked(level, pos)) {
            return Optional.empty();
        }
        return original.call(level, players);
    }
}
