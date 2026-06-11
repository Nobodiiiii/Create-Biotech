package com.nobodiiiii.createbiotech.content.boneratchet;

import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class BoneRatchetBlockEntity extends SimpleKineticBlockEntity {

	private boolean refreshingStress;
	private boolean wasReverseRotation;

	public BoneRatchetBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.BONE_RATCHET.get(), pos, state);
	}

	@Override
	public float calculateStressApplied() {
		lastStressApplied = getDirectionalStressImpact();
		return lastStressApplied;
	}

	@Override
	public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
		super.updateFromNetwork(maxStress, currentStress, networkSize);
		refreshStressFromDirection(false);
	}

	@Override
	public void onSpeedChanged(float previousSpeed) {
		super.onSpeedChanged(previousSpeed);
		refreshStressFromDirection(true);
	}

	private void refreshStressFromDirection(boolean forceNetworkUpdate) {
		if (level == null || level.isClientSide || !hasNetwork() || refreshingStress)
			return;

		boolean reverseRotation = isReverseRotation();
		if (reverseRotation && !wasReverseRotation)
			CBAdvancements.awardNearby(level, getBlockPos(), 16, CBAdvancements.BONE_RATCHET);
		wasReverseRotation = reverseRotation;

		float directionalStress = getDirectionalStressImpact();
		if (!forceNetworkUpdate && Mth.equal(lastStressApplied, directionalStress))
			return;

		lastStressApplied = directionalStress;
		refreshingStress = true;
		try {
			getOrCreateNetwork().updateStressFor(this, directionalStress);
		} finally {
			refreshingStress = false;
		}
	}

	private float getDirectionalStressImpact() {
		return isReverseRotation() ? getJammingStressImpact() : 0;
	}

	private boolean isReverseRotation() {
		float speed = getTheoreticalSpeed();
		if (speed == 0)
			return false;
		AxisDirection facingDirection = getBlockState().getValue(DirectionalKineticBlock.FACING).getAxisDirection();
		boolean positiveIsForward = facingDirection == AxisDirection.POSITIVE;
		return positiveIsForward ? speed < 0 : speed > 0;
	}

	private static float getJammingStressImpact() {
		double creativeMotorCapacity = BlockStressValues.getCapacity(AllBlocks.CREATIVE_MOTOR.get());
		double impact = Math.max(getFallbackJamStressImpact(), creativeMotorCapacity + getCreativeMotorMargin());
		return impact > Float.MAX_VALUE ? Float.MAX_VALUE : (float) impact;
	}

	private static double getFallbackJamStressImpact() {
		return CBConfigs.SERVER.boneRatchet.fallbackJamStressImpact.get();
	}

	private static double getCreativeMotorMargin() {
		return CBConfigs.SERVER.boneRatchet.creativeMotorMargin.get();
	}
}
