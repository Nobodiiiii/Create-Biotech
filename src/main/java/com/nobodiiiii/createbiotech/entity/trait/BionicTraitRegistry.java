package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** Immutable loaded catalogs. Remote client data never replaces integrated-server data. */
public final class BionicTraitRegistry {
	private static volatile Snapshot current = defaults(0);
	private static volatile Snapshot client = defaults(-1);
	private static boolean registered;
	private static final List<Runnable> CLIENT_LISTENERS = new ArrayList<>();

	private BionicTraitRegistry() {}
	public static void register() {
		if (registered) return;
		registered = true;
		NeoForge.EVENT_BUS.addListener(BionicTraitRegistry::onTagsUpdated);
	}
	private static void onTagsUpdated(TagsUpdatedEvent event) {
		if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) invalidate();
	}
	public static synchronized void invalidate() {
		current = new Snapshot(current.generation() + 1, current.traits(), current.parts(), current.templates(), current.resources());
	}
	static synchronized void replace(BionicTraitDataLoader.Catalog data,
		Map<ResourceLocation, JsonElement> resources) {
		current = new Snapshot(current.generation() + 1, data.traits(), data.parts(), data.templates(), resources);
	}
	public static void acceptClient(long generation, Map<ResourceLocation, JsonElement> resources) {
		var loaded = BionicTraitDataLoader.decode(resources, false);
		client = new Snapshot(generation, loaded.traits(), loaded.parts(), loaded.templates(), resources);
		CLIENT_LISTENERS.forEach(Runnable::run);
	}
	public static void resetClient() {
		client = defaults(-1);
		CLIENT_LISTENERS.forEach(Runnable::run);
	}
	public static void addClientCatalogListener(Runnable listener) { CLIENT_LISTENERS.add(listener); }
	public static long generation() { return current.generation(); }
	public static long clientGeneration() { return client.generation(); }
	static Snapshot snapshot() { return current; }
	static Snapshot snapshot(Level level) { return level != null && level.isClientSide ? client : current; }
	public static List<BionicTrait> clientTraits() { return client.definitions(); }
	public static BionicTrait clientTrait(ResourceLocation id) { return client.find(id); }
	public static BionicPartDefinition clientPart(BionicAnatomyRole role) { return client.parts().get(role); }
	public static boolean clientCapturedHead(ResourceLocation entity, String label) {
		return client.parts().values().stream().anyMatch(part -> part.capturedHead() && part.matches(entity, label));
	}
	public static net.minecraft.network.chat.Component clientPartName(BionicAnatomyRole role) {
		BionicPartDefinition part = clientPart(role);
		return part == null ? net.minecraft.network.chat.Component.literal(role.toString()) : part.name();
	}
	public static Map<BionicTrait, Set<BionicTrait>> clientOverrides() {
		Map<BionicTrait, Set<BionicTrait>> result = new LinkedHashMap<>();
		client.traits().forEach((trait, data) -> result.put(trait, data.overrides()));
		return result;
	}
	public static BionicTraitRule clientRule(BionicTrait trait) { return client.get(trait).rule(); }
	public static double clientParameter(BionicTrait trait, String key, double fallback) {
		return client.get(trait).parameter(key, fallback);
	}
	public static List<BionicTrait> traits(Level level) { return snapshot(level).definitions(); }
	public static BionicTrait find(Level level, ResourceLocation id) { return snapshot(level).find(id); }

	private static Snapshot defaults(long generation) {
		Map<BionicTrait, BionicTraitData> data = new LinkedHashMap<>();
		for (BionicTrait trait : BionicTrait.builtIns()) data.put(trait, BionicTraitData.defaults(trait));
		return new Snapshot(generation, data, Map.of(), Map.of(), Map.of());
	}
	static final class Snapshot {
		private final long generation;
		private final Map<BionicTrait, BionicTraitData> traits;
		private final Map<ResourceLocation, JsonElement> resources;
		private final Map<BionicAnatomyRole, BionicPartDefinition> parts;
		private final Map<ResourceLocation, List<BionicAnatomyRegistry.Template>> templates;
		private final Map<ResourceLocation, BionicTrait> byId;
		private final List<BionicTrait> definitions;
		Snapshot(long generation, Map<BionicTrait, BionicTraitData> traits,
			Map<BionicAnatomyRole, BionicPartDefinition> parts,
			Map<ResourceLocation, List<BionicAnatomyRegistry.Template>> templates,
			Map<ResourceLocation, JsonElement> resources) {
			this.generation = generation;
			this.traits = Map.copyOf(traits);
			this.resources = Map.copyOf(resources);
			this.parts = Map.copyOf(parts);
			this.templates = Map.copyOf(templates);
			this.definitions = traits.keySet().stream().sorted().toList();
			Map<ResourceLocation, BionicTrait> index = new LinkedHashMap<>();
			definitions.forEach(trait -> index.put(trait.id(), trait));
			this.byId = Map.copyOf(index);
		}
		long generation() { return generation; }
		Map<BionicTrait, BionicTraitData> traits() { return traits; }
		Map<ResourceLocation, JsonElement> resources() { return resources; }
		Map<BionicAnatomyRole, BionicPartDefinition> parts() { return parts; }
		Map<ResourceLocation, List<BionicAnatomyRegistry.Template>> templates() { return templates; }
		List<BionicTrait> definitions() { return definitions; }
		BionicTrait find(ResourceLocation id) { return byId.get(id); }
		BionicTraitData get(BionicTrait trait) {
			BionicTraitData value = traits.get(trait);
			return value == null ? BionicTraitData.defaults(trait) : value;
		}
	}
}
