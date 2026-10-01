package com.gilfort.architectstrials.client;

import com.gilfort.architectstrials.registry.ModDataComponents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Client-only entry point for opening the {@link SelectionEditScreen} from common code. Its signature contains no
 * client classes, so the selection tool can reference it; it is only ever called on the client.
 */
public final class SelectionEditorAccess {

    private SelectionEditorAccess() {
    }

    /**
     * Opens the corner editor for the selection tool in the given hand.
     *
     * @param hand the hand holding the tool
     */
    public static void open(InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null) {
            minecraft.gui.setScreen(new SelectionEditScreen(hand, player.getItemInHand(hand).get(ModDataComponents.SELECTION.get()),
                    player.blockPosition()));
        }
    }
}
