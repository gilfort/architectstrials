package com.gilfort.architectstrials.menu;

import com.gilfort.architectstrials.block.SpawnMarkerBlockEntity;
import com.gilfort.architectstrials.registry.ModMenuTypes;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Container menu of the direct spawn and spawner markers: one {@link MarkerSlot marker row} — a spawn egg slot
 * (entity type; the stack size is the mob count) on top and six equipment slots (head, chest, legs, feet, main
 * hand, off hand) below.
 */
public class SpawnMarkerMenu extends MarkerMenu {

    /** Button id that toggles whether the mobs are required. */
    public static final int BUTTON_TOGGLE_REQUIRED = 0;

    private static final int[] EQUIPMENT_XS = {35, 53, 71, 89, 116, 134};

    private final ContainerData data;

    /**
     * Creates the client-side menu.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     */
    public SpawnMarkerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(SpawnMarkerBlockEntity.SIZE), new SimpleContainerData(SpawnMarkerBlockEntity.DATA_COUNT));
    }

    /**
     * Creates the server-side menu for a marker.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     * @param container   the marker's container
     * @param data        the marker's settings (required flag and whether it is available)
     */
    public SpawnMarkerMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenuTypes.SPAWN_MARKER.get(), containerId, inventory, container, 80, EQUIPMENT_XS, 31, new int[] {22}, 84);
        checkContainerDataCount(data, SpawnMarkerBlockEntity.DATA_COUNT);
        this.data = data;
        this.addDataSlots(data);
    }

    /** @return {@code true} if the marker's mobs are required */
    public boolean required() {
        return this.data.get(SpawnMarkerBlockEntity.DATA_REQUIRED) != 0;
    }

    /** @return {@code true} if the marker supports required mobs (Direct Spawn Marker) */
    public boolean requiredAvailable() {
        return this.data.get(SpawnMarkerBlockEntity.DATA_REQUIRED_AVAILABLE) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_TOGGLE_REQUIRED && this.requiredAvailable()) {
            this.data.set(SpawnMarkerBlockEntity.DATA_REQUIRED, this.required() ? 0 : 1);
            return true;
        }
        return false;
    }
}
