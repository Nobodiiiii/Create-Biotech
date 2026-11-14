package com.nobodiiiii.createbiotech.entity.trait;

import java.util.*;
import com.google.gson.*;
import com.nobodiiiii.createbiotech.api.bionic.*;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Compiled gameplay content. Raw JSON uses the existing atomic catalog/reload/network boundary. */
public final class BionicGameplayRules {
	private static final Map<ResourceLocation, BionicTraitApi.Factory<BionicSpecialAttack>> ATTACKS = new HashMap<>();
	private static final Map<ResourceLocation, BionicTraitApi.Factory<BionicDonorDetector>> DETECTORS = new HashMap<>();
	private static final Map<ResourceLocation, BionicTraitApi.Factory<BionicRelationCondition>> RELATIONS = new HashMap<>();
	private static final Map<ResourceLocation, BionicTraitApi.Factory<BionicAnatomyEvaluator>> ANATOMY = new HashMap<>();
	private static boolean frozen;
	public static final BionicGameplayRules EMPTY = new BionicGameplayRules();
	final List<Relation> relations;
	final boolean replaceRelations;
	final Map<String, Output> outputs;
	final List<Cycle> production;
	final List<Interaction> interactions;
	final List<Equipment> equipment;
	final Anatomy anatomy;
	final BionicAnatomyEvaluator anatomyEvaluator;
	final BionicDonorDetector detector;
	final Map<ResourceLocation, BionicSpecialAttack> attacks;
	private BionicGameplayRules() {
		relations = List.of(); replaceRelations = false; outputs = Map.of(); production = List.of();
		interactions = List.of(); equipment = List.of(); anatomy = null; anatomyEvaluator = null; detector = null; attacks = Map.of();
	}
	private BionicGameplayRules(JsonObject root, boolean strict) {
		for (String key : root.keySet()) if (!Set.of("relations", "outputs", "production", "interactions", "equipment", "anatomy", "detector", "attacks").contains(key)) throw new IllegalArgumentException("Unknown gameplay field " + key);
		var relation = object(root.get("relations"));
		replaceRelations = bool(relation, "replace_native", false);
		relations = array(relation, "rules").stream().map(j -> Relation.parse(j.getAsJsonObject(), strict)).toList();
		Map<String, Output> parsedOutputs = new LinkedHashMap<>();
		object(root.get("outputs")).entrySet().forEach(e -> parsedOutputs.put(e.getKey(), Output.parse(e.getValue().getAsJsonObject())));
		outputs = Map.copyOf(parsedOutputs);
		production = array(root, "production").stream().map(j -> Cycle.parse(j.getAsJsonObject())).toList();
		interactions = array(root, "interactions").stream().map(j -> Interaction.parse(j.getAsJsonObject())).toList();
		if (production.stream().map(Cycle::id).distinct().count() != production.size() || interactions.stream().map(Interaction::id).distinct().count() != interactions.size()) throw new IllegalArgumentException("Duplicate gameplay rule ID");
		equipment = array(root, "equipment").stream().map(j -> Equipment.parse(j.getAsJsonObject())).toList();
		var anatomyJson = object(root.get("anatomy"));
		anatomy = root.has("anatomy") && !anatomyJson.has("type") ? Anatomy.parse(anatomyJson) : null;
		anatomyEvaluator = anatomyJson.has("type") ? compile(anatomyJson, ANATOMY, strict, (a,t,r) -> r.disabled(BionicTraitResult.InactiveReason.EXTENSION_CONDITION)) : null;
		detector = root.has("detector") ? compile(root.getAsJsonObject("detector"), DETECTORS, strict, d -> null) : null;
		Map<ResourceLocation, BionicSpecialAttack> parsedAttacks = new LinkedHashMap<>();
		object(root.get("attacks")).entrySet().forEach(e -> parsedAttacks.put(ResourceLocation.parse(e.getKey()),
			compile(e.getValue().getAsJsonObject(), ATTACKS, strict, c -> {})));
		attacks = Map.copyOf(parsedAttacks);
		if (outputs.size() > 64 || attacks.size() > 64) throw new IllegalArgumentException("Too many gameplay entries");
	}
	static BionicGameplayRules compile(JsonObject root, boolean strict) {
		frozen = true;
		return root.has("gameplay") ? new BionicGameplayRules(root.getAsJsonObject("gameplay"), strict) : EMPTY;
	}
	public static BionicGameplayRules get(net.minecraft.world.level.Level level, BionicTrait trait) {
		return BionicTraitRegistry.snapshot(level).get(trait).extensions().gameplay();
	}
	public static synchronized void registerAnatomy(ResourceLocation id, BionicTraitApi.Factory<BionicAnatomyEvaluator> factory) { register(ANATOMY, id, factory); }
	public static synchronized void registerAttack(ResourceLocation id, BionicTraitApi.Factory<BionicSpecialAttack> factory) { register(ATTACKS, id, factory); }
	public static synchronized void registerDetector(ResourceLocation id, BionicTraitApi.Factory<BionicDonorDetector> factory) { register(DETECTORS, id, factory); }
	public static synchronized void registerRelation(ResourceLocation id, BionicTraitApi.Factory<BionicRelationCondition> factory) { register(RELATIONS, id, factory); }
	private static <T> void register(Map<ResourceLocation, T> types, ResourceLocation id, T factory) {
		if (frozen || types.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(factory)) != null)
			throw new IllegalStateException("Late or duplicate bionic gameplay type: " + id);
	}
	private static <T> T compile(JsonObject json, Map<ResourceLocation, BionicTraitApi.Factory<T>> types, boolean strict, T fallback) {
		ResourceLocation id = ResourceLocation.parse(json.get("type").getAsString());
		var factory = types.get(id);
		if (factory == null) {
			if (strict) throw new IllegalArgumentException("Unknown bionic gameplay type: " + id);
			return fallback;
		}
		return Objects.requireNonNull(factory.parse(json.deepCopy()));
	}
	static {
		registerDetector(id("attribute"), json -> {
			var attribute = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(json.get("attribute").getAsString())).orElseThrow();
			double scale = number(json, "scale", -1024, 1024, 1), offset = number(json, "offset", -4096, 4096, 0);
			return donor -> donor.getAttribute(attribute) == null ? null : donor.getAttribute(attribute).getBaseValue() * scale + offset;
		});
		registerRelation(id("environment"), json -> {
			Boolean water = nullableBool(json, "target_in_water"), baby = nullableBool(json, "target_baby"), tamed = nullableBool(json, "tamed");
			boolean sight = bool(json, "line_of_sight", false);
			double light = number(json, "maximum_light", 0, 1, 1);
			return (entity, donor, target) -> (!sight || entity.hasLineOfSight(target))
				&& (water == null || water == target.isInWaterOrBubble()) && (baby == null || baby == target.isBaby())
				&& (tamed == null || tamed == (entity.getBionicOwnerId() != null)) && entity.getLightLevelDependentMagicValue() <= light;
		});
		registerAttack(id("projectile"), json -> {
			var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(json.get("entity").getAsString())).orElseThrow();
			float speed = (float) number(json, "speed", 0.01, 20, 1.5), spread = (float) number(json, "inaccuracy", 0, 180, 0);
			int count = integer(json, "count", 1, 64, 1);
			return context -> {
				for (int i = 0; i < count; i++) {
					Entity created = type.create(context.entity().level());
					if (!(created instanceof Projectile projectile)) throw new IllegalArgumentException("Attack entity is not a projectile: " + type);
					Vec3 aim = context.target().getBoundingBox().getCenter().subtract(context.origin());
					projectile.setOwner(context.entity()); projectile.setPos(context.origin());
					projectile.shoot(aim.x, aim.y, aim.z, speed, spread);
					BionicCombatEvents.mark(projectile, context.entity()); context.entity().level().addFreshEntity(projectile);
				}
			};
		});
		registerAttack(id("direct_damage"), json -> {
			float damage = (float) number(json, "damage", 0, 2048, 6);
			return context -> context.target().hurt(context.entity().damageSources().mobAttack(context.entity()), damage);
		});
	}
	static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("create_biotech", path); }
	static JsonObject object(JsonElement json) { return json == null ? new JsonObject() : json.getAsJsonObject(); }
	static List<JsonElement> array(JsonObject root, String key) {
		if (!root.has(key)) return List.of();
		var values = root.getAsJsonArray(key);
		if (values.size() > 64) throw new IllegalArgumentException("Too many " + key);
		return List.copyOf(values.asList());
	}
	static String string(JsonObject root, String key, String fallback) { return root.has(key) ? root.get(key).getAsString() : fallback; }
	static boolean bool(JsonObject root, String key, boolean fallback) { return root.has(key) ? Objects.requireNonNull(nullableBool(root, key)) : fallback; }
	static Boolean nullableBool(JsonObject root, String key) {
		if (!root.has(key)) return null;
		if (!root.get(key).isJsonPrimitive() || !root.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException("Expected boolean " + key);
		return root.get(key).getAsBoolean();
	}
	static double number(JsonObject json, String key, double min, double max, double fallback) { return BionicExtensions.number(json, key, min, max, fallback); }
	static int integer(JsonObject json, String key, int min, int max, int fallback) { return BionicExtensions.integer(json, key, min, max, fallback); }
	public record Selector(ResourceLocation id, boolean tag) {
		static Selector parse(String value) { return new Selector(ResourceLocation.parse(value.startsWith("#") ? value.substring(1) : value), value.startsWith("#")); }
		public boolean entity(EntityType<?> type) { return tag ? type.is(TagKey.create(Registries.ENTITY_TYPE, id)) : BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(id); }
		public boolean entity(ResourceLocation entity) { return BuiltInRegistries.ENTITY_TYPE.getOptional(entity).map(this::entity).orElse(false); }
		public boolean item(ItemStack stack) { return tag ? stack.is(TagKey.create(Registries.ITEM, id)) : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id); }
	}
	static List<Selector> selectors(JsonObject root, String key) { return array(root, key).stream().map(j -> Selector.parse(j.getAsString())).toList(); }
	public record Relation(List<Selector> donors, List<Selector> targets, double range, double speed,
		int cooldown, BionicRelationCondition condition) {
		static Relation parse(JsonObject json, boolean strict) {
			var targets = selectors(json, "targets"); if (targets.isEmpty()) throw new IllegalArgumentException("Empty relation targets");
			return new Relation(selectors(json, "donors"), targets, number(json, "range", 0, 128, 16), number(json, "speed", 0.01, 4, 1.2),
				integer(json, "cooldown_ticks", 0, 24000, 0), json.has("condition") ? compile(json.getAsJsonObject("condition"), RELATIONS, strict, (e,d,t) -> false) : (e,d,t) -> true);
		}
		boolean donor(ResourceLocation donor) { return donors.isEmpty() || donors.stream().anyMatch(s -> s.entity(donor)); }
		public boolean matches(SlimeBionicEntity entity, ResourceLocation donor, LivingEntity target) {
			return donor(donor) && targets.stream().anyMatch(s -> s.entity(target.getType()))
				&& entity.distanceToSqr(target) <= range * range && condition.matches(entity, donor, target);
		}
	}
	record Output(Item item, ResourceLocation loot, int min, int max) {
		static Output parse(JsonObject json) {
			if (json.has("item") == json.has("loot_table")) throw new IllegalArgumentException("Output requires exactly one item or loot_table");
			Item item = json.has("item") ? BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(json.get("item").getAsString())).orElseThrow() : null;
			if (item == Items.AIR) throw new IllegalArgumentException("Output is air");
			int min = integer(json, "min", 0, 64, 1), max = integer(json, "max", min, 64, min);
			return new Output(item, json.has("loot_table") ? ResourceLocation.parse(json.get("loot_table").getAsString()) : null, min, max);
		}
	}
	record Cycle(String id, int min, int max, BionicAnatomyRole organ, Output output) {
		static Cycle parse(JsonObject json) {
			String id = json.get("id").getAsString(); ResourceLocation.parse(id);
			int min = integer(json, "min_interval_ticks", 20, 24000, 6000);
			return new Cycle(id, min, integer(json, "max_interval_ticks", min, 24000, min),
				json.has("per_organ") ? BionicAnatomyRole.parse(json.get("per_organ").getAsString()) : null, Output.parse(json));
		}
	}
	record Interaction(String id, Selector ingredient, int consume, int damage, int cooldown, Output output, boolean ownerOnly) {
		static Interaction parse(JsonObject json) {
			String id = json.get("id").getAsString(); ResourceLocation.parse(id);
			return new Interaction(id, Selector.parse(json.get("ingredient").getAsString()), integer(json, "consume", 0, 64, 1),
				integer(json, "tool_damage", 0, 4096, 0), integer(json, "cooldown_ticks", 0, 24000, 0), Output.parse(json), bool(json, "owner_only", true));
		}
	}
	public record Equipment(List<Selector> donors, Selector tameItem, Selector chestItem, Selector saddleItem,
		int capacity, int seats, double chance, List<Vec3> seatOffsets) {
		static Equipment parse(JsonObject json) {
			List<Vec3> offsets = array(json, "seat_offsets").stream().map(j -> {
				var coords = j.getAsJsonArray(); if (coords.size() != 3) throw new IllegalArgumentException("Seat offset requires x,y,z");
				double x = coords.get(0).getAsDouble(), y = coords.get(1).getAsDouble(), z = coords.get(2).getAsDouble();
				if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) > 16) throw new IllegalArgumentException("Invalid seat offset");
				return new Vec3(x,y,z);
			}).toList();
			int seats = json.has("seats") ? integer(json, "seats", 1, 16, 1) : offsets.isEmpty() ? -1 : offsets.size();
			if (seats > 16 || !offsets.isEmpty() && offsets.size() != seats || seats > 2 && offsets.isEmpty()) throw new IllegalArgumentException("seat_offsets must describe 1 to 16 custom seats");
			return new Equipment(selectors(json, "donors"), selector(json, "tame_item"), selector(json, "chest_item"), selector(json, "saddle_item"),
				json.has("capacity") ? integer(json, "capacity", 0, 54, 15) : -1, seats, json.has("taming_chance") ? number(json, "taming_chance", 0, 1, 1) : -1, offsets);
		}
		private static Selector selector(JsonObject json, String key) { return json.has(key) ? Selector.parse(json.get(key).getAsString()) : null; }
		boolean matches(ResourceLocation donor) { return donors.isEmpty() || donors.stream().anyMatch(s -> s.entity(donor)); }
	}
	public Equipment equipment(ResourceLocation donor) {
		return equipment.stream().filter(e -> e.matches(donor)).reduce((a,b) -> b).orElse(null);
	}
	Double probe(LivingEntity donor) {
		try { return detector.detect(donor); }
		catch (RuntimeException exception) { com.mojang.logging.LogUtils.getLogger().warn("Bionic donor detector failed for {}", donor.getType(), exception); return null; }
	}
	public boolean replacesRelations() { return replaceRelations; }
	public List<Relation> relations() { return relations; }
	public Set<ResourceLocation> relationTargets(ResourceLocation donor) {
		Set<ResourceLocation> result = new HashSet<>();
		relations.stream().filter(r -> r.donor(donor)).forEach(r -> r.targets.forEach(s -> {
			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) if (s.entity(type)) result.add(BuiltInRegistries.ENTITY_TYPE.getKey(type));
		}));
		return Set.copyOf(result);
	}
	public boolean hasAttack(ResourceLocation variant) { return attacks.containsKey(variant); }
	public BionicSpecialAttack attack(ResourceLocation variant) { return attacks.get(variant); }
	Set<BionicAnatomyRole> referencedParts() {
		Set<BionicAnatomyRole> result = new HashSet<>();
		production.stream().map(Cycle::organ).filter(Objects::nonNull).forEach(result::add);
		if (anatomy != null) anatomy.alternatives.forEach(result::addAll);
		return result;
	}
	record Anatomy(List<List<BionicAnatomyRole>> alternatives, String grouping, boolean replaceNative) {
		static Anatomy parse(JsonObject root) {
			List<List<BionicAnatomyRole>> alternatives = array(root, "any_of").stream().map(j -> {
				var values = j.getAsJsonArray(); if (values.isEmpty() || values.size() > 64) throw new IllegalArgumentException("Invalid anatomy all_of");
				return values.asList().stream().map(v -> BionicAnatomyRole.parse(v.getAsString())).distinct().toList();
			}).toList();
			if (alternatives.isEmpty()) throw new IllegalArgumentException("Empty anatomy any_of");
			String grouping = string(root, "group_by", "species");
			if (!Set.of("species", "source", "limb", "body").contains(grouping)) throw new IllegalArgumentException("Invalid anatomy group_by");
			return new Anatomy(alternatives, grouping, bool(root, "replace_native", false));
		}
	}
}
