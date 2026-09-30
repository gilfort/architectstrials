package com.gilfort.architectstrials.registry;

import java.util.function.Supplier;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.run.RunCompletedTrigger;

import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for the advancement criteria of Architect's Trials.
 */
public final class ModCriteriaTriggers {

    /** Deferred register for criterion triggers in the {@code architectstrials} namespace. */
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, ArchitectsTrials.MOD_ID);

    /** {@code architectstrials:run_completed}: a player completed a run via an exit. */
    public static final Supplier<RunCompletedTrigger> RUN_COMPLETED = TRIGGERS.register("run_completed", RunCompletedTrigger::new);

    private ModCriteriaTriggers() {
    }

    /**
     * Attaches the trigger register to the mod event bus.
     *
     * @param modEventBus the mod-specific event bus
     */
    public static void register(IEventBus modEventBus) {
        TRIGGERS.register(modEventBus);
    }
}
