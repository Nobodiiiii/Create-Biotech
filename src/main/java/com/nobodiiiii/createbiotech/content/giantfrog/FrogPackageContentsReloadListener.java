package com.nobodiiiii.createbiotech.content.giantfrog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Loads random package contents from the {@code frog_package_contents} data-pack directory. */
public final class FrogPackageContentsReloadListener extends SimpleJsonResourceReloadListener {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	public static final FrogPackageContentsReloadListener INSTANCE = new FrogPackageContentsReloadListener();

	private volatile Map<ResourceLocation, List<Entry>> profiles = Map.of();
	private final Set<TagKey<Item>> warnedEmptyTags = ConcurrentHashMap.newKeySet();

	private FrogPackageContentsReloadListener() {
		super(GSON, "frog_package_contents");
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager,
		ProfilerFiller profiler) {
		Map<ResourceLocation, List<Entry>> loaded = new HashMap<>();
		resources.forEach((id, element) -> parseProfile(id, element).ifPresent(entries -> loaded.put(id, entries)));
		profiles = Map.copyOf(loaded);
		warnedEmptyTags.clear();
	}

	public List<ItemStack> roll(ResourceLocation profileId, RandomSource random) {
		List<Entry> entries = profiles.get(profileId);
		if (entries == null)
			return List.of();

		List<ItemStack> result = new ArrayList<>(entries.size());
		for (Entry entry : entries) {
			int count = entry.min() + random.nextInt(entry.max() - entry.min() + 1);
			if (count == 0)
				continue;
			Item item = pick(entry.selector(), random);
			if (item != null && item != Items.AIR)
				result.add(new ItemStack(item, count));
		}
		return result;
	}

	@Nullable
	private Item pick(Selector selector, RandomSource random) {
		if (selector.item() != null)
			return selector.item();
		List<Holder<Item>> candidates = BuiltInRegistries.ITEM.getTag(selector.tag()).stream()
			.flatMap(named -> named.stream())
			.map(holder -> (Holder<Item>) holder)
			.toList();
		if (!candidates.isEmpty())
			return candidates.get(random.nextInt(candidates.size())).value();
		if (warnedEmptyTags.add(selector.tag()))
			LOGGER.warn("Frog package item tag {} is empty or missing", selector.tag().location());
		return null;
	}

	private Optional<List<Entry>> parseProfile(ResourceLocation id, JsonElement element) {
		try {
			JsonObject root = element.getAsJsonObject();
			List<Entry> entries = new ArrayList<>();
			for (JsonElement entryElement : GsonHelper.getAsJsonArray(root, "entries")) {
				JsonObject entryObject = entryElement.getAsJsonObject();
				Selector selector = parseSelector(id, entryObject);
				int min = GsonHelper.getAsInt(entryObject, "min", 0);
				int max = GsonHelper.getAsInt(entryObject, "max");
				if (selector == null || min < 0 || max < min || max > 64) {
					LOGGER.warn("Ignoring invalid frog package entry in {} (required: one item/tag and 0 <= min <= max <= 64)",
						id);
					continue;
				}
				entries.add(new Entry(selector, min, max));
			}
			return Optional.of(List.copyOf(entries));
		} catch (RuntimeException exception) {
			LOGGER.warn("Could not read frog package contents {}", id, exception);
			return Optional.empty();
		}
	}

	@Nullable
	private Selector parseSelector(ResourceLocation fileId, JsonObject object) {
		boolean hasItem = object.has("item");
		boolean hasTag = object.has("tag");
		if (hasItem == hasTag)
			return null;

		String value = GsonHelper.getAsString(object, hasItem ? "item" : "tag");
		ResourceLocation id = ResourceLocation.tryParse(value);
		if (id == null) {
			LOGGER.warn("Ignoring invalid frog package selector '{}' in {}", value, fileId);
			return null;
		}
		if (hasTag)
			return new Selector(null, TagKey.create(Registries.ITEM, id));
		if (!BuiltInRegistries.ITEM.containsKey(id)) {
			LOGGER.warn("Ignoring unknown frog package item {} in {}", id, fileId);
			return null;
		}
		return new Selector(BuiltInRegistries.ITEM.get(id), null);
	}

	private record Entry(Selector selector, int min, int max) {}

	private record Selector(@Nullable Item item, @Nullable TagKey<Item> tag) {}
}
