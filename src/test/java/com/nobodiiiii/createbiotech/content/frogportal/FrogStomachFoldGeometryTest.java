package com.nobodiiiii.createbiotech.content.frogportal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FrogStomachFoldGeometryTest {

	@Test
	void foldsStayInsideShellAndLeaveAnOpenCenterAtAllSupportedSizeExtremes() {
		for (int width : new int[] {16, 18, 48, 256})
			for (int height : new int[] {16, 32, 256})
				for (long seed = 0; seed < 8; seed++) {
					int[][] depths = FrogStomachFoldGeometry.wallDepths(width, height, seed);
					for (int along = 0; along < width; along++)
						for (int y = 0; y < height; y++) {
							int depth = depths[along][y];
							assertTrue(depth >= 0 && depth <= 7);
							assertTrue(2 * depth < width - 2, "Opposite folds must not close the room");
							if (along <= 1 || along >= width - 2 || y == 0 || y == height - 1)
								assertTrue(depth == 0, "Fold crossed the shell or corner margin");
						}
				}
	}

	@Test
	void eachRibRemainsConnectedFromFloorToCeilingInsteadOfFormingFloatingShelves() {
		for (long seed = 0; seed < 24; seed++) {
			int width = 48, height = 32;
			int[][] depths = FrogStomachFoldGeometry.wallDepths(width, height, seed);
			Set<Integer> remaining = new HashSet<>();
			for (int x = 0; x < width; x++)
				for (int y = 0; y < height; y++)
					if (depths[x][y] > 0)
						remaining.add(x * height + y);
			assertFalse(remaining.isEmpty());
			while (!remaining.isEmpty()) {
				ArrayDeque<Integer> queue = new ArrayDeque<>();
				int first = remaining.iterator().next();
				queue.add(first);
				remaining.remove(first);
				boolean floor = false, ceiling = false;
				while (!queue.isEmpty()) {
					int key = queue.removeFirst(), x = key / height, y = key % height;
					floor |= y == 1;
					ceiling |= y == height - 2;
					for (int[] delta : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
						int nx = x + delta[0], ny = y + delta[1];
						if (nx < 0 || nx >= width || ny < 0 || ny >= height)
							continue;
						int neighbor = nx * height + ny;
						if (remaining.remove(neighbor))
							queue.add(neighbor);
					}
				}
				assertTrue(floor && ceiling, "Detached rib or shelf for seed " + seed);
			}
		}
	}

	@Test
	void roomSeedReproducesItsFolds() {
		int[][] first = FrogStomachFoldGeometry.wallDepths(48, 32, 12345L);
		int[][] again = FrogStomachFoldGeometry.wallDepths(48, 32, 12345L);
		for (int x = 0; x < first.length; x++)
			assertArrayEquals(first[x], again[x]);
	}
}
