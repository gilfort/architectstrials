package com.gilfort.architectstrials.gametest;

import java.util.Map;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.loot.LootGroup;
import com.gilfort.architectstrials.marker.EquipmentList;
import com.gilfort.architectstrials.marker.MarkerEquipment;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.registry.ModItems;
import com.gilfort.architectstrials.scroll.ParkedEffects;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.structure.StructureValidation;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-27 (data of removed mods): every lenient codec keeps the valid entries when one entry refers to
 * an unknown id, and {@code /at structure validate} reports missing content of a stored structure.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class MissingContentGameTests {

    private static final String SPEED = "{\"target\": \"player\", \"effect\": \"minecraft:speed\", \"duration\": 100}";
    private static final String MISSING_EFFECT = "{\"target\": \"player\", \"effect\": \"missingmod:strange_effect\", \"duration\": 100}";

    private MissingContentGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "missing_ids_are_skipped", MissingContentGameTests::missingIdsAreSkipped);
            register(helper, "structure_validate_reports_missing_content", MissingContentGameTests::validateReportsMissingContent);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Each codec decodes a list / map with one unknown id: the unknown entry is skipped, the valid ones stay.
     */
    private static void missingIdsAreSkipped(GameTestHelper helper) {
        DynamicOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);

        EquipmentList equipment = parse(ops, EquipmentList.CODEC, "["
                + "{\"position\": 0, \"item\": {\"id\": \"minecraft:iron_sword\", \"count\": 1}, \"chance\": 50},"
                + "{\"position\": 1, \"item\": {\"id\": \"missingmod:strange_item\", \"count\": 1}, \"chance\": 50}]");
        helper.assertTrue(equipment.entries().size() == 1, "Random equipment: expected 1 entry, got " + equipment.entries());

        MarkerEquipment fixed = parse(ops, MarkerEquipment.CODEC, "{\"fixed\": {"
                + "\"head\": {\"id\": \"minecraft:iron_helmet\", \"count\": 1},"
                + "\"chest\": {\"id\": \"missingmod:strange_item\", \"count\": 1}}}");
        helper.assertTrue(fixed.fixed().size() == 1 && fixed.fixed().containsKey(EquipmentSlot.HEAD),
                "Fixed equipment: expected only the helmet, got " + fixed.fixed());

        LootGroup group = parse(ops, LootGroup.CODEC, "["
                + "{\"position\": 0, \"item\": {\"id\": \"minecraft:diamond\", \"count\": 1}, \"chance\": 50},"
                + "{\"position\": 1, \"item\": {\"id\": \"missingmod:strange_item\", \"count\": 1}, \"chance\": 50}]");
        helper.assertTrue(group.entries().size() == 1, "Loot setup: expected 1 entry, got " + group.entries());

        ScrollEffects effects = parse(ops, ScrollEffects.CODEC, "[" + SPEED + "," + MISSING_EFFECT + "]");
        helper.assertTrue(effects.entries().size() == 1, "Scroll effects: expected 1 entry, got " + effects.entries());

        ItemStack scroll = parse(ops, ItemStack.CODEC, "{\"id\": \"" + ModItems.CHALLENGE_SCROLL.getId() + "\", \"count\": 1,"
                + "\"components\": {\"" + ArchitectsTrials.id("effects") + "\": [" + SPEED + "," + MISSING_EFFECT + "]}}");
        helper.assertTrue(scroll.is(ModItems.CHALLENGE_SCROLL.get()), "Scroll with an unknown effect did not survive");
        helper.assertTrue(scroll.getOrDefault(ModDataComponents.SCROLL_EFFECTS.get(), ScrollEffects.NONE).entries().size() == 1,
                "Scroll did not keep its known effect");

        ParkedEffects parked = parse(ops, ParkedEffects.MAP_CODEC.codec(),
                "{\"applied\": [\"minecraft:speed\", \"missingmod:strange_effect\"]}");
        helper.assertTrue(parked.applied().size() == 1, "Parked effects: expected 1 entry, got " + parked.applied());
        helper.succeed();
    }

    /**
     * The validation reads the raw template and reports a missing block, item, entity type, loot table and mob
     * effect; a clean structure reports nothing.
     */
    private static void validateReportsMissingContent(GameTestHelper helper) {
        Map<String, Integer> problems = StructureValidation.validate(helper.getLevel().getServer(),
                ChallengeStructures.get(ArchitectsTrials.id("gametest_validation/tier_2/missing_content")).orElseThrow());
        for (String expected : new String[] {"missing block: missingmod:strange_block", "missing item: missingmod:strange_item",
                "missing entity: missingmod:strange_mob", "missing loot table: missingmod:chests/strange",
                "missing mob effect: missingmod:strange_effect"}) {
            helper.assertTrue(problems.containsKey(expected), "Not reported: " + expected + " (got " + problems.keySet() + ")");
        }
        helper.assertTrue(problems.size() == 5, "Unexpected problems reported: " + problems.keySet());

        Map<String, Integer> clean = StructureValidation.validate(helper.getLevel().getServer(),
                ChallengeStructures.get(ArchitectsTrials.id("the_nether/tier_2/spawn_platform")).orElseThrow());
        helper.assertTrue(clean.isEmpty(), "Problems reported for a clean structure: " + clean.keySet());
        helper.succeed();
    }

    private static <T> T parse(DynamicOps<JsonElement> ops, Codec<T> codec, String json) {
        return codec.parse(ops, JsonParser.parseString(json))
                .getOrThrow(error -> new IllegalStateException("Decoding failed although only one entry is unknown: " + error));
    }
}
