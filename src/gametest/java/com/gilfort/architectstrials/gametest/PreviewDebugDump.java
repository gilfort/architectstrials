package com.gilfort.architectstrials.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.client.browser.preview.PreviewRenderer;
import com.gilfort.architectstrials.client.browser.preview.StructureMesh;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Development aid for US-43, only in dev runs (never in the jar): if the file
 * {@code <gamedir>/architectstrials/preview-debug/.dump} exists, the title screen renders the previews of a few templates from all four
 * directions into {@code <gamedir>/architectstrials/preview-debug/}, so the camera can be checked without a world.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID, value = Dist.CLIENT)
public final class PreviewDebugDump {

    private static final List<String> TEMPLATES = List.of("/data/architectstrials/structure/gametest/spawn_platform.nbt",
            "/data/minecraft/structure/igloo/top.nbt", "/data/minecraft/structure/village/plains/houses/plains_small_house_1.nbt");
    private static boolean done;

    private PreviewDebugDump() {
    }

    /**
     * Renders the debug previews once the title screen is shown.
     *
     * @param event the screen init event
     */
    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Path directory = minecraft.gameDirectory.toPath().resolve(ArchitectsTrials.MOD_ID).resolve("preview-debug");
        if (done || !(event.getScreen() instanceof TitleScreen) || !Files.exists(directory.resolve(".dump"))) {
            return;
        }
        done = true;
        dump(directory, "compass", compass());
        for (String resource : TEMPLATES) {
            try (InputStream stream = PreviewDebugDump.class.getResourceAsStream(resource) != null
                    ? PreviewDebugDump.class.getResourceAsStream(resource) : Minecraft.class.getResourceAsStream(resource)) {
                if (stream == null) {
                    ArchitectsTrials.LOGGER.warn("Preview dump: {} not found", resource);
                    continue;
                }
                CompoundTag tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
                tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(minecraft.getFixerUpper(), tag, NbtUtils.getDataVersion(tag, 500));
                StructureTemplate template = new StructureTemplate();
                template.load(BuiltInRegistries.BLOCK, tag);
                dump(directory, resource.substring(resource.lastIndexOf('/') + 1).replace(".nbt", ""), template);
            } catch (IOException | RuntimeException e) {
                ArchitectsTrials.LOGGER.error("Preview dump of {} failed", resource, e);
            }
        }
    }

    private static void dump(Path directory, String name, StructureTemplate template) {
        Minecraft minecraft = Minecraft.getInstance();
        StructureMesh mesh = StructureMesh.build(template, minecraft.getModelManager().getBlockStateModelSet(), minecraft.getBlockColors(), null);
        ArchitectsTrials.LOGGER.info("Preview dump: {} has {} quads", name, mesh.quads().size());
        for (int direction = 0; direction < 4; direction++) {
            Path file = directory.resolve(name + "_" + direction + ".png");
            PreviewRenderer.render(mesh, direction, image -> {
                try {
                    Files.createDirectories(directory);
                    image.writeToFile(file);
                    ArchitectsTrials.LOGGER.info("Preview dump written: {}", file);
                } catch (IOException e) {
                    ArchitectsTrials.LOGGER.warn("Preview dump failed", e);
                } finally {
                    image.close();
                }
            });
        }
    }

    /**
     * An asymmetric test template to check the compass directions of the views: stone at the origin with a gold block
     * on top, a redstone block to the east (+X), a lapis block to the south (+Z).
     */
    private static StructureTemplate compass() {
        CompoundTag tag = new CompoundTag();
        tag.put("size", newIntList(3, 2, 3));
        ListTag palette = new ListTag();
        for (BlockState state : List.of(Blocks.STONE.defaultBlockState(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Blocks.LAPIS_BLOCK.defaultBlockState(),
                Blocks.GOLD_BLOCK.defaultBlockState())) {
            palette.add(NbtUtils.writeBlockState(state));
        }
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        int[][] positions = {{0, 0, 0, 0}, {2, 0, 0, 1}, {0, 0, 2, 2}, {0, 1, 0, 3}};
        for (int[] position : positions) {
            CompoundTag block = new CompoundTag();
            block.put("pos", newIntList(position[0], position[1], position[2]));
            block.putInt("state", position[3]);
            blocks.add(block);
        }
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK, tag);
        return template;
    }

    private static ListTag newIntList(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }
}
