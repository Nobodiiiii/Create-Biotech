package com.nobodiiiii.createbiotech.content.experience;

public final class ExperienceConstants {
	public static final int XP_PER_NUGGET = 3;
	public static final int TANK_CAPACITY_PER_BLOCK = 3000;
	public static final int CHAMBER_CACHE_CAPACITY = 3000;
	public static final int CHAMBER_XP_PER_LEVEL = 1000;
	public static final float PUMP_EFFICIENCY = 1.0f;
	public static final float PUMP_XP_PER_RPM_PER_SECOND = 3.0f / 4.0f * PUMP_EFFICIENCY;
	public static final float SPEED_NORMALIZATION_RPM = 256.0f;

	public static final int CLUSTER_NUGGET_VALUE = 64;
	public static final int LARGE_BUD_NUGGET_VALUE = 32;
	public static final int MEDIUM_BUD_NUGGET_VALUE = 16;
	public static final int SMALL_BUD_NUGGET_VALUE = 8;
	public static final int BUDDING_MATURE_XP = CLUSTER_NUGGET_VALUE * XP_PER_NUGGET;
	public static final int BUDDING_GROWTH_CHANCE = 20;

	private ExperienceConstants() {
	}
}
