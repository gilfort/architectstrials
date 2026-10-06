package com.gilfort.architectstrials.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.structure.OreGeneration;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-37 (natural ore generation). {@code minecraft:the_nether} tiers 8–11 are 16 × 32 × 16 rooms
 * of stone (tier 9: netherrack) with a stone brick pillar in one corner and spawn and exit markers on top:
 * tier 8 with Overworld ores (plains, density 4), tier 9 with Nether ores (nether wastes, density 4), tier 10
 * with an unknown biome, tier 11 without ore generation.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class OreGenerationGameTests {

    private static final int SIZE = 16;
    private static final int LAYERS = 32;

    private OreGenerationGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "ore_generation_overworld", OreGenerationGameTests::overworld);
            register(helper, "ore_generation_nether", OreGenerationGameTests::nether);
            register(helper, "ore_generation_disabled", OreGenerationGameTests::disabled);
            register(helper, "ore_generation_settings", OreGenerationGameTests::settings);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Overworld ores appear in the stone room although the challenge dimension is the Nether; diamonds only in
     * the lowest layers (linear Y mapping); the stone brick pillar and the markers stay; other instances get a
     * different distribution.
     */
    private static void overworld(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengeInstance first = create(helper, nether, 8);
        ChallengeInstance second = create(helper, nether, 8);
        Map<Block, Integer> ores = count(nether, first);
        int overworldOres = ores.getOrDefault(Blocks.COAL_ORE, 0) + ores.getOrDefault(Blocks.IRON_ORE, 0)
                + ores.getOrDefault(Blocks.COPPER_ORE, 0);
        helper.assertTrue(overworldOres > 0, "No Overworld ores in the stone room: " + ores);
        helper.assertFalse(ores.containsKey(Blocks.NETHER_QUARTZ_ORE), "Nether ores in an Overworld ore room");
        int highestDiamond = highest(nether, first, Blocks.DIAMOND_ORE);
        helper.assertTrue(highestDiamond <= 13, "Diamond ore too high for the stretched Y range: layer " + highestDiamond);
        for (int y = 0; y < LAYERS; y++) {
            helper.assertTrue(nether.getBlockState(first.origin().above(y)).is(Blocks.STONE_BRICKS), "Ores replaced the stone brick pillar");
        }
        helper.assertTrue(first.ready() && !first.exits().isEmpty(), "Markers were not resolved after ore generation");
        helper.assertFalse(ores.equals(count(nether, second)), "Two instances got the same ore distribution");
        InstanceManager.close(nether, first.id());
        InstanceManager.close(nether, second.id());
        helper.succeed();
    }

    /**
     * Nether ores appear in the netherrack room.
     */
    private static void nether(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengeInstance instance = create(helper, nether, 9);
        Map<Block, Integer> ores = count(nether, instance);
        helper.assertTrue(ores.getOrDefault(Blocks.NETHER_QUARTZ_ORE, 0) + ores.getOrDefault(Blocks.NETHER_GOLD_ORE, 0) > 0,
                "No Nether ores in the netherrack room: " + ores);
        helper.assertFalse(ores.containsKey(Blocks.COAL_ORE), "Overworld ores in a Nether ore room");
        InstanceManager.close(nether, instance.id());
        helper.succeed();
    }

    /**
     * Without ore generation, or with an unknown biome, the stone room stays plain stone.
     */
    private static void disabled(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        for (int tier : new int[] {10, 11}) {
            ChallengeInstance instance = create(helper, nether, tier);
            Map<Block, Integer> blocks = count(nether, instance);
            helper.assertTrue(blocks.isEmpty(), "Tier " + tier + " room is not plain stone: " + blocks);
            helper.assertTrue(instance.ready(), "Tier " + tier + " instance is not ready");
            InstanceManager.close(nether, instance.id());
        }
        helper.succeed();
    }

    /**
     * The metadata is read; the default range follows the biome's home dimension; the default density is
     * structure height / range height.
     */
    private static void settings(GameTestHelper helper) {
        Optional<OreGeneration> settings = ChallengeStructures.get(ArchitectsTrials.id("the_nether/tier_8/ore_room"))
                .flatMap(structure -> structure.oreGeneration());
        helper.assertTrue(settings.isPresent() && settings.get().biome().equals(Biomes.PLAINS)
                && settings.get().density().equals(Optional.of(4.0F)), "ore_generation was not read: " + settings);
        helper.assertTrue(ChallengeStructures.get(ArchitectsTrials.id("the_nether/tier_11/plain_ore_room"))
                .map(structure -> structure.oreGeneration().isEmpty()).orElse(false), "Missing ore_generation is not empty");

        Holder<Biome> plains = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        Holder<Biome> wastes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.NETHER_WASTES);
        OreGeneration defaults = new OreGeneration(Biomes.PLAINS, Optional.empty(), Optional.empty(), Optional.empty());
        int[] overworld = defaults.range(plains);
        int[] netherRange = defaults.range(wastes);
        helper.assertTrue(overworld[0] == -64 && overworld[1] == 319, "Overworld default range wrong");
        helper.assertTrue(netherRange[0] == 0 && netherRange[1] == 127, "Nether default range wrong");
        helper.assertTrue(Math.abs(defaults.density(32, overworld) - 32F / 384F) < 1.0E-6F, "Default density is not height / range");
        OreGeneration custom = new OreGeneration(Biomes.PLAINS, Optional.of(0), Optional.of(63), Optional.of(2.5F));
        helper.assertTrue(custom.range(plains)[0] == 0 && custom.range(plains)[1] == 63 && custom.density(32, custom.range(plains)) == 2.5F,
                "Configured range / density not used");
        helper.succeed();
    }

    private static ChallengeInstance create(GameTestHelper helper, ServerLevel level, int tier) {
        InstanceCreation result = InstanceManager.create(level, ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow(), tier,
                level.getRandom(), InstanceManager.defaultTimeLimitTicks(), ScrollOptions.DEFAULT);
        if (!(result instanceof InstanceCreation.Success(ChallengeInstance created))) {
            throw new IllegalStateException("Instance creation failed for tier " + tier + ": "
                    + ((InstanceCreation.Failure) result).reason().getString());
        }
        return InstanceManager.data(level).get(created.id()).orElseThrow();
    }

    /** Counts every block of the room's fill volume that is not stone, netherrack or the stone brick pillar. */
    private static Map<Block, Integer> count(ServerLevel level, ChallengeInstance instance) {
        Map<Block, Integer> counts = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(instance.origin(), instance.origin().offset(SIZE - 1, LAYERS - 1, SIZE - 1))) {
            Block block = level.getBlockState(pos).getBlock();
            if (block != Blocks.STONE && block != Blocks.NETHERRACK && block != Blocks.STONE_BRICKS) {
                counts.merge(block, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** @return the highest layer (relative to the origin) containing the block, or −1 */
    private static int highest(ServerLevel level, ChallengeInstance instance, Block block) {
        int highest = -1;
        for (BlockPos pos : BlockPos.betweenClosed(instance.origin(), instance.origin().offset(SIZE - 1, LAYERS - 1, SIZE - 1))) {
            if (level.getBlockState(pos).is(block)) {
                highest = Math.max(highest, pos.getY() - instance.origin().getY());
            }
        }
        return highest;
    }
}
