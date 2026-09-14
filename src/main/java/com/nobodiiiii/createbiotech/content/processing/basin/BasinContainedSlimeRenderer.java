package com.nobodiiiii.createbiotech.content.processing.basin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureRenderer;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Draws fixed slime models from the basin's own animation phase. */
public final class BasinContainedSlimeRenderer {
	private static final int MAX_VISIBLE_SLIMES = 4;
	private static final double BASE_Y = .2d;
	private static final double MAX_RANDOM_Y_OFFSET = 1d / 16d;

	private BasinContainedSlimeRenderer() {}

	public static void render(BasinBlockEntity basin, float partialTicks, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		Level level = basin.getLevel();
		int count = BasinEntityProcessing.getCapturedSmallSlimeItemCount(basin);
		if (level == null || count <= 0)
			return;

		int visible = Math.min(count, MAX_VISIBLE_SLIMES);
		BlockPos pos = basin.getBlockPos();
		float densityScale = 1f + .045f * Math.min(4, Math.max(0, count - MAX_VISIBLE_SLIMES));
		for (int index = 0; index < visible; index++) {
			float phase = BasinEntityProcessing.getContainedSlimeAnimationPhase(level, pos, index, partialTicks);
			float squish = Mth.sin(phase) * .22f;

			double angle = Math.PI * 2d * index / Math.max(1, visible)
				+ Math.floorMod(pos.getX() * 31 + pos.getZ() * 17, 360) * Mth.DEG_TO_RAD;
			double radius = visible == 1 ? 0 : .19d;
			double baseY = BASE_Y + getRandomBaseYOffset(pos, index);
			poseStack.pushPose();
			poseStack.translate(.5d + Math.cos(angle) * radius, baseY, .5d + Math.sin(angle) * radius);
			poseStack.scale(densityScale, densityScale, densityScale);
			MachineCreatureRenderer.renderSlime(poseStack, buffer, packedLight, 0, 1, squish);
			poseStack.popPose();
		}
	}

	private static double getRandomBaseYOffset(BlockPos basinPos, int visualIndex) {
		int hash = Long.hashCode(basinPos.asLong()) ^ visualIndex * 0x9E3779B9;
		hash ^= hash >>> 16;
		return (hash & 0xffff) / (double) 0xffff * MAX_RANDOM_Y_OFFSET;
	}
}
