package com.gilfort.architectstrials.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * Keeps the mouse cursor in place when switching between the marker screens and the equipment list screen.
 * <p>
 * The server switches menus by closing one and opening the next; vanilla then grabs the mouse and releases it again
 * in the screen center. The marker screens remember the cursor when they close and restore it when the next one
 * opens shortly afterwards.
 */
public final class CursorMemory {

    /** How long a remembered position stays valid, in milliseconds. */
    private static final long VALID_MILLIS = 1000L;

    private static double x;
    private static double y;
    private static long rememberedAt = Long.MIN_VALUE;

    private CursorMemory() {
    }

    /** Remembers the current cursor position (called when a marker screen closes). */
    public static void remember() {
        Minecraft minecraft = Minecraft.getInstance();
        x = minecraft.mouseHandler.xpos();
        y = minecraft.mouseHandler.ypos();
        rememberedAt = Util.getMillis();
    }

    /** Moves the cursor back to the remembered position if it was remembered just before (called on init). */
    public static void restore() {
        if (Util.getMillis() - rememberedAt > VALID_MILLIS) {
            return;
        }
        rememberedAt = Long.MIN_VALUE;
        InputConstants.releaseMouse(Minecraft.getInstance().getWindow(), x, y);
    }
}
