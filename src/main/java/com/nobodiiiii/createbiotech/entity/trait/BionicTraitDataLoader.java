package com.nobodiiiii.createbiotech.entity.trait;

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
	protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager,
		ProfilerFiller profiler) {
		EnumMap<BionicTrait, BionicTraitData> loaded = new EnumMap<>(BionicTrait.class);
		for (BionicTrait trait : BionicTrait.values())
			loaded.put(trait, read(trait, resources));
		BionicTraitRegistry.replace(loaded);
	}

	private static BionicTraitData read(BionicTrait trait, Map<ResourceLocation, JsonElement> resources) {
		ResourceLocation primary = CreateBiotech.asResource(trait.id());
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
		if ((rule.type() == BionicTraitType.COVERAGE_THRESHOLD || trait.valueKind() == BionicTrait.ValueKind.NUMBER)
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
		// Canonical settings/carriers come from create_biotech; other namespaces extend donor values.
		List<Map.Entry<ResourceLocation, JsonElement>> files = resources.entrySet().stream()
			.filter(entry -> entry.getKey().getPath().equals(trait.id()))
			.sorted(java.util.Comparator.<Map.Entry<ResourceLocation, JsonElement>, Boolean>
				comparing(entry -> !entry.getKey().equals(primary)).thenComparing(Map.Entry.comparingByKey()))
			.toList();
		BionicDonorOverrides<Boolean> abilities = trait.valueKind() == BionicTrait.ValueKind.ABILITY
			? overrides(files, BionicTraitDataLoader::booleanValue) : BionicDonorOverrides.empty();
		BionicDonorOverrides<Double> numbers = trait.valueKind() == BionicTrait.ValueKind.NUMBER
			? overrides(files, BionicTraitDataLoader::numberValue) : BionicDonorOverrides.empty();
		BionicDonorOverrides<Map<ResourceLocation, Boolean>> effects = (trait.valueKind() == BionicTrait.ValueKind.EFFECT_SET
			|| trait.valueKind() == BionicTrait.ValueKind.ATTACK_EFFECT_SET)
			? effectOverrides(files) : BionicDonorOverrides.empty();
		return new BionicTraitData(automatic, rule, abilities, numbers, effects,
			readCarriers(root, primary), parameters, readAttackEffects(root, primary));
	}

	private static Map<ResourceLocation, BionicAttackEffect> readAttackEffects(JsonObject root, ResourceLocation file) {
		Map<ResourceLocation, BionicAttackEffect> result = new HashMap<>();
		object(root.get("effects")).entrySet().forEach(entry -> {
			try {
				ResourceLocation id = ResourceLocation.parse(entry.getKey());
				JsonObject settings = entry.getValue().getAsJsonObject();
				Double duration = numberValue(settings.get("duration_ticks"));
				Double amplifier = settings.has("amplifier") ? numberValue(settings.get("amplifier")) : 0.0d;
				Double chance = settings.has("chance") ? numberValue(settings.get("chance")) : 1.0d;
				Boolean unarmed = settings.has("unarmed_only") ? booleanValue(settings.get("unarmed_only")) : false;
				if (duration == null || duration < 1 || duration > 12000 || duration != Math.floor(duration)
					|| amplifier == null || amplifier > 255 || amplifier != Math.floor(amplifier)
					|| chance == null || chance > 1 || unarmed == null)
					throw new IllegalArgumentException("Invalid attack parameters");
				java.util.List<BionicAnatomyRole> roles = new java.util.ArrayList<>();
				for (JsonElement role : settings.getAsJsonArray("roles"))
					roles.add(BionicAnatomyRole.valueOf(role.getAsString()));
				if (roles.isEmpty())
					throw new IllegalArgumentException("Missing attack carriers");
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
			return name.matches("[A-Za-z0-9_./-]{1,64}") ? name : null;
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
