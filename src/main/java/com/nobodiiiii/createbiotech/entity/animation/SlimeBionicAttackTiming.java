package com.nobodiiiii.createbiotech.entity.animation;

/** Presentation-only timing for authored bionic-slime attack curves. */
public final class SlimeBionicAttackTiming {
	/** Normal playback length; faster real attack intervals compress the whole curve below this. */
	public static final int PLAYBACK_TICKS = 15;
	/** Full articulated empty-hand curve: wind-up 10, strike 5, hold 5, recovery 5. */
	public static final float ARTICULATED_EMPTY_HAND_CURVE_TICKS = 25.0f;
	/** Full rigid-arm curve: wind-up 4, strike 2, recovery 14. */
	public static final float RIGID_ARM_CURVE_TICKS = 20.0f;
	private SlimeBionicAttackTiming() {}

	public static int playbackTicks(int attackInterval) {
		return Math.max(1, Math.min(PLAYBACK_TICKS, attackInterval));
	}
}
