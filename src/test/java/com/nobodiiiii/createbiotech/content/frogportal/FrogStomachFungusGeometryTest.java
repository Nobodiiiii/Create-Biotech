package com.nobodiiiii.createbiotech.content.frogportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FrogStomachFungusGeometryTest {

	@Test
	void matureFungiVaryWithinTheIntendedRoomScale() {
		Set<String> sizes = new HashSet<>();
		for (long seed = 0; seed < 128; seed++) {
			FrogStomachFungusGeometry.Structure fungus = FrogStomachFungusGeometry.create(seed);
			assertTrue(fungus.stemHeight() >= 5 && fungus.stemHeight() <= 12);
			assertTrue(fungus.firstRadius() >= 3 && fungus.firstRadius() <= 6);
			assertTrue(fungus.secondRadius() >= 3 && fungus.secondRadius() <= 6);
			assertTrue(fungus.stemHeight() <= Math.max(fungus.firstRadius(), fungus.secondRadius()) * 2,
				"The stem must not exceed twice the larger cap radius");
			assertTrue(fungus.crownHeight() >= 0 && fungus.crownHeight() <= 1);
			sizes.add(fungus.stemHeight() + ":" + fungus.firstRadius() + ":" + fungus.secondRadius()
				+ ":" + fungus.crownHeight());
		}
		assertTrue(sizes.size() >= 24, "Mature fungi should not collapse to a few repeated sizes");
	}

	@Test
	void largerCapsFavorLongerStems() {
		long[] totalStemHeight = new long[7];
		int[] samples = new int[7];
		for (long seed = 0; seed < 4096; seed++) {
			FrogStomachFungusGeometry.Structure fungus = FrogStomachFungusGeometry.create(seed);
			int capRadius = Math.max(fungus.firstRadius(), fungus.secondRadius());
			assertTrue(fungus.stemHeight() <= capRadius * 2);
			totalStemHeight[capRadius] += fungus.stemHeight();
			samples[capRadius]++;
		}
		for (int radius = 4; radius <= 6; radius++) {
			assertTrue(samples[radius - 1] > 0 && samples[radius] > 0);
			assertTrue(totalStemHeight[radius] * samples[radius - 1]
				> totalStemHeight[radius - 1] * samples[radius],
				"Average stem height should increase with the cap radius");
		}
	}

	@Test
	void everyFungusBaseFormsTwoOppositePairsWithOneBlockOfHeightDifference() {
		for (long seed = 0; seed < 512; seed++) {
			FrogStomachFungusGeometry.Structure fungus = FrogStomachFungusGeometry.create(seed);
			Map<Position, FrogStomachFungusGeometry.Part> parts = new HashMap<>();
			for (FrogStomachFungusGeometry.Cell cell : fungus.cells())
				parts.put(new Position(cell.first(), cell.forward(), cell.second()), cell.part());

			int negativeFirstHeight = baseHeight(parts, -1, 0);
			int positiveFirstHeight = baseHeight(parts, 1, 0);
			int negativeSecondHeight = baseHeight(parts, 0, -1);
			int positiveSecondHeight = baseHeight(parts, 0, 1);
			assertEquals(negativeFirstHeight, positiveFirstHeight,
				"Opposite bases on the first axis must be equally tall");
			assertEquals(negativeSecondHeight, positiveSecondHeight,
				"Opposite bases on the second axis must be equally tall");

			int capRadius = Math.max(fungus.firstRadius(), fungus.secondRadius());
			int tallerHeight = Math.max(negativeFirstHeight, negativeSecondHeight);
			assertTrue(negativeFirstHeight >= 1 && negativeSecondHeight >= 1,
				"Every fungus must have both base pairs");
			assertEquals(1, Math.abs(negativeFirstHeight - negativeSecondHeight));
			assertTrue(tallerHeight >= Math.max(2, capRadius - 2) && tallerHeight <= capRadius,
				"The taller base pair must be between radius minus two and the radius");
		}
	}

	@Test
	void everyFungusBuildsAnUmbrellaBeforeLiningItsExposedInteriorWithGills() {
		boolean foundLargeGilledFungus = false;
		for (long seed = 0; seed < 64; seed++) {
			FrogStomachFungusGeometry.Structure fungus = FrogStomachFungusGeometry.create(seed);
			boolean shouldHaveGills = fungus.firstRadius() >= 4 && fungus.secondRadius() >= 4;
			foundLargeGilledFungus |= shouldHaveGills;
			Set<Position> remaining = new HashSet<>();
			Map<Position, FrogStomachFungusGeometry.Part> parts = new HashMap<>();
			Set<Integer> gillLevels = new HashSet<>();
			boolean hasLight = false;
			int highestCap = Integer.MIN_VALUE;
			for (FrogStomachFungusGeometry.Cell cell : fungus.cells()) {
				Position position = new Position(cell.first(), cell.forward(), cell.second());
				remaining.add(position);
				parts.put(position, cell.part());
				if (cell.part() == FrogStomachFungusGeometry.Part.GILLS)
					gillLevels.add(cell.forward());
				hasLight |= cell.part() == FrogStomachFungusGeometry.Part.FROGLIGHT;
				if (cell.part() == FrogStomachFungusGeometry.Part.CAP)
					highestCap = Math.max(highestCap, cell.forward());
			}
			assertEquals(shouldHaveGills
				? Set.of(fungus.stemHeight(), fungus.stemHeight() + 1)
				: Set.of(), gillLevels,
				"Only caps whose two radii are at least four should have a stepped gill lining");
			assertEquals(shouldHaveGills, hasLight,
				"Froglights should only replace gills inside the largest caps");
			assertTrue(remaining.contains(new Position(0, 0, 0)));
			assertTrue(remaining.contains(new Position(0, fungus.stemHeight() + 1, 0)),
				"The stem must extend into the central hollow");
			assertFalse(remaining.contains(new Position(1, fungus.stemHeight() + 1, 0)),
				"The area around the stem must remain hollow");
			assertTrue(remaining.contains(new Position(fungus.firstRadius(), fungus.stemHeight(), 0)),
				"A cap lip must wrap around the exposed outer edge of the gills");
			assertFalse(remaining.contains(new Position(fungus.firstRadius(), fungus.stemHeight() + 2, 0)),
				"The roof must step inward instead of stacking a tall outer wall");
			assertTrue(highestCap <= fungus.stemHeight() + 3,
				"The cap should stay close to its earlier shallow profile");
			parts.forEach((position, part) -> {
				if (part == FrogStomachFungusGeometry.Part.GILLS
					|| part == FrogStomachFungusGeometry.Part.FROGLIGHT)
					assertEquals(FrogStomachFungusGeometry.Part.CAP,
						parts.get(new Position(position.first, position.forward + 1, position.second)),
						"Every visible gill must be covered by the cap above it");
			});

			ArrayDeque<Position> queue = new ArrayDeque<>();
			queue.add(new Position(0, 0, 0));
			remaining.remove(queue.getFirst());
			while (!queue.isEmpty()) {
				Position current = queue.removeFirst();
				for (int[] delta : DELTAS) {
					Position neighbor = new Position(current.first + delta[0], current.forward + delta[1],
						current.second + delta[2]);
					if (remaining.remove(neighbor))
						queue.add(neighbor);
				}
			}
			assertEquals(Set.of(), remaining, "Every cap block must stay attached to the stem");
			assertFalse(fungus.cells().isEmpty());
		}
		assertTrue(foundLargeGilledFungus, "The sampled structures must cover the gilled size variant");
	}

	private static final int[][] DELTAS = {
		{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}
	};

	private static int baseHeight(Map<Position, FrogStomachFungusGeometry.Part> parts,
		int first, int second) {
		int height = 0;
		while (parts.get(new Position(first, height, second)) == FrogStomachFungusGeometry.Part.STEM)
			height++;
		return height;
	}

	private record Position(int first, int forward, int second) {}
}
