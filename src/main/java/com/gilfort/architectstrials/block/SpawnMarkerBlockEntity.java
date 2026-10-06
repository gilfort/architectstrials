package com.gilfort.architectstrials.block;

import java.util.Map;

import com.gilfort.architectstrials.menu.MarkerSlot;
import com.gilfort.architectstrials.menu.SpawnMarkerMenu;
import com.gilfort.architectstrials.registry.ModBlockEntityTypes;
import com.gilfort.architectstrials.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Block entity shared by the {@link SpawnMarkerBlock}s: a single marker row — one spawn egg stack (entity type
 * and count) and six equipment items. Direct Spawn Markers can mark their mobs as required (US-30); Spawner Markers
 * cannot, since a spawner never stops spawning.
 */
public class SpawnMarkerBlockEntity extends MobMarkerBlockEntity {

    /** Container index of the spawn egg slot. */
    public static final int EGG_SLOT = 0;

    /** Total number of container slots. */
    public static final int SIZE = MarkerSlot.ROW_SIZE;

    /** Menu data index: whether the mobs are required (1) or not (0). */
    public static final int DATA_REQUIRED = 0;

    /** Menu data index: whether this marker supports required mobs (1, Direct Spawn Marker) or not (0). */
    public static final int DATA_REQUIRED_AVAILABLE = 1;

    /** Number of menu data values. */
    public static final int DATA_COUNT = 2;

    private static final String REQUIRED_TAG = "required";

    private boolean required;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_REQUIRED -> SpawnMarkerBlockEntity.this.required() ? 1 : 0;
                case DATA_REQUIRED_AVAILABLE -> SpawnMarkerBlockEntity.this.supportsRequired() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_REQUIRED) {
                SpawnMarkerBlockEntity.this.setRequired(value != 0);
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
    public SpawnMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SPAWN_MARKER.get(), pos, state, 1);
    }

    /**
     * Returns the container index of an equipment slot.
     *
     * @param slot the equipment slot
     * @return the container index
     */
    public static int indexOf(EquipmentSlot slot) {
        return MarkerSlot.indexOf(0, slot);
    }

    /** @return the spawn egg stack (possibly empty) */
    public ItemStack egg() {
        return this.egg(0);
    }

    /** @return the entity type of the spawn egg, or {@code null} if the slot holds no spawn egg */
    public @Nullable EntityType<?> entityType() {
        return this.entityType(0);
    }

    /** @return copies of the non-empty equipment items by slot */
    public Map<EquipmentSlot, ItemStack> equipment() {
        return this.equipment(0);
    }

    /** @return {@code true} if this is a Direct Spawn Marker, whose mobs can be required */
    public boolean supportsRequired() {
        return this.getBlockState().is(ModBlocks.DIRECT_SPAWN_MARKER.get());
    }

    /** @return {@code true} if the mobs of this marker are required (only for Direct Spawn Markers) */
    public boolean required() {
        return this.required && this.supportsRequired();
    }

    /**
     * Marks the mobs of this marker as required or not. Ignored for markers that do not support it.
     *
     * @param required whether the mobs are required
     */
    public void setRequired(boolean required) {
        if (this.supportsRequired()) {
            this.required = required;
            this.setChanged();
        }
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new SpawnMarkerMenu(containerId, inventory, this, this.data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.required = input.getBooleanOr(REQUIRED_TAG, false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.required) {
            output.putBoolean(REQUIRED_TAG, true);
        }
    }
}
