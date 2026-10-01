package com.gilfort.architectstrials.loot;

import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payloads of the {@link LootSetupMenu}: the server sends the setup state and the loot table list; the client sends
 * edited values and picked loot tables. All of them address the open menu by its container id.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class LootNetwork {

    private LootNetwork() {
    }

    /**
     * Server → client: the setup shown in the menu.
     *
     * @param containerId    the container id
     * @param setup          the setup
     * @param page           the current page
     * @param ominous        whether the ominous setup is shown
     * @param ominousVariant whether the source has an ominous setup
     */
    public record Sync(int containerId, LootSetup setup, int page, boolean ominous, boolean ominousVariant) implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<Sync> TYPE = new Type<>(ArchitectsTrials.id("loot_setup_sync"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::containerId,
                LootSetup.STREAM_CODEC, Sync::setup,
                ByteBufCodecs.VAR_INT, Sync::page,
                ByteBufCodecs.BOOL, Sync::ominous,
                ByteBufCodecs.BOOL, Sync::ominousVariant,
                Sync::new);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: all loot table ids, for the picker.
     *
     * @param containerId the container id
     * @param tables      the loot table ids
     */
    public record TableList(int containerId, List<Identifier> tables) implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<TableList> TYPE = new Type<>(ArchitectsTrials.id("loot_table_list"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, TableList> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TableList::containerId,
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), TableList::tables,
                TableList::new);

        @Override
        public Type<TableList> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: chance and roll range of an entry of the current page.
     *
     * @param containerId the container id
     * @param page        the page the client edited (ignored by the server if it shows another page)
     * @param ominous     whether the client edited the ominous setup
     * @param position    the position
     * @param chance      the chance in tenths of a percent
     * @param min         the minimum rolls
     * @param max         the maximum rolls
     */
    public record EditValues(int containerId, int page, boolean ominous, int position, int chance, int min, int max)
            implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<EditValues> TYPE = new Type<>(ArchitectsTrials.id("loot_entry_values"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, EditValues> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EditValues::containerId,
                ByteBufCodecs.VAR_INT, EditValues::page,
                ByteBufCodecs.BOOL, EditValues::ominous,
                ByteBufCodecs.VAR_INT, EditValues::position,
                ByteBufCodecs.VAR_INT, EditValues::chance,
                ByteBufCodecs.VAR_INT, EditValues::min,
                ByteBufCodecs.VAR_INT, EditValues::max,
                EditValues::new);

        @Override
        public Type<EditValues> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: the loot table of an entry of the current page (empty removes it).
     *
     * @param containerId the container id
     * @param page        the page the client edited (ignored by the server if it shows another page)
     * @param ominous     whether the client edited the ominous setup
     * @param position    the position
     * @param table       the loot table id
     */
    public record EditTable(int containerId, int page, boolean ominous, int position, Optional<Identifier> table)
            implements CustomPacketPayload {

        /** Payload type. */
        public static final Type<EditTable> TYPE = new Type<>(ArchitectsTrials.id("loot_entry_table"));

        /** Network codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, EditTable> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EditTable::containerId,
                ByteBufCodecs.VAR_INT, EditTable::page,
                ByteBufCodecs.BOOL, EditTable::ominous,
                ByteBufCodecs.VAR_INT, EditTable::position,
                ByteBufCodecs.optional(Identifier.STREAM_CODEC), EditTable::table,
                EditTable::new);

        @Override
        public Type<EditTable> type() {
            return TYPE;
        }
    }

    /**
     * Registers the payloads.
     *
     * @param event the payload registration event
     */
    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(Sync.TYPE, Sync.STREAM_CODEC, (payload, context) -> menu(context, payload.containerId())
                .ifPresent(menu -> menu.applySync(payload.setup(), payload.page(), payload.ominous(), payload.ominousVariant())));
        registrar.playToClient(TableList.TYPE, TableList.STREAM_CODEC, (payload, context) -> menu(context, payload.containerId())
                .ifPresent(menu -> menu.applyTables(payload.tables())));
        registrar.playToServer(EditValues.TYPE, EditValues.STREAM_CODEC, (payload, context) -> menu(context, payload.containerId())
                .filter(menu -> validPosition(payload.position()) && menu.shows(payload.page(), payload.ominous()))
                .ifPresent(menu -> menu.setValues(payload.position(), payload.chance(), payload.min(), payload.max())));
        registrar.playToServer(EditTable.TYPE, EditTable.STREAM_CODEC, (payload, context) -> menu(context, payload.containerId())
                .filter(menu -> validPosition(payload.position()) && menu.shows(payload.page(), payload.ominous()))
                .ifPresent(menu -> menu.setTable(payload.position(), payload.table().map(id -> ResourceKey.create(Registries.LOOT_TABLE, id)))));
    }

    private static boolean validPosition(int position) {
        return position >= 0 && position < LootGroup.MAX_ENTRIES;
    }

    private static Optional<LootSetupMenu> menu(IPayloadContext context, int containerId) {
        return context.player().containerMenu instanceof LootSetupMenu menu && menu.containerId == containerId
                ? Optional.of(menu) : Optional.empty();
    }
}
