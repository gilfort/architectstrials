package com.gilfort.architectstrials.block;

import java.util.Optional;

import com.gilfort.architectstrials.menu.TrialSpawnerMarkerMenu;
import com.gilfort.architectstrials.registry.ModBlockEntityTypes;

import net.minecraft.core.BlockPos;
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
 * Block entity of the {@link TrialSpawnerMarkerBlock}: three marker rows (one mob type each), the number of
 * simultaneous mobs and an optional reward loot table. Total mobs = sum of all spawn egg counts; the counts also
 * act as spawn weights.
 */
public class TrialSpawnerMarkerBlockEntity extends MobMarkerBlockEntity implements LootTableReference {

    /** Number of mob rows. */
    public static final int ROWS = 3;

    /** Smallest allowed number of simultaneous mobs. */
    public static final int MIN_SIMULTANEOUS = 1;

    /** Largest allowed number of simultaneous mobs. */
    public static final int MAX_SIMULTANEOUS = 32;

    /** Default number of simultaneous mobs (vanilla default). */
    public static final int DEFAULT_SIMULTANEOUS = 2;

    private static final String SIMULTANEOUS_TAG = "simultaneous_mobs";

    private int simultaneousMobs = DEFAULT_SIMULTANEOUS;
    private Optional<ResourceKey<LootTable>> lootTable = Optional.empty();

    /** Synchronizes the simultaneous mob count with open menus. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return TrialSpawnerMarkerBlockEntity.this.simultaneousMobs;
        }

        @Override
        public void set(int index, int value) {
            TrialSpawnerMarkerBlockEntity.this.setSimultaneousMobs(value);
        }

        @Override
        public int getCount() {
            return 1;
        }
    };

    /**
     * Creates the block entity.
     *
     * @param pos   the position
     * @param state the block state
     */
    public TrialSpawnerMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.TRIAL_SPAWNER_MARKER.get(), pos, state, ROWS);
    }

    /** @return the number of mobs alive at the same time (for one player) */
    public int simultaneousMobs() {
        return this.simultaneousMobs;
    }

    /**
     * Sets the number of simultaneous mobs, clamped to {@value #MIN_SIMULTANEOUS}–{@value #MAX_SIMULTANEOUS}.
     *
     * @param value the new value
     */
    public void setSimultaneousMobs(int value) {
        this.simultaneousMobs = Mth.clamp(value, MIN_SIMULTANEOUS, MAX_SIMULTANEOUS);
        this.setChanged();
    }

    /** @return the sum of all spawn egg counts */
    public int totalMobs() {
        int total = 0;
        for (int row = 0; row < ROWS; row++) {
            if (this.entityType(row) != null) {
                total += this.egg(row).getCount();
            }
        }
        return total;
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

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new TrialSpawnerMarkerMenu(containerId, inventory, this, this.data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.simultaneousMobs = Mth.clamp(input.getIntOr(SIMULTANEOUS_TAG, DEFAULT_SIMULTANEOUS), MIN_SIMULTANEOUS, MAX_SIMULTANEOUS);
        this.lootTable = LootTableReference.read(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(SIMULTANEOUS_TAG, this.simultaneousMobs);
        LootTableReference.write(output, this.lootTable);
    }
}
