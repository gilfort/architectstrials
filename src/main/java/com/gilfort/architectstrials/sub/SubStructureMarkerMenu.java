package com.gilfort.architectstrials.sub;

import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.registry.ModMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Menu of the Sub Structure Marker (US-32): no slots. The screen reads the marker's setup and offsets from the
 * synced block entity and sends edits with {@link SubStructureNetwork.Edit}.
 */
public class SubStructureMarkerMenu extends AbstractContainerMenu {

    private final BlockPos pos;

    /**
     * Creates the menu.
     *
     * @param containerId the container id
     * @param pos         the marker position
     */
    public SubStructureMarkerMenu(int containerId, BlockPos pos) {
        super(ModMenuTypes.SUB_STRUCTURE_MARKER.get(), containerId);
        this.pos = pos.immutable();
    }

    /** @return the marker position */
    public BlockPos pos() {
        return this.pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(player.level(), this.pos), player, ModBlocks.SUB_STRUCTURE_MARKER.get());
    }
}
