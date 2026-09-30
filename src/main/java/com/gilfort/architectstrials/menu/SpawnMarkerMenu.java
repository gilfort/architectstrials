package com.gilfort.architectstrials.menu;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.SpawnMarkerBlockEntity;
import com.gilfort.architectstrials.registry.ModMenuTypes;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Container menu of the spawn markers: one spawn egg slot (entity type; the stack size is the mob count) and
 * six equipment slots (head, chest, legs, feet, main hand, off hand).
 */
public class SpawnMarkerMenu extends AbstractContainerMenu {

    /** Sprite shown in the empty spawn egg slot. */
    public static final Identifier EMPTY_SPAWN_EGG_SLOT = ArchitectsTrials.id("container/slot/spawn_egg");

    private static final Identifier[] EMPTY_EQUIPMENT_SLOTS = {
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, Identifier.withDefaultNamespace("container/slot/sword"),
            InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD};

    private final Container container;

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
        super(ModMenuTypes.SPAWN_MARKER.get(), containerId);
        checkContainerSize(container, SpawnMarkerBlockEntity.SIZE);
        this.container = container;
        container.startOpen(inventory.player);

        this.addSlot(new MarkerSlot(container, SpawnMarkerBlockEntity.EGG_SLOT, 80, 22, EMPTY_SPAWN_EGG_SLOT));
        for (int i = 0; i < SpawnMarkerBlockEntity.EQUIPMENT_SLOTS.size(); i++) {
            int x = i < 4 ? 35 + i * 18 : 116 + (i - 4) * 18;
            this.addSlot(new MarkerSlot(container, 1 + i, x, 53, EMPTY_EQUIPMENT_SLOTS[i]));
        }
        this.addStandardInventorySlots(inventory, 8, 84);
    }

    /**
     * Checks whether an item may be put into a marker slot: the egg slot takes spawn eggs only, armor slots take
     * items equippable in that slot, the hand slots take anything.
     *
     * @param index the container index
     * @param stack the item
     * @return {@code true} if the item fits
     */
    public static boolean accepts(int index, ItemStack stack) {
        if (index == SpawnMarkerBlockEntity.EGG_SLOT) {
            return stack.getItem() instanceof SpawnEggItem && SpawnEggItem.getType(stack) != null;
        }
        EquipmentSlot slot = SpawnMarkerBlockEntity.EQUIPMENT_SLOTS.get(index - 1);
        if (slot.getType() == EquipmentSlot.Type.HAND) {
            return true;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == slot;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int size = this.container.getContainerSize();
        if (slotIndex < size) {
            if (!this.moveItemStackTo(stack, size, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, size, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    /**
     * A marker slot with an item filter, an empty-slot sprite and a stack limit of one for equipment.
     */
    private static final class MarkerSlot extends Slot {

        private final Identifier emptyIcon;

        MarkerSlot(Container container, int index, int x, int y, Identifier emptyIcon) {
            super(container, index, x, y);
            this.emptyIcon = emptyIcon;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return accepts(this.getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return this.getContainerSlot() == SpawnMarkerBlockEntity.EGG_SLOT ? super.getMaxStackSize() : 1;
        }

        @Override
        public Identifier getNoItemIcon() {
            return this.emptyIcon;
        }
    }
}
