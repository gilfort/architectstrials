package com.gilfort.architectstrials.loot;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The loot tool (US-25). On a loot source (lootable container, Exit Marker, Trial Spawner Marker):
 * <ul>
 * <li>right click opens the {@link LootSetupMenu},</li>
 * <li>shift + right click copies the source's setup into the tool (component {@code architectstrials:loot_clipboard}),</li>
 * <li>left click pastes the stored setup; it stays stored until something else is copied.</li>
 * </ul>
 * The tool never breaks blocks, also not in creative mode.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public class LootToolItem extends Item {

    /**
     * Creates the item.
     *
     * @param properties the item properties
     */
    public LootToolItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            BlockEntity target = context.getLevel().getBlockEntity(context.getClickedPos());
            if (!LootSetups.supports(target)) {
                player.sendOverlayMessage(Component.translatable("message.architectstrials.loot_tool.no_target").withStyle(ChatFormatting.RED));
            } else if (player.isSecondaryUseActive()) {
                LootSetup setup = LootSetups.get(target, false);
                context.getItemInHand().set(ModDataComponents.LOOT_CLIPBOARD.get(), setup);
                player.sendOverlayMessage(Component.translatable("message.architectstrials.loot_tool.copied"));
            } else {
                open(player, target);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Opens the setup menu of a loot source.
     *
     * @param player the player
     * @param target the loot source
     */
    public static void open(ServerPlayer player, BlockEntity target) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new LootSetupMenu(id, inventory, target),
                Component.translatable("gui.architectstrials.loot_setup.title", target.getBlockState().getBlock().getName())));
        if (player.containerMenu instanceof LootSetupMenu menu) {
            menu.sync(true);
        }
    }

    @Override
    public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
        return false;
    }

    /**
     * Lets right clicks with the tool reach the tool instead of opening the container.
     *
     * @param event the right click event
     */
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() instanceof LootToolItem) {
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
        if (!(stack.getItem() instanceof LootToolItem)) {
            return;
        }
        event.setCanceled(true);
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LootSetup clipboard = stack.get(ModDataComponents.LOOT_CLIPBOARD.get());
        BlockEntity target = player.level().getBlockEntity(event.getPos());
        if (clipboard == null) {
            player.sendOverlayMessage(Component.translatable("message.architectstrials.loot_tool.empty_clipboard").withStyle(ChatFormatting.RED));
        } else if (!LootSetups.supports(target)) {
            player.sendOverlayMessage(Component.translatable("message.architectstrials.loot_tool.no_target").withStyle(ChatFormatting.RED));
        } else {
            LootSetups.set(target, false, clipboard);
            player.sendOverlayMessage(Component.translatable("message.architectstrials.loot_tool.pasted"));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.architectstrials.loot_tool").withStyle(ChatFormatting.GRAY));
        LootSetup clipboard = stack.get(ModDataComponents.LOOT_CLIPBOARD.get());
        if (clipboard != null) {
            long groups = clipboard.groups().stream().filter(group -> !group.isEmpty()).count();
            builder.accept(Component.translatable("tooltip.architectstrials.loot_tool.clipboard", groups,
                    clipboard.consolation().entries().size()).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
