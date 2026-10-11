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
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixTypes;
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
                StructureMesh mesh = StructureMesh.build(template, minecraft.getModelManager().getBlockStateModelSet(), minecraft.getBlockColors(), null);
                String name = resource.substring(resource.lastIndexOf('/') + 1).replace(".nbt", "");
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
            } catch (IOException | RuntimeException e) {
                ArchitectsTrials.LOGGER.error("Preview dump of {} failed", resource, e);
            }
        }
    }
}
