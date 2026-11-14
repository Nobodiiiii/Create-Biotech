package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
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
				ResourceLocation id = converter.fileToId(entry.getKey());
				if (id.getNamespace().equals("create_biotech") && id.getPath().startsWith("__extension/"))
					throw new IllegalArgumentException("Reserved bionic trait ID: " + id);
				resources.put(id, json);
			} catch (java.io.IOException | RuntimeException exception) {
				throw new IllegalArgumentException("Cannot load bionic trait resource " + entry.getKey(), exception);
			}
		}
		for (String directory : List.of("bionic_trait_bindings", "bionic_trait_modifiers", "bionic_parts", "bionic_anatomy")) {
			var extension = net.minecraft.resources.FileToIdConverter.json(directory);
			extension.listMatchingResources(manager).forEach((file, resource) -> {
				try (var reader = resource.openAsReader()) {
					var json = com.google.gson.JsonParser.parseReader(reader);
					if (!json.isJsonObject()) throw new IllegalArgumentException("Expected extension object");
					resources.put(BionicTraitResources.transportId(directory, extension.fileToId(file)), json);
				} catch (java.io.IOException exception) { throw new IllegalArgumentException("Cannot load " + file, exception); }
			});
		}
		BionicExtensions.addDefaults(resources);
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
	static Catalog decode(Map<ResourceLocation, JsonElement> resources) {
		return decode(resources, true);
	}
	static Catalog decode(Map<ResourceLocation, JsonElement> raw, boolean strict) {
		Map<BionicAnatomyRole, BionicPartDefinition> parts = new HashMap<>(BionicPartDefinition.decode(raw));
		Map<ResourceLocation, JsonElement> resources = BionicTraitResources.expand(raw);
		resources.forEach((id, json) -> compileLocalParts(id, json.getAsJsonObject(), parts));
		if (parts.size() > BionicPartDefinition.MAX_PARTS) throw new IllegalArgumentException("Too many bionic parts");
		Map<ResourceLocation, BionicTrait> definitions = new LinkedHashMap<>();
		for (BionicTrait trait : BionicTrait.builtIns())
			if (!object(resources.get(trait.id())).has("disabled") || !object(resources.get(trait.id())).get("disabled").getAsBoolean())
				definitions.put(trait.id(), trait);
		resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
			JsonObject root = object(entry.getValue());
			if (root.has("disabled") && root.get("disabled").getAsBoolean()) return;
			if (!root.has("schema_version")) {
				// Built-in IDs can override settings without repeating their definition.
				if (BionicTrait.builtIn(entry.getKey()) == null)
					throw new IllegalArgumentException("Missing bionic trait schema in " + entry.getKey());
				return;
			}
			if (!root.get("schema_version").isJsonPrimitive()
				|| !root.getAsJsonPrimitive("schema_version").isNumber()
				|| root.get("schema_version").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) != 0)
				throw new IllegalArgumentException("Unsupported bionic trait schema in " + entry.getKey());
			definitions.put(entry.getKey(), readDefinition(entry.getKey(), root));
		});
		Map<BionicTrait, BionicTraitData> loaded = new LinkedHashMap<>();
		for (BionicTrait trait : definitions.values()) {
			try { loaded.put(trait, read(trait, resources, definitions, parts, strict)); }
			catch (RuntimeException exception) { throw new IllegalArgumentException("Cannot load bionic trait " + trait.id(), exception); }
		}
		Map<BionicTrait, Set<BionicTrait>> overrides = new LinkedHashMap<>();
		loaded.forEach((trait, data) -> overrides.put(trait, data.overrides()));
		Map<BionicTrait, Set<BionicTrait>> valid = BionicTraitOverrides.validate(overrides);
		loaded.replaceAll((trait, data) -> data.withOverrides(valid.get(trait)));
		loaded.forEach((trait, data) -> {
			for (var variant : data.extensions().gameplay().attacks.keySet())
				if (!data.subtraits().containsKey(variant) && !(trait.equals(BionicTrait.SONIC_BOOM) && variant.equals(ResourceLocation.withDefaultNamespace("sonic_boom"))))
					throw new IllegalArgumentException("Attack references undefined variant " + variant + " in " + trait);
			if (data.extensions().gameplay().detector != null && trait.valueKind() != BionicTrait.ValueKind.ABILITY && trait.valueKind() != BionicTrait.ValueKind.NUMBER)
				throw new IllegalArgumentException("Donor detector requires ability or number: " + trait);
			Set<BionicAnatomyRole> referenced = new HashSet<>();
			collectParts(trait.carrier(), referenced);
			referenced.addAll(data.extensions().gameplay().referencedParts());
			data.carriers().values().forEach(roles -> referenced.addAll(roles.keySet()));
			data.subtraits().values().forEach(variant -> collectParts(variant.carrier(), referenced));
			data.attackEffects().values().forEach(effect -> collectParts(effect.carrier(), referenced));
			BionicTraitConditions.required(trait).forEach(carrier -> collectParts(carrier, referenced));
			if (referenced.size() > BionicTraitDonorsPacket.MAX_CARRIER_PARTS)
				throw new IllegalArgumentException("Too many bionic parts referenced by trait " + trait.id());
			referenced.forEach(role -> requirePart(role, parts, trait.id()));
		});
		return new Catalog(Map.copyOf(loaded), Map.copyOf(parts), BionicAnatomyRegistry.decode(raw, parts));
	}

	/** Inline parts are scoped to their trait; only their generated ID and text enter the catalog. */
	private static void compileLocalParts(ResourceLocation trait, JsonObject root, Map<BionicAnatomyRole, BionicPartDefinition> parts) {
		if (root.has("disabled") && root.get("disabled").getAsBoolean()) return;
		if (root.has("parts") && !root.get("parts").isJsonObject()) throw new IllegalArgumentException("Expected parts object in " + trait);
		JsonObject local = object(root.get("parts"));
		for (var entry : local.entrySet()) {
			BionicPartDefinition part = BionicPartDefinition.local(trait, entry.getKey(), entry.getValue().getAsJsonObject());
			if (parts.putIfAbsent(part.role(), part) != null) throw new IllegalArgumentException("Duplicate inline part " + part.role());
			JsonObject carriers = root.has("carriers") ? root.getAsJsonObject("carriers") : new JsonObject();
			object(entry.getValue().getAsJsonObject().get("bindings")).entrySet().forEach(binding -> {
				ResourceLocation.parse(binding.getKey());
				JsonObject roles = carriers.has(binding.getKey()) ? carriers.getAsJsonObject(binding.getKey()) : new JsonObject();
				if (!roles.has("#" + entry.getKey()) && !roles.has(part.role().toString())) roles.add(part.role().toString(), binding.getValue().deepCopy());
				carriers.add(binding.getKey(), roles);
			});
			root.add("carriers", carriers);
		}
		JsonObject gameplay = object(root.get("gameplay"));
		JsonObject anatomy = object(gameplay.get("anatomy"));
		if (anatomy.has("any_of")) for (var branch : anatomy.getAsJsonArray("any_of")) {
			var values = branch.getAsJsonArray();
			for (int i = 0; i < values.size(); i++) values.set(i, new com.google.gson.JsonPrimitive(localReference(trait, values.get(i).getAsString(), local)));
		}
		if (gameplay.has("production")) for (var value : gameplay.getAsJsonArray("production")) {
			var cycle = value.getAsJsonObject();
			if (cycle.has("per_organ")) cycle.addProperty("per_organ", localReference(trait, cycle.get("per_organ").getAsString(), local));
		}
		normalizeCarrier(trait, object(root.get("carrier")), local);
		for (String collection : List.of("variants", "effects"))
			object(root.get(collection)).entrySet().forEach(entry -> normalizeCarrier(trait, entry.getValue().getAsJsonObject(), local));
		object(root.get("carriers")).entrySet().forEach(entity -> {
			JsonObject roles = entity.getValue().getAsJsonObject();
			for (String key : List.copyOf(roles.keySet())) if (key.startsWith("#")) {
				String resolved = localReference(trait, key, local);
				if (roles.has(resolved)) throw new IllegalArgumentException("Duplicate local carrier " + resolved);
				roles.add(resolved, roles.remove(key));
			}
		});
	}
	private static void normalizeCarrier(ResourceLocation trait, JsonObject carrier, JsonObject local) {
		for (String key : List.of("roles", "fallback", "excluded")) if (carrier.has(key)) {
			com.google.gson.JsonArray normalized = new com.google.gson.JsonArray();
			for (JsonElement value : carrier.getAsJsonArray(key)) normalized.add(localReference(trait, value.getAsString(), local));
			carrier.add(key, normalized);
		}
	}
	private static String localReference(ResourceLocation trait, String value, JsonObject local) {
		if (!value.startsWith("#")) return value;
		String key = value.substring(1);
		if (!local.has(key)) throw new IllegalArgumentException("Undefined inline part " + value + " in " + trait);
		return BionicPartDefinition.localRole(trait, key).toString();
	}

	record Catalog(Map<BionicTrait, BionicTraitData> traits, Map<BionicAnatomyRole, BionicPartDefinition> parts,
		Map<ResourceLocation, List<BionicAnatomyRegistry.Template>> templates) {}

	private static void collectParts(BionicTrait.Carrier carrier, Set<BionicAnatomyRole> parts) {
		parts.addAll(carrier.roles());
		parts.addAll(carrier.fallback());
		parts.addAll(carrier.excluded());
	}

	private static void requirePart(BionicAnatomyRole role, Map<BionicAnatomyRole, BionicPartDefinition> parts,
		ResourceLocation trait) {
		if (!parts.containsKey(role) || parts.get(role).owner() != null && !parts.get(role).owner().equals(trait)) throw new IllegalArgumentException("Undefined bionic part " + role + " in trait " + trait);
	}

	private static BionicTrait readDefinition(ResourceLocation id, JsonObject root) {
		try {
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
			double minimum = kind == BionicTrait.ValueKind.NUMBER ? 0
				: inheritance.has("min_coverage") ? inheritance.get("min_coverage").getAsDouble()
				: type == BionicTraitType.COVERAGE_THRESHOLD || type == BionicTraitType.SPECIAL ? 0.5d : 0;
			BionicTraitRule rule = new BionicTraitRule(type, minimum);
			if (kind == BionicTrait.ValueKind.NUMBER && type != BionicTraitType.COVERAGE_SCALED)
				throw new IllegalArgumentException("Numeric traits require scaled inheritance");
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
			roles.add(BionicAnatomyRole.parse(role.getAsString()));
		return roles.stream().distinct().toList();
	}

	private static net.minecraft.network.chat.Component component(JsonElement json) {
		return json == null ? null : net.minecraft.network.chat.ComponentSerialization.CODEC
			.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
	}

	private static BionicTraitData read(BionicTrait trait, Map<ResourceLocation, JsonElement> resources,
		Map<ResourceLocation, BionicTrait> definitions, Map<BionicAnatomyRole, BionicPartDefinition> parts, boolean strict) {
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
		if (trait.valueKind() != BionicTrait.ValueKind.NUMBER
			&& (rule.type() == BionicTraitType.COVERAGE_THRESHOLD || rule.type() == BionicTraitType.SPECIAL)
			&& root.has("min_coverage")) {
			Double value = numberValue(root.get("min_coverage"));
			if (value != null && value <= 1.0d)
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
		BionicDonorOverrides<Boolean> abilities = trait.valueKind() == BionicTrait.ValueKind.ABILITY
			? overrides(root, primary, BionicTraitDataLoader::booleanValue) : BionicDonorOverrides.empty();
		BionicDonorOverrides<Double> numbers = trait.valueKind() == BionicTrait.ValueKind.NUMBER
			? overrides(root, primary, trait.isStatOffset() ? BionicTraitDataLoader::signedNumberValue
				: BionicTraitDataLoader::numberValue) : BionicDonorOverrides.empty();
		BionicDonorOverrides<Map<ResourceLocation, Boolean>> effects = (trait.valueKind() == BionicTrait.ValueKind.EFFECT_SET
			|| trait.valueKind() == BionicTrait.ValueKind.ATTACK_EFFECT_SET
			|| trait.valueKind() == BionicTrait.ValueKind.SUBTRAIT_SET)
			? effectOverrides(root, primary) : BionicDonorOverrides.empty();
		Map<ResourceLocation, BionicSubtrait> subtraits = new HashMap<>(BionicHeadTraits.variants(trait));
		subtraits.putAll(readSubtraits(root, primary));
		JsonObject carrier = object(root.get("carrier"));
		boolean replaceBindings = false;
		if (carrier.has("replace_with_bindings")) {
			Boolean value = booleanValue(carrier.get("replace_with_bindings"));
			if (value == null) throw new IllegalArgumentException("Expected boolean carrier.replace_with_bindings in " + primary);
			replaceBindings = value;
		}
		return new BionicTraitData(automatic, rule, abilities, numbers, effects,
			readCarriers(root, primary), replaceBindings, parts.entrySet().stream().filter(entry -> primary.equals(entry.getValue().owner()))
				.collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)), readDescendantBindings(root, primary, parts), parameters, readAttackEffects(root, primary), subtraits,
			readOverrides(root, trait, primary, definitions), BionicExtensions.compile(root, strict));
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
				List<BionicAnatomyRole> roles = roles(settings.get("roles"));
				Map<String, Double> parameters = new HashMap<>();
				object(settings.get("parameters")).entrySet().forEach(parameter -> {
					Double value = numberValue(parameter.getValue());
					if (value == null) throw new IllegalArgumentException("Invalid variant parameter");
					parameters.put(parameter.getKey(), value);
				});
				result.put(id, new BionicSubtrait(id, new BionicTrait.Carrier(roles, List.of()), parameters));
			} catch (RuntimeException exception) {
				throw new IllegalArgumentException("Invalid trait variant " + entry.getKey() + " in " + file, exception);
			}
		});
		return Map.copyOf(result);
	}

	private static Map<ResourceLocation, BionicAttackEffect> readAttackEffects(JsonObject root, ResourceLocation file) {
		Map<ResourceLocation, BionicAttackEffect> result = new HashMap<>();
		JsonObject attack = object(root.get("attack_settings"));
		object(root.get("effects")).entrySet().forEach(entry -> {
			try {
				ResourceLocation id = ResourceLocation.parse(entry.getKey());
				JsonObject settings = entry.getValue().getAsJsonObject();
				Double duration = numberValue(attack.get("duration_ticks"));
				Double amplifier = attack.has("amplifier") ? numberValue(attack.get("amplifier")) : 0.0d;
				Double chance = attack.has("chance") ? numberValue(attack.get("chance")) : 1.0d;
				Boolean unarmed = attack.has("unarmed_only") ? booleanValue(attack.get("unarmed_only")) : false;
				if (duration == null || duration < 1 || duration > 12000 || duration != Math.floor(duration)
					|| amplifier == null || amplifier > 255 || amplifier != Math.floor(amplifier)
					|| chance == null || chance > 1 || unarmed == null)
					throw new IllegalArgumentException("Invalid attack parameters");
				List<BionicAnatomyRole> roles = roles(settings.get("roles"));
				result.put(id, new BionicAttackEffect(id, duration.intValue(), amplifier.intValue(), chance,
					unarmed, new BionicTrait.Carrier(roles, List.of())));
			} catch (RuntimeException exception) {
				throw new IllegalArgumentException("Invalid attack effect " + entry.getKey() + " in " + file, exception);
			}
		});
		return Map.copyOf(result);
	}

	private static <T> BionicDonorOverrides<T> overrides(JsonObject root, ResourceLocation file,
		Function<JsonElement, T> parse) {
		Map<ResourceLocation, T> entities = new HashMap<>();
		Map<ResourceLocation, T> tags = new HashMap<>();
		for (var entry : object(root.get("values")).entrySet()) {
			Selector selector = selector(entry.getKey());
			T value = parse.apply(entry.getValue());
			if (selector == null || value == null) {
				LOGGER.warn("Invalid trait donor value {} in {}", entry.getKey(), file);
				continue;
			}
			(selector.tag() ? tags : entities).put(selector.id(), value);
		}
		return freeze(entities, tags);
	}

	private static BionicDonorOverrides<Map<ResourceLocation, Boolean>> effectOverrides(
		JsonObject root, ResourceLocation file) {
		Map<ResourceLocation, Map<ResourceLocation, Boolean>> entities = new HashMap<>();
		Map<ResourceLocation, Map<ResourceLocation, Boolean>> tags = new HashMap<>();
		for (var entry : object(root.get("values")).entrySet()) {
			Selector selector = selector(entry.getKey());
			if (selector == null || !entry.getValue().isJsonObject()) {
				LOGGER.warn("Invalid effect donor {} in {}", entry.getKey(), file);
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
					LOGGER.warn("Invalid effect toggle {} in {}", effect.getKey(), file);
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
			Map<BionicAnatomyRole, Set<String>> roles = new HashMap<>();
			for (var entry : entity.getValue().getAsJsonObject().entrySet()) {
				try {
					BionicAnatomyRole role = BionicAnatomyRole.parse(entry.getKey());
					JsonElement labels = entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject().get("names") : entry.getValue();
					if (labels == null || !labels.isJsonArray()) throw new IllegalArgumentException("Expected carrier names list");
					Set<String> names = new HashSet<>();
					for (JsonElement element : labels.getAsJsonArray()) {
						String name = carrierName(element);
						if (name != null)
							names.add(name);
						else
							LOGGER.warn("Invalid carrier name in {} for {}", file, id);
					}
					roles.computeIfAbsent(role, ignored -> new HashSet<>()).addAll(names);
				} catch (IllegalArgumentException exception) {
					throw new IllegalArgumentException("Invalid carrier role " + entry.getKey() + " in " + file, exception);
				}
			}
			roles.replaceAll((role, names) -> Set.copyOf(names));
			result.put(id, Map.copyOf(roles));
		}
		return Map.copyOf(result);
	}

	private static Map<ResourceLocation, Set<BionicAnatomyRole>> readDescendantBindings(JsonObject root,
		ResourceLocation trait, Map<BionicAnatomyRole, BionicPartDefinition> parts) {
		Map<ResourceLocation, Set<BionicAnatomyRole>> result = new HashMap<>();
		object(root.get("carriers")).entrySet().forEach(entity -> {
			Set<BionicAnatomyRole> included = new HashSet<>();
			entity.getValue().getAsJsonObject().entrySet().forEach(entry -> {
				BionicAnatomyRole role = BionicAnatomyRole.parse(entry.getKey());
				BionicPartDefinition part = parts.get(role);
				boolean children = part != null && trait.equals(part.owner()) && part.includeChildren();
				if (entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has("include_children")) {
					Boolean value = booleanValue(entry.getValue().getAsJsonObject().get("include_children"));
					if (value == null) throw new IllegalArgumentException("Expected boolean binding.include_children in " + trait);
					children = value;
				}
				if (children) included.add(role);
			});
			result.put(ResourceLocation.parse(entity.getKey()), Set.copyOf(included));
		});
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
		Double value = signedNumberValue(element);
		return value != null && value >= 0.0d ? value : null;
	}

	@Nullable
	private static Double signedNumberValue(@Nullable JsonElement element) {
		try {
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
				return null;
			double value = element.getAsDouble();
			return Double.isFinite(value) ? value : null;
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
