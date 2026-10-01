package com.gilfort.architectstrials.loot;

import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.SingleEntryContainerBase;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

/**
 * Loot pool entry type {@code architectstrials:loot_setup}: rolls the {@link LootSetup} of the block entity at the
 * loot origin (used by the {@link LootSetups#PLACEHOLDER} loot table of lootable containers). Without an origin
 * or setup it yields nothing.
 */
public class LootSetupEntry extends SingleEntryContainerBase {

    /** Codec (only the common entry fields). */
    public static final MapCodec<LootSetupEntry> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> uniformFields(instance)
            .apply(instance, LootSetupEntry::new));

    private LootSetupEntry(int weight, int quality, Optional<Holder<LootItemCondition>> condition, Optional<Holder<LootItemFunction>> modifier) {
        super(weight, quality, condition, modifier);
    }

    @Override
    public MapCodec<LootSetupEntry> codec() {
        return MAP_CODEC;
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
        ServerLevel level = context.getLevel();
        if (origin == null) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(BlockPos.containing(origin));
        if (blockEntity == null) {
            return;
        }
        LootSetups.get(blockEntity, false).roll(context.getRandom(), (key, items) -> {
            if (!LootSetups.PLACEHOLDER.equals(key)) {
                level.getServer().reloadableRegistries().getLootTable(key).getRandomItemsRaw(context, items);
            }
        }, output);
    }
}
