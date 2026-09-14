package com.nobodiiiii.createbiotech.foundation.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Draws contained creatures from explicit machine state, without entering entity-renderer hooks. */
public final class MachineCreatureRenderer {

	private static final float MODEL_FOOT_OFFSET = 1.501f;
	private static final float SLIME_RENDER_SCALE = 0.999f;
	private static final float SLIME_RENDER_Y_OFFSET = 0.001f;

	private static final ResourceLocation SLIME_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png");
	private static final ResourceLocation MAGMA_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/slime/magmacube.png");
	private static final ResourceLocation CREEPER_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper.png");
	private static final ResourceLocation CREEPER_POWER_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");

	private MachineCreatureRenderer() {}

	/** The caller supplies the model pose; the origin and yaw use the existing entity-space convention. */
	public static void renderAtFeet(MachineCreatureModel model, ResourceLocation texture,
		PoseStack poseStack, MultiBufferSource buffer, int packedLight, float yaw) {
		poseStack.pushPose();
		try {
			orient(poseStack, yaw);
			poseStack.translate(0, -MODEL_FOOT_OFFSET, 0);
			model.renderToBuffer(poseStack, buffer.getBuffer(model.renderType(texture)), packedLight,
				OverlayTexture.NO_OVERLAY, -1);
		} finally {
			poseStack.popPose();
		}
	}

	public static void renderSlime(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float yaw, int size, float squish) {
		poseStack.pushPose();
		try {
			orient(poseStack, yaw);
			poseStack.scale(SLIME_RENDER_SCALE, SLIME_RENDER_SCALE, SLIME_RENDER_SCALE);
			poseStack.translate(0, SLIME_RENDER_Y_OFFSET, 0);
			squash(poseStack, size, squish);
			poseStack.translate(0, -MODEL_FOOT_OFFSET, 0);
			Models.SLIME_INNER.resetPose();
			Models.SLIME_OUTER.resetPose();
			Models.SLIME_INNER.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(SLIME_TEXTURE)),
				packedLight, OverlayTexture.NO_OVERLAY, -1);
			Models.SLIME_OUTER.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityTranslucent(SLIME_TEXTURE)),
				packedLight, OverlayTexture.NO_OVERLAY, -1);
		} finally {
			poseStack.popPose();
		}
	}

	public static void renderMagmaCube(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float yaw, int size, float squish) {
		MachineCreatureModel model = Models.MAGMA;
		model.resetPose();
		float separation = Math.max(0, squish) * 1.7f;
		for (int slice = 0; slice < 8; slice++)
			model.root().getChild("cube" + slice).y = (slice - 4) * separation;
		poseStack.pushPose();
		try {
			orient(poseStack, yaw);
			squash(poseStack, size, squish);
			poseStack.translate(0, -MODEL_FOOT_OFFSET, 0);
			model.renderToBuffer(poseStack, buffer.getBuffer(model.renderType(MAGMA_TEXTURE)),
				packedLight, OverlayTexture.NO_OVERLAY, -1);
		} finally {
			poseStack.popPose();
		}
	}

	/** Width, height and depth of the fixed slime's resting outer cube. */
	public static float restingSlimeSide(int size, boolean magmaCube) {
		return Math.max(1, size) * 0.5f * (magmaCube ? 1 : SLIME_RENDER_SCALE);
	}

	/** Centers the resting geometry in a unit block for normal item display transforms. */
	public static void renderCenteredSlime(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float yRotation, int size, float scale, boolean magmaCube) {
		// Both fixed cubes span model Y = 16..24; include the renderer's foot offset and slime shrink.
		float centerY = (MODEL_FOOT_OFFSET - 20f / 16f) * Math.max(1, size);
		if (!magmaCube)
			centerY = (centerY - SLIME_RENDER_Y_OFFSET) * SLIME_RENDER_SCALE;
		poseStack.pushPose();
		try {
			poseStack.translate(.5f, .5f, .5f);
			poseStack.mulPose(Axis.YP.rotationDegrees(yRotation));
			poseStack.scale(scale, scale, scale);
			poseStack.translate(0, -centerY, 0);
			if (magmaCube)
				renderMagmaCube(poseStack, buffer, packedLight, 0, size, 0);
			else
				renderSlime(poseStack, buffer, packedLight, 0, size, 0);
		} finally {
			poseStack.popPose();
		}
	}

	/** Head yaw is relative to body yaw; swelling is the normalized 0..1 render value. */
	public static void renderCreeper(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float bodyYaw, float headYaw, float headPitch, float swelling, float animationTime, boolean charged) {
		float swell = Mth.clamp(swelling, 0, 1);
		float pulse = 1.0f + Mth.sin(swell * 100.0f) * swell * 0.01f;
		float bulge = swell * swell * swell * swell;
		float width = (1.0f + bulge * 0.4f) * pulse;
		float height = (1.0f + bulge * 0.1f) / pulse;
		float flash = (int) (swell * 10.0f) % 2 == 0 ? 0 : Mth.clamp(swell, 0.5f, 1.0f);
		int overlay = OverlayTexture.pack(OverlayTexture.u(flash), OverlayTexture.v(false));
		poseCreeper(Models.CREEPER, headYaw, headPitch);
		poseStack.pushPose();
		try {
			orient(poseStack, bodyYaw);
			poseStack.scale(width, height, width);
			poseStack.translate(0, -MODEL_FOOT_OFFSET, 0);
			Models.CREEPER.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(CREEPER_TEXTURE)),
				packedLight, overlay, -1);
			if (charged) {
				poseCreeper(Models.CREEPER_POWER, headYaw, headPitch);
				float offset = animationTime * 0.01f % 1.0f;
				Models.CREEPER_POWER.renderToBuffer(poseStack,
					buffer.getBuffer(RenderType.energySwirl(CREEPER_POWER_TEXTURE, offset, offset)),
					packedLight, OverlayTexture.NO_OVERLAY, 0xFF808080);
			}
		} finally {
			poseStack.popPose();
		}
	}

	private static void poseCreeper(MachineCreatureModel model, float headYaw, float headPitch) {
		model.resetPose();
		ModelPart head = model.root().getChild("head");
		head.yRot = Mth.wrapDegrees(headYaw) * Mth.DEG_TO_RAD;
		head.xRot = headPitch * Mth.DEG_TO_RAD;
	}

	private static void orient(PoseStack poseStack, float yaw) {
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));
		poseStack.scale(-1, -1, 1);
	}

	private static void squash(PoseStack poseStack, int size, float squish) {
		float scale = Math.max(1, size);
		float stretch = Math.max(0.01f, 1.0f + squish / (scale * 0.5f + 1.0f));
		poseStack.scale(scale / stretch, scale * stretch, scale / stretch);
	}

	/** Render-thread models; all state is reset before each draw and none retain a level or entity. */
	private static final class Models {
		private static final MachineCreatureModel SLIME_INNER = MachineCreatureModels.slimeInner();
		private static final MachineCreatureModel SLIME_OUTER = MachineCreatureModels.slimeOuter();
		private static final MachineCreatureModel MAGMA = MachineCreatureModels.magmaCube();
		private static final MachineCreatureModel CREEPER = MachineCreatureModels.creeper(0);
		private static final MachineCreatureModel CREEPER_POWER = MachineCreatureModels.creeper(2);
	}
}
