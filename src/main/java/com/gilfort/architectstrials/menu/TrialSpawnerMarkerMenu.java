package com.gilfort.architectstrials.menu;

import com.gilfort.architectstrials.block.TrialSpawnerMarkerBlockEntity;
import com.gilfort.architectstrials.registry.ModMenuTypes;

import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Container menu of the Trial Spawner Marker: three {@link MarkerSlot marker rows} (one mob type each) and the
 * number of simultaneous mobs, changed with the +/- buttons.
 */
public class TrialSpawnerMarkerMenu extends MarkerMenu {

    /** Button id that decreases the simultaneous mob count. */
    public static final int BUTTON_DECREASE = 0;

    /** Button id that increases the simultaneous mob count. */
    public static final int BUTTON_INCREASE = 1;

    /** X position of the spawn egg slots. */
    public static final int EGG_X = 8;

    /** Y positions of the rows. */
    public static final int[] ROW_YS = {18, 38, 58};

    /** Y position of the player inventory. */
    public static final int INVENTORY_Y = 104;

    private static final int[] EQUIPMENT_XS = {34, 52, 70, 88, 106, 124};

    private final ContainerData data;

    /**
     * Creates the client-side menu.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     */
    public TrialSpawnerMarkerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(TrialSpawnerMarkerBlockEntity.ROWS * MarkerSlot.ROW_SIZE), new SimpleContainerData(1));
    }

    /**
     * Creates the server-side menu for a marker.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     * @param container   the marker's container
     * @param data        the marker's simultaneous mob count
     */
    public TrialSpawnerMarkerMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenuTypes.TRIAL_SPAWNER_MARKER.get(), containerId, inventory, container, EGG_X, EQUIPMENT_XS, 0, ROW_YS, INVENTORY_Y);
        checkContainerDataCount(data, 1);
        this.data = data;
        this.addDataSlots(data);
    }

    /** @return the number of simultaneous mobs */
    public int simultaneousMobs() {
        return this.data.get(0);
    }

    /** @return the sum of all spawn egg counts currently in the menu */
    public int totalMobs() {
        int total = 0;
        for (int row = 0; row < TrialSpawnerMarkerBlockEntity.ROWS; row++) {
            ItemStack egg =this.container().getItem(row * MarkerSlot.ROW_SIZE);
            if (MarkerSlot.accepts(row * MarkerSlot.ROW_SIZE, egg)) {
                total += egg.getCount();
            }
        }
        return total;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_DECREASE && id != BUTTON_INCREASE) {
            return false;
        }
        int delta = id == BUTTON_INCREASE ? 1 : -1;
        this.data.set(0, Mth.clamp(this.data.get(0) + delta, TrialSpawnerMarkerBlockEntity.MIN_SIMULTANEOUS,
                TrialSpawnerMarkerBlockEntity.MAX_SIMULTANEOUS));
        return true;
    }
}
