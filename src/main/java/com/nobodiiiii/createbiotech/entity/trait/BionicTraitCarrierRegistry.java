package com.nobodiiiii.createbiotech.entity.trait;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Reads carrier model-part names from the same JSON files as each special trait. */
public final class BionicTraitCarrierRegistry extends SimpleJsonResourceReloadListener {
	public static final BionicTraitCarrierRegistry HEAD =
		new BionicTraitCarrierRegistry("bionic_head_traits");
	public static final BionicTraitCarrierRegistry ORGAN =
		new BionicTraitCarrierRegistry("bionic_organ_traits");
	private static final AtomicLong GENERATION = new AtomicLong();
	private final Set<String> traitIds;
	private volatile Map<String, Map<ResourceLocation, Map<BionicAnatomyRole, Set<String>>>> carriers = Map.of();

	private BionicTraitCarrierRegistry(String directory) {
		super(new Gson(), directory);
		traitIds = directory.equals("bionic_head_traits")
			? java.util.Arrays.stream(BionicHeadTrait.values()).map(BionicHeadTrait::id)
				.collect(java.util.stream.Collectors.toUnmodifiableSet())
			: java.util.Arrays.stream(BionicOrganTrait.values()).map(BionicOrganTrait::id)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public static long generation() { return GENERATION.get(); }

	public static Map<BionicAnatomyRole, BitSet> rolesFor(ResourceLocation entity,
		Map<Integer, Set<String>> parts, int cubeCount) {
		EnumMap<BionicAnatomyRole, BitSet> result = new EnumMap<>(BionicAnatomyRole.class);
		HEAD.carriers.values().forEach(byEntity -> apply(byEntity.get(entity), parts, cubeCount, result));
		ORGAN.carriers.values().forEach(byEntity -> apply(byEntity.get(entity), parts, cubeCount, result));
		return result;
	}

	/** Null means this trait has no usable carrier rule, so saved roles or a template may apply. */
	@Nullable
	public static BitSet headCubes(BionicHeadTrait trait, ResourceLocation entity,
		Map<Integer, Set<String>> parts, int cubeCount, BionicAnatomyRole role) {
		return HEAD.cubesFor(trait.id(), entity, parts, cubeCount, role);
	}

	@Nullable
	public static BitSet organCubes(BionicOrganTrait trait, ResourceLocation entity,
		Map<Integer, Set<String>> parts, int cubeCount, BionicAnatomyRole role) {
		return ORGAN.cubesFor(trait.id(), entity, parts, cubeCount, role);
	}

	@Nullable
	private BitSet cubesFor(String trait, ResourceLocation entity,
		Map<Integer, Set<String>> parts, int cubeCount, BionicAnatomyRole role) {
		Map<BionicAnatomyRole, Set<String>> rules = carriers.getOrDefault(trait, Map.of())
			.get(entity);
		if (rules == null || !rules.containsKey(role))
			return null;
		Set<String> names = rules.get(role);
		if (parts.isEmpty() && names.stream().noneMatch(name -> name.startsWith("#")))
			return null;
		return selected(names, parts, cubeCount);
	}

	private static void apply(Map<BionicAnatomyRole, Set<String>> rules,
		Map<Integer, Set<String>> parts, int cubeCount,
		EnumMap<BionicAnatomyRole, BitSet> result) {
		if (rules == null)
			return;
		rules.forEach((role, names) -> result.computeIfAbsent(role, ignored -> new BitSet())
			.or(selected(names, parts, cubeCount)));
	}

	private static BitSet selected(Set<String> names, Map<Integer, Set<String>> parts,
		int cubeCount) {
		BitSet result = new BitSet();
		for (String name : names)
			if (name.startsWith("#")) {
				int cube = Integer.parseInt(name.substring(1));
				if (cube < cubeCount)
					result.set(cube);
			}
		parts.forEach((cube, labels) -> {
			if (labels.stream().anyMatch(names::contains))
				result.set(cube);
		});
		return result;
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager,
		ProfilerFiller profiler) {
		Map<String, Map<ResourceLocation, EnumMap<BionicAnatomyRole, Set<String>>>> loaded =
			new HashMap<>();
		resources.entrySet().stream().sorted(Map.Entry.comparingByKey())
			.filter(file -> file.getKey().getNamespace().equals(CreateBiotech.MOD_ID)
				&& traitIds.contains(file.getKey().getPath()))
			.forEach(file -> {
			if (!file.getValue().isJsonObject())
				return;
			JsonObject root = file.getValue().getAsJsonObject();
			if (!root.has("carriers"))
				return;
			if (!root.get("carriers").isJsonObject()) {
				LogUtils.getLogger().warn("Invalid carrier map in {}", file.getKey());
				return;
			}
			for (Map.Entry<String, JsonElement> entityEntry : root.getAsJsonObject("carriers").entrySet()) {
				ResourceLocation entity = ResourceLocation.tryParse(entityEntry.getKey());
				if (entity == null || !entityEntry.getValue().isJsonObject()) {
					LogUtils.getLogger().warn("Invalid carrier entity in {}: {}", file.getKey(),
						entityEntry.getKey());
					continue;
				}
				EnumMap<BionicAnatomyRole, Set<String>> byRole = loaded
					.computeIfAbsent(file.getKey().getPath(), ignored -> new HashMap<>())
					.computeIfAbsent(entity, ignored -> new EnumMap<>(BionicAnatomyRole.class));
				for (Map.Entry<String, JsonElement> roleEntry : entityEntry.getValue()
					.getAsJsonObject().entrySet()) {
					BionicAnatomyRole role;
					try { role = BionicAnatomyRole.valueOf(roleEntry.getKey()); }
					catch (IllegalArgumentException exception) {
						LogUtils.getLogger().warn("Invalid carrier role in {}: {}", file.getKey(),
							roleEntry.getKey());
						continue;
					}
					if (!roleEntry.getValue().isJsonArray())
						continue;
					Set<String> names = byRole.computeIfAbsent(role, ignored -> new HashSet<>());
					for (JsonElement name : roleEntry.getValue().getAsJsonArray()) {
						if (name.isJsonPrimitive() && name.getAsJsonPrimitive().isNumber()) {
							try {
								int cube = name.getAsBigDecimal().intValueExact();
								if (cube >= 0 && cube < SurgicalAssembly.MAX_CUBES)
									names.add("#" + cube);
								else
									LogUtils.getLogger().warn("Invalid carrier cube in {}", file.getKey());
							} catch (RuntimeException exception) {
								LogUtils.getLogger().warn("Invalid carrier cube in {}", file.getKey());
							}
							continue;
						}
						if (!name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()
							|| !name.getAsString().matches("[A-Za-z0-9_./-]{1,64}")) {
							LogUtils.getLogger().warn("Invalid model part name in {}", file.getKey());
							continue;
						}
						names.add(name.getAsString());
					}
				}
			}
		});
		Map<String, Map<ResourceLocation, Map<BionicAnatomyRole, Set<String>>>> frozen = new HashMap<>();
		loaded.forEach((trait, byEntity) -> {
			Map<ResourceLocation, Map<BionicAnatomyRole, Set<String>>> entities = new HashMap<>();
			byEntity.forEach((entity, byRole) -> {
				EnumMap<BionicAnatomyRole, Set<String>> copy =
					new EnumMap<>(BionicAnatomyRole.class);
				byRole.forEach((role, names) -> copy.put(role, Set.copyOf(names)));
				entities.put(entity, Map.copyOf(copy));
			});
			frozen.put(trait, Map.copyOf(entities));
		});
		carriers = Map.copyOf(frozen);
		GENERATION.incrementAndGet();
	}
}
