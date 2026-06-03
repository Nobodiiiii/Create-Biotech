package com.nobodiiiii.createbiotech.content.petridish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;

public class PetriDishRenderer extends SmartBlockEntityRenderer<PetriDishBlockEntity> {

	private static final BlockState SLIME_BLOCK = Blocks.SLIME_BLOCK.defaultBlockState();
	private static final float SLIME_BASE_Y = 2.25f / 16.0f;
	private static final float SLIME_MAX_TOP_Y = 0.96f;
	private static final float[] STAGE_SCALES = { 0.0f, 0.28f, 0.42f, 0.56f, 0.70f };
	private static final float IDLE_SPEED = 0.18f;
	private static final float IDLE_VERTICAL_STRETCH = 0.08f;
	private static final float IDLE_VERTICAL_SQUASH = 0.05f;
	private static final float IDLE_HORIZONTAL_STRETCH = 0.04f;
	private static final float IDLE_HORIZONTAL_SQUASH = 0.06f;
	private static final float GROWTH_OVERSHOOT = 1.15f;
	private static final float EMERGENCE_OVERSHOOT = 1.3f;
	private static final float EMERGENCE_OVERSHOOT_END = 0.86f;
	private static final float EMERGENCE_OVERSHOOT_FACTOR = 1.2f;
	private static final float EMERGENCE_END_FACTOR = 1.0f;
	private static final float BIONIC_ITEM_Y = 3.2f / 16.0f;
	private static final float BIONIC_ITEM_SCALE = 0.5f;

	private final BlockRenderDispatcher blockRenderer;

	public PetriDishRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
		blockRenderer = context.getBlockRenderDispatcher();
	}

	@Override
	protected void renderSafe(PetriDishBlockEntity be, float partialTicks, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay) {
		super.renderSafe(be, partialTicks, poseStack, buffer, packedLight, packedOverlay);

		int growthStage = be.getRenderedSlimeStage();
		if (growthStage <= 0) {
			renderIdleBionicMechanism(be, poseStack, buffer, packedLight, packedOverlay);
			return;
		}

		if (be.isEmergenceAnimating()) {
			renderEmergence(be, partialTicks, poseStack, buffer, packedLight, packedOverlay);
			return;
		}

		float baseScale = getBaseScale(be, partialTicks);
		renderSlimeCube(be, poseStack, buffer, packedLight, packedOverlay, baseScale, SLIME_BASE_Y);
	}

	private void renderIdleBionicMechanism(PetriDishBlockEntity be, PoseStack poseStack, MultiBufferSource buffer,
		int packedLight, int packedOverlay) {
		ItemStack bionicMechanism = be.getBionicMechanism();
		if (bionicMechanism.isEmpty() || be.isEmergenceAnimating())
			return;
		if (be.getLevel() == null)
			return;

		ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

		poseStack.pushPose();
		poseStack.translate(0.5f, BIONIC_ITEM_Y, 0.5f);
		poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
		poseStack.scale(BIONIC_ITEM_SCALE, BIONIC_ITEM_SCALE, BIONIC_ITEM_SCALE);
		itemRenderer.renderStatic(bionicMechanism, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack,
			buffer, be.getLevel(), 0);
		poseStack.popPose();
	}

	private void renderEmergence(PetriDishBlockEntity be, float partialTicks, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay) {
		float progress = be.getEmergenceAnimationProgress(partialTicks);
		LivingEntity previewEntity = be.getClientPreviewEntity();

		if (previewEntity == null) {
			float fallbackScale = getEmergenceScale(progress, STAGE_SCALES[4],
				STAGE_SCALES[4] * EMERGENCE_OVERSHOOT_FACTOR, STAGE_SCALES[4] * EMERGENCE_END_FACTOR);
			renderSlimeCube(be, poseStack, buffer, packedLight, packedOverlay, fallbackScale, SLIME_BASE_Y,
				fallbackScale, fallbackScale, false, false);
			return;
		}

		float adultWidth = getAdultWidth(previewEntity);
		float adultHeight = getAdultHeight(previewEntity);

		float targetWidth = Math.max(STAGE_SCALES[4], adultWidth * EMERGENCE_END_FACTOR);
		float targetHeight = Math.max(STAGE_SCALES[4], adultHeight * EMERGENCE_END_FACTOR);
		float overshootWidth = Math.max(targetWidth, adultWidth * EMERGENCE_OVERSHOOT_FACTOR);
		float overshootHeight = Math.max(targetHeight, adultHeight * EMERGENCE_OVERSHOOT_FACTOR);
		float slimeScaleX = getEmergenceScale(progress, STAGE_SCALES[4], overshootWidth, targetWidth);
		float slimeScaleY = getEmergenceScale(progress, STAGE_SCALES[4], overshootHeight, targetHeight);
		float morphCurve = progress <= EMERGENCE_OVERSHOOT_END
			? easeOutCubic(Mth.clamp(progress / EMERGENCE_OVERSHOOT_END, 0.0f, 1.0f))
			: 1.0f - 0.08f * easeOutCubic(Mth.clamp((progress - EMERGENCE_OVERSHOOT_END)
				/ (1.0f - EMERGENCE_OVERSHOOT_END), 0.0f, 1.0f));
		float horizontalSquash = Mth.lerp(morphCurve, 1.0f, 0.92f);
		float verticalStretch = Mth.lerp(morphCurve, 1.0f, 1.08f);
		renderSlimeCube(be, poseStack, buffer, packedLight, packedOverlay, slimeScaleX * horizontalSquash,
			SLIME_BASE_Y, slimeScaleY * verticalStretch, slimeScaleX * horizontalSquash, false, false);
	}

	private void renderSlimeCube(PetriDishBlockEntity be, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		int packedOverlay, float uniformScale, float baseY) {
		renderSlimeCube(be, poseStack, buffer, packedLight, packedOverlay, uniformScale, baseY, uniformScale,
			uniformScale, true, true);
	}

	private void renderSlimeCube(PetriDishBlockEntity be, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		int packedOverlay, float scaleX, float baseY, float scaleY, float scaleZ, boolean clampToDishTop,
		boolean animateIdle) {
		float verticalFactor = 1.0f;
		float horizontalFactor = 1.0f;
		if (animateIdle) {
			float idleWave = Mth.sin(AnimationTickHolder.getRenderTime(be.getLevel()) * IDLE_SPEED
				+ (be.getBlockPos()
					.asLong() & 31L) * 0.21f);
			verticalFactor = idleWave >= 0.0f
				? 1.0f + idleWave * IDLE_VERTICAL_STRETCH
				: 1.0f + idleWave * IDLE_VERTICAL_SQUASH;
			horizontalFactor = idleWave >= 0.0f
				? 1.0f - idleWave * IDLE_HORIZONTAL_STRETCH
				: 1.0f - idleWave * IDLE_HORIZONTAL_SQUASH;
		}

		float actualScaleX = scaleX * horizontalFactor;
		float actualScaleY = scaleY * verticalFactor;
		if (clampToDishTop)
			actualScaleY = Math.min(actualScaleY, SLIME_MAX_TOP_Y - baseY);
		float actualScaleZ = scaleZ * horizontalFactor;
		float minX = 0.5f - actualScaleX / 2.0f;
		float minZ = 0.5f - actualScaleZ / 2.0f;

		poseStack.pushPose();
		poseStack.translate(minX, baseY, minZ);
		poseStack.scale(actualScaleX, actualScaleY, actualScaleZ);
		blockRenderer.renderSingleBlock(SLIME_BLOCK, poseStack, buffer, packedLight, packedOverlay);
		poseStack.popPose();
	}

	private static float getAdultWidth(LivingEntity previewEntity) {
		EntityDimensions dimensions = previewEntity.getDimensions(previewEntity.getPose());
		return Math.max(previewEntity.getBbWidth(), dimensions.width);
	}

	private static float getAdultHeight(LivingEntity previewEntity) {
		EntityDimensions dimensions = previewEntity.getDimensions(previewEntity.getPose());
		return Math.max(previewEntity.getBbHeight(), dimensions.height);
	}

	private static float getBaseScale(PetriDishBlockEntity be, float partialTicks) {
		if (!be.isGrowthAnimating())
			return STAGE_SCALES[be.getRenderedSlimeStage()];

		float progress = be.getGrowthAnimationProgress(partialTicks);
		float from = STAGE_SCALES[be.getGrowthAnimationFromStage()];
		float to = STAGE_SCALES[be.getGrowthAnimationToStage()];
		return Mth.lerp(easeOutBack(progress, GROWTH_OVERSHOOT), from, to);
	}

	private static float getEmergenceScale(float progress, float startScale, float endScale) {
		return getEmergenceScale(progress, startScale, endScale, endScale);
	}

	private static float getEmergenceScale(float progress, float startScale, float overshootScale, float endScale) {
		if (progress <= EMERGENCE_OVERSHOOT_END) {
			float overshootProgress = Mth.clamp(progress / EMERGENCE_OVERSHOOT_END, 0.0f, 1.0f);
			return Mth.lerp(easeOutBack(overshootProgress, EMERGENCE_OVERSHOOT), startScale, overshootScale);
		}

		float settleProgress = Mth.clamp((progress - EMERGENCE_OVERSHOOT_END) / (1.0f - EMERGENCE_OVERSHOOT_END),
			0.0f, 1.0f);
		return Mth.lerp(easeOutCubic(settleProgress), overshootScale, endScale);
	}

	private static float easeOutBack(float progress, float overshoot) {
		float shifted = progress - 1.0f;
		float c3 = overshoot + 1.0f;
		return 1.0f + c3 * shifted * shifted * shifted + overshoot * shifted * shifted;
	}

	private static float easeOutCubic(float progress) {
		float inverse = 1.0f - progress;
		return 1.0f - inverse * inverse * inverse;
	}
}
