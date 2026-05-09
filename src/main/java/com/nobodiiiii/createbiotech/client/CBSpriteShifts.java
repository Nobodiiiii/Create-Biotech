package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;

import net.createmod.catnip.data.Couple;

public class CBSpriteShifts {

	public static final CTSpriteShiftEntry EXPLOSION_PROOF_CASING = omni("explosion_proof_casing"),
		EXPLOSION_PROOF_CASING_SIDE = omni("explosion_proof_casing_side"),
		BLAST_PROOF_FRAMED_GLASS = omni("blast_proof_framed_glass");

	public static final Couple<CTSpriteShiftEntry> EXPLOSION_PROOF_ITEM_VAULT_TOP = vault("explosion_proof_item_vault_top"),
		EXPLOSION_PROOF_ITEM_VAULT_FRONT = vault("explosion_proof_item_vault_front"),
		EXPLOSION_PROOF_ITEM_VAULT_SIDE = vault("explosion_proof_item_vault_side"),
		EXPLOSION_PROOF_ITEM_VAULT_BOTTOM = vault("explosion_proof_item_vault_bottom");

	private CBSpriteShifts() {}

	public static void init() {}

	private static CTSpriteShiftEntry omni(String name) {
		return CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL, CreateBiotech.asResource("block/" + name),
			CreateBiotech.asResource("block/" + name + "_connected"));
	}

	private static Couple<CTSpriteShiftEntry> vault(String name) {
		final String prefixed = "block/vault/" + name;
		return Couple.createWithContext(
			medium -> CTSpriteShifter.getCT(AllCTTypes.RECTANGLE, CreateBiotech.asResource(prefixed + "_small"),
				CreateBiotech.asResource(medium ? prefixed + "_medium" : prefixed + "_large")));
	}
}
