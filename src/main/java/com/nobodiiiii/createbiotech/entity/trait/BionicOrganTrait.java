package com.nobodiiiii.createbiotech.entity.trait;

/** Abilities whose donor material must retain a particular original anatomical role. */
public enum BionicOrganTrait {
	TRUST("trust", BionicTraitRule.presence(), BionicAnatomyRole.HEAD),
	FOOD_RECOGNITION("food_recognition", BionicTraitRule.presence(), BionicAnatomyRole.HEAD),
	VIBRATION_SENSE("vibration_sense", BionicTraitRule.presence(), BionicAnatomyRole.HEAD),
	WATER_AVERSION("water_aversion", BionicTraitRule.presence(), BionicAnatomyRole.HEAD),
	DETERRENCE("deterrence", BionicTraitRule.presence(), BionicAnatomyRole.HEAD),
	AGILE_LANDING("agile_landing", BionicTraitRule.threshold(0.5d), BionicAnatomyRole.LEG),
	FALL_REDUCTION("fall_reduction", BionicTraitRule.threshold(0.5d), BionicAnatomyRole.LEG),
	POWDER_SNOW_WALK("powder_snow_walk", BionicTraitRule.threshold(0.5d), BionicAnatomyRole.FOOT),
	LAVA_WALK("lava_walk", BionicTraitRule.threshold(0.5d), BionicAnatomyRole.FOOT),
	WALL_CLIMB("wall_climb", BionicTraitRule.threshold(0.5d), BionicAnatomyRole.LEG),
	WING_FLIGHT("wing_flight", BionicTraitRule.threshold(0.5d),
		BionicAnatomyRole.LEFT_WING, BionicAnatomyRole.RIGHT_WING),
	SWIM_SPECIALIST("swim_specialist", BionicTraitRule.threshold(0.5d), BionicAnatomyRole.FIN,
		BionicAnatomyRole.TAIL, BionicAnatomyRole.TENTACLE, BionicAnatomyRole.LEG),
	POISON_ATTACK("poison_attack", BionicTraitRule.presence(),
		BionicAnatomyRole.MOUTH, BionicAnatomyRole.SPINE),
	WITHER_ATTACK("wither_attack", BionicTraitRule.presence(), BionicAnatomyRole.ATTACK_HAND),
	HUNGER_ATTACK("hunger_attack", BionicTraitRule.presence(), BionicAnatomyRole.ATTACK_HAND),
	RANGED_EFFECT("ranged_effect", BionicTraitRule.threshold(0.5d), BionicAnatomyRole.ATTACK_HAND),
	ACTIVE_SPINES("active_spines", BionicTraitRule.presence(), BionicAnatomyRole.SPINE),
	SHELL_DEFENSE("shell_defense", BionicTraitRule.threshold(0.5d), BionicAnatomyRole.SHELL);

	private final String id;
	private final BionicTraitRule rule;
	private final BionicAnatomyRole[] roles;

	BionicOrganTrait(String id, BionicTraitRule rule, BionicAnatomyRole... roles) {
		this.id = id;
		this.rule = rule;
		this.roles = roles;
	}

	public String id() { return id; }
	public BionicTraitRule rule() { return rule; }
	public String descriptionId() { return "create_biotech.trait." + id; }
	public BionicAnatomyRole[] roles() { return roles.clone(); }
	/** Listed under the place of its first role; each role still works only in its own place. */
	public BionicTraitSlot slot() { return BionicTraitSlot.of(roles[0]); }
}
