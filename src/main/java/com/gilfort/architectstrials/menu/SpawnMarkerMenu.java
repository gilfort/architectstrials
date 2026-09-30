package com.gilfort.architectstrials.menu;

import com.gilfort.architectstrials.block.SpawnMarkerBlockEntity;
import com.gilfort.architectstrials.registry.ModMenuTypes;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;

/**
 * Container menu of the direct spawn and spawner markers: one {@link MarkerSlot marker row} — a spawn egg slot
 * (entity type; the stack size is the mob count) on top and six equipment slots (head, chest, legs, feet, main
 * hand, off hand) below.
 */
public class SpawnMarkerMenu extends MarkerMenu {

    private static final int[] EQUIPMENT_XS = {35, 53, 71, 89, 116, 134};

    /**
     * Creates the client-side menu.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     */
    public SpawnMarkerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(SpawnMarkerBlockEntity.SIZE));
    }

    /**
     * Creates the server-side menu for a marker.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     * @param container   the marker's container
     */
    public SpawnMarkerMenu(int containerId, Inventory inventory, Container container) {
        super(ModMenuTypes.SPAWN_MARKER.get(), containerId, inventory, container, 80, EQUIPMENT_XS, 31, new int[] {22}, 84);
    }
}
