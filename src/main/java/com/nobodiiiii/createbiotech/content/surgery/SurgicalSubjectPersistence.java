package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** SurgicalSubjectPersistence algorithms over the canonical SurgicalSubject state. */
final class SurgicalSubjectPersistence {
	private SurgicalSubjectPersistence() {}


	static CompoundTag save(SurgicalSubject operationOwner) {
		return SurgicalSubjectPersistence.save(operationOwner, null);
	}

	static CompoundTag save(SurgicalSubject operationOwner, @Nullable SurgicalStorage.Writer storage) {
		CompoundTag tag = new CompoundTag();
		tag.putInt(SurgicalSubject.ID_TAG, operationOwner.id);
		tag.putUUID(SurgicalSubject.PERSISTENT_ID_TAG, operationOwner.persistentId);
		tag.putUUID(SurgicalSubject.DONOR_ID_TAG, operationOwner.donorId);
		SurgicalStorage.writeDonor(tag, storage, operationOwner.profile, operationOwner.cubeCount, operationOwner.seams,
			operationOwner.anatomy);
		tag.putInt(SurgicalSubject.FACING_TAG, operationOwner.placementFacing.get3DDataValue());
		tag.putInt(SurgicalSubject.LAY_AXIS_TAG, operationOwner.layPose.axis().ordinal());
		tag.putInt(SurgicalSubject.LAY_YAW_TAG, operationOwner.layPose.yaw());
		tag.putDouble(SurgicalSubject.LAY_TRANSLATE_X_TAG, operationOwner.layPose.translation().x);
		tag.putDouble(SurgicalSubject.LAY_TRANSLATE_Y_TAG, operationOwner.layPose.translation().y);
		tag.putDouble(SurgicalSubject.LAY_TRANSLATE_Z_TAG, operationOwner.layPose.translation().z);
		if (operationOwner.originOffsetX != 0.0d || operationOwner.originOffsetZ != 0.0d) {
			tag.putDouble(SurgicalSubject.ORIGIN_OFFSET_X_TAG, operationOwner.originOffsetX);
			tag.putDouble(SurgicalSubject.ORIGIN_OFFSET_Z_TAG, operationOwner.originOffsetZ);
		}
		tag.put(SurgicalSubject.FOOTPRINTS_TAG, SurgicalSubjectPersistence.writeFootprints(operationOwner.occupiedFootprints));
		if (storage != null) {
			storage.writeRelations(tag, operationOwner.glueJoints, operationOwner.combinations, operationOwner.limbJoints);
		} else {
			if (!operationOwner.glueJoints.isEmpty()) {
				ListTag encodedJoints = new ListTag();
				for (SurgicalGlueJoint joint : operationOwner.glueJoints)
					encodedJoints.add(joint.save());
				tag.put(SurgicalSubject.GLUE_JOINTS_TAG, encodedJoints);
			}
			if (!operationOwner.combinations.isEmpty()) {
				ListTag encodedCombinations = new ListTag();
				for (SurgicalCombination combination : operationOwner.combinations)
					encodedCombinations.add(combination.save());
				tag.put(SurgicalSubject.COMBINATIONS_TAG, encodedCombinations);
			}
			if (!operationOwner.limbJoints.isEmpty()) {
				ListTag encodedLimbs = new ListTag();
				for (SurgicalLimbJoint joint : operationOwner.limbJoints)
					encodedLimbs.add(joint.save());
				tag.put(SurgicalSubject.LIMB_JOINTS_TAG, encodedLimbs);
			}
		}
		tag.putLongArray(SurgicalSubject.PRESENT_CUBES_TAG, operationOwner.presentCubes.toLongArray());
		if (!operationOwner.cutSeams.isEmpty())
			tag.putLongArray(SurgicalSubject.CUT_SEAMS_TAG, operationOwner.cutSeams.toLongArray());
		if (!operationOwner.cutOrder.isEmpty())
			tag.putIntArray(SurgicalSubject.CUT_ORDER_TAG, operationOwner.cutOrder.stream().mapToInt(Integer::intValue).toArray());
		SurgicalSubjectPersistence.writeOffsets(operationOwner, tag);
		SurgicalSubjectPersistence.writeRotations(operationOwner, tag);
		return tag;
	}

	static @Nullable
	SurgicalSubject load(CompoundTag tag) {
		return SurgicalSubjectPersistence.load(tag, null);
	}

	static @Nullable
	SurgicalSubject load(CompoundTag tag, @Nullable SurgicalStorage.Reader storage) {
		SurgicalStorage.Donor donor = SurgicalStorage.readDonor(tag, storage);
		if (!tag.contains(SurgicalSubject.ID_TAG, Tag.TAG_ANY_NUMERIC) || !tag.hasUUID(SurgicalSubject.PERSISTENT_ID_TAG)
			|| !tag.contains(SurgicalSubject.FACING_TAG, Tag.TAG_ANY_NUMERIC)
			|| donor == null
			|| !tag.contains(SurgicalSubject.PRESENT_CUBES_TAG, Tag.TAG_LONG_ARRAY)
			|| !tag.contains(SurgicalSubject.FOOTPRINTS_TAG, Tag.TAG_LIST))
			return null;
		if (SurgicalSubjectPersistence.hasWrongType(tag, SurgicalSubject.CUT_SEAMS_TAG, Tag.TAG_LONG_ARRAY)
			|| SurgicalSubjectPersistence.hasWrongType(tag, SurgicalSubject.CUT_ORDER_TAG, Tag.TAG_INT_ARRAY))
			return null;
		MimicProfile profile = donor.profile();
		int id = tag.getInt(SurgicalSubject.ID_TAG);
		UUID persistentId = tag.getUUID(SurgicalSubject.PERSISTENT_ID_TAG);
		UUID donorId = tag.hasUUID(SurgicalSubject.DONOR_ID_TAG) ? tag.getUUID(SurgicalSubject.DONOR_ID_TAG) : persistentId;
		Direction facing = Direction.from3DDataValue(tag.getInt(SurgicalSubject.FACING_TAG));
		SurgicalLayPose layPose = SurgicalSubjectPersistence.readLayPose(tag);
		if (id < 0 || profile == null || !facing.getAxis().isHorizontal() || layPose == null)
			return null;
		boolean hasOrigin = tag.contains(SurgicalSubject.ORIGIN_OFFSET_X_TAG) || tag.contains(SurgicalSubject.ORIGIN_OFFSET_Z_TAG);
		if (hasOrigin && (!tag.contains(SurgicalSubject.ORIGIN_OFFSET_X_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(SurgicalSubject.ORIGIN_OFFSET_Z_TAG, Tag.TAG_ANY_NUMERIC)))
			return null;
		double originX = tag.contains(SurgicalSubject.ORIGIN_OFFSET_X_TAG, Tag.TAG_ANY_NUMERIC)
			? tag.getDouble(SurgicalSubject.ORIGIN_OFFSET_X_TAG) : 0.0d;
		double originZ = tag.contains(SurgicalSubject.ORIGIN_OFFSET_Z_TAG, Tag.TAG_ANY_NUMERIC)
			? tag.getDouble(SurgicalSubject.ORIGIN_OFFSET_Z_TAG) : 0.0d;
		if (!SurgicalSubject.finiteOffset(originX) || !SurgicalSubject.finiteOffset(originZ))
			return null;

		int cubeCount = donor.cubeCount();
		List<SurgicalAssembly.Seam> seams = donor.seams();
		BitSet present = BitSet.valueOf(tag.getLongArray(SurgicalSubject.PRESENT_CUBES_TAG));
		BitSet cuts = tag.contains(SurgicalSubject.CUT_SEAMS_TAG, Tag.TAG_LONG_ARRAY)
			? BitSet.valueOf(tag.getLongArray(SurgicalSubject.CUT_SEAMS_TAG)) : new BitSet();
		BionicAnatomySnapshot anatomy = donor.anatomy();
		if (seams == null || !SurgicalAssembly.validTopology(cubeCount, seams) || present.isEmpty()
			|| present.length() > cubeCount || cuts.length() > seams.size())
			return null;
		List<Integer> cutOrder = SurgicalSubjectPersistence.readCutOrder(tag, cuts, seams.size());
		Map<Integer, Vec3> offsets = SurgicalSubjectPersistence.readOffsets(tag, cubeCount, present);
		Map<Integer, SurgicalCubeRotation> rotations = SurgicalSubjectPersistence.readRotations(tag, cubeCount, present);
		List<SurgicalTableLayout.Footprint> footprints = SurgicalSubjectPersistence.readFootprints(tag);
		List<SurgicalGlueJoint> glueJoints = storage != null && tag.contains(SurgicalStorage.GLUE_REFS_TAG)
			? storage.glue(tag) : SurgicalSubjectPersistence.readGlueJoints(tag);
		List<SurgicalCombination> combinations = storage != null && tag.contains(SurgicalStorage.COMBINATION_REFS_TAG)
			? storage.combinations(tag) : SurgicalSubjectPersistence.readCombinations(tag);
		List<SurgicalLimbJoint> limbJoints = storage != null && tag.contains(SurgicalStorage.LIMB_REFS_TAG)
			? storage.limbs(tag) : SurgicalSubjectPersistence.readLimbJoints(tag);
		if (cutOrder == null || offsets == null || rotations == null || footprints == null || footprints.isEmpty()
			|| glueJoints == null || combinations == null || limbJoints == null)
			return null;
		return new SurgicalSubject(id, persistentId, donorId,
			anatomy, profile, facing, layPose, cubeCount, present,
			seams, cuts, cutOrder,
			originX, originZ, offsets, rotations, footprints, glueJoints, combinations, limbJoints);
	}

	static @Nullable
	SurgicalLayPose readLayPose(CompoundTag tag) {
		if (!tag.contains(SurgicalSubject.LAY_AXIS_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(SurgicalSubject.LAY_YAW_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(SurgicalSubject.LAY_TRANSLATE_X_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(SurgicalSubject.LAY_TRANSLATE_Y_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(SurgicalSubject.LAY_TRANSLATE_Z_TAG, Tag.TAG_ANY_NUMERIC))
			return null;
		int axisId = tag.getInt(SurgicalSubject.LAY_AXIS_TAG);
		if (axisId < 0 || axisId >= SurgicalLayPose.RotationAxis.values().length)
			return null;
		try {
			return new SurgicalLayPose(SurgicalLayPose.RotationAxis.values()[axisId], tag.getInt(SurgicalSubject.LAY_YAW_TAG),
				new Vec3(tag.getDouble(SurgicalSubject.LAY_TRANSLATE_X_TAG), tag.getDouble(SurgicalSubject.LAY_TRANSLATE_Y_TAG),
					tag.getDouble(SurgicalSubject.LAY_TRANSLATE_Z_TAG)));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	static void writeOffsets(SurgicalSubject operationOwner, CompoundTag tag) {
		if (operationOwner.componentOffsets.isEmpty())
			return;
		List<Map.Entry<Integer, Vec3>> entries = operationOwner.componentOffsets.entrySet().stream()
			.sorted(Map.Entry.comparingByKey()).toList();
		int[] cubes = new int[entries.size()];
		long[] xOffsets = new long[entries.size()];
		long[] yOffsets = new long[entries.size()];
		long[] zOffsets = new long[entries.size()];
		for (int index = 0; index < entries.size(); index++) {
			Map.Entry<Integer, Vec3> entry = entries.get(index);
			cubes[index] = entry.getKey();
			xOffsets[index] = Double.doubleToRawLongBits(entry.getValue().x);
			yOffsets[index] = Double.doubleToRawLongBits(entry.getValue().y);
			zOffsets[index] = Double.doubleToRawLongBits(entry.getValue().z);
		}
		tag.putIntArray(SurgicalSubject.OFFSET_CUBES_TAG, cubes);
		tag.putLongArray(SurgicalSubject.OFFSET_X_TAG, xOffsets);
		tag.putLongArray(SurgicalSubject.OFFSET_Y_TAG, yOffsets);
		tag.putLongArray(SurgicalSubject.OFFSET_Z_TAG, zOffsets);
	}

	static @Nullable
	Map<Integer, Vec3> readOffsets(CompoundTag tag, int cubeCount, BitSet present) {
		boolean presentInTag = tag.contains(SurgicalSubject.OFFSET_CUBES_TAG) || tag.contains(SurgicalSubject.OFFSET_X_TAG)
			|| tag.contains(SurgicalSubject.OFFSET_Y_TAG) || tag.contains(SurgicalSubject.OFFSET_Z_TAG);
		if (!presentInTag)
			return Map.of();
		if (!tag.contains(SurgicalSubject.OFFSET_CUBES_TAG, Tag.TAG_INT_ARRAY)
			|| !tag.contains(SurgicalSubject.OFFSET_X_TAG, Tag.TAG_LONG_ARRAY)
			|| !tag.contains(SurgicalSubject.OFFSET_Y_TAG, Tag.TAG_LONG_ARRAY)
			|| !tag.contains(SurgicalSubject.OFFSET_Z_TAG, Tag.TAG_LONG_ARRAY))
			return null;
		int[] cubes = tag.getIntArray(SurgicalSubject.OFFSET_CUBES_TAG);
		long[] xOffsets = tag.getLongArray(SurgicalSubject.OFFSET_X_TAG);
		long[] yOffsets = tag.getLongArray(SurgicalSubject.OFFSET_Y_TAG);
		long[] zOffsets = tag.getLongArray(SurgicalSubject.OFFSET_Z_TAG);
		if (cubes.length != xOffsets.length || cubes.length != yOffsets.length
			|| cubes.length != zOffsets.length || cubes.length > cubeCount)
			return null;
		Map<Integer, Vec3> loaded = new HashMap<>();
		for (int index = 0; index < cubes.length; index++) {
			double x = Double.longBitsToDouble(xOffsets[index]);
			double y = Double.longBitsToDouble(yOffsets[index]);
			double z = Double.longBitsToDouble(zOffsets[index]);
			if (cubes[index] < 0 || cubes[index] >= cubeCount || !present.get(cubes[index])
				|| !SurgicalSubject.finiteOffset(x) || !SurgicalSubject.finiteOffset(y) || !SurgicalSubject.finiteOffset(z)
				|| loaded.put(cubes[index], new Vec3(x, y, z)) != null)
				return null;
		}
		return Map.copyOf(loaded);
	}

	static void writeRotations(SurgicalSubject operationOwner, CompoundTag tag) {
		if (operationOwner.componentRotations.isEmpty())
			return;
		ListTag encoded = new ListTag();
		for (Map.Entry<Integer, SurgicalCubeRotation> entry : operationOwner.componentRotations.entrySet().stream()
			.sorted(Map.Entry.comparingByKey()).toList()) {
			CompoundTag value = new CompoundTag();
			value.putInt(SurgicalSubject.ROTATION_CUBE_TAG, entry.getKey());
			value.put(SurgicalSubject.ROTATION_VALUE_TAG, entry.getValue().save());
			encoded.add(value);
		}
		tag.put(SurgicalSubject.ROTATIONS_TAG, encoded);
	}

	static @Nullable
	Map<Integer, SurgicalCubeRotation> readRotations(CompoundTag tag, int cubeCount,
		BitSet present) {
		if (!tag.contains(SurgicalSubject.ROTATIONS_TAG))
			return Map.of();
		if (!tag.contains(SurgicalSubject.ROTATIONS_TAG, Tag.TAG_LIST))
			return null;
		ListTag encoded = (ListTag) tag.get(SurgicalSubject.ROTATIONS_TAG);
		if (!encoded.isEmpty() && encoded.getElementType() != Tag.TAG_COMPOUND)
			return null;
		if (encoded.size() > cubeCount)
			return null;
		Map<Integer, SurgicalCubeRotation> loaded = new HashMap<>();
		for (int index = 0; index < encoded.size(); index++) {
			CompoundTag value = encoded.getCompound(index);
			if (!value.contains(SurgicalSubject.ROTATION_CUBE_TAG, Tag.TAG_ANY_NUMERIC))
				return null;
			int cube = value.getInt(SurgicalSubject.ROTATION_CUBE_TAG);
			SurgicalCubeRotation rotation = value.contains(SurgicalSubject.ROTATION_VALUE_TAG, Tag.TAG_COMPOUND)
				? SurgicalCubeRotation.load(value.getCompound(SurgicalSubject.ROTATION_VALUE_TAG)) : null;
			if (cube < 0 || cube >= cubeCount || !present.get(cube) || rotation == null
				|| loaded.putIfAbsent(cube, rotation) != null)
				return null;
		}
		return SurgicalSubject.sanitizeRotations(loaded);
	}

	static @Nullable
	List<SurgicalGlueJoint> readGlueJoints(CompoundTag tag) {
		if (!tag.contains(SurgicalSubject.GLUE_JOINTS_TAG))
			return List.of();
		if (!tag.contains(SurgicalSubject.GLUE_JOINTS_TAG, Tag.TAG_LIST))
			return null;
		ListTag encoded = (ListTag) tag.get(SurgicalSubject.GLUE_JOINTS_TAG);
		if (!encoded.isEmpty() && encoded.getElementType() != Tag.TAG_COMPOUND)
			return null;
		if (encoded.size() > SurgicalAssembly.MAX_SEAMS)
			return null;
		List<SurgicalGlueJoint> joints = new ArrayList<>();
		for (int index = 0; index < encoded.size(); index++) {
			SurgicalGlueJoint joint = SurgicalGlueJoint.load(encoded.getCompound(index));
			if (joint == null || joints.contains(joint))
				return null;
			joints.add(joint);
		}
		return List.copyOf(joints);
	}

	static @Nullable
	List<SurgicalLimbJoint> readLimbJoints(CompoundTag tag) {
		if (!tag.contains(SurgicalSubject.LIMB_JOINTS_TAG))
			return List.of();
		if (!tag.contains(SurgicalSubject.LIMB_JOINTS_TAG, Tag.TAG_LIST))
			return null;
		ListTag encoded = (ListTag) tag.get(SurgicalSubject.LIMB_JOINTS_TAG);
		if (!encoded.isEmpty() && encoded.getElementType() != Tag.TAG_COMPOUND)
			return null;
		if (encoded.size() > SurgicalAssembly.MAX_CUBES)
			return null;
		List<SurgicalLimbJoint> joints = new ArrayList<>();
		for (int index = 0; index < encoded.size(); index++) {
			SurgicalLimbJoint joint = SurgicalLimbJoint.load(encoded.getCompound(index));
			if (joint == null || joints.contains(joint))
				return null;
			joints.add(joint);
		}
		return List.copyOf(joints);
	}

	static @Nullable
	List<SurgicalCombination> readCombinations(CompoundTag tag) {
		if (!tag.contains(SurgicalSubject.COMBINATIONS_TAG))
			return List.of();
		if (!tag.contains(SurgicalSubject.COMBINATIONS_TAG, Tag.TAG_LIST))
			return null;
		ListTag encoded = (ListTag) tag.get(SurgicalSubject.COMBINATIONS_TAG);
		if (!encoded.isEmpty() && encoded.getElementType() != Tag.TAG_COMPOUND)
			return null;
		if (encoded.size() > SurgicalAssembly.MAX_CUBES)
			return null;
		List<SurgicalCombination> combinations = new ArrayList<>();
		for (int index = 0; index < encoded.size(); index++) {
			SurgicalCombination combination = SurgicalCombination.load(encoded.getCompound(index));
			if (combination == null || combinations.contains(combination))
				return null;
			combinations.add(combination);
		}
		return List.copyOf(combinations);
	}

	static @Nullable
	List<Integer> readCutOrder(CompoundTag tag, BitSet cuts, int seamCount) {
		if (!tag.contains(SurgicalSubject.CUT_ORDER_TAG))
			return cuts.isEmpty() ? List.of() : null;
		if (!tag.contains(SurgicalSubject.CUT_ORDER_TAG, Tag.TAG_INT_ARRAY))
			return null;
		List<Integer> loaded = new ArrayList<>();
		for (int seamId : tag.getIntArray(SurgicalSubject.CUT_ORDER_TAG))
			loaded.add(seamId);
		List<Integer> normalized = SurgicalAssembly.normalizeCutOrder(loaded, cuts, seamCount);
		return normalized.equals(loaded) ? normalized : null;
	}

	static ListTag writeFootprints(List<SurgicalTableLayout.Footprint> footprints) {
		ListTag encoded = new ListTag();
		for (SurgicalTableLayout.Footprint footprint : footprints) {
			CompoundTag entry = new CompoundTag();
			entry.putInt(SurgicalSubject.FOOTPRINT_ROOT_TAG, footprint.componentRoot());
			entry.putDouble(SurgicalSubject.FOOTPRINT_MIN_X_TAG, footprint.minX());
			entry.putDouble(SurgicalSubject.FOOTPRINT_MIN_Z_TAG, footprint.minZ());
			entry.putDouble(SurgicalSubject.FOOTPRINT_MAX_X_TAG, footprint.maxX());
			entry.putDouble(SurgicalSubject.FOOTPRINT_MAX_Z_TAG, footprint.maxZ());
			entry.putInt(SurgicalSubject.FOOTPRINT_GRID_X_TAG, footprint.gridX());
			entry.putInt(SurgicalSubject.FOOTPRINT_GRID_Z_TAG, footprint.gridZ());
			encoded.add(entry);
		}
		return encoded;
	}

	static @Nullable
	List<SurgicalTableLayout.Footprint> readFootprints(CompoundTag tag) {
		if (!tag.contains(SurgicalSubject.FOOTPRINTS_TAG, Tag.TAG_LIST))
			return null;
		ListTag encoded = (ListTag) tag.get(SurgicalSubject.FOOTPRINTS_TAG);
		if (!encoded.isEmpty() && encoded.getElementType() != Tag.TAG_COMPOUND)
			return null;
		if (encoded.isEmpty() || encoded.size() > SurgicalAssembly.MAX_CUBES)
			return null;
		List<SurgicalTableLayout.Footprint> loaded = new ArrayList<>(encoded.size());
		for (int index = 0; index < encoded.size(); index++) {
			CompoundTag entry = encoded.getCompound(index);
			if (!entry.contains(SurgicalSubject.FOOTPRINT_ROOT_TAG, Tag.TAG_ANY_NUMERIC)
				|| !entry.contains(SurgicalSubject.FOOTPRINT_MIN_X_TAG, Tag.TAG_ANY_NUMERIC)
				|| !entry.contains(SurgicalSubject.FOOTPRINT_MIN_Z_TAG, Tag.TAG_ANY_NUMERIC)
				|| !entry.contains(SurgicalSubject.FOOTPRINT_MAX_X_TAG, Tag.TAG_ANY_NUMERIC)
				|| !entry.contains(SurgicalSubject.FOOTPRINT_MAX_Z_TAG, Tag.TAG_ANY_NUMERIC)
				|| !entry.contains(SurgicalSubject.FOOTPRINT_GRID_X_TAG, Tag.TAG_ANY_NUMERIC)
				|| !entry.contains(SurgicalSubject.FOOTPRINT_GRID_Z_TAG, Tag.TAG_ANY_NUMERIC))
				return null;
			SurgicalTableLayout.Footprint footprint = new SurgicalTableLayout.Footprint(
				entry.getInt(SurgicalSubject.FOOTPRINT_ROOT_TAG), entry.getDouble(SurgicalSubject.FOOTPRINT_MIN_X_TAG),
				entry.getDouble(SurgicalSubject.FOOTPRINT_MIN_Z_TAG), entry.getDouble(SurgicalSubject.FOOTPRINT_MAX_X_TAG),
				entry.getDouble(SurgicalSubject.FOOTPRINT_MAX_Z_TAG), entry.getInt(SurgicalSubject.FOOTPRINT_GRID_X_TAG),
				entry.getInt(SurgicalSubject.FOOTPRINT_GRID_Z_TAG));
			if (!SurgicalTableLayout.validStoredFootprint(footprint))
				return null;
			loaded.add(footprint);
		}
		return List.copyOf(loaded);
	}

	static boolean hasWrongType(CompoundTag tag, String key, int expectedType) {
		return tag.contains(key) && !tag.contains(key, expectedType);
	}
}
