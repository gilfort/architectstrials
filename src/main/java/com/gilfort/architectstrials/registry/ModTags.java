package com.gilfort.architectstrials.registry;

import com.gilfort.architectstrials.ArchitectsTrials;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Tags of Architect's Trials. All of them ship empty and are filled by modpack datapacks.
 */
public final class ModTags {

    /** Items that cannot be used inside challenges (US-44): right and left clicks with them are cancelled. */
    public static final TagKey<Item> FORBIDDEN_ITEMS = TagKey.create(Registries.ITEM, ArchitectsTrials.id("forbidden_in_challenges"));

    /** Blocks that cannot be placed inside challenges (US-44), no matter by whom or how. */
    public static final TagKey<Block> FORBIDDEN_BLOCKS = TagKey.create(Registries.BLOCK, ArchitectsTrials.id("forbidden_in_challenges"));

    private ModTags() {
    }
}
