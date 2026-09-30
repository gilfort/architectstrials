package com.gilfort.architectstrials.block;

import java.util.Optional;

import com.gilfort.architectstrials.registry.ModBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Block entity of the {@link ExitMarkerBlock}: stores an optional completion bonus loot table that overrides
 * the theme/tier convention. Saved with the structure and handed to the functional exit on placement.
 */
public class ExitMarkerBlockEntity extends BlockEntity implements LootTableReference {

    private Optional<ResourceKey<LootTable>> lootTable = Optional.empty();

    /**
     * Creates the block entity.
     *
     * @param pos   the position
     * @param state the block state
     */
    public ExitMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.EXIT_MARKER.get(), pos, state);
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
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.lootTable = LootTableReference.read(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        LootTableReference.write(output, this.lootTable);
    }
}
