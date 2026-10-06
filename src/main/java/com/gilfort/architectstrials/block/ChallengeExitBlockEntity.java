package com.gilfort.architectstrials.block;

import java.util.Optional;

import com.gilfort.architectstrials.loot.LootSetup;
import com.gilfort.architectstrials.loot.LootSetupHolder;
import com.gilfort.architectstrials.registry.ModBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Block entity of the {@link ChallengeExitBlock}. Holds the optional completion bonus override taken over from
 * the exit marker, and lets the client render the animated portal surface of an {@link ExitGroup}.
 */
public class ChallengeExitBlockEntity extends BlockEntity implements LootTableReference, LootSetupHolder {

    private static final String SETUP_TAG = "loot_setup";
    private static final String REQUIRES_MOBS_TAG = "requires_mobs";

    private Optional<ResourceKey<LootTable>> lootTable = Optional.empty();
    private LootSetup lootSetup = LootSetup.EMPTY;
    private boolean requiresMobs;

    /**
     * Creates the block entity.
     *
     * @param pos   the position
     * @param state the block state
     */
    public ChallengeExitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CHALLENGE_EXIT.get(), pos, state);
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
    public LootSetup lootSetup(boolean ominous) {
        return this.lootSetup;
    }

    @Override
    public void setLootSetup(boolean ominous, LootSetup setup) {
        this.lootSetup = setup;
        this.setChanged();
    }

    /** @return {@code true} if this exit stays sealed until all required mobs of the instance are defeated (US-30) */
    public boolean requiresMobs() {
        return this.requiresMobs;
    }

    /**
     * Sets whether this exit stays sealed until all required mobs of the instance are defeated.
     *
     * @param requiresMobs the setting
     */
    public void setRequiresMobs(boolean requiresMobs) {
        this.requiresMobs = requiresMobs;
        this.setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.requiresMobs = input.getBooleanOr(REQUIRES_MOBS_TAG, false);
        this.lootTable = LootTableReference.read(input);
        this.lootSetup = input.read(SETUP_TAG, LootSetup.CODEC).orElse(LootSetup.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        LootTableReference.write(output, this.lootTable);
        if (this.requiresMobs) {
            output.putBoolean(REQUIRES_MOBS_TAG, true);
        }
        if (!this.lootSetup.isEmpty()) {
            output.store(SETUP_TAG, LootSetup.CODEC, this.lootSetup);
        }
    }
}
