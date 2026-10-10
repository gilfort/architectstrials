package com.gilfort.architectstrials.browser;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * Key figures of a structure template shown in the challenge browser (US-40): size, markers, enemies and loot.
 * Computed on the server from the raw template file; the template itself never goes to the client.
 *
 * @param found          whether the template file was found
 * @param sizeX          the size along X
 * @param sizeY          the size along Y
 * @param sizeZ          the size along Z
 * @param playerSpawns   number of Player Spawn Markers
 * @param exits          number of Exit Markers
 * @param exitsNeedMobs  number of Exit Markers that need all required mobs defeated
 * @param mobs           enemies grouped by marker kind, entity type and required flag
 * @param loot           loot sources grouped by kind and loot table
 * @param vaults         number of Vault Markers
 * @param subStructures  sub structure ids referenced by Sub Structure Markers (entries and fallbacks), sorted
 */
public record StructureStats(boolean found, int sizeX, int sizeY, int sizeZ, int playerSpawns, int exits, int exitsNeedMobs,
        List<MobCount> mobs, List<LootCount> loot, int vaults, List<Identifier> subStructures) {

    /** Statistics of a template that could not be found. */
    public static final StructureStats MISSING = new StructureStats(false, 0, 0, 0, 0, 0, 0, List.of(), List.of(), 0, List.of());

    /** Codec, used for the network transfer. */
    public static final Codec<StructureStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("found").forGetter(StructureStats::found),
            Codec.INT.fieldOf("size_x").forGetter(StructureStats::sizeX),
            Codec.INT.fieldOf("size_y").forGetter(StructureStats::sizeY),
            Codec.INT.fieldOf("size_z").forGetter(StructureStats::sizeZ),
            Codec.INT.fieldOf("player_spawns").forGetter(StructureStats::playerSpawns),
            Codec.INT.fieldOf("exits").forGetter(StructureStats::exits),
            Codec.INT.fieldOf("exits_need_mobs").forGetter(StructureStats::exitsNeedMobs),
            MobCount.CODEC.listOf().fieldOf("mobs").forGetter(StructureStats::mobs),
            LootCount.CODEC.listOf().fieldOf("loot").forGetter(StructureStats::loot),
            Codec.INT.fieldOf("vaults").forGetter(StructureStats::vaults),
            Identifier.CODEC.listOf().fieldOf("sub_structures").forGetter(StructureStats::subStructures)
    ).apply(instance, StructureStats::new));

    /** Creates the statistics, defensively copying the lists. */
    public StructureStats {
        mobs = List.copyOf(mobs);
        loot = List.copyOf(loot);
        subStructures = List.copyOf(subStructures);
    }

    /** The marker that spawns a group of enemies. */
    public enum MarkerKind implements StringRepresentable {
        /** Direct Spawn Marker: mobs spawned once on placement. */
        DIRECT("direct"),
        /** Spawner Marker: a vanilla monster spawner. */
        SPAWNER("spawner"),
        /** Trial Spawner Marker, normal rows. */
        TRIAL("trial"),
        /** Trial Spawner Marker, ominous rows. */
        TRIAL_OMINOUS("trial_ominous");

        /** Codec by serialized name. */
        public static final Codec<MarkerKind> CODEC = StringRepresentable.fromEnum(MarkerKind::values);

        private final String name;

        MarkerKind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    /**
     * A group of enemies.
     *
     * @param kind     the marker kind
     * @param entity   the entity type id
     * @param count    the number of mobs (spawners: per spawn cycle; trial spawners: in total)
     * @param required whether the mobs are required to open sealed exits
     */
    public record MobCount(MarkerKind kind, Identifier entity, int count, boolean required) {

        /** Codec. */
        public static final Codec<MobCount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MarkerKind.CODEC.fieldOf("kind").forGetter(MobCount::kind),
                Identifier.CODEC.fieldOf("entity").forGetter(MobCount::entity),
                Codec.INT.fieldOf("count").forGetter(MobCount::count),
                Codec.BOOL.fieldOf("required").forGetter(MobCount::required)
        ).apply(instance, MobCount::new));
    }

    /** How a container gets its loot. */
    public enum LootKind implements StringRepresentable {
        /** A loot table, rolled on placement (pool loot). */
        TABLE("table"),
        /** A loot setup composed with the Loot Tool. */
        SETUP("setup"),
        /** Filled by hand (guaranteed loot). */
        FILLED("filled");

        /** Codec by serialized name. */
        public static final Codec<LootKind> CODEC = StringRepresentable.fromEnum(LootKind::values);

        private final String name;

        LootKind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    /**
     * A group of containers with the same loot source.
     *
     * @param kind  the loot kind
     * @param table the loot table ({@link LootKind#TABLE} only)
     * @param count the number of containers
     */
    public record LootCount(LootKind kind, Optional<Identifier> table, int count) {

        /** Codec. */
        public static final Codec<LootCount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LootKind.CODEC.fieldOf("kind").forGetter(LootCount::kind),
                Identifier.CODEC.optionalFieldOf("table").forGetter(LootCount::table),
                Codec.INT.fieldOf("count").forGetter(LootCount::count)
        ).apply(instance, LootCount::new));
    }
}
