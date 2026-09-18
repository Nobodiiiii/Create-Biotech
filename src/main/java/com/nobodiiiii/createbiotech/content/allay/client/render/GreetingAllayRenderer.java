package com.nobodiiiii.createbiotech.content.allay.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.BlockEntityModelElement;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

final class GreetingAllayRenderer {

	static final double ALLAY_POSITION_Y = 1.0d - 2.0d / 16.0d;
	static final float LIVING_ENTITY_MODEL_Y_OFFSET = -1.501f;
	static final float ALLAY_SCALE = 1.0f;

	private final MachineCreatureModel allayModel = MachineCreatureModels.allay();

	void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Direction facing,
		float animationTime, float waveStrength) {
		render(poseStack, buffer, packedLight, facing, animationTime, waveStrength, true);
	}

	void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Direction facing,
		float animationTime, float waveStrength, boolean renderBody) {
		prepareGreetingPose(allayModel, animationTime, waveStrength);
		int allayLight = LightTexture.pack(15, LightTexture.sky(packedLight));

		BlockEntityModelElement.builder()
			.atLocal(0.5d, ALLAY_POSITION_Y, 0.5d)
			.rotateY(180.0f - facing.toYRot())
			.scale(-ALLAY_SCALE, -ALLAY_SCALE, ALLAY_SCALE)
			.packedLight(allayLight)
			.render(poseStack, buffer, (modelPose, modelBuffer, modelLight) -> {
				modelPose.translate(0.0f, LIVING_ENTITY_MODEL_Y_OFFSET, 0.0f);
				if (renderBody)
					allayModel.render(modelPose, modelBuffer, modelLight,
						OverlayTexture.NO_OVERLAY, -1);
				renderLogisticsHat(modelPose, modelBuffer, modelLight);
			});
	}

	private void renderLogisticsHat(PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		AllayLogisticsHatRenderer.render(allayModel, poseStack, buffer, packedLight);
	}

	static void prepareGreetingPose(MachineCreatureModel model, float animationTime, float waveStrength) {
		MachineCreatureModel.Part root = model.root();
		model.resetPose();

		MachineCreatureModel.Part head = root.getChild("head");
		MachineCreatureModel.Part body = root.getChild("body");
		MachineCreatureModel.Part rightArm = body.getChild("right_arm");
		MachineCreatureModel.Part leftArm = body.getChild("left_arm");
		MachineCreatureModel.Part rightWing = body.getChild("right_wing");
		MachineCreatureModel.Part leftWing = body.getChild("left_wing");

		root.zRot = degrees(18.0f);
		body.zRot = degrees(5.0f);
		head.zRot = degrees(-6.0f);
		head.yRot = degrees(-5.0f);

		float wave = Mth.sin(animationTime * 0.65f) * waveStrength;
		rightArm.xRot = degrees(-20.0f + wave * 8.0f);
		rightArm.yRot = degrees(-15.0f - wave * 6.0f);
		rightArm.zRot = degrees(130.0f + wave * 24.0f);
		head.zRot += degrees(-wave * 3.0f);
		leftArm.xRot = degrees(-5.0f);
		leftArm.zRot = degrees(-25.0f);

		rightWing.xRot = degrees(25.0f);
		rightWing.yRot = degrees(-45.0f);
		leftWing.xRot = degrees(25.0f);
		leftWing.yRot = degrees(45.0f);
	}

	private static float degrees(float degrees) {
		return (float) Math.toRadians(degrees);
	}
}
