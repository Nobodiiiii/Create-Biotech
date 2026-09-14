package com.nobodiiiii.createbiotech.content.squidprinter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class SquidPrinterSquidVisual {
	public static final ResourceLocation SQUID_TEXTURE =
		CreateBiotech.asResource("textures/entity/squid_printer/squid.png");
	public static final float RENDER_SCALE = 1.0f;
	public static final float MODEL_HEIGHT_BLOCKS = 33.0f / 16.0f;
	public static final double HEAD_TOP_Y = 1.0d;
	public static final float POSE_SPEED = 0.04f;

	private static final float CLOSED_TENTACLE_ANGLE = 0.14f;
	private static final float OPEN_TENTACLE_ANGLE = Mth.PI * 0.25f;

	private SquidPrinterSquidVisual() {
	}

	public static void prepareIdleModel(MachineCreatureModel squidModel) {
		prepareModel(squidModel, 0.0f);
	}

	public static void prepareOpenModel(MachineCreatureModel squidModel) {
		prepareModel(squidModel, 1.0f);
	}

	public static void prepareModel(MachineCreatureModel squidModel, float openness) {
		squidModel.resetPose();
		float easedOpenness = (float) Mth.smoothstep(Mth.clamp(openness, 0.0f, 1.0f));
		float tentacleAngle = Mth.lerp(easedOpenness, CLOSED_TENTACLE_ANGLE, OPEN_TENTACLE_ANGLE);
		for (int index = 0; index < 8; index++)
			squidModel.root().getChild("tentacle" + index).xRot = tentacleAngle;
	}

	public static void renderModel(MachineCreatureModel squidModel, PoseStack ms, MultiBufferSource buffer, int packedLight) {
		VertexConsumer consumer = buffer.getBuffer(squidModel.renderType(SQUID_TEXTURE));
		squidModel.renderToBuffer(ms, consumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
	}
}
