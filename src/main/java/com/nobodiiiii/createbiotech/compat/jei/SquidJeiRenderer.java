package com.nobodiiiii.createbiotech.compat.jei;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.SquidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Squid;

public final class SquidJeiRenderer {

	private static final ResourceLocation SQUID_TEXTURE =
		new ResourceLocation("minecraft", "textures/entity/squid/squid.png");
	private static final float RUN_CYCLE_SPEED = 0.045f;
	private static final float RUN_MIN_TENTACLE_ANGLE = 0.14f;
	private static final float RUN_MAX_TENTACLE_ANGLE = Mth.PI * 0.25f;
	private static final float GUI_BASE_SCALE = 8.5f;
	private static final float GUI_Y_OFFSET = -48.0f;
	private static final float SQUID_BLOCK_SCALE = 0.8f;
	private static final int TENTACLE_COUNT = 8;
	private static final int FULL_BRIGHT = 0x00F000F0;

	@Nullable
	private static SquidModel<Squid> squidModel;

	private SquidJeiRenderer() {
	}

	public static void render(GuiGraphics graphics, int centerX, int centerY, float scale) {
		renderWithTransform(graphics, centerX, centerY + GUI_Y_OFFSET, 100.0f, scale);
	}

	public static void renderInCurrentScene(GuiGraphics graphics, int blockScale) {
		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();

		poseStack.scale(blockScale, blockScale, blockScale);
		UIRenderHelper.flipForGuiRender(poseStack);

		poseStack.translate(0.5d, 1.05d, 0.5d);
		poseStack.scale(-SQUID_BLOCK_SCALE, -SQUID_BLOCK_SCALE, SQUID_BLOCK_SCALE);

		renderSquidModel(poseStack, graphics.bufferSource(), FULL_BRIGHT, AnimationTickHolder.getRenderTime());

		poseStack.popPose();
	}

	private static void renderWithTransform(GuiGraphics graphics, float x, float y, float z, float scale) {
		MultiBufferSource.BufferSource buffer = graphics.bufferSource();
		PoseStack poseStack = graphics.pose();

		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.mulPose(Axis.XP.rotationDegrees(-15.5f));
		poseStack.mulPose(Axis.YP.rotationDegrees(22.5f));
		float finalScale = GUI_BASE_SCALE * scale * 2;
		poseStack.scale(finalScale, finalScale, -finalScale);
		renderSquidModel(poseStack, buffer, FULL_BRIGHT, AnimationTickHolder.getRenderTime());
		poseStack.popPose();
	}

	private static void renderSquidModel(PoseStack poseStack, MultiBufferSource buffer, int light, float renderTime) {
		SquidModel<Squid> model = getModel();
		ModelPart root = model.root();
		root.getAllParts().forEach(ModelPart::resetPose);

		float runningCycle = renderTime * RUN_CYCLE_SPEED;
		runningCycle -= Mth.floor(runningCycle);
		float openness = smoothPingPong(runningCycle);
		float tentacleAngle = Mth.lerp(openness, RUN_MIN_TENTACLE_ANGLE, RUN_MAX_TENTACLE_ANGLE);

		model.setupAnim(null, 0.0f, 0.0f, tentacleAngle, 0.0f, 0.0f);
		applyRunningPose(root, runningCycle, tentacleAngle);

		VertexConsumer consumer = buffer.getBuffer(model.renderType(SQUID_TEXTURE));
		model.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
	}

	private static SquidModel<Squid> getModel() {
		if (squidModel == null) {
			squidModel = new SquidModel<>(Minecraft.getInstance().getEntityModels()
				.bakeLayer(ModelLayers.SQUID));
		}
		return squidModel;
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
