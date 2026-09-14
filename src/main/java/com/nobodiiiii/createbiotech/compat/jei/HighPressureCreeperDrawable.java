package com.nobodiiiii.createbiotech.compat.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureRenderer;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public class HighPressureCreeperDrawable extends AnimatedKineticsWithEntities {
	private static final int PRESS_CYCLE = 30;
	private static final int PRESS_SCALE = 20;
	private static final double CREEPER_ATTACHMENT_Y = 2d;
	private static final float PRESS_EFFECT_START_OFFSET = 0.4f;
	private static final float SWELL_DIVISOR = 28f;

	private final int width;
	private final int height;
	private final float horizontalScale;
	private final float verticalScale;
	private final int swell;

	public HighPressureCreeperDrawable(int width, int height, float horizontalScale, float verticalScale, int swell) {
		this.width = width;
		this.height = height;
		this.horizontalScale = horizontalScale;
		this.verticalScale = verticalScale;
		this.swell = swell;
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
		float headOffset = getAnimatedHeadOffset();
		scene(guiGraphics, xOffset, yOffset, () -> {
			blockElement(shaft(Direction.Axis.Z))
				.rotateBlock(0, 0, getCurrentAngle())
				.scale(PRESS_SCALE)
				.render(guiGraphics);

			blockElement(AllBlocks.MECHANICAL_PRESS.getDefaultState())
				.scale(PRESS_SCALE)
				.render(guiGraphics);

			renderCreeper(guiGraphics, headOffset);

			blockElement(AllPartialModels.MECHANICAL_PRESS_HEAD)
				.atLocal(0, -headOffset, 0)
				.scale(PRESS_SCALE)
				.render(guiGraphics);
		});
	}

	private void renderCreeper(GuiGraphics guiGraphics, float headOffset) {
		float compression = getCompressionFromHeadOffset(headOffset);
		float renderTime = AnimationTickHolder.getRenderTime();
		float pulse = 0.5f + 0.5f * Mth.sin(renderTime * 0.9f);
		int renderSwell =
			Mth.floor(Mth.clamp(compression * Mth.lerp(pulse, 0.55f, 1f), 0f, 1f) * swell);

		float appliedHorizontalScale = Mth.lerp(compression, 1f, horizontalScale);
		float appliedVerticalScale = Mth.lerp(compression, 1f, verticalScale);
		guiGraphics.flush();
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableDepthTest();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		DEFAULT_LIGHTING.applyLighting();
		PoseStack poseStack = guiGraphics.pose();
		poseStack.pushPose();
		try {
			poseStack.scale(PRESS_SCALE, PRESS_SCALE, PRESS_SCALE);
			poseStack.translate(0.5d, CREEPER_ATTACHMENT_Y, 0.5d);
			UIRenderHelper.flipForGuiRender(poseStack);
			poseStack.scale(appliedHorizontalScale, appliedVerticalScale, appliedHorizontalScale);
			MachineCreatureRenderer.renderCreeper(poseStack, guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT,
				0, 0, 0, renderSwell / SWELL_DIVISOR,
				Mth.floor(renderTime) + AnimationTickHolder.getPartialTicks(), true);
			guiGraphics.flush();
		} finally {
			poseStack.popPose();
			Lighting.setupFor3DItems();
		}
	}

	private float getAnimatedHeadOffset() {
		float cycle = (AnimationTickHolder.getRenderTime() - offset * 8) % PRESS_CYCLE;
		if (cycle < 10) {
			float progress = cycle / 10;
			return -(progress * progress * progress);
		}
		if (cycle < 15)
			return -1;
		if (cycle < 20)
			return -1 + (1 - ((20 - cycle) / 5));
		return 0;
	}

	private static float getCompressionFromHeadOffset(float headOffset) {
		float pressOffset = Mth.clamp(-headOffset, 0f, 1f);
		return Mth.clamp((pressOffset - PRESS_EFFECT_START_OFFSET) / (1f - PRESS_EFFECT_START_OFFSET), 0f, 1f);
	}
}
