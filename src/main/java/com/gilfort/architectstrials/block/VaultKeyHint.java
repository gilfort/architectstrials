package com.gilfort.architectstrials.block;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.theme.ChallengeThemes;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Tells players in a challenge what opens a vault (US-26): clicking a vault with the wrong item or an empty hand
 * shows "Opens with: 2× Crypt Key" in the action bar. Vanilla's fail sound stays; the click itself is not changed.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class VaultKeyHint {

    private VaultKeyHint() {
    }

    /**
     * Shows the key hint when a vault is clicked with something that does not open it.
     *
     * @param event the click event
     */
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ChallengeThemes.isChallengeDimension(player.level().dimension())
                || !(player.level().getBlockEntity(event.getPos()) instanceof VaultBlockEntity vault)) {
            return;
        }
        ItemStack key = vault.getConfig().keyItem();
        if (!opens(key, event.getItemStack())) {
            player.sendOverlayMessage(hint(key));
        }
    }

    /**
     * Checks whether a held stack opens a vault with the given key (same item and components, enough items).
     *
     * @param key  the vault's key
     * @param held the held stack
     * @return {@code true} if the stack opens the vault
     */
    public static boolean opens(ItemStack key, ItemStack held) {
        return ItemStack.isSameItemSameComponents(key, held) && held.getCount() >= key.getCount();
    }

    /**
     * Creates the hint naming a vault's key.
     *
     * @param key the key
     * @return the message
     */
    public static Component hint(ItemStack key) {
        return Component.translatable("message.architectstrials.vault.opens_with", key.getCount(), key.getHoverName());
    }
}
