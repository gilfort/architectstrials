package com.gilfort.architectstrials.block;

import java.util.Optional;

import com.gilfort.architectstrials.menu.TrialSpawnerMarkerMenu;
import com.gilfort.architectstrials.registry.ModBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Block entity of the {@link TrialSpawnerMarkerBlock}. It has two pages of three marker rows (one mob type each):
 * the normal page and the ominous page. Per page: the number of simultaneous mobs; total mobs = sum of the page's
 * spawn egg counts (the counts also act as spawn weights). Further settings: whether the spawner may turn
 * ominous, the reward loot table and an optional ominous reward loot table.
 */
public class TrialSpawnerMarkerBlockEntity extends MobMarkerBlockEntity implements LootTableReference {

    /** Number of mob rows per page. */
    public static final int ROWS = 3;

    /** First row of the ominous page. */
    public static final int OMINOUS_FIRST_ROW = ROWS;

    /** Smallest allowed number of simultaneous mobs. */
    public static final int MIN_SIMULTANEOUS = 1;

    /** Largest allowed number of simultaneous mobs. */
    public static final int MAX_SIMULTANEOUS = 32;

    /** Default number of simultaneous mobs (vanilla default). */
    public static final int DEFAULT_SIMULTANEOUS = 2;

    /** Index of the normal simultaneous mob count in the menu data. */
    public static final int DATA_SIMULTANEOUS = 0;

    /** Index of the ominous simultaneous mob count in the menu data. */
    public static final int DATA_OMINOUS_SIMULTANEOUS = 1;

    /** Index of the "ominous allowed" flag (0/1) in the menu data. */
    public static final int DATA_OMINOUS_ALLOWED = 2;

    /** Number of menu data values. */
    public static final int DATA_COUNT = 3;

    private static final String SIMULTANEOUS_TAG = "simultaneous_mobs";
    private static final String OMINOUS_SIMULTANEOUS_TAG = "ominous_simultaneous_mobs";
    private static final String OMINOUS_ALLOWED_TAG = "ominous_allowed";
    private static final String OMINOUS_LOOT_TABLE_TAG = "ominous_loot_table";

    private int simultaneousMobs = DEFAULT_SIMULTANEOUS;
    private int ominousSimultaneousMobs = DEFAULT_SIMULTANEOUS;
    private boolean ominousAllowed;
    private Optional<ResourceKey<LootTable>> lootTable = Optional.empty();
    private Optional<ResourceKey<LootTable>> ominousLootTable = Optional.empty();

    /** Synchronizes the page settings with open menus. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            TrialSpawnerMarkerBlockEntity marker = TrialSpawnerMarkerBlockEntity.this;
            return switch (index) {
                case DATA_SIMULTANEOUS -> marker.simultaneousMobs;
                case DATA_OMINOUS_SIMULTANEOUS -> marker.ominousSimultaneousMobs;
                default -> marker.ominousAllowed ? 1 : 0;
            };
        }

        @Override
        public void set(int index, int value) {
            TrialSpawnerMarkerBlockEntity marker = TrialSpawnerMarkerBlockEntity.this;
            switch (index) {
                case DATA_SIMULTANEOUS -> marker.setSimultaneousMobs(false, value);
                case DATA_OMINOUS_SIMULTANEOUS -> marker.setSimultaneousMobs(true, value);
                default -> marker.setOminousAllowed(value != 0);
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    /**
     * Creates the block entity.
     *
     * @param pos   the position
     * @param state the block state
     */
    public TrialSpawnerMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.TRIAL_SPAWNER_MARKER.get(), pos, state, 2 * ROWS);
    }

    /**
     * Returns the first row of a page.
     *
     * @param ominous {@code true} for the ominous page
     * @return the first row index
     */
    public static int firstRow(boolean ominous) {
        return ominous ? OMINOUS_FIRST_ROW : 0;
    }

    /** @return the number of normal mobs alive at the same time (for one player) */
    public int simultaneousMobs() {
        return this.simultaneousMobs;
    }

    /**
     * Returns the number of mobs alive at the same time of a page.
     *
     * @param ominous {@code true} for the ominous page
     * @return the number of simultaneous mobs
     */
    public int simultaneousMobs(boolean ominous) {
        return ominous ? this.ominousSimultaneousMobs : this.simultaneousMobs;
    }

    /**
     * Sets the number of normal simultaneous mobs, clamped to {@value #MIN_SIMULTANEOUS}–{@value #MAX_SIMULTANEOUS}.
     *
     * @param value the new value
     */
    public void setSimultaneousMobs(int value) {
        this.setSimultaneousMobs(false, value);
    }

    /**
     * Sets the number of simultaneous mobs of a page, clamped to {@value #MIN_SIMULTANEOUS}–{@value #MAX_SIMULTANEOUS}.
     *
     * @param ominous {@code true} for the ominous page
     * @param value   the new value
     */
    public void setSimultaneousMobs(boolean ominous, int value) {
        int clamped = Mth.clamp(value, MIN_SIMULTANEOUS, MAX_SIMULTANEOUS);
        if (ominous) {
            this.ominousSimultaneousMobs = clamped;
        } else {
            this.simultaneousMobs = clamped;
        }
        this.setChanged();
    }

    /** @return {@code true} if the placed trial spawner may turn ominous */
    public boolean ominousAllowed() {
        return this.ominousAllowed;
    }

    /**
     * Sets whether the placed trial spawner may turn ominous.
     *
     * @param allowed {@code true} to allow it
     */
    public void setOminousAllowed(boolean allowed) {
        this.ominousAllowed = allowed;
        this.setChanged();
    }

    /** @return the sum of the normal page's spawn egg counts */
    public int totalMobs() {
        return this.totalMobs(false);
    }

    /**
     * Returns the sum of a page's spawn egg counts.
     *
     * @param ominous {@code true} for the ominous page
     * @return the total mob count
     */
    public int totalMobs(boolean ominous) {
        int total = 0;
        for (int row = firstRow(ominous); row < firstRow(ominous) + ROWS; row++) {
            if (this.entityType(row) != null) {
                total += this.egg(row).getCount();
            }
        }
        return total;
    }

    /**
     * Checks whether a page holds at least one spawn egg.
     *
     * @param ominous {@code true} for the ominous page
     * @return {@code true} if the page defines mobs
     */
    public boolean hasEggs(boolean ominous) {
        return this.totalMobs(ominous) > 0;
    }

    @Override
    public boolean hasAnyEgg() {
        return this.hasEggs(false);
    }

    @Override
    public Optional<ResourceKey<LootTable>> lootTableReference() {
        return this.lootTable;
    }

    @Override
    public void setLootTableReference(Optional<ResourceKey<LootTable>> lootTable) {
        this.lootTable = lootTable;
        this.setChanged();
    }

    /** @return the reward of the ominous variant, if set (otherwise the normal reward is used) */
    public Optional<ResourceKey<LootTable>> ominousLootTable() {
        return this.ominousLootTable;
    }

    /**
     * Sets or clears the reward of the ominous variant.
     *
     * @param lootTable the loot table, or empty to use the normal reward
     */
    public void setOminousLootTable(Optional<ResourceKey<LootTable>> lootTable) {
        this.ominousLootTable = lootTable;
        this.setChanged();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new TrialSpawnerMarkerMenu(containerId, inventory, this, this.data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.simultaneousMobs = Mth.clamp(input.getIntOr(SIMULTANEOUS_TAG, DEFAULT_SIMULTANEOUS), MIN_SIMULTANEOUS, MAX_SIMULTANEOUS);
        this.ominousSimultaneousMobs = Mth.clamp(input.getIntOr(OMINOUS_SIMULTANEOUS_TAG, DEFAULT_SIMULTANEOUS), MIN_SIMULTANEOUS,
                MAX_SIMULTANEOUS);
        this.ominousAllowed = input.getBooleanOr(OMINOUS_ALLOWED_TAG, false);
        this.lootTable = LootTableReference.read(input);
        this.ominousLootTable = input.read(OMINOUS_LOOT_TABLE_TAG, ResourceKey.codec(Registries.LOOT_TABLE));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(SIMULTANEOUS_TAG, this.simultaneousMobs);
        output.putInt(OMINOUS_SIMULTANEOUS_TAG, this.ominousSimultaneousMobs);
        output.putBoolean(OMINOUS_ALLOWED_TAG, this.ominousAllowed);
        LootTableReference.write(output, this.lootTable);
        this.ominousLootTable.ifPresent(key -> output.store(OMINOUS_LOOT_TABLE_TAG, ResourceKey.codec(Registries.LOOT_TABLE), key));
    }
}
