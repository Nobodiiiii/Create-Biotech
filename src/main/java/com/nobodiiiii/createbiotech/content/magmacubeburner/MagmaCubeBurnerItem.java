package com.nobodiiiii.createbiotech.content.magmacubeburner;

import java.util.Map;
import java.util.function.Consumer;

import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public class MagmaCubeBurnerItem extends BlockItem {

	private final boolean capturedMagmaCube;

	public static MagmaCubeBurnerItem empty(Block block, Properties properties) {
		return new MagmaCubeBurnerItem(block, properties, false);
	}

	public static MagmaCubeBurnerItem withMagmaCube(Block block, Properties properties) {
		return new CapturedMagmaCubeBurnerItem(block, properties);
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

	private static final class CapturedMagmaCubeBurnerItem extends MagmaCubeBurnerItem {

		private CapturedMagmaCubeBurnerItem(Block block, Properties properties) {
			super(block, properties, true);
		}

		@Override
		@OnlyIn(Dist.CLIENT)
		public void initializeClient(Consumer<IClientItemExtensions> consumer) {
			// Item invokes this method from its constructor, before MagmaCubeBurnerItem's
			// instance fields are assigned. The subtype itself identifies the captured variant.
			consumer.accept(SimpleCustomRenderer.create(this, new MagmaCubeBurnerItemRenderer()));
		}
	}
}
