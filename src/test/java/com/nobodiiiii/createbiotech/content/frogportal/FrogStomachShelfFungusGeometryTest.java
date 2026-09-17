package com.nobodiiiii.createbiotech.content.frogportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FrogStomachShelfFungusGeometryTest {

	@Test
	void shelfFungiAreStandardWallAttachedHalfEllipsesWithInsetGills() {
		for (long seed = 0; seed < 256; seed++) {
			FrogStomachShelfFungusGeometry.Structure fungus =
				FrogStomachShelfFungusGeometry.create(seed);
			assertTrue(fungus.longRadius() >= 3 && fungus.longRadius() <= 6);
			assertTrue(fungus.outwardRadius() >= 3 && fungus.outwardRadius() <= 6);
			assertTrue(fungus.longRadius() >= fungus.outwardRadius(),
				"The long radius must run parallel to the wall");

			Map<Position, FrogStomachShelfFungusGeometry.Part> parts = new HashMap<>();
			int maximumCapAlong = 0;
			int maximumCapOutward = 0;
			int maximumGillAlong = 0;
			int maximumGillOutward = 0;
			for (FrogStomachShelfFungusGeometry.Cell cell : fungus.cells()) {
				assertTrue(cell.outward() >= 0, "A shelf fungus must only grow away from its wall");
				Position position = new Position(cell.alongWall(), cell.outward(), cell.vertical());
				assertTrue(parts.put(position, cell.part()) == null, "Shelf geometry must not overlap itself");
				if (cell.part() == FrogStomachShelfFungusGeometry.Part.CAP) {
					assertEquals(0, cell.vertical());
					maximumCapAlong = Math.max(maximumCapAlong, Math.abs(cell.alongWall()));
					maximumCapOutward = Math.max(maximumCapOutward, cell.outward());
				} else {
					assertEquals(-1, cell.vertical());
					maximumGillAlong = Math.max(maximumGillAlong, Math.abs(cell.alongWall()));
					maximumGillOutward = Math.max(maximumGillOutward, cell.outward());
				}
			}

			assertEquals(fungus.longRadius(), maximumCapAlong);
			assertEquals(fungus.outwardRadius(), maximumCapOutward);
			assertEquals(fungus.longRadius() - 1, maximumGillAlong);
			assertEquals(fungus.outwardRadius() - 1, maximumGillOutward);
			assertStandardHalfEllipse(parts, fungus.longRadius(), fungus.outwardRadius(), 0,
				FrogStomachShelfFungusGeometry.Part.CAP);
			assertStandardHalfEllipse(parts, fungus.longRadius() - 1, fungus.outwardRadius() - 1, -1,
				FrogStomachShelfFungusGeometry.Part.GILLS);
			parts.forEach((position, part) -> {
				if (part == FrogStomachShelfFungusGeometry.Part.GILLS)
					assertEquals(FrogStomachShelfFungusGeometry.Part.CAP,
						parts.get(new Position(position.alongWall(), position.outward(), 0)),
						"Every gill must sit exactly one block below the cap");
			});
		}
	}

	private static void assertStandardHalfEllipse(
		Map<Position, FrogStomachShelfFungusGeometry.Part> parts,
		int alongRadius, int outwardRadius, int vertical,
		FrogStomachShelfFungusGeometry.Part expectedPart) {
		for (int alongWall = -alongRadius; alongWall <= alongRadius; alongWall++)
			for (int outward = 0; outward <= outwardRadius; outward++) {
				double along = alongWall / (double) alongRadius;
				double away = outward / (double) outwardRadius;
				FrogStomachShelfFungusGeometry.Part actual =
					parts.get(new Position(alongWall, outward, vertical));
				if (along * along + away * away <= 1.0d)
					assertEquals(expectedPart, actual);
				else
					assertNull(actual);
			}
	}

	private record Position(int alongWall, int outward, int vertical) {}
}
