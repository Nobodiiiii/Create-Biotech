package com.nobodiiiii.createbiotech.content.surgery;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;
import com.nobodiiiii.createbiotech.entity.trait.BionicModelNode;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Direct upgrades of published saves, never a chain through development schemas.
 * beta.1 (44c8bfd5) and beta.2 (4c6b5a57) both wrote table/assembly version 1.
 * See docs/surgical-save-compatibility.zh-CN.md before changing this allowlist.
 */
final class SurgicalReleaseMigration {
	// A model pixel cubed, used only when a published save has no volume measurement at all.
	private static final float UNMEASURED_CUBE_VOLUME = 1.0f / 4096.0f;
	private static final Map<String, String> ROLE_NAMES = Map.ofEntries(
		Map.entry("HEAD", "head"), Map.entry("GILL", "gill"), Map.entry("TORSO", "body"),
		Map.entry("LEG", "leg"), Map.entry("FOOT", "foot"), Map.entry("LEFT_WING", "left_wing"),
		Map.entry("RIGHT_WING", "right_wing"), Map.entry("FIN", "fin"), Map.entry("TAIL", "tail"),
		Map.entry("TENTACLE", "tentacle"), Map.entry("SHELL", "shell"), Map.entry("SPINE", "spine"),
		Map.entry("MOUTH", "mouth"), Map.entry("ATTACK_HAND", "arm"));

	private SurgicalReleaseMigration() {}

	static boolean supportsAssembly(int version) {
		return version == 1;
	}

	static boolean supportsTable(int version) {
		return version == 1;
	}

	/** The input may be a shared, read-only item snapshot. Only the returned copy is edited. */
	@Nullable
	static CompoundTag assembly(CompoundTag old, int currentVersion) {
		if (!supportsAssembly(old.getInt("Version")) || old.contains(SurgicalStorage.SHARED_TAG)
			|| !compoundList(old, "Sources", SurgicalAssembly.MAX_SOURCES)) return null;
		ListTag sources = old.getList("Sources", Tag.TAG_COMPOUND);
		if (sources.isEmpty()) return null;
		int presentCount = 0;
		for (int i = 0; i < sources.size(); i++) {
			CompoundTag source = sources.getCompound(i);
			if (!source.contains("PresentCubes", Tag.TAG_LONG_ARRAY)) return null;
			BitSet present = BitSet.valueOf(source.getLongArray("PresentCubes"));
			if (present.length() > SurgicalAssembly.MAX_CUBES) return null;
			presentCount += present.cardinality();
		}
		if (presentCount == 0 || presentCount > SurgicalAssembly.MAX_CUBES) return null;
		float fallback = UNMEASURED_CUBE_VOLUME;
		if (old.contains("BodyVolume", Tag.TAG_ANY_NUMERIC)) {
			double volume = old.getDouble("BodyVolume");
			if (!SurgicalHealthCalibration.validVolume(volume)) return null;
			float perCube = (float) (volume / presentCount);
			if (perCube > 0.0f) fallback = perCube;
		}
		ListTag converted = new ListTag();
		Map<UUID, BitSet> donorHeads = donorHeads(sources);
		for (int i = 0; i < sources.size(); i++) {
			CompoundTag source = subject(sources.getCompound(i), fallback, donorHeads);
			if (source == null) return null;
			converted.add(source);
		}
		// Shallow-copy untouched geometry; the current decoder never mutates it.
		CompoundTag result = shallowCopy(old);
		result.put("Sources", converted);
		result.putInt("Version", currentVersion);
		return result;
	}

	@Nullable
	static CompoundTag subject(CompoundTag old, Map<UUID, BitSet> donorHeads) {
		return subject(old, UNMEASURED_CUBE_VOLUME, donorHeads);
	}

	@Nullable
	private static CompoundTag subject(CompoundTag old, float fallbackVolume, Map<UUID, BitSet> donorHeads) {
		// These fields belong to unreleased schemas, even when an intermediate build reused v1.
		if (old.contains("DonorTemplate") || old.contains("Anatomy")
			|| !old.contains("CubeCount", Tag.TAG_ANY_NUMERIC)
			|| !old.contains("PresentCubes", Tag.TAG_LONG_ARRAY)) return null;
		int count = old.getInt("CubeCount");
		if (!SurgicalAssembly.validCubeCount(count)) return null;
		BitSet present = BitSet.valueOf(old.getLongArray("PresentCubes"));
		BitSet heads = bitSet(old, "HeadCubes", count);
		BitSet originalHeads = bitSet(old, "OriginalHeadCubes", count);
		if (heads == null || originalHeads == null || present.length() > count) return null;
		BitSet outside = (BitSet) heads.clone();
		outside.andNot(present);
		if (!outside.isEmpty()) return null;
		if (old.contains("OriginalHeadCubes")) {
			outside = (BitSet) heads.clone();
			outside.andNot(originalHeads);
			if (!outside.isEmpty()) return null;
		} else originalHeads = heads;
		if (old.hasUUID("DonorId")) {
			BitSet complete = donorHeads.get(old.getUUID("DonorId"));
			if (complete != null) originalHeads = complete;
		}
		if (originalHeads.length() > count) return null;
		if (old.contains("AnatomyRoles") && !old.contains("AnatomyRoles", Tag.TAG_COMPOUND)) return null;
		BionicAnatomySnapshot anatomy = anatomy(old.getCompound("AnatomyRoles"), count, originalHeads, fallbackVolume);
		if (anatomy == null) return null;
		CompoundTag result = shallowCopy(old);
		result.remove("HeadCubes");
		result.remove("OriginalHeadCubes");
		result.remove("AnatomyRoles");
		result.put("Anatomy", anatomy.save());
		return result;
	}

	/** Fragment-local head sets must become the same anatomy on every piece of one donor. */
	static Map<UUID, BitSet> donorHeads(ListTag subjects) {
		Map<UUID, BitSet> heads = new HashMap<>();
		for (int i = 0; i < subjects.size(); i++) {
			CompoundTag subject = subjects.getCompound(i);
			if (!subject.hasUUID("DonorId")) continue;
			String key = subject.contains("OriginalHeadCubes") ? "OriginalHeadCubes" : "HeadCubes";
			BitSet cubes = bitSet(subject, key, SurgicalAssembly.MAX_CUBES);
			if (cubes != null) heads.computeIfAbsent(subject.getUUID("DonorId"), ignored -> new BitSet()).or(cubes);
		}
		return heads;
	}

	@Nullable
	private static BionicAnatomySnapshot anatomy(CompoundTag old, int count, BitSet heads, float fallbackVolume) {
		Map<Integer, Set<String>> parts = new HashMap<>();
		Map<Integer, Set<BionicModelNode>> nodes = new HashMap<>();
		for (String key : old.getAllKeys()) {
			if (key.equals("ModelParts") || key.equals("CubeVolumes")) continue;
			String name = ROLE_NAMES.get(key);
			BitSet cubes = bitSet(old, key, count);
			if (name == null || cubes == null) return null;
			addRole(nodes, cubes, name);
		}
		// Retain the old head classification without inventing missing model ancestry.
		addRole(nodes, heads, "head");
		if (old.contains("ModelParts")) {
			if (!compoundList(old, "ModelParts", count)) return null;
			ListTag encoded = old.getList("ModelParts", Tag.TAG_COMPOUND);
			for (int i = 0; i < encoded.size(); i++) {
				CompoundTag cube = encoded.getCompound(i);
				if (!cube.contains("Cube", Tag.TAG_ANY_NUMERIC) || !cube.contains("Names", Tag.TAG_LIST)) return null;
				ListTag names = (ListTag) cube.get("Names");
				if (names.size() > 16 || !names.isEmpty() && names.getElementType() != Tag.TAG_STRING) return null;
				Set<String> labels = new HashSet<>();
				for (int j = 0; j < names.size(); j++) labels.add(names.getString(j));
				if (parts.putIfAbsent(cube.getInt("Cube"), labels) != null) return null;
			}
		}
		BionicAnatomySnapshot result = BionicAnatomySnapshot.ofParts(parts, nodes, count);
		if (result == null) return null;
		// beta.1 never measured individual cubes; beta.2 also accepted unmeasured old captures.
		// Materialize a stable estimate once instead of adding a fallback to every trait evaluation.
		float[] volumes = new float[count];
		Arrays.fill(volumes, fallbackVolume);
		ListTag measured = old.getList("CubeVolumes", Tag.TAG_FLOAT);
		if (measured.size() == count) {
			for (int i = 0; i < count; i++) volumes[i] = measured.getFloat(i);
			BionicAnatomySnapshot captured = result.withVolumes(volumes);
			if (captured != null) return captured;
			Arrays.fill(volumes, fallbackVolume);
		}
		return result.withVolumes(volumes);
	}

	private static void addRole(Map<Integer, Set<BionicModelNode>> nodes, BitSet cubes, String name) {
		BionicModelNode node = new BionicModelNode(Set.of(name), null);
		for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1))
			nodes.computeIfAbsent(cube, ignored -> new HashSet<>()).add(node);
	}

	@Nullable
	private static BitSet bitSet(CompoundTag tag, String key, int count) {
		if (!tag.contains(key)) return new BitSet();
		if (!tag.contains(key, Tag.TAG_LONG_ARRAY)) return null;
		BitSet cubes = BitSet.valueOf(tag.getLongArray(key));
		return cubes.length() <= count ? cubes : null;
	}

	private static boolean compoundList(CompoundTag tag, String key, int limit) {
		if (!(tag.get(key) instanceof ListTag list)) return false;
		return list.size() <= limit && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND);
	}

	private static CompoundTag shallowCopy(CompoundTag old) {
		CompoundTag result = new CompoundTag();
		for (String key : old.getAllKeys()) result.put(key, old.get(key));
		return result;
	}
}
