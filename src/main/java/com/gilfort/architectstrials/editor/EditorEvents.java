package com.gilfort.architectstrials.editor;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Game event hooks of the editor dimension.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class EditorEvents {

    private EditorEvents() {
    }

    /**
     * Applies the editor's world border when the server has started.
     *
     * @param event the server started event
     */
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        ServerLevel editor = event.getServer().getLevel(EditorDimension.KEY);
        if (editor != null) {
            EditorDimension.applyWorldBorder(editor);
        }
    }

    /**
     * Keeps pool-loot containers in the editor from being opened: opening would roll the loot table into the
     * container and remove the reference, turning pool loot into guaranteed loot on the next save. The player is
     * told the loot table instead. Placing blocks against the container while sneaking stays possible.
     *
     * @param event the right-click event
     */
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(EditorDimension.KEY)) {
            return;
        }
        Player player = event.getEntity();
        if (player.isSecondaryUseActive() && !event.getItemStack().isEmpty()) {
            return;
        }
        if (level.getBlockEntity(event.getPos()) instanceof RandomizableContainer container && container.getLootTable() != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            player.sendOverlayMessage(Component.translatable("message.architectstrials.editor.loot_container_locked",
                    container.getLootTable().identifier().toString()));
        }
    }
}
