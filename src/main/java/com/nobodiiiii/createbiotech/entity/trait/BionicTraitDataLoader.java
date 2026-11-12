package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;

/** Loads every bionic trait from the single canonical data directory. */
public final class BionicTraitDataLoader extends SimpleJsonResourceReloadListener {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final BionicTraitDataLoader INSTANCE = new BionicTraitDataLoader();

	private BionicTraitDataLoader() {
		super(new Gson(), "bionic_traits");
	}

	@Override
	protected Map<ResourceLocation, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
		Map<ResourceLocation, JsonElement> resources = new HashMap<>();
		var converter = net.minecraft.resources.FileToIdConverter.json("bionic_traits");
		for (var entry : converter.listMatchingResources(manager).entrySet()) {
			try (var reader = entry.getValue().openAsReader()) {
				JsonElement json = com.google.gson.JsonParser.parseReader(reader);
				if (!json.isJsonObject()) throw new IllegalArgumentException("Expected a JSON object");
				resources.put(converter.fileToId(entry.getKey()), json);
			} catch (java.io.IOException | RuntimeException exception) {
				throw new IllegalArgumentException("Cannot load bionic trait resource " + entry.getKey(), exception);
			}
		}
		return resources;
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager,
		ProfilerFiller profiler) {
		Map<ResourceLocation, JsonElement> frozen = new HashMap<>();
		resources.forEach((id, json) -> frozen.put(id, json.deepCopy()));
		BionicTraitRulesPacket.validateResources(frozen);
		BionicTraitRegistry.replace(decode(frozen), frozen);
	}

	/** Pure loading shared with the client; publication only happens after the complete graph loads. */
	static Map<BionicTrait, BionicTraitData> decode(Map<ResourceLocation, JsonElement> resources) {
		Map<ResourceLocation, BionicTrait> definitions = new LinkedHashMap<>();
		for (BionicTrait trait : BionicTrait.builtIns()) definitions.put(trait.id(), trait);
		resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
			JsonObject root = object(entry.getValue());
			if (!root.has("schema_version")) return; // Legacy files extend built-in donor values.
			if (!root.get("schema_version").isJsonPrimitive()
				|| !root.getAsJsonPrimitive("schema_version").isNumber()
				|| root.get("schema_version").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) != 0)
				throw new IllegalArgumentException("Unsupported bionic trait schema in " + entry.getKey());
			definitions.put(entry.getKey(), readDefinition(entry.getKey(), root));
		});
		Map<BionicTrait, BionicTraitData> loaded = new LinkedHashMap<>();
		for (BionicTrait trait : definitions.values()) loaded.put(trait, read(trait, resources, definitions));
		Map<BionicTrait, Set<BionicTrait>> overrides = new LinkedHashMap<>();
		loaded.forEach((trait, data) -> overrides.put(trait, data.overrides()));
		Map<BionicTrait, Set<BionicTrait>> valid = BionicTraitOverrides.validate(overrides);
		loaded.replaceAll((trait, data) -> data.withOverrides(valid.get(trait)));
		return loaded;
	}

	private static BionicTrait readDefinition(ResourceLocation id, JsonObject root) {
		try {
			if (root.has("behaviors")) throw new IllegalArgumentException("Behavior components are not implemented in phase 1");
			Set<BionicTraitScope> scopes = java.util.EnumSet.noneOf(BionicTraitScope.class);
			for (JsonElement scope : root.getAsJsonArray("scopes"))
				scopes.add(BionicTraitScope.valueOf(scope.getAsString().toUpperCase(java.util.Locale.ROOT)));
			BionicTrait.ValueKind kind = BionicTrait.ValueKind.valueOf(root.get("value_kind").getAsString().toUpperCase(java.util.Locale.ROOT));
			JsonObject inheritance = root.getAsJsonObject("inheritance");
			BionicTraitType type = BionicTraitType.valueOf(inheritance.get("type").getAsString().toUpperCase(java.util.Locale.ROOT));
			BionicTrait builtin = BionicTrait.builtIn(id);
			if (builtin != null && kind != builtin.valueKind())
				throw new IllegalArgumentException("Built-in value kinds cannot change");
			if (type == BionicTraitType.SPECIAL && (builtin == null || !builtin.isHeadClassification())) throw new IllegalArgumentException("SPECIAL requires a built-in evaluator");
			double minimum = inheritance.has("min_coverage") ? inheritance.get("min_coverage").getAsDouble()
				: type == BionicTraitType.COVERAGE_THRESHOLD || type == BionicTraitType.SPECIAL || kind == BionicTrait.ValueKind.NUMBER ? 0.5d : 0;
			BionicTraitRule rule = new BionicTraitRule(type, minimum);
			if (kind == BionicTrait.ValueKind.NUMBER && (type != BionicTraitType.COVERAGE_SCALED || minimum <= 0))
				throw new IllegalArgumentException("Numeric traits require scaled inheritance and a positive volume budget");
			JsonObject carrier = object(root.get("carrier"));
			JsonObject display = object(root.get("display"));
			return new BionicTrait(id, scopes, rule, kind, builtin == null ? BionicTrait.Condition.NONE : builtin.condition(),
				new BionicTrait.Carrier(roles(carrier.get("roles")), roles(carrier.get("fallback")), roles(carrier.get("excluded"))),
				component(display.get("name")), component(display.get("description")));
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Invalid bionic trait definition " + id, exception);
		}
	}

	private static List<BionicAnatomyRole> roles(JsonElement json) {
		if (json == null) return List.of();
		List<BionicAnatomyRole> roles = new ArrayList<>();
		for (JsonElement role : json.getAsJsonArray())
			roles.add(BionicAnatomyRole.valueOf(role.getAsString().toUpperCase(java.util.Locale.ROOT)));
		return List.copyOf(roles);
	}

	private static net.minecraft.network.chat.Component component(JsonElement json) {
		return json == null ? null : net.minecraft.network.chat.ComponentSerialization.CODEC
			.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
	}

	private static BionicTraitData read(BionicTrait trait, Map<ResourceLocation, JsonElement> resources,
		Map<ResourceLocation, BionicTrait> definitions) {
		ResourceLocation primary = trait.id();
		JsonObject root = object(resources.get(primary));
		boolean automatic = trait.automaticDetection();
		BionicTraitRule rule = trait.rule();
		if (root.has("automatic_detection")) {
			Boolean value = booleanValue(root.get("automatic_detection"));
			if (value != null)
				automatic = value;
			else
				LOGGER.warn("Invalid automatic_detection in {}", primary);
		}
		if ((rule.type() == BionicTraitType.COVERAGE_THRESHOLD || rule.type() == BionicTraitType.SPECIAL
			|| trait.valueKind() == BionicTrait.ValueKind.NUMBER)
			&& root.has("min_coverage")) {
			Double value = numberValue(root.get("min_coverage"));
			if (value != null && value <= 1.0d
				&& (trait.valueKind() != BionicTrait.ValueKind.NUMBER || value > 0.0d))
				rule = new BionicTraitRule(rule.type(), value);
			else
				LOGGER.warn("Invalid min_coverage in {}; using {}", primary, rule.minCoverage());
		}
		Map<String, Double> parameters = new HashMap<>();
		object(root.get("parameters")).entrySet().forEach(entry -> {
			Double number = numberValue(entry.getValue());
			if (number != null)
				parameters.put(entry.getKey(), number);
			else
				LOGGER.warn("Invalid trait parameter {} in {}", entry.getKey(), primary);
		});
		for (String key : List.of("max_body_volume_per_wing_volume")) {
			if (!root.has(key))
				continue;
			Double number = numberValue(root.get(key));
			if (number != null && number > 0.0d)
				parameters.put(key, number);
			else
				LOGGER.warn("Invalid {} in {}", key, primary);
		}
		// The definition owns its settings; schema-less legacy files extend built-in donor values only.
		List<Map.Entry<ResourceLocation, JsonElement>> files = resources.entrySet().stream()
			.filter(entry -> entry.getKey().equals(primary) || trait.isBuiltIn()
				&& !object(entry.getValue()).has("schema_version")
				&& entry.getKey().getPath().equals(primary.getPath()))
			.sorted(java.util.Comparator.<Map.Entry<ResourceLocation, JsonElement>, Boolean>
				comparing(entry -> !entry.getKey().equals(primary)).thenComparing(Map.Entry.comparingByKey()))
			.toList();
		BionicDonorOverrides<Boolean> abilities = trait.valueKind() == BionicTrait.ValueKind.ABILITY
			? overrides(files, BionicTraitDataLoader::booleanValue) : BionicDonorOverrides.empty();
		BionicDonorOverrides<Double> numbers = trait.valueKind() == BionicTrait.ValueKind.NUMBER
			? overrides(files, BionicTraitDataLoader::numberValue) : BionicDonorOverrides.empty();
		BionicDonorOverrides<Map<ResourceLocation, Boolean>> effects = (trait.valueKind() == BionicTrait.ValueKind.EFFECT_SET
			|| trait.valueKind() == BionicTrait.ValueKind.ATTACK_EFFECT_SET
			|| trait.valueKind() == BionicTrait.ValueKind.SUBTRAIT_SET)
			? effectOverrides(files) : BionicDonorOverrides.empty();
		Map<ResourceLocation, BionicSubtrait> subtraits = new HashMap<>(BionicHeadTraits.variants(trait));
		subtraits.putAll(readSubtraits(root, primary));
		return new BionicTraitData(automatic, rule, abilities, numbers, effects,
			readCarriers(root, primary), parameters, readAttackEffects(root, primary), subtraits,
			readOverrides(root, trait, primary, definitions));
	}

	private static Set<BionicTrait> readOverrides(JsonObject root, BionicTrait source, ResourceLocation file,
		Map<ResourceLocation, BionicTrait> definitions) {
		Set<BionicTrait> targets = new LinkedHashSet<>();
		if (!root.has("overrides")) return Set.of();
		if (!root.get("overrides").isJsonArray()) {
			LOGGER.warn("Invalid overrides in {}; expected an array of trait IDs", file);
			return Set.of();
		}
		for (JsonElement element : root.getAsJsonArray("overrides")) {
			BionicTrait target = null;
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
				String text = element.getAsString();
				ResourceLocation id = ResourceLocation.tryParse(text.contains(":") ? text : CreateBiotech.MOD_ID + ":" + text);
				target = definitions.get(id);
			}
			if (target == null || source.equals(target))
				LOGGER.warn("Invalid trait override {} in {}", element, file);
			else
				targets.add(target);
		}
		return Set.copyOf(targets);
	}

	private static Map<ResourceLocation, BionicSubtrait> readSubtraits(JsonObject root, ResourceLocation file) {
		Map<ResourceLocation, BionicSubtrait> result = new HashMap<>();
		object(root.get("variants")).entrySet().forEach(entry -> {
			try {
				ResourceLocation id = ResourceLocation.parse(entry.getKey());
				JsonObject settings = entry.getValue().getAsJsonObject();
				List<BionicAnatomyRole> roles = new ArrayList<>();
				if (settings.has("roles"))
					for (JsonElement role : settings.getAsJsonArray("roles"))
						roles.add(BionicAnatomyRole.valueOf(role.getAsString()));
				Map<String, Double> parameters = new HashMap<>();
				object(settings.get("parameters")).entrySet().forEach(parameter -> {
					Double value = numberValue(parameter.getValue());
					if (value == null) throw new IllegalArgumentException("Invalid variant parameter");
					parameters.put(parameter.getKey(), value);
				});
				result.put(id, new BionicSubtrait(id, new BionicTrait.Carrier(roles, List.of()), parameters));
			} catch (RuntimeException exception) {
				LOGGER.warn("Invalid trait variant {} in {}", entry.getKey(), file);
			}
		});
		return Map.copyOf(result);
	}

	private static Map<ResourceLocation, BionicAttackEffect> readAttackEffects(JsonObject root, ResourceLocation file) {
		Map<ResourceLocation, BionicAttackEffect> result = new HashMap<>();
		object(root.get("effects")).entrySet().forEach(entry -> {
			try {
				ResourceLocation id = ResourceLocation.parse(entry.getKey());
				JsonObject settings = entry.getValue().getAsJsonObject();
				// Shared settings take precedence; older data packs may still configure each effect.
				JsonObject attack = root.has("attack_settings") ? root.getAsJsonObject("attack_settings") : settings;
				Double duration = numberValue(attack.get("duration_ticks"));
				Double amplifier = attack.has("amplifier") ? numberValue(attack.get("amplifier")) : 0.0d;
				Double chance = attack.has("chance") ? numberValue(attack.get("chance")) : 1.0d;
				Boolean unarmed = attack.has("unarmed_only") ? booleanValue(attack.get("unarmed_only")) : false;
				if (duration == null || duration < 1 || duration > 12000 || duration != Math.floor(duration)
					|| amplifier == null || amplifier > 255 || amplifier != Math.floor(amplifier)
					|| chance == null || chance > 1 || unarmed == null)
					throw new IllegalArgumentException("Invalid attack parameters");
				List<BionicAnatomyRole> roles = new ArrayList<>();
				if (settings.has("roles"))
					for (JsonElement role : settings.getAsJsonArray("roles"))
						roles.add(BionicAnatomyRole.valueOf(role.getAsString()));
				result.put(id, new BionicAttackEffect(id, duration.intValue(), amplifier.intValue(), chance,
					unarmed, new BionicTrait.Carrier(roles, List.of())));
			} catch (RuntimeException exception) {
				LOGGER.warn("Invalid attack effect {} in {}", entry.getKey(), file);
			}
		});
		return Map.copyOf(result);
	}

	private static <T> BionicDonorOverrides<T> overrides(List<Map.Entry<ResourceLocation, JsonElement>> files,
		Function<JsonElement, T> parse) {
		Map<ResourceLocation, T> entities = new HashMap<>();
		Map<ResourceLocation, T> tags = new HashMap<>();
		for (var file : files)
			for (var entry : object(object(file.getValue()).get("values")).entrySet()) {
				Selector selector = selector(entry.getKey());
				T value = parse.apply(entry.getValue());
				if (selector == null || value == null) {
					LOGGER.warn("Invalid trait donor value {} in {}", entry.getKey(), file.getKey());
					continue;
				}
				(selector.tag() ? tags : entities).put(selector.id(), value);
			}
		return freeze(entities, tags);
	}

	private static BionicDonorOverrides<Map<ResourceLocation, Boolean>> effectOverrides(
		List<Map.Entry<ResourceLocation, JsonElement>> files) {
		Map<ResourceLocation, Map<ResourceLocation, Boolean>> entities = new HashMap<>();
		Map<ResourceLocation, Map<ResourceLocation, Boolean>> tags = new HashMap<>();
		for (var file : files)
			for (var entry : object(object(file.getValue()).get("values")).entrySet()) {
				Selector selector = selector(entry.getKey());
				if (selector == null || !entry.getValue().isJsonObject()) {
					LOGGER.warn("Invalid effect donor {} in {}", entry.getKey(), file.getKey());
					continue;
				}
				Map<ResourceLocation, Boolean> toggles = (selector.tag() ? tags : entities)
					.computeIfAbsent(selector.id(), ignored -> new HashMap<>());
				for (var effect : entry.getValue().getAsJsonObject().entrySet()) {
					ResourceLocation id = ResourceLocation.tryParse(effect.getKey());
					Boolean value = booleanValue(effect.getValue());
					if (id != null && value != null)
						toggles.put(id, value);
					else
						LOGGER.warn("Invalid effect toggle {} in {}", effect.getKey(), file.getKey());
				}
			}
		entities.replaceAll((id, values) -> Map.copyOf(values));
		tags.replaceAll((id, values) -> Map.copyOf(values));
		return freeze(entities, tags);
	}

	private static <T> BionicDonorOverrides<T> freeze(Map<ResourceLocation, T> entities, Map<ResourceLocation, T> tags) {
		return new BionicDonorOverrides<>(entities, tags.entrySet().stream().sorted(Map.Entry.comparingByKey())
			.map(entry -> new BionicDonorOverrides.Tagged<>(TagKey.create(Registries.ENTITY_TYPE, entry.getKey()), entry.getValue()))
			.toList());
	}

	private static Map<ResourceLocation, Map<BionicAnatomyRole, Set<String>>> readCarriers(JsonObject root,
		ResourceLocation file) {
		Map<ResourceLocation, Map<BionicAnatomyRole, Set<String>>> result = new HashMap<>();
		for (var entity : object(root.get("carriers")).entrySet()) {
			ResourceLocation id = ResourceLocation.tryParse(entity.getKey());
			if (id == null || !entity.getValue().isJsonObject()) {
				LOGGER.warn("Invalid carrier entity {} in {}", entity.getKey(), file);
				continue;
			}
			EnumMap<BionicAnatomyRole, Set<String>> roles = new EnumMap<>(BionicAnatomyRole.class);
			for (var entry : entity.getValue().getAsJsonObject().entrySet()) {
				try {
					BionicAnatomyRole role = BionicAnatomyRole.valueOf(entry.getKey());
					if (!entry.getValue().isJsonArray())
						throw new IllegalArgumentException("Expected carrier list");
					Set<String> names = new HashSet<>();
					for (JsonElement element : entry.getValue().getAsJsonArray()) {
						String name = carrierName(element);
						if (name != null)
							names.add(name);
						else
							LOGGER.warn("Invalid carrier name in {} for {}", file, id);
					}
					roles.put(role, Set.copyOf(names));
				} catch (IllegalArgumentException exception) {
					LOGGER.warn("Invalid carrier role {} in {}", entry.getKey(), file);
				}
			}
			result.put(id, Map.copyOf(roles));
		}
		return Map.copyOf(result);
	}

	@Nullable
	private static String carrierName(JsonElement value) {
		try {
			if (!value.isJsonPrimitive())
				return null;
			if (value.getAsJsonPrimitive().isNumber()) {
				int cube = value.getAsBigDecimal().intValueExact();
				return cube >= 0 && cube < SurgicalAssembly.MAX_CUBES ? "#" + cube : null;
			}
			if (!value.getAsJsonPrimitive().isString())
				return null;
			String name = value.getAsString();
			if (name.matches("#[0-9]{1,9}")) {
				int cube = Integer.parseInt(name.substring(1));
				return cube < SurgicalAssembly.MAX_CUBES ? "#" + cube : null;
			}
			return BionicAnatomySnapshot.validPartName(name) ? name : null;
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static JsonObject object(@Nullable JsonElement element) {
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	@Nullable
	private static Boolean booleanValue(@Nullable JsonElement element) {
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
			? element.getAsBoolean() : null;
	}

	@Nullable
	private static Double numberValue(@Nullable JsonElement element) {
		try {
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
				return null;
			double value = element.getAsDouble();
			return Double.isFinite(value) && value >= 0.0d ? value : null;
		} catch (RuntimeException exception) {
			return null;
		}
	}

	@Nullable
	private static Selector selector(String text) {
		boolean tag = text.startsWith("#");
		ResourceLocation id = ResourceLocation.tryParse(tag ? text.substring(1) : text);
		return id == null ? null : new Selector(id, tag);
	}
	private record Selector(ResourceLocation id, boolean tag) {}
}
