package com.gilfort.architectstrials.browser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.EditorState;
import com.gilfort.architectstrials.structure.ChallengeStructure;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.structure.StructureValidation;
import com.gilfort.architectstrials.sub.SubStructure;
import com.gilfort.architectstrials.sub.SubStructures;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payloads of the structure preview (US-43): the client asks for a template when it has no cached picture of it; the
 * server answers with the data-fixed template as compressed NBT. Templates are only sent on demand, never with the
 * browser snapshot.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class PreviewNetwork {

    /** Largest compressed template that is sent; larger ones get a failure answer. */
    public static final int MAX_TEMPLATE_BYTES = 16 * 1024 * 1024;

    private PreviewNetwork() {
    }

    /**
     * Client → server: send the template of a stored structure.
     *
     * @param structure the structure
     */
    public record RequestTemplate(EditorState.StructureRef structure) implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<RequestTemplate> TYPE = new Type<>(ArchitectsTrials.id("preview_request"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestTemplate> STREAM_CODEC = BrowserNetwork.STRUCTURE_REF
                .map(RequestTemplate::new, RequestTemplate::structure);

        @Override
        public Type<RequestTemplate> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: the template of a stored structure.
     *
     * @param structure the structure
     * @param hash      the hash of the raw template file, as in the snapshot's statistics
     * @param data      the data-fixed template as compressed NBT; empty if it could not be sent
     */
    public record TemplateData(EditorState.StructureRef structure, String hash, byte[] data) implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<TemplateData> TYPE = new Type<>(ArchitectsTrials.id("preview_template"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, TemplateData> STREAM_CODEC = StreamCodec.composite(
                BrowserNetwork.STRUCTURE_REF, TemplateData::structure,
                ByteBufCodecs.STRING_UTF8, TemplateData::hash,
                ByteBufCodecs.byteArray(MAX_TEMPLATE_BYTES), TemplateData::data,
                TemplateData::new);

        @Override
        public Type<TemplateData> type() {
            return TYPE;
        }
    }

    /**
     * Registers the payloads; the client handler lives in {@code client.browser.preview.PreviewClient}.
     *
     * @param event the payload registration event
     */
    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(TemplateData.TYPE, TemplateData.STREAM_CODEC);
        registrar.playToServer(RequestTemplate.TYPE, RequestTemplate.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && ChallengeBrowser.mayUse(player)) {
                PacketDistributor.sendToPlayer(player, template(player.level().getServer(), payload.structure()));
            }
        });
    }

    /**
     * Reads, data-fixes and compresses the template of a stored structure.
     *
     * @param server    the server
     * @param structure the structure
     * @return the answer; with empty data if the template is missing or too large
     */
    public static TemplateData template(MinecraftServer server, EditorState.StructureRef structure) {
        Optional<Identifier> templateId = structure.sub()
                ? SubStructures.get(structure.id()).map(SubStructure::structure)
                : ChallengeStructures.get(structure.id()).map(ChallengeStructure::structure);
        Optional<CompoundTag> raw = templateId.flatMap(id -> StructureValidation.readTemplate(server, id));
        if (raw.isEmpty()) {
            return new TemplateData(structure, "", new byte[0]);
        }
        String hash = StructureStatistics.hash(raw.get());
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(StructureStatistics.load(server, raw.get()).save(new CompoundTag()), bytes);
            byte[] data = bytes.toByteArray();
            if (data.length > MAX_TEMPLATE_BYTES) {
                ArchitectsTrials.LOGGER.warn("Template of {} is too large for a preview ({} bytes)", structure.id(), data.length);
                return new TemplateData(structure, hash, new byte[0]);
            }
            return new TemplateData(structure, hash, data);
        } catch (IOException e) {
            ArchitectsTrials.LOGGER.warn("Could not prepare the preview template of {}", structure.id(), e);
            return new TemplateData(structure, hash, new byte[0]);
        }
    }
}
