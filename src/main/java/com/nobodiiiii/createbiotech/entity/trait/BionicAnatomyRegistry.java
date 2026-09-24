package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
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

/** Server-owned cube-role templates. Unknown layouts grant no template-based organ ability. */
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
	public static Template get(SurgicalAssembly.Source source) {
		for (Template template : TEMPLATES.getOrDefault(source.profile().entityTypeId(), List.of()))
			if (template.matches(source))
				return template;
		return null;
	}

	/** Captured cube roles remain valid after cutting; only the donor's species grants abilities. */
	@Nullable
	public static Template getForSource(SurgicalAssembly.Source source) {
		Template configured = get(source);
		BionicAnatomySnapshot anatomy = source.anatomy();
		Map<BionicAnatomyRole, BitSet> declared = BionicTraitCarrierRegistry.rolesFor(
			source.profile().entityTypeId(), anatomy.parts(), source.cubeCount());
		if (anatomy.isEmpty() && declared.values().stream().allMatch(BitSet::isEmpty))
			return configured;
		EnumMap<BionicAnatomyRole, BitSet> roles = new EnumMap<>(BionicAnatomyRole.class);
		if (configured != null)
			configured.roles.forEach((role, cubes) -> roles.put(role, (BitSet) cubes.clone()));
		roles.putAll(anatomy.roles());
		declared.forEach((role, cubes) -> {
			if (!cubes.isEmpty())
				roles.put(role, cubes);
		});
		if (roles.values().stream().allMatch(BitSet::isEmpty))
			return null;
		double[] weights = configured == null ? new double[source.cubeCount()]
			: configured.weights.clone();
		if (configured == null)
			java.util.Arrays.fill(weights, 1.0d);
		return new Template(source.cubeCount(), roles, weights, source.seams(), null);
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
			double[] weights = new double[count];
			java.util.Arrays.fill(weights, 1.0d);
			if (json.has("weights")) {
				JsonArray values = json.getAsJsonArray("weights");
				if (values.size() != count)
					return null;
				for (int cube = 0; cube < count; cube++) {
					weights[cube] = values.get(cube).getAsDouble();
					if (!Double.isFinite(weights[cube]) || weights[cube] <= 0.0d)
						return null;
				}
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
			return new Template(count, roles, weights, seams, baby);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	public static final class Template {
		private final int cubeCount;
		private final Map<BionicAnatomyRole, BitSet> roles;
		private final double[] weights;
		@Nullable private final List<SurgicalAssembly.Seam> seams;
		@Nullable private final Boolean baby;

		private Template(int cubeCount, Map<BionicAnatomyRole, BitSet> roles,
			double[] weights, @Nullable List<SurgicalAssembly.Seam> seams,
			@Nullable Boolean baby) {
			this.cubeCount = cubeCount;
			EnumMap<BionicAnatomyRole, BitSet> copy = new EnumMap<>(BionicAnatomyRole.class);
			roles.forEach((role, cubes) -> copy.put(role, (BitSet) cubes.clone()));
			this.roles = Map.copyOf(copy);
			this.weights = weights.clone();
			this.seams = seams == null ? null : List.copyOf(seams);
			this.baby = baby;
		}

		private boolean matches(SurgicalAssembly.Source source) {
			return source.cubeCount() == cubeCount
				&& (seams == null || seams.equals(source.seams()))
				&& (baby == null || baby.equals(source.profile().baby()));
		}

		public BitSet cubes(BionicAnatomyRole role) {
			BitSet cubes = roles.get(role);
			return cubes == null ? new BitSet() : (BitSet) cubes.clone();
		}

		public double weight(int cube) { return weights[cube]; }
	}
}
