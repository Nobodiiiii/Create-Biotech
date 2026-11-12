package com.nobodiiiii.createbiotech.entity.trait;

import java.util.List;
import java.util.Set;

/** Single trait catalog. Scope, inheritance and conditions are independent. */
public enum BionicTrait {
	DISPOSITION("disposition", BionicTraitScope.HEAD,
		BionicTraitRule.special(0.5d), ValueKind.SUBTRAIT_SET, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	INTELLIGENCE("intelligence", BionicTraitScope.HEAD,
		BionicTraitRule.special(0.5d), ValueKind.SUBTRAIT_SET, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
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
	WATER_SINKING("water_sinking", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	WEB_ADAPTED("web_adapted", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	NO_BREATHING("no_breathing", BionicTraitScope.HEAD,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	WINGLESS_FLIGHT("wingless_flight", BionicTraitScope.TORSO,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	SHOULDER_RIDING("shoulder_riding", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	PROJECTILE_DEFLECTION("projectile_deflection", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	CONTACT_RETALIATION("contact_retaliation", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	WATER_BREATHING("water_breathing", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.prefer(BionicAnatomyRole.GILL, BionicAnatomyRole.HEAD)),
	DRY_SUFFOCATION("dry_suffocation", BionicTraitScope.HEAD,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.prefer(BionicAnatomyRole.GILL, BionicAnatomyRole.HEAD)),
	TAMEABLE("tameable", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	LIVESTOCK_TAMEABLE("livestock_tameable", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	VIBRATION_SENSE("vibration_sense", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.TENDRIL)),
	DETERRENCE("deterrence", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	// JSON selects feline legs; the other donors contribute any tissue installed in leg chains.
	FALL_DAMAGE_IMMUNE("fall_damage_immune", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	FALL_REDUCTION("fall_reduction", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.LEG)),
	POWDER_SNOW_WALK("powder_snow_walk", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.FOOT)),
	LAVA_WALK("lava_walk", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.FOOT)),
	WALL_CLIMB("wall_climb", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.LEG)),
	WING_FLIGHT("wing_flight", BionicTraitScope.ARM,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.WING_LIFT,
		Carrier.of(BionicAnatomyRole.LEFT_WING, BionicAnatomyRole.RIGHT_WING)),
	SWIM_SPECIALIST("swim_specialist", BionicTraitScope.TORSO,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	EFFECT_ATTACK("effect_attack", BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.ATTACK_EFFECT_SET, Condition.NONE,
		Carrier.allTissue()),
	SHELL_DEFENSE("shell_defense", BionicTraitScope.TORSO,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.SHELL_AND_BODY,
		Carrier.of(BionicAnatomyRole.SHELL, BionicAnatomyRole.TORSO)),
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
		Carrier.allTissue()),
	ENCHANT_VULNERABILITY("enchant_vulnerability", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.SUBTRAIT_SET, Condition.NONE,
		Carrier.allTissue()),
	HUNTING_INSTINCT("hunting_instinct", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	PACK_ALERT("pack_alert", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD)),
	ATTACK_KNOCKBACK("attack_knockback", BionicTraitScope.ARM,
		new BionicTraitRule(BionicTraitType.COVERAGE_SCALED, 0.5d), ValueKind.NUMBER, Condition.NONE,
		Carrier.allTissue()),
	DOOR_HANDLING("door_handling", BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	WING_SLOW_FALL("wing_slow_fall", BionicTraitScope.ARM,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.LIMB_COUNT,
		Carrier.of(BionicAnatomyRole.LEFT_WING, BionicAnatomyRole.RIGHT_WING)),
	// JSON restricts other donors to legs; Breeze contributes any tissue, installed in leg chains.
	STRONG_JUMP("strong_jump", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	ENDER_TELEPORT("ender_teleport", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	BLOCK_ATTACHMENT("block_attachment", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.BASE)),
	MAGIC_RESISTANCE("magic_resistance", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	WITHER_SHIELD("wither_shield", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue()),
	LAUNCH_STRIKE("launch_strike", BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		// Species mappings select golem arms and the other donors' entire heads.
		Carrier.allTissue()),
	RIDEABLE("rideable", BionicTraitScope.TORSO,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE, Carrier.of(BionicAnatomyRole.TORSO)),
	CARGO_CHEST("cargo_chest", BionicTraitScope.TORSO,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE, Carrier.of(BionicAnatomyRole.TORSO)),
	LOOT_PICKUP("loot_pickup", BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE, Carrier.of(BionicAnatomyRole.ATTACK_HAND)),
	SONIC_BOOM("sonic_boom", BionicTraitScope.TORSO,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.COMPLETE_CHEST,
		Carrier.of(BionicAnatomyRole.TORSO, BionicAnatomyRole.LEFT_RIBCAGE, BionicAnatomyRole.RIGHT_RIBCAGE)),
	HEAD_PROJECTILE("head_projectile", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.SUBTRAIT_SET, Condition.NONE, Carrier.of(BionicAnatomyRole.HEAD)),
	GUARDIAN_BEAM("guardian_beam", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.SUBTRAIT_SET, Condition.NONE, Carrier.of(BionicAnatomyRole.EYE)),
	SPELLCASTING("spellcasting", BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.SUBTRAIT_SET, Condition.NONE, Carrier.of(BionicAnatomyRole.ATTACK_HAND)),
	SELF_DESTRUCT("self_destruct", BionicTraitScope.TORSO,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE, Carrier.allTissue()),
	SNOW_TRAIL("snow_trail", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE, Carrier.of(BionicAnatomyRole.FOOT)),
	POLLINATION("pollination", BionicTraitScope.TORSO,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE, Carrier.of(BionicAnatomyRole.TORSO)),
	MILKABLE("milkable", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.UDDER_AND_BODY,
		Carrier.of(BionicAnatomyRole.UDDER, BionicAnatomyRole.TORSO)),
	WOOL_SHEARING("wool_shearing", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.WOOL_AND_BODY,
		Carrier.of(BionicAnatomyRole.TORSO, BionicAnatomyRole.WOOL)),
	EGG_LAYING("egg_laying", BionicTraitScope.TORSO,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE, Carrier.of(BionicAnatomyRole.TORSO)),
	SCUTE_SHEDDING("scute_shedding", BionicTraitScope.TORSO,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.SHELL_AND_BODY,
		Carrier.of(BionicAnatomyRole.SHELL, BionicAnatomyRole.TORSO));

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
	/** A value-free name is also used when an installed carrier cannot supply an effective value. */
	public String nameDescriptionId() {
		return valueKind == ValueKind.NUMBER || valueKind == ValueKind.SUBTRAIT_SET
			|| valueKind == ValueKind.ATTACK_EFFECT_SET || this == DETERRENCE || this == HUNTING_INSTINCT || this == PACK_ALERT
			? "create_biotech.trait.name." + id : descriptionId();
	}
	/** Allowed regions; head, arm and leg coverage is evaluated separately for each installed chain. */
	public Set<BionicTraitScope> scopes() { return scopes; }
	public Carrier carrier() { return carrier; }
	public BionicTraitRule rule() { return rule; }
	public ValueKind valueKind() { return valueKind; }
	public Condition condition() { return condition; }
	public boolean isHeadClassification() { return this == DISPOSITION || this == INTELLIGENCE; }

	boolean automaticDetection() {
		return switch (this) {
		case DISPOSITION, INTELLIGENCE -> true;
		case RIDEABLE, CARGO_CHEST, ENCHANT_VULNERABILITY, HUNTING_INSTINCT, ATTACK_KNOCKBACK, FIRE_IMMUNE, FIRE_RESISTANCE, WATER_SENSITIVE, FREEZE_IMMUNE, FREEZE_RESISTANCE, FREEZE_VULNERABLE,
			SUN_SENSITIVE, MOISTURE_DEPENDENT, HEAT_SENSITIVE, INVERTED_HEALING, WATER_SINKING,
			WEB_ADAPTED, NO_BREATHING, WINGLESS_FLIGHT,
			PROJECTILE_DEFLECTION, CONTACT_RETALIATION,
			WATER_BREATHING, TAMEABLE, LIVESTOCK_TAMEABLE, DETERRENCE, FALL_DAMAGE_IMMUNE, POWDER_SNOW_WALK, SWIM_SPECIALIST,
			IMMUNE_EFFECTS, NATURAL_ARMOR, KNOCKBACK_RESISTANCE, PASSIVE_REGENERATION -> true;
		default -> false;
		};
	}

	/** Donor model labels select carriers only; they never imply an installation scope. */
	public record Carrier(List<BionicAnatomyRole> roles, List<BionicAnatomyRole> fallback,
		List<BionicAnatomyRole> excluded) {
		public Carrier(List<BionicAnatomyRole> roles, List<BionicAnatomyRole> fallback) {
			this(roles, fallback, List.of());
		}
		public Carrier {
			roles = List.copyOf(roles);
			fallback = List.copyOf(fallback);
			excluded = List.copyOf(excluded);
		}
		/** Distinguish a supporting core from appendages sharing its model-part label. */
		Carrier excluding(BionicAnatomyRole... roles) {
			var exclusions = new java.util.ArrayList<>(excluded);
			exclusions.addAll(List.of(roles));
			return new Carrier(this.roles, fallback, exclusions);
		}
		public boolean isAllTissue() { return roles.isEmpty(); }
		static Carrier allTissue() { return new Carrier(List.of(), List.of()); }
		static Carrier of(BionicAnatomyRole... roles) { return new Carrier(List.of(roles), List.of()); }
		static Carrier prefer(BionicAnatomyRole primary, BionicAnatomyRole fallback) {
			return new Carrier(List.of(primary), List.of(fallback));
		}
	}

	public enum ValueKind { ABILITY, NUMBER, EFFECT_SET, ATTACK_EFFECT_SET, SUBTRAIT_SET }
	public enum Condition {
		NONE, LIMB_COUNT, WING_LIFT, BODY_VOLUME, WOOL_AND_BODY, UDDER_AND_BODY,
		SHELL_AND_BODY, COMPLETE_CHEST
	}
}
