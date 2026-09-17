package com.nobodiiiii.createbiotech.content.frogportal;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure local-space geometry for a shelf fungus growing horizontally out of a stomach wall. */
final class FrogStomachShelfFungusGeometry {

	enum Part {
		CAP,
		GILLS
	}

	record Cell(int alongWall, int outward, int vertical, Part part) {}

	record Structure(List<Cell> cells, int longRadius, int outwardRadius) {}

	private FrogStomachShelfFungusGeometry() {}

	static Structure create(long seed) {
		Random random = new Random(mixSeed(seed));
		int longRadius = 3 + random.nextInt(4);
		int outwardRadius = 3 + random.nextInt(longRadius - 2);
		List<Cell> cells = new ArrayList<>();

		addHalfEllipse(cells, longRadius, outwardRadius, 0, Part.CAP);
		addHalfEllipse(cells, longRadius - 1, outwardRadius - 1, -1, Part.GILLS);
		return new Structure(List.copyOf(cells), longRadius, outwardRadius);
	}

	private static void addHalfEllipse(List<Cell> cells, int alongRadius, int outwardRadius,
		int vertical, Part part) {
		for (int alongWall = -alongRadius; alongWall <= alongRadius; alongWall++)
			for (int outward = 0; outward <= outwardRadius; outward++) {
				double along = alongWall / (double) alongRadius;
				double away = outward / (double) outwardRadius;
				if (along * along + away * away <= 1.0d)
					cells.add(new Cell(alongWall, outward, vertical, part));
			}
	}

	private static long mixSeed(long seed) {
		long mixed = seed + 0xD1B54A32D192ED03L;
		mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
		mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
		return mixed ^ (mixed >>> 31);
	}
}
