package com.gilfort.architectstrials.marker;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.MobMarkerBlockEntity;
import com.gilfort.architectstrials.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The Spawn Marker Tool (US-39), handled like the sub structure tool. On a Direct Spawn, Spawner or Trial Spawner
 * Marker:
 * <ul>
 * <li>right click opens the marker GUI,</li>
 * <li>shift + right click copies the marker's complete configuration into the tool (component
 * {@code architectstrials:spawn_marker_clipboard}),</li>
 * <li>left click replaces the configuration of a marker of the same type with the stored one; other marker types
 * are rejected.</li>
 * </ul>
 * The tool never breaks blocks, also not in creative mode.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public class SpawnMarkerToolItem extends Item {

    /**
     * Creates the item.
     *
     * @param properties the item properties
     */
    public SpawnMarkerToolItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof MobMarkerBlockEntity marker)) {
                player.sendOverlayMessage(Component.translatable("message.architectstrials.spawn_marker_tool.no_target").withStyle(ChatFormatting.RED));
            } else if (player.isSecondaryUseActive()) {
                context.getItemInHand().set(ModDataComponents.SPAWN_MARKER_CLIPBOARD.get(), SpawnMarkerClipboard.copy(marker));
                player.sendOverlayMessage(Component.translatable("message.architectstrials.spawn_marker_tool.copied",
                        marker.getBlockState().getBlock().getName()));
            } else if (player.canUseGameMasterBlocks()) {
                player.openMenu(marker);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
        return false;
    }

    /**
     * Keeps the marker's own right click (GUI) from running, so shift + right click copies.
     *
     * @param event the interaction event
     */
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() instanceof SpawnMarkerToolItem) {
            event.setUseBlock(TriState.FALSE);
        }
    }

    /**
     * Pastes the stored configuration on left click instead of breaking the block.
     *
     * @param event the interaction event
     */
    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SpawnMarkerToolItem)) {
            return;
        }
        event.setCanceled(true);
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || !(event.getEntity() instanceof ServerPlayer player)
                || !player.canUseGameMasterBlocks()) {
            return;
        }
        SpawnMarkerClipboard clipboard = stack.get(ModDataComponents.SPAWN_MARKER_CLIPBOARD.get());
        if (clipboard == null) {
            player.sendOverlayMessage(Component.translatable("message.architectstrials.spawn_marker_tool.empty_clipboard").withStyle(ChatFormatting.RED));
        } else if (!(player.level().getBlockEntity(event.getPos()) instanceof MobMarkerBlockEntity marker)) {
            player.sendOverlayMessage(Component.translatable("message.architectstrials.spawn_marker_tool.no_target").withStyle(ChatFormatting.RED));
        } else if (!clipboard.fits(marker)) {
            player.sendOverlayMessage(Component.translatable("message.architectstrials.spawn_marker_tool.wrong_type",
                    clipboard.markerBlock().getName()).withStyle(ChatFormatting.RED));
        } else {
            clipboard.pasteInto(marker);
            player.sendOverlayMessage(Component.translatable("message.architectstrials.spawn_marker_tool.pasted"));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.architectstrials.spawn_marker_tool").withStyle(ChatFormatting.GRAY));
        SpawnMarkerClipboard clipboard = stack.get(ModDataComponents.SPAWN_MARKER_CLIPBOARD.get());
        if (clipboard == null) {
            return;
        }
        builder.accept(Component.translatable("tooltip.architectstrials.spawn_marker_tool.clipboard", clipboard.markerBlock().getName())
                .withStyle(ChatFormatting.DARK_AQUA));
        for (ItemStack egg : clipboard.eggs()) {
            if (egg.getItem() instanceof SpawnEggItem && SpawnEggItem.getType(egg) != null) {
                builder.accept(Component.translatable("tooltip.architectstrials.spawn_marker_tool.mob", egg.getCount(),
                        SpawnEggItem.getType(egg).getDescription()).withStyle(ChatFormatting.DARK_AQUA));
            }
        }
    }
}
