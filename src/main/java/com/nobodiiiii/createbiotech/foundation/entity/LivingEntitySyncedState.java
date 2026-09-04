package com.nobodiiiii.createbiotech.foundation.entity;

/**
 * Compact, versioned payload shared by the small pieces of Biotech state attached to every living entity.
 * <p>
 * Layout (least-significant bit first): 2 version bits, 1 slime-mimic flag, 12 amplifier bits, 14 phase bits and
 * 35 phase-start-tick bits. The tick value is stored modulo {@code 2^35}; decoding chooses the occurrence nearest
 * the current game time, which keeps the representation stable for more than fifty years of continuous play.
 */
public final class LivingEntitySyncedState {
	private static final int VERSION = 1;
	private static final int VERSION_BITS = 2;
	private static final long VERSION_MASK = (1L << VERSION_BITS) - 1L;
	private static final long EMPTY = VERSION;

	private static final int SLIME_MIMIC_SHIFT = VERSION_BITS;
	private static final long SLIME_MIMIC_MASK = 1L << SLIME_MIMIC_SHIFT;

	private static final int AMPLIFIER_SHIFT = SLIME_MIMIC_SHIFT + 1;
	private static final int AMPLIFIER_BITS = 12;
	private static final int AMPLIFIER_VALUE_MASK = (1 << AMPLIFIER_BITS) - 1;
	private static final long AMPLIFIER_MASK = (long) AMPLIFIER_VALUE_MASK << AMPLIFIER_SHIFT;

	private static final int PHASE_SHIFT = AMPLIFIER_SHIFT + AMPLIFIER_BITS;
	private static final int PHASE_BITS = 14;
	private static final int PHASE_STEPS = 1 << PHASE_BITS;
	private static final int PHASE_VALUE_MASK = PHASE_STEPS - 1;
	private static final long PHASE_MASK = (long) PHASE_VALUE_MASK << PHASE_SHIFT;

	private static final int PHASE_START_SHIFT = PHASE_SHIFT + PHASE_BITS;
	private static final int PHASE_START_BITS = Long.SIZE - PHASE_START_SHIFT;
	private static final long PHASE_START_VALUE_MASK = (1L << PHASE_START_BITS) - 1L;
	private static final long PHASE_START_PERIOD = 1L << PHASE_START_BITS;
	private static final long PHASE_START_HALF_PERIOD = PHASE_START_PERIOD >>> 1;
	private static final long PHASE_START_MASK = PHASE_START_VALUE_MASK << PHASE_START_SHIFT;

	private LivingEntitySyncedState() {}

	public static long empty() {
		return EMPTY;
	}

	public static boolean isSlimeMimic(long packed) {
		return (normalize(packed) & SLIME_MIMIC_MASK) != 0L;
	}

	public static long withSlimeMimic(long packed, boolean slimeMimic) {
		long normalized = normalize(packed);
		return slimeMimic ? normalized | SLIME_MIMIC_MASK : normalized & ~SLIME_MIMIC_MASK;
	}

	public static int butterRotationAmplifier(long packed) {
		int encoded = (int) ((normalize(packed) & AMPLIFIER_MASK) >>> AMPLIFIER_SHIFT);
		return encoded == 0 ? -1 : encoded - 1;
	}

	public static float butterRotationPhase(long packed) {
		int encoded = (int) ((normalize(packed) & PHASE_MASK) >>> PHASE_SHIFT);
		float degrees = encoded * (360.0F / PHASE_STEPS);
		return degrees >= 180.0F ? degrees - 360.0F : degrees;
	}

	public static long butterRotationPhaseStartTick(long packed, long currentGameTime) {
		long normalized = normalize(packed);
		if (butterRotationAmplifier(normalized) < 0)
			return -1L;

		long encoded = (normalized & PHASE_START_MASK) >>> PHASE_START_SHIFT;
		long candidate = (currentGameTime & ~PHASE_START_VALUE_MASK) | encoded;
		long delta = candidate - currentGameTime;
		if (delta > PHASE_START_HALF_PERIOD)
			candidate -= PHASE_START_PERIOD;
		else if (delta < -PHASE_START_HALF_PERIOD)
			candidate += PHASE_START_PERIOD;
		return candidate;
	}

	public static long withButterRotation(long packed, int amplifier, float phase, long phaseStartTick) {
		long normalized = normalize(packed) & ~(AMPLIFIER_MASK | PHASE_MASK | PHASE_START_MASK);
		if (amplifier < 0)
			return normalized;

		int encodedAmplifier = Math.min(amplifier, AMPLIFIER_VALUE_MASK - 1) + 1;
		float wrappedPhase = phase % 360.0F;
		if (wrappedPhase < 0.0F)
			wrappedPhase += 360.0F;
		int encodedPhase = Math.round(wrappedPhase * PHASE_STEPS / 360.0F) & PHASE_VALUE_MASK;
		long encodedStart = phaseStartTick & PHASE_START_VALUE_MASK;

		return normalized
			| (long) encodedAmplifier << AMPLIFIER_SHIFT
			| (long) encodedPhase << PHASE_SHIFT
			| encodedStart << PHASE_START_SHIFT;
	}

	private static long normalize(long packed) {
		return (packed & VERSION_MASK) == VERSION ? packed : EMPTY;
	}
}
