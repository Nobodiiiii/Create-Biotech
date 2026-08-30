package com.nobodiiiii.createbiotech.entity.animation;

/** Presentation-only timing for authored bionic-slime attack curves. */
public final class SlimeBionicAttackTiming {
	/** Normal playback length; faster real attack intervals compress the whole curve below this. */
	public static final int PLAYBACK_TICKS = 15;
	/** Full Ender Golem Attack 1 timeline: wind-up 10, strike 5, hold 5, recovery 5. */
	public static final float ENDER_GOLEM_SOURCE_TICKS = 25.0f;
	/** Full Deepling Brute melee timeline: wind-up 4, strike 2, recovery 14. */
	public static final float DEEPLING_BRUTE_SOURCE_TICKS = 20.0f;
	private SlimeBionicAttackTiming() {}

	public static int playbackTicks(int attackInterval) {
		return Math.max(1, Math.min(PLAYBACK_TICKS, attackInterval));
	}
}
