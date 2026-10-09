package com.gilfort.architectstrials.menu;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Click handling of {@link GhostSlot ghost slots} (US-39), shared by all menus that contain them.
 * <ul>
 * <li>Click with an item: countable slots take a copy with the cursor's count (left) or one item (right), adding to
 * the same item; other slots take a single copy. The cursor item is never consumed.</li>
 * <li>Click with an empty hand: countable slots +1 (left) / −1 (right); other slots are cleared by a right click.</li>
 * <li>Shift click with an empty hand clears; shift click in the inventory copies into the first free matching ghost
 * slot ({@link #copyToFirstFree}).</li>
 * <li>Mouse wheel and JEI drag &amp; drop send a {@link SetGhost} payload.</li>
 * </ul>
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class GhostSlots {

    private GhostSlots() {
    }

    /**
     * Handles a click if it targets a ghost slot. Runs on both sides with the same result (client prediction).
     *
     * @param menu      the menu
     * @param slotIndex the menu slot index
     * @param button    the mouse button (0 left, 1 right)
     * @param input     the click type
     * @return {@code true} if the click was handled (the caller must not pass it to vanilla)
     */
    public static boolean click(AbstractContainerMenu menu, int slotIndex, int button, ContainerInput input) {
        if (slotIndex < 0 || slotIndex >= menu.slots.size() || !(menu.slots.get(slotIndex) instanceof GhostSlot slot)) {
            return false;
        }
        if (input == ContainerInput.CLONE) {
            return false;
        }
        if (!slot.isActive()) {
            return true;
        }
        ItemStack carried = menu.getCarried();
        ItemStack current = slot.getItem();
        if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
            if (!carried.isEmpty()) {
                if (slot.accepts(carried)) {
                    boolean single = input == ContainerInput.PICKUP && button == 1;
                    int added = single ? 1 : carried.getCount();
                    int base = ItemStack.isSameItemSameComponents(current, carried) ? current.getCount() : 0;
                    slot.setGhost(carried.copyWithCount(slot.countable() ? base + added : 1));
                }
            } else if (input == ContainerInput.QUICK_MOVE) {
                slot.setGhost(ItemStack.EMPTY);
            } else if (!current.isEmpty()) {
                if (slot.countable()) {
                    slot.setGhost(current.copyWithCount(current.getCount() + (button == 1 ? -1 : 1)));
                } else if (button == 1) {
                    slot.setGhost(ItemStack.EMPTY);
                }
            }
        }
        return true;
    }

    /**
     * Copies an item into the first empty, active ghost slot of a range that accepts it (shift click in the
     * inventory). The item itself stays where it is.
     *
     * @param menu  the menu
     * @param stack the item
     * @param from  the first menu slot index (inclusive)
     * @param to    the last menu slot index (exclusive)
     * @return always {@link ItemStack#EMPTY}, so vanilla's quick move loop stops
     */
    public static ItemStack copyToFirstFree(AbstractContainerMenu menu, ItemStack stack, int from, int to) {
        for (int index = from; index < to; index++) {
            if (menu.slots.get(index) instanceof GhostSlot slot && slot.isActive() && !slot.hasItem() && slot.accepts(stack)) {
                slot.setGhost(slot.countable() ? stack : stack.copyWithCount(1));
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Sets a ghost slot to an item (server side, from a {@link SetGhost} payload): ignored unless the slot is an
     * active ghost slot that accepts the item.
     *
     * @param menu      the menu
     * @param slotIndex the menu slot index
     * @param stack     the item, or empty to clear
     * @return {@code true} if the slot was set
     */
    public static boolean set(AbstractContainerMenu menu, int slotIndex, ItemStack stack) {
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
            return false;
        }
        Slot target = menu.slots.get(slotIndex);
        if (!(target instanceof GhostSlot slot) || !slot.isActive() || (!stack.isEmpty() && !slot.accepts(stack))) {
            return false;
        }
        slot.setGhost(stack);
        menu.broadcastChanges();
        return true;
    }

    /**
     * Client → server: sets a ghost slot of the open menu (mouse wheel, JEI drag &amp; drop).
     *
     * @param containerId the container id
     * @param slotIndex   the menu slot index
     * @param stack       the item, or empty to clear
     */
    public record SetGhost(int containerId, int slotIndex, ItemStack stack) implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<SetGhost> TYPE = new Type<>(ArchitectsTrials.id("set_ghost_slot"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, SetGhost> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetGhost::containerId,
                ByteBufCodecs.VAR_INT, SetGhost::slotIndex,
                ItemStack.OPTIONAL_STREAM_CODEC, SetGhost::stack,
                SetGhost::new);

        @Override
        public Type<SetGhost> type() {
            return TYPE;
        }
    }

    /**
     * Registers the payload.
     *
     * @param event the payload registration event
     */
    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(SetGhost.TYPE, SetGhost.STREAM_CODEC, (payload, context) -> {
            AbstractContainerMenu menu = context.player().containerMenu;
            if (menu.containerId == payload.containerId() && menu.stillValid(context.player())) {
                set(menu, payload.slotIndex(), payload.stack());
            }
        });
    }
}
