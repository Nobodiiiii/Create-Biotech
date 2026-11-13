package com.nobodiiiii.createbiotech.content.surgery;

import java.util.Map;
import com.nobodiiiii.createbiotech.api.bionic.BionicStat;
import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStats;
import net.minecraft.world.level.Level;

/** Converts a stitched body's sampled union volume into its maximum health. */
public final class SurgicalHealthCalibration {
	public static final double MAX_HEALTH = 400.0d;
	public static final double MIN_HEALTH = 1.0d;

	private static final double MAX_MEASURED_VOLUME = SurgicalAssembly.MAX_BODY_SIZE
		* SurgicalAssembly.MAX_BODY_SIZE * SurgicalAssembly.MAX_BODY_SIZE;
	private static final double ENVELOPE_TOLERANCE = 1.05d;
	private static final double GEOMETRY_EPSILON = 1.0e-6d;

	private SurgicalHealthCalibration() {}

	/**
	 * A Hill curve matches the zombie (20 health) and ravager (100 health) reference bodies, then gives diminishing gains
	 * at larger sizes while approaching, but never exceeding, the configured ceiling.
	 */
	public static double maximumHealth(double volume) {
		return maximumHealth(volume, null);
	}

	public static double maximumHealth(double volume, Level level) {
		if (!validVolume(volume))
			return MIN_HEALTH;
		return BionicStats.evaluate(BionicStat.MAX_HEALTH, level, Map.of("volume", volume));
	}

	/** Zero is a valid measurement for planar parts; NaN denotes an unmeasured body. */
	public static boolean validVolume(double volume) {
		return Double.isFinite(volume) && volume >= 0.0d && volume <= MAX_MEASURED_VOLUME;
	}

	/** Rejects a client-reported union volume that cannot fit inside its reported visual envelope. */
	public static boolean validMeasuredVolume(double volume,
		SurgicalAssembly.HitboxGeometry hitboxGeometry) {
		if (!validVolume(volume) || hitboxGeometry == null)
			return false;
		SurgicalAssembly.VisualBounds overall = hitboxGeometry.overall();
		double envelopeVolume = (double) overall.size(0) * overall.size(1) * overall.size(2);
		return Double.isFinite(envelopeVolume) && envelopeVolume > 0.0d
			&& volume <= envelopeVolume * ENVELOPE_TOLERANCE + GEOMETRY_EPSILON;
	}
}
