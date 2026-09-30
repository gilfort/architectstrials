package com.gilfort.architectstrials.block;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/**
 * Block item for editor marker blocks. Shows an explanatory tooltip from the lang key
 * {@code tooltip.architectstrials.<block name>}.
 */
public class MarkerBlockItem extends BlockItem {

    /**
     * Creates the item.
     *
     * @param block      the marker block
     * @param properties the item properties
     */
    public MarkerBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        String key = "tooltip." + this.getBlock().builtInRegistryHolder().key().identifier().toLanguageKey();
        builder.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
