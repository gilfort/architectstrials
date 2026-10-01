package com.gilfort.architectstrials.editor;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.slot.Slot;
import com.gilfort.architectstrials.travel.ChallengeTravel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Selection tool for copying areas of the world into the editor ({@code /at editor import}). Left click on a
 * block sets the first corner, right click the second; the selection is stored on the tool
 * ({@link Selection}) and drawn as an outline while the tool is held. The tool never breaks blocks.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public class SelectionToolItem extends Item {

    /**
     * Creates the item.
     *
     * @param properties the item properties
     */
    public SelectionToolItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            setCorner(player, context.getItemInHand(), context.getClickedPos(), false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
        return false;
    }

    /**
     * Sets the first corner on left click and keeps the block from being broken.
     *
     * @param event the left click event
     */
    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SelectionToolItem)) {
            return;
        }
        event.setCanceled(true);
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START && event.getEntity() instanceof ServerPlayer player) {
            setCorner(player, stack, event.getPos(), true);
        }
    }

    private static void setCorner(ServerPlayer player, ItemStack stack, BlockPos pos, boolean first) {
        if (ChallengeTravel.isModDimension(player.level().dimension())) {
            player.sendOverlayMessage(Component.translatable("message.architectstrials.selection.mod_dimension")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Selection selection = Selection.withCorner(stack.get(ModDataComponents.SELECTION.get()), player.level().dimension(), pos, first);
        stack.set(ModDataComponents.SELECTION.get(), selection);
        Component corner = Component.translatable(first ? "message.architectstrials.selection.first" : "message.architectstrials.selection.second",
                pos.getX(), pos.getY(), pos.getZ());
        player.sendOverlayMessage(selection.box().map(box -> Component.translatable("message.architectstrials.selection.size", corner,
                        box.getXSpan(), box.getYSpan(), box.getZSpan())
                .withStyle(tooLarge(box) ? ChatFormatting.RED : ChatFormatting.GREEN)).orElse(corner.copy().withStyle(ChatFormatting.YELLOW)));
    }

    /**
     * Checks whether a box exceeds the maximum structure footprint.
     *
     * @param box the box
     * @return {@code true} if it is wider or longer than {@link Slot#MAX_STRUCTURE_SIZE}
     */
    public static boolean tooLarge(BoundingBox box) {
        return box.getXSpan() > Slot.MAX_STRUCTURE_SIZE || box.getZSpan() > Slot.MAX_STRUCTURE_SIZE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.architectstrials.selection_tool").withStyle(ChatFormatting.GRAY));
        Selection selection = stack.get(ModDataComponents.SELECTION.get());
        if (selection != null) {
            selection.box().ifPresent(box -> builder.accept(Component.translatable("tooltip.architectstrials.selection_tool.size",
                    box.getXSpan(), box.getYSpan(), box.getZSpan(), selection.dimension().identifier().toString()).withStyle(ChatFormatting.DARK_AQUA)));
        }
    }

    /**
     * Finds the selection tool a player uses for importing: main hand, off hand, then the inventory.
     *
     * @param player the player
     * @return the tool stack, or {@link ItemStack#EMPTY}
     */
    public static ItemStack find(Player player) {
        for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof SelectionToolItem) {
                return stack;
            }
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof SelectionToolItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
