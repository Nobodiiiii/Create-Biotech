package com.nobodiiiii.createbiotech.entity.trait;

import java.util.*;
import com.google.gson.*;
import com.nobodiiiii.createbiotech.api.bionic.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/** Implementation of API type registration and compiled, immutable extension components. */
public final class BionicExtensions {
	private static final Map<ResourceLocation, BionicTraitApi.Factory<BionicBehavior>> BEHAVIORS = new LinkedHashMap<>();
	private static final Map<ResourceLocation, BionicTraitApi.Factory<BionicCondition>> CONDITIONS = new LinkedHashMap<>();
	private static final Map<ResourceLocation, BionicTraitApi.Factory<BionicCarrierSelector>> SELECTORS = new LinkedHashMap<>();
	private static final Map<ResourceLocation, JsonObject> DEFAULTS = new LinkedHashMap<>();
	private static boolean frozen;
	private BionicExtensions() {}

	static {
		registerBehavior(id("attribute"), json -> {
			var attribute = BuiltInRegistries.ATTRIBUTE.getHolder(requiredId(json, "attribute")).orElseThrow();
			double amount = number(json, "amount", -1024, 1024, 0);
			var operation = AttributeModifier.Operation.valueOf(string(json, "operation", "add_value").toUpperCase(Locale.ROOT));
			String scale = string(json, "scale", "strength");
			if (!Set.of("strength", "value", "none").contains(scale)) throw new IllegalArgumentException("Invalid attribute scale");
			return new BionicBehavior() {
				@Override public void tick(Context c) {
					var instance = c.entity().getAttribute(attribute);
					if (instance == null) return;
					double factor = scale.equals("value") ? c.result().value() : scale.equals("strength") ? c.result().strength() : 1;
					double value = amount * factor;
					if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite attribute amount");
					var modifier = new AttributeModifier(c.modifierId(), value, operation);
					if (!modifier.equals(instance.getModifier(c.modifierId()))) {
						instance.removeModifier(c.modifierId());
						instance.addTransientModifier(modifier);
					}
				}
				@Override public void removed(Context c) {
					var instance = c.entity().getAttribute(attribute);
					if (instance != null) instance.removeModifier(c.modifierId());
				}
			};
		});
		for (boolean attack : new boolean[]{false, true}) registerBehavior(id(attack ? "on_hit_effect" : "status_effect"), json -> {
			var effect = BuiltInRegistries.MOB_EFFECT.getHolder(requiredId(json, "effect")).orElseThrow();
			int duration = integer(json, "duration_ticks", 1, 24000, 100);
			int amplifier = integer(json, "amplifier", 0, 255, 0);
			int interval = integer(json, "interval_ticks", 1, 24000, 20);
			double chance = number(json, "chance", 0, 1, 1);
			return new BionicBehavior() {
				@Override public void tick(Context c) {
					if (!attack && c.every(interval) && c.entity().getRandom().nextDouble() < chance)
						c.entity().addEffect(new MobEffectInstance(effect, duration, amplifier), c.entity());
				}
				@Override public void melee(Context c, net.minecraft.world.entity.LivingEntity target,
					Set<com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember> arm) {
					if (attack && c.uses(arm) && c.entity().getRandom().nextDouble() < chance)
						target.addEffect(new MobEffectInstance(effect, duration, amplifier), c.entity());
				}
			};
		});
		registerBehavior(id("damage_multiplier"), json -> {
			double multiplier = number(json, "multiplier", 0, 100, 1);
			ResourceLocation damage = json.has("damage_type") ? requiredId(json, "damage_type") : null;
			var tag = json.has("damage_tag") ? TagKey.create(Registries.DAMAGE_TYPE, requiredId(json, "damage_tag")) : null;
			return new BionicBehavior() {
				@Override public float incomingDamage(Context c, net.minecraft.world.damagesource.DamageSource source, float amount) {
					if (damage != null && !source.typeHolder().is(net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE, damage))
						|| tag != null && !source.is(tag)) return amount;
					return (float) (amount * (1 + (multiplier - 1) * c.result().strength()));
				}
			};
		});
		registerBehavior(id("regeneration"), json -> {
			int interval = integer(json, "interval_ticks", 1, 24000, 100);
			double amount = number(json, "amount", 0, 1024, 1);
			return new BionicBehavior() {
				@Override public void tick(Context c) { if (c.every(interval)) c.entity().heal((float)(amount * c.result().strength())); }
			};
		});
		registerBehavior(id("effect_immunity"), json -> new BionicBehavior() {
			@Override public boolean allowsEffect(Context c, ResourceLocation effect) {
				return !c.result().immuneEffects().contains(effect);
			}
		});
		registerBehavior(id("inherited_attack_effects"), json -> new BionicBehavior() {
			@Override public void melee(Context c, net.minecraft.world.entity.LivingEntity target,
				Set<com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember> arm) {
				for (var entry : c.result().attackEffects().entrySet()) {
					var effect = entry.getKey();
					if (effect.unarmedOnly() && c.entity().isAttackActionWeapon()
						|| arm.stream().noneMatch(entry.getValue()::contains)
						|| c.entity().getRandom().nextDouble() >= effect.chance()) continue;
					BuiltInRegistries.MOB_EFFECT.getHolder(effect.effect()).ifPresent(holder -> target.addEffect(
						new MobEffectInstance(holder, effect.durationTicks(), effect.amplifier()), c.entity()));
				}
			}
		});
		registerBehavior(id("item_production"), json -> {
			var item = BuiltInRegistries.ITEM.getOptional(requiredId(json, "item")).orElseThrow();
			if (item == net.minecraft.world.item.Items.AIR) throw new IllegalArgumentException("Production item is air");
			int interval = integer(json, "interval_ticks", 20, 24000, 6000);
			int count = integer(json, "count", 1, 64, 1);
			return new BionicBehavior() {
				@Override public void tick(Context c) { if (c.every(interval) && !c.entity().isNoAi()) c.entity().spawnAtLocation(new ItemStack(item, count)); }
			};
		});
		registerCondition(id("body_volume"), json -> {
			double min = number(json, "min", 0, Double.MAX_VALUE, 0);
			double max = number(json, "max", min, Double.MAX_VALUE, Double.MAX_VALUE);
			return c -> c.assembly().hasBodyVolume() && c.assembly().bodyVolume() >= min && c.assembly().bodyVolume() <= max;
		});
		registerCondition(id("requires_trait"), json -> {
			var required = BionicTrait.reference(requiredId(json, "trait"));
			return c -> c.covered().has(required);
		});
		registerCondition(id("source_count"), json -> {
			int min = integer(json, "min", 1, 4096, 1);
			return c -> c.result().members().stream().map(m -> m.source()).distinct().count() >= min;
		});
		registerSelector(id("parts"), json -> {
			Set<String> names = new HashSet<>();
			for (var value : json.getAsJsonArray("names")) names.add(value.getAsString());
			if (names.isEmpty()) throw new IllegalArgumentException("Empty parts selector");
			return source -> {
				var cubes = new BitSet();
				source.anatomy().parts().forEach((cube, labels) -> { if (labels.stream().anyMatch(names::contains)) cubes.set(cube); });
				return cubes;
			};
		});
	}

	public static synchronized void registerBehavior(ResourceLocation id, BionicTraitApi.Factory<BionicBehavior> factory) { register(BEHAVIORS, id, factory); }
	public static synchronized void registerCondition(ResourceLocation id, BionicTraitApi.Factory<BionicCondition> factory) { register(CONDITIONS, id, factory); }
	public static synchronized void registerSelector(ResourceLocation id, BionicTraitApi.Factory<BionicCarrierSelector> factory) { register(SELECTORS, id, factory); }
	public static synchronized void registerDefault(ResourceLocation id, JsonObject json) {
		if (id.getNamespace().equals("create_biotech") && id.getPath().startsWith("__extension/"))
			throw new IllegalArgumentException("Reserved bionic trait ID: " + id);
		putDefault(id, json);
	}
	public static synchronized void registerAnatomyDefault(ResourceLocation id, JsonObject json) {
		putDefault(BionicTraitResources.transportId("bionic_anatomy", id), json);
	}
	private static void putDefault(ResourceLocation id, JsonObject json) {
		if (frozen || DEFAULTS.containsKey(id)) throw new IllegalStateException("Late or duplicate bionic definition: " + id);
		DEFAULTS.put(id, json.deepCopy());
	}
	private static <T> void register(Map<ResourceLocation, T> map, ResourceLocation id, T value) {
		if (frozen || map.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(value)) != null)
			throw new IllegalStateException("Late or duplicate bionic extension type: " + id);
	}
	static synchronized void addDefaults(Map<ResourceLocation, JsonElement> resources) {
		frozen = true;
		DEFAULTS.forEach((id, json) -> resources.putIfAbsent(id, json.deepCopy()));
	}
	static Compiled compile(JsonObject root, boolean strict) {
		return new Compiled(parse(root.get("behaviors"), BEHAVIORS, strict, () -> new BionicBehavior() {}),
			parse(root.get("conditions"), CONDITIONS, strict, () -> c -> false),
			parse(object(root.get("carrier")).get("selectors"), SELECTORS, strict, () -> source -> new BitSet()), BionicGameplayRules.compile(root, strict));
	}
	private static <T> List<T> parse(JsonElement json, Map<ResourceLocation, BionicTraitApi.Factory<T>> types,
		boolean strict, java.util.function.Supplier<T> unknown) {
		if (json == null) return List.of();
		if (!json.isJsonArray() || json.getAsJsonArray().size() > 64) throw new IllegalArgumentException("Expected at most 64 components");
		List<T> values = new ArrayList<>();
		for (var element : json.getAsJsonArray()) {
			var config = element.getAsJsonObject();
			var type = requiredId(config, "type");
			var factory = types.get(type);
			if (factory == null && strict) throw new IllegalArgumentException("Unknown bionic component type: " + type);
			values.add(factory == null ? unknown.get() : Objects.requireNonNull(factory.parse(config.deepCopy())));
		}
		return List.copyOf(values);
	}
	record Compiled(List<BionicBehavior> behaviors, List<BionicCondition> conditions, List<BionicCarrierSelector> selectors, BionicGameplayRules gameplay) {
		static final Compiled EMPTY = new Compiled(List.of(), List.of(), List.of(), BionicGameplayRules.EMPTY);
	}
	static JsonObject object(JsonElement json) { return json == null ? new JsonObject() : json.getAsJsonObject(); }
	private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("create_biotech", path); }
	public static ResourceLocation requiredId(JsonObject json, String key) { return ResourceLocation.parse(json.get(key).getAsString()); }
	public static String string(JsonObject json, String key, String fallback) { return json.has(key) ? json.get(key).getAsString() : fallback; }
	public static double number(JsonObject json, String key, double min, double max, double fallback) {
		double value = json.has(key) ? json.get(key).getAsDouble() : fallback;
		if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid " + key);
		return value;
	}
	public static int integer(JsonObject json, String key, int min, int max, int fallback) {
		double value = number(json, key, min, max, fallback);
		if (value != Math.floor(value)) throw new IllegalArgumentException("Expected integer " + key);
		return (int)value;
	}
}
