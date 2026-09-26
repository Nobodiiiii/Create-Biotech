package com.nobodiiiii.createbiotech.entity.trait;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.trait.BionicBodyTraitRegistry.DataOverrides;
import com.nobodiiiii.createbiotech.entity.trait.BionicBodyTraitRegistry.EffectOverrides;
import com.nobodiiiii.createbiotech.entity.trait.BionicBodyTraitRegistry.TaggedBooleanOverride;
import com.nobodiiiii.createbiotech.entity.trait.BionicBodyTraitRegistry.TaggedEffectOverride;
import com.nobodiiiii.createbiotech.entity.trait.BionicBodyTraitRegistry.TaggedNumericOverride;
import com.nobodiiiii.createbiotech.entity.trait.BionicBodyTraitRegistry.TraitOverrides;
import com.nobodiiiii.createbiotech.entity.trait.BionicBodyTraitRegistry.NumericOverrides;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;

/** Reloads donor data for torso and whole-body traits; each trait declares its scope in code. */
public final class BionicBodyTraitDataReloadListener extends SimpleJsonResourceReloadListener {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	public static final BionicBodyTraitDataReloadListener INSTANCE =
		new BionicBodyTraitDataReloadListener();

	private BionicBodyTraitDataReloadListener() {
		super(GSON, "bionic_body_traits");
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager,
		ProfilerFiller profiler) {
		EnumMap<BionicBodyTrait, TraitOverrides> traits = new EnumMap<>(BionicBodyTrait.class);
		for (BionicBodyTrait trait : BionicBodyTrait.values())
			traits.put(trait, loadTrait(resources, trait));
		BionicBodyTraitRegistry.replaceData(new DataOverrides(traits, loadEffects(resources),
			loadNumber(resources, BionicBodyProperty.NATURAL_ARMOR.id()),
			loadNumber(resources, BionicBodyProperty.KNOCKBACK_RESISTANCE.id()),
			loadNumber(resources, BionicBodyProperty.PASSIVE_REGENERATION.id())));
	}

	private TraitOverrides loadTrait(Map<ResourceLocation, JsonElement> resources,
		BionicBodyTrait trait) {
		ResourceLocation primaryId = CreateBiotech.asResource(trait.serializedName());
		boolean automaticDetection = readAutomaticDetection(resources.get(primaryId), primaryId);
		Map<ResourceLocation, Boolean> entityTypes = new HashMap<>();
		Map<ResourceLocation, Boolean> tags = new HashMap<>();
		forEachFile(resources, primaryId,
			(fileId, element) -> readBooleanValues(fileId, element, entityTypes, tags));
		List<TaggedBooleanOverride> tagged = tags.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> new TaggedBooleanOverride(entityTag(entry.getKey()), entry.getValue()))
			.toList();
		return new TraitOverrides(automaticDetection, entityTypes, tagged);
	}

	private EffectOverrides loadEffects(Map<ResourceLocation, JsonElement> resources) {
		ResourceLocation primaryId = CreateBiotech.asResource(BionicBodyProperty.IMMUNE_EFFECTS.id());
		boolean automaticDetection = readAutomaticDetection(resources.get(primaryId), primaryId);
		Map<ResourceLocation, Map<ResourceLocation, Boolean>> entityTypes = new HashMap<>();
		Map<ResourceLocation, Map<ResourceLocation, Boolean>> tags = new HashMap<>();
		forEachFile(resources, primaryId,
			(fileId, element) -> readEffectValues(fileId, element, entityTypes, tags));
		List<TaggedEffectOverride> tagged = tags.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> new TaggedEffectOverride(entityTag(entry.getKey()), entry.getValue()))
			.toList();
		return new EffectOverrides(automaticDetection, entityTypes, tagged);
	}

	private NumericOverrides loadNumber(Map<ResourceLocation, JsonElement> resources, String path) {
		ResourceLocation primaryId = CreateBiotech.asResource(path);
		boolean automaticDetection = readAutomaticDetection(resources.get(primaryId), primaryId);
		Map<ResourceLocation, Double> entityTypes = new HashMap<>();
		Map<ResourceLocation, Double> tags = new HashMap<>();
		forEachFile(resources, primaryId,
			(fileId, element) -> readNumberValues(fileId, element, entityTypes, tags));
		List<TaggedNumericOverride> tagged = tags.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> new TaggedNumericOverride(entityTag(entry.getKey()), entry.getValue()))
			.toList();
		return new NumericOverrides(automaticDetection, entityTypes, tagged);
	}

	private void forEachFile(Map<ResourceLocation, JsonElement> resources, ResourceLocation primaryId,
		FileReader reader) {
		JsonElement primary = resources.get(primaryId);
		if (primary != null)
			reader.read(primaryId, primary);
		resources.entrySet().stream()
			.filter(entry -> !entry.getKey().equals(primaryId))
			.filter(entry -> entry.getKey().getPath().equals(primaryId.getPath()))
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> reader.read(entry.getKey(), entry.getValue()));
	}

	private boolean readAutomaticDetection(@Nullable JsonElement element, ResourceLocation fileId) {
		if (element == null)
			return true;
		try {
			JsonObject root = element.getAsJsonObject();
			if (!root.has("automatic_detection"))
				return true;
			Boolean value = readBoolean(root.get("automatic_detection"));
			if (value != null)
				return value;
			LOGGER.warn("Ignoring non-boolean automatic_detection in {}", fileId);
		} catch (RuntimeException exception) {
			LOGGER.warn("Could not read automatic_detection in {}", fileId, exception);
		}
		return true;
	}

	private void readBooleanValues(ResourceLocation fileId, JsonElement element,
		Map<ResourceLocation, Boolean> entityTypes, Map<ResourceLocation, Boolean> tags) {
		try {
			JsonObject values = GsonHelper.getAsJsonObject(element.getAsJsonObject(), "values");
			for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
				Selector selector = parseSelector(fileId, entry.getKey());
				Boolean value = readBoolean(entry.getValue());
				if (selector == null || value == null) {
					if (value == null)
						LOGGER.warn("Ignoring non-boolean value for '{}' in {}", entry.getKey(), fileId);
					continue;
				}
				(selector.tag() ? tags : entityTypes).put(selector.id(), value);
			}
		} catch (RuntimeException exception) {
			LOGGER.warn("Could not read bionic body trait data {}", fileId, exception);
		}
	}

	private void readEffectValues(ResourceLocation fileId, JsonElement element,
		Map<ResourceLocation, Map<ResourceLocation, Boolean>> entityTypes,
		Map<ResourceLocation, Map<ResourceLocation, Boolean>> tags) {
		try {
			JsonObject values = GsonHelper.getAsJsonObject(element.getAsJsonObject(), "values");
			for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
				Selector selector = parseSelector(fileId, entry.getKey());
				if (selector == null)
					continue;
				Map<ResourceLocation, Map<ResourceLocation, Boolean>> target =
					selector.tag() ? tags : entityTypes;
				Map<ResourceLocation, Boolean> effects =
					target.computeIfAbsent(selector.id(), ignored -> new HashMap<>());
				JsonObject effectValues = entry.getValue().getAsJsonObject();
				for (Map.Entry<String, JsonElement> effectEntry : effectValues.entrySet()) {
					ResourceLocation effectId = ResourceLocation.tryParse(effectEntry.getKey());
					Boolean enabled = readBoolean(effectEntry.getValue());
					if (effectId == null || !BuiltInRegistries.MOB_EFFECT.containsKey(effectId)) {
						LOGGER.warn("Ignoring unknown immune effect '{}' in {}",
							effectEntry.getKey(), fileId);
						continue;
					}
					if (enabled == null) {
						LOGGER.warn("Ignoring non-boolean immune effect '{}' in {}", effectId, fileId);
						continue;
					}
					effects.put(effectId, enabled);
				}
			}
		} catch (RuntimeException exception) {
			LOGGER.warn("Could not read bionic immune effect data {}", fileId, exception);
		}
	}

	private void readNumberValues(ResourceLocation fileId, JsonElement element,
		Map<ResourceLocation, Double> entityTypes, Map<ResourceLocation, Double> tags) {
		try {
			JsonObject values = GsonHelper.getAsJsonObject(element.getAsJsonObject(), "values");
			for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
				Selector selector = parseSelector(fileId, entry.getKey());
				if (selector == null)
					continue;
				double value = GsonHelper.convertToDouble(entry.getValue(), entry.getKey());
				if (!Double.isFinite(value) || value < 0.0d) {
					LOGGER.warn("Ignoring invalid numeric body trait value {} for '{}' in {}",
						value, entry.getKey(), fileId);
					continue;
				}
				(selector.tag() ? tags : entityTypes).put(selector.id(), value);
			}
		} catch (RuntimeException exception) {
			LOGGER.warn("Could not read numeric bionic body trait data {}", fileId, exception);
		}
	}

	@Nullable
	private Selector parseSelector(ResourceLocation fileId, String value) {
		boolean tag = value.startsWith("#");
		ResourceLocation id = ResourceLocation.tryParse(tag ? value.substring(1) : value);
		if (id == null) {
			LOGGER.warn("Ignoring invalid entity selector '{}' in {}", value, fileId);
			return null;
		}
		if (!tag && !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
			LOGGER.warn("Ignoring unknown entity type {} in {}", id, fileId);
			return null;
		}
		return new Selector(id, tag);
	}

	@Nullable
	private Boolean readBoolean(JsonElement element) {
		return element instanceof JsonPrimitive primitive && primitive.isBoolean()
			? primitive.getAsBoolean() : null;
	}

	private static TagKey<EntityType<?>> entityTag(ResourceLocation id) {
		return TagKey.create(Registries.ENTITY_TYPE, id);
	}

	private record Selector(ResourceLocation id, boolean tag) {}

	@FunctionalInterface
	private interface FileReader {
		void read(ResourceLocation fileId, JsonElement element);
	}
}
