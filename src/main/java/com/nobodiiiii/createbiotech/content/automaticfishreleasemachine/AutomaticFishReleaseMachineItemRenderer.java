package com.nobodiiiii.createbiotech.content.automaticfishreleasemachine;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class AutomaticFishReleaseMachineItemRenderer extends CustomRenderedItemModelRenderer {

	private final MachineCreatureModel fishModel = MachineCreatureModels.salmon();

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		renderer.render(model.getOriginalModel(), light);
		renderBladeClamps(renderer, poseStack, light);

		boolean guiLighting = transformType == ItemDisplayContext.GUI;
		if (guiLighting)
			Lighting.setupForEntityInInventory();
		try {
			renderFishRing(poseStack, buffer, light, overlay);
		} finally {
			if (guiLighting)
				Lighting.setupFor3DItems();
		}
	}

	private static void renderBladeClamps(PartialItemModelRenderer renderer, PoseStack poseStack, int light) {
		for (int bladeIndex = 0; bladeIndex < AutomaticFishReleaseMachineRenderer.BLADE_COUNT; bladeIndex++) {
			float clampRadius =
				((bladeIndex & 1) == 0
					? AutomaticFishReleaseMachineRenderer.CARDINAL_BLADE_CLAMP_RADIUS
					: AutomaticFishReleaseMachineRenderer.INTERMEDIATE_BLADE_CLAMP_RADIUS)
					+ AutomaticFishReleaseMachineRenderer.BLADE_CLAMP_OUTWARD_OFFSET;
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees(
				bladeIndex * AutomaticFishReleaseMachineRenderer.SLOT_ANGLE));
			poseStack.translate(0, 0, -clampRadius);
			renderer.renderSolid(AutomaticFishReleaseMachineRenderer.BLADE_CLAMP.get(), light);
			poseStack.popPose();
		}
	}

	private void renderFishRing(PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		MachineCreatureModel model = fishModel;
		model.resetPose();

		for (int fishIndex = 0; fishIndex < AutomaticFishReleaseMachineRenderer.BLADE_COUNT; fishIndex++) {
			float gapAngle = AutomaticFishReleaseMachineRenderer.FIRST_GAP_ANGLE
				+ fishIndex * AutomaticFishReleaseMachineRenderer.SLOT_ANGLE;
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees(gapAngle));
			poseStack.translate(0, 0, -AutomaticFishReleaseMachineRenderer.FISH_RING_RADIUS);
			poseStack.mulPose(Axis.YP.rotationDegrees(
				AutomaticFishReleaseMachineRenderer.FISH_IN_PLANE_ROTATION));
			poseStack.mulPose(Axis.YP.rotationDegrees(90));
			poseStack.mulPose(Axis.ZP.rotationDegrees(-90));
			poseStack.translate(0, 0, AutomaticFishReleaseMachineRenderer.FISH_TAIL_OFFSET);
			poseStack.scale(
				-AutomaticFishReleaseMachineRenderer.FISH_SCALE,
				-AutomaticFishReleaseMachineRenderer.FISH_SCALE,
				AutomaticFishReleaseMachineRenderer.FISH_SCALE);
			poseStack.translate(0, -1.501f, 0);
			model.renderToBuffer(poseStack,
				buffer.getBuffer(RenderType.entityCutoutNoCull(
					AutomaticFishReleaseMachineRenderer.SALMON_TEXTURE)),
				light, overlay, -1);
			poseStack.popPose();
		}
	}
}
