package com.gilfort.architectstrials.sub;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload of the Sub Structure Marker GUI (US-32): the client sends the edited setup and offsets of the open
 * marker; the server validates them (known sub structures, chances at most 100 % in total) and stores them.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class SubStructureNetwork {

    private SubStructureNetwork() {
    }

    /**
     * Client → server: new setup and offsets of the marker of the open menu.
     *
     * @param containerId the container id
     * @param setup       the setup
     * @param offsets     the offsets −X, +X, −Y, +Y, −Z, +Z
     */
    public record Edit(int containerId, SubStructureSetup setup, List<Integer> offsets) implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<Edit> TYPE = new Type<>(ArchitectsTrials.id("sub_structure_marker_edit"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Edit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Edit::containerId,
                SubStructureSetup.STREAM_CODEC, Edit::setup,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(SubStructureMarkerBlockEntity.OFFSETS)), Edit::offsets,
                Edit::new);

        @Override
        public Type<Edit> type() {
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
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(Edit.TYPE, Edit.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof SubStructureMarkerMenu menu
                    && menu.containerId == payload.containerId() && menu.stillValid(player) && player.canUseGameMasterBlocks()
                    && player.level().getBlockEntity(menu.pos()) instanceof SubStructureMarkerBlockEntity marker) {
                Component error = validate(payload.setup());
                if (error != null) {
                    player.sendOverlayMessage(error.copy().withStyle(ChatFormatting.RED));
                    return;
                }
                marker.setSetup(payload.setup());
                marker.setOffsets(payload.offsets().stream().mapToInt(Integer::intValue).toArray());
                player.sendOverlayMessage(Component.translatable("message.architectstrials.sub_structure_marker.saved"));
            }
        });
    }

    /**
     * Checks a setup edited in the GUI.
     *
     * @param setup the setup
     * @return the error message, or {@code null} if the setup is valid
     */
    public static @Nullable Component validate(SubStructureSetup setup) {
        if (setup.totalPercent() > 100) {
            return Component.translatable("message.architectstrials.sub_structure_marker.too_much", setup.totalPercent());
        }
        for (SubStructureSetup.Entry entry : setup.entries()) {
            if (entry.percent() < 1 || entry.percent() > 100) {
                return Component.translatable("message.architectstrials.sub_structure_marker.bad_percent", entry.structure().toString());
            }
            if (SubStructures.get(entry.structure()).isEmpty()) {
                return Component.translatable("message.architectstrials.sub_structure_marker.unknown", entry.structure().toString());
            }
        }
        if (setup.fallback().isPresent() && SubStructures.get(setup.fallback().get()).isEmpty()) {
            return Component.translatable("message.architectstrials.sub_structure_marker.unknown", setup.fallback().get().toString());
        }
        return null;
    }
}
