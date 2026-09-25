package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Server-owned cube-role templates. Unknown layouts contribute no template roles. */
public final class BionicAnatomyRegistry extends SimpleJsonResourceReloadListener {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final BionicAnatomyRegistry INSTANCE = new BionicAnatomyRegistry();
	private static final AtomicLong GENERATION = new AtomicLong();
	private static volatile Map<ResourceLocation, List<Template>> TEMPLATES = Map.of();

	private BionicAnatomyRegistry() { super(new Gson(), "bionic_anatomy"); }
	public static long generation() {
		return GENERATION.get() * 31L + BionicTraitCarrierRegistry.generation();
	}

	@Nullable
	private static Template get(SurgicalAssembly.Source source) {
		for (Template template : TEMPLATES.getOrDefault(source.profile().entityTypeId(), List.of()))
			if (template.matches(source))
				return template;
		return null;
	}

	/**
	 * Every original role known for this source: a matching template, saved legacy roles, the model
	 * parts' ordinary anatomical names, parts named by any trait's carriers, and the captured head.
	 * Roles survive cutting unchanged. Generic names are important for coverage denominators: an
	 * ordinary leg still belongs to the leg region even when its species grants no leg trait.
	 */
	public static Map<BionicAnatomyRole, BitSet> roles(SurgicalAssembly.Source source) {
		EnumMap<BionicAnatomyRole, BitSet> roles = new EnumMap<>(BionicAnatomyRole.class);
		Template configured = get(source);
		if (configured != null)
			configured.roles.forEach((role, cubes) -> merge(roles, role, cubes));
		source.anatomy().roles().forEach((role, cubes) -> merge(roles, role, cubes));
		inferNamedRoles(source.anatomy().parts(), roles);
		BionicTraitCarrierRegistry.rolesFor(source.profile().entityTypeId(),
			source.anatomy().parts(), source.cubeCount())
			.forEach((role, cubes) -> merge(roles, role, cubes));
		merge(roles, BionicAnatomyRole.HEAD, BionicTissue.capturedHead(source));
		return roles;
	}

	/**
	 * Infer only names whose anatomical meaning is stable across ordinary entity models. Ambiguous
	 * names such as {@code body} remain the responsibility of entity-specific carrier data or an
	 * anatomy template.
	 */
	private static void inferNamedRoles(Map<Integer, java.util.Set<String>> parts,
		EnumMap<BionicAnatomyRole, BitSet> roles) {
		parts.forEach((cube, names) -> names.forEach(label -> {
			String name = canonicalName(label);
			boolean leg = containsAny(name, "leg", "foot", "feet", "paw", "hoof");
			if (name.contains("head") || name.contains("skull"))
				set(roles, BionicAnatomyRole.HEAD, cube);
			if (name.contains("gill"))
				set(roles, BionicAnatomyRole.GILL, cube);
			if (containsAny(name, "torso", "chest"))
				set(roles, BionicAnatomyRole.TORSO, cube);
			if (leg) {
				set(roles, BionicAnatomyRole.LEG, cube);
				// Existing support-foot rules deliberately treat the whole named leg as the foot region.
				set(roles, BionicAnatomyRole.FOOT, cube);
			}
			if (name.contains("wing")) {
				if (name.contains("left"))
					set(roles, BionicAnatomyRole.LEFT_WING, cube);
				if (name.contains("right"))
					set(roles, BionicAnatomyRole.RIGHT_WING, cube);
			}
			if (name.contains("fin"))
				set(roles, BionicAnatomyRole.FIN, cube);
			if (name.contains("tail"))
				set(roles, BionicAnatomyRole.TAIL, cube);
			if (name.contains("tentacle"))
				set(roles, BionicAnatomyRole.TENTACLE, cube);
			if (containsAny(name, "shell", "carapace"))
				set(roles, BionicAnatomyRole.SHELL, cube);
			if (containsAny(name, "spine", "spike", "stinger", "quill", "thorn"))
				set(roles, BionicAnatomyRole.SPINE, cube);
			if (containsAny(name, "mouth", "fang", "jaw", "beak", "snout", "muzzle"))
				set(roles, BionicAnatomyRole.MOUTH, cube);
			if (name.contains("arm") || name.contains("hand"))
				set(roles, BionicAnatomyRole.ATTACK_HAND, cube);
		}));
	}

	private static String canonicalName(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private static boolean containsAny(String name, String... parts) {
		for (String part : parts)
			if (name.contains(part))
				return true;
		return false;
	}

	private static void set(EnumMap<BionicAnatomyRole, BitSet> roles,
		BionicAnatomyRole role, int cube) {
		roles.computeIfAbsent(role, ignored -> new BitSet()).set(cube);
	}

	private static void merge(EnumMap<BionicAnatomyRole, BitSet> roles, BionicAnatomyRole role,
		BitSet cubes) {
		if (!cubes.isEmpty())
			roles.computeIfAbsent(role, ignored -> new BitSet()).or(cubes);
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager,
		ProfilerFiller profiler) {
		Map<ResourceLocation, List<Template>> loaded = new HashMap<>();
		resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
			String path = entry.getKey().getPath();
			int slash = path.indexOf('/');
			if (slash <= 0 || slash == path.length() - 1) {
				LOGGER.warn("Ignoring anatomy {}: expected <entity_namespace>/<entity_path>.json",
					entry.getKey());
				return;
			}
			ResourceLocation entityId = ResourceLocation.tryParse(
				path.substring(0, slash) + ":" + path.substring(slash + 1));
			try {
				if (entry.getValue().isJsonObject()
					&& entry.getValue().getAsJsonObject().has("entity_type"))
					entityId = ResourceLocation.tryParse(entry.getValue().getAsJsonObject()
						.get("entity_type").getAsString());
			} catch (RuntimeException exception) {
				LOGGER.warn("Ignoring invalid anatomy entity selector in {}", entry.getKey());
				return;
			}
			Template template = parse(entry.getValue());
			if (entityId == null || template == null) {
				LOGGER.warn("Ignoring invalid anatomy template {}", entry.getKey());
				return;
			}
			loaded.computeIfAbsent(entityId, ignored -> new ArrayList<>()).add(template);
		});
		Map<ResourceLocation, List<Template>> frozen = new HashMap<>();
		loaded.forEach((entity, variants) -> {
			variants.sort(java.util.Comparator.comparing((Template template) -> template.baby == null));
			frozen.put(entity, List.copyOf(variants));
		});
		TEMPLATES = Map.copyOf(frozen);
		GENERATION.incrementAndGet();
	}

	@Nullable
	private static Template parse(JsonElement element) {
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
			EnumMap<BionicAnatomyRole, BitSet> roles = new EnumMap<>(BionicAnatomyRole.class);
			for (Map.Entry<String, JsonElement> entry : rolesJson.entrySet()) {
				BionicAnatomyRole role = BionicAnatomyRole.valueOf(entry.getKey().toUpperCase(
					java.util.Locale.ROOT));
				JsonArray ids = entry.getValue().getAsJsonArray();
				BitSet cubes = new BitSet(count);
				for (JsonElement id : ids) {
					int cube = id.getAsInt();
					if (cube < 0 || cube >= count || cubes.get(cube))
						return null;
					cubes.set(cube);
				}
				roles.put(role, cubes);
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

	private static final class Template {
		private final int cubeCount;
		private final Map<BionicAnatomyRole, BitSet> roles;
		@Nullable private final List<SurgicalAssembly.Seam> seams;
		@Nullable private final Boolean baby;

		private Template(int cubeCount, Map<BionicAnatomyRole, BitSet> roles,
			@Nullable List<SurgicalAssembly.Seam> seams, @Nullable Boolean baby) {
			this.cubeCount = cubeCount;
			EnumMap<BionicAnatomyRole, BitSet> copy = new EnumMap<>(BionicAnatomyRole.class);
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
