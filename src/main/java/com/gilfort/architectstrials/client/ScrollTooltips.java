package com.gilfort.architectstrials.client;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.scroll.ScrollTarget;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Adds the rank requirement of a scroll to its tooltip, green if the viewing player meets it and red if
 * not. Uses the player's own ranks, which are synced to their client.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID, value = Dist.CLIENT)
public final class ScrollTooltips {

    private ScrollTooltips() {
    }

    /**
     * Appends the requirement line to scroll tooltips.
     *
     * @param event the tooltip event
     */
    @SubscribeEvent
    static void onItemTooltip(ItemTooltipEvent event) {
        ScrollTarget target = event.getItemStack().get(ModDataComponents.SCROLL_TARGET.get());
        Player player = event.getEntity();
        if (target == null || player == null) {
            return;
        }
        boolean allowed = player.getData(ModAttachments.RANK).allows(target.theme(), target.tier());
        event.getToolTip().add(Component.translatable("tooltip.architectstrials.challenge_scroll.requires", target.tier())
                .withStyle(allowed ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
