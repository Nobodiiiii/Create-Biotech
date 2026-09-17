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
			assertTrue(fungus.crownHeight() >= 0 && fungus.crownHeight() <= 1);
			sizes.add(fungus.stemHeight() + ":" + fungus.firstRadius() + ":" + fungus.secondRadius()
				+ ":" + fungus.crownHeight());
		}
		assertTrue(sizes.size() >= 24, "Mature fungi should not collapse to a few repeated sizes");
	}

	@Test
	void everyFungusIsAConnectedUmbrellaWithAHollowSteppedUnderside() {
		for (long seed = 0; seed < 64; seed++) {
			FrogStomachFungusGeometry.Structure fungus = FrogStomachFungusGeometry.create(seed);
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
				hasLight |= cell.part() == FrogStomachFungusGeometry.Part.LIGHT;
				if (cell.part() == FrogStomachFungusGeometry.Part.CAP)
					highestCap = Math.max(highestCap, cell.forward());
			}
			assertEquals(Set.of(fungus.stemHeight(), fungus.stemHeight() + 1), gillLevels,
				"The shallow underside should rise once toward its hollow center");
			assertTrue(hasLight);
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
					|| part == FrogStomachFungusGeometry.Part.LIGHT)
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
	}

	private static final int[][] DELTAS = {
		{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}
	};

	private record Position(int first, int forward, int second) {}
}
