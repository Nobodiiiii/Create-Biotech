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
		FROGLIGHT
	}

	record Cell(int first, int forward, int second, Part part) {}

	record Structure(List<Cell> cells, int stemHeight, int firstRadius, int secondRadius,
		int crownHeight) {}

	private FrogStomachFungusGeometry() {}

	static Structure create(long seed) {
		return create(seed, 3, 6);
	}

	static Structure createSmall(long seed) {
		return create(seed, 3, 4);
	}

	static Structure createLarge(long seed) {
		return create(seed, 5, 6);
	}

	private static Structure create(long seed, int minimumRadius, int maximumRadius) {
		Random random = new Random(mixSeed(seed));
		int firstRadius = minimumRadius + random.nextInt(maximumRadius - minimumRadius + 1);
		int secondRadius = Math.clamp(firstRadius - 1 + random.nextInt(3), minimumRadius, maximumRadius);
		int capRadius = Math.max(firstRadius, secondRadius);
		int stemHeight = 5 + random.nextInt(capRadius * 2 - 4);
		int crownHeight = firstRadius >= 5 && random.nextBoolean() ? 1 : 0;
		Map<LocalPos, Part> parts = new LinkedHashMap<>();

		// The stem rises into the hollow under the cap instead of stopping at its lower rim.
		for (int forward = 0; forward < stemHeight + 2; forward++)
			put(parts, 0, forward, 0, Part.STEM);
		int minimumTallerBaseHeight = Math.max(2, capRadius - 2);
		int tallerBaseHeight = minimumTallerBaseHeight
			+ random.nextInt(capRadius - minimumTallerBaseHeight + 1);
		int shorterBaseHeight = tallerBaseHeight - 1;
		boolean firstPairIsTaller = random.nextBoolean();
		addOppositeBasePair(parts, true,
			firstPairIsTaller ? tallerBaseHeight : shorterBaseHeight);
		addOppositeBasePair(parts, false,
			firstPairIsTaller ? shorterBaseHeight : tallerBaseHeight);

		generateUmbrellaCap(parts, stemHeight, firstRadius, secondRadius, crownHeight);
		List<LocalPos> lightCandidates = firstRadius >= 4 && secondRadius >= 4
			? lineCapInteriorWithGills(parts, stemHeight)
			: new ArrayList<>();

		int lightCount = Math.min(3, Math.max(1, (firstRadius + secondRadius) / 4));
		for (int i = 0; i < lightCount && !lightCandidates.isEmpty(); i++) {
			LocalPos light = lightCandidates.remove(random.nextInt(lightCandidates.size()));
			parts.put(light, Part.FROGLIGHT);
		}

		List<Cell> cells = new ArrayList<>(parts.size());
		parts.forEach((pos, part) -> cells.add(new Cell(pos.first(), pos.forward(), pos.second(), part)));
		return new Structure(List.copyOf(cells), stemHeight, firstRadius, secondRadius, crownHeight);
	}

	private static void addOppositeBasePair(Map<LocalPos, Part> parts, boolean firstAxis, int height) {
		for (int forward = 0; forward < height; forward++) {
			put(parts, firstAxis ? -1 : 0, forward, firstAxis ? 0 : -1, Part.STEM);
			put(parts, firstAxis ? 1 : 0, forward, firstAxis ? 0 : 1, Part.STEM);
		}
	}

	private static void generateUmbrellaCap(Map<LocalPos, Part> parts, int stemHeight, int firstRadius,
		int secondRadius, int crownHeight) {
		for (int first = -firstRadius; first <= firstRadius; first++)
			for (int second = -secondRadius; second <= secondRadius; second++) {
				if (!insideUmbrella(first, second, firstRadius, secondRadius))
					continue;
				double radius = normalizedRadius(first, second, firstRadius, secondRadius);
				if (radius > 0.72d)
					put(parts, first, stemHeight, second, Part.CAP);
				if (radius > 0.30d)
					put(parts, first, stemHeight + 1, second, Part.CAP);
				if (radius <= 0.72d)
					put(parts, first, stemHeight + 2, second, Part.CAP);
			}

		if (crownHeight > 0) {
			int crownFirstRadius = Math.max(1, firstRadius - 2);
			int crownSecondRadius = Math.max(1, secondRadius - 2);
			for (int first = -crownFirstRadius; first <= crownFirstRadius; first++)
				for (int second = -crownSecondRadius; second <= crownSecondRadius; second++)
					if (insideUmbrella(first, second, crownFirstRadius, crownSecondRadius))
						put(parts, first, stemHeight + 3, second, Part.CAP);
		}
	}

	private static List<LocalPos> lineCapInteriorWithGills(Map<LocalPos, Part> parts, int stemHeight) {
		List<LocalPos> exposedInnerFaces = new ArrayList<>();
		List<LocalPos> capBlocks = parts.entrySet()
			.stream()
			.filter(entry -> entry.getValue() == Part.CAP)
			.map(Map.Entry::getKey)
			.toList();
		for (LocalPos cap : capBlocks) {
			if (cap.forward() <= stemHeight)
				continue;
			LocalPos inner = new LocalPos(cap.first(), cap.forward() - 1, cap.second());
			boolean centralCavity = Math.abs(inner.first()) + Math.abs(inner.second()) <= 1;
			if (centralCavity || parts.containsKey(inner))
				continue;
			parts.put(inner, Part.GILLS);
			exposedInnerFaces.add(inner);
		}
		return exposedInnerFaces;
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
