package com.nobodiiiii.createbiotech.content.squidprinter;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.OversizedBlockItemRenderer;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class SquidPrinterItemRenderer extends OversizedBlockItemRenderer<SquidPrinterBlockEntity> {

	private static final float ITEM_Y_OFFSET = 17f / 64f;

	@Override
	protected float getRenderYOffset() {
		return ITEM_Y_OFFSET;
	}

	@Override
	protected SquidPrinterBlockEntity createBlockEntity() {
		return new SquidPrinterBlockEntity(CBBlockEntityTypes.SQUID_PRINTER.get(), BlockPos.ZERO,
			com.nobodiiiii.createbiotech.registry.CBBlocks.SQUID_PRINTER.get().defaultBlockState());
	}

	@Override
	protected void renderTransformed(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay,
		SquidPrinterBlockEntity blockEntity) {
		renderer.render(model.getOriginalModel(), light);

		boolean guiLighting = transformType == ItemDisplayContext.GUI;
		if (guiLighting)
			Lighting.setupForEntityInInventory();
		try {
			renderBlockEntity(blockEntity, ms, buffer, light, overlay);
		} finally {
			if (guiLighting)
				Lighting.setupFor3DItems();
		}
	}
}
