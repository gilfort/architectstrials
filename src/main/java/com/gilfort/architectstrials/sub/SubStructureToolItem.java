package com.gilfort.architectstrials.sub;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The sub structure tool (US-32), handled like the loot tool. On a Sub Structure Marker:
 * <ul>
 * <li>right click opens the marker GUI,</li>
 * <li>shift + right click copies the marker's setup into the tool (component
 * {@code architectstrials:sub_structure_clipboard}),</li>
 * <li>left click pastes the stored setup as an independent copy; offsets stay as they are.</li>
 * </ul>
 * The tool never breaks blocks, also not in creative mode.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public class SubStructureToolItem extends Item {

    /**
     * Creates the item.
     *
     * @param properties the item properties
     */
    public SubStructureToolItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof SubStructureMarkerBlockEntity marker)) {
                player.sendOverlayMessage(Component.translatable("message.architectstrials.sub_structure_tool.no_target").withStyle(ChatFormatting.RED));
            } else if (player.isSecondaryUseActive()) {
                context.getItemInHand().set(ModDataComponents.SUB_STRUCTURE_CLIPBOARD.get(), marker.setup());
                player.sendOverlayMessage(Component.translatable("message.architectstrials.sub_structure_tool.copied"));
            } else if (player.canUseGameMasterBlocks()) {
                SubStructureMarkerBlock.open(player, context.getClickedPos());
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
        return false;
    }

    /**
     * Lets right clicks with the tool reach the tool instead of the block.
     *
     * @param event the right click event
     */
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() instanceof SubStructureToolItem) {
            event.setUseBlock(TriState.FALSE);
        }
    }

    /**
     * Pastes the stored setup on left click and keeps the block from being broken.
     *
     * @param event the left click event
     */
    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SubStructureToolItem)) {
            return;
        }
        event.setCanceled(true);
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SubStructureSetup clipboard = stack.get(ModDataComponents.SUB_STRUCTURE_CLIPBOARD.get());
        if (clipboard == null) {
            player.sendOverlayMessage(Component.translatable("message.architectstrials.sub_structure_tool.empty_clipboard").withStyle(ChatFormatting.RED));
        } else if (!(player.level().getBlockEntity(event.getPos()) instanceof SubStructureMarkerBlockEntity marker)) {
            player.sendOverlayMessage(Component.translatable("message.architectstrials.sub_structure_tool.no_target").withStyle(ChatFormatting.RED));
        } else {
            marker.setSetup(clipboard);
            player.sendOverlayMessage(Component.translatable("message.architectstrials.sub_structure_tool.pasted"));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.architectstrials.sub_structure_tool").withStyle(ChatFormatting.GRAY));
        SubStructureSetup clipboard = stack.get(ModDataComponents.SUB_STRUCTURE_CLIPBOARD.get());
        if (clipboard != null) {
            builder.accept(Component.translatable("tooltip.architectstrials.sub_structure_tool.clipboard", clipboard.entries().size(),
                    clipboard.fallback().map(Object::toString).orElse("-")).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
