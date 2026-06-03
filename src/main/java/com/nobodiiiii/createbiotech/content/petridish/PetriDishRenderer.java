package com.nobodiiiii.createbiotech.content.petridish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.foundation.render.EntityRenderHelper;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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
	private static final float EMERGENCE_SWITCH_PROGRESS = 0.5f;
	private static final float EMERGENCE_CREATURE_LAND_Y = 1.0f;
	private static final float EMERGENCE_SWITCH_Y = 1.22f;
	private static final float EMERGENCE_PREVIEW_SCALE = 1.0f;
	private static final float EMERGENCE_SLIME_POP_SCALE = 0.84f;
	private static final float BIONIC_ITEM_Y = 5.2f / 16.0f;
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
		float ascentProgress = Mth.clamp(progress / EMERGENCE_SWITCH_PROGRESS, 0.0f, 1.0f);
		float descentProgress = Mth.clamp((progress - EMERGENCE_SWITCH_PROGRESS) / (1.0f - EMERGENCE_SWITCH_PROGRESS),
			0.0f, 1.0f);

		if (progress <= EMERGENCE_SWITCH_PROGRESS) {
			float ascentCurve = easeOutCubic(ascentProgress);
			float slimeScale =
				Mth.lerp(easeOutBack(ascentProgress, EMERGENCE_OVERSHOOT), STAGE_SCALES[4], EMERGENCE_SLIME_POP_SCALE);
			float stretch = Mth.lerp(ascentCurve, 1.0f, 1.45f);
			float squash = Mth.lerp(ascentCurve, 1.0f, 0.82f);
			float slimeBaseY = Mth.lerp(ascentCurve, SLIME_BASE_Y, EMERGENCE_SWITCH_Y);
			renderSlimeCube(be, poseStack, buffer, packedLight, packedOverlay, slimeScale * squash, slimeBaseY,
				slimeScale * stretch, slimeScale * squash, false, false);
			return;
		}

		LivingEntity previewEntity = be.getClientPreviewEntity();
		if (previewEntity == null)
			return;

		float previewScale = Mth.lerp(easeOutBack(descentProgress, 0.9f), 0.36f, EMERGENCE_PREVIEW_SCALE);
		float previewYOffset = Mth.lerp(easeInQuad(descentProgress), EMERGENCE_SWITCH_Y, EMERGENCE_CREATURE_LAND_Y);
		float yaw = be.getSpawnYaw();

		poseStack.pushPose();
		poseStack.translate(0.5f, previewYOffset, 0.5f);
		poseStack.scale(previewScale, previewScale, previewScale);
		EntityRenderHelper.render(EntityRenderHelper.settings(previewEntity)
			.packedLight(packedLight)
			.partialTicks(partialTicks)
			.ticks(Mth.floor(AnimationTickHolder.getRenderTime(be.getLevel())))
			.renderShadow(false)
			.yaw(yaw)
			.bodyYaw(yaw)
			.headYaw(yaw)
			.dispatcherYaw(0.0f)
			.flushBuffers(false), poseStack, buffer);
		poseStack.popPose();
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

	private static float getBaseScale(PetriDishBlockEntity be, float partialTicks) {
		if (!be.isGrowthAnimating())
			return STAGE_SCALES[be.getRenderedSlimeStage()];

		float progress = be.getGrowthAnimationProgress(partialTicks);
		float from = STAGE_SCALES[be.getGrowthAnimationFromStage()];
		float to = STAGE_SCALES[be.getGrowthAnimationToStage()];
		return Mth.lerp(easeOutBack(progress, GROWTH_OVERSHOOT), from, to);
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

	private static float easeInQuad(float progress) {
		return progress * progress;
	}
}
