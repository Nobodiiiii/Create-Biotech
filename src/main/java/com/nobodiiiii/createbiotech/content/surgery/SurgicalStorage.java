package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Self-contained dictionaries for one table or packed body; never merges donor identities. */
final class SurgicalStorage {
	static final String SHARED_TAG = "SharedSurgicalData";
	private static final String DONORS_TAG = "DonorTemplates";
	private static final String DONOR_REF_TAG = "DonorTemplate";
	private static final String GLUE_TAG = "GlueJoints";
	private static final String COMBINATIONS_TAG = "Combinations";
	private static final String LIMBS_TAG = "LimbJoints";
	static final String GLUE_REFS_TAG = "GlueRefs";
	static final String COMBINATION_REFS_TAG = "CombinationRefs";
	static final String LIMB_REFS_TAG = "LimbRefs";

	private SurgicalStorage() {}

	static void writeDonor(CompoundTag tag, @Nullable Writer storage, MimicProfile profile,
		int cubeCount, List<SurgicalAssembly.Seam> seams,
		BionicAnatomySnapshot anatomy) {
		Donor donor = new Donor(profile, cubeCount, seams, anatomy);
		if (storage == null)
			donor.write(tag);
		else
			tag.putInt(DONOR_REF_TAG, storage.donorIds.computeIfAbsent(donor, storage.donors::intern));
	}

	@Nullable
	static Donor readDonor(CompoundTag tag, @Nullable Reader storage) {
		if (!tag.contains(DONOR_REF_TAG))
			return Donor.load(tag);
		if (storage == null || !tag.contains(DONOR_REF_TAG, Tag.TAG_INT))
			return null;
		return storage.donors.get(tag.getInt(DONOR_REF_TAG));
	}

	/** Immutable model data; the subject/source keeps all identity and editing state separately. */
	record Donor(MimicProfile profile, int cubeCount, List<SurgicalAssembly.Seam> seams,
		BionicAnatomySnapshot anatomy) {
		Donor {
			seams = List.copyOf(seams);
		}

		void write(CompoundTag tag) {
			tag.put("MimicProfile", profile.save());
			tag.putInt("CubeCount", cubeCount);
			tag.putIntArray("Seams", SurgicalAssembly.encodeSeams(seams));
			if (!anatomy.isEmpty())
				tag.put("Anatomy", anatomy.save());
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			write(tag);
			return tag;
		}

		@Nullable
		static Donor load(CompoundTag tag) {
			if (!tag.contains("MimicProfile", Tag.TAG_COMPOUND)
				|| !tag.contains("CubeCount", Tag.TAG_ANY_NUMERIC)
				|| !tag.contains("Seams", Tag.TAG_INT_ARRAY))
				return null;
			int count = tag.getInt("CubeCount");
			List<SurgicalAssembly.Seam> seams = SurgicalAssembly.decodeSeams(tag.getIntArray("Seams"));
			if (seams == null || !SurgicalAssembly.validTopology(count, seams))
				return null;
			MimicProfile profile = MimicProfile.load(tag.getCompound("MimicProfile"));
			BionicAnatomySnapshot anatomy = BionicAnatomySnapshot.load(tag.getCompound("Anatomy"), count);
			return profile == null || anatomy == null ? null
				: new Donor(profile, count, seams, anatomy);
		}
	}

	static final class Writer {
		private final Pool<Donor> donors = new Pool<>(Donor::save);
		private final Map<Donor, Integer> donorIds = new HashMap<>();
		private final Pool<SurgicalGlueJoint> glue = new Pool<>(SurgicalGlueJoint::save);
		private final Pool<SurgicalCombination> combinations = new Pool<>(SurgicalCombination::save);
		private final Pool<SurgicalLimbJoint> limbs = new Pool<>(SurgicalLimbJoint::save);

		void writeRelations(CompoundTag tag, List<SurgicalGlueJoint> glueJoints,
			List<SurgicalCombination> groups, List<SurgicalLimbJoint> limbJoints) {
			writeRefs(tag, GLUE_REFS_TAG, glueJoints, glue);
			writeRefs(tag, COMBINATION_REFS_TAG, groups, combinations);
			writeRefs(tag, LIMB_REFS_TAG, limbJoints, limbs);
		}

		void save(CompoundTag target) {
			CompoundTag shared = new CompoundTag();
			donors.save(shared, DONORS_TAG);
			glue.save(shared, GLUE_TAG);
			combinations.save(shared, COMBINATIONS_TAG);
			limbs.save(shared, LIMBS_TAG);
			if (!shared.isEmpty())
				target.put(SHARED_TAG, shared);
		}

		private static <T> void writeRefs(CompoundTag tag, String key, List<T> values, Pool<T> pool) {
			if (values.isEmpty())
				return;
			int[] refs = new int[values.size()];
			for (int index = 0; index < refs.length; index++)
				refs[index] = pool.intern(values.get(index));
			tag.putIntArray(key, refs);
		}
	}

	private static final class Pool<T> {
		private final ListTag encoded = new ListTag();
		private final Map<T, Integer> identities = new IdentityHashMap<>();
		// Use full serialized content, since e.g. SurgicalCombination.equals compares only its UUID.
		private final Map<CompoundTag, Integer> contents = new HashMap<>();
		private final Function<T, CompoundTag> encoder;

		Pool(Function<T, CompoundTag> encoder) { this.encoder = encoder; }

		int intern(T value) {
			Integer existing = identities.get(value);
			if (existing != null)
				return existing;
			CompoundTag tag = encoder.apply(value);
			int index = contents.computeIfAbsent(tag, key -> {
				int added = encoded.size();
				encoded.add(key);
				return added;
			});
			identities.put(value, index);
			return index;
		}

		void save(CompoundTag target, String key) {
			if (!encoded.isEmpty())
				target.put(key, encoded);
		}
	}

	static final class Reader {
		private final ReadPool<Donor> donors;
		private final ReadPool<SurgicalGlueJoint> glue;
		private final ReadPool<SurgicalCombination> combinations;
		private final ReadPool<SurgicalLimbJoint> limbs;

		Reader(CompoundTag source, int maxSubjects) {
			CompoundTag shared = source.getCompound(SHARED_TAG);
			donors = new ReadPool<>(shared, DONORS_TAG, maxSubjects, Donor::load);
			glue = new ReadPool<>(shared, GLUE_TAG, maxSubjects * SurgicalAssembly.MAX_SEAMS, SurgicalGlueJoint::load);
			combinations = new ReadPool<>(shared, COMBINATIONS_TAG, maxSubjects * SurgicalAssembly.MAX_CUBES,
				SurgicalCombination::load);
			limbs = new ReadPool<>(shared, LIMBS_TAG, maxSubjects * SurgicalAssembly.MAX_CUBES, SurgicalLimbJoint::load);
		}

		@Nullable List<SurgicalGlueJoint> glue(CompoundTag tag) {
			return glue.refs(tag, GLUE_REFS_TAG, SurgicalAssembly.MAX_SEAMS);
		}

		@Nullable List<SurgicalCombination> combinations(CompoundTag tag) {
			return combinations.refs(tag, COMBINATION_REFS_TAG, SurgicalAssembly.MAX_CUBES);
		}

		@Nullable List<SurgicalLimbJoint> limbs(CompoundTag tag) {
			return limbs.refs(tag, LIMB_REFS_TAG, SurgicalAssembly.MAX_CUBES);
		}
	}

	/** Decode each referenced definition once and reuse its immutable runtime objects. */
	private static final class ReadPool<T> {
		private final ListTag encoded;
		private final boolean valid;
		private final Map<Integer, T> decoded = new HashMap<>();
		private final Function<CompoundTag, T> decoder;

		ReadPool(CompoundTag shared, String key, int limit, Function<CompoundTag, T> decoder) {
			Tag raw = shared.get(key);
			encoded = raw instanceof ListTag list ? list : new ListTag();
			valid = (raw == null || raw instanceof ListTag)
				&& (encoded.isEmpty() || encoded.getElementType() == Tag.TAG_COMPOUND) && encoded.size() <= limit;
			this.decoder = decoder;
		}

		@Nullable T get(int index) {
			if (!valid || index < 0 || index >= encoded.size())
				return null;
			if (!decoded.containsKey(index))
				decoded.put(index, decoder.apply(encoded.getCompound(index)));
			return decoded.get(index);
		}

		@Nullable List<T> refs(CompoundTag tag, String key, int limit) {
			if (!tag.contains(key, Tag.TAG_INT_ARRAY))
				return null;
			int[] refs = tag.getIntArray(key);
			if (refs.length > limit)
				return null;
			List<T> values = new ArrayList<>(refs.length);
			HashSet<T> distinct = new HashSet<>();
			for (int ref : refs) {
				T value = get(ref);
				if (value == null || !distinct.add(value))
					return null;
				values.add(value);
			}
			return List.copyOf(values);
		}
	}
}
