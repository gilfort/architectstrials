package com.gilfort.architectstrials.gametest;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.slot.Slot;
import com.gilfort.architectstrials.slot.SlotManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTest for clearing entities of released slots whose chunks are no longer loaded (leftover armor stands and
 * item frames reappeared in the next instance of the slot).
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class SlotEntityClearGameTests {

    private SlotEntityClearGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> helper.register(
                ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id("slot_clear_removes_unloaded_entities")),
                (Consumer<GameTestHelper>) SlotEntityClearGameTests::removesUnloadedEntities));
    }

    /**
     * An armor stand in a slot whose chunk has been unloaded is removed when the slot is released.
     */
    private static void removesUnloadedEntities(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        Slot slot = SlotManager.allocate(nether).orElseThrow();
        BlockPos pos = new BlockPos(slot.centerX(), 64, slot.centerZ());
        ChunkPos chunk = ChunkPos.containing(pos);
        nether.getChunkSource().addTicketWithRadius(TicketType.FORCED, chunk, 0);
        AABB area = new AABB(pos).inflate(2);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(nether.areEntitiesLoaded(chunk.pack()), "Waiting for the slot chunk"))
                .thenExecute(() -> {
                    ArmorStand stand = EntityTypes.ARMOR_STAND.create(nether, EntitySpawnReason.COMMAND);
                    stand.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    nether.addFreshEntity(stand);
                    nether.getChunkSource().removeTicketWithRadius(TicketType.FORCED, chunk, 0);
                })
                .thenWaitUntil(() -> helper.assertFalse(nether.areEntitiesLoaded(chunk.pack()), "Waiting for the slot chunk to unload"))
                .thenExecute(() -> SlotManager.release(nether, slot.index()))
                .thenWaitUntil(() -> helper.assertFalse(SlotManager.isClearing(nether, slot.index()), "Waiting for the slot to be cleared"))
                .thenExecute(() -> nether.getChunkSource().addTicketWithRadius(TicketType.FORCED, chunk, 0))
                .thenWaitUntil(() -> helper.assertTrue(nether.areEntitiesLoaded(chunk.pack()), "Waiting for the slot chunk again"))
                .thenExecute(() -> {
                    int left = nether.getEntitiesOfClass(ArmorStand.class, area).size();
                    nether.getChunkSource().removeTicketWithRadius(TicketType.FORCED, chunk, 0);
                    helper.assertTrue(left == 0, "Armor stand from the unloaded chunk survived the slot clearing");
                })
                .thenSucceed();
    }
}
