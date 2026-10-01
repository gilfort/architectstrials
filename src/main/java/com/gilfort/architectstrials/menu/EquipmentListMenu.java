package com.gilfort.architectstrials.menu;

import com.gilfort.architectstrials.block.MobMarkerBlockEntity;
import com.gilfort.architectstrials.marker.EquipmentList;
import com.gilfort.architectstrials.registry.ModMenuTypes;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menu of the weighted list of one equipment slot of a mob marker: up to {@value EquipmentList#MAX_ENTRIES} items
 * (3 × 3 slots) with a chance each. Items and chances are stored in the marker immediately; the chance of an entry
 * is clamped so the list never exceeds 100 %. The remainder up to 100 % means "nothing".
 * <p>
 * Chances are set with menu button clicks: id = position × {@value #CHANCE_STRIDE} + chance (tenths of a percent).
 * {@link #BUTTON_BACK} returns to the marker menu.
 */
public class EquipmentListMenu extends AbstractContainerMenu {

    /** Button id stride for setting chances. */
    public static final int CHANCE_STRIDE = 2000;

    /** Button id that returns to the marker menu. */
    public static final int BUTTON_BACK = EquipmentList.MAX_ENTRIES * CHANCE_STRIDE;

    /** X position of the first list slot. */
    public static final int GRID_X = 10;

    /** Y position of the first list slot. */
    public static final int GRID_Y = 20;

    /** Horizontal distance between list cells (slot + chance field). */
    public static final int CELL_WIDTH = 62;

    /** Vertical distance between list rows. */
    public static final int CELL_HEIGHT = 22;

    /** Y position of the player inventory. */
    public static final int INVENTORY_Y = 112;

    private final Container list;
    private final ContainerData chances;
    private final DataSlot markerIndex;
    private final @Nullable MobMarkerBlockEntity marker;

    /**
     * Creates the client-side menu.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     */
    public EquipmentListMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(EquipmentList.MAX_ENTRIES), new SimpleContainerData(EquipmentList.MAX_ENTRIES),
                DataSlot.standalone(), null);
    }

    /**
     * Creates the server-side menu for an equipment slot of a marker.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     * @param marker      the marker
     * @param index       the container index of the equipment slot
     */
    public EquipmentListMenu(int containerId, Inventory inventory, MobMarkerBlockEntity marker, int index) {
        this(containerId, inventory, marker.listContainer(index), marker.listChances(index), constant(index), marker);
    }

    private EquipmentListMenu(int containerId, Inventory inventory, Container list, ContainerData chances, DataSlot markerIndex,
            @Nullable MobMarkerBlockEntity marker) {
        super(ModMenuTypes.EQUIPMENT_LIST.get(), containerId);
        this.list = list;
        this.chances = chances;
        this.markerIndex = markerIndex;
        this.marker = marker;
        for (int position = 0; position < EquipmentList.MAX_ENTRIES; position++) {
            int x = GRID_X + (position % 3) * CELL_WIDTH;
            int y = GRID_Y + (position / 3) * CELL_HEIGHT;
            this.addSlot(new Slot(list, position, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return MarkerSlot.accepts(EquipmentListMenu.this.markerIndex.get(), stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        this.addStandardInventorySlots(inventory, 19, INVENTORY_Y);
        this.addDataSlots(chances);
        this.addDataSlot(markerIndex);
    }

    private static DataSlot constant(int value) {
        DataSlot slot = DataSlot.standalone();
        slot.set(value);
        return slot;
    }

    /**
     * Returns the chance of a position.
     *
     * @param position the position (0–8)
     * @return the chance in tenths of a percent
     */
    public int chance(int position) {
        return this.chances.get(position);
    }

    /** @return the "nothing" remainder in tenths of a percent */
    public int remainder() {
        int total = 0;
        for (int position = 0; position < EquipmentList.MAX_ENTRIES; position++) {
            if (!this.list.getItem(position).isEmpty()) {
                total += this.chances.get(position);
            }
        }
        return EquipmentList.TOTAL - total;
    }

    /**
     * Checks whether a position holds an item.
     *
     * @param position the position (0–8)
     * @return {@code true} if an item is set
     */
    public boolean hasItem(int position) {
        return !this.list.getItem(position).isEmpty();
    }

    /** @return the container index of the equipment slot (for the slot icon) */
    public int markerIndex() {
        return this.markerIndex.get();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_BACK) {
            if (this.marker != null) {
                player.openMenu(this.marker);
            }
            return true;
        }
        if (id >= 0 && id < BUTTON_BACK) {
            this.chances.set(id / CHANCE_STRIDE, Math.min(id % CHANCE_STRIDE, EquipmentList.TOTAL));
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.list.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = slotIndex < EquipmentList.MAX_ENTRIES
                ? this.moveItemStackTo(stack, EquipmentList.MAX_ENTRIES, this.slots.size(), true)
                : this.moveItemStackTo(stack, 0, EquipmentList.MAX_ENTRIES, false);
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
