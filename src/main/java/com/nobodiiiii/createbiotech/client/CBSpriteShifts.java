package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;

public class CBSpriteShifts {

	public static final CTSpriteShiftEntry EXPLOSION_PROOF_CASING = omni("explosion_proof_casing"),
		EXPLOSION_PROOF_CASING_SIDE = omni("explosion_proof_casing_side");

	private CBSpriteShifts() {}

	public static void init() {}

	private static CTSpriteShiftEntry omni(String name) {
		return CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL, CreateBiotech.asResource("block/" + name),
			CreateBiotech.asResource("block/" + name + "_connected"));
	}
}
