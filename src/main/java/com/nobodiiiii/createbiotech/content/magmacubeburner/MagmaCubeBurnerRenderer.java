package com.nobodiiiii.createbiotech.content.magmacubeburner;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureRenderer;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.fluids.FluidStack;

public class MagmaCubeBurnerRenderer extends SmartBlockEntityRenderer<MagmaCubeBurnerBlockEntity> {

	private static final int MAGMA_CUBE_SIZE = 1;
	private static final float MAGMA_CUBE_BASE_Y = 2f / 16f;
	private static final float MAGMA_CUBE_JUMP_HEIGHT = 2f / 16f;
	private static final float JUMP_ANIMATION_PERIOD = MagmaCubeBurnerBlockEntity.BURNING_ANIMATION_PERIOD;
	private static final float JUMP_AIR_TIME = 16f;
	private static final float BURNING_LANDING_PHASE =
		MagmaCubeBurnerBlockEntity.BURNING_LANDING_TICK / JUMP_ANIMATION_PERIOD;
	private static final float FLUID_MIN_XZ = 3f / 16f + 1f / 512f;
	private static final float FLUID_MAX_XZ = 13f / 16f - 1f / 512f;
	private static final float FLUID_MIN_Y = 2f / 16f + 1f / 512f;

	public MagmaCubeBurnerRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(MagmaCubeBurnerBlockEntity blockEntity, float partialTicks, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay) {
		if (blockEntity.getLevel() == null)
			return;

		renderLava(blockEntity, poseStack, buffer, packedLight);

		Direction facing = blockEntity.getBlockState().getValue(MagmaCubeBurnerBlock.FACING);
		poseStack.pushPose();
		poseStack.translate(.5, getMagmaCubeY(blockEntity, partialTicks), .5);
		MachineCreatureRenderer.renderMagmaCube(poseStack, buffer, LightTexture.FULL_BRIGHT,
			facing.toYRot(), MAGMA_CUBE_SIZE, getSquish(blockEntity, partialTicks));
		poseStack.popPose();
	}

	private static float getMagmaCubeY(MagmaCubeBurnerBlockEntity blockEntity, float partialTicks) {
		HeatLevel heat = blockEntity.getBlockState().getValue(BlazeBurnerBlock.HEAT_LEVEL);
		if (MagmaCubeBurnerBlock.isBurning(heat))
			return MAGMA_CUBE_BASE_Y;

		float cycleTick = (blockEntity.getLevel().getGameTime() + partialTicks) % JUMP_ANIMATION_PERIOD;
		if (cycleTick >= JUMP_AIR_TIME)
			return MAGMA_CUBE_BASE_Y;

		float progress = cycleTick / JUMP_AIR_TIME;
		float ballisticHeight = 4f * progress * (1f - progress);
		return MAGMA_CUBE_BASE_Y + MAGMA_CUBE_JUMP_HEIGHT * ballisticHeight;
	}

	private static float getSquish(MagmaCubeBurnerBlockEntity blockEntity, float partialTicks) {
		HeatLevel heat = blockEntity.getBlockState().getValue(BlazeBurnerBlock.HEAT_LEVEL);
		if (!MagmaCubeBurnerBlock.isBurning(heat))
			return 0;

		float time = blockEntity.getLevel().getGameTime() + partialTicks;
		float phase = time % JUMP_ANIMATION_PERIOD / JUMP_ANIMATION_PERIOD;
		if (phase < .2f)
			return Mth.sin(phase / .2f * Mth.PI) * .5f;
		if (phase >= BURNING_LANDING_PHASE)
			return -Mth.sin((phase - BURNING_LANDING_PHASE) / (1 - BURNING_LANDING_PHASE) * Mth.PI) * .25f;
		return 0;
	}

	private static void renderLava(MagmaCubeBurnerBlockEntity blockEntity, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		FluidStack lava = blockEntity.getLavaFluidForRender();
		int heightPixels = blockEntity.getRenderedLavaHeightPixels();
		if (lava.isEmpty() || heightPixels == 0)
			return;

		float maxY = (2 + heightPixels) / 16f;
		NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(lava, FLUID_MIN_XZ, FLUID_MIN_Y, FLUID_MIN_XZ,
			FLUID_MAX_XZ, maxY, FLUID_MAX_XZ, buffer, poseStack, packedLight, false, true);
	}
}
