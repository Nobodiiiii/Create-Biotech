package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Original model names, parent provenance and measured cube volumes retained across every cut. */
public final class BionicAnatomySnapshot {
	private static final int MAX_NODES = 4096;
	private static final int MAX_NAMES = 32768;
	private static final String CUBE_VOLUMES_TAG = "CubeVolumes";
	private static final double MAX_CUBE_VOLUME = SurgicalAssembly.MAX_BODY_SIZE
		* SurgicalAssembly.MAX_BODY_SIZE * SurgicalAssembly.MAX_BODY_SIZE;
	private static final float[] NO_VOLUMES = new float[0];
	private static final java.util.regex.Pattern PART_NAME =
		java.util.regex.Pattern.compile("[A-Za-z0-9_./-]{1,64}");
	public static final BionicAnatomySnapshot EMPTY =
		new BionicAnatomySnapshot(Map.of(), Map.of(), NO_VOLUMES);

	/** Whether {@code name} is a storable model-part label. */
	public static boolean validPartName(String name) {
		return name != null && PART_NAME.matcher(name).matches();
	}
	private final Map<Integer, Set<String>> parts;
	private final Map<Integer, Set<BionicModelNode>> nodes;
	private final float[] volumes;
	private final double originalVolume;

	private BionicAnatomySnapshot(Map<Integer, Set<String>> parts, Map<Integer, Set<BionicModelNode>> nodes, float[] volumes) {
		Map<Integer, Set<String>> names = new HashMap<>();
		parts.forEach((cube, labels) -> {
			if (labels != null && !labels.isEmpty())
				names.put(cube, Set.copyOf(labels));
		});
		this.parts = Map.copyOf(names);
		Map<Integer, Set<BionicModelNode>> paths = new HashMap<>();
		nodes.forEach((cube, value) -> { if (!value.isEmpty()) paths.put(cube, Set.copyOf(value)); });
		this.nodes = Map.copyOf(paths);
		this.volumes = volumes.length == 0 ? NO_VOLUMES : volumes.clone();
		double total = 0;
		for (float volume : this.volumes) total += volume;
		this.originalVolume = total;
	}

	@Nullable
	public static BionicAnatomySnapshot ofParts(Map<Integer, Set<String>> parts, int cubeCount) {
		return ofParts(parts, Map.of(), cubeCount);
	}

	@Nullable
	public static BionicAnatomySnapshot ofParts(Map<Integer, Set<String>> parts,
		Map<Integer, Set<BionicModelNode>> nodes, int cubeCount) {
		if (nodes == null || nodes.size() > cubeCount || nodes.entrySet().stream().anyMatch(entry ->
			entry.getKey() == null || entry.getKey() < 0 || entry.getKey() >= cubeCount || entry.getValue() == null
			|| entry.getValue().size() > 16 || entry.getValue().stream().anyMatch(java.util.Objects::isNull))) return null;
		if (parts == null || cubeCount < 0 || cubeCount > SurgicalAssembly.MAX_CUBES || parts.size() > cubeCount)
			return null;
		for (Map.Entry<Integer, Set<String>> entry : parts.entrySet()) {
			Integer cube = entry.getKey();
			Set<String> names = entry.getValue();
			if (cube == null || cube < 0 || cube >= cubeCount || names == null
				|| names.size() > 16 || names.stream().anyMatch(name -> name == null
					|| !validPartName(name)))
				return null;
		}
		Set<BionicModelNode> unique = new java.util.HashSet<>();
		for (Set<BionicModelNode> paths : nodes.values()) for (BionicModelNode leaf : paths)
			for (BionicModelNode node = leaf; node != null && unique.add(node); node = node.parent())
				if (unique.size() > MAX_NODES) return null;
		Set<String> allNames = new java.util.HashSet<>();
		parts.values().forEach(allNames::addAll); unique.forEach(node -> allNames.addAll(node.names()));
		if (allNames.size() > MAX_NAMES) return null;
		return parts.isEmpty() && nodes.isEmpty() ? EMPTY : new BionicAnatomySnapshot(parts, nodes, NO_VOLUMES);
	}

	/** Adds one captured volume per original cube; {@link #fits} checks the count against a model. */
	@Nullable
	public BionicAnatomySnapshot withVolumes(float[] volumes) {
		if (volumes == null || volumes.length == 0 || volumes.length > SurgicalAssembly.MAX_CUBES)
			return null;
		for (float volume : volumes)
			if (!validVolume(volume))
				return null;
		return new BionicAnatomySnapshot(parts, nodes, volumes);
	}

	private static boolean validVolume(float volume) {
		return Float.isFinite(volume) && volume > 0.0f && volume <= MAX_CUBE_VOLUME;
	}

	/** Whether saved indices and measurements fit this original model. */
	public boolean fits(int cubeCount) {
		return nodes.keySet().stream().allMatch(cube -> cube >= 0 && cube < cubeCount)
			&& parts.keySet().stream().allMatch(cube -> cube >= 0 && cube < cubeCount)
			&& (volumes.length == 0 || volumes.length == cubeCount);
	}

	public boolean isEmpty() { return parts.isEmpty() && nodes.isEmpty() && volumes.length == 0; }
	public Map<Integer, Set<String>> parts() { return parts; }
	public Map<Integer, Set<BionicModelNode>> nodes() { return nodes; }

	/** Match each unique node/name group once per definition, then reuse it for all its cubes. */
	public BitSet select(BionicPartDefinition part, net.minecraft.resources.ResourceLocation entity) {
		BitSet selected = new BitSet();
		Map<Set<String>, Boolean> labels = new HashMap<>();
		parts.forEach((cube, names) -> {
			if (labels.computeIfAbsent(names, key -> key.stream().anyMatch(name -> part.matches(entity, name)))) selected.set(cube);
		});
		Map<BionicModelNode, Boolean> matches = new HashMap<>();
		class NodeMatches {
			boolean matches(BionicModelNode node) {
				Boolean known = matches.get(node); if (known != null) return known;
				boolean result = node.names().stream().anyMatch(name -> part.matches(entity, name))
					|| part.includeChildren() && node.parent() != null && matches(node.parent());
				matches.put(node, result); return result;
			}
		}
		NodeMatches matcher = new NodeMatches();
		nodes.forEach((cube, paths) -> { if (paths.stream().anyMatch(matcher::matches)) selected.set(cube); });
		return selected;
	}
	public boolean hasVolumes() { return volumes.length > 0; }
	public float[] volumes() { return volumes.clone(); }
	/** Complete captured donor volume, including cubes removed by later cuts. */
	public double originalVolume() { return originalVolume; }

	/** Captured volume in blocks³. */
	public double volume(int cube) {
		return cube >= 0 && cube < volumes.length ? volumes[cube] : Double.NaN;
	}

	/** Captured volume of the selected cubes in blocks³. */
	public double volume(BitSet cubes) {
		double volume = 0.0d;
		for (int cube = cubes.nextSetBit(0); cube >= 0 && cube < volumes.length;
			cube = cubes.nextSetBit(cube + 1))
			volume += volumes[cube];
		return volume;
	}

	/** Names and parent nodes are dictionaries; cubes only contain integer references. */
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		java.util.List<String> names = new java.util.ArrayList<>();
		Map<String, Integer> nameIds = new java.util.LinkedHashMap<>();
		java.util.function.Function<String, Integer> internName = name -> nameIds.computeIfAbsent(name, key -> {
			if (names.size() >= MAX_NAMES) throw new IllegalArgumentException("Too many anatomy names");
			names.add(key); return names.size() - 1;
		});
		ListTag encodedNodes = new ListTag();
		Map<BionicModelNode, Integer> nodeIds = new HashMap<>();
		class Nodes {
			int intern(BionicModelNode node) {
				Integer known = nodeIds.get(node); if (known != null) return known;
				int parent = node.parent() == null ? -1 : intern(node.parent());
				if (encodedNodes.size() >= MAX_NODES) throw new IllegalArgumentException("Too many anatomy nodes");
				CompoundTag value = new CompoundTag();
				value.putInt("Parent", parent);
				value.putIntArray("Names", node.names().stream().sorted().mapToInt(internName::apply).toArray());
				int id = encodedNodes.size(); encodedNodes.add(value); nodeIds.put(node, id); return id;
			}
		}
		Nodes nodePool = new Nodes();
		ListTag cubes = new ListTag();
		Set<Integer> ids = new java.util.TreeSet<>(parts.keySet()); ids.addAll(nodes.keySet());
		for (int cube : ids) {
			CompoundTag value = new CompoundTag(); value.putInt("Cube", cube);
			value.putIntArray("Names", parts.getOrDefault(cube, Set.of()).stream().sorted().mapToInt(internName::apply).toArray());
			value.putIntArray("Nodes", nodes.getOrDefault(cube, Set.of()).stream()
				.sorted(java.util.Comparator.comparing(Object::toString)).mapToInt(nodePool::intern).toArray());
			cubes.add(value);
		}
		ListTag dictionary = new ListTag(); names.forEach(name -> dictionary.add(StringTag.valueOf(name)));
		tag.put("Names", dictionary); tag.put("Nodes", encodedNodes); tag.put("Cubes", cubes);
		if (volumes.length > 0) {
			int[] bits = new int[volumes.length];
			for (int cube = 0; cube < volumes.length; cube++) bits[cube] = Float.floatToIntBits(volumes[cube]);
			tag.putIntArray(CUBE_VOLUMES_TAG, bits);
		}
		return tag;
	}

	@Nullable
	public static BionicAnatomySnapshot load(CompoundTag tag, int cubeCount) {
		if (tag == null || cubeCount < 0 || cubeCount > SurgicalAssembly.MAX_CUBES) return null;
		if (tag.isEmpty()) return EMPTY;
		try {
			if (!tag.contains("Names", Tag.TAG_LIST) || !tag.contains("Nodes", Tag.TAG_LIST)
				|| !tag.contains("Cubes", Tag.TAG_LIST)) return null;
			for (String key : java.util.List.of("Names", "Nodes", "Cubes")) {
				ListTag list = (ListTag) tag.get(key);
				if (!list.isEmpty() && list.getElementType() != (key.equals("Names") ? Tag.TAG_STRING : Tag.TAG_COMPOUND)) return null;
			}
			ListTag dictionary = tag.getList("Names", Tag.TAG_STRING);
			if (dictionary.size() > MAX_NAMES) return null;
			java.util.List<String> names = new java.util.ArrayList<>();
			for (int i = 0; i < dictionary.size(); i++) {
				String name = dictionary.getString(i); if (!validPartName(name)) return null; names.add(name);
			}
			java.util.function.Function<int[], Set<String>> resolveNames = refs -> {
				if (refs.length > 16) throw new IllegalArgumentException("Too many model aliases");
				Set<String> result = new java.util.HashSet<>();
				for (int ref : refs) { if (ref < 0 || ref >= names.size() || !result.add(names.get(ref)))
					throw new IllegalArgumentException("Invalid name reference"); }
				return Set.copyOf(result);
			};
			java.util.List<BionicModelNode> nodes = new java.util.ArrayList<>();
			ListTag encodedNodes = tag.getList("Nodes", Tag.TAG_COMPOUND);
			if (encodedNodes.size() > MAX_NODES) return null;
			for (int i = 0; i < encodedNodes.size(); i++) {
				CompoundTag node = encodedNodes.getCompound(i); int parent = node.getInt("Parent");
				if (!node.contains("Parent", Tag.TAG_INT) || !node.contains("Names", Tag.TAG_INT_ARRAY)
					|| parent < -1 || parent >= i) return null;
				nodes.add(new BionicModelNode(resolveNames.apply(node.getIntArray("Names")), parent < 0 ? null : nodes.get(parent)));
			}
			Map<Integer, Set<String>> parts = new HashMap<>();
			Map<Integer, Set<BionicModelNode>> paths = new HashMap<>();
			ListTag cubes = tag.getList("Cubes", Tag.TAG_COMPOUND);
			if (cubes.size() > cubeCount) return null;
			for (int i = 0; i < cubes.size(); i++) {
				CompoundTag cube = cubes.getCompound(i); int id = cube.getInt("Cube");
				if (!cube.contains("Cube", Tag.TAG_INT) || !cube.contains("Names", Tag.TAG_INT_ARRAY)
					|| !cube.contains("Nodes", Tag.TAG_INT_ARRAY) || parts.containsKey(id)) return null;
				parts.put(id, resolveNames.apply(cube.getIntArray("Names")));
				int[] refs = cube.getIntArray("Nodes"); if (refs.length > 16) return null;
				Set<BionicModelNode> resolved = new java.util.HashSet<>();
				for (int ref : refs) if (ref < 0 || ref >= nodes.size() || !resolved.add(nodes.get(ref))) return null;
				paths.put(id, resolved);
			}
			BionicAnatomySnapshot result = ofParts(parts, paths, cubeCount);
			if (result == null) return null;
			if (tag.contains(CUBE_VOLUMES_TAG)) {
				if (!tag.contains(CUBE_VOLUMES_TAG, Tag.TAG_INT_ARRAY)) return null;
				int[] bits = tag.getIntArray(CUBE_VOLUMES_TAG); if (bits.length != cubeCount) return null;
				float[] measured = new float[bits.length];
				for (int i = 0; i < bits.length; i++) measured[i] = Float.intBitsToFloat(bits[i]);
				return result.withVolumes(measured);
			}
			return result;
		} catch (IllegalArgumentException exception) { return null; }
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof BionicAnatomySnapshot snapshot
			&& parts.equals(snapshot.parts)
			&& nodes.equals(snapshot.nodes)
			&& Arrays.equals(volumes, snapshot.volumes);
	}

	@Override
	public int hashCode() {
		return 31 * (31 * parts.hashCode() + nodes.hashCode()) + Arrays.hashCode(volumes);
	}
}
