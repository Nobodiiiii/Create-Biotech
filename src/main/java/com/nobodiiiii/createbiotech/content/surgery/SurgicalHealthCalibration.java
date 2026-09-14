package com.nobodiiiii.createbiotech.content.surgery;

/** Converts a stitched body's sampled union volume into its maximum health. */
public final class SurgicalHealthCalibration {
	public static final double MAX_HEALTH = 200.0d;
	public static final double MIN_HEALTH = 1.0d;

	/** The undeformed humanoid cubes in a zombie's base model occupy 1664 model pixels. */
	private static final double ZOMBIE_VOLUME = 1664.0d / (16.0d * 16.0d * 16.0d);
	/** Deterministic union-sampler result for the overlapping cubes in an iron golem's rest model. */
	private static final double IRON_GOLEM_VOLUME = 1.4150238037109375d;
	private static final double HILL_OFFSET = 9.0d;
	private static final double HILL_EXPONENT = Math.log(6.0d)
		/ Math.log(IRON_GOLEM_VOLUME / ZOMBIE_VOLUME);
	private static final double MAX_MEASURED_VOLUME = SurgicalAssembly.MAX_BODY_SIZE
		* SurgicalAssembly.MAX_BODY_SIZE * SurgicalAssembly.MAX_BODY_SIZE;
	private static final double ENVELOPE_TOLERANCE = 1.05d;
	private static final double GEOMETRY_EPSILON = 1.0e-6d;

	private SurgicalHealthCalibration() {}

	/**
	 * A Hill curve matches the zombie and iron-golem reference bodies, then gives diminishing gains
	 * at larger sizes while approaching, but never exceeding, the configured ceiling.
	 */
	public static double maximumHealth(double volume) {
		if (!validVolume(volume))
			return MIN_HEALTH;
		double scaledVolume = Math.pow(volume / ZOMBIE_VOLUME, HILL_EXPONENT);
		double health = MAX_HEALTH * scaledVolume / (HILL_OFFSET + scaledVolume);
		return Math.max(MIN_HEALTH, Math.min(MAX_HEALTH, health));
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
