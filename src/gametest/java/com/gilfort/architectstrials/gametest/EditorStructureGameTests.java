package com.gilfort.architectstrials.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.EditorCapture;
import com.gilfort.architectstrials.editor.StructureLibrary;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.structure.ChallengeStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-14 (saving structures from the editor). Capturing runs on a small area of the test region;
 * files are written to the GameTest world's managed datapack (without reloading, which would disturb parallel
 * tests).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class EditorStructureGameTests {

    private EditorStructureGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> helper.register(
                ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id("editor_capture_validate_write")),
                (Consumer<GameTestHelper>) EditorStructureGameTests::captureValidateWrite));
    }

    private static void captureValidateWrite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 9; x++) {
            for (int y = 1; y <= 6; y++) {
                for (int z = 0; z <= 9; z++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        for (int x = 1; x <= 6; x++) {
            for (int z = 1; z <= 6; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        helper.setBlock(8, 1, 8, ModBlocks.EDITOR_PLATFORM.get());
        helper.setBlock(2, 2, 2, ModBlocks.PLAYER_SPAWN_MARKER.get());
        helper.setBlock(5, 2, 5, ModBlocks.EXIT_MARKER.get());
        Mob zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.setPos(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
        zombie.setNoAi(true);
        level.addFreshEntity(zombie);
        ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        stand.setPos(helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
        level.addFreshEntity(stand);

        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(9, 6, 9)));
        EditorCapture.Captured captured = EditorCapture.capture(level, area).orElseThrow();
        StructureTemplate template = captured.template();
        helper.assertTrue(template.getSize().getX() == 6 && template.getSize().getY() == 2 && template.getSize().getZ() == 6,
                "Capture was not trimmed to the built blocks (platform must be ignored): " + template.getSize());
        helper.assertTrue(template.save(new CompoundTag()).getListOrEmpty(StructureTemplate.ENTITIES_TAG).size() == 1,
                "Capture must contain the armor stand but not the zombie");

        EditorCapture.Validation valid = EditorCapture.validate(level, captured);
        helper.assertTrue(valid.valid() && valid.warnings().isEmpty(), "Valid structure was rejected or warned: " + valid);

        helper.setBlock(2, 3, 2, Blocks.STONE);
        EditorCapture.Validation blocked = EditorCapture.validate(level, captured);
        helper.assertTrue(blocked.valid() && blocked.warnings().size() == 1, "Blocked spawn marker did not produce exactly one warning");

        helper.setBlock(5, 2, 5, Blocks.AIR);
        EditorCapture.Validation noExit = EditorCapture.validate(level, EditorCapture.capture(level, area).orElseThrow());
        helper.assertFalse(noExit.valid(), "Structure without exit marker was accepted");

        MinecraftServer server = level.getServer();
        StructureLibrary.Entry entry = new StructureLibrary.Entry(Level.NETHER.identifier(), 7, "gametest_saved");
        ChallengeStructure metadata = new ChallengeStructure(entry.theme(), entry.tier(), entry.structureId(), Optional.of("Saved"),
                Optional.of("gametest"), Optional.of(0L), 1, false);
        try {
            StructureLibrary.write(server, entry, template, metadata);
            helper.assertTrue(Files.exists(StructureLibrary.packRoot(server).resolve("pack.mcmeta")), "pack.mcmeta was not created");
            helper.assertTrue(StructureLibrary.readMetadata(server, entry).equals(Optional.of(metadata)), "Metadata did not round-trip");
            CompoundTag saved = NbtIo.readCompressed(StructureLibrary.packRoot(server).resolve("data/minecraft/structure/challenges/the_nether/tier_7/gametest_saved.nbt"),
                    NbtAccounter.unlimitedHeap());
            StructureTemplate reloaded = new StructureTemplate();
            reloaded.load(level.holderLookup(Registries.BLOCK), saved);
            helper.assertTrue(reloaded.getSize().equals(template.getSize()), "Saved template has a different size");
            helper.assertTrue(StructureLibrary.delete(server, entry), "Saved files were not deleted");
        } catch (IOException e) {
            helper.fail("I/O error: " + e.getMessage());
            return;
        }
        zombie.discard();
        stand.discard();
        helper.succeed();
    }
}
