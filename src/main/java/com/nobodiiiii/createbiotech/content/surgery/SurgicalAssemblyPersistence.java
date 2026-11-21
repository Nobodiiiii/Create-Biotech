package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** SurgicalAssemblyPersistence algorithms over the canonical SurgicalAssembly state. */
final class SurgicalAssemblyPersistence {
	private SurgicalAssemblyPersistence() {}


	static @Nullable
	SurgicalAssembly load(CompoundTag tag) {
		int version = tag.getInt(SurgicalAssembly.VERSION_TAG);
		if (SurgicalReleaseMigration.supportsAssembly(version)) {
			CompoundTag upgraded = SurgicalReleaseMigration.assembly(tag, SurgicalAssembly.CURRENT_VERSION);
			return upgraded == null ? null : SurgicalAssemblyPersistence.load(upgraded);
		}
		// Model ancestry replaces the independent head metadata in format 4.
		if (version != SurgicalAssembly.CURRENT_VERSION || !tag.contains(SurgicalAssembly.SOURCES_TAG, Tag.TAG_LIST)
			|| !tag.contains(SurgicalAssembly.LAYOUT_FACING_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(SurgicalAssembly.LAYOUT_LAY_POSE_TAG, Tag.TAG_COMPOUND))
			return null;
		if (SurgicalAssemblyPersistence.hasWrongType(tag, SurgicalAssembly.JOINTS_TAG, Tag.TAG_LIST)
			|| SurgicalAssemblyPersistence.hasWrongType(tag, SurgicalAssembly.COMBINATIONS_TAG, Tag.TAG_LIST)
			|| SurgicalAssemblyPersistence.hasWrongType(tag, SurgicalAssembly.LIMBS_TAG, Tag.TAG_LIST)
			|| SurgicalAssemblyPersistence.hasWrongType(tag, SurgicalAssembly.PRESERVE_LAYOUT_TAG, Tag.TAG_BYTE)
			|| SurgicalAssemblyPersistence.hasWrongType(tag, SurgicalAssembly.BODY_VOLUME_TAG, Tag.TAG_ANY_NUMERIC)
			|| SurgicalAssemblyPersistence.hasWrongType(tag, SurgicalAssembly.ATTACK_GEOMETRY_TAG, Tag.TAG_COMPOUND)
			|| SurgicalAssemblyPersistence.hasWrongType(tag, SurgicalAssembly.BLOCK_ATTACHMENT_CARRIER_TAG, Tag.TAG_INT_ARRAY)
			|| SurgicalAssemblyPersistence.hasWrongType(tag, SurgicalAssembly.HITBOX_GEOMETRY_TAG, Tag.TAG_COMPOUND))
			return null;

		ListTag encodedSources = tag.getList(SurgicalAssembly.SOURCES_TAG, Tag.TAG_COMPOUND);
		if (encodedSources.isEmpty() || encodedSources.size() > SurgicalAssembly.MAX_SOURCES)
			return null;
		List<SurgicalAssembly.Source> sources = new ArrayList<>(encodedSources.size());
		SurgicalStorage.Reader storage = new SurgicalStorage.Reader(tag, SurgicalAssembly.MAX_SOURCES);
		for (int index = 0; index < encodedSources.size(); index++) {
			SurgicalAssembly.Source source = SurgicalAssembly.Source.load(encodedSources.getCompound(index), storage);
			if (source == null)
				return null;
			sources.add(source);
		}

		List<SurgicalAssembly.Joint> joints = new ArrayList<>();
		if (tag.contains(SurgicalAssembly.JOINTS_TAG, Tag.TAG_LIST)) {
			ListTag encodedJoints = (ListTag) tag.get(SurgicalAssembly.JOINTS_TAG);
			if (!encodedJoints.isEmpty() && encodedJoints.getElementType() != Tag.TAG_COMPOUND)
				return null;
			if (encodedJoints.size() > SurgicalAssembly.MAX_SEAMS)
				return null;
			for (int index = 0; index < encodedJoints.size(); index++) {
				CompoundTag encoded = encodedJoints.getCompound(index);
				if (!encoded.contains(SurgicalAssembly.FIRST_SOURCE_TAG, Tag.TAG_ANY_NUMERIC)
					|| !encoded.contains(SurgicalAssembly.FIRST_CUBE_TAG, Tag.TAG_ANY_NUMERIC)
					|| !encoded.contains(SurgicalAssembly.SECOND_SOURCE_TAG, Tag.TAG_ANY_NUMERIC)
					|| !encoded.contains(SurgicalAssembly.SECOND_CUBE_TAG, Tag.TAG_ANY_NUMERIC))
					return null;
				int firstSource = encoded.getInt(SurgicalAssembly.FIRST_SOURCE_TAG);
				int firstCube = encoded.getInt(SurgicalAssembly.FIRST_CUBE_TAG);
				int secondSource = encoded.getInt(SurgicalAssembly.SECOND_SOURCE_TAG);
				int secondCube = encoded.getInt(SurgicalAssembly.SECOND_CUBE_TAG);
				SurgicalAssembly.JointReplay replay = null;
				if (SurgicalAssemblyPersistence.hasWrongType(encoded, SurgicalAssembly.JOINT_REPLAY_TAG, Tag.TAG_COMPOUND))
					return null;
				if (encoded.contains(SurgicalAssembly.JOINT_REPLAY_TAG, Tag.TAG_COMPOUND)) {
					CompoundTag encodedReplay = encoded.getCompound(SurgicalAssembly.JOINT_REPLAY_TAG);
					SurgicalGlueTransform transform = encodedReplay.contains(SurgicalAssembly.GLUE_TRANSFORM_TAG, Tag.TAG_COMPOUND)
						? SurgicalGlueTransform.load(encodedReplay.getCompound(SurgicalAssembly.GLUE_TRANSFORM_TAG)) : null;
					SurgicalGlueContact anchorContact = encodedReplay.contains(SurgicalAssembly.ANCHOR_CONTACT_TAG, Tag.TAG_COMPOUND)
						? SurgicalGlueContact.load(encodedReplay.getCompound(SurgicalAssembly.ANCHOR_CONTACT_TAG)) : null;
					if (!encodedReplay.contains(SurgicalAssembly.MOVING_SOURCE_TAG, Tag.TAG_ANY_NUMERIC)
						|| !encodedReplay.contains(SurgicalAssembly.MOVING_CUBE_TAG, Tag.TAG_ANY_NUMERIC)
						|| transform == null || anchorContact == null)
						return null;
					int movingSource = encodedReplay.getInt(SurgicalAssembly.MOVING_SOURCE_TAG);
					int movingCube = encodedReplay.getInt(SurgicalAssembly.MOVING_CUBE_TAG);
					if (movingSource < 0 || movingCube < 0)
						return null;
					replay = new SurgicalAssembly.JointReplay(movingSource, movingCube, transform, anchorContact);
				}
				if (replay != null && !replay.matches(firstSource, firstCube)
					&& !replay.matches(secondSource, secondCube))
					return null;
				joints.add(new SurgicalAssembly.Joint(firstSource, firstCube, secondSource, secondCube, replay));
			}
		}
		List<SurgicalAssembly.Combination> combinations = new ArrayList<>();
		if (tag.contains(SurgicalAssembly.COMBINATIONS_TAG, Tag.TAG_LIST)) {
			ListTag encodedCombinations = (ListTag) tag.get(SurgicalAssembly.COMBINATIONS_TAG);
			if (!encodedCombinations.isEmpty() && encodedCombinations.getElementType() != Tag.TAG_COMPOUND)
				return null;
			if (encodedCombinations.size() > SurgicalAssembly.MAX_CUBES)
				return null;
			for (int index = 0; index < encodedCombinations.size(); index++) {
				CompoundTag encoded = encodedCombinations.getCompound(index);
				if (!encoded.hasUUID(SurgicalAssembly.COMBINATION_ID_TAG)
					|| !encoded.contains(SurgicalAssembly.COMBINATION_MEMBERS_TAG, Tag.TAG_LIST))
					return null;
				ListTag encodedMembers = encoded.getList(SurgicalAssembly.COMBINATION_MEMBERS_TAG, Tag.TAG_COMPOUND);
				if (encodedMembers.size() < 2 || encodedMembers.size() > SurgicalAssembly.MAX_CUBES)
					return null;
				List<SurgicalAssembly.CombinationMember> members = new ArrayList<>(encodedMembers.size());
				for (int memberIndex = 0; memberIndex < encodedMembers.size(); memberIndex++) {
					CompoundTag member = encodedMembers.getCompound(memberIndex);
					if (!member.contains(SurgicalAssembly.MEMBER_SOURCE_TAG, Tag.TAG_ANY_NUMERIC)
						|| !member.contains(SurgicalAssembly.MEMBER_CUBE_TAG, Tag.TAG_ANY_NUMERIC))
						return null;
					members.add(new SurgicalAssembly.CombinationMember(member.getInt(SurgicalAssembly.MEMBER_SOURCE_TAG),
						member.getInt(SurgicalAssembly.MEMBER_CUBE_TAG)));
				}
				combinations.add(new SurgicalAssembly.Combination(encoded.getUUID(SurgicalAssembly.COMBINATION_ID_TAG), members));
			}
		}
		List<SurgicalAssembly.Limb> limbs = new ArrayList<>();
		if (tag.contains(SurgicalAssembly.LIMBS_TAG, Tag.TAG_LIST)) {
			ListTag encodedLimbs = (ListTag) tag.get(SurgicalAssembly.LIMBS_TAG);
			if (!encodedLimbs.isEmpty() && encodedLimbs.getElementType() != Tag.TAG_COMPOUND)
				return null;
			if (encodedLimbs.size() > SurgicalAssembly.MAX_CUBES)
				return null;
			for (int index = 0; index < encodedLimbs.size(); index++) {
				CompoundTag encoded = encodedLimbs.getCompound(index);
				SurgicalLimbType type = encoded.contains(SurgicalAssembly.LIMB_TYPE_TAG, Tag.TAG_STRING)
					? SurgicalLimbType.byId(encoded.getString(SurgicalAssembly.LIMB_TYPE_TAG)) : null;
				if (type == null || !encoded.contains(SurgicalAssembly.LIMB_CHILD_SOURCE_TAG, Tag.TAG_ANY_NUMERIC)
					|| !encoded.contains(SurgicalAssembly.LIMB_CHILD_CUBE_TAG, Tag.TAG_ANY_NUMERIC)
					|| !encoded.contains(SurgicalAssembly.LIMB_PARENT_SOURCE_TAG, Tag.TAG_ANY_NUMERIC)
					|| !encoded.contains(SurgicalAssembly.LIMB_PARENT_CUBE_TAG, Tag.TAG_ANY_NUMERIC))
					return null;
				limbs.add(new SurgicalAssembly.Limb(type, encoded.getInt(SurgicalAssembly.LIMB_CHILD_SOURCE_TAG),
					encoded.getInt(SurgicalAssembly.LIMB_CHILD_CUBE_TAG), encoded.getInt(SurgicalAssembly.LIMB_PARENT_SOURCE_TAG),
					encoded.getInt(SurgicalAssembly.LIMB_PARENT_CUBE_TAG)));
			}
		}
		Direction layoutFacing = Direction.from3DDataValue(tag.getInt(SurgicalAssembly.LAYOUT_FACING_TAG));
		SurgicalLayPose layoutLayPose = SurgicalAssemblyPersistence.readLayPose(tag.getCompound(SurgicalAssembly.LAYOUT_LAY_POSE_TAG));
		if (!layoutFacing.getAxis().isHorizontal() || layoutLayPose == null)
			return null;
		SurgicalAssembly assembly = SurgicalAssembly.createComposite(sources, joints, combinations, limbs, layoutFacing,
			layoutLayPose);
		if (assembly == null)
			return null;
		assembly = tag.getBoolean(SurgicalAssembly.PRESERVE_LAYOUT_TAG) ? assembly
			: new SurgicalAssembly(assembly.sources, assembly.joints, assembly.combinations,
				assembly.limbs, false, assembly.layoutFacing,
				assembly.layoutLayPose, null, Double.NaN, null, null);
		boolean hasAnyBounds = tag.contains(SurgicalAssembly.BODY_WIDTH_TAG) || tag.contains(SurgicalAssembly.BODY_HEIGHT_TAG)
			|| tag.contains(SurgicalAssembly.BODY_DEPTH_TAG) || tag.contains(SurgicalAssembly.BODY_CENTER_X_TAG)
			|| tag.contains(SurgicalAssembly.BODY_MIN_Y_TAG) || tag.contains(SurgicalAssembly.BODY_CENTER_Z_TAG)
			|| tag.contains(SurgicalAssembly.BODY_EYE_HEIGHT_TAG) || tag.contains(SurgicalAssembly.BODY_LEG_LENGTH_TAG)
			|| tag.contains(SurgicalAssembly.BODY_GROUNDED_LEGS_TAG) || tag.contains(SurgicalAssembly.BODY_GROUNDED_KNEES_TAG)
			|| tag.contains(SurgicalAssembly.BODY_LEG_VOLUME_RATIO_TAG);
		boolean hasBounds = tag.contains(SurgicalAssembly.BODY_WIDTH_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(SurgicalAssembly.BODY_HEIGHT_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(SurgicalAssembly.BODY_DEPTH_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(SurgicalAssembly.BODY_CENTER_X_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(SurgicalAssembly.BODY_MIN_Y_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(SurgicalAssembly.BODY_CENTER_Z_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(SurgicalAssembly.BODY_EYE_HEIGHT_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(SurgicalAssembly.BODY_LEG_LENGTH_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(SurgicalAssembly.BODY_GROUNDED_LEGS_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(SurgicalAssembly.BODY_GROUNDED_KNEES_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(SurgicalAssembly.BODY_LEG_VOLUME_RATIO_TAG, Tag.TAG_ANY_NUMERIC);
		if (hasAnyBounds && !hasBounds)
			return null;
		if (hasBounds) {
			SurgicalAssembly.BodyBounds bounds = SurgicalAssembly.BodyBounds.create(tag.getDouble(SurgicalAssembly.BODY_WIDTH_TAG),
				tag.getDouble(SurgicalAssembly.BODY_HEIGHT_TAG), tag.getDouble(SurgicalAssembly.BODY_DEPTH_TAG),
				tag.getDouble(SurgicalAssembly.BODY_CENTER_X_TAG), tag.getDouble(SurgicalAssembly.BODY_MIN_Y_TAG),
				tag.getDouble(SurgicalAssembly.BODY_CENTER_Z_TAG), tag.getDouble(SurgicalAssembly.BODY_EYE_HEIGHT_TAG),
				tag.getDouble(SurgicalAssembly.BODY_LEG_LENGTH_TAG), tag.getInt(SurgicalAssembly.BODY_GROUNDED_LEGS_TAG),
				tag.getInt(SurgicalAssembly.BODY_GROUNDED_KNEES_TAG), tag.getDouble(SurgicalAssembly.BODY_LEG_VOLUME_RATIO_TAG));
			if (bounds == null)
				return null;
			assembly = assembly.withBodyBounds(bounds);
		}
		if (tag.contains(SurgicalAssembly.BODY_VOLUME_TAG, Tag.TAG_ANY_NUMERIC)) {
			double volume = tag.getDouble(SurgicalAssembly.BODY_VOLUME_TAG);
			if (!SurgicalHealthCalibration.validVolume(volume))
				return null;
			assembly = assembly.withBodyVolume(volume);
		}
		if (tag.contains(SurgicalAssembly.ATTACK_GEOMETRY_TAG, Tag.TAG_COMPOUND)) {
			SurgicalAssembly.AttackGeometry geometry = SurgicalAssembly.AttackGeometry.load(tag.getCompound(SurgicalAssembly.ATTACK_GEOMETRY_TAG));
			if (geometry == null)
				return null;
			assembly = assembly.withAttackGeometry(geometry);
		}
		if (tag.contains(SurgicalAssembly.HITBOX_GEOMETRY_TAG, Tag.TAG_COMPOUND)) {
			SurgicalAssembly.HitboxGeometry geometry = SurgicalAssembly.HitboxGeometry.load(tag.getCompound(SurgicalAssembly.HITBOX_GEOMETRY_TAG));
			if (geometry == null)
				return null;
			assembly = assembly.withHitboxGeometry(geometry);
		}
		if (assembly.hasBodyVolume() && assembly.hitboxGeometry != null
			&& !SurgicalHealthCalibration.validMeasuredVolume(assembly.bodyVolume, assembly.hitboxGeometry))
			return null;
		if (tag.contains(SurgicalAssembly.BLOCK_ATTACHMENT_CARRIER_TAG, Tag.TAG_INT_ARRAY)) {
			int[] carrier = tag.getIntArray(SurgicalAssembly.BLOCK_ATTACHMENT_CARRIER_TAG);
			if (carrier.length != 2 || carrier[0] < 0 || carrier[0] >= assembly.sources.size()
				|| !assembly.sources.get(carrier[0]).containsCube(carrier[1])) return null;
			assembly = assembly.withBlockAttachmentCarrier(new SurgicalAssembly.CombinationMember(carrier[0], carrier[1]));
		}
		return assembly;
	}

	/** Published formats only; callers can persist the decoded body's current save once. */
	static boolean needsReleaseMigration(CompoundTag tag) {
		return SurgicalReleaseMigration.supportsAssembly(tag.getInt(SurgicalAssembly.VERSION_TAG));
	}

	static CompoundTag save(SurgicalAssembly operationOwner) {
		return new PreparedSave(operationOwner, operationOwner.sources.size()).finish(operationOwner);
	}

	/** Only the new donor templates need the final growth timestamp. All poses and geometry can be encoded earlier. */
	static final class PreparedSave {
		private final CompoundTag tag;
		private final ListTag sources = new ListTag();
		private final SurgicalStorage.Writer storage = new SurgicalStorage.Writer();
		private final int stampedFrom;

		PreparedSave(SurgicalAssembly assembly, int stampedFrom) {
			this.stampedFrom = stampedFrom;
			tag = saveStructure(assembly);
			for (var source : assembly.sources) {
				var placed = source.savePlacement();
				if (sources.size() < stampedFrom) source.writeDonor(placed, storage);
				sources.add(placed);
			}
		}

		CompoundTag finish(SurgicalAssembly assembly) {
			for (int i = stampedFrom; i < sources.size(); i++)
				assembly.sources.get(i).writeDonor(sources.getCompound(i), storage);
			tag.put(SurgicalAssembly.SOURCES_TAG, sources);
			storage.save(tag);
			return tag;
		}
	}

	private static CompoundTag saveStructure(SurgicalAssembly operationOwner) {
		CompoundTag tag = new CompoundTag();
		tag.putInt(SurgicalAssembly.VERSION_TAG, SurgicalAssembly.CURRENT_VERSION);
		if (!operationOwner.joints.isEmpty()) {
			ListTag encodedJoints = new ListTag();
			for (SurgicalAssembly.Joint joint : operationOwner.joints) {
				CompoundTag encoded = new CompoundTag();
				encoded.putInt(SurgicalAssembly.FIRST_SOURCE_TAG, joint.firstSource());
				encoded.putInt(SurgicalAssembly.FIRST_CUBE_TAG, joint.firstCube());
				encoded.putInt(SurgicalAssembly.SECOND_SOURCE_TAG, joint.secondSource());
				encoded.putInt(SurgicalAssembly.SECOND_CUBE_TAG, joint.secondCube());
				if (joint.replay() != null) {
					CompoundTag encodedReplay = new CompoundTag();
					encodedReplay.putInt(SurgicalAssembly.MOVING_SOURCE_TAG, joint.replay().movingSource());
					encodedReplay.putInt(SurgicalAssembly.MOVING_CUBE_TAG, joint.replay().movingCube());
					encodedReplay.put(SurgicalAssembly.GLUE_TRANSFORM_TAG, joint.replay().transform().save());
					encodedReplay.put(SurgicalAssembly.ANCHOR_CONTACT_TAG, joint.replay().anchorContact().save());
					encoded.put(SurgicalAssembly.JOINT_REPLAY_TAG, encodedReplay);
				}
				encodedJoints.add(encoded);
			}
			tag.put(SurgicalAssembly.JOINTS_TAG, encodedJoints);
		}
		if (!operationOwner.combinations.isEmpty()) {
			ListTag encodedCombinations = new ListTag();
			for (SurgicalAssembly.Combination combination : operationOwner.combinations) {
				CompoundTag encoded = new CompoundTag();
				encoded.putUUID(SurgicalAssembly.COMBINATION_ID_TAG, combination.id());
				ListTag encodedMembers = new ListTag();
				for (SurgicalAssembly.CombinationMember member : combination.members()) {
					CompoundTag encodedMember = new CompoundTag();
					encodedMember.putInt(SurgicalAssembly.MEMBER_SOURCE_TAG, member.source());
					encodedMember.putInt(SurgicalAssembly.MEMBER_CUBE_TAG, member.cube());
					encodedMembers.add(encodedMember);
				}
				encoded.put(SurgicalAssembly.COMBINATION_MEMBERS_TAG, encodedMembers);
				encodedCombinations.add(encoded);
			}
			tag.put(SurgicalAssembly.COMBINATIONS_TAG, encodedCombinations);
		}
		if (!operationOwner.limbs.isEmpty()) {
			ListTag encodedLimbs = new ListTag();
			for (SurgicalAssembly.Limb limb : operationOwner.limbs) {
				CompoundTag encoded = new CompoundTag();
				encoded.putString(SurgicalAssembly.LIMB_TYPE_TAG, limb.type().id());
				encoded.putInt(SurgicalAssembly.LIMB_CHILD_SOURCE_TAG, limb.childSource());
				encoded.putInt(SurgicalAssembly.LIMB_CHILD_CUBE_TAG, limb.childCube());
				encoded.putInt(SurgicalAssembly.LIMB_PARENT_SOURCE_TAG, limb.parentSource());
				encoded.putInt(SurgicalAssembly.LIMB_PARENT_CUBE_TAG, limb.parentCube());
				encodedLimbs.add(encoded);
			}
			tag.put(SurgicalAssembly.LIMBS_TAG, encodedLimbs);
		}
		if (operationOwner.preserveLayout)
			tag.putBoolean(SurgicalAssembly.PRESERVE_LAYOUT_TAG, true);
		tag.putInt(SurgicalAssembly.LAYOUT_FACING_TAG, operationOwner.layoutFacing.get3DDataValue());
		tag.put(SurgicalAssembly.LAYOUT_LAY_POSE_TAG, SurgicalAssemblyPersistence.writeLayPose(operationOwner.layoutLayPose));
		if (operationOwner.bodyBounds != null) {
			tag.putFloat(SurgicalAssembly.BODY_WIDTH_TAG, operationOwner.bodyBounds.width());
			tag.putFloat(SurgicalAssembly.BODY_HEIGHT_TAG, operationOwner.bodyBounds.height());
			tag.putFloat(SurgicalAssembly.BODY_DEPTH_TAG, operationOwner.bodyBounds.depth());
			tag.putFloat(SurgicalAssembly.BODY_CENTER_X_TAG, operationOwner.bodyBounds.centerX());
			tag.putFloat(SurgicalAssembly.BODY_MIN_Y_TAG, operationOwner.bodyBounds.minY());
			tag.putFloat(SurgicalAssembly.BODY_CENTER_Z_TAG, operationOwner.bodyBounds.centerZ());
			tag.putFloat(SurgicalAssembly.BODY_EYE_HEIGHT_TAG, operationOwner.bodyBounds.eyeHeight());
			tag.putFloat(SurgicalAssembly.BODY_LEG_LENGTH_TAG, operationOwner.bodyBounds.legLength());
			tag.putInt(SurgicalAssembly.BODY_GROUNDED_LEGS_TAG, operationOwner.bodyBounds.groundedLegCount());
			tag.putInt(SurgicalAssembly.BODY_GROUNDED_KNEES_TAG, operationOwner.bodyBounds.groundedKneeCount());
			tag.putFloat(SurgicalAssembly.BODY_LEG_VOLUME_RATIO_TAG, operationOwner.bodyBounds.legVolumeRatio());
		}
		if (operationOwner.hasBodyVolume())
			tag.putDouble(SurgicalAssembly.BODY_VOLUME_TAG, operationOwner.bodyVolume);
		if (operationOwner.attackGeometry != null)
			tag.put(SurgicalAssembly.ATTACK_GEOMETRY_TAG, operationOwner.attackGeometry.save());
		if (operationOwner.blockAttachmentCarrier != null)
			tag.putIntArray(SurgicalAssembly.BLOCK_ATTACHMENT_CARRIER_TAG,
				new int[] { operationOwner.blockAttachmentCarrier.source(), operationOwner.blockAttachmentCarrier.cube() });
		if (operationOwner.hitboxGeometry != null)
			tag.put(SurgicalAssembly.HITBOX_GEOMETRY_TAG, operationOwner.hitboxGeometry.save());
		return tag;
	}

	static int[] encodeSeams(List<SurgicalAssembly.Seam> seams) {
		int[] encoded = new int[seams.size() * 2];
		for (int i = 0; i < seams.size(); i++) {
			encoded[i * 2] = seams.get(i).first();
			encoded[i * 2 + 1] = seams.get(i).second();
		}
		return encoded;
	}

	static @Nullable
	List<SurgicalAssembly.Seam> decodeSeams(int[] encoded) {
		if (encoded == null || (encoded.length & 1) != 0 || encoded.length > SurgicalAssembly.MAX_SEAMS * 2)
			return null;
		List<SurgicalAssembly.Seam> seams = new ArrayList<>(encoded.length / 2);
		for (int i = 0; i < encoded.length; i += 2)
			seams.add(SurgicalAssembly.Seam.of(encoded[i], encoded[i + 1]));
		return List.copyOf(seams);
	}

	static BitSet normalize(BitSet input, int size) {
		BitSet normalized = input == null ? new BitSet() : (BitSet) input.clone();
		if (normalized.length() > size)
			normalized.clear(size, normalized.length());
		return normalized;
	}

	static List<Integer> normalizeCutOrder(List<Integer> input, BitSet cutSeams, int seamCount) {
		BitSet included = new BitSet(seamCount);
		List<Integer> normalized = new ArrayList<>();
		if (input != null) {
			for (Integer seamId : input) {
				if (seamId == null || seamId < 0 || seamId >= seamCount
					|| !cutSeams.get(seamId) || included.get(seamId))
					continue;
				included.set(seamId);
				normalized.add(seamId);
			}
		}
		for (int seamId = cutSeams.nextSetBit(0); seamId >= 0 && seamId < seamCount;
			seamId = cutSeams.nextSetBit(seamId + 1)) {
			if (!included.get(seamId))
				normalized.add(seamId);
		}
		return List.copyOf(normalized);
	}

	static @Nullable
	List<Integer> decodeCutOrder(int[] encoded) {
		if (encoded == null || encoded.length > SurgicalAssembly.MAX_SEAMS)
			return null;
		List<Integer> decoded = new ArrayList<>(encoded.length);
		for (int seamId : encoded)
			decoded.add(seamId);
		return List.copyOf(decoded);
	}

	static boolean finiteVector(Vec3 value) {
		return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)
			&& Math.abs(value.x) <= SurgicalTablePlane.MAX_TILES + 2.0d
			&& Math.abs(value.y) <= SurgicalTablePlane.MAX_TILES + 2.0d
			&& Math.abs(value.z) <= SurgicalTablePlane.MAX_TILES + 2.0d;
	}

	static CompoundTag writeLayPose(SurgicalLayPose pose) {
		CompoundTag tag = new CompoundTag();
		tag.putInt(SurgicalAssembly.POSE_AXIS_TAG, pose.axis().ordinal());
		tag.putInt(SurgicalAssembly.POSE_YAW_TAG, pose.yaw());
		tag.putDouble(SurgicalAssembly.POSE_X_TAG, pose.translation().x);
		tag.putDouble(SurgicalAssembly.POSE_Y_TAG, pose.translation().y);
		tag.putDouble(SurgicalAssembly.POSE_Z_TAG, pose.translation().z);
		return tag;
	}

	static @Nullable
	SurgicalLayPose readLayPose(CompoundTag tag) {
		if (!tag.contains(SurgicalAssembly.POSE_AXIS_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(SurgicalAssembly.POSE_YAW_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(SurgicalAssembly.POSE_X_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(SurgicalAssembly.POSE_Y_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(SurgicalAssembly.POSE_Z_TAG, Tag.TAG_ANY_NUMERIC))
			return null;
		int axis = tag.getInt(SurgicalAssembly.POSE_AXIS_TAG);
		if (axis < 0 || axis >= SurgicalLayPose.RotationAxis.values().length)
			return null;
		try {
			return new SurgicalLayPose(SurgicalLayPose.RotationAxis.values()[axis], tag.getInt(SurgicalAssembly.POSE_YAW_TAG),
				new Vec3(tag.getDouble(SurgicalAssembly.POSE_X_TAG), tag.getDouble(SurgicalAssembly.POSE_Y_TAG), tag.getDouble(SurgicalAssembly.POSE_Z_TAG)));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	static Map<Integer, Vec3> sanitizeOffsets(Map<Integer, Vec3> offsets, int cubeCount,
		BitSet presentCubes) {
		if (offsets == null || offsets.size() > cubeCount)
			return Map.of();
		Map<Integer, Vec3> sanitized = new HashMap<>();
		for (Map.Entry<Integer, Vec3> entry : offsets.entrySet()) {
			Integer cube = entry.getKey();
			if (cube == null || cube < 0 || cube >= cubeCount || !presentCubes.get(cube)
				|| !SurgicalAssemblyPersistence.finiteVector(entry.getValue()))
				return Map.of();
			if (!entry.getValue().equals(Vec3.ZERO))
				sanitized.put(cube, entry.getValue());
		}
		return Map.copyOf(sanitized);
	}

	static Map<Integer, SurgicalCubeRotation> sanitizeRotations(
		Map<Integer, SurgicalCubeRotation> rotations, int cubeCount, BitSet presentCubes) {
		if (rotations == null || rotations.size() > cubeCount)
			return Map.of();
		Map<Integer, SurgicalCubeRotation> sanitized = new HashMap<>();
		for (Map.Entry<Integer, SurgicalCubeRotation> entry : rotations.entrySet()) {
			Integer cube = entry.getKey();
			SurgicalCubeRotation rotation = entry.getValue();
			if (cube == null || cube < 0 || cube >= cubeCount || !presentCubes.get(cube) || rotation == null)
				return Map.of();
			if (!rotation.isIdentity())
				sanitized.put(cube, rotation);
		}
		return Map.copyOf(sanitized);
	}

	static void writeOffsets(CompoundTag tag, Map<Integer, Vec3> offsets) {
		if (offsets.isEmpty())
			return;
		List<Map.Entry<Integer, Vec3>> entries = offsets.entrySet().stream()
			.sorted(Map.Entry.comparingByKey()).toList();
		int[] cubes = new int[entries.size()];
		long[] x = new long[entries.size()];
		long[] y = new long[entries.size()];
		long[] z = new long[entries.size()];
		for (int index = 0; index < entries.size(); index++) {
			cubes[index] = entries.get(index).getKey();
			x[index] = Double.doubleToRawLongBits(entries.get(index).getValue().x);
			y[index] = Double.doubleToRawLongBits(entries.get(index).getValue().y);
			z[index] = Double.doubleToRawLongBits(entries.get(index).getValue().z);
		}
		tag.putIntArray(SurgicalAssembly.OFFSET_CUBES_TAG, cubes);
		tag.putLongArray(SurgicalAssembly.OFFSET_X_TAG, x);
		tag.putLongArray(SurgicalAssembly.OFFSET_Y_TAG, y);
		tag.putLongArray(SurgicalAssembly.OFFSET_Z_TAG, z);
	}

	static @Nullable
	Map<Integer, Vec3> readOffsets(CompoundTag tag, int cubeCount, BitSet present) {
		boolean presentInTag = tag.contains(SurgicalAssembly.OFFSET_CUBES_TAG) || tag.contains(SurgicalAssembly.OFFSET_X_TAG)
			|| tag.contains(SurgicalAssembly.OFFSET_Y_TAG) || tag.contains(SurgicalAssembly.OFFSET_Z_TAG);
		if (!presentInTag)
			return Map.of();
		if (!tag.contains(SurgicalAssembly.OFFSET_CUBES_TAG, Tag.TAG_INT_ARRAY)
			|| !tag.contains(SurgicalAssembly.OFFSET_X_TAG, Tag.TAG_LONG_ARRAY)
			|| !tag.contains(SurgicalAssembly.OFFSET_Y_TAG, Tag.TAG_LONG_ARRAY)
			|| !tag.contains(SurgicalAssembly.OFFSET_Z_TAG, Tag.TAG_LONG_ARRAY))
			return null;
		int[] cubes = tag.getIntArray(SurgicalAssembly.OFFSET_CUBES_TAG);
		long[] x = tag.getLongArray(SurgicalAssembly.OFFSET_X_TAG);
		long[] y = tag.getLongArray(SurgicalAssembly.OFFSET_Y_TAG);
		long[] z = tag.getLongArray(SurgicalAssembly.OFFSET_Z_TAG);
		if (cubes.length != x.length || cubes.length != y.length || cubes.length != z.length
			|| cubes.length > cubeCount)
			return null;
		Map<Integer, Vec3> offsets = new HashMap<>();
		for (int index = 0; index < cubes.length; index++) {
			Vec3 offset = new Vec3(Double.longBitsToDouble(x[index]), Double.longBitsToDouble(y[index]),
				Double.longBitsToDouble(z[index]));
			if (cubes[index] < 0 || cubes[index] >= cubeCount || !present.get(cubes[index])
				|| !SurgicalAssemblyPersistence.finiteVector(offset) || offsets.put(cubes[index], offset) != null)
				return null;
		}
		return Map.copyOf(offsets);
	}

	static void writeRotations(CompoundTag tag, Map<Integer, SurgicalCubeRotation> rotations) {
		if (rotations.isEmpty())
			return;
		ListTag encoded = new ListTag();
		for (Map.Entry<Integer, SurgicalCubeRotation> entry : rotations.entrySet().stream()
			.sorted(Map.Entry.comparingByKey()).toList()) {
			CompoundTag value = new CompoundTag();
			value.putInt(SurgicalAssembly.ROTATION_CUBE_TAG, entry.getKey());
			value.put(SurgicalAssembly.ROTATION_VALUE_TAG, entry.getValue().save());
			encoded.add(value);
		}
		tag.put(SurgicalAssembly.ROTATIONS_TAG, encoded);
	}

	static @Nullable
	Map<Integer, SurgicalCubeRotation> readRotations(CompoundTag tag, int cubeCount,
		BitSet present) {
		if (!tag.contains(SurgicalAssembly.ROTATIONS_TAG))
			return Map.of();
		if (!tag.contains(SurgicalAssembly.ROTATIONS_TAG, Tag.TAG_LIST))
			return null;
		ListTag encoded = (ListTag) tag.get(SurgicalAssembly.ROTATIONS_TAG);
		if (!encoded.isEmpty() && encoded.getElementType() != Tag.TAG_COMPOUND)
			return null;
		if (encoded.size() > cubeCount)
			return null;
		Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>();
		for (int index = 0; index < encoded.size(); index++) {
			CompoundTag value = encoded.getCompound(index);
			if (!value.contains(SurgicalAssembly.ROTATION_CUBE_TAG, Tag.TAG_ANY_NUMERIC))
				return null;
			int cube = value.getInt(SurgicalAssembly.ROTATION_CUBE_TAG);
			SurgicalCubeRotation rotation = value.contains(SurgicalAssembly.ROTATION_VALUE_TAG, Tag.TAG_COMPOUND)
				? SurgicalCubeRotation.load(value.getCompound(SurgicalAssembly.ROTATION_VALUE_TAG)) : null;
			if (cube < 0 || cube >= cubeCount || !present.get(cube) || rotation == null
				|| rotations.putIfAbsent(cube, rotation) != null)
				return null;
		}
		return SurgicalAssemblyPersistence.sanitizeRotations(rotations, cubeCount, present);
	}

	static boolean hasWrongType(CompoundTag tag, String key, int expectedType) {
		return tag.contains(key) && !tag.contains(key, expectedType);
	}
}
