package com.nobodiiiii.createbiotech.entity.trait;

import java.util.List;
import java.util.Set;

/** Single trait catalog. Legacy data location, scope, inheritance and conditions are independent. */
public enum BionicTrait {
	FIRE_IMMUNE("fire_immune", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	WATER_SENSITIVE("water_sensitive", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	FREEZE_IMMUNE("freeze_immune", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	FREEZE_VULNERABLE("freeze_vulnerable", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	SUN_SENSITIVE("sun_sensitive", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	MOISTURE_DEPENDENT("moisture_dependent", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	HEAT_SENSITIVE("heat_sensitive", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	INVERTED_HEALING("inverted_healing", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	FALL_DAMAGE_IMMUNE("fall_damage_immune", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	WEB_ADAPTED("web_adapted", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	NO_BREATHING("no_breathing", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	WINGLESS_FLIGHT("wingless_flight", DataDirectory.BODY, BionicTraitScope.TORSO,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	BODY_BOUNCE("body_bounce", DataDirectory.BODY, BionicTraitScope.TORSO,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	BODY_SLOW_FALL("body_slow_fall", DataDirectory.BODY, BionicTraitScope.TORSO,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	PROJECTILE_DEFLECTION("projectile_deflection", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	CONTACT_RETALIATION("contact_retaliation", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	WATER_BREATHING("water_breathing", DataDirectory.HEAD, BionicTraitScope.HEAD,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.prefer(BionicAnatomyRole.GILL, BionicAnatomyRole.HEAD)),
	DRY_SUFFOCATION("dry_suffocation", DataDirectory.HEAD, BionicTraitScope.HEAD,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.BREATHING_REQUIRED,
		Carrier.prefer(BionicAnatomyRole.GILL, BionicAnatomyRole.HEAD)),
	LONG_BREATH("long_breath", DataDirectory.HEAD, BionicTraitScope.HEAD,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	TAMEABLE("tameable", DataDirectory.HEAD, BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	TRUST("trust", DataDirectory.ORGAN, BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	FOOD_RECOGNITION("food_recognition", DataDirectory.ORGAN, BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	VIBRATION_SENSE("vibration_sense", DataDirectory.ORGAN, BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	WATER_AVERSION("water_aversion", DataDirectory.ORGAN, BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	DETERRENCE("deterrence", DataDirectory.ORGAN, BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	AGILE_LANDING("agile_landing", DataDirectory.ORGAN, BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.LEG_SUPPORT,
		Carrier.of(BionicAnatomyRole.LEG)),
	FALL_REDUCTION("fall_reduction", DataDirectory.ORGAN, BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.LEG_SUPPORT,
		Carrier.of(BionicAnatomyRole.LEG)),
	POWDER_SNOW_WALK("powder_snow_walk", DataDirectory.ORGAN, BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.LEG_SUPPORT,
		Carrier.of(BionicAnatomyRole.FOOT)),
	LAVA_WALK("lava_walk", DataDirectory.ORGAN, BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.LEG_SUPPORT,
		Carrier.of(BionicAnatomyRole.FOOT)),
	WALL_CLIMB("wall_climb", DataDirectory.ORGAN, BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.LEG_SUPPORT,
		Carrier.of(BionicAnatomyRole.LEG)),
	WING_FLIGHT("wing_flight", DataDirectory.ORGAN, BionicTraitScope.ARM,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.WING_LIFT,
		Carrier.of(BionicAnatomyRole.LEFT_WING, BionicAnatomyRole.RIGHT_WING)),
	SWIM_SPECIALIST("swim_specialist", DataDirectory.ORGAN, Set.of(BionicTraitScope.ARM, BionicTraitScope.LEG),
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.FIN, BionicAnatomyRole.TAIL,
			BionicAnatomyRole.TENTACLE, BionicAnatomyRole.LEG)),
	POISON_ATTACK("poison_attack", DataDirectory.ORGAN, BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.MOUTH, BionicAnatomyRole.SPINE)),
	WITHER_ATTACK("wither_attack", DataDirectory.ORGAN, BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.ATTACK_HAND)),
	HUNGER_ATTACK("hunger_attack", DataDirectory.ORGAN, BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.ATTACK_HAND)),
	RANGED_EFFECT("ranged_effect", DataDirectory.ORGAN, BionicTraitScope.ARM,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.RANGED_ATTACK,
		Carrier.of(BionicAnatomyRole.ATTACK_HAND)),
	ACTIVE_SPINES("active_spines", DataDirectory.ORGAN, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.SPINE)),
	SHELL_DEFENSE("shell_defense", DataDirectory.ORGAN, BionicTraitScope.TORSO,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.SHELL)),
	IMMUNE_EFFECTS("immune_effects", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(1.0d), ValueKind.EFFECT_SET, Condition.NONE,
		Carrier.allTissue()),
	NATURAL_ARMOR("natural_armor", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.NUMBER, Condition.NONE,
		Carrier.allTissue()),
	KNOCKBACK_RESISTANCE("knockback_resistance", DataDirectory.BODY, BionicTraitScope.TORSO,
		BionicTraitRule.scaled(), ValueKind.NUMBER, Condition.NONE,
		Carrier.allTissue()),
	PASSIVE_REGENERATION("passive_regeneration", DataDirectory.BODY, BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.NUMBER, Condition.NONE,
		Carrier.allTissue());

	private final String id;
	private final DataDirectory directory;
	private final Set<BionicTraitScope> scopes;
	private final Carrier carrier;
	private final BionicTraitRule rule;
	private final ValueKind valueKind;
	private final Condition condition;

	BionicTrait(String id, DataDirectory directory, BionicTraitScope scope, BionicTraitRule rule,
		ValueKind valueKind, Condition condition, Carrier carrier) {
		this(id, directory, Set.of(scope), rule, valueKind, condition, carrier);
	}

	BionicTrait(String id, DataDirectory directory, Set<BionicTraitScope> scopes, BionicTraitRule rule,
		ValueKind valueKind, Condition condition, Carrier carrier) {
		if (scopes.isEmpty() || scopes.contains(BionicTraitScope.WHOLE_BODY) && scopes.size() != 1)
			throw new IllegalArgumentException("Choose whole body or explicit installation scopes");
		this.id = id;
		this.directory = directory;
		this.scopes = Set.copyOf(scopes);
		this.carrier = carrier;
		this.rule = rule;
		this.valueKind = valueKind;
		this.condition = condition;
	}

	public String id() { return id; }
	public String descriptionId() { return "create_biotech.trait." + id; }
	public DataDirectory directory() { return directory; }
	/** These scopes select both valid installations and the full coverage denominator. */
	public Set<BionicTraitScope> scopes() { return scopes; }
	public Carrier carrier() { return carrier; }
	public BionicTraitRule rule() { return rule; }
	public ValueKind valueKind() { return valueKind; }
	public Condition condition() { return condition; }

	boolean automaticDetection() {
		return switch (directory) {
		case BODY -> true;
		case HEAD -> this != DRY_SUFFOCATION;
		case ORGAN -> this == AGILE_LANDING || this == POWDER_SNOW_WALK;
		};
	}

	/** Donor model labels select carriers only; they never imply an installation scope. */
	public record Carrier(List<BionicAnatomyRole> roles, List<BionicAnatomyRole> fallback) {
		public Carrier {
			roles = List.copyOf(roles);
			fallback = List.copyOf(fallback);
		}
		public boolean isAllTissue() { return roles.isEmpty(); }
		static Carrier allTissue() { return new Carrier(List.of(), List.of()); }
		static Carrier of(BionicAnatomyRole... roles) { return new Carrier(List.of(roles), List.of()); }
		static Carrier prefer(BionicAnatomyRole primary, BionicAnatomyRole fallback) {
			return new Carrier(List.of(primary), List.of(fallback));
		}
	}

	public enum ValueKind { ABILITY, NUMBER, EFFECT_SET }
	public enum Condition { NONE, LEG_SUPPORT, WING_LIFT, BREATHING_REQUIRED, RANGED_ATTACK }

	/** Compatibility routing for existing packs; never selects an evaluation algorithm. */
	public enum DataDirectory {
		BODY("bionic_body_traits"), HEAD("bionic_head_traits"), ORGAN("bionic_organ_traits");
		private final String path;
		DataDirectory(String path) { this.path = path; }
		public String path() { return path; }
	}
}
