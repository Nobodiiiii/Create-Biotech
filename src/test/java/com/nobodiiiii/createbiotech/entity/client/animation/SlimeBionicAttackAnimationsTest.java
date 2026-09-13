package com.nobodiiiii.createbiotech.entity.client.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.nobodiiiii.createbiotech.entity.animation.SlimeBionicAttackTiming;

class SlimeBionicAttackAnimationsTest {
	private static final float EPSILON = 1.0e-5f;

	@Test
	void articulatedEmptyHandFoldsAttackingElbowDeeplyAtFullWindup() {
		float windupProgress = 10.0f
			/ SlimeBionicAttackTiming.ARTICULATED_EMPTY_HAND_CURVE_TICKS;
		SlimeBionicAttackAnimations.AttackPose pose =
			SlimeBionicAttackAnimations.articulatedEmptyHandSwing(windupProgress);

		assertEquals((float) Math.toRadians(-110.0d), pose.attackingElbow().x(), EPSILON);
	}

	@Test
	void articulatedEmptyHandStraightensAndReachesForwardAtImpact() {
		float impactProgress = SlimeBionicAttackTiming.ARTICULATED_EMPTY_HAND_IMPACT_TICK
			/ SlimeBionicAttackTiming.ARTICULATED_EMPTY_HAND_CURVE_TICKS;
		SlimeBionicAttackAnimations.AttackPose pose =
			SlimeBionicAttackAnimations.articulatedEmptyHandSwing(impactProgress);

		assertEquals((float) Math.toRadians(-75.0d), pose.attackingShoulder().x(), EPSILON);
		assertEquals(0.0f, pose.attackingElbow().x(), EPSILON);
		assertEquals(0.0f, pose.attackingElbow().y(), EPSILON);
		assertEquals(0.0f, pose.attackingElbow().z(), EPSILON);
	}
}
