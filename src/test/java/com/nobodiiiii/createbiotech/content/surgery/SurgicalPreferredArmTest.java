package com.nobodiiiii.createbiotech.content.surgery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class SurgicalPreferredArmTest {
	@Test
	void choosesDpsRatherThanSingleHitDamageAcrossBothSides() {
		var slow = arm(0.1f, 1.0f);
		var fast = arm(1.5f, 2.0f);
		assertTrue(SurgicalCombatCalibration.stats(slow).damageMultiplier()
			> SurgicalCombatCalibration.stats(fast).damageMultiplier());
		assertEquals(2, SurgicalCombatCalibration.highestDpsArmIndex(
			new SurgicalAssembly.AttackGeometry(List.of(slow, arm(0.75f, 0.5f)), List.of(fast))));
	}

	@Test
	void equalDpsKeepsFirstSlotDespiteDifferentHitDamage() {
		var first = arm(0.75f, 1.0f);
		var second = arm(1.5f, 1.0f);
		assertEquals(0, SurgicalCombatCalibration.highestDpsArmIndex(
			new SurgicalAssembly.AttackGeometry(List.of(first), List.of(second))));
	}

	@Test
	void minimumRecoveryCountsAgainstAnOtherwiseEqualVeryFastArm() {
		var tooFast = arm(12.0f, 1.0f);
		assertEquals(10, SurgicalCombatCalibration.stats(tooFast).attackInterval());
		assertEquals(1, SurgicalCombatCalibration.highestDpsArmIndex(
			new SurgicalAssembly.AttackGeometry(List.of(tooFast, arm(0.75f, 1.0f)), List.of())));
	}

	@Test
	void leftOnlyAndZeroDamageAssembliesStillHaveAStableFavourite() {
		assertEquals(1, SurgicalCombatCalibration.highestDpsArmIndex(
			new SurgicalAssembly.AttackGeometry(List.of(), List.of(arm(0.75f, 1.0f), arm(0.75f, 2.0f)))));
		assertEquals(0, SurgicalCombatCalibration.highestDpsArmIndex(
			new SurgicalAssembly.AttackGeometry(List.of(), List.of(arm(0.75f, 0.0f), arm(0.75f, 0.0f)))));
	}

	private static SurgicalAssembly.ArmAttackGeometry arm(float length, float volumeScale) {
		return new SurgicalAssembly.ArmAttackGeometry(Vec3.ZERO, length + 0.125f, -length, length,
			0.125f, (float) SurgicalCombatCalibration.ZOMBIE_ARM_VOLUME * volumeScale);
	}
}
