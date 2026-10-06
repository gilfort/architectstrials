package com.gilfort.architectstrials.menu;

import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.registry.ModMenuTypes;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Menu of the Exit Marker, opened by right-clicking it with an empty hand: no slots, only the exit's settings.
 * Currently the "Requires required mobs" toggle (US-30).
 */
public class ExitMarkerMenu extends AbstractContainerMenu {

    /** Button id that toggles whether the exit requires the required mobs. */
    public static final int BUTTON_TOGGLE_REQUIRES_MOBS = 0;

    /** Number of menu data values. */
    public static final int DATA_COUNT = 1;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    /**
     * Creates the client-side menu.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     */
    public ExitMarkerMenu(int containerId, Inventory inventory) {
        this(containerId, ContainerLevelAccess.NULL, new SimpleContainerData(DATA_COUNT));
    }

    /**
     * Creates the server-side menu for a marker.
     *
     * @param containerId the container id
     * @param access      the marker's position
     * @param data        the marker's settings
     */
    public ExitMarkerMenu(int containerId, ContainerLevelAccess access, ContainerData data) {
        super(ModMenuTypes.EXIT_MARKER.get(), containerId);
        checkContainerDataCount(data, DATA_COUNT);
        this.access = access;
        this.data = data;
        this.addDataSlots(data);
    }

    /** @return {@code true} if the exit stays sealed until all required mobs are defeated */
    public boolean requiresMobs() {
        return this.data.get(0) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_TOGGLE_REQUIRES_MOBS) {
            this.data.set(0, this.requiresMobs() ? 0 : 1);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.EXIT_MARKER.get());
    }
}
