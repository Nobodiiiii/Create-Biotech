package com.nobodiiiii.createbiotech.content.buttercat.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/**
 * Block item for the cute cat on a shaft and the butter cat engine. Its model carries only
 * the item transforms; the cat itself is drawn by {@link ButterCatItemRenderer} so the stack
 * shows the breed it was picked up with.
 */
public class ButterCatBlockItem extends BlockItem {

	public ButterCatBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

}
