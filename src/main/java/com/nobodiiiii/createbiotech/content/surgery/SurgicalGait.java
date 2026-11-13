package com.nobodiiiii.createbiotech.content.surgery;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import java.util.Map;
import com.nobodiiiii.createbiotech.api.bionic.BionicStat;
import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStats;

/** Shared anatomical calibration for a stitched body's movement and walk animation. */
public final class SurgicalGait {
	/** Villager legs are twelve model pixels long. */
	public static final double VILLAGER_LEG_LENGTH = 12.0d / 16.0d;
	/** A standard zombie uses the same twelve-pixel humanoid leg length. */
	public static final double ZOMBIE_LEG_LENGTH = 12.0d / 16.0d;
	/** Enderman legs are thirty model pixels long. */
	public static final double ENDERMAN_LEG_LENGTH = 30.0d / 16.0d;
	/** Ordinary zombie ground speed and the fixed horizontal speed of zero/one-leg hoppers. */
	public static final double ZOMBIE_WALK_SPEED = 0.23d;
	/** An enderman's 0.30 base movement plus its 0.15 attacking modifier. */
	public static final double ANGRY_ENDERMAN_SPEED = 0.45d;
	/** Asymptote of the single diminishing-return leg-length curve before other body factors. */
	public static final double LENGTH_SPEED_ASYMPTOTE = 621.0d / 1075.0d;
	/** Half-saturation length chosen to pass through 12 px / 0.23 and 30 px / 0.36. */
	public static final double LENGTH_SPEED_HALF_SATURATION = 195.0d / 172.0d;
	/** Keeps extreme long-leg and multiplier combinations within the intended survival range. */
	public static final double MAX_MOVEMENT_SPEED = 0.60d;
	/** Flight shares the land-speed ceiling and the diminishing-return tissue-volume curve. */
	public static final double MAX_FLYING_SPEED = 0.60d;
	/** HumanoidModel's normal walking-angle multiplier. */
	public static final float HUMANOID_LEG_SWING_FACTOR = 1.4f;
	/** Villager legs reach 1.4 * 0.5 = 0.7 radians. */
	public static final float VILLAGER_MAX_LEG_SWING = 0.7f;
	/** EndermanModel halves and then clamps each leg to 0.4 radians. */
	public static final float ENDERMAN_MAX_LEG_SWING = 0.4f;
	/** Vanilla caps this at 1; the bionic gait preserves phase through its full movement endpoint. */
	public static final float MAX_WALK_ANIMATION_SPEED = (float) (MAX_MOVEMENT_SPEED * 4.0d);

	private static final float MIN_ANIMATION_FREQUENCY_SCALE = 0.25f;
	private static final float MAX_ANIMATION_FREQUENCY_SCALE = 4.0f;

	private SurgicalGait() {}

	/** Calculates the authoritative base speed stored on a completed surgical body. */
	public static double movementSpeed(SurgicalAssembly.BodyBounds bounds) {
		return movementSpeed(bounds, null);
	}

	public static double movementSpeed(SurgicalAssembly.BodyBounds bounds, Level level) {
		return bounds == null ? movementSpeed(0, 0, 0, 0, level)
			: movementSpeed(bounds.legLength(), bounds.groundedLegCount(),
				bounds.groundedKneeCount(), bounds.legVolumeRatio(), level);
	}

	/**
	 * Calibrates two standard zombie-length grounded legs to ordinary zombie speed, then applies the
	 * diminishing leg-length curve and the bonuses for extra grounded legs, grounded-leg volume and knees.
	 * Bodies with at most one grounded leg use a fixed hopping speed instead.
	 */
	public static double movementSpeed(double averageLegLength, int groundedLegCount,
		int groundedKneeCount, double legVolumeRatio) {
		return movementSpeed(averageLegLength, groundedLegCount, groundedKneeCount, legVolumeRatio, null);
	}

	public static double movementSpeed(double averageLegLength, int groundedLegCount,
		int groundedKneeCount, double legVolumeRatio, Level level) {
		return BionicStats.evaluate(BionicStat.MOVEMENT_SPEED, level, Map.of(
			"leg_length", Math.max(0, averageLegLength), "leg_count", (double) groundedLegCount,
			"knee_count", (double) groundedKneeCount, "leg_volume_ratio", Mth.clamp(legVolumeRatio, 0, 1)));
	}

	/**
	 * Applies one saturating curve across the whole non-negative length range. Twelve pixels produce
	 * 0.23; thirty pixels produce 0.36; infinite length approaches about 0.578 before other factors.
	 */
	public static double lengthSpeed(double averageLegLength) {
		double length = Math.max(0.0d, averageLegLength);
		return LENGTH_SPEED_ASYMPTOTE * length
			/ (length + LENGTH_SPEED_HALF_SATURATION);
	}

	/** Legs three and four add 0.1 each; legs five through eight add 0.05 each. */
	public static double legCountFactor(int groundedLegCount) {
		int thirdAndFourth = Mth.clamp(groundedLegCount - 2, 0, 2);
		int fifthThroughEighth = Mth.clamp(groundedLegCount - 4, 0, 4);
		return 1.0d + thirdAndFourth * 0.1d + fifthThroughEighth * 0.05d;
	}

	/**
	 * Concave factor in [0.8, 1.2]. The logarithmic calibration passes exactly through 1.0 when
	 * grounded legs occupy one quarter of the sampled whole-body union volume.
	 */
	public static double legVolumeFactor(double legVolumeRatio) {
		return 0.8d + 0.4d * volumeRatioProgress(legVolumeRatio);
	}

	/** Active flight tissue occupying 25%, 50% and 100% yields about 0.30, 0.44 and 0.60. */
	public static double flyingSpeed(double flightVolumeRatio) {
		return flyingSpeed(flightVolumeRatio, null);
	}

	public static double flyingSpeed(double flightVolumeRatio, Level level) {
		double ratio = Double.isFinite(flightVolumeRatio) ? Mth.clamp(flightVolumeRatio, 0, 1) : 0;
		return BionicStats.evaluate(BionicStat.FLYING_SPEED, level, Map.of("flight_volume_ratio", ratio));
	}

	public static float maximumWalkAnimationSpeed(Level level) {
		return (float) (BionicStats.parameter(level, "movement_maximum") * 4);
	}

	private static double volumeRatioProgress(double volumeRatio) {
		double ratio = Double.isFinite(volumeRatio) ? Mth.clamp(volumeRatio, 0.0d, 1.0d) : 0.0d;
		return Math.log1p(8.0d * ratio) / Math.log(9.0d);
	}

	/** Every grounded knee adds 0.05 within one factor, capped at four knees / +0.2. */
	public static double kneeFactor(int groundedKneeCount) {
		return 1.0d + Mth.clamp(groundedKneeCount, 0, 4) * 0.05d;
	}

	/** Long legs use a progressively narrower arc, bottoming out at EndermanModel's limit. */
	public static float maximumLegSwing(double legLength) {
		return Mth.lerp((float) smoothLegProgress(legLength),
			VILLAGER_MAX_LEG_SWING, ENDERMAN_MAX_LEG_SWING);
	}

	/** Maximum walk-animation amount that produces {@link #maximumLegSwing(double)}. */
	public static float maximumHumanoidSwingAmount(double legLength) {
		return maximumLegSwing(legLength) / HUMANOID_LEG_SWING_FACTOR;
	}

	/**
	 * Keeps stride speed matched as both leg radius and maximum angle change. The horizontal reach
	 * of one side of a pendulum-like step is {@code legLength * sin(maximumAngle)}.
	 */
	public static float animationFrequencyScale(double legLength) {
		if (!Double.isFinite(legLength) || legLength <= 0.0d)
			return 1.0f;
		double referenceReach = VILLAGER_LEG_LENGTH * Math.sin(VILLAGER_MAX_LEG_SWING);
		double actualReach = legLength * Math.sin(maximumLegSwing(legLength));
		return Mth.clamp((float) (referenceReach / actualReach),
			MIN_ANIMATION_FREQUENCY_SCALE, MAX_ANIMATION_FREQUENCY_SCALE);
	}

	private static double smoothLegProgress(double legLength) {
		if (!Double.isFinite(legLength) || legLength <= 0.0d)
			return 0.0d;
		double progress = Mth.clamp((legLength - VILLAGER_LEG_LENGTH)
			/ (ENDERMAN_LEG_LENGTH - VILLAGER_LEG_LENGTH), 0.0d, 1.0d);
		return progress * progress * (3.0d - 2.0d * progress);
	}
}
