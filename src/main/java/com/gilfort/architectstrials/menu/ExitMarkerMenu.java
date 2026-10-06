package com.gilfort.architectstrials.menu;

import java.util.Optional;

import com.gilfort.architectstrials.block.ExitCamouflage;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.registry.ModMenuTypes;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Menu of the Exit Marker, opened by right-clicking it with an empty hand: no slots, only the exit's settings.
 * The "Requires required mobs" toggle (US-30) and the camouflage preview with a remove button (US-34).
 */
public class ExitMarkerMenu extends AbstractContainerMenu {

    /** Button id that toggles whether the exit requires the required mobs. */
    public static final int BUTTON_TOGGLE_REQUIRES_MOBS = 0;

    /** Button id that removes the camouflage (US-34). */
    public static final int BUTTON_REMOVE_CAMOUFLAGE = 1;

    /** Data index of the "requires required mobs" setting. */
    public static final int DATA_REQUIRES_MOBS = 0;

    /** Data index of the low bits of the camouflage block state id (+ 1, 0 = none). */
    public static final int DATA_CAMOUFLAGE_LOW = 1;

    /** Data index of the high bits of the camouflage block state id. */
    public static final int DATA_CAMOUFLAGE_HIGH = 2;

    /** Number of menu data values. */
    public static final int DATA_COUNT = 3;

    /** Menu data values are synced as shorts, so the state id is split into two 15-bit parts. */
    private static final int PART_BITS = 15;
    private static final int PART_MASK = (1 << PART_BITS) - 1;

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
        return this.data.get(DATA_REQUIRES_MOBS) != 0;
    }

    /** @return the current camouflage of the marker, if any */
    public Optional<BlockState> camouflage() {
        int id = this.data.get(DATA_CAMOUFLAGE_LOW) | this.data.get(DATA_CAMOUFLAGE_HIGH) << PART_BITS;
        return id == 0 ? Optional.empty() : Optional.ofNullable(Block.stateById(id - 1));
    }

    /**
     * Encodes a camouflage for the menu data.
     *
     * @param camouflage the camouflage
     * @param index      {@link #DATA_CAMOUFLAGE_LOW} or {@link #DATA_CAMOUFLAGE_HIGH}
     * @return the data value
     */
    public static int camouflagePart(Optional<BlockState> camouflage, int index) {
        int id = camouflage.map(state -> Block.getId(state) + 1).orElse(0);
        return index == DATA_CAMOUFLAGE_LOW ? id & PART_MASK : id >>> PART_BITS & PART_MASK;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_TOGGLE_REQUIRES_MOBS) {
            this.data.set(DATA_REQUIRES_MOBS, this.requiresMobs() ? 0 : 1);
            return true;
        }
        if (id == BUTTON_REMOVE_CAMOUFLAGE) {
            this.access.execute((level, pos) -> {
                if (level.getBlockEntity(pos) instanceof ExitCamouflage.Holder holder) {
                    holder.setCamouflage(Optional.empty());
                }
            });
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
