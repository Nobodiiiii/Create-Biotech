package com.nobodiiiii.createbiotech.content.surgery;

import net.minecraft.util.RandomSource;

/** Slime-ball yield for a connected group shoveled off a surgical table. */
public final class SurgicalSlimeDrops {
	public static final double ONE_BALL_VOLUME = 1.0d / 8.0d;
	public static final double TWO_BALL_VOLUME = 1.0d;
	public static final int MAX_DROPS = 4;

	private SurgicalSlimeDrops() {}

	/**
	 * The expected yield rises from zero to one over the first eighth of a block, then follows the
	 * line through (1/8, 1) and (1, 2), capped at four. Stochastic rounding preserves that linear
	 * expectation while making volumes above those two thresholds guarantee one and two balls.
	 */
	public static double expected(double volume) {
		if (!Double.isFinite(volume) || volume <= 0.0d)
			return 0.0d;
		double expected = volume <= ONE_BALL_VOLUME
			? volume / ONE_BALL_VOLUME
			: 1.0d + (volume - ONE_BALL_VOLUME) / (TWO_BALL_VOLUME - ONE_BALL_VOLUME);
		return Math.min(MAX_DROPS, expected);
	}

	public static int roll(double volume, RandomSource random) {
		double expected = expected(volume);
		int guaranteed = (int) Math.floor(expected);
		if (guaranteed >= MAX_DROPS || random == null)
			return guaranteed;
		return guaranteed + (random.nextDouble() < expected - guaranteed ? 1 : 0);
	}
}
