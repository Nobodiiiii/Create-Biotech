package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.OversizedBlockItemRenderer;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ShulkerTeleporterItemRenderer extends OversizedBlockItemRenderer<ShulkerTeleporterBlockEntity> {

	private static final float ITEM_Y_OFFSET = 0.25f;

	@Override
	protected float getRenderYOffset() {
		return ITEM_Y_OFFSET;
	}

	@Override
	protected ShulkerTeleporterBlockEntity createBlockEntity() {
		return new ShulkerTeleporterBlockEntity(BlockPos.ZERO, ShulkerTeleporterBlock.defaultItemRenderState());
	}

	@Override
	protected void renderTransformed(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay,
		ShulkerTeleporterBlockEntity blockEntity) {
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
