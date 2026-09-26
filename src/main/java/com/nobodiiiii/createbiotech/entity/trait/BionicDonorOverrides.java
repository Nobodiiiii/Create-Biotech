package com.nobodiiiii.createbiotech.entity.trait;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/** Shared, deterministic precedence: automatic fact, sorted tags, exact entity ID. */
record BionicDonorOverrides<T>(Map<ResourceLocation, T> entities, List<Tagged<T>> tags) {
	BionicDonorOverrides {
		entities = Map.copyOf(entities);
		tags = List.copyOf(tags);
	}

	static <T> BionicDonorOverrides<T> empty() { return new BionicDonorOverrides<>(Map.of(), List.of()); }

	T resolve(EntityType<?> type, ResourceLocation id, T automatic) {
		T result = automatic;
		for (Tagged<T> tagged : tags)
			if (type.is(tagged.tag()))
				result = tagged.value();
		return entities.getOrDefault(id, result);
	}

	/** Maps such as effect toggles merge, rather than replace, at each precedence level. */
	void forEach(EntityType<?> type, ResourceLocation id, Consumer<T> consumer) {
		for (Tagged<T> tagged : tags)
			if (type.is(tagged.tag()))
				consumer.accept(tagged.value());
		T exact = entities.get(id);
		if (exact != null)
			consumer.accept(exact);
	}

	record Tagged<T>(TagKey<EntityType<?>> tag, T value) {}
}
