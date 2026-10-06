package com.gilfort.architectstrials.marker;

import java.util.Map;
import java.util.Optional;

import com.gilfort.architectstrials.util.LenientCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The equipment configuration of one mob marker row, applied to every mob spawned from it.
 * <p>
 * Order: the mob's natural equipment (from its spawn setup), then the row's equipment loot table, then fixed items
 * and finally the weighted lists of the slots. A list that draws "nothing" empties its slot. All drop chances are
 * set to 0 %.
 *
 * @param fixed the fixed items by slot ("100 %")
 * @param lists the weighted lists by slot
 * @param table the equipment loot table of the row, if any (e.g. imported from a vanilla spawner)
 */
public record MarkerEquipment(Map<EquipmentSlot, ItemStack> fixed, Map<EquipmentSlot, EquipmentList> lists,
        Optional<ResourceKey<LootTable>> table) {

    /** No equipment configuration. */
    public static final MarkerEquipment NONE = new MarkerEquipment(Map.of(), Map.of(), Optional.empty());

    /** Persistent codec. */
    public static final Codec<MarkerEquipment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LenientCodecs.map(EquipmentSlot.CODEC, ItemStack.CODEC, "fixed equipment").optionalFieldOf("fixed", Map.of()).forGetter(MarkerEquipment::fixed),
            LenientCodecs.map(EquipmentSlot.CODEC, EquipmentList.CODEC, "equipment list").optionalFieldOf("lists", Map.of()).forGetter(MarkerEquipment::lists),
            ResourceKey.codec(Registries.LOOT_TABLE).optionalFieldOf("table").forGetter(MarkerEquipment::table)
    ).apply(instance, MarkerEquipment::new));

    /** Creates the configuration, defensively copying the maps. */
    public MarkerEquipment {
        fixed = Map.copyOf(fixed);
        lists = Map.copyOf(lists);
    }

    /**
     * Equips a mob (see the class description) and sets all its drop chances to 0 %.
     *
     * @param mob    the mob, after its natural spawn setup
     * @param random the random source for the weighted lists
     */
    public void apply(Mob mob, RandomSource random) {
        if (this.table.isPresent() && mob.level() instanceof ServerLevel) {
            mob.equip(this.table.get(), Map.of());
        }
        this.fixed.forEach((slot, stack) -> mob.setItemSlot(slot, stack.copy()));
        this.lists.forEach((slot, list) -> {
            if (!list.isEmpty()) {
                mob.setItemSlot(slot, list.roll(random));
            }
        });
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, 0.0F);
        }
    }
}
