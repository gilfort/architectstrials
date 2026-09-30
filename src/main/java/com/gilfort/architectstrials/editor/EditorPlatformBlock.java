package com.gilfort.architectstrials.editor;

import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The glass-looking start platform of the editor dimension. It only exists to give builders a first block to
 * build from and is ignored when a structure is saved, so it never ends up in a challenge.
 */
public class EditorPlatformBlock extends TransparentBlock {

    /**
     * Creates the block.
     *
     * @param properties the block properties
     */
    public EditorPlatformBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
