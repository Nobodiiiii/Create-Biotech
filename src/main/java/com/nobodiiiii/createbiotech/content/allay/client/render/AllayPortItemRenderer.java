package com.nobodiiiii.createbiotech.content.allay.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class AllayPortItemRenderer extends CustomRenderedItemModelRenderer {

	private static final float ITEM_SCALE = 0.9f;
	private static final float GUI_Y_OFFSET = -1.0f / 16.0f;

	private final GreetingAllayRenderer greetingAllayRenderer = new GreetingAllayRenderer();

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		poseStack.pushPose();
		try {
			if (transformType == ItemDisplayContext.GUI) {
				poseStack.translate(0.0f, GUI_Y_OFFSET, 0.0f);
			}
			poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
			renderer.render(model.getOriginalModel(), light);
			renderGreetingAllay(transformType, poseStack, buffer, light);
		} finally {
			poseStack.popPose();
		}
	}

	private void renderGreetingAllay(ItemDisplayContext transformType, PoseStack poseStack,
		MultiBufferSource buffer, int light) {
		boolean guiLighting = transformType == ItemDisplayContext.GUI;
		if (guiLighting) {
			Lighting.setupForEntityInInventory();
		}
		try {
			poseStack.translate(-0.5f, -0.5f, -0.5f);
			greetingAllayRenderer.render(poseStack, buffer, light, Direction.NORTH, 0.0f, 0.0f);
		} finally {
			if (guiLighting) {
				Lighting.setupFor3DItems();
			}
		}
	}
}
