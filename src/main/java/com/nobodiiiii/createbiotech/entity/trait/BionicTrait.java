package com.nobodiiiii.createbiotech.entity.trait;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

/** Immutable ID-based definition. Built-in constants are defaults; loaded catalogs may contain any ID. */
public final class BionicTrait implements Comparable<BionicTrait> {
	public static final BionicTrait DISPOSITION = new BionicTrait("disposition", BionicTraitScope.HEAD,
		BionicTraitRule.special(0.5d), ValueKind.SUBTRAIT_SET, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD));
	public static final BionicTrait INTELLIGENCE = new BionicTrait("intelligence", BionicTraitScope.HEAD,
		BionicTraitRule.special(0.5d), ValueKind.SUBTRAIT_SET, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD));
	public static final BionicTrait FIRE_IMMUNE = new BionicTrait("fire_immune", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait FIRE_RESISTANCE = new BionicTrait("fire_resistance", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait WATER_SENSITIVE = new BionicTrait("water_sensitive", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait FREEZE_IMMUNE = new BionicTrait("freeze_immune", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait FREEZE_RESISTANCE = new BionicTrait("freeze_resistance", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait FREEZE_VULNERABLE = new BionicTrait("freeze_vulnerable", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait SUN_SENSITIVE = new BionicTrait("sun_sensitive", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait MOISTURE_DEPENDENT = new BionicTrait("moisture_dependent", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait HEAT_SENSITIVE = new BionicTrait("heat_sensitive", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait INVERTED_HEALING = new BionicTrait("inverted_healing", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait WATER_SINKING = new BionicTrait("water_sinking", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait WEB_ADAPTED = new BionicTrait("web_adapted", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait NO_BREATHING = new BionicTrait("no_breathing", BionicTraitScope.HEAD,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD));
	public static final BionicTrait WINGLESS_FLIGHT = new BionicTrait("wingless_flight", BionicTraitScope.TORSO,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait SHOULDER_RIDING = new BionicTrait("shoulder_riding", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait PROJECTILE_DEFLECTION = new BionicTrait("projectile_deflection", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait CONTACT_RETALIATION = new BionicTrait("contact_retaliation", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait WATER_BREATHING = new BionicTrait("water_breathing", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.prefer(BionicAnatomyRole.GILL, BionicAnatomyRole.HEAD));
	public static final BionicTrait DRY_SUFFOCATION = new BionicTrait("dry_suffocation", BionicTraitScope.HEAD,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.prefer(BionicAnatomyRole.GILL, BionicAnatomyRole.HEAD));
	public static final BionicTrait TAMEABLE = new BionicTrait("tameable", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD));
	public static final BionicTrait LIVESTOCK_TAMEABLE = new BionicTrait("livestock_tameable", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD));
	public static final BionicTrait VIBRATION_SENSE = new BionicTrait("vibration_sense", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.TENTACLE));
	public static final BionicTrait DETERRENCE = new BionicTrait("deterrence", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD));
	// JSON selects feline legs; the other donors contribute any tissue installed in leg chains.
	public static final BionicTrait FALL_DAMAGE_IMMUNE = new BionicTrait("fall_damage_immune", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait FALL_REDUCTION = new BionicTrait("fall_reduction", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.LEG));
	public static final BionicTrait POWDER_SNOW_WALK = new BionicTrait("powder_snow_walk", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.LEG));
	public static final BionicTrait LAVA_WALK = new BionicTrait("lava_walk", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.LEG));
	public static final BionicTrait WALL_CLIMB = new BionicTrait("wall_climb", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.LEG));
	public static final BionicTrait WING_FLIGHT = new BionicTrait("wing_flight", BionicTraitScope.ARM,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.WING_LIFT,
		Carrier.of(BionicAnatomyRole.WING));
	public static final BionicTrait SWIM_SPECIALIST = new BionicTrait("swim_specialist", BionicTraitScope.TORSO,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait EFFECT_ATTACK = new BionicTrait("effect_attack", BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.ATTACK_EFFECT_SET, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait SHELL_DEFENSE = new BionicTrait("shell_defense", BionicTraitScope.TORSO,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.SHELL_AND_BODY,
		Carrier.of(BionicAnatomyRole.SHELL, BionicAnatomyRole.TORSO));
	public static final BionicTrait IMMUNE_EFFECTS = new BionicTrait("immune_effects", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.EFFECT_SET, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait NATURAL_ARMOR = new BionicTrait("natural_armor", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.NUMBER, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait HEALTH_OFFSET = new BionicTrait("health_offset", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.NUMBER, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait DAMAGE_OFFSET = new BionicTrait("damage_offset", BionicTraitScope.ARM,
		BionicTraitRule.scaled(), ValueKind.NUMBER, Condition.NONE,
		Carrier.of(BionicAnatomyRole.ATTACK_HAND));
	public static final BionicTrait KNOCKBACK_RESISTANCE = new BionicTrait("knockback_resistance", BionicTraitScope.TORSO,
		BionicTraitRule.scaled(), ValueKind.NUMBER, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait PASSIVE_REGENERATION = new BionicTrait("passive_regeneration", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.NUMBER, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait ENCHANT_VULNERABILITY = new BionicTrait("enchant_vulnerability", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.SUBTRAIT_SET, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait HUNTING_INSTINCT = new BionicTrait("hunting_instinct", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD));
	public static final BionicTrait PACK_ALERT = new BionicTrait("pack_alert", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD));
	public static final BionicTrait ATTACK_KNOCKBACK = new BionicTrait("attack_knockback", BionicTraitScope.ARM,
		BionicTraitRule.scaled(), ValueKind.NUMBER, Condition.NONE,
		Carrier.of(BionicAnatomyRole.HEAD, BionicAnatomyRole.ATTACK_HAND));
	public static final BionicTrait DOOR_HANDLING = new BionicTrait("door_handling", BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait WING_SLOW_FALL = new BionicTrait("wing_slow_fall", BionicTraitScope.ARM,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.LIMB_COUNT,
		Carrier.of(BionicAnatomyRole.WING));
	// JSON restricts other donors to legs; Breeze contributes any tissue, installed in leg chains.
	public static final BionicTrait STRONG_JUMP = new BionicTrait("strong_jump", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait ENDER_TELEPORT = new BionicTrait("ender_teleport", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait BLOCK_ATTACHMENT = new BionicTrait("block_attachment", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		Carrier.of(BionicAnatomyRole.BASE));
	public static final BionicTrait MAGIC_RESISTANCE = new BionicTrait("magic_resistance", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.scaled(), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait WITHER_SHIELD = new BionicTrait("wither_shield", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE,
		Carrier.allTissue());
	public static final BionicTrait LAUNCH_STRIKE = new BionicTrait("launch_strike", BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE,
		// Species mappings select golem arms and the other donors' entire heads.
		Carrier.allTissue());
	public static final BionicTrait RIDEABLE = new BionicTrait("rideable", BionicTraitScope.TORSO,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE, Carrier.of(BionicAnatomyRole.BACK));
	public static final BionicTrait CARGO_CHEST = new BionicTrait("cargo_chest", BionicTraitScope.TORSO,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE, Carrier.of(BionicAnatomyRole.TORSO));
	public static final BionicTrait LOOT_PICKUP = new BionicTrait("loot_pickup", BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE, Carrier.of(BionicAnatomyRole.ATTACK_HAND));
	public static final BionicTrait SONIC_BOOM = new BionicTrait("sonic_boom", BionicTraitScope.TORSO,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.COMPLETE_CHEST,
		Carrier.of(BionicAnatomyRole.TORSO, BionicAnatomyRole.LEFT_RIBCAGE, BionicAnatomyRole.RIGHT_RIBCAGE));
	public static final BionicTrait HEAD_PROJECTILE = new BionicTrait("head_projectile", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.SUBTRAIT_SET, Condition.NONE, Carrier.of(BionicAnatomyRole.HEAD));
	public static final BionicTrait GUARDIAN_BEAM = new BionicTrait("guardian_beam", BionicTraitScope.HEAD,
		BionicTraitRule.presence(), ValueKind.SUBTRAIT_SET, Condition.NONE, Carrier.of(BionicAnatomyRole.EYE));
	public static final BionicTrait SPELLCASTING = new BionicTrait("spellcasting", BionicTraitScope.ARM,
		BionicTraitRule.presence(), ValueKind.SUBTRAIT_SET, Condition.NONE, Carrier.of(BionicAnatomyRole.ATTACK_HAND));
	public static final BionicTrait SELF_DESTRUCT = new BionicTrait("self_destruct", BionicTraitScope.TORSO,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE, Carrier.allTissue());
	public static final BionicTrait SNOW_TRAIL = new BionicTrait("snow_trail", BionicTraitScope.LEG,
		BionicTraitRule.threshold(0.5d), ValueKind.ABILITY, Condition.NONE, Carrier.of(BionicAnatomyRole.LEG));
	public static final BionicTrait POLLINATION = new BionicTrait("pollination", BionicTraitScope.TORSO,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE, Carrier.of(BionicAnatomyRole.TORSO));
	public static final BionicTrait MILKABLE = new BionicTrait("milkable", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.UDDER_AND_BODY,
		Carrier.of(BionicAnatomyRole.UDDER, BionicAnatomyRole.TORSO));
	public static final BionicTrait WOOL_SHEARING = new BionicTrait("wool_shearing", BionicTraitScope.WHOLE_BODY,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.WOOL_AND_BODY,
		Carrier.of(BionicAnatomyRole.TORSO, BionicAnatomyRole.WOOL));
	public static final BionicTrait EGG_LAYING = new BionicTrait("egg_laying", BionicTraitScope.TORSO,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE, Carrier.of(BionicAnatomyRole.TORSO));
	public static final BionicTrait SCUTE_SHEDDING = new BionicTrait("scute_shedding", BionicTraitScope.TORSO,
		BionicTraitRule.presence(), ValueKind.ABILITY, Condition.SHELL_AND_BODY,
		Carrier.of(BionicAnatomyRole.SHELL, BionicAnatomyRole.TORSO));
	private final ResourceLocation id;
	private final Component displayName;
	private final Component displayDescription;
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
		this.id = ResourceLocation.fromNamespaceAndPath("create_biotech", id);
		this.displayName = null;
		this.displayDescription = null;
		this.scopes = Set.copyOf(scopes);
		this.carrier = carrier;
		this.rule = rule;
		this.valueKind = valueKind;
		this.condition = condition;
	}

	public ResourceLocation id() { return id; }
	/** Built-in defaults are not the loaded trait catalog. */
	public static List<BionicTrait> builtIns() { return BUILT_INS; }
	/** ID-only transport handle; consumers resolve metadata against their own catalog. */
	public static BionicTrait reference(ResourceLocation id) {
		BionicTrait known = builtIn(id);
		return known != null ? known : new BionicTrait(id, Set.of(BionicTraitScope.WHOLE_BODY),
			BionicTraitRule.presence(), ValueKind.ABILITY, Condition.NONE, Carrier.allTissue(), null, null);
	}
	public static BionicTrait builtIn(ResourceLocation id) {
		return BUILT_INS.stream().filter(trait -> trait.id.equals(id)).findFirst().orElse(null);
	}
	public Component displayName() {
		return displayName != null ? displayName.copy() : isBuiltIn()
			? Component.translatable(nameDescriptionId()) : Component.literal(id.toString());
	}
	public Component displayDescription() {
		return displayDescription != null ? displayDescription.copy() : isBuiltIn()
			? Component.translatable("create_biotech.jei.trait." + id.getPath() + ".effect") : Component.empty();
	}
	public boolean isBuiltIn() { return builtIn(id) != null; }
	public boolean hasCustomName() { return displayName != null || !isBuiltIn(); }
	public BionicTrait(ResourceLocation id, Set<BionicTraitScope> scopes,
		BionicTraitRule rule, ValueKind valueKind, Condition condition, Carrier carrier,
		Component name, Component description) {
		if (scopes.isEmpty() || scopes.contains(BionicTraitScope.WHOLE_BODY) && scopes.size() != 1)
			throw new IllegalArgumentException("Choose whole body or explicit installation scopes");
		this.id = java.util.Objects.requireNonNull(id);
		this.scopes = Set.copyOf(scopes);
		this.rule = java.util.Objects.requireNonNull(rule);
		this.valueKind = java.util.Objects.requireNonNull(valueKind);
		this.condition = java.util.Objects.requireNonNull(condition);
		this.carrier = java.util.Objects.requireNonNull(carrier);
		this.displayName = name == null ? null : name.copy();
		this.displayDescription = description == null ? null : description.copy();
	}
	@Override public boolean equals(Object other) { return other instanceof BionicTrait trait && id.equals(trait.id); }
	@Override public int hashCode() { return id.hashCode(); }
	@Override public String toString() { return id.toString(); }
	@Override public int compareTo(BionicTrait other) {
		int left = BUILT_INS.indexOf(this), right = BUILT_INS.indexOf(other);
		if (left >= 0 || right >= 0) return Integer.compare(left < 0 ? Integer.MAX_VALUE : left, right < 0 ? Integer.MAX_VALUE : right);
		return id.compareTo(other.id);
	}
	public String descriptionId() { return "create_biotech.trait." + id.getPath(); }
	/** A value-free name is also used when an installed carrier cannot supply an effective value. */
	public String nameDescriptionId() {
		return valueKind == ValueKind.NUMBER || valueKind == ValueKind.SUBTRAIT_SET
			|| valueKind == ValueKind.ATTACK_EFFECT_SET || equals(DETERRENCE) || equals(HUNTING_INSTINCT) || equals(PACK_ALERT)
			? "create_biotech.trait.name." + id.getPath() : descriptionId();
	}
	/** Allowed regions; head, arm and leg coverage is evaluated separately for each installed chain. */
	public Set<BionicTraitScope> scopes() { return scopes; }
	public Carrier carrier() { return carrier; }
	public BionicTraitRule rule() { return rule; }
	public ValueKind valueKind() { return valueKind; }
	/** These numeric traits also accept negative donor values and aggregated results. */
	public boolean isStatOffset() { return equals(HEALTH_OFFSET) || equals(DAMAGE_OFFSET); }
	public Condition condition() { return condition; }
	public boolean isHeadClassification() { return equals(DISPOSITION) || equals(INTELLIGENCE); }

	boolean automaticDetection() {
		return AUTOMATIC.contains(this);
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
		public Carrier excluding(BionicAnatomyRole... roles) {
			var exclusions = new java.util.ArrayList<>(excluded);
			exclusions.addAll(List.of(roles));
			return new Carrier(this.roles, fallback, exclusions);
		}
		public boolean isAllTissue() { return roles.isEmpty(); }
		public static Carrier allTissue() { return new Carrier(List.of(), List.of()); }
		public static Carrier of(BionicAnatomyRole... roles) { return new Carrier(List.of(roles), List.of()); }
		public static Carrier prefer(BionicAnatomyRole primary, BionicAnatomyRole fallback) {
			return new Carrier(List.of(primary), List.of(fallback));
		}
	}

	public enum ValueKind { ABILITY, NUMBER, EFFECT_SET, ATTACK_EFFECT_SET, SUBTRAIT_SET }
	public enum Condition {
		NONE, LIMB_COUNT, WING_LIFT, BODY_VOLUME, WOOL_AND_BODY, UDDER_AND_BODY,
		SHELL_AND_BODY, COMPLETE_CHEST
	}

	private static final Set<BionicTrait> AUTOMATIC = Set.of(
		DISPOSITION, INTELLIGENCE, RIDEABLE, CARGO_CHEST, ENCHANT_VULNERABILITY, HUNTING_INSTINCT,
		ATTACK_KNOCKBACK, FIRE_IMMUNE, FIRE_RESISTANCE, WATER_SENSITIVE, FREEZE_IMMUNE, FREEZE_RESISTANCE,
		FREEZE_VULNERABLE, SUN_SENSITIVE, MOISTURE_DEPENDENT, HEAT_SENSITIVE, INVERTED_HEALING, WATER_SINKING,
		WEB_ADAPTED, NO_BREATHING, WINGLESS_FLIGHT, PROJECTILE_DEFLECTION, CONTACT_RETALIATION, WATER_BREATHING,
		TAMEABLE, LIVESTOCK_TAMEABLE, DETERRENCE, FALL_DAMAGE_IMMUNE, POWDER_SNOW_WALK, SWIM_SPECIALIST,
		IMMUNE_EFFECTS, NATURAL_ARMOR, KNOCKBACK_RESISTANCE, PASSIVE_REGENERATION);

	private static final List<BionicTrait> BUILT_INS = List.of(
		DISPOSITION, INTELLIGENCE, HEALTH_OFFSET, FIRE_IMMUNE, FIRE_RESISTANCE, WATER_SENSITIVE, FREEZE_IMMUNE,
		FREEZE_RESISTANCE, FREEZE_VULNERABLE, SUN_SENSITIVE, MOISTURE_DEPENDENT, HEAT_SENSITIVE, INVERTED_HEALING,
		WATER_SINKING, WEB_ADAPTED, NO_BREATHING, WINGLESS_FLIGHT, SHOULDER_RIDING, PROJECTILE_DEFLECTION,
		CONTACT_RETALIATION, WATER_BREATHING, DRY_SUFFOCATION, TAMEABLE, LIVESTOCK_TAMEABLE, VIBRATION_SENSE,
		DETERRENCE, FALL_DAMAGE_IMMUNE, FALL_REDUCTION, POWDER_SNOW_WALK, LAVA_WALK, WALL_CLIMB,
		DAMAGE_OFFSET, WING_FLIGHT, SWIM_SPECIALIST, EFFECT_ATTACK, SHELL_DEFENSE, IMMUNE_EFFECTS, NATURAL_ARMOR,
		KNOCKBACK_RESISTANCE, PASSIVE_REGENERATION, ENCHANT_VULNERABILITY, HUNTING_INSTINCT, PACK_ALERT, ATTACK_KNOCKBACK,
		DOOR_HANDLING, WING_SLOW_FALL, STRONG_JUMP, ENDER_TELEPORT, BLOCK_ATTACHMENT, MAGIC_RESISTANCE,
		WITHER_SHIELD, LAUNCH_STRIKE, RIDEABLE, CARGO_CHEST, LOOT_PICKUP, SONIC_BOOM,
		HEAD_PROJECTILE, GUARDIAN_BEAM, SPELLCASTING, SELF_DESTRUCT, SNOW_TRAIL, POLLINATION,
		MILKABLE, WOOL_SHEARING, EGG_LAYING, SCUTE_SHEDDING);
}
