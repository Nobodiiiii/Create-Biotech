package com.nobodiiiii.createbiotech.content.surgery;

import net.minecraft.util.RandomSource;

/** Slime-ball yield for a connected group shoveled off a surgical table. */
public final class SurgicalSlimeDrops {
	public static final double ONE_BALL_VOLUME = 1.0d / 8.0d;
	public static final double TWO_BALL_VOLUME = 1.0d;
	public static final double THREE_BALL_VOLUME = 8.0d;
	public static final double FOUR_BALL_VOLUME = 64.0d;
	public static final int MAX_DROPS = 4;
	private static final double[] VOLUME_THRESHOLDS = {
		0.0d, ONE_BALL_VOLUME, TWO_BALL_VOLUME, THREE_BALL_VOLUME, FOUR_BALL_VOLUME
	};

	private SurgicalSlimeDrops() {}

	/**
	 * The expected yield is linear between the volume/yield knots (0, 0), (1/8, 1), (1, 2),
	 * (8, 3) and (64, 4). Stochastic rounding preserves that expectation, so reaching each volume
	 * threshold adds one guaranteed slime ball while the progress toward the next one is probabilistic.
	 */
	public static double expected(double volume) {
		if (!Double.isFinite(volume) || volume <= 0.0d)
			return 0.0d;
		for (int drops = 1; drops <= MAX_DROPS; drops++) {
			double upper = VOLUME_THRESHOLDS[drops];
			if (volume <= upper) {
				double lower = VOLUME_THRESHOLDS[drops - 1];
				return drops - 1.0d + (volume - lower) / (upper - lower);
			}
		}
		return MAX_DROPS;
	}

	public static int roll(double volume, RandomSource random) {
		double expected = expected(volume);
		int guaranteed = (int) Math.floor(expected);
		if (guaranteed >= MAX_DROPS || random == null)
			return guaranteed;
		return guaranteed + (random.nextDouble() < expected - guaranteed ? 1 : 0);
	}
}
