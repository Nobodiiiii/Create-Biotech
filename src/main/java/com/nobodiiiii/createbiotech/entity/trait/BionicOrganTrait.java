package com.nobodiiiii.createbiotech.entity.trait;

/** Abilities whose donor material must retain a particular original anatomical role. */
public enum BionicOrganTrait {
	TRUST("trust", BionicAnatomyRole.HEAD),
	FOOD_RECOGNITION("food_recognition", BionicAnatomyRole.HEAD),
	VIBRATION_SENSE("vibration_sense", BionicAnatomyRole.HEAD),
	WATER_AVERSION("water_aversion", BionicAnatomyRole.HEAD),
	DETERRENCE("deterrence", BionicAnatomyRole.HEAD),
	AGILE_LANDING("agile_landing", BionicAnatomyRole.LEG),
	FALL_REDUCTION("fall_reduction", BionicAnatomyRole.LEG),
	POWDER_SNOW_WALK("powder_snow_walk", BionicAnatomyRole.FOOT),
	LAVA_WALK("lava_walk", BionicAnatomyRole.FOOT),
	WALL_CLIMB("wall_climb", BionicAnatomyRole.LEG),
	WING_FLIGHT("wing_flight", BionicAnatomyRole.LEFT_WING, BionicAnatomyRole.RIGHT_WING),
	SWIM_SPECIALIST("swim_specialist", BionicAnatomyRole.FIN,
		BionicAnatomyRole.TAIL, BionicAnatomyRole.TENTACLE, BionicAnatomyRole.LEG),
	POISON_ATTACK("poison_attack", BionicAnatomyRole.MOUTH, BionicAnatomyRole.SPINE),
	WITHER_ATTACK("wither_attack", BionicAnatomyRole.ATTACK_HAND),
	HUNGER_ATTACK("hunger_attack", BionicAnatomyRole.ATTACK_HAND),
	RANGED_EFFECT("ranged_effect", BionicAnatomyRole.ATTACK_HAND),
	ACTIVE_SPINES("active_spines", BionicAnatomyRole.SPINE),
	SHELL_DEFENSE("shell_defense", BionicAnatomyRole.SHELL);

	private final String id;
	private final BionicAnatomyRole[] roles;

	BionicOrganTrait(String id, BionicAnatomyRole... roles) {
		this.id = id;
		this.roles = roles;
	}

	public String id() { return id; }
	public String descriptionId() { return "create_biotech.trait." + id; }
	public BionicAnatomyRole[] roles() { return roles.clone(); }
	/** Listed under the place of its first role; each role still works only in its own place. */
	public BionicTraitSlot slot() { return BionicTraitSlot.of(roles[0]); }
}
