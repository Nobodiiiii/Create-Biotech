package com.nobodiiiii.createbiotech.entity.trait;

import java.util.EnumMap;
import java.util.Map;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** Owns loaded trait data and its revision; contains no JSON parsing or assembly evaluation. */
public final class BionicTraitRegistry {
	private static volatile Snapshot current = defaults();
	private static boolean registered;

	private BionicTraitRegistry() {}

	public static void register() {
		if (registered)
			return;
		registered = true;
		NeoForge.EVENT_BUS.addListener(BionicTraitRegistry::onTagsUpdated);
	}

	private static synchronized void onTagsUpdated(TagsUpdatedEvent event) {
		current = new Snapshot(current.generation() + 1, current.traits());
	}

	static synchronized void replace(Map<BionicTrait, BionicTraitData> data) {
		EnumMap<BionicTrait, BionicTraitData> loaded = new EnumMap<>(BionicTrait.class);
		for (BionicTrait trait : BionicTrait.values())
			loaded.put(trait, data.getOrDefault(trait, BionicTraitData.defaults(trait)));
		current = new Snapshot(current.generation() + 1, loaded);
	}

	public static long generation() { return current.generation(); }
	static Snapshot snapshot() { return current; }

	private static Snapshot defaults() {
		EnumMap<BionicTrait, BionicTraitData> data = new EnumMap<>(BionicTrait.class);
		for (BionicTrait trait : BionicTrait.values())
			data.put(trait, BionicTraitData.defaults(trait));
		return new Snapshot(0, data);
	}

	record Snapshot(long generation, Map<BionicTrait, BionicTraitData> traits) {
		Snapshot { traits = Map.copyOf(traits); }
		BionicTraitData get(BionicTrait trait) { return traits.get(trait); }
	}
}
