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

    private Optional<ResourceKey<LootTable>> lootTable = Optional.empty();
    private LootSetup lootSetup = LootSetup.EMPTY;

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

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.lootTable = LootTableReference.read(input);
        this.lootSetup = input.read(SETUP_TAG, LootSetup.CODEC).orElse(LootSetup.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        LootTableReference.write(output, this.lootTable);
        if (!this.lootSetup.isEmpty()) {
            output.store(SETUP_TAG, LootSetup.CODEC, this.lootSetup);
        }
    }
}
