package com.nobodiiiii.createbiotech.content.magmabelt;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.Create;

import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SpriteShifter;

public class MagmaBeltSpriteShifts {

	public static final SpriteShiftEntry BELT = get("block/belt", "block/magma_belt_scroll"),
		BELT_OFFSET = get("block/belt_offset", "block/magma_belt_scroll"),
		BELT_DIAGONAL = get("block/belt_diagonal", "block/magma_belt_diagonal_scroll");

	private MagmaBeltSpriteShifts() {}

	public static void init() {}

	private static SpriteShiftEntry get(String originalLocation, String targetLocation) {
		return SpriteShifter.get(Create.asResource(originalLocation), CreateBiotech.asResource(targetLocation));
	}
}
