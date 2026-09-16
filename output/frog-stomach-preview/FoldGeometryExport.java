package com.nobodiiiii.createbiotech.content.frogportal;

import java.util.Arrays;
import java.util.Random;

/** Exports the actual wall-rib algorithm to the concept viewer, without starting Minecraft. */
public class FoldGeometryExport {
	public static void main(String[] args) {
		Random random = new Random(20260916L);
		int[][][] walls = new int[4][][];
		for (int wall = 0; wall < walls.length; wall++)
			walls[wall] = FrogStomachFoldGeometry.wallDepths(48, 32, random.nextLong());
		System.out.println(Arrays.deepToString(walls));
	}
}
