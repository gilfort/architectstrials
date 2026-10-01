package com.gilfort.architectstrials.loot;

/**
 * A block entity that keeps loot setups (see {@link LootSetup}) itself, e.g. the Exit Marker (completion bonus)
 * and the Trial Spawner Marker (normal and ominous reward). Vanilla containers keep theirs in the persistent block
 * entity data instead (see {@link LootSetups}).
 */
public interface LootSetupHolder {

    /**
     * Returns a loot setup.
     *
     * @param ominous {@code true} for the ominous variant (only used by holders with {@link #hasOminousVariant()})
     * @return the setup (possibly {@link LootSetup#EMPTY})
     */
    LootSetup lootSetup(boolean ominous);

    /**
     * Sets a loot setup.
     *
     * @param ominous {@code true} for the ominous variant
     * @param setup   the setup
     */
    void setLootSetup(boolean ominous, LootSetup setup);

    /** @return {@code true} if the holder has a separate ominous setup */
    default boolean hasOminousVariant() {
        return false;
    }
}
