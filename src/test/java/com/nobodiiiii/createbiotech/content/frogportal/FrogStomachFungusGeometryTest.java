package com.nobodiiiii.createbiotech.content.frogportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
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
			assertTrue(fungus.crownHeight() >= 1 && fungus.crownHeight() <= 2);
			sizes.add(fungus.stemHeight() + ":" + fungus.firstRadius() + ":" + fungus.secondRadius()
				+ ":" + fungus.crownHeight());
		}
		assertTrue(sizes.size() >= 24, "Mature fungi should not collapse to a few repeated sizes");
	}

	@Test
	void everyFungusIsAConnectedUmbrellaWithGillsBelowItsCap() {
		for (long seed = 0; seed < 64; seed++) {
			FrogStomachFungusGeometry.Structure fungus = FrogStomachFungusGeometry.create(seed);
			Set<Position> remaining = new HashSet<>();
			boolean hasGills = false, hasLight = false, hasCapAboveGills = false;
			for (FrogStomachFungusGeometry.Cell cell : fungus.cells()) {
				remaining.add(new Position(cell.first(), cell.forward(), cell.second()));
				hasGills |= cell.part() == FrogStomachFungusGeometry.Part.GILLS;
				hasLight |= cell.part() == FrogStomachFungusGeometry.Part.LIGHT;
				hasCapAboveGills |= cell.part() == FrogStomachFungusGeometry.Part.CAP
					&& cell.forward() == fungus.stemHeight() + 1;
			}
			assertTrue(hasGills && hasLight && hasCapAboveGills);
			assertTrue(remaining.contains(new Position(0, 0, 0)));

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
