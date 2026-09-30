package com.gilfort.architectstrials.block;

import java.util.Map;

import com.gilfort.architectstrials.menu.MarkerSlot;
import com.gilfort.architectstrials.menu.SpawnMarkerMenu;
import com.gilfort.architectstrials.registry.ModBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Block entity shared by the {@link SpawnMarkerBlock}s: a single marker row — one spawn egg stack (entity type
 * and count) and six equipment items.
 */
public class SpawnMarkerBlockEntity extends MobMarkerBlockEntity {

    /** Container index of the spawn egg slot. */
    public static final int EGG_SLOT = 0;

    /** Total number of container slots. */
    public static final int SIZE = MarkerSlot.ROW_SIZE;

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

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new SpawnMarkerMenu(containerId, inventory, this);
    }
}
