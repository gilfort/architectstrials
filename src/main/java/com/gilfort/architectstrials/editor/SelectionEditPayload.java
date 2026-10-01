package com.gilfort.architectstrials.editor;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: corners entered in the selection editor screen (shift + right click with the
 * {@link SelectionToolItem}).
 *
 * @param mainHand {@code true} if the tool is held in the main hand
 * @param first    the first corner
 * @param second   the second corner
 */
public record SelectionEditPayload(boolean mainHand, BlockPos first, BlockPos second) implements CustomPacketPayload {

    /** Payload type. */
    public static final CustomPacketPayload.Type<SelectionEditPayload> TYPE = new CustomPacketPayload.Type<>(ArchitectsTrials.id("selection_edit"));

    /** Network codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectionEditPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SelectionEditPayload::mainHand,
            BlockPos.STREAM_CODEC, SelectionEditPayload::first,
            BlockPos.STREAM_CODEC, SelectionEditPayload::second,
            SelectionEditPayload::new);

    @Override
    public CustomPacketPayload.Type<SelectionEditPayload> type() {
        return TYPE;
    }

    /**
     * Applies the corners to the tool in the given hand, in the player's current dimension.
     *
     * @param context the payload context
     */
    void handle(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = player.getItemInHand(this.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (stack.getItem() instanceof SelectionToolItem) {
            SelectionToolItem.setCorners(player, stack, this.first, this.second);
        }
    }

    /** Registers the payload on the mod event bus. */
    @EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
    static final class Registration {

        private Registration() {
        }

        /**
         * Registers the payload type and its server handler.
         *
         * @param event the payload registration event
         */
        @SubscribeEvent
        static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(TYPE, STREAM_CODEC, SelectionEditPayload::handle);
        }
    }
}
