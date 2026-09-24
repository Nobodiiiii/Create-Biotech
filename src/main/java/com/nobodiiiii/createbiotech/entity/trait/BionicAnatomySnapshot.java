package com.nobodiiiii.createbiotech.entity.trait;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Original model-part labels and legacy roles retained across every cut. */
public final class BionicAnatomySnapshot {
	private static final String MODEL_PARTS_TAG = "ModelParts";
	public static final BionicAnatomySnapshot EMPTY = new BionicAnatomySnapshot(Map.of(), Map.of());
	private final Map<BionicAnatomyRole, BitSet> roles;
	private final Map<Integer, Set<String>> parts;

	private BionicAnatomySnapshot(Map<BionicAnatomyRole, BitSet> roles,
		Map<Integer, Set<String>> parts) {
		EnumMap<BionicAnatomyRole, BitSet> copy = new EnumMap<>(BionicAnatomyRole.class);
		roles.forEach((role, cubes) -> {
			if (role != null && cubes != null && !cubes.isEmpty())
				copy.put(role, (BitSet) cubes.clone());
		});
		this.roles = Map.copyOf(copy);
		Map<Integer, Set<String>> names = new HashMap<>();
		parts.forEach((cube, labels) -> {
			if (labels != null && !labels.isEmpty())
				names.put(cube, Set.copyOf(labels));
		});
		this.parts = Map.copyOf(names);
	}

	@Nullable
	public static BionicAnatomySnapshot of(Map<BionicAnatomyRole, BitSet> roles, int cubeCount) {
		if (roles == null || cubeCount < 0)
			return null;
		for (Map.Entry<BionicAnatomyRole, BitSet> entry : roles.entrySet())
			if (entry.getKey() == null || entry.getValue() == null
				|| entry.getValue().length() > cubeCount)
				return null;
		return roles.isEmpty() ? EMPTY : new BionicAnatomySnapshot(roles, Map.of());
	}

	@Nullable
	public static BionicAnatomySnapshot ofParts(Map<Integer, Set<String>> parts, int cubeCount) {
		if (parts == null || cubeCount < 0 || parts.size() > cubeCount)
			return null;
		for (Map.Entry<Integer, Set<String>> entry : parts.entrySet()) {
			Integer cube = entry.getKey();
			Set<String> names = entry.getValue();
			if (cube == null || cube < 0 || cube >= cubeCount || names == null
				|| names.size() > 16 || names.stream().anyMatch(name -> name == null
					|| !name.matches("[A-Za-z0-9_./-]{1,64}")))
				return null;
		}
		return parts.isEmpty() ? EMPTY : new BionicAnatomySnapshot(Map.of(), parts);
	}

	@Nullable
	public static BionicAnatomySnapshot of(Map<BionicAnatomyRole, BitSet> roles,
		Map<Integer, Set<String>> parts, int cubeCount) {
		BionicAnatomySnapshot old = of(roles, cubeCount);
		BionicAnatomySnapshot named = ofParts(parts, cubeCount);
		return old == null || named == null ? null
			: new BionicAnatomySnapshot(old.roles, named.parts);
	}

	public BitSet cubes(BionicAnatomyRole role) {
		BitSet cubes = roles.get(role);
		return cubes == null ? new BitSet() : (BitSet) cubes.clone();
	}

	public boolean isEmpty() { return roles.isEmpty() && parts.isEmpty(); }
	public Map<Integer, Set<String>> parts() { return parts; }

	public Map<BionicAnatomyRole, BitSet> roles() {
		EnumMap<BionicAnatomyRole, BitSet> copy = new EnumMap<>(BionicAnatomyRole.class);
		roles.forEach((role, cubes) -> copy.put(role, (BitSet) cubes.clone()));
		return copy;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		roles.forEach((role, cubes) -> tag.putLongArray(role.name(), cubes.toLongArray()));
		if (!parts.isEmpty()) {
			ListTag encoded = new ListTag();
			parts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
				CompoundTag part = new CompoundTag();
				part.putInt("Cube", entry.getKey());
				ListTag labels = new ListTag();
				entry.getValue().stream().sorted().forEach(name -> labels.add(StringTag.valueOf(name)));
				part.put("Names", labels);
				encoded.add(part);
			});
			tag.put(MODEL_PARTS_TAG, encoded);
		}
		return tag;
	}

	@Nullable
	public static BionicAnatomySnapshot load(CompoundTag tag, int cubeCount) {
		EnumMap<BionicAnatomyRole, BitSet> found = new EnumMap<>(BionicAnatomyRole.class);
		for (String key : tag.getAllKeys()) {
			if (key.equals(MODEL_PARTS_TAG))
				continue;
			BionicAnatomyRole role;
			try { role = BionicAnatomyRole.valueOf(key); }
			catch (IllegalArgumentException exception) { return null; }
			if (!tag.contains(key, Tag.TAG_LONG_ARRAY))
				return null;
			found.put(role, BitSet.valueOf(tag.getLongArray(key)));
		}
		BionicAnatomySnapshot legacy = of(found, cubeCount);
		if (legacy == null)
			return null;
		if (!tag.contains(MODEL_PARTS_TAG))
			return legacy;
		if (!tag.contains(MODEL_PARTS_TAG, Tag.TAG_LIST))
			return null;
		ListTag encoded = tag.getList(MODEL_PARTS_TAG, Tag.TAG_COMPOUND);
		Map<Integer, Set<String>> parts = new HashMap<>();
		for (int index = 0; index < encoded.size(); index++) {
			CompoundTag part = encoded.getCompound(index);
			if (!part.contains("Cube", Tag.TAG_ANY_NUMERIC)
				|| !part.contains("Names", Tag.TAG_LIST))
				return null;
			ListTag labels = part.getList("Names", Tag.TAG_STRING);
			Set<String> names = new java.util.HashSet<>();
			for (int label = 0; label < labels.size(); label++)
				names.add(labels.getString(label));
			if (parts.putIfAbsent(part.getInt("Cube"), names) != null)
				return null;
		}
		BionicAnatomySnapshot named = ofParts(parts, cubeCount);
		return named == null ? null : new BionicAnatomySnapshot(legacy.roles, named.parts);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof BionicAnatomySnapshot snapshot
			&& roles.equals(snapshot.roles) && parts.equals(snapshot.parts);
	}

	@Override
	public int hashCode() { return 31 * roles.hashCode() + parts.hashCode(); }
}
