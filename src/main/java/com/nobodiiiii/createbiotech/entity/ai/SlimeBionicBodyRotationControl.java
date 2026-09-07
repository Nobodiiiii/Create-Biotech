package com.nobodiiiii.createbiotech.entity.ai;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;

public class SlimeBionicBodyRotationControl extends BodyRotationControl {
	private static final float HEAD_STABLE_ANGLE = 15.0f;
	private static final float BODY_ROTATE_LIMIT = 75.0f;
	private static final int FACE_FORWARD_DELAY = 10;
	private static final int FACE_FORWARD_TIME = 10;
	private static final double MOVING_THRESHOLD_SQR = 2.5000003e-7d;
	private static final float APPROACH_FACTOR = 0.55f;

	private final Mob mob;
	private int headStableTime;
	private float lastStableYHeadRot;

	public SlimeBionicBodyRotationControl(Mob mob) {
		super(mob);
		this.mob = mob;
	}

	@Override
	public void clientTick() {
		if (mob instanceof SlimeBionicEntity bionic && bionic.applyCombatFacing()) {
			rotateHeadIfNecessary();
			headStableTime = 0;
			lastStableYHeadRot = mob.yHeadRot;
			return;
		}
		if (isMoving()) {
			mob.yBodyRot = approach(mob.yBodyRot, mob.getYRot(), BODY_ROTATE_LIMIT);
			rotateHeadIfNecessary();
			lastStableYHeadRot = mob.yHeadRot;
			headStableTime = 0;
			return;
		}

		if (isCarryingMobPassenger())
			return;

		if (Math.abs(mob.yHeadRot - lastStableYHeadRot) > HEAD_STABLE_ANGLE) {
			headStableTime = 0;
			lastStableYHeadRot = mob.yHeadRot;
			rotateBodyIfNecessary();
			return;
		}

		headStableTime++;
		if (headStableTime > FACE_FORWARD_DELAY)
			rotateBodyTowardsHead();
	}

	private void rotateBodyIfNecessary() {
		mob.yBodyRot = Mth.rotateIfNecessary(mob.yBodyRot, mob.yHeadRot, mob.getMaxHeadYRot());
	}

	private void rotateHeadIfNecessary() {
		mob.yHeadRot = Mth.rotateIfNecessary(mob.yHeadRot, mob.yBodyRot, mob.getMaxHeadYRot());
	}

	private void rotateBodyTowardsHead() {
		int ticks = headStableTime - FACE_FORWARD_DELAY;
		float progress = Mth.clamp((float) ticks / FACE_FORWARD_TIME, 0.0f, 1.0f);
		float limit = BODY_ROTATE_LIMIT * (1.0f - progress);
		mob.yBodyRot = keepWithin(mob.yHeadRot, mob.yBodyRot, limit);
	}

	private boolean isCarryingMobPassenger() {
		return mob.getFirstPassenger() instanceof Mob;
	}

	private boolean isMoving() {
		double x = mob.getX() - mob.xo;
		double z = mob.getZ() - mob.zo;
		return x * x + z * z > MOVING_THRESHOLD_SQR;
	}

	private static float approach(float current, float target, float maxChange) {
		float delta = Mth.wrapDegrees(target - current);
		delta = Mth.clamp(delta, -maxChange, maxChange);
		return current + delta * APPROACH_FACTOR;
	}

	private static float keepWithin(float anchor, float value, float maxDifference) {
		float delta = Mth.wrapDegrees(value - anchor);
		delta = Mth.clamp(delta, -maxDifference, maxDifference);
		return anchor + delta * APPROACH_FACTOR;
	}
}
