package com.nobodiiiii.createbiotech.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.entity.animation.SlimeBionicAttackTiming;

class SlimeBionicCombatTimingTest {
	@Test
	void articulatedEmptyHandImpactRoundsToAuthoredAnimationTick() {
		assertEquals(7, SlimeBionicAttackTiming.articulatedImpactTick(12, false));
		assertEquals(12, SlimeBionicAttackTiming.articulatedImpactTick(20, false));
		assertEquals(12, SlimeBionicAttackTiming.articulatedImpactTick(44, false));
	}

	@Test
	void articulatedWeaponImpactRoundsToAuthoredAnimationTick() {
		assertEquals(6, SlimeBionicAttackTiming.articulatedImpactTick(12, true));
		assertEquals(10, SlimeBionicAttackTiming.articulatedImpactTick(20, true));
		assertEquals(10, SlimeBionicAttackTiming.articulatedImpactTick(44, true));
	}

	@Test
	void armTypesShareDurationButUseSeparateContactStarts() {
		assertEquals(20, SlimeBionicCombat.duration(20));
		assertEquals(6, SlimeBionicCombat.contactStartTick(20, false, false));
		assertEquals(8, SlimeBionicCombat.contactStartTick(20, true, false));
		assertEquals(6, SlimeBionicCombat.contactStartTick(20, true, true));
		for (int interval = SurgicalCombatCalibration.MIN_GLOBAL_ATTACK_INTERVAL;
			interval <= SurgicalCombatCalibration.MAX_GLOBAL_ATTACK_INTERVAL; interval++) {
			assertEquals(Math.min(20, interval), SlimeBionicCombat.duration(interval));
			assertEquals(5, contactTicks(interval, false, false));
			assertEquals(5, contactTicks(interval, true, false));
			assertEquals(5, contactTicks(interval, true, true));
		}
	}

	private static int contactTicks(int interval, boolean hasElbow, boolean weapon) {
		int contactStart = SlimeBionicCombat.contactStartTick(interval, hasElbow, weapon);
		int count = 0;
		for (int elapsed = 0; elapsed < SlimeBionicCombat.duration(interval); elapsed++)
			if (SlimeBionicCombat.isContactTick(elapsed, contactStart))
				count++;
		return count;
	}
}
