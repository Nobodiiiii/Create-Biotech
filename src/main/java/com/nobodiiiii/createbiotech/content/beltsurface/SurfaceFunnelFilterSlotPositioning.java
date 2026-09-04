package com.nobodiiiii.createbiotech.content.beltsurface;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.funnel.FunnelFilterSlotPositioning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Extends Create's funnel filter slot into the canonical frame used by Surface Funnels.
 * <p>
 * {@link FunnelFilterSlotPositioning} expects both {@code HORIZONTAL_FACING} and its selected side to be in world
 * space. Surface Funnels store the facing in surface-local space, while input handlers provide the clicked side in
 * world space. For a tilted surface, this adapter temporarily presents the clicked side to Create in local space,
 * then rotates the resulting slot position and orientation back into world space.
 */
public final class SurfaceFunnelFilterSlotPositioning extends FunnelFilterSlotPositioning {

	@Override
	public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
		Direction outwardNormal = BeltFunnelStateExtensions.tiltedOutwardNormal(state);
		if (outwardNormal == null)
			return super.getLocalOffset(level, pos, state);

		Direction worldSide = direction;
		direction = BeltSurface.localizeCanonical(worldSide, outwardNormal);
		try {
			return BeltSurface.transformPosition(super.getLocalOffset(level, pos, state), outwardNormal);
		} finally {
			direction = worldSide;
		}
	}

	@Override
	public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack poseStack) {
		Direction outwardNormal = BeltFunnelStateExtensions.tiltedOutwardNormal(state);
		if (outwardNormal == null) {
			super.rotate(level, pos, state, poseStack);
			return;
		}

		Direction worldSide = direction;
		direction = BeltSurface.localizeCanonical(worldSide, outwardNormal);
		try {
			poseStack.mulPose(BeltSurface.surfaceToWorld(outwardNormal));
			super.rotate(level, pos, state, poseStack);
		} finally {
			direction = worldSide;
		}
	}

	@Override
	protected boolean isSideActive(BlockState state, Direction worldDirection) {
		Direction outwardNormal = BeltFunnelStateExtensions.tiltedOutwardNormal(state);
		if (outwardNormal == null)
			return super.isSideActive(state, worldDirection);
		return super.isSideActive(state, BeltSurface.localizeCanonical(worldDirection, outwardNormal));
	}
}
