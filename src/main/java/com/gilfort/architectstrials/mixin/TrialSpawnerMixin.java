package com.gilfort.architectstrials.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gilfort.architectstrials.loot.LootSetups;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Lets trial spawners created from a Trial Spawner Marker eject a loot setup (US-25). Vanilla rolls the reward
 * without any loot context (no position, no block entity), so a loot table cannot find the spawner's setup; there
 * is no event for it either. When the spawner ejects {@link LootSetups#PLACEHOLDER}, the setup is ejected instead.
 */
@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerMixin {

    @Shadow
    public abstract boolean isOminous();

    /**
     * Ejects the spawner's loot setup instead of the placeholder loot table.
     *
     * @param level             the level
     * @param pos               the trial spawner position
     * @param ejectingLootTable the loot table vanilla would eject
     * @param ci                the callback
     */
    @Inject(method = "ejectReward", at = @At("HEAD"), cancellable = true)
    private void architectstrials$ejectLootSetup(ServerLevel level, BlockPos pos, ResourceKey<LootTable> ejectingLootTable, CallbackInfo ci) {
        if (LootSetups.PLACEHOLDER.equals(ejectingLootTable)) {
            LootSetups.ejectTrialReward(level, pos, this.isOminous());
            ci.cancel();
        }
    }
}
