package com.gilfort.architectstrials.editor;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
}
