package com.nobodiiiii.createbiotech.entity.trait;

import java.util.List;
import java.util.Set;

/** Single trait catalog. Scope, inheritance and conditions are independent. */
public enum BionicTrait {
	FIRE_IMMUNE("fire_immune", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	FIRE_RESISTANCE("fire_resistance", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	WATER_SENSITIVE("water_sensitive", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	FREEZE_IMMUNE("freeze_immune", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	FREEZE_RESISTANCE("freeze_resistance", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	FREEZE_VULNERABLE("freeze_vulnerable", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	SUN_SENSITIVE("sun_sensitive", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	MOISTURE_DEPENDENT("moisture_dependent", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	HEAT_SENSITIVE("heat_sensitive", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	INVERTED_HEALING("inverted_healing", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	FALL_DAMAGE_IMMUNE("fall_damage_immune", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	WEB_ADAPTED("web_adapted", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	NO_BREATHING("no_breathing", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	WINGLESS_FLIGHT("wingless_flight", BionicTraitScope.TORSO,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	BODY_BOUNCE("body_bounce", BionicTraitScope.TORSO,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	PROJECTILE_DEFLECTION("projectile_deflection", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	CONTACT_RETALIATION("contact_retaliation", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	WATER_BREATHING("water_breathing", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.prefer(BionicAnatomyRole.GILL, BionicAnatomyRole.HEAD)),
	DRY_SUFFOCATION("dry_suffocation", BionicTraitScope.HEAD,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.BREATHING_REQUIRED,
		Carrier.prefer(BionicAnatomyRole.GILL, BionicAnatomyRole.HEAD)),
	TAMEABLE("tameable", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	VIBRATION_SENSE("vibration_sense", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	WATER_AVERSION("water_aversion", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	DETERRENCE("deterrence", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	AGILE_LANDING("agile_landing", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.LIMB_COUNT,
		Carrier.of(BionicAnatomyRole.LEG)),
	FALL_REDUCTION("fall_reduction", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.LIMB_COUNT,
		Carrier.of(BionicAnatomyRole.LEG)),
	POWDER_SNOW_WALK("powder_snow_walk", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.LIMB_COUNT,
		Carrier.of(BionicAnatomyRole.FOOT)),
	LAVA_WALK("lava_walk", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.LIMB_COUNT,
		Carrier.of(BionicAnatomyRole.FOOT)),
	WALL_CLIMB("wall_climb", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.LIMB_COUNT,
		Carrier.of(BionicAnatomyRole.LEG)),
	WING_FLIGHT("wing_flight", BionicTraitScope.ARM,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.WING_LIFT,
		Carrier.of(BionicAnatomyRole.LEFT_WING, BionicAnatomyRole.RIGHT_WING)),
	SWIM_SPECIALIST("swim_specialist", BionicTraitScope.TORSO,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	EFFECT_ATTACK("effect_attack", BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.ATTACK_EFFECT_SET, Condition.NONE,
		Carrier.of(BionicAnatomyRole.MOUTH, BionicAnatomyRole.SPINE, BionicAnatomyRole.ATTACK_HAND)),
	ACTIVE_SPINES("active_spines", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.SPINE)),
	SHELL_DEFENSE("shell_defense", BionicTraitScope.TORSO,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.SHELL)),
	IMMUNE_EFFECTS("immune_effects", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.EFFECT_SET, Condition.NONE,
		Carrier.allTissue()),
	NATURAL_ARMOR("natural_armor", BionicTraitScope.WHOLE_BODY,
		new BionicTraitRule(BionicTraitType.COVERAGE_SCALED, 0.5d), ValueKind.NUMBER, Condition.NONE,
		Carrier.allTissue()),
	KNOCKBACK_RESISTANCE("knockback_resistance", BionicTraitScope.TORSO,
		new BionicTraitRule(BionicTraitType.COVERAGE_SCALED, 0.5d), ValueKind.NUMBER, Condition.NONE,
		Carrier.allTissue()),
	PASSIVE_REGENERATION("passive_regeneration", BionicTraitScope.WHOLE_BODY,
		new BionicTraitRule(BionicTraitType.COVERAGE_SCALED, 0.5d), ValueKind.NUMBER, Condition.NONE,
		Carrier.allTissue());

	private final String id;
	private final Set<BionicTraitScope> scopes;
	private final Carrier carrier;
	private final BionicTraitRule rule;
	private final ValueKind valueKind;
	private final Condition condition;

	BionicTrait(String id, BionicTraitScope scope, BionicTraitRule rule,
		ValueKind valueKind, Condition condition, Carrier carrier) {
		this(id, Set.of(scope), rule, valueKind, condition, carrier);
	}

	BionicTrait(String id, Set<BionicTraitScope> scopes, BionicTraitRule rule,
		ValueKind valueKind, Condition condition, Carrier carrier) {
		if (scopes.isEmpty() || scopes.contains(BionicTraitScope.WHOLE_BODY) && scopes.size() != 1)
			throw new IllegalArgumentException("Choose whole body or explicit installation scopes");
		this.id = id;
		this.scopes = Set.copyOf(scopes);
		this.carrier = carrier;
		this.rule = rule;
		this.valueKind = valueKind;
		this.condition = condition;
	}

	public String id() { return id; }
	public String descriptionId() { return "create_biotech.trait." + id; }
	/** Allowed regions; head, arm and leg coverage is evaluated separately for each installed chain. */
	public Set<BionicTraitScope> scopes() { return scopes; }
	public Carrier carrier() { return carrier; }
	public BionicTraitRule rule() { return rule; }
	public ValueKind valueKind() { return valueKind; }
	public Condition condition() { return condition; }

	boolean automaticDetection() {
		return switch (this) {
		case FIRE_IMMUNE, FIRE_RESISTANCE, WATER_SENSITIVE, FREEZE_IMMUNE, FREEZE_RESISTANCE, FREEZE_VULNERABLE,
			SUN_SENSITIVE, MOISTURE_DEPENDENT, HEAT_SENSITIVE, INVERTED_HEALING,
			FALL_DAMAGE_IMMUNE, WEB_ADAPTED, NO_BREATHING, WINGLESS_FLIGHT,
			BODY_BOUNCE, PROJECTILE_DEFLECTION, CONTACT_RETALIATION,
			WATER_BREATHING, TAMEABLE, DETERRENCE, AGILE_LANDING, POWDER_SNOW_WALK, SWIM_SPECIALIST,
			IMMUNE_EFFECTS, NATURAL_ARMOR, KNOCKBACK_RESISTANCE, PASSIVE_REGENERATION -> true;
		default -> false;
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

	public enum ValueKind { ABILITY, NUMBER, EFFECT_SET, ATTACK_EFFECT_SET }
	public enum Condition { NONE, LIMB_COUNT, WING_LIFT, BREATHING_REQUIRED }
}
