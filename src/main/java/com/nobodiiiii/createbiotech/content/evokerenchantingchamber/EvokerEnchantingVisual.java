package com.nobodiiiii.createbiotech.content.evokerenchantingchamber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class EvokerEnchantingVisual {

	public static final ResourceLocation EVOKER_TEXTURE =
		ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/illager/evoker.png");

	private static final float HEAD_X_ROT = 0.08726646f;
	private static final float RIGHT_LEG_X_ROT = -1.4137167f;
	private static final float RIGHT_LEG_Y_ROT = 0.31415927f;
	private static final float RIGHT_LEG_Z_ROT = 0.07853982f;
	private static final float LEFT_LEG_X_ROT = -1.4137167f;
	private static final float LEFT_LEG_Y_ROT = -0.31415927f;
	private static final float LEFT_LEG_Z_ROT = -0.07853982f;
	private static final float ARM_CAST_X_ROT = -0.95f;
	private static final float ARM_CAST_Y_ROT = 0.18f;
	private static final float ARM_RAISED_Z_ROT = 2.3561945f;

	private EvokerEnchantingVisual() {
	}

	public static void prepareModel(MachineCreatureModel evokerModel, boolean casting) {
		ModelPart root = evokerModel.root();
		evokerModel.resetPose();
		root.getChild("arms").visible = !casting;
		root.getChild("right_arm").visible = casting;
		root.getChild("left_arm").visible = casting;

		ModelPart head = root.getChild("head");
		ModelPart rightLeg = root.getChild("right_leg");
		ModelPart leftLeg = root.getChild("left_leg");

		head.xRot = HEAD_X_ROT;
		rightLeg.xRot = RIGHT_LEG_X_ROT;
		rightLeg.yRot = RIGHT_LEG_Y_ROT;
		rightLeg.zRot = RIGHT_LEG_Z_ROT;
		leftLeg.xRot = LEFT_LEG_X_ROT;
		leftLeg.yRot = LEFT_LEG_Y_ROT;
		leftLeg.zRot = LEFT_LEG_Z_ROT;

		applyArmPose(root, casting);
	}

	public static void renderModel(MachineCreatureModel evokerModel, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		VertexConsumer consumer = buffer.getBuffer(evokerModel.renderType(EVOKER_TEXTURE));
		evokerModel.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
	}

	private static void applyArmPose(ModelPart root, boolean casting) {
		ModelPart rightArm = root.getChild("right_arm");
		ModelPart leftArm = root.getChild("left_arm");

		if (!casting)
			return;

		rightArm.xRot = ARM_CAST_X_ROT;
		leftArm.xRot = ARM_CAST_X_ROT;
		rightArm.yRot = ARM_CAST_Y_ROT;
		leftArm.yRot = -ARM_CAST_Y_ROT;
		rightArm.zRot = ARM_RAISED_Z_ROT;
		leftArm.zRot = -ARM_RAISED_Z_ROT;
	}
}
