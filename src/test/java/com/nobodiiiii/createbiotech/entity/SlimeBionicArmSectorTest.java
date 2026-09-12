package com.nobodiiiii.createbiotech.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

class SlimeBionicArmSectorTest {
	private static final float EPSILON = 0.001f;

	@Test
	void downwardUpperArmUsesOnlyTheLowerVerticalHemisphere() {
		Vec3 upperArm = new Vec3(0.0d, -1.0d, 0.0d);
		SlimeBionicCombat.AngularRange range = SlimeBionicCombat.attackRange(
			arm(upperArm), 0.0f, 0.0f, 0.0f);

		assertContains(range, upperArm);
		assertContains(range, new Vec3(0.0d, 0.0d, 1.0d));
		assertEquals(0.0f, range.minimumPitch(), EPSILON);
		assertEquals(90.0f, range.maximumPitch(), EPSILON);
		assertVerticalSpanAtMostNinety(range);
	}

	@Test
	void raisedUpperArmUsesOnlyTheUpperVerticalHemisphere() {
		Vec3 upperArm = new Vec3(0.0d, 1.0d, 0.0d);
		SlimeBionicCombat.AngularRange range = SlimeBionicCombat.attackRange(
			arm(upperArm), 0.0f, 0.0f, 0.0f);

		assertContains(range, upperArm);
		assertContains(range, new Vec3(0.0d, 0.0d, 1.0d));
		assertEquals(-90.0f, range.minimumPitch(), EPSILON);
		assertEquals(0.0f, range.maximumPitch(), EPSILON);
		assertVerticalSpanAtMostNinety(range);
	}

	@Test
	void horizontalUpperArmCoversFortyFiveDegreesAboveAndBelow() {
		Vec3 upperArm = new Vec3(0.0d, 0.0d, 1.0d);
		SlimeBionicCombat.AngularRange range = SlimeBionicCombat.attackRange(
			arm(upperArm), 0.0f, 0.0f, 0.0f);

		assertEquals(-45.0f, range.minimumPitch(), EPSILON);
		assertEquals(45.0f, range.maximumPitch(), EPSILON);
		assertVerticalSpanAtMostNinety(range);
	}

	@Test
	void obliqueUpperArmsInterpolateTheVerticalSectorContinuously() {
		for (float mountedPitch : new float[] {-90.0f, -60.0f, -30.0f, 0.0f, 30.0f, 60.0f, 90.0f}) {
			SlimeBionicCombat.AngularRange range = SlimeBionicCombat.attackRange(
				arm(SlimeBionicCombat.direction(0.0f, mountedPitch)), 0.0f, 55.0f, -60.0f);

			assertEquals(mountedPitch * 0.5f, range.centerPitch(), EPSILON);
			assertEquals(90.0f, range.maximumPitch() - range.minimumPitch(), EPSILON);
			assertContains(range, SlimeBionicCombat.direction(0.0f, mountedPitch));
		}
	}

	@Test
	void obliqueUpperArmsInterpolateTheHorizontalSectorContinuously() {
		for (float mountedYaw : new float[] {-90.0f, -60.0f, -30.0f, 0.0f, 30.0f, 60.0f, 90.0f}) {
			SlimeBionicCombat.AngularRange range = SlimeBionicCombat.attackRange(
				arm(SlimeBionicCombat.direction(mountedYaw, 0.0f)), 0.0f, -55.0f, 60.0f);

			assertEquals(mountedYaw * 55.0f / 90.0f, range.centerYaw(), EPSILON);
			assertEquals(70.0f, range.maximumYaw() - range.minimumYaw(), EPSILON);
			assertContains(range, SlimeBionicCombat.direction(mountedYaw, 0.0f));
		}
	}

	@Test
	void nearVerticalPiglinUpperArmsStillCoverStraightAhead() {
		Vec3[] upperArms = {
			new Vec3(-0.09980941563844681d, -0.9950041770935059d, -0.002189687453210354d),
			new Vec3(0.09980950504541397d, -0.9950041770935059d, 0.0021896983962506056d)
		};

		for (Vec3 upperArm : upperArms) {
			SlimeBionicCombat.AngularRange range = SlimeBionicCombat.attackRange(arm(upperArm));
			assertTrue(range.minimumYaw() <= 0.0f && range.maximumYaw() >= 0.0f,
				() -> "Near-vertical upper arm excluded straight ahead: " + range);
			assertTrue(Math.abs(range.centerYaw()) < 6.0f,
				() -> "Small upper-arm lean produced excessive horizontal bias: " + range);
		}
	}

	@Test
	void currentWorldPiglinTemplateCanStartAgainstTheAdjacentLevelDummy() {
		Vec3 bodyPosition = new Vec3(-54.84318133376923d, -60.0d, -49.87833087223628d);
		AABB target = new AABB(-55.8d, -60.0d, -50.8d, -55.2d, -58.0d, -50.2d);
		SurgicalAssembly.ArmAttackGeometry rightArm = new SurgicalAssembly.ArmAttackGeometry(
			new Vec3(-0.3621213436126709d, 1.5062334537506104d, -0.015969229862093925d),
			0.9267765283584595f, 0.5832037925720215f, 1.6830101013183594f,
			0.1767766773700714f, 0.04687497019767761f,
			new Vec3(-0.09980941563844681d, -0.9950041770935059d, -0.002189687453210354d));
		SurgicalAssembly.ArmAttackGeometry leftArm = new SurgicalAssembly.ArmAttackGeometry(
			new Vec3(0.3621213436126709d, 1.5062334537506104d, -0.00008036535291466862d),
			0.9267764091491699f, 0.583203911781311f, 1.6830101013183594f,
			0.17677666246891022f, 0.046874962747097015f,
			new Vec3(0.09980950504541397d, -0.9950041770935059d, 0.0021896983962506056d));

		assertTrue(SlimeBionicCombat.canStart(target, bodyPosition, 133.42514038085938f, rightArm)
			|| SlimeBionicCombat.canStart(target, bodyPosition, 133.42514038085938f, leftArm));
	}

	@Test
	void sidewaysUpperArmOwnsAFixedSideSector() {
		Vec3 upperArm = new Vec3(1.0d, 0.0d, 0.0d);
		SlimeBionicCombat.AngularRange range = SlimeBionicCombat.attackRange(
			arm(upperArm), 0.0f, 0.0f, 0.0f);

		assertContains(range, upperArm);
		assertEquals(-90.0f, range.minimumYaw(), EPSILON);
		assertEquals(-20.0f, range.maximumYaw(), EPSILON);
	}

	@Test
	void targetAimDoesNotChangeTheAttackSector() {
		SurgicalAssembly.ArmAttackGeometry arm = arm(new Vec3(0.0d, -1.0d, 0.0d));
		SlimeBionicCombat.AngularRange forward = SlimeBionicCombat.attackRange(arm);
		SlimeBionicCombat.AngularRange displaced = SlimeBionicCombat.attackRange(
			arm, 73.0f, -120.0f, -60.0f);

		assertEquals(forward, displaced);
	}

	@Test
	void hangingArmCanStartAnAttackAgainstALevelTarget() {
		SurgicalAssembly.ArmAttackGeometry arm = new SurgicalAssembly.ArmAttackGeometry(
			new Vec3(0.0d, 1.0d, 0.0d), 2.0f, -1.0f, 3.0f,
			0.25f, 0.5f, new Vec3(0.0d, -1.0d, 0.0d));

		assertTrue(SlimeBionicCombat.canStart(
			new AABB(-0.25d, 0.75d, 1.0d, 0.25d, 1.25d, 1.5d),
			Vec3.ZERO, 0.0f, arm));
	}

	@Test
	void hangingArmCannotHitEntirelyAboveItsHorizontalPlane() {
		SurgicalAssembly.ArmAttackGeometry arm = new SurgicalAssembly.ArmAttackGeometry(
			new Vec3(0.0d, 1.0d, 0.0d), 2.0f, -1.0f, 3.0f,
			0.25f, 0.5f, new Vec3(0.0d, -1.0d, 0.0d));

		assertFalse(SlimeBionicCombat.intersects(
			new AABB(-0.25d, 1.25d, 1.0d, 0.25d, 1.75d, 1.5d),
			Vec3.ZERO, 0.0f, new Vec3(0.0d, 0.0d, 1.0d), arm));
	}

	@Test
	void aimingUsesTheNearestPointOfTheCompleteTargetBox() {
		AABB target = new AABB(-0.25d, 0.0d, 1.0d, 0.25d, 2.0d, 1.5d);
		Vec3 origin = new Vec3(0.0d, 1.25d, 0.0d);

		assertEquals(new Vec3(0.0d, 1.25d, 1.0d), SlimeBionicCombat.aimPoint(target, origin));
		assertEquals(0.0f, SlimeBionicCombat.pitch(SlimeBionicCombat.aimAt(target, origin, 0.0f)), EPSILON);
	}

	@Test
	void verticalNearestPointUsesBodyYawWhenConstrained() {
		AABB target = new AABB(-0.25d, 2.0d, -0.25d, 0.25d, 2.5d, 0.25d);
		Vec3 aim = SlimeBionicCombat.constrainAim(
			SlimeBionicCombat.aimAt(target, Vec3.ZERO, 73.0f), 73.0f);

		assertEquals(73.0f, SlimeBionicCombat.yaw(aim), EPSILON);
		assertEquals(SlimeBionicCombat.MIN_AIM_PITCH_DEGREES, SlimeBionicCombat.pitch(aim), EPSILON);
	}

	private static SurgicalAssembly.ArmAttackGeometry arm(Vec3 upperArm) {
		return new SurgicalAssembly.ArmAttackGeometry(Vec3.ZERO, 2.0f, -2.0f, 2.0f,
			0.25f, 0.5f, upperArm);
	}

	private static void assertContains(SlimeBionicCombat.AngularRange range, Vec3 direction) {
		float yaw = SlimeBionicCombat.yaw(direction);
		float pitch = SlimeBionicCombat.pitch(direction);
		assertTrue(yaw >= range.minimumYaw() - EPSILON && yaw <= range.maximumYaw() + EPSILON,
			() -> "Upper-arm yaw " + yaw + " was outside " + range);
		assertTrue(pitch >= range.minimumPitch() - EPSILON && pitch <= range.maximumPitch() + EPSILON,
			() -> "Upper-arm pitch " + pitch + " was outside " + range);
	}

	private static void assertVerticalSpanAtMostNinety(SlimeBionicCombat.AngularRange range) {
		assertTrue(range.maximumPitch() - range.minimumPitch() <= 90.0f + EPSILON,
			() -> "Vertical span exceeded 90 degrees: " + range);
	}
}
