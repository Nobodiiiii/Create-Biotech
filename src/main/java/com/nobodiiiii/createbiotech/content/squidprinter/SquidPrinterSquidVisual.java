package com.nobodiiiii.createbiotech.content.squidprinter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.client.model.SquidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Squid;

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

	public static void prepareIdleModel(SquidModel<Squid> squidModel) {
		prepareModel(squidModel, 0.0f);
	}

	public static void prepareOpenModel(SquidModel<Squid> squidModel) {
		prepareModel(squidModel, 1.0f);
	}

	public static void prepareModel(SquidModel<Squid> squidModel, float openness) {
		resetModelPose(squidModel);
		float easedOpenness = (float) Mth.smoothstep(Mth.clamp(openness, 0.0f, 1.0f));
		float tentacleAngle = Mth.lerp(easedOpenness, CLOSED_TENTACLE_ANGLE, OPEN_TENTACLE_ANGLE);
		squidModel.setupAnim(null, 0.0f, 0.0f, tentacleAngle, 0.0f, 0.0f);
	}

	public static void renderModel(SquidModel<Squid> squidModel, PoseStack ms, MultiBufferSource buffer, int packedLight) {
		VertexConsumer consumer = buffer.getBuffer(squidModel.renderType(SQUID_TEXTURE));
		squidModel.renderToBuffer(ms, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
	}

	private static void resetModelPose(SquidModel<Squid> squidModel) {
		ModelPart root = squidModel.root();
		root.getAllParts()
			.forEach(ModelPart::resetPose);
	}
}
