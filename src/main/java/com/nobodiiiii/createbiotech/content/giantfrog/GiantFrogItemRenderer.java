package com.nobodiiiii.createbiotech.content.giantfrog;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class GiantFrogItemRenderer extends BlockEntityWithoutLevelRenderer {
	private static final float ITEM_ENTITY_SCALE =
		Math.min(1.75f / GiantFrogVisual.REST_MAX_DIMENSION, 2.0f) * 1.25f;

	private final MachineCreatureModel frogModel = MachineCreatureModels.frog();

	public GiantFrogItemRenderer() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int overlay) {
		GiantFrogVisual.prepareModel(frogModel, false, 0);
		poseStack.pushPose();
		try {
			poseStack.translate(0, 1.0f / 16.0f, 0);
			poseStack.scale(ITEM_ENTITY_SCALE, ITEM_ENTITY_SCALE, ITEM_ENTITY_SCALE);
			MachineCreatureRenderer.renderAtFeet(frogModel, GiantFrogVisual.TEXTURE,
				poseStack, buffer, packedLight, 0);
		} finally {
			poseStack.popPose();
		}
	}
}
