package com.nobodiiiii.createbiotech.content.surgery;

import java.util.function.DoubleUnaryOperator;

/** Inverts the active volume formula on the measurable body domain, including capped plateaus. */
public final class SurgicalStatVolume {
	public static final double LIMIT = SurgicalAssembly.MAX_BODY_SIZE * SurgicalAssembly.MAX_BODY_SIZE * SurgicalAssembly.MAX_BODY_SIZE;

	private SurgicalStatVolume() {}

	public static double forValue(DoubleUnaryOperator curve, double reference, double target) {
		reference = Math.clamp(reference, 0, LIMIT);
		double current = curve.applyAsDouble(reference);
		if (target == current) return reference;
		double minimum = curve.applyAsDouble(0), maximum = curve.applyAsDouble(LIMIT);
		if (maximum <= minimum) return reference;
		if (target <= minimum) return 0;
		target = Math.min(target, maximum);
		if (target == current) return reference;
		double low = 0, high = reference;
		if (target > current) {
			low = reference;
			high = Math.min(LIMIT, Math.max(0.001, reference * 2));
			while (high < LIMIT && curve.applyAsDouble(high) < target) {
				low = high;
				high = Math.min(LIMIT, 2 * high);
			}
		}
		// Choose the start of a capped plateau, so attainable equivalent volumes stay finite.
		for (int step = 0; step < 42; step++) {
			double mid = (low + high) * 0.5;
			if (curve.applyAsDouble(mid) < target) low = mid;
			else high = mid;
		}
		return high;
	}
}
