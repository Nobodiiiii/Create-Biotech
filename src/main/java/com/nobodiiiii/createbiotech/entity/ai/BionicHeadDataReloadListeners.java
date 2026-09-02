package com.nobodiiiii.createbiotech.entity.ai;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/** Server-data reloaders for the two independent bionic-head classification tables. */
public final class BionicHeadDataReloadListeners {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final ResourceLocation DEFAULTS = CreateBiotech.asResource("defaults");

	public static final SimpleJsonResourceReloadListener DISPOSITIONS =
		new ValueReloadListener<>("bionic_head/disposition", BionicDisposition.class,
			BionicDispositionRegistry::replaceData);
	public static final SimpleJsonResourceReloadListener INTELLIGENCE =
		new ValueReloadListener<>("bionic_head/intelligence", BionicIntelligence.class,
			BionicIntelligenceRegistry::replaceData);

	private BionicHeadDataReloadListeners() {}

	private static final class ValueReloadListener<T extends Enum<T>>
		extends SimpleJsonResourceReloadListener {
		private static final Logger LOGGER = LogUtils.getLogger();

		private final Class<T> valueClass;
		private final Consumer<Map<ResourceLocation, T>> sink;

		private ValueReloadListener(String directory, Class<T> valueClass,
			Consumer<Map<ResourceLocation, T>> sink) {
			super(GSON, directory);
			this.valueClass = valueClass;
			this.sink = sink;
		}

		@Override
		protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager,
			ProfilerFiller profiler) {
			Map<ResourceLocation, T> loaded = new HashMap<>();
			JsonElement defaults = resources.get(DEFAULTS);
			if (defaults != null)
				applyFile(DEFAULTS, defaults, loaded);
			resources.entrySet().stream()
				.filter(entry -> !entry.getKey().equals(DEFAULTS))
				.sorted(Map.Entry.comparingByKey())
				.forEach(entry -> applyFile(entry.getKey(), entry.getValue(), loaded));
			sink.accept(Map.copyOf(loaded));
		}

		private void applyFile(ResourceLocation fileId, JsonElement element,
			Map<ResourceLocation, T> loaded) {
			try {
				JsonObject root = element.getAsJsonObject();
				JsonObject values = GsonHelper.getAsJsonObject(root, "values");
				for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
					ResourceLocation entityTypeId = ResourceLocation.tryParse(entry.getKey());
					if (entityTypeId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityTypeId)) {
						LOGGER.warn("Ignoring unknown bionic head entity type {} in {}", entry.getKey(), fileId);
						continue;
					}
					String name = GsonHelper.convertToString(entry.getValue(), entry.getKey());
					T value;
					try {
						value = Enum.valueOf(valueClass, name.toUpperCase(Locale.ROOT));
					} catch (IllegalArgumentException exception) {
						LOGGER.warn("Ignoring invalid {} value '{}' for {} in {}",
							valueClass.getSimpleName(), name, entityTypeId, fileId);
						continue;
					}
					loaded.put(entityTypeId, value);
				}
			} catch (RuntimeException exception) {
				LOGGER.warn("Could not read bionic head data {}", fileId, exception);
			}
		}
	}
}
