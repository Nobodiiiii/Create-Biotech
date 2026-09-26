package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Current model-part labels and measured cube volumes retained across every cut. */
public final class BionicAnatomySnapshot {
	private static final String MODEL_PARTS_TAG = "ModelParts";
	private static final String CUBE_VOLUMES_TAG = "CubeVolumes";
	private static final double MAX_CUBE_VOLUME = SurgicalAssembly.MAX_BODY_SIZE
		* SurgicalAssembly.MAX_BODY_SIZE * SurgicalAssembly.MAX_BODY_SIZE;
	private static final float[] NO_VOLUMES = new float[0];
	public static final BionicAnatomySnapshot EMPTY =
		new BionicAnatomySnapshot(Map.of(), NO_VOLUMES);
	private final Map<Integer, Set<String>> parts;
	private final float[] volumes;

	private BionicAnatomySnapshot(Map<Integer, Set<String>> parts, float[] volumes) {
		Map<Integer, Set<String>> names = new HashMap<>();
		parts.forEach((cube, labels) -> {
			if (labels != null && !labels.isEmpty())
				names.put(cube, Set.copyOf(labels));
		});
		this.parts = Map.copyOf(names);
		this.volumes = volumes.length == 0 ? NO_VOLUMES : volumes.clone();
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
		return parts.isEmpty() ? EMPTY : new BionicAnatomySnapshot(parts, NO_VOLUMES);
	}

	/** Adds one captured volume per original cube; {@link #fits} checks the count against a model. */
	@Nullable
	public BionicAnatomySnapshot withVolumes(float[] volumes) {
		if (volumes == null || volumes.length == 0 || volumes.length > SurgicalAssembly.MAX_CUBES)
			return null;
		for (float volume : volumes)
			if (!validVolume(volume))
				return null;
		return new BionicAnatomySnapshot(parts, volumes);
	}

	private static boolean validVolume(float volume) {
		return Float.isFinite(volume) && volume > 0.0f && volume <= MAX_CUBE_VOLUME;
	}

	/** Whether every saved cube index belongs to a model with this many cubes. */
	public boolean fits(int cubeCount) {
		return parts.keySet().stream().allMatch(cube -> cube >= 0 && cube < cubeCount)
			&& volumes.length == cubeCount;
	}

	public boolean isEmpty() { return parts.isEmpty() && volumes.length == 0; }
	public Map<Integer, Set<String>> parts() { return parts; }
	public boolean hasVolumes() { return volumes.length > 0; }
	public float[] volumes() { return volumes.clone(); }

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

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
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
		if (volumes.length > 0) {
			ListTag encoded = new ListTag();
			for (float volume : volumes)
				encoded.add(FloatTag.valueOf(volume));
			tag.put(CUBE_VOLUMES_TAG, encoded);
		}
		return tag;
	}

	@Nullable
	public static BionicAnatomySnapshot load(CompoundTag tag, int cubeCount) {
		if (tag == null || cubeCount <= 0 || !tag.contains(CUBE_VOLUMES_TAG, Tag.TAG_LIST)
			|| tag.contains(MODEL_PARTS_TAG) && !tag.contains(MODEL_PARTS_TAG, Tag.TAG_LIST))
			return null;
		for (String key : tag.getAllKeys())
			if (!key.equals(MODEL_PARTS_TAG) && !key.equals(CUBE_VOLUMES_TAG))
				return null;
		Map<Integer, Set<String>> parts = new HashMap<>();
		if (tag.contains(MODEL_PARTS_TAG)) {
			ListTag encoded = tag.getList(MODEL_PARTS_TAG, Tag.TAG_COMPOUND);
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
		}
		BionicAnatomySnapshot named = ofParts(parts, cubeCount);
		if (named == null)
			return null;
		ListTag encodedVolumes = tag.getList(CUBE_VOLUMES_TAG, Tag.TAG_FLOAT);
		if (encodedVolumes.size() != cubeCount)
			return null;
		float[] volumes = new float[cubeCount];
		for (int cube = 0; cube < cubeCount; cube++)
			volumes[cube] = encodedVolumes.getFloat(cube);
		BionicAnatomySnapshot measured = named.withVolumes(volumes);
		return measured;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof BionicAnatomySnapshot snapshot
			&& parts.equals(snapshot.parts)
			&& Arrays.equals(volumes, snapshot.volumes);
	}

	@Override
	public int hashCode() {
		return 31 * parts.hashCode() + Arrays.hashCode(volumes);
	}
}
