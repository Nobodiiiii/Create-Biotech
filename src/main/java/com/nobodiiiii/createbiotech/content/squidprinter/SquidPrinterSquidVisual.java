package com.nobodiiiii.createbiotech.content.squidprinter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.SquidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Squid;

public final class SquidPrinterSquidVisual {
	static final ResourceLocation SQUID_TEXTURE =
		new ResourceLocation("minecraft", "textures/entity/squid/squid.png");
	static final float RENDER_SCALE = 0.8f;
	static final float MODEL_HEIGHT_BLOCKS = 33.0f / 16.0f;

	private static final float RUN_CYCLE_SPEED = 0.045f;
	private static final float RUN_MIN_TENTACLE_ANGLE = 0.14f;
	private static final float RUN_MAX_TENTACLE_ANGLE = Mth.PI * 0.25f;
	private static final int TENTACLE_COUNT = 8;

	private SquidPrinterSquidVisual() {
	}

	static float runningCycleFromRenderTime(float renderTime) {
		float time = renderTime * RUN_CYCLE_SPEED;
		return time - Mth.floor(time);
	}

	static void prepareIdleModel(SquidModel<Squid> squidModel) {
		resetModelPose(squidModel);
		squidModel.setupAnim(null, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
	}

	static void prepareRunningModel(SquidModel<Squid> squidModel, float runningCycle) {
		resetModelPose(squidModel);
		float tentacleAngle = Mth.lerp(smoothPingPong(runningCycle), RUN_MIN_TENTACLE_ANGLE, RUN_MAX_TENTACLE_ANGLE);
		squidModel.setupAnim(null, 0.0f, 0.0f, tentacleAngle, 0.0f, 0.0f);
		applyRunningPose(squidModel.root(), runningCycle, tentacleAngle);
	}

	static void renderModel(SquidModel<Squid> squidModel, PoseStack ms, MultiBufferSource buffer, int packedLight) {
		VertexConsumer consumer = buffer.getBuffer(squidModel.renderType(SQUID_TEXTURE));
		squidModel.renderToBuffer(ms, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
	}

	private static void resetModelPose(SquidModel<Squid> squidModel) {
		ModelPart root = squidModel.root();
		root.getAllParts()
			.forEach(ModelPart::resetPose);
	}

	private static float smoothPingPong(float cycle) {
		float pingPong = cycle < 0.5f ? cycle * 2.0f : (1.0f - cycle) * 2.0f;
		pingPong = Mth.clamp(pingPong, 0.0f, 1.0f);
		return pingPong * pingPong * (3.0f - 2.0f * pingPong);
	}

	private static void applyRunningPose(ModelPart root, float cycle, float tentacleAngle) {
		float cycleRadians = cycle * Mth.TWO_PI;
		float openness = Mth.inverseLerp(tentacleAngle, RUN_MIN_TENTACLE_ANGLE, RUN_MAX_TENTACLE_ANGLE);

		ModelPart body = root.getChild("body");
		body.y += Mth.sin(cycleRadians) * 0.35f;
		body.xRot -= 0.08f * openness;
		body.zRot = Mth.sin(cycleRadians * 0.5f) * 0.04f;

		for (int i = 0; i < TENTACLE_COUNT; i++) {
			ModelPart tentacle = root.getChild("tentacle" + i);
			float phase = cycleRadians + i * (Mth.TWO_PI / TENTACLE_COUNT);
			float flutter = Mth.sin(phase) * 0.08f * (0.35f + 0.65f * openness);
			tentacle.xRot += flutter;
			tentacle.yRot += Mth.cos(phase) * 0.04f * openness;
		}
	}
}
