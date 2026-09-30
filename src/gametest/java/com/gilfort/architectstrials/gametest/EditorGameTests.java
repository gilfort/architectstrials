package com.gilfort.architectstrials.gametest;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.EditorDimension;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.slot.Slot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-13 (editor dimension). The GameTest server does not load datapack dimensions, so the editor
 * logic runs against {@code minecraft:the_end}, which no other test uses.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class EditorGameTests {

    private EditorGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> helper.register(
                ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id("editor_border_platform_and_clear")),
                (Consumer<GameTestHelper>) EditorGameTests::borderPlatformAndClear));
    }

    /**
     * The border limits the area to 128 × 128; entering builds the platform and keeps the game mode; clearing
     * removes blocks and entities, rebuilds the platform and puts players back onto it.
     */
    private static void borderPlatformAndClear(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        EditorDimension.applyWorldBorder(end);
        helper.assertTrue(end.getWorldBorder().getSize() == Slot.MAX_STRUCTURE_SIZE, "World border is not 128 blocks wide");

        ServerPlayer builder = TestPlayers.atStart(helper, GameType.CREATIVE);
        EditorDimension.enter(builder, end);
        builder.hasChangedDimension();
        BlockPos platform = new BlockPos(0, EditorDimension.platformY(), 0);
        helper.assertTrue(end.getBlockState(platform).is(ModBlocks.EDITOR_PLATFORM.get()), "Platform was not created on entry");
        helper.assertTrue(builder.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "Entering the editor changed the game mode");

        BlockPos built = new BlockPos(20, EditorDimension.platformY() + 5, 20);
        end.setBlock(built, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        end.setBlock(platform.offset(1, 0, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        ArmorStand stand = EntityTypes.ARMOR_STAND.create(end, EntitySpawnReason.COMMAND);
        stand.setPos(10.5, EditorDimension.platformY() + 1, 10.5);
        end.addFreshEntity(stand);
        builder.teleportTo(30.5, EditorDimension.platformY() + 10, 30.5);

        AtomicBoolean cleared = new AtomicBoolean();
        EditorDimension.clear(end, level -> cleared.set(true));
        AABB area = AABB.of(EditorDimension.area(end));
        helper.succeedWhen(() -> {
            helper.assertTrue(cleared.get(), "Editor clear has not finished");
            helper.assertTrue(end.getBlockState(built).isAir(), "Built block was not removed");
            helper.assertTrue(end.getEntitiesOfClass(ArmorStand.class, area).isEmpty(), "Entity was not removed");
            helper.assertTrue(end.getBlockState(platform.offset(1, 0, 0)).is(ModBlocks.EDITOR_PLATFORM.get()), "Platform was not rebuilt");
            Vec3 spawn = EditorDimension.spawnPosition();
            helper.assertTrue(builder.position().distanceTo(spawn) < 0.01, "Builder was not moved onto the platform");
            TestPlayers.finish(helper, builder);
        });
    }
}
