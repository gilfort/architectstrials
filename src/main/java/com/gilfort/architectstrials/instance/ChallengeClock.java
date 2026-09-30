package com.gilfort.architectstrials.instance;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.mojang.serialization.Codec;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent server-tick clock for challenge timers. Advances once per server tick and is saved with the world,
 * so time limits only run while the server runs and survive restarts.
 */
public final class ChallengeClock extends SavedData {

    /** Saved data type; stored as {@code data/architectstrials/clock.dat} in the overworld. */
    public static final SavedDataType<ChallengeClock> TYPE = new SavedDataType<>(ArchitectsTrials.id("clock"), ChallengeClock::new,
            Codec.LONG.xmap(ChallengeClock::new, clock -> clock.ticks));

    /** Game ticks per second. */
    public static final int TICKS_PER_SECOND = 20;

    private long ticks;

    /** Creates a clock at zero. */
    public ChallengeClock() {
        this(0L);
    }

    private ChallengeClock(long ticks) {
        this.ticks = ticks;
    }

    /**
     * Returns the clock of a server.
     *
     * @param server the server
     * @return the clock, created on first access
     */
    public static ChallengeClock of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Returns the current clock value.
     *
     * @param server the server
     * @return the number of server ticks counted so far
     */
    public static long now(MinecraftServer server) {
        return of(server).ticks;
    }

    /**
     * Advances the clock.
     *
     * @param ticks the number of ticks to advance
     */
    public void advance(long ticks) {
        this.ticks += ticks;
        this.setDirty();
    }
}
