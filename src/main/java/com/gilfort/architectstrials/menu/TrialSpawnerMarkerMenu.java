package com.gilfort.architectstrials.menu;

import com.gilfort.architectstrials.block.TrialSpawnerMarkerBlockEntity;
import com.gilfort.architectstrials.registry.ModMenuTypes;

import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Container menu of the Trial Spawner Marker: two pages (normal and ominous) of three {@link MarkerSlot marker
 * rows} each, only the current page's slots are active. Buttons change the current page's simultaneous mobs,
 * toggle whether the spawner may turn ominous and switch pages.
 */
public class TrialSpawnerMarkerMenu extends MarkerMenu {

    /** Button id that decreases the current page's simultaneous mob count. */
    public static final int BUTTON_DECREASE = 0;

    /** Button id that increases the current page's simultaneous mob count. */
    public static final int BUTTON_INCREASE = 1;

    /** Button id that toggles whether the spawner may turn ominous. */
    public static final int BUTTON_TOGGLE_OMINOUS = 2;

    /** Button id that switches between the normal and the ominous page. */
    public static final int BUTTON_SWITCH_PAGE = 3;

    /** X position of the spawn egg slots. */
    public static final int EGG_X = 8;

    /** Y positions of the rows (both pages use the same positions). */
    public static final int[] ROW_YS = {18, 38, 58, 18, 38, 58};

    /** Y position of the player inventory. */
    public static final int INVENTORY_Y = 120;

    private static final int[] EQUIPMENT_XS = {34, 52, 70, 88, 106, 124};
    private static final int PAGE_SLOTS = TrialSpawnerMarkerBlockEntity.ROWS * MarkerSlot.ROW_SIZE;

    private final ContainerData data;
    private final DataSlot page = DataSlot.standalone();

    /**
     * Creates the client-side menu.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     */
    public TrialSpawnerMarkerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(2 * PAGE_SLOTS), new SimpleContainerData(TrialSpawnerMarkerBlockEntity.DATA_COUNT));
    }

    /**
     * Creates the server-side menu for a marker.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     * @param container   the marker's container
     * @param data        the marker's page settings
     */
    public TrialSpawnerMarkerMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenuTypes.TRIAL_SPAWNER_MARKER.get(), containerId, inventory, container, EGG_X, EQUIPMENT_XS, 0, ROW_YS, INVENTORY_Y);
        checkContainerDataCount(data, TrialSpawnerMarkerBlockEntity.DATA_COUNT);
        this.data = data;
        this.addDataSlots(data);
        this.addDataSlot(this.page);
    }

    @Override
    protected boolean isSlotActive(int index) {
        return (index >= PAGE_SLOTS) == this.ominousPage();
    }

    /** @return {@code true} while the ominous page is shown */
    public boolean ominousPage() {
        return this.page != null && this.page.get() == 1;
    }

    /** @return the current page's number of simultaneous mobs */
    public int simultaneousMobs() {
        return this.data.get(this.ominousPage() ? TrialSpawnerMarkerBlockEntity.DATA_OMINOUS_SIMULTANEOUS
                : TrialSpawnerMarkerBlockEntity.DATA_SIMULTANEOUS);
    }

    /** @return {@code true} if the spawner may turn ominous */
    public boolean ominousAllowed() {
        return this.data.get(TrialSpawnerMarkerBlockEntity.DATA_OMINOUS_ALLOWED) != 0;
    }

    /** @return the sum of the current page's spawn egg counts */
    public int totalMobs() {
        int total = 0;
        int first = this.ominousPage() ? PAGE_SLOTS : 0;
        for (int row = 0; row < TrialSpawnerMarkerBlockEntity.ROWS; row++) {
            int index = first + row * MarkerSlot.ROW_SIZE;
            ItemStack egg = this.container().getItem(index);
            if (MarkerSlot.accepts(index, egg)) {
                total += egg.getCount();
            }
        }
        return total;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case BUTTON_DECREASE, BUTTON_INCREASE -> {
                int index = this.ominousPage() ? TrialSpawnerMarkerBlockEntity.DATA_OMINOUS_SIMULTANEOUS
                        : TrialSpawnerMarkerBlockEntity.DATA_SIMULTANEOUS;
                this.data.set(index, Mth.clamp(this.data.get(index) + (id == BUTTON_INCREASE ? 1 : -1),
                        TrialSpawnerMarkerBlockEntity.MIN_SIMULTANEOUS, TrialSpawnerMarkerBlockEntity.MAX_SIMULTANEOUS));
            }
            case BUTTON_TOGGLE_OMINOUS -> this.data.set(TrialSpawnerMarkerBlockEntity.DATA_OMINOUS_ALLOWED, this.ominousAllowed() ? 0 : 1);
            case BUTTON_SWITCH_PAGE -> this.page.set(this.ominousPage() ? 0 : 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Shift-click only moves items into the slots of the current page.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int containerSize = this.container().getContainerSize();
        int pageStart = this.ominousPage() ? PAGE_SLOTS : 0;
        boolean moved = slotIndex < containerSize
                ? this.moveItemStackTo(stack, containerSize, this.inventoryEnd(), true)
                : this.moveItemStackTo(stack, pageStart, pageStart + PAGE_SLOTS, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
