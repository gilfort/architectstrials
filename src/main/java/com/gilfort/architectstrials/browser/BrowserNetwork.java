package com.gilfort.architectstrials.browser;

import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.EditorLoading;
import com.gilfort.architectstrials.editor.EditorState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payloads of the challenge browser (US-40). Server → client payloads are registered here without a handler; the
 * client registers its handlers in {@code client.browser.BrowserClientHandlers}.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class BrowserNetwork {

    /** Network codec of a stored structure reference. */
    public static final StreamCodec<RegistryFriendlyByteBuf, EditorState.StructureRef> STRUCTURE_REF = StreamCodec.composite(
            ByteBufCodecs.BOOL, EditorState.StructureRef::sub,
            Identifier.STREAM_CODEC, EditorState.StructureRef::id,
            EditorState.StructureRef::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, BrowserSnapshot> SNAPSHOT =
            ByteBufCodecs.fromCodecWithRegistriesTrusted(BrowserSnapshot.CODEC);

    private BrowserNetwork() {
    }

    /**
     * Server → client: opens the browser with a snapshot.
     *
     * @param snapshot the snapshot
     */
    public record Open(BrowserSnapshot snapshot) implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<Open> TYPE = new Type<>(ArchitectsTrials.id("browser_open"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = SNAPSHOT.map(Open::new, Open::snapshot);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: a fresh snapshot for an open browser (refresh, or after a reload).
     *
     * @param snapshot the snapshot
     */
    public record Update(BrowserSnapshot snapshot) implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<Update> TYPE = new Type<>(ArchitectsTrials.id("browser_update"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> STREAM_CODEC = SNAPSHOT.map(Update::new, Update::snapshot);

        @Override
        public Type<Update> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: the editor is not empty; the client asks whether it may be cleared.
     *
     * @param structure the structure that should be loaded
     * @param builders  names of the players in the editor
     * @param last      the structure last loaded into or saved from the editor, if known
     */
    public record Busy(EditorState.StructureRef structure, List<String> builders, Optional<EditorState.StructureRef> last)
            implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<Busy> TYPE = new Type<>(ArchitectsTrials.id("browser_editor_busy"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Busy> STREAM_CODEC = StreamCodec.composite(
                STRUCTURE_REF, Busy::structure,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Busy::builders,
                ByteBufCodecs.optional(STRUCTURE_REF), Busy::last,
                Busy::new);

        @Override
        public Type<Busy> type() {
            return TYPE;
        }
    }

    /** Server → client: the structure is (being) loaded; the browser closes. */
    public record Close() implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<Close> TYPE = new Type<>(ArchitectsTrials.id("browser_close"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Close> STREAM_CODEC = StreamCodec.unit(new Close());

        @Override
        public Type<Close> type() {
            return TYPE;
        }
    }

    /** Client → server: send a fresh snapshot. */
    public record Refresh() implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<Refresh> TYPE = new Type<>(ArchitectsTrials.id("browser_refresh"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Refresh> STREAM_CODEC = StreamCodec.unit(new Refresh());

        @Override
        public Type<Refresh> type() {
            return TYPE;
        }
    }

    /** Client → server: the browser was closed; stop sending updates. */
    public record Closed() implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<Closed> TYPE = new Type<>(ArchitectsTrials.id("browser_closed"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Closed> STREAM_CODEC = StreamCodec.unit(new Closed());

        @Override
        public Type<Closed> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: load a structure into the editor and enter it.
     *
     * @param structure  the structure
     * @param clearFirst {@code true} if the player confirmed clearing a non-empty editor
     */
    public record Load(EditorState.StructureRef structure, boolean clearFirst) implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<Load> TYPE = new Type<>(ArchitectsTrials.id("browser_load"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Load> STREAM_CODEC = StreamCodec.composite(
                STRUCTURE_REF, Load::structure,
                ByteBufCodecs.BOOL, Load::clearFirst,
                Load::new);

        @Override
        public Type<Load> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: save edited settings of a challenge structure (US-42).
     *
     * @param id       the metadata id
     * @param revision the revision the edit is based on
     * @param json     the edited metadata as JSON
     */
    public record Save(Identifier id, String revision, String json) implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<Save> TYPE = new Type<>(ArchitectsTrials.id("browser_save"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Save> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Save::id,
                ByteBufCodecs.STRING_UTF8, Save::revision,
                ByteBufCodecs.stringUtf8(1 << 20), Save::json,
                Save::new);

        @Override
        public Type<Save> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: the result of a save request.
     *
     * @param id     the metadata id
     * @param status the status
     * @param errors the field errors
     */
    public record SaveResult(Identifier id, ChallengeEdits.SaveStatus status, List<ChallengeEdits.FieldError> errors) implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<SaveResult> TYPE = new Type<>(ArchitectsTrials.id("browser_save_result"));

        private static final StreamCodec<RegistryFriendlyByteBuf, ChallengeEdits.FieldError> FIELD_ERROR = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ChallengeEdits.FieldError::field,
                ComponentSerialization.STREAM_CODEC, ChallengeEdits.FieldError::message,
                ChallengeEdits.FieldError::new);

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveResult> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, SaveResult::id,
                ByteBufCodecs.idMapper(i -> ChallengeEdits.SaveStatus.values()[i], ChallengeEdits.SaveStatus::ordinal), SaveResult::status,
                FIELD_ERROR.apply(ByteBufCodecs.list()), SaveResult::errors,
                SaveResult::new);

        @Override
        public Type<SaveResult> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: delete a structure of the managed datapack (US-42).
     *
     * @param structure the structure
     */
    public record Delete(EditorState.StructureRef structure) implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<Delete> TYPE = new Type<>(ArchitectsTrials.id("browser_delete"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Delete> STREAM_CODEC = STRUCTURE_REF.map(Delete::new, Delete::structure);

        @Override
        public Type<Delete> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: start a test run of exactly one challenge structure (US-42).
     *
     * @param id the metadata id
     */
    public record TestRun(Identifier id) implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<TestRun> TYPE = new Type<>(ArchitectsTrials.id("browser_test_run"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, TestRun> STREAM_CODEC = Identifier.STREAM_CODEC
                .<RegistryFriendlyByteBuf>cast().map(TestRun::new, TestRun::id);

        @Override
        public Type<TestRun> type() {
            return TYPE;
        }
    }

    /**
     * Registers the payloads and the server handlers.
     *
     * @param event the payload registration event
     */
    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(Open.TYPE, Open.STREAM_CODEC);
        registrar.playToClient(Update.TYPE, Update.STREAM_CODEC);
        registrar.playToClient(Busy.TYPE, Busy.STREAM_CODEC);
        registrar.playToClient(Close.TYPE, Close.STREAM_CODEC);
        registrar.playToClient(SaveResult.TYPE, SaveResult.STREAM_CODEC);
        registrar.playToServer(Save.TYPE, Save.STREAM_CODEC, (payload, context) -> operator(context).ifPresent(player -> {
            ChallengeEdits.SaveResult result = ChallengeEdits.save(player.level().getServer(), payload.id(), payload.revision(), payload.json());
            PacketDistributor.sendToPlayer(player, new SaveResult(payload.id(), result.status(), result.errors()));
        }));
        registrar.playToServer(Delete.TYPE, Delete.STREAM_CODEC, (payload, context) -> operator(context)
                .ifPresent(player -> player.sendSystemMessage(ChallengeEdits.delete(player.level().getServer(), payload.structure()))));
        registrar.playToServer(TestRun.TYPE, TestRun.STREAM_CODEC, (payload, context) -> operator(context).ifPresent(player -> {
            Optional<Component> failure = ChallengeEdits.testRun(player, payload.id());
            if (failure.isPresent()) {
                player.sendSystemMessage(failure.get().copy().withStyle(ChatFormatting.RED));
            } else {
                ChallengeBrowser.closed(player);
                PacketDistributor.sendToPlayer(player, new Close());
            }
        }));
        registrar.playToServer(Refresh.TYPE, Refresh.STREAM_CODEC, (payload, context) -> operator(context)
                .ifPresent(ChallengeBrowser::refresh));
        registrar.playToServer(Closed.TYPE, Closed.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ChallengeBrowser.closed(player);
            }
        });
        registrar.playToServer(Load.TYPE, Load.STREAM_CODEC, (payload, context) -> operator(context)
                .ifPresent(player -> load(player, payload)));
    }

    private static void load(ServerPlayer player, Load payload) {
        switch (EditorLoading.load(player, payload.structure(), payload.clearFirst())) {
            case EditorLoading.Loaded loaded -> {
                ChallengeBrowser.closed(player);
                PacketDistributor.sendToPlayer(player, new Close());
            }
            case EditorLoading.Busy busy -> PacketDistributor.sendToPlayer(player, new Busy(payload.structure(), busy.builders(), busy.last()));
            case EditorLoading.Failed failed -> player.sendSystemMessage(failed.message().copy().withStyle(ChatFormatting.RED));
        }
    }

    private static Optional<ServerPlayer> operator(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player && ChallengeBrowser.mayUse(player) ? Optional.of(player) : Optional.empty();
    }
}
