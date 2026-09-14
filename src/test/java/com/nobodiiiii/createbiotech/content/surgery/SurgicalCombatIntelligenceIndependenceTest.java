package com.nobodiiiii.createbiotech.content.surgery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.nobodiiiii.createbiotech.entity.SlimeBionicCombat;
import com.nobodiiiii.createbiotech.entity.ai.BionicIntelligence;

class SurgicalCombatIntelligenceIndependenceTest {
	@Test
	@SuppressWarnings("deprecation")
	void everyIntelligenceTierUsesTheSameRecoveryAndGlobalInterval() {
		SurgicalCombatCalibration.ArmCombatStats stats =
			new SurgicalCombatCalibration.ArmCombatStats(1.0d, 20);

		for (BionicIntelligence intelligence : BionicIntelligence.values()) {
			assertEquals(1.0f, intelligence.meleeIntervalScale());
			assertEquals(SlimeBionicCombat.BODY_TURN_DEGREES, intelligence.bodyTurnDegrees());
			assertEquals(SlimeBionicCombat.AIM_TRACKING_DEGREES, intelligence.aimTrackingDegrees());
			assertEquals(1.0f, intelligence.posturePreferenceWeight());
			assertEquals(20, SurgicalCombatCalibration.armRecovery(stats, intelligence));
			assertEquals(20, SurgicalCombatCalibration.attackInterval(stats, 1, intelligence));
			assertEquals(18, SurgicalCombatCalibration.attackInterval(stats, 2, intelligence));
			assertEquals(16, SurgicalCombatCalibration.attackInterval(stats, 3, intelligence));
		}
	}

	@Test
	void anatomicalMinimumStillAppliesWithoutAnIntelligenceScale() {
		SurgicalCombatCalibration.ArmCombatStats stats =
			new SurgicalCombatCalibration.ArmCombatStats(0.5d, 10);

		assertEquals(12, SurgicalCombatCalibration.armRecovery(stats));
		assertEquals(12, SurgicalCombatCalibration.attackInterval(stats, 8));
	}
}
