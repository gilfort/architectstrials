package com.gilfort.architectstrials.client.browser;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.browser.BrowserNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Client handlers of the challenge browser payloads (US-40): open the browser, update it, ask before clearing the
 * editor and close it once the structure is loaded.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID, value = Dist.CLIENT)
public final class BrowserClientHandlers {

    private BrowserClientHandlers() {
    }

    /**
     * Registers the client handlers of the server → client payloads.
     *
     * @param event the client payload handler registration event
     */
    @SubscribeEvent
    static void onRegisterClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(BrowserNetwork.Open.TYPE, (payload, context) ->
                Minecraft.getInstance().gui.setScreen(new ChallengeBrowserScreen(payload.snapshot())));
        event.register(BrowserNetwork.Update.TYPE, (payload, context) -> {
            if (ChallengeBrowserScreen.current != null) {
                ChallengeBrowserScreen.current.update(payload.snapshot());
            }
        });
        event.register(BrowserNetwork.Busy.TYPE, (payload, context) -> {
            if (ChallengeBrowserScreen.current != null) {
                ChallengeBrowserScreen.current.confirmClear(payload);
            }
        });
        event.register(BrowserNetwork.Close.TYPE, (payload, context) -> {
            Screen screen = Minecraft.getInstance().gui.screen();
            if (ChallengeBrowserScreen.current != null && (screen == ChallengeBrowserScreen.current || screen instanceof ConfirmScreen)) {
                Minecraft.getInstance().gui.setScreen(null);
            }
            ChallengeBrowserScreen.current = null;
        });
    }
}
