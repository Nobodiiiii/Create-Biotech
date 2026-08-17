package com.nobodiiiii.createbiotech.content.magmacubeburner;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.OversizedBlockItemRenderer;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class MagmaCubeBurnerItemRenderer extends OversizedBlockItemRenderer<MagmaCubeBurnerBlockEntity> {

	@Override
	protected MagmaCubeBurnerBlockEntity createBlockEntity() {
		return new MagmaCubeBurnerBlockEntity(BlockPos.ZERO, CBBlocks.MAGMA_CUBE_BURNER.get().defaultBlockState()
			.setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.SMOULDERING));
	}

	@Override
	protected float getRenderYOffset() {
		return 0;
	}

	@Override
	protected float getItemScale() {
		return 1;
	}

	@Override
	protected void renderTransformed(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay,
		MagmaCubeBurnerBlockEntity blockEntity) {
		renderOriginalModel(model, renderer, poseStack, light);

		boolean guiLighting = transformType == ItemDisplayContext.GUI;
		if (guiLighting)
			Lighting.setupForEntityInInventory();
		try {
			renderBlockEntity(blockEntity, poseStack, buffer, light, overlay);
		} finally {
			if (guiLighting)
				Lighting.setupFor3DItems();
		}
	}
}
