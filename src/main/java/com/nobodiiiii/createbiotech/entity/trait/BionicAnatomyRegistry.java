package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.resources.ResourceLocation;

/** Server-owned cube-role templates. Unknown layouts contribute no template roles. */
public final class BionicAnatomyRegistry {
	private BionicAnatomyRegistry() {}

	@Nullable
	private static Template get(SurgicalAssembly.Source source, BionicTraitRegistry.Snapshot data) {
		for (Template template : data.templates().getOrDefault(source.profile().entityTypeId(), List.of()))
			if (template.matches(source))
				return template;
		return null;
	}

	/**
	 * Every original role known for this source: a matching template, the model parts' ordinary
	 * anatomical names, parts named by trait carriers, and the captured head. Installation scopes
	 * and coverage denominators are determined separately, without consulting these labels.
	 */
	public static Map<BionicAnatomyRole, BitSet> roles(SurgicalAssembly.Source source) {
		return roles(source, BionicTraitRegistry.snapshot());
	}

	static Map<BionicAnatomyRole, BitSet> roles(SurgicalAssembly.Source source,
		BionicTraitRegistry.Snapshot data) {
		Map<BionicAnatomyRole, BitSet> roles = new HashMap<>();
		Template configured = get(source, data);
		if (configured != null)
			configured.roles.forEach((role, cubes) -> merge(roles, role, cubes));
		ResourceLocation entity = source.profile().entityTypeId();
		inferNamedRoles(entity, source.anatomy().parts(), roles, data.parts());
		BionicTraitCarriers.rolesFor(data, source.profile().entityTypeId(),
			source.anatomy().parts(), source.cubeCount())
			.forEach((role, cubes) -> merge(roles, role, cubes));
		data.parts().values().stream().filter(BionicPartDefinition::capturedHead)
			.forEach(part -> merge(roles, part.role(), BionicTissue.capturedHead(source)));
		for (BionicPartDefinition part : data.parts().values())
			if (roles.getOrDefault(part.role(), new BitSet()).isEmpty() && part.fallsBackToAll(entity))
				roles.computeIfAbsent(part.role(), ignored -> new BitSet()).set(0, source.cubeCount());
		return roles;
	}

	/**
	 * Infer only names whose anatomical meaning is stable across ordinary entity models. Ambiguous
	 * names such as {@code body} remain the responsibility of entity-specific carrier data or an
	 * anatomy template.
	 */
	private static void inferNamedRoles(ResourceLocation entity, Map<Integer, java.util.Set<String>> parts,
		Map<BionicAnatomyRole, BitSet> roles, Map<BionicAnatomyRole, BionicPartDefinition> definitions) {
		parts.forEach((cube, names) -> names.forEach(label -> {
			for (BionicPartDefinition part : definitions.values())
				if (part.matches(entity, label)) set(roles, part.role(), cube);
		}));
	}

	private static void set(Map<BionicAnatomyRole, BitSet> roles,
		BionicAnatomyRole role, int cube) {
		roles.computeIfAbsent(role, ignored -> new BitSet()).set(cube);
	}

	private static void merge(Map<BionicAnatomyRole, BitSet> roles, BionicAnatomyRole role,
		BitSet cubes) {
		if (!cubes.isEmpty())
			roles.computeIfAbsent(role, ignored -> new BitSet()).or(cubes);
	}

	static Map<ResourceLocation, List<Template>> decode(Map<ResourceLocation, JsonElement> resources,
		Map<BionicAnatomyRole, BionicPartDefinition> parts) {
		Map<ResourceLocation, List<Template>> loaded = new HashMap<>();
		resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
			ResourceLocation id = BionicTraitResources.resourceId("bionic_anatomy", entry.getKey());
			if (id == null) return;
			String path = id.getPath();
			int slash = path.indexOf('/');
			if (slash <= 0 || slash == path.length() - 1) {
				throw new IllegalArgumentException("Expected <entity_namespace>/<entity_path> for anatomy " + id);
			}
			ResourceLocation entityId = ResourceLocation.tryParse(
				path.substring(0, slash) + ":" + path.substring(slash + 1));
			try {
				if (entry.getValue().isJsonObject()
					&& entry.getValue().getAsJsonObject().has("entity_type"))
					entityId = ResourceLocation.tryParse(entry.getValue().getAsJsonObject()
						.get("entity_type").getAsString());
			} catch (RuntimeException exception) {
				throw new IllegalArgumentException("Invalid anatomy entity selector in " + id, exception);
			}
			Template template = parse(entry.getValue(), parts);
			if (entityId == null || template == null) {
				throw new IllegalArgumentException("Invalid anatomy template " + id);
			}
			loaded.computeIfAbsent(entityId, ignored -> new ArrayList<>()).add(template);
		});
		Map<ResourceLocation, List<Template>> frozen = new HashMap<>();
		loaded.forEach((entity, variants) -> {
			variants.sort(java.util.Comparator.comparing((Template template) -> template.baby == null));
			frozen.put(entity, List.copyOf(variants));
		});
		return Map.copyOf(frozen);
	}

	@Nullable
	private static Template parse(JsonElement element, Map<BionicAnatomyRole, BionicPartDefinition> parts) {
		try {
			if (!element.isJsonObject())
				return null;
			JsonObject json = element.getAsJsonObject();
			int count = json.get("cube_count").getAsInt();
			if (count <= 0 || count > SurgicalAssembly.MAX_CUBES)
				return null;
			JsonObject rolesJson = json.getAsJsonObject("roles");
			if (rolesJson == null)
				return null;
			Map<BionicAnatomyRole, BitSet> roles = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : rolesJson.entrySet()) {
				BionicAnatomyRole role = BionicAnatomyRole.parse(entry.getKey());
				if (!parts.containsKey(role)) throw new IllegalArgumentException("Undefined bionic part " + role);
				JsonArray ids = entry.getValue().getAsJsonArray();
				BitSet cubes = new BitSet(count);
				for (JsonElement id : ids) {
					int cube = id.getAsInt();
					if (cube < 0 || cube >= count || cubes.get(cube))
						return null;
					cubes.set(cube);
				}
				merge(roles, role, cubes);
			}
			List<SurgicalAssembly.Seam> seams = null;
			if (json.has("seams")) {
				seams = new ArrayList<>();
				for (JsonElement pair : json.getAsJsonArray("seams")) {
					JsonArray ends = pair.getAsJsonArray();
					if (ends.size() != 2)
						return null;
					int first = ends.get(0).getAsInt();
					int second = ends.get(1).getAsInt();
					if (first < 0 || second < 0 || first >= count || second >= count || first == second)
						return null;
					seams.add(SurgicalAssembly.Seam.of(first, second));
				}
				if (!SurgicalAssembly.validTopology(count, seams))
					return null;
			}
			if (seams == null)
				return null;
			Boolean baby = json.has("baby") ? json.get("baby").getAsBoolean() : null;
			return new Template(count, roles, seams, baby);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	static final class Template {
		private final int cubeCount;
		private final Map<BionicAnatomyRole, BitSet> roles;
		@Nullable private final List<SurgicalAssembly.Seam> seams;
		@Nullable private final Boolean baby;

		private Template(int cubeCount, Map<BionicAnatomyRole, BitSet> roles,
			@Nullable List<SurgicalAssembly.Seam> seams, @Nullable Boolean baby) {
			this.cubeCount = cubeCount;
			Map<BionicAnatomyRole, BitSet> copy = new HashMap<>();
			roles.forEach((role, cubes) -> copy.put(role, (BitSet) cubes.clone()));
			this.roles = Map.copyOf(copy);
			this.seams = seams == null ? null : List.copyOf(seams);
			this.baby = baby;
		}

		private boolean matches(SurgicalAssembly.Source source) {
			return source.cubeCount() == cubeCount
				&& (seams == null || seams.equals(source.seams()))
				&& (baby == null || baby.equals(source.profile().baby()));
		}
	}
}
