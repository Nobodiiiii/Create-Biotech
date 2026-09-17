package com.nobodiiiii.createbiotech.content.frogportal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Pure local-space geometry for the broad, umbrella-shaped mature stomach fungus. */
final class FrogStomachFungusGeometry {

	enum Part {
		STEM,
		GILLS,
		CAP,
		LIGHT
	}

	record Cell(int first, int forward, int second, Part part) {}

	record Structure(List<Cell> cells, int stemHeight, int firstRadius, int secondRadius,
		int crownHeight) {}

	private FrogStomachFungusGeometry() {}

	static Structure create(long seed) {
		Random random = new Random(mixSeed(seed));
		int stemHeight = 5 + random.nextInt(8);
		int firstRadius = 3 + random.nextInt(4);
		int secondRadius = Math.clamp(firstRadius - 1 + random.nextInt(3), 3, 6);
		int crownHeight = firstRadius >= 5 && random.nextBoolean() ? 2 : 1;
		Map<LocalPos, Part> parts = new LinkedHashMap<>();

		// The stem rises into the hollow under the cap instead of stopping at a flat underside.
		for (int forward = 0; forward < stemHeight + 2; forward++)
			put(parts, 0, forward, 0, Part.STEM);
		if (firstRadius >= 5) {
			put(parts, -1, 0, 0, Part.STEM);
			put(parts, 1, 0, 0, Part.STEM);
			put(parts, 0, 0, -1, Part.STEM);
			put(parts, 0, 0, 1, Part.STEM);
			if (stemHeight >= 9) {
				put(parts, -1, 1, 0, Part.STEM);
				put(parts, 1, 1, 0, Part.STEM);
				put(parts, 0, 1, -1, Part.STEM);
				put(parts, 0, 1, 1, Part.STEM);
			}
		}

		List<LocalPos> lightCandidates = new ArrayList<>();
		for (int first = -firstRadius; first <= firstRadius; first++)
			for (int second = -secondRadius; second <= secondRadius; second++) {
				if (!insideUmbrella(first, second, firstRadius, secondRadius))
					continue;
				double radius = normalizedRadius(first, second, firstRadius, secondRadius);
				int inwardRise = radius > 0.72d ? 0 : radius > 0.38d ? 1 : 2;
				int underside = stemHeight + inwardRise;
				put(parts, first, underside, second, Part.GILLS);
				put(parts, first, underside + 1, second, Part.CAP);
				if ((first != 0 || second != 0)
					&& radius <= 0.72d)
					lightCandidates.add(new LocalPos(first, underside, second));
			}

		int innerFirstRadius = Math.max(1, firstRadius - 2);
		int innerSecondRadius = Math.max(1, secondRadius - 2);
		for (int layer = 0; layer < crownHeight; layer++) {
			int layerFirstRadius = Math.max(1, innerFirstRadius - layer);
			int layerSecondRadius = Math.max(1, innerSecondRadius - layer);
			for (int first = -layerFirstRadius; first <= layerFirstRadius; first++)
				for (int second = -layerSecondRadius; second <= layerSecondRadius; second++)
					if (insideUmbrella(first, second, layerFirstRadius, layerSecondRadius))
						put(parts, first, stemHeight + 4 + layer, second, Part.CAP);
		}

		int lightCount = Math.min(3, Math.max(1, (firstRadius + secondRadius) / 4));
		for (int i = 0; i < lightCount && !lightCandidates.isEmpty(); i++) {
			LocalPos light = lightCandidates.remove(random.nextInt(lightCandidates.size()));
			parts.put(light, Part.LIGHT);
		}

		List<Cell> cells = new ArrayList<>(parts.size());
		parts.forEach((pos, part) -> cells.add(new Cell(pos.first(), pos.forward(), pos.second(), part)));
		return new Structure(List.copyOf(cells), stemHeight, firstRadius, secondRadius, crownHeight);
	}

	private static long mixSeed(long seed) {
		long mixed = seed + 0x9E3779B97F4A7C15L;
		mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
		mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
		return mixed ^ (mixed >>> 31);
	}

	private static boolean insideUmbrella(int first, int second, int firstRadius, int secondRadius) {
		if (firstRadius < 1 || secondRadius < 1)
			return false;
		return normalizedRadius(first, second, firstRadius, secondRadius) <= 1.08d;
	}

	private static double normalizedRadius(int first, int second, int firstRadius, int secondRadius) {
		double normalizedFirst = first / (double) firstRadius;
		double normalizedSecond = second / (double) secondRadius;
		return normalizedFirst * normalizedFirst + normalizedSecond * normalizedSecond;
	}

	private static void put(Map<LocalPos, Part> parts, int first, int forward, int second, Part part) {
		parts.put(new LocalPos(first, forward, second), part);
	}

	private record LocalPos(int first, int forward, int second) {}
}
