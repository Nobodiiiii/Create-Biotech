package com.nobodiiiii.createbiotech.content.allay.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.allay.entity.courier.AllayCourierEntity;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;

import net.minecraft.client.model.AllayModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.animal.allay.Allay;

/** One fixed JSON attachment shared by the station, item and flying courier. */
final class AllayLogisticsHatRenderer extends RenderLayer<Allay, AllayModel> {

	private static final MachineCreatureModel HAT = MachineCreatureModels.allayHat();

	AllayLogisticsHatRenderer(RenderLayerParent<Allay, AllayModel> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Allay allay,
		float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
		float netHeadYaw, float headPitch) {
		if (!(allay instanceof AllayCourierEntity courier) || !courier.shouldRenderLogisticsHat())
			return;
		poseStack.pushPose();
		ModelPart root = getParentModel().root();
		root.translateAndRotate(poseStack);
		root.getChild("head").translateAndRotate(poseStack);
		renderHat(poseStack, buffer, packedLight);
		poseStack.popPose();
	}

	static void render(MachineCreatureModel model, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		model.applyTransformTo("head", poseStack);
		renderHat(poseStack, buffer, packedLight);
		poseStack.popPose();
	}

	private static void renderHat(PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		HAT.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, -1);
	}
}
