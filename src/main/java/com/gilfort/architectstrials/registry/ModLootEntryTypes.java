package com.gilfort.architectstrials.registry;

import java.util.function.Supplier;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.loot.LootSetupEntry;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for the loot pool entry types of Architect's Trials.
 */
public final class ModLootEntryTypes {

    /** Deferred register for loot pool entry types in the {@code architectstrials} namespace. */
    public static final DeferredRegister<MapCodec<? extends LootPoolEntryContainer>> LOOT_ENTRY_TYPES =
            DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, ArchitectsTrials.MOD_ID);

    /** {@code architectstrials:loot_setup}: rolls the loot setup of the block entity at the loot origin. */
    public static final Supplier<MapCodec<LootSetupEntry>> LOOT_SETUP = LOOT_ENTRY_TYPES.register("loot_setup", () -> LootSetupEntry.MAP_CODEC);

    private ModLootEntryTypes() {
    }

    /**
     * Attaches the loot entry type register to the mod event bus.
     *
     * @param modEventBus the mod-specific event bus
     */
    public static void register(IEventBus modEventBus) {
        LOOT_ENTRY_TYPES.register(modEventBus);
    }
}
