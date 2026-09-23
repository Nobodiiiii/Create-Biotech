package com.nobodiiiii.createbiotech.entity.trait;

/** Abilities requiring retained donor respiratory or interaction organs. */
public enum BionicHeadTrait {
	WATER_BREATHING("water_breathing"),
	DRY_SUFFOCATION("dry_suffocation"),
	LONG_BREATH("long_breath"),
	TAMEABLE("tameable");

	private final String id;

	BionicHeadTrait(String id) { this.id = id; }

	public String id() { return id; }
	public String descriptionId() { return "create_biotech.trait." + id; }
}
