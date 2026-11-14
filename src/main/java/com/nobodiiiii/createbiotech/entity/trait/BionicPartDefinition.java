package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/** The single data definition used for model-name inference, carrier IDs and donor display names. */
public record BionicPartDefinition(BionicAnatomyRole role, Component name, int order,
	NameMatch match, @Nullable NamespaceFilter fallbackToAll, boolean includeChildren, @Nullable ResourceLocation owner) {
	public static final int MAX_PARTS = 1024;
	private static final Pattern NAME_SYMBOLS = Pattern.compile("[^a-z0-9]");

	public BionicPartDefinition {
		name = name.copy();
	}

	@Override public Component name() { return name.copy(); }

	public boolean matches(ResourceLocation entity, String label) {
		return match.matches(entity, label);
	}

	public boolean fallsBackToAll(ResourceLocation entity) {
		return fallbackToAll != null && fallbackToAll.matches(entity);
	}

	static String canonicalName(String name) {
		return NAME_SYMBOLS.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("");
	}

	static Map<BionicAnatomyRole, BionicPartDefinition> decode(Map<ResourceLocation, JsonElement> resources) {
		Map<BionicAnatomyRole, BionicPartDefinition> result = new HashMap<>();
		resources.forEach((transport, json) -> {
			ResourceLocation id = BionicTraitResources.resourceId("bionic_parts", transport);
			if (id == null) return;
			try {
				JsonObject root = json.getAsJsonObject();
				if (root.has("disabled") && root.get("disabled").getAsBoolean()) return;
				BionicAnatomyRole role = new BionicAnatomyRole(id);
				Component name = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, root.get("name")).getOrThrow();
				int order = root.has("order") ? root.get("order").getAsBigDecimal().intValueExact() : 0;
				NameMatch match = NameMatch.decode(root.has("match") ? root.getAsJsonObject("match") : new JsonObject(), 0);
				NamespaceFilter fallback = root.has("fallback_to_all")
					? NamespaceFilter.decode(root.getAsJsonObject("fallback_to_all")) : null;
				if (root.has("include_children") && (!root.get("include_children").isJsonPrimitive()
					|| !root.getAsJsonPrimitive("include_children").isBoolean())) throw new IllegalArgumentException("Expected boolean include_children");
				boolean children = root.has("include_children") && root.get("include_children").getAsBoolean();
				if (root.has("captured_head")) throw new IllegalArgumentException("Use include_children instead of captured_head");
				result.put(role, new BionicPartDefinition(role, name, order, match, fallback, children, null));
			} catch (RuntimeException exception) {
				throw new IllegalArgumentException("Invalid bionic part " + id, exception);
			}
		});
		if (result.size() > MAX_PARTS) throw new IllegalArgumentException("Too many bionic parts");
		return Map.copyOf(result);
	}

	static BionicAnatomyRole localRole(ResourceLocation trait, String key) {
		if (!key.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid local part key " + key);
		return new BionicAnatomyRole(ResourceLocation.fromNamespaceAndPath(trait.getNamespace(), "trait/" + trait.getPath() + "/" + key));
	}
	static BionicPartDefinition local(ResourceLocation trait, String key, JsonObject json) {
		BionicAnatomyRole role = localRole(trait, key);
		BionicPartDefinition decoded = decode(Map.of(BionicTraitResources.transportId("bionic_parts", role.id()), json)).get(role);
		if (decoded == null) throw new IllegalArgumentException("Local parts cannot be disabled individually; remove their entry");
		return new BionicPartDefinition(role, decoded.name(), decoded.order(), decoded.match(), decoded.fallbackToAll(), decoded.includeChildren(), trait);
	}

	public record NamespaceFilter(Set<String> included, Set<String> excluded) {
		public NamespaceFilter { included = Set.copyOf(included); excluded = Set.copyOf(excluded); }
		boolean matches(ResourceLocation entity) {
			return (included.isEmpty() || included.contains(entity.getNamespace())) && !excluded.contains(entity.getNamespace());
		}
		static NamespaceFilter decode(JsonObject root) {
			Set<String> included = Set.copyOf(strings(root, "entity_namespaces"));
			Set<String> excluded = Set.copyOf(strings(root, "excluded_entity_namespaces"));
			java.util.stream.Stream.concat(included.stream(), excluded.stream())
				.forEach(namespace -> ResourceLocation.fromNamespaceAndPath(namespace, "part"));
			return new NamespaceFilter(included, excluded);
		}
	}

	public record NameMatch(String target, NamespaceFilter entities, List<String> exact,
		List<String> contains, List<Pattern> patterns, List<NameMatch> anyOf) {
		public NameMatch {
			exact = List.copyOf(exact); contains = List.copyOf(contains);
			patterns = List.copyOf(patterns); anyOf = List.copyOf(anyOf);
		}
		boolean matches(ResourceLocation entity, String label) {
			if (!entities.matches(entity)) return false;
			String name = transform(target, label);
			boolean modelName = !label.startsWith("material/") || target.equals("raw");
			return modelName && (exact.contains(name) || contains.stream().anyMatch(name::contains)
				|| patterns.stream().anyMatch(pattern -> pattern.matcher(name).find()))
				|| anyOf.stream().anyMatch(child -> child.matches(entity, label));
		}
		static NameMatch decode(JsonObject root, int depth) {
			if (depth > 4) throw new IllegalArgumentException("Model-name matcher nesting is too deep");
			String target = root.has("target") ? root.get("target").getAsString() : "canonical";
			if (!Set.of("canonical", "raw", "leaf").contains(target)) throw new IllegalArgumentException("Unknown name target " + target);
			List<NameMatch> children = new ArrayList<>();
			if (root.has("any_of")) for (JsonElement child : root.getAsJsonArray("any_of")) {
				if (children.size() >= 64) throw new IllegalArgumentException("Too many model-name alternatives");
				children.add(decode(child.getAsJsonObject(), depth + 1));
			}
			return new NameMatch(target, NamespaceFilter.decode(root), keywords(root, "exact", target),
				keywords(root, "contains", target), strings(root, "regex").stream().map(Pattern::compile).toList(), children);
		}
		private static List<String> keywords(JsonObject root, String key, String target) {
			return strings(root, key).stream().map(word -> {
				String result = target.equals("canonical") ? canonicalName(word) : word;
				if (result.isEmpty()) throw new IllegalArgumentException("Empty model-name keyword");
				return result;
			}).toList();
		}
		private static String transform(String target, String label) {
			return switch (target) {
			case "raw" -> label;
			case "leaf" -> label.substring(Math.max(label.lastIndexOf('/'), label.lastIndexOf('.')) + 1);
			default -> canonicalName(label);
			};
		}
	}

	private static List<String> strings(JsonObject root, String key) {
		if (!root.has(key)) return List.of();
		List<String> result = new ArrayList<>();
		for (JsonElement value : root.getAsJsonArray(key)) {
			if (result.size() >= 64 || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
				|| value.getAsString().length() > 256)
				throw new IllegalArgumentException("Invalid model-name matcher " + key);
			result.add(value.getAsString());
		}
		return List.copyOf(result);
	}
}
