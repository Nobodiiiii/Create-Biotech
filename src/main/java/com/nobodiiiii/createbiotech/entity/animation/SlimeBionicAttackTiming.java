package com.nobodiiiii.createbiotech.entity.animation;

/** Shared timing markers for authored bionic-slime attack curves. */
public final class SlimeBionicAttackTiming {
	/** Normal playback length; faster real attack intervals compress the whole curve below this. */
	public static final int PLAYBACK_TICKS = 20;
	/** Full articulated empty-hand curve: wind-up 10, strike 5, hold 5, recovery 5. */
	public static final float ARTICULATED_EMPTY_HAND_CURVE_TICKS = 25.0f;
	/** The articulated empty hand reaches its authored strike pose at curve tick 15. */
	public static final float ARTICULATED_EMPTY_HAND_IMPACT_TICK = 15.0f;
	/** Full duration and impact keyframe of the authored articulated weapon curve. */
	public static final float ARTICULATED_WEAPON_CURVE_SECONDS = 1.125f;
	public static final float ARTICULATED_WEAPON_IMPACT_SECONDS = 0.5833f;
	/** Full rigid-arm curve: wind-up 4, strike 2, recovery 14. */
	public static final float RIGID_ARM_CURVE_TICKS = 20.0f;
	private SlimeBionicAttackTiming() {}

	public static int playbackTicks(int attackInterval) {
		return Math.max(1, Math.min(PLAYBACK_TICKS, attackInterval));
	}

	/** Converts the selected articulated curve's impact marker to the nearest playback tick. */
	public static int articulatedImpactTick(int attackInterval, boolean weapon) {
		float impactProgress = weapon
			? ARTICULATED_WEAPON_IMPACT_SECONDS / ARTICULATED_WEAPON_CURVE_SECONDS
			: ARTICULATED_EMPTY_HAND_IMPACT_TICK / ARTICULATED_EMPTY_HAND_CURVE_TICKS;
		return Math.max(1, Math.round(playbackTicks(attackInterval) * impactProgress));
	}
}
