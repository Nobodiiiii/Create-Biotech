package com.nobodiiiii.createbiotech.content.frogportal;

import java.util.Random;

/** Pure local-space geometry; each cell gives a solid rib's inward depth from the wall shell. */
final class FrogStomachFoldGeometry {

	private FrogStomachFoldGeometry() {}

	static int[][] wallDepths(int width, int height, long seed) {
		int[][] depths = new int[width][height];
		if (width < 16 || height < 16)
			return depths;

		Random random = new Random(seed);
		int ribCount = Math.max(2, (width - 8) / 9);
		double spacing = (width - 8.0d) / ribCount;
		int depthLimit = Math.min(7, (width - 6) / 2);
		for (int rib = 0; rib < ribCount; rib++) {
			double center = 4.0d + (rib + 0.5d) * spacing + (random.nextDouble() - 0.5d);
			double phase = random.nextDouble() * Math.PI * 2.0d;
			double radius = Math.min(3.5d, spacing * 0.4d);
			for (int y = 1; y < height - 1; y++) {
				double progress = (y - 1.0d) / (height - 3.0d);
				double bend = Math.sin(y * 0.18d + phase) * Math.min(1.4d, spacing * 0.15d);
				double envelope = Math.sin(progress * Math.PI);
				double bulge = 0.5d + 0.5d * Math.sin(y * 0.32d + phase);
				double crestDepth = 3.0d + envelope * (2.0d + 2.0d * bulge);
				for (int along = 2; along < width - 2; along++) {
					double across = Math.abs(along - center - bend) / radius;
					if (across >= 1.0d)
						continue;
					int depth = Math.min(depthLimit,
						(int) Math.round(2.0d + (crestDepth - 2.0d) * Math.cos(across * Math.PI / 2.0d)));
					depths[along][y] = Math.max(depths[along][y], depth);
				}
			}
		}
		return depths;
	}
}
