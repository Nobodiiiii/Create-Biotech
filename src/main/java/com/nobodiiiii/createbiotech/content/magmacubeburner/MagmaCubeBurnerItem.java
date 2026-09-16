package com.nobodiiiii.createbiotech.content.magmacubeburner;

import java.util.Map;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class MagmaCubeBurnerItem extends BlockItem {

	private final boolean capturedMagmaCube;

	public static MagmaCubeBurnerItem empty(Block block, Properties properties) {
		return new MagmaCubeBurnerItem(block, properties, false);
	}

	public static MagmaCubeBurnerItem withMagmaCube(Block block, Properties properties) {
		return new MagmaCubeBurnerItem(block, properties, true);
	}

	private MagmaCubeBurnerItem(Block block, Properties properties, boolean capturedMagmaCube) {
		super(block, properties);
		this.capturedMagmaCube = capturedMagmaCube;
	}

	@Override
	public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
		if (!capturedMagmaCube)
			return;
		super.registerBlocks(blockToItemMap, item);
	}

	@Override
	public String getDescriptionId() {
		return capturedMagmaCube ? super.getDescriptionId() : "item.create_biotech.empty_magma_cube_burner";
	}

	public boolean hasCapturedMagmaCube() {
		return capturedMagmaCube;
	}
}
