package com.nobodiiiii.createbiotech.entity.trait;

import java.util.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;

/** Deterministic overlays. Raw extension resources travel with the catalog for identical client loading. */
final class BionicTraitResources {
	private static final String PREFIX = "__extension/";
	private BionicTraitResources() {}
	static ResourceLocation transportId(String directory, ResourceLocation id) {
		return ResourceLocation.fromNamespaceAndPath("create_biotech", PREFIX + directory + "/" + id.getNamespace() + "/" + id.getPath());
	}
	static ResourceLocation resourceId(String directory, ResourceLocation transport) {
		if (!directory(transport, directory)) return null;
		String path = transport.getPath().substring((PREFIX + directory + "/").length());
		int slash = path.indexOf('/');
		if (slash <= 0 || slash == path.length() - 1) throw new IllegalArgumentException("Invalid catalog resource " + transport);
		return ResourceLocation.fromNamespaceAndPath(path.substring(0, slash), path.substring(slash + 1));
	}
	static Map<ResourceLocation, JsonElement> expand(Map<ResourceLocation, JsonElement> raw) {
		Map<ResourceLocation, JsonElement> result = new LinkedHashMap<>();
		raw.entrySet().stream().sorted(Map.Entry.comparingByKey()).filter(e -> !extension(e.getKey()))
			.forEach(e -> result.put(e.getKey(), e.getValue().deepCopy()));
		var bindings = raw.entrySet().stream().filter(e -> directory(e.getKey(), "bionic_trait_bindings"))
			.sorted(Map.Entry.comparingByKey()).toList();
		for (var entry : bindings) {
			var root = entry.getValue().getAsJsonObject();
			var target = ResourceLocation.parse(root.get("trait").getAsString());
			if (!exists(result, target, root)) continue;
			var definition = result.get(target).getAsJsonObject();
			for (String key : List.of("values", "carriers", "effects", "variants"))
				if (root.has(key)) {
					var merged = BionicExtensions.object(definition.get(key)).deepCopy();
					merge(merged, root.getAsJsonObject(key));
					definition.add(key, merged);
				}
		}
		var modifiers = raw.entrySet().stream().filter(e -> directory(e.getKey(), "bionic_trait_modifiers"))
			.sorted(Comparator.<Map.Entry<ResourceLocation, JsonElement>>comparingInt(e ->
				BionicExtensions.integer(e.getValue().getAsJsonObject(), "priority", -100000, 100000, 0))
				.thenComparing(Map.Entry.comparingByKey())).toList();
		for (var entry : modifiers) {
			var root = entry.getValue().getAsJsonObject();
			var target = ResourceLocation.parse(root.get("trait").getAsString());
			if (!exists(result, target, root)) continue;
			var definition = result.get(target).getAsJsonObject();
			if (root.has("patch")) merge(definition, root.getAsJsonObject("patch"));
		}
		return result;
	}
	private static boolean exists(Map<ResourceLocation, JsonElement> result, ResourceLocation id, JsonObject root) {
		if (result.containsKey(id)) return true;
		// Built-in defaults can be patched without supplying a complete definition.
		if (BionicTrait.builtIn(id) != null) { result.put(id, new JsonObject()); return true; }
		if (root.has("optional") && root.get("optional").getAsBoolean()) return false;
		throw new IllegalArgumentException("Unknown bionic extension target: " + id);
	}
	private static boolean extension(ResourceLocation id) { return id.getNamespace().equals("create_biotech") && id.getPath().startsWith(PREFIX); }
	private static boolean directory(ResourceLocation id, String directory) { return extension(id) && id.getPath().startsWith(PREFIX + directory + "/"); }
	/** JSON merge patch: recursive objects, replacing arrays/scalars, null removes the field. */
	private static void merge(JsonObject target, JsonObject patch) {
		patch.entrySet().forEach(e -> {
			if (e.getValue().isJsonNull()) target.remove(e.getKey());
			else if (e.getValue().isJsonObject()) {
				var child = target.has(e.getKey()) && target.get(e.getKey()).isJsonObject() ? target.getAsJsonObject(e.getKey()) : new JsonObject();
				merge(child, e.getValue().getAsJsonObject()); target.add(e.getKey(), child);
			} else target.add(e.getKey(), e.getValue().deepCopy());
		});
	}
}
