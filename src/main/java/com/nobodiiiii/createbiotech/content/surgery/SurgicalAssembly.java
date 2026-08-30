package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/** Immutable, side-safe topology and source-model state for one packed surgical body. */
public final class SurgicalAssembly {
	public static final int MAX_CUBES = 1024;
	public static final int MAX_SEAMS = 4096;
	public static final int MAX_SOURCES = 256;
	/** One neck, two two-joint arms, and as many as eight two-joint legs. */
	public static final int MAX_LIMBS = 21;
	public static final double MAX_BODY_SIZE = 64.0d;
	public static final double MIN_BODY_SIZE = 1.0d / 64.0d;
	private static final int CURRENT_VERSION = 16;
	private static final String VERSION_TAG = "Version";
	private static final String PROFILE_TAG = "MimicProfile";
	private static final String CUBE_COUNT_TAG = "CubeCount";
	private static final String PRESENT_CUBES_TAG = "PresentCubes";
	private static final String SEAMS_TAG = "Seams";
	private static final String CUT_SEAMS_TAG = "CutSeams";
	private static final String CUT_ORDER_TAG = "CutOrder";
	private static final String SOURCES_TAG = "Sources";
	private static final String JOINTS_TAG = "GlueJoints";
	private static final String COMBINATIONS_TAG = "Combinations";
	private static final String COMBINATION_ID_TAG = "Id";
	private static final String COMBINATION_MEMBERS_TAG = "Members";
	private static final String MEMBER_SOURCE_TAG = "Source";
	private static final String MEMBER_CUBE_TAG = "Cube";
	private static final String LIMBS_TAG = "Limbs";
	private static final String LIMB_TYPE_TAG = "Type";
	private static final String LIMB_CHILD_SOURCE_TAG = "ChildSource";
	private static final String LIMB_CHILD_CUBE_TAG = "ChildCube";
	private static final String LIMB_PARENT_SOURCE_TAG = "ParentSource";
	private static final String LIMB_PARENT_CUBE_TAG = "ParentCube";
	private static final String PRESERVE_LAYOUT_TAG = "PreserveLayout";
	private static final String LAYOUT_FACING_TAG = "LayoutFacing";
	private static final String LAYOUT_LAY_POSE_TAG = "LayoutLayPose";
	private static final String BODY_WIDTH_TAG = "BodyWidth";
	private static final String BODY_HEIGHT_TAG = "BodyHeight";
	private static final String BODY_DEPTH_TAG = "BodyDepth";
	private static final String BODY_CENTER_X_TAG = "BodyCenterX";
	private static final String BODY_MIN_Y_TAG = "BodyMinY";
	private static final String BODY_CENTER_Z_TAG = "BodyCenterZ";
	private static final String BODY_LEG_LENGTH_TAG = "BodyLegLength";
	private static final String ATTACK_GEOMETRY_TAG = "AttackGeometry";
	private static final String RIGHT_ARM_TAG = "RightArm";
	private static final String LEFT_ARM_TAG = "LeftArm";
	private static final String ATTACK_RADIUS_TAG = "Radius";
	private static final String ATTACK_VOLUME_TAG = "Volume";
	private static final String ATTACK_ORIGIN_X_TAG = "OriginX";
	private static final String ATTACK_ORIGIN_Y_TAG = "OriginY";
	private static final String ATTACK_ORIGIN_Z_TAG = "OriginZ";
	private static final String ATTACK_REACH_TAG = "Reach";
	private static final String ATTACK_MINIMUM_Y_TAG = "MinimumY";
	private static final String ATTACK_MAXIMUM_Y_TAG = "MaximumY";
	// Version 13 stored authored animation paths. They remain readable for save migration only.
	private static final String EMPTY_HAND_PATH_TAG = "EmptyHandPath";
	private static final String WEAPON_PATH_TAG = "WeaponPath";
	private static final String FACING_TAG = "Facing";
	private static final String LAY_POSE_TAG = "LayPose";
	private static final String POSE_AXIS_TAG = "Axis";
	private static final String POSE_YAW_TAG = "Yaw";
	private static final String POSE_X_TAG = "TranslateX";
	private static final String POSE_Y_TAG = "TranslateY";
	private static final String POSE_Z_TAG = "TranslateZ";
	private static final String ORIGIN_X_TAG = "OriginX";
	private static final String ORIGIN_Y_TAG = "OriginY";
	private static final String ORIGIN_Z_TAG = "OriginZ";
	private static final String OFFSET_CUBES_TAG = "OffsetCubes";
	private static final String OFFSET_X_TAG = "OffsetX";
	private static final String OFFSET_Y_TAG = "OffsetY";
	private static final String OFFSET_Z_TAG = "OffsetZ";
	private static final String ROTATIONS_TAG = "CubeRotations";
	private static final String ROTATION_CUBE_TAG = "Cube";
	private static final String ROTATION_VALUE_TAG = "Rotation";
	private static final String FIRST_SOURCE_TAG = "FirstSource";
	private static final String FIRST_CUBE_TAG = "FirstCube";
	private static final String SECOND_SOURCE_TAG = "SecondSource";
	private static final String SECOND_CUBE_TAG = "SecondCube";
	private static final String JOINT_REPLAY_TAG = "GlueReplay";
	private static final String MOVING_SOURCE_TAG = "MovingSource";
	private static final String MOVING_CUBE_TAG = "MovingCube";
	private static final String GLUE_TRANSFORM_TAG = "Transform";
	private static final String ANCHOR_CONTACT_TAG = "AnchorContact";

	private final List<Source> sources;
	private final List<Joint> joints;
	private final List<Combination> combinations;
	private final List<Limb> limbs;
	@Nullable
	private transient LimbTopology limbTopology;
	private final boolean preserveLayout;
	private final Direction layoutFacing;
	private final SurgicalLayPose layoutLayPose;
	@Nullable
	private final BodyBounds bodyBounds;
	@Nullable
	private final AttackGeometry attackGeometry;

	private SurgicalAssembly(List<Source> sources, List<Joint> joints, List<Combination> combinations,
		List<Limb> limbs, boolean preserveLayout,
		Direction layoutFacing, SurgicalLayPose layoutLayPose, @Nullable BodyBounds bodyBounds,
		@Nullable AttackGeometry attackGeometry) {
		this.sources = List.copyOf(sources);
		this.joints = List.copyOf(joints);
		this.combinations = List.copyOf(combinations);
		this.limbs = List.copyOf(limbs);
		this.preserveLayout = preserveLayout;
		this.layoutFacing = horizontal(layoutFacing);
		this.layoutLayPose = layoutLayPose == null ? SurgicalLayPose.IDENTITY : layoutLayPose;
		this.bodyBounds = bodyBounds;
		this.attackGeometry = attackGeometry;
	}

	@Nullable
	public static SurgicalAssembly create(MimicProfile profile, int cubeCount, BitSet presentCubes,
		List<Seam> seams, BitSet cutSeams) {
		return create(profile, cubeCount, presentCubes, seams, cutSeams, List.of());
	}

	@Nullable
	public static SurgicalAssembly create(MimicProfile profile, int cubeCount, BitSet presentCubes,
		List<Seam> seams, BitSet cutSeams, List<Integer> cutOrder) {
		Source source = Source.create(profile, cubeCount, presentCubes, seams, cutSeams, cutOrder,
			Direction.NORTH, SurgicalLayPose.IDENTITY, Vec3.ZERO, Map.of());
		return source == null ? null
			: new SurgicalAssembly(List.of(source), List.of(), List.of(), List.of(), false, Direction.NORTH,
				SurgicalLayPose.IDENTITY, null, null);
	}

	@Nullable
	public static SurgicalAssembly createComposite(List<Source> sources, List<Joint> joints) {
		return createComposite(sources, joints, List.of(), inferLayoutFacing(sources), inferLayoutLayPose(sources));
	}

	@Nullable
	public static SurgicalAssembly createComposite(List<Source> sources, List<Joint> joints,
		Direction layoutFacing) {
		return createComposite(sources, joints, List.of(), layoutFacing, inferLayoutLayPose(sources));
	}

	@Nullable
	public static SurgicalAssembly createComposite(List<Source> sources, List<Joint> joints,
		Direction layoutFacing, SurgicalLayPose layoutLayPose) {
		return createComposite(sources, joints, List.of(), layoutFacing, layoutLayPose);
	}

	@Nullable
	public static SurgicalAssembly createComposite(List<Source> sources, List<Joint> joints,
		List<Combination> combinations, Direction layoutFacing, SurgicalLayPose layoutLayPose) {
		return createComposite(sources, joints, combinations, List.of(), layoutFacing, layoutLayPose);
	}

	@Nullable
	public static SurgicalAssembly createComposite(List<Source> sources, List<Joint> joints,
		List<Combination> combinations, List<Limb> limbs, Direction layoutFacing,
		SurgicalLayPose layoutLayPose) {
		if (sources == null || sources.isEmpty() || sources.size() > MAX_SOURCES || joints == null
			|| joints.size() > MAX_SEAMS || combinations == null || combinations.size() > MAX_CUBES
			|| limbs == null || limbs.size() > MAX_LIMBS)
			return null;
		List<Source> frozenSources = new ArrayList<>(sources.size());
		int totalCubes = 0;
		for (Source source : sources) {
			if (source == null || !source.valid())
				return null;
			totalCubes += source.presentCubes.cardinality();
			if (totalCubes > MAX_CUBES)
				return null;
			frozenSources.add(source.copy());
		}
		Set<ConnectionKey> unique = new HashSet<>();
		List<Joint> frozenJoints = new ArrayList<>(joints.size());
		for (Joint joint : joints) {
			Joint normalized = joint == null ? null : joint.normalized();
			if (normalized == null || !normalized.validFor(frozenSources)
				|| !unique.add(ConnectionKey.of(normalized.firstSource, normalized.firstCube,
					normalized.secondSource, normalized.secondCube)))
				return null;
			frozenJoints.add(normalized);
		}
		Set<UUID> combinationIds = new HashSet<>();
		Set<CombinationMember> combinedMembers = new HashSet<>();
		List<Combination> frozenCombinations = new ArrayList<>(combinations.size());
		for (Combination combination : combinations) {
			Combination normalized = combination == null ? null : combination.normalized();
			if (normalized == null || !normalized.validFor(frozenSources)
				|| !combinationIds.add(normalized.id()))
				return null;
			for (CombinationMember member : normalized.members())
				if (!combinedMembers.add(member))
					return null;
			frozenCombinations.add(normalized);
		}
		List<Limb> frozenLimbs = normalizeLimbs(limbs, frozenSources);
		if (frozenLimbs == null)
			return null;
		return new SurgicalAssembly(frozenSources, frozenJoints, frozenCombinations, frozenLimbs, true,
			layoutFacing, layoutLayPose, null, null);
	}

	/**
	 * Drops limbs that no longer describe present cubes and enforces the per-type limits. Packing
	 * and placement both round-trip through this, so a body can never carry a second neck or a limb
	 * whose pivot has been cut away.
	 */
	@Nullable
	private static List<Limb> normalizeLimbs(List<Limb> limbs, List<Source> sources) {
		if (limbs.isEmpty())
			return List.of();
		Map<SurgicalLimbType, Integer> counts = new HashMap<>();
		Set<CombinationMember> children = new HashSet<>();
		List<Limb> normalized = new ArrayList<>(limbs.size());
		for (Limb limb : limbs) {
			if (limb == null || limb.type() == null || !limb.validFor(sources))
				return null;
			if (!children.add(new CombinationMember(limb.childSource(), limb.childCube())))
				return null;
			int used = counts.merge(limb.type(), 1, Integer::sum);
			if (used > limb.type().maxPerBody())
				return null;
			normalized.add(limb);
		}
		return List.copyOf(normalized);
	}

	@Nullable
	public static SurgicalAssembly load(CompoundTag tag) {
		int version = tag.getInt(VERSION_TAG);
		if (version == 1 || version == 2)
			return loadLegacy(tag, version);
		if (version < 3 || version > CURRENT_VERSION || !tag.contains(SOURCES_TAG, Tag.TAG_LIST))
			return null;

		ListTag encodedSources = tag.getList(SOURCES_TAG, Tag.TAG_COMPOUND);
		if (encodedSources.isEmpty() || encodedSources.size() > MAX_SOURCES)
			return null;
		List<Source> sources = new ArrayList<>(encodedSources.size());
		for (int index = 0; index < encodedSources.size(); index++) {
			Source source = Source.load(encodedSources.getCompound(index));
			if (source == null)
				return null;
			sources.add(source);
		}

		List<Joint> joints = new ArrayList<>();
		if (tag.contains(JOINTS_TAG, Tag.TAG_LIST)) {
			ListTag encodedJoints = tag.getList(JOINTS_TAG, Tag.TAG_COMPOUND);
			if (encodedJoints.size() > MAX_SEAMS)
				return null;
			for (int index = 0; index < encodedJoints.size(); index++) {
				CompoundTag encoded = encodedJoints.getCompound(index);
				int firstSource = encoded.getInt(FIRST_SOURCE_TAG);
				int firstCube = encoded.getInt(FIRST_CUBE_TAG);
				int secondSource = encoded.getInt(SECOND_SOURCE_TAG);
				int secondCube = encoded.getInt(SECOND_CUBE_TAG);
				JointReplay replay = null;
				if (version >= 16 && encoded.contains(JOINT_REPLAY_TAG, Tag.TAG_COMPOUND)) {
					CompoundTag encodedReplay = encoded.getCompound(JOINT_REPLAY_TAG);
					SurgicalGlueTransform transform = encodedReplay.contains(GLUE_TRANSFORM_TAG, Tag.TAG_COMPOUND)
						? SurgicalGlueTransform.load(encodedReplay.getCompound(GLUE_TRANSFORM_TAG)) : null;
					SurgicalGlueContact anchorContact = encodedReplay.contains(ANCHOR_CONTACT_TAG, Tag.TAG_COMPOUND)
						? SurgicalGlueContact.load(encodedReplay.getCompound(ANCHOR_CONTACT_TAG)) : null;
					if (encodedReplay.contains(MOVING_SOURCE_TAG, Tag.TAG_ANY_NUMERIC)
						&& encodedReplay.contains(MOVING_CUBE_TAG, Tag.TAG_ANY_NUMERIC)
						&& transform != null && anchorContact != null) {
						int movingSource = encodedReplay.getInt(MOVING_SOURCE_TAG);
						int movingCube = encodedReplay.getInt(MOVING_CUBE_TAG);
						if (movingSource >= 0 && movingCube >= 0)
							replay = new JointReplay(movingSource, movingCube, transform, anchorContact);
					}
				}
				if (replay != null && !replay.matches(firstSource, firstCube)
					&& !replay.matches(secondSource, secondCube))
					replay = null;
				joints.add(new Joint(firstSource, firstCube, secondSource, secondCube, replay));
			}
		}
		List<Combination> combinations = new ArrayList<>();
		if (version >= 7 && tag.contains(COMBINATIONS_TAG, Tag.TAG_LIST)) {
			ListTag encodedCombinations = tag.getList(COMBINATIONS_TAG, Tag.TAG_COMPOUND);
			if (encodedCombinations.size() > MAX_CUBES)
				return null;
			for (int index = 0; index < encodedCombinations.size(); index++) {
				CompoundTag encoded = encodedCombinations.getCompound(index);
				if (!encoded.hasUUID(COMBINATION_ID_TAG)
					|| !encoded.contains(COMBINATION_MEMBERS_TAG, Tag.TAG_LIST))
					return null;
				ListTag encodedMembers = encoded.getList(COMBINATION_MEMBERS_TAG, Tag.TAG_COMPOUND);
				if (encodedMembers.size() < 2 || encodedMembers.size() > MAX_CUBES)
					return null;
				List<CombinationMember> members = new ArrayList<>(encodedMembers.size());
				for (int memberIndex = 0; memberIndex < encodedMembers.size(); memberIndex++) {
					CompoundTag member = encodedMembers.getCompound(memberIndex);
					if (!member.contains(MEMBER_SOURCE_TAG, Tag.TAG_ANY_NUMERIC)
						|| !member.contains(MEMBER_CUBE_TAG, Tag.TAG_ANY_NUMERIC))
						return null;
					members.add(new CombinationMember(member.getInt(MEMBER_SOURCE_TAG),
						member.getInt(MEMBER_CUBE_TAG)));
				}
				combinations.add(new Combination(encoded.getUUID(COMBINATION_ID_TAG), members));
			}
		}
		List<Limb> limbs = new ArrayList<>();
		if (version >= 8 && tag.contains(LIMBS_TAG, Tag.TAG_LIST)) {
			ListTag encodedLimbs = tag.getList(LIMBS_TAG, Tag.TAG_COMPOUND);
			if (encodedLimbs.size() > MAX_LIMBS)
				return null;
			for (int index = 0; index < encodedLimbs.size(); index++) {
				CompoundTag encoded = encodedLimbs.getCompound(index);
				SurgicalLimbType type = encoded.contains(LIMB_TYPE_TAG, Tag.TAG_STRING)
					? SurgicalLimbType.byId(encoded.getString(LIMB_TYPE_TAG)) : null;
				if (type == null)
					return null;
				limbs.add(new Limb(type, encoded.getInt(LIMB_CHILD_SOURCE_TAG),
					encoded.getInt(LIMB_CHILD_CUBE_TAG), encoded.getInt(LIMB_PARENT_SOURCE_TAG),
					encoded.getInt(LIMB_PARENT_CUBE_TAG)));
			}
		}
		Direction layoutFacing = version >= 4 && tag.contains(LAYOUT_FACING_TAG, Tag.TAG_ANY_NUMERIC)
			? Direction.from3DDataValue(tag.getInt(LAYOUT_FACING_TAG)) : inferLayoutFacing(sources);
		SurgicalLayPose layoutLayPose = version >= 5 && tag.contains(LAYOUT_LAY_POSE_TAG, Tag.TAG_COMPOUND)
			? readLayPose(tag.getCompound(LAYOUT_LAY_POSE_TAG)) : inferLayoutLayPose(sources);
		SurgicalAssembly assembly = createComposite(sources, joints, combinations, limbs, layoutFacing,
			layoutLayPose);
		if (assembly == null)
			return null;
		assembly = tag.getBoolean(PRESERVE_LAYOUT_TAG) ? assembly
			: new SurgicalAssembly(assembly.sources, assembly.joints, assembly.combinations,
				assembly.limbs, false, assembly.layoutFacing,
				assembly.layoutLayPose, null, null);
		// Versions 9 and 10 used older bounds. Discard them so those bodies are measured again with
		// the horizontal-only weighting introduced in version 11. Version 11 bounds remain usable;
		// their absent leg length defaults to zero until a rendering client measures it.
		if (version >= 11 && tag.contains(BODY_WIDTH_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(BODY_HEIGHT_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(BODY_DEPTH_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(BODY_CENTER_X_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(BODY_MIN_Y_TAG, Tag.TAG_ANY_NUMERIC)
			&& tag.contains(BODY_CENTER_Z_TAG, Tag.TAG_ANY_NUMERIC)) {
			BodyBounds bounds = BodyBounds.create(tag.getDouble(BODY_WIDTH_TAG),
				tag.getDouble(BODY_HEIGHT_TAG), tag.getDouble(BODY_DEPTH_TAG),
				tag.getDouble(BODY_CENTER_X_TAG), tag.getDouble(BODY_MIN_Y_TAG),
				tag.getDouble(BODY_CENTER_Z_TAG),
				version >= 12 && tag.contains(BODY_LEG_LENGTH_TAG, Tag.TAG_ANY_NUMERIC)
					? tag.getDouble(BODY_LEG_LENGTH_TAG) : 0.0d);
			if (bounds == null)
				return null;
			assembly = assembly.withBodyBounds(bounds);
		}
		if (version >= 13 && tag.contains(ATTACK_GEOMETRY_TAG, Tag.TAG_COMPOUND)) {
			AttackGeometry geometry = AttackGeometry.load(tag.getCompound(ATTACK_GEOMETRY_TAG));
			if (geometry == null)
				return null;
			assembly = assembly.withAttackGeometry(geometry);
		}
		return assembly;
	}

	@Nullable
	private static SurgicalAssembly loadLegacy(CompoundTag tag, int version) {
		if (!tag.contains(PROFILE_TAG, Tag.TAG_COMPOUND)
			|| !tag.contains(CUBE_COUNT_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(PRESENT_CUBES_TAG, Tag.TAG_LONG_ARRAY)
			|| !tag.contains(SEAMS_TAG, Tag.TAG_INT_ARRAY))
			return null;
		MimicProfile profile = MimicProfile.load(tag.getCompound(PROFILE_TAG));
		int cubeCount = tag.getInt(CUBE_COUNT_TAG);
		List<Seam> seams = decodeSeams(tag.getIntArray(SEAMS_TAG));
		if (profile == null || seams == null || !validTopology(cubeCount, seams))
			return null;
		BitSet present = BitSet.valueOf(tag.getLongArray(PRESENT_CUBES_TAG));
		BitSet cut = tag.contains(CUT_SEAMS_TAG, Tag.TAG_LONG_ARRAY)
			? BitSet.valueOf(tag.getLongArray(CUT_SEAMS_TAG)) : new BitSet();
		List<Integer> cutOrder = version >= 2 && tag.contains(CUT_ORDER_TAG, Tag.TAG_INT_ARRAY)
			? decodeCutOrder(tag.getIntArray(CUT_ORDER_TAG)) : List.of();
		return create(profile, cubeCount, present, seams, cut, cutOrder);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt(VERSION_TAG, CURRENT_VERSION);
		ListTag encodedSources = new ListTag();
		for (Source source : sources)
			encodedSources.add(source.save());
		tag.put(SOURCES_TAG, encodedSources);
		if (!joints.isEmpty()) {
			ListTag encodedJoints = new ListTag();
			for (Joint joint : joints) {
				CompoundTag encoded = new CompoundTag();
				encoded.putInt(FIRST_SOURCE_TAG, joint.firstSource);
				encoded.putInt(FIRST_CUBE_TAG, joint.firstCube);
				encoded.putInt(SECOND_SOURCE_TAG, joint.secondSource);
				encoded.putInt(SECOND_CUBE_TAG, joint.secondCube);
				if (joint.replay != null) {
					CompoundTag encodedReplay = new CompoundTag();
					encodedReplay.putInt(MOVING_SOURCE_TAG, joint.replay.movingSource);
					encodedReplay.putInt(MOVING_CUBE_TAG, joint.replay.movingCube);
					encodedReplay.put(GLUE_TRANSFORM_TAG, joint.replay.transform.save());
					encodedReplay.put(ANCHOR_CONTACT_TAG, joint.replay.anchorContact.save());
					encoded.put(JOINT_REPLAY_TAG, encodedReplay);
				}
				encodedJoints.add(encoded);
			}
			tag.put(JOINTS_TAG, encodedJoints);
		}
		if (!combinations.isEmpty()) {
			ListTag encodedCombinations = new ListTag();
			for (Combination combination : combinations) {
				CompoundTag encoded = new CompoundTag();
				encoded.putUUID(COMBINATION_ID_TAG, combination.id());
				ListTag encodedMembers = new ListTag();
				for (CombinationMember member : combination.members()) {
					CompoundTag encodedMember = new CompoundTag();
					encodedMember.putInt(MEMBER_SOURCE_TAG, member.source());
					encodedMember.putInt(MEMBER_CUBE_TAG, member.cube());
					encodedMembers.add(encodedMember);
				}
				encoded.put(COMBINATION_MEMBERS_TAG, encodedMembers);
				encodedCombinations.add(encoded);
			}
			tag.put(COMBINATIONS_TAG, encodedCombinations);
		}
		if (!limbs.isEmpty()) {
			ListTag encodedLimbs = new ListTag();
			for (Limb limb : limbs) {
				CompoundTag encoded = new CompoundTag();
				encoded.putString(LIMB_TYPE_TAG, limb.type().id());
				encoded.putInt(LIMB_CHILD_SOURCE_TAG, limb.childSource());
				encoded.putInt(LIMB_CHILD_CUBE_TAG, limb.childCube());
				encoded.putInt(LIMB_PARENT_SOURCE_TAG, limb.parentSource());
				encoded.putInt(LIMB_PARENT_CUBE_TAG, limb.parentCube());
				encodedLimbs.add(encoded);
			}
			tag.put(LIMBS_TAG, encodedLimbs);
		}
		if (preserveLayout)
			tag.putBoolean(PRESERVE_LAYOUT_TAG, true);
		tag.putInt(LAYOUT_FACING_TAG, layoutFacing.get3DDataValue());
		tag.put(LAYOUT_LAY_POSE_TAG, writeLayPose(layoutLayPose));
		if (bodyBounds != null) {
			tag.putFloat(BODY_WIDTH_TAG, bodyBounds.width());
			tag.putFloat(BODY_HEIGHT_TAG, bodyBounds.height());
			tag.putFloat(BODY_DEPTH_TAG, bodyBounds.depth());
			tag.putFloat(BODY_CENTER_X_TAG, bodyBounds.centerX());
			tag.putFloat(BODY_MIN_Y_TAG, bodyBounds.minY());
			tag.putFloat(BODY_CENTER_Z_TAG, bodyBounds.centerZ());
			tag.putFloat(BODY_LEG_LENGTH_TAG, bodyBounds.legLength());
		}
		if (attackGeometry != null)
			tag.put(ATTACK_GEOMETRY_TAG, attackGeometry.save());
		return tag;
	}

	public List<Source> sources() { return sources; }
	public List<Joint> joints() { return joints; }
	public List<Combination> combinations() { return combinations; }
	public List<Limb> limbs() { return limbs; }
	/** Installed joints that currently satisfy cube ownership and tier-matching rules. */
	public List<Limb> effectiveLimbs() { return limbTopology().effective; }
	public boolean preservesLayout() { return preserveLayout; }
	public Direction layoutFacing() { return layoutFacing; }
	public SurgicalLayPose layoutLayPose() { return layoutLayPose; }
	@Nullable
	public BodyBounds bodyBounds() { return bodyBounds; }
	@Nullable
	public AttackGeometry attackGeometry() { return attackGeometry; }

	public SurgicalAssembly withBodyBounds(BodyBounds bounds) {
		if (bounds == null)
			throw new IllegalArgumentException("A surgical body requires valid bounds");
		return new SurgicalAssembly(sources, joints, combinations, limbs, preserveLayout, layoutFacing,
			layoutLayPose, bounds, attackGeometry);
	}

	public SurgicalAssembly withAttackGeometry(AttackGeometry geometry) {
		if (geometry == null)
			throw new IllegalArgumentException("A surgical attack geometry cannot be null");
		return new SurgicalAssembly(sources, joints, combinations, limbs, preserveLayout, layoutFacing,
			layoutLayPose, bodyBounds, geometry);
	}

	/**
	 * The rigid island moved by a valid limb. Anatomical ownership remains cube/honey based, while
	 * motion also follows every ordinary seam or glue connection that does not cross an installed
	 * hinge.
	 */
	public List<CombinationMember> rotatingGroup(int source, int cube) {
		CombinationMember selected = new CombinationMember(source, cube);
		List<CombinationMember> automatic = limbTopology().groups.get(selected);
		if (automatic != null)
			return automatic;
		for (Combination combination : combinations)
			if (combination.members().contains(selected))
				return combination.members();
		return List.of(selected);
	}

	private LimbTopology limbTopology() {
		LimbTopology cached = limbTopology;
		if (cached != null)
			return cached;
		cached = buildLimbTopology();
		limbTopology = cached;
		return cached;
	}

	/**
	 * Resolves the two explicit ownership cases:
	 * <ul>
	 *   <li>a cube (or honey combination) whose only anatomical attachment is one joint's child side;</li>
	 *   <li>a middle island attached by one first-level child side and the matching second-level
	 *       parent side, which belongs to the first-level joint.</li>
	 * </ul>
	 * A second-level joint remains serialized when unmatched, but is absent from {@code effective}.
	 */
	private LimbTopology buildLimbTopology() {
		if (limbs.isEmpty())
			return LimbTopology.EMPTY;
		// Ordinary model seams and glue do not decide anatomical ownership. Honey combinations are the
		// sole explicit way to make several cubes one ownership unit.
		Map<CombinationMember, Integer> componentIds = new HashMap<>();
		List<List<CombinationMember>> components = new ArrayList<>();
		for (Combination combination : combinations) {
			int componentId = components.size();
			components.add(combination.members());
			for (CombinationMember member : combination.members())
				componentIds.put(member, componentId);
		}
		for (int sourceId = 0; sourceId < sources.size(); sourceId++) {
			Source source = sources.get(sourceId);
			for (int cubeId = source.presentCubes.nextSetBit(0); cubeId >= 0;
				cubeId = source.presentCubes.nextSetBit(cubeId + 1)) {
				CombinationMember member = new CombinationMember(sourceId, cubeId);
				if (componentIds.containsKey(member))
					continue;
				int componentId = components.size();
				componentIds.put(member, componentId);
				components.add(List.of(member));
			}
		}

		Map<Integer, List<LimbAttachment>> attachments = new HashMap<>();
		Map<Limb, Integer> childComponents = new HashMap<>();
		Map<Limb, Integer> parentComponents = new HashMap<>();
		for (Limb limb : limbs) {
			Integer child = componentIds.get(new CombinationMember(limb.childSource(), limb.childCube()));
			Integer parent = componentIds.get(new CombinationMember(limb.parentSource(), limb.parentCube()));
			if (child == null || parent == null || child.equals(parent))
				continue;
			childComponents.put(limb, child);
			parentComponents.put(limb, parent);
			attachments.computeIfAbsent(child, ignored -> new ArrayList<>())
				.add(new LimbAttachment(limb, true));
			attachments.computeIfAbsent(parent, ignored -> new ArrayList<>())
				.add(new LimbAttachment(limb, false));
		}

		Map<Integer, Limb> owners = new HashMap<>();
		for (Map.Entry<Integer, List<LimbAttachment>> entry : attachments.entrySet()) {
			List<LimbAttachment> attached = entry.getValue();
			List<Limb> primaryChildren = new ArrayList<>();
			boolean primaryParent = false;
			List<Limb> secondaryChildren = new ArrayList<>();
			for (LimbAttachment attachment : attached) {
				if (attachment.child && attachment.limb.type().primary())
					primaryChildren.add(attachment.limb);
				else if (!attachment.child && attachment.limb.type().primary())
					primaryParent = true;
				else if (attachment.child && attachment.limb.type().secondary())
					secondaryChildren.add(attachment.limb);
			}
			// Inactive second-level joints may be installed anywhere and therefore do not steal a rigid
			// island from its one first-level child joint. A primary-to-primary chain remains ambiguous.
			if (primaryChildren.size() == 1 && !primaryParent)
				owners.put(entry.getKey(), primaryChildren.getFirst());
			else if (primaryChildren.isEmpty() && !primaryParent && secondaryChildren.size() == 1
				&& attached.size() == 1)
				owners.put(entry.getKey(), secondaryChildren.getFirst());
		}

		List<Limb> effective = new ArrayList<>();
		for (Limb limb : limbs) {
			Integer child = childComponents.get(limb);
			Integer parent = parentComponents.get(limb);
			if (child == null || parent == null || owners.get(child) != limb)
				continue;
			if (limb.type().primary() && attachments.getOrDefault(parent, List.of()).stream()
				.anyMatch(attachment -> attachment.child && attachment.limb.type().primary()))
				continue;
			if (limb.type().secondary()) {
				Limb primary = owners.get(parent);
				if (primary == null || primary.type() != limb.type().matchingPrimary())
					continue;
			}
			effective.add(limb);
		}

		MotionComponents motion = buildMotionComponents();
		Map<CombinationMember, List<CombinationMember>> groups = new HashMap<>();
		for (Limb limb : effective) {
			CombinationMember childMember = new CombinationMember(limb.childSource(), limb.childCube());
			CombinationMember parentMember = new CombinationMember(limb.parentSource(), limb.parentCube());
			Integer motionChild = motion.componentIds.get(childMember);
			Integer motionParent = motion.componentIds.get(parentMember);
			Integer ownershipChild = childComponents.get(limb);
			List<CombinationMember> group = motionChild != null && !motionChild.equals(motionParent)
				? motion.components.get(motionChild)
				: components.get(ownershipChild);
			for (CombinationMember member : group)
				groups.put(member, group);
		}
		return new LimbTopology(List.copyOf(effective), Map.copyOf(groups));
	}

	/**
	 * Splits the packed connection graph at every installed anatomical hinge. The child-side graph
	 * component is the complete set of cubes that must physically follow that hinge, including cubes
	 * attached to the limb by ordinary seams or glue. Ownership validation intentionally does not use
	 * these broad components, so an unrelated connected cluster cannot block another primary joint.
	 */
	private MotionComponents buildMotionComponents() {
		Set<ConnectionKey> hingeEdges = new HashSet<>();
		for (Limb limb : limbs)
			hingeEdges.add(ConnectionKey.of(limb.childSource(), limb.childCube(),
				limb.parentSource(), limb.parentCube()));

		List<SurgicalConnectionGraph.Body<Integer>> bodies = new ArrayList<>(sources.size());
		for (int sourceId = 0; sourceId < sources.size(); sourceId++) {
			Source source = sources.get(sourceId);
			BitSet rigidCuts = source.cutSeams();
			for (int seamId = 0; seamId < source.seams.size(); seamId++) {
				Seam seam = source.seams.get(seamId);
				if (hingeEdges.contains(ConnectionKey.of(sourceId, seam.first(), sourceId, seam.second())))
					rigidCuts.set(seamId);
			}
			bodies.add(new SurgicalConnectionGraph.Body<>(sourceId, source.cubeCount,
				source.presentCubes, source.seams, rigidCuts));
		}

		List<SurgicalConnectionGraph.Link<Integer>> links = new ArrayList<>();
		for (Joint joint : joints)
			if (!hingeEdges.contains(ConnectionKey.of(joint.firstSource(), joint.firstCube(),
				joint.secondSource(), joint.secondCube())))
				links.add(new SurgicalConnectionGraph.Link<>(joint.firstSource(), joint.firstCube(),
					joint.secondSource(), joint.secondCube()));
		// Honey remains an explicit rigid constraint. Honey across a hinge therefore rejoins both sides,
		// and the fallback below prevents that closed rigid loop from dragging the parent body around.
		for (Combination combination : combinations) {
			CombinationMember anchor = combination.members().getFirst();
			for (CombinationMember member : combination.members().subList(1, combination.members().size()))
				links.add(new SurgicalConnectionGraph.Link<>(anchor.source(), anchor.cube(),
					member.source(), member.cube()));
		}
		SurgicalConnectionGraph<Integer> graph = SurgicalConnectionGraph.create(bodies, links);
		if (graph == null)
			return MotionComponents.EMPTY;

		Map<CombinationMember, Integer> componentIds = new HashMap<>();
		List<List<CombinationMember>> motionComponents = new ArrayList<>();
		for (SurgicalConnectionGraph.Component<Integer> component : graph.components()) {
			int componentId = motionComponents.size();
			List<CombinationMember> members = new ArrayList<>(component.size());
			for (Map.Entry<Integer, BitSet> entry : component.members().entrySet())
				for (int cubeId = entry.getValue().nextSetBit(0); cubeId >= 0;
					cubeId = entry.getValue().nextSetBit(cubeId + 1)) {
					CombinationMember member = new CombinationMember(entry.getKey(), cubeId);
					members.add(member);
					componentIds.put(member, componentId);
				}
			motionComponents.add(List.copyOf(members));
		}
		return new MotionComponents(Map.copyOf(componentIds), List.copyOf(motionComponents));
	}

	private record LimbTopology(List<Limb> effective,
		Map<CombinationMember, List<CombinationMember>> groups) {
		private static final LimbTopology EMPTY = new LimbTopology(List.of(), Map.of());
	}

	private record LimbAttachment(Limb limb, boolean child) {}

	private record MotionComponents(Map<CombinationMember, Integer> componentIds,
		List<List<CombinationMember>> components) {
		private static final MotionComponents EMPTY = new MotionComponents(Map.of(), List.of());
	}

	private record ConnectionKey(int firstSource, int firstCube, int secondSource, int secondCube) {
		private static ConnectionKey of(int firstSource, int firstCube, int secondSource, int secondCube) {
			return firstSource < secondSource || firstSource == secondSource && firstCube <= secondCube
				? new ConnectionKey(firstSource, firstCube, secondSource, secondCube)
				: new ConnectionKey(secondSource, secondCube, firstSource, firstCube);
		}
	}

	public SurgicalLayPose placedLayPose(Direction placementFacing) {
		return layoutLayPose.rotateClockwise(clockwiseTurns(layoutFacing, horizontal(placementFacing)));
	}

	/** Rotates every stored source from the packed layout frame into a new table-facing frame. */
	public List<PlacedSource> placedSources(Direction placementFacing) {
		int turns = clockwiseTurns(layoutFacing, horizontal(placementFacing));
		List<PlacedSource> placed = new ArrayList<>(sources.size());
		for (Source source : sources) {
			Map<Integer, Vec3> offsets = new HashMap<>();
			for (Map.Entry<Integer, Vec3> entry : source.cubeOffsets.entrySet())
				offsets.put(entry.getKey(), rotateClockwise(entry.getValue(), turns));
			Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>();
			for (Map.Entry<Integer, SurgicalCubeRotation> entry : source.cubeRotations.entrySet())
				rotations.put(entry.getKey(), entry.getValue().rotateClockwise(turns));
			placed.add(new PlacedSource(source, rotateClockwise(source.facing, turns),
				source.layPose.rotateClockwise(turns),
				rotateClockwise(source.originOffset, turns), offsets, rotations));
		}
		return List.copyOf(placed);
	}

	/** Rotates recorded table-space glue edits into the same frame as {@link #placedSources}. */
	public List<Joint> placedJoints(Direction placementFacing) {
		int turns = clockwiseTurns(layoutFacing, horizontal(placementFacing));
		return joints.stream().map(joint -> joint.rotateClockwise(turns)).toList();
	}

	/** Legacy single-source view retained for ordinary assemblies. */
	public MimicProfile profile() { return sources.getFirst().profile; }
	public int cubeCount() { return sources.getFirst().cubeCount; }
	public boolean containsCube(int cubeId) { return sources.getFirst().containsCube(cubeId); }
	public boolean isSeamCut(int seamId) { return sources.getFirst().isSeamCut(seamId); }
	public BitSet presentCubes() { return sources.getFirst().presentCubes(); }
	public List<Seam> seams() { return sources.getFirst().seams; }
	public BitSet cutSeams() { return sources.getFirst().cutSeams(); }
	public List<Integer> cutOrder() { return sources.getFirst().cutOrder; }
	public Map<Integer, SurgicalCubeRotation> cubeRotations() { return sources.getFirst().cubeRotations; }
	public boolean validCubeId(int cubeId) { return sources.getFirst().validCubeId(cubeId); }

	public BitSet componentContaining(int cubeId) {
		Source source = sources.getFirst();
		return componentContaining(source.cubeCount, source.presentCubes, source.seams, source.cutSeams, cubeId);
	}

	public static boolean validCubeCount(int cubeCount) {
		return cubeCount > 0 && cubeCount <= MAX_CUBES;
	}

	public static boolean validTopology(int cubeCount, List<Seam> seams) {
		if (!validCubeCount(cubeCount) || seams == null
			|| seams.size() > Math.min(MAX_SEAMS, cubeCount * (cubeCount - 1) / 2))
			return false;
		if (cubeCount == 1)
			return seams.isEmpty();
		Set<Long> unique = new HashSet<>();
		for (Seam seam : seams) {
			if (seam == null || seam.first < 0 || seam.second >= cubeCount || seam.first >= seam.second)
				return false;
			long key = ((long) seam.first << 32) | (seam.second & 0xffffffffL);
			if (!unique.add(key))
				return false;
		}
		return true;
	}

	public static BitSet componentContaining(int cubeCount, BitSet presentCubes, List<Seam> seams,
		BitSet cutSeams, int startCube) {
		SurgicalConnectionGraph<Integer> graph = SurgicalConnectionGraph.create(
			List.of(new SurgicalConnectionGraph.Body<>(0, cubeCount, presentCubes, seams, cutSeams)), List.of());
		return graph == null ? new BitSet(cubeCount) : graph.componentContaining(0, startCube).cubes(0);
	}

	public static List<BitSet> components(int cubeCount, BitSet presentCubes, List<Seam> seams, BitSet cutSeams) {
		SurgicalConnectionGraph<Integer> graph = SurgicalConnectionGraph.create(
			List.of(new SurgicalConnectionGraph.Body<>(0, cubeCount, presentCubes, seams, cutSeams)), List.of());
		return graph == null ? List.of() : graph.components().stream()
			.map(component -> component.cubes(0)).toList();
	}

	public static int[] encodeSeams(List<Seam> seams) {
		int[] encoded = new int[seams.size() * 2];
		for (int i = 0; i < seams.size(); i++) {
			encoded[i * 2] = seams.get(i).first;
			encoded[i * 2 + 1] = seams.get(i).second;
		}
		return encoded;
	}

	@Nullable
	public static List<Seam> decodeSeams(int[] encoded) {
		if (encoded == null || (encoded.length & 1) != 0 || encoded.length > MAX_SEAMS * 2)
			return null;
		List<Seam> seams = new ArrayList<>(encoded.length / 2);
		for (int i = 0; i < encoded.length; i += 2)
			seams.add(Seam.of(encoded[i], encoded[i + 1]));
		return List.copyOf(seams);
	}

	private static BitSet normalize(BitSet input, int size) {
		BitSet normalized = input == null ? new BitSet() : (BitSet) input.clone();
		if (normalized.length() > size)
			normalized.clear(size, normalized.length());
		return normalized;
	}

	public static List<Integer> normalizeCutOrder(List<Integer> input, BitSet cutSeams, int seamCount) {
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

	private static List<Integer> decodeCutOrder(int[] encoded) {
		if (encoded == null || encoded.length > MAX_SEAMS)
			return List.of();
		List<Integer> decoded = new ArrayList<>(encoded.length);
		for (int seamId : encoded)
			decoded.add(seamId);
		return List.copyOf(decoded);
	}

	/** Immutable physical arm dimensions baked once from the assembled model's rest geometry. */
	public record AttackGeometry(@Nullable ArmAttackGeometry right,
		@Nullable ArmAttackGeometry left) {
		private static final double MAX_COORDINATE = MAX_BODY_SIZE * 2.0d;

		public AttackGeometry {
			if (right == null && left == null)
				throw new IllegalArgumentException("Attack geometry requires at least one arm");
		}

		@Nullable
		public static AttackGeometry create(@Nullable ArmAttackGeometry right,
			@Nullable ArmAttackGeometry left) {
			try {
				return new AttackGeometry(right, left);
			} catch (IllegalArgumentException ignored) {
				return null;
			}
		}

		@Nullable
		public ArmAttackGeometry arm(boolean leftSide) {
			ArmAttackGeometry preferred = leftSide ? left : right;
			return preferred != null ? preferred : leftSide ? right : left;
		}

		private CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			if (right != null)
				tag.put(RIGHT_ARM_TAG, right.save());
			if (left != null)
				tag.put(LEFT_ARM_TAG, left.save());
			return tag;
		}

		public void write(FriendlyByteBuf buffer) {
			buffer.writeBoolean(right != null);
			if (right != null)
				right.write(buffer);
			buffer.writeBoolean(left != null);
			if (left != null)
				left.write(buffer);
		}

		@Nullable
		public static AttackGeometry read(FriendlyByteBuf buffer) {
			boolean hasRight = buffer.readBoolean();
			ArmAttackGeometry right = hasRight ? ArmAttackGeometry.read(buffer) : null;
			boolean hasLeft = buffer.readBoolean();
			ArmAttackGeometry left = hasLeft ? ArmAttackGeometry.read(buffer) : null;
			if (hasRight && right == null || hasLeft && left == null)
				return null;
			return create(right, left);
		}

		@Nullable
		private static AttackGeometry load(CompoundTag tag) {
			ArmAttackGeometry right = tag.contains(RIGHT_ARM_TAG, Tag.TAG_COMPOUND)
				? ArmAttackGeometry.load(tag.getCompound(RIGHT_ARM_TAG)) : null;
			ArmAttackGeometry left = tag.contains(LEFT_ARM_TAG, Tag.TAG_COMPOUND)
				? ArmAttackGeometry.load(tag.getCompound(LEFT_ARM_TAG)) : null;
			if (tag.contains(RIGHT_ARM_TAG, Tag.TAG_COMPOUND) && right == null
				|| tag.contains(LEFT_ARM_TAG, Tag.TAG_COMPOUND) && left == null)
				return null;
			return create(right, left);
		}

		private static boolean validPoint(Vec3 point) {
			return point != null && Double.isFinite(point.x) && Double.isFinite(point.y)
				&& Double.isFinite(point.z) && Math.abs(point.x) <= MAX_COORDINATE
				&& Math.abs(point.y) <= MAX_COORDINATE && Math.abs(point.z) <= MAX_COORDINATE;
		}
	}

	public record ArmAttackGeometry(Vec3 origin, float reach, float minimumY, float maximumY,
		float radius, float volume) {
		private static final float MIN_RADIUS = 0.05f;
		private static final float MAX_RADIUS = 8.0f;
		private static final float MAX_VOLUME = (float) (MAX_BODY_SIZE * MAX_BODY_SIZE * MAX_BODY_SIZE);
		private static final int LEGACY_PATH_SAMPLES = 16;

		public ArmAttackGeometry {
			if (!AttackGeometry.validPoint(origin) || !Float.isFinite(reach)
				|| reach < MIN_RADIUS || reach > AttackGeometry.MAX_COORDINATE
				|| !Float.isFinite(minimumY) || !Float.isFinite(maximumY)
				|| minimumY > maximumY || Math.abs(minimumY) > AttackGeometry.MAX_COORDINATE
				|| Math.abs(maximumY) > AttackGeometry.MAX_COORDINATE
				|| !Float.isFinite(radius) || radius < MIN_RADIUS || radius > MAX_RADIUS
				|| !Float.isFinite(volume) || volume < 0.0f || volume > MAX_VOLUME)
				throw new IllegalArgumentException("Invalid arm attack geometry");
		}

		@Nullable
		public static ArmAttackGeometry create(Vec3 origin, float reach, float minimumY,
			float maximumY, float radius, float volume) {
			try {
				return new ArmAttackGeometry(origin, reach, minimumY, maximumY, radius, volume);
			} catch (IllegalArgumentException ignored) {
				return null;
			}
		}

		/** Approximates saves predating explicit arm volume as a square shaft ending at the baked tip. */
		@Nullable
		private static ArmAttackGeometry createLegacy(Vec3 origin, float reach, float minimumY,
			float maximumY, float radius) {
			float length = Math.max(0.0f, reach - radius);
			float estimatedVolume = Math.min(MAX_VOLUME, 2.0f * radius * radius * length);
			return create(origin, reach, minimumY, maximumY, radius, estimatedVolume);
		}

		private CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putFloat(ATTACK_ORIGIN_X_TAG, (float) origin.x);
			tag.putFloat(ATTACK_ORIGIN_Y_TAG, (float) origin.y);
			tag.putFloat(ATTACK_ORIGIN_Z_TAG, (float) origin.z);
			tag.putFloat(ATTACK_REACH_TAG, reach);
			tag.putFloat(ATTACK_MINIMUM_Y_TAG, minimumY);
			tag.putFloat(ATTACK_MAXIMUM_Y_TAG, maximumY);
			tag.putFloat(ATTACK_RADIUS_TAG, radius);
			tag.putFloat(ATTACK_VOLUME_TAG, volume);
			return tag;
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeFloat((float) origin.x);
			buffer.writeFloat((float) origin.y);
			buffer.writeFloat((float) origin.z);
			buffer.writeFloat(reach);
			buffer.writeFloat(minimumY);
			buffer.writeFloat(maximumY);
			buffer.writeFloat(radius);
			buffer.writeFloat(volume);
		}

		@Nullable
		private static ArmAttackGeometry read(FriendlyByteBuf buffer) {
			Vec3 origin = new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
			return create(origin, buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
				buffer.readFloat(), buffer.readFloat());
		}

		@Nullable
		private static ArmAttackGeometry load(CompoundTag tag) {
			if (!tag.contains(ATTACK_RADIUS_TAG, Tag.TAG_ANY_NUMERIC))
				return null;
			if (tag.contains(ATTACK_ORIGIN_X_TAG, Tag.TAG_ANY_NUMERIC)
				&& tag.contains(ATTACK_ORIGIN_Y_TAG, Tag.TAG_ANY_NUMERIC)
				&& tag.contains(ATTACK_ORIGIN_Z_TAG, Tag.TAG_ANY_NUMERIC)
				&& tag.contains(ATTACK_REACH_TAG, Tag.TAG_ANY_NUMERIC)
				&& tag.contains(ATTACK_MINIMUM_Y_TAG, Tag.TAG_ANY_NUMERIC)
				&& tag.contains(ATTACK_MAXIMUM_Y_TAG, Tag.TAG_ANY_NUMERIC))
				return tag.contains(ATTACK_VOLUME_TAG, Tag.TAG_ANY_NUMERIC)
					? create(new Vec3(tag.getFloat(ATTACK_ORIGIN_X_TAG),
					tag.getFloat(ATTACK_ORIGIN_Y_TAG), tag.getFloat(ATTACK_ORIGIN_Z_TAG)),
					tag.getFloat(ATTACK_REACH_TAG), tag.getFloat(ATTACK_MINIMUM_Y_TAG),
					tag.getFloat(ATTACK_MAXIMUM_Y_TAG), tag.getFloat(ATTACK_RADIUS_TAG),
					tag.getFloat(ATTACK_VOLUME_TAG))
					: createLegacy(new Vec3(tag.getFloat(ATTACK_ORIGIN_X_TAG),
						tag.getFloat(ATTACK_ORIGIN_Y_TAG), tag.getFloat(ATTACK_ORIGIN_Z_TAG)),
						tag.getFloat(ATTACK_REACH_TAG), tag.getFloat(ATTACK_MINIMUM_Y_TAG),
						tag.getFloat(ATTACK_MAXIMUM_Y_TAG), tag.getFloat(ATTACK_RADIUS_TAG));
			return loadLegacy(tag);
		}

		@Nullable
		private static ArmAttackGeometry loadLegacy(CompoundTag tag) {
			if (!tag.contains(EMPTY_HAND_PATH_TAG, Tag.TAG_INT_ARRAY)
				|| !tag.contains(WEAPON_PATH_TAG, Tag.TAG_INT_ARRAY))
				return null;
			int[] emptyHand = tag.getIntArray(EMPTY_HAND_PATH_TAG);
			int[] weapon = tag.getIntArray(WEAPON_PATH_TAG);
			if (emptyHand.length != LEGACY_PATH_SAMPLES * 3
				|| weapon.length != LEGACY_PATH_SAMPLES * 3)
				return null;
			float radius = tag.getFloat(ATTACK_RADIUS_TAG);
			float reach = 0.0f;
			float minimumY = Float.POSITIVE_INFINITY;
			float maximumY = Float.NEGATIVE_INFINITY;
			for (int[] path : List.of(emptyHand, weapon))
				for (int index = 0; index < LEGACY_PATH_SAMPLES; index++) {
					Vec3 point = new Vec3(Float.intBitsToFloat(path[index * 3]),
						Float.intBitsToFloat(path[index * 3 + 1]),
						Float.intBitsToFloat(path[index * 3 + 2]));
					if (!AttackGeometry.validPoint(point))
						return null;
					reach = Math.max(reach,
						(float) Math.sqrt(point.x * point.x + point.z * point.z) + radius);
					minimumY = Math.min(minimumY, (float) point.y - radius);
					maximumY = Math.max(maximumY, (float) point.y + radius);
				}
			return createLegacy(Vec3.ZERO, reach, minimumY, maximumY, radius);
		}
	}

	/** Volume-weighted upright collision core, visible offset and measured effective leg length. */
	public record BodyBounds(float width, float height, float depth,
		float centerX, float minY, float centerZ, float legLength) {
		public BodyBounds {
			if (!validSize(width) || !validSize(height) || !validSize(depth)
				|| !validOffset(centerX) || !validVerticalOffset(minY) || !validOffset(centerZ)
				|| !validLegLength(legLength))
				throw new IllegalArgumentException("Invalid surgical body bounds");
		}

		@Nullable
		public static BodyBounds create(double width, double height, double depth) {
			return create(width, height, depth, 0.0d, 0.0d, 0.0d);
		}

		@Nullable
		public static BodyBounds create(double width, double height, double depth,
			double centerX, double minY, double centerZ) {
			return create(width, height, depth, centerX, minY, centerZ, 0.0d);
		}

		@Nullable
		public static BodyBounds create(double width, double height, double depth,
			double centerX, double minY, double centerZ, double legLength) {
			if (!validSize(width) || !validSize(height) || !validSize(depth))
				return null;
			if (!validOffset(centerX) || !validVerticalOffset(minY) || !validOffset(centerZ)
				|| !validLegLength(legLength))
				return null;
			return new BodyBounds((float) width, (float) height, (float) depth,
				(float) centerX, (float) minY, (float) centerZ, (float) legLength);
		}

		public BodyBounds withLegLength(double measuredLegLength) {
			if (!validLegLength(measuredLegLength))
				throw new IllegalArgumentException("Invalid surgical leg length");
			return new BodyBounds(width, height, depth, centerX, minY, centerZ,
				(float) measuredLegLength);
		}

		private static boolean validSize(double value) {
			return Double.isFinite(value) && value >= MIN_BODY_SIZE && value <= MAX_BODY_SIZE;
		}

		private static boolean validOffset(double value) {
			return Double.isFinite(value) && Math.abs(value) <= MAX_BODY_SIZE;
		}

		private static boolean validVerticalOffset(double value) {
			return Double.isFinite(value) && value >= 0.0d && value <= MAX_BODY_SIZE;
		}

		private static boolean validLegLength(double value) {
			return value == 0.0d || validSize(value);
		}
	}

	public static final class Source {
		private final MimicProfile profile;
		private final int cubeCount;
		private final BitSet presentCubes;
		private final List<Seam> seams;
		private final BitSet cutSeams;
		private final List<Integer> cutOrder;
		private final Direction facing;
		private final SurgicalLayPose layPose;
		private final Vec3 originOffset;
		private final Map<Integer, Vec3> cubeOffsets;
		private final Map<Integer, SurgicalCubeRotation> cubeRotations;

		private Source(MimicProfile profile, int cubeCount, BitSet presentCubes, List<Seam> seams,
			BitSet cutSeams, List<Integer> cutOrder, Direction facing, SurgicalLayPose layPose,
			Vec3 originOffset,
			Map<Integer, Vec3> cubeOffsets, Map<Integer, SurgicalCubeRotation> cubeRotations) {
			this.profile = profile;
			this.cubeCount = cubeCount;
			this.presentCubes = normalize(presentCubes, cubeCount);
			this.seams = List.copyOf(seams);
			this.cutSeams = normalize(cutSeams, seams.size());
			this.cutOrder = normalizeCutOrder(cutOrder, this.cutSeams, seams.size());
			this.facing = facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
			this.layPose = layPose == null ? SurgicalLayPose.IDENTITY : layPose;
			this.originOffset = originOffset;
			this.cubeOffsets = Map.copyOf(cubeOffsets);
			this.cubeRotations = Map.copyOf(cubeRotations);
		}

		@Nullable
		public static Source create(MimicProfile profile, int cubeCount, BitSet presentCubes,
			List<Seam> seams, BitSet cutSeams, List<Integer> cutOrder, Direction facing,
			SurgicalLayPose layPose, Vec3 originOffset, Map<Integer, Vec3> cubeOffsets) {
			return create(profile, cubeCount, presentCubes, seams, cutSeams, cutOrder, facing, layPose,
				originOffset, cubeOffsets, Map.of());
		}

		@Nullable
		public static Source create(MimicProfile profile, int cubeCount, BitSet presentCubes,
			List<Seam> seams, BitSet cutSeams, List<Integer> cutOrder, Direction facing,
			SurgicalLayPose layPose, Vec3 originOffset, Map<Integer, Vec3> cubeOffsets,
			Map<Integer, SurgicalCubeRotation> cubeRotations) {
			if (profile == null || presentCubes == null || seams == null || cutSeams == null
				|| cubeOffsets == null || cubeOffsets.size() > cubeCount || cubeRotations == null
				|| cubeRotations.size() > cubeCount)
				return null;
			for (Map.Entry<Integer, Vec3> entry : cubeOffsets.entrySet()) {
				Integer cube = entry.getKey();
				if (cube == null || cube < 0 || cube >= cubeCount || !presentCubes.get(cube)
					|| !finiteVector(entry.getValue()))
					return null;
			}
			for (Map.Entry<Integer, SurgicalCubeRotation> entry : cubeRotations.entrySet()) {
				Integer cube = entry.getKey();
				if (cube == null || cube < 0 || cube >= cubeCount || !presentCubes.get(cube)
					|| entry.getValue() == null)
					return null;
			}
			Map<Integer, Vec3> sanitized = sanitizeOffsets(cubeOffsets, cubeCount, presentCubes);
			Map<Integer, SurgicalCubeRotation> sanitizedRotations = sanitizeRotations(cubeRotations,
				cubeCount, presentCubes);
			Source source = new Source(profile, cubeCount, presentCubes, seams, cutSeams, cutOrder, facing,
				layPose,
				originOffset == null ? Vec3.ZERO : originOffset, sanitized, sanitizedRotations);
			return source.valid() ? source : null;
		}

		private boolean valid() {
			if (profile == null || !validTopology(cubeCount, seams) || presentCubes.isEmpty()
				|| layPose == null || !layPose.valid() || !finiteVector(originOffset))
				return false;
			for (Map.Entry<Integer, Vec3> entry : cubeOffsets.entrySet())
				if (entry.getKey() == null || !containsCube(entry.getKey()) || !finiteVector(entry.getValue()))
					return false;
			for (Map.Entry<Integer, SurgicalCubeRotation> entry : cubeRotations.entrySet())
				if (entry.getKey() == null || !containsCube(entry.getKey()) || entry.getValue() == null)
					return false;
			return true;
		}

		private Source copy() {
			return new Source(profile, cubeCount, presentCubes, seams, cutSeams, cutOrder, facing, layPose,
				originOffset, cubeOffsets, cubeRotations);
		}

		public MimicProfile profile() { return profile; }
		public int cubeCount() { return cubeCount; }
		public BitSet presentCubes() { return (BitSet) presentCubes.clone(); }
		public List<Seam> seams() { return seams; }
		public BitSet cutSeams() { return (BitSet) cutSeams.clone(); }
		public List<Integer> cutOrder() { return cutOrder; }
		public Direction facing() { return facing; }
		public SurgicalLayPose layPose() { return layPose; }
		public Vec3 originOffset() { return originOffset; }
		public Map<Integer, Vec3> cubeOffsets() { return cubeOffsets; }
		public Map<Integer, SurgicalCubeRotation> cubeRotations() { return cubeRotations; }
		public boolean containsCube(int cubeId) { return validCubeId(cubeId) && presentCubes.get(cubeId); }
		public boolean validCubeId(int cubeId) { return cubeId >= 0 && cubeId < cubeCount; }
		public boolean isSeamCut(int seamId) {
			return seamId >= 0 && seamId < seams.size() && cutSeams.get(seamId);
		}

		private CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.put(PROFILE_TAG, profile.save());
			tag.putInt(CUBE_COUNT_TAG, cubeCount);
			tag.putLongArray(PRESENT_CUBES_TAG, presentCubes.toLongArray());
			tag.putIntArray(SEAMS_TAG, encodeSeams(seams));
			if (!cutSeams.isEmpty())
				tag.putLongArray(CUT_SEAMS_TAG, cutSeams.toLongArray());
			if (!cutOrder.isEmpty())
				tag.putIntArray(CUT_ORDER_TAG, cutOrder.stream().mapToInt(Integer::intValue).toArray());
			tag.putInt(FACING_TAG, facing.get3DDataValue());
			tag.put(LAY_POSE_TAG, writeLayPose(layPose));
			if (!originOffset.equals(Vec3.ZERO)) {
				tag.putDouble(ORIGIN_X_TAG, originOffset.x);
				tag.putDouble(ORIGIN_Y_TAG, originOffset.y);
				tag.putDouble(ORIGIN_Z_TAG, originOffset.z);
			}
			writeOffsets(tag, cubeOffsets);
			writeRotations(tag, cubeRotations);
			return tag;
		}

		@Nullable
		private static Source load(CompoundTag tag) {
			if (!tag.contains(PROFILE_TAG, Tag.TAG_COMPOUND)
				|| !tag.contains(CUBE_COUNT_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(PRESENT_CUBES_TAG, Tag.TAG_LONG_ARRAY)
				|| !tag.contains(SEAMS_TAG, Tag.TAG_INT_ARRAY))
				return null;
			MimicProfile profile = MimicProfile.load(tag.getCompound(PROFILE_TAG));
			int cubeCount = tag.getInt(CUBE_COUNT_TAG);
			List<Seam> seams = decodeSeams(tag.getIntArray(SEAMS_TAG));
			if (profile == null || seams == null)
				return null;
			BitSet present = BitSet.valueOf(tag.getLongArray(PRESENT_CUBES_TAG));
			BitSet cuts = tag.contains(CUT_SEAMS_TAG, Tag.TAG_LONG_ARRAY)
				? BitSet.valueOf(tag.getLongArray(CUT_SEAMS_TAG)) : new BitSet();
			List<Integer> order = tag.contains(CUT_ORDER_TAG, Tag.TAG_INT_ARRAY)
				? decodeCutOrder(tag.getIntArray(CUT_ORDER_TAG)) : List.of();
			Direction facing = Direction.from3DDataValue(tag.getInt(FACING_TAG));
			SurgicalLayPose layPose = tag.contains(LAY_POSE_TAG, Tag.TAG_COMPOUND)
				? readLayPose(tag.getCompound(LAY_POSE_TAG)) : SurgicalLayPose.IDENTITY;
			Vec3 origin = new Vec3(tag.getDouble(ORIGIN_X_TAG), tag.getDouble(ORIGIN_Y_TAG),
				tag.getDouble(ORIGIN_Z_TAG));
			return create(profile, cubeCount, present, seams, cuts, order, facing, layPose, origin,
				readOffsets(tag, cubeCount, present), readRotations(tag, cubeCount, present));
		}
	}

	/** One immutable source after rotating the packed layout to the placing player's direction. */
	public record PlacedSource(Source source, Direction facing, SurgicalLayPose layPose, Vec3 originOffset,
		Map<Integer, Vec3> cubeOffsets, Map<Integer, SurgicalCubeRotation> cubeRotations) {
		public PlacedSource {
			if (source == null)
				throw new IllegalArgumentException("A placed surgical source requires source data");
			facing = horizontal(facing);
			layPose = layPose == null ? SurgicalLayPose.IDENTITY : layPose;
			originOffset = originOffset == null ? Vec3.ZERO : originOffset;
			cubeOffsets = Map.copyOf(cubeOffsets);
			cubeRotations = Map.copyOf(cubeRotations);
		}
	}

	public record Joint(int firstSource, int firstCube, int secondSource, int secondCube,
		@Nullable JointReplay replay) {
		public Joint(int firstSource, int firstCube, int secondSource, int secondCube) {
			this(firstSource, firstCube, secondSource, secondCube, null);
		}

		private Joint normalized() {
			return firstSource < secondSource || firstSource == secondSource && firstCube <= secondCube
				? this : new Joint(secondSource, secondCube, firstSource, firstCube, replay);
		}

		private boolean validFor(List<Source> sources) {
			boolean endpointsValid = firstSource >= 0 && firstSource < sources.size()
				&& secondSource >= 0 && secondSource < sources.size()
				&& sources.get(firstSource).containsCube(firstCube)
				&& sources.get(secondSource).containsCube(secondCube)
				&& (firstSource != secondSource || firstCube != secondCube);
			return endpointsValid && (replay == null || replay.matches(firstSource, firstCube)
				|| replay.matches(secondSource, secondCube));
		}

		private Joint rotateClockwise(int turns) {
			return replay == null ? this : new Joint(firstSource, firstCube, secondSource, secondCube,
				new JointReplay(replay.movingSource, replay.movingCube,
					replay.transform.rotateClockwise(turns), replay.anchorContact));
		}
	}

	public record JointReplay(int movingSource, int movingCube, SurgicalGlueTransform transform,
		SurgicalGlueContact anchorContact) {
		public JointReplay {
			if (movingSource < 0 || movingCube < 0 || transform == null || anchorContact == null)
				throw new IllegalArgumentException("Invalid packed surgical glue replay");
		}

		private boolean matches(int source, int cube) {
			return movingSource == source && movingCube == cube;
		}
	}

	public record Combination(UUID id, List<CombinationMember> members) {
		public Combination {
			members = members == null ? List.of() : List.copyOf(members);
		}

		@Nullable
		private Combination normalized() {
			if (id == null || members.size() < 2 || members.size() > MAX_CUBES)
				return null;
			Set<CombinationMember> unique = new HashSet<>(members);
			if (unique.size() != members.size())
				return null;
			List<CombinationMember> normalized = new ArrayList<>(unique);
			normalized.sort(Comparator.comparingInt(CombinationMember::source)
				.thenComparingInt(CombinationMember::cube));
			return new Combination(id, normalized);
		}

		private boolean validFor(List<Source> sources) {
			for (CombinationMember member : members)
				if (member.source < 0 || member.source >= sources.size()
					|| !sources.get(member.source).containsCube(member.cube))
					return false;
			return true;
		}
	}

	public record CombinationMember(int source, int cube) {}

	/** One anatomical joint inside a packed body: {@code child} rotates around {@code parent}. */
	public record Limb(SurgicalLimbType type, int childSource, int childCube, int parentSource,
		int parentCube) {
		private boolean validFor(List<Source> sources) {
			return childSource >= 0 && childSource < sources.size()
				&& parentSource >= 0 && parentSource < sources.size()
				&& sources.get(childSource).containsCube(childCube)
				&& sources.get(parentSource).containsCube(parentCube)
				&& (childSource != parentSource || childCube != parentCube);
		}
	}

	public record Seam(int first, int second) {
		public static Seam of(int first, int second) {
			return first <= second ? new Seam(first, second) : new Seam(second, first);
		}
	}

	private static boolean finiteVector(Vec3 value) {
		return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)
			&& Math.abs(value.x) <= SurgicalTablePlane.MAX_TILES + 2.0d
			&& Math.abs(value.y) <= SurgicalTablePlane.MAX_TILES + 2.0d
			&& Math.abs(value.z) <= SurgicalTablePlane.MAX_TILES + 2.0d;
	}

	private static CompoundTag writeLayPose(SurgicalLayPose pose) {
		CompoundTag tag = new CompoundTag();
		tag.putInt(POSE_AXIS_TAG, pose.axis().ordinal());
		tag.putInt(POSE_YAW_TAG, pose.yaw());
		tag.putDouble(POSE_X_TAG, pose.translation().x);
		tag.putDouble(POSE_Y_TAG, pose.translation().y);
		tag.putDouble(POSE_Z_TAG, pose.translation().z);
		return tag;
	}

	private static SurgicalLayPose readLayPose(CompoundTag tag) {
		int axis = tag.getInt(POSE_AXIS_TAG);
		if (axis < 0 || axis >= SurgicalLayPose.RotationAxis.values().length)
			return SurgicalLayPose.IDENTITY;
		try {
			return new SurgicalLayPose(SurgicalLayPose.RotationAxis.values()[axis], tag.getInt(POSE_YAW_TAG),
				new Vec3(tag.getDouble(POSE_X_TAG), tag.getDouble(POSE_Y_TAG), tag.getDouble(POSE_Z_TAG)));
		} catch (IllegalArgumentException ignored) {
			return SurgicalLayPose.IDENTITY;
		}
	}

	private static Direction inferLayoutFacing(List<Source> sources) {
		if (sources == null || sources.isEmpty())
			return Direction.NORTH;
		Source nearestOrigin = null;
		double nearestDistance = Double.POSITIVE_INFINITY;
		for (Source candidate : sources) {
			if (candidate == null)
				continue;
			double distance = horizontalDistanceSqr(candidate.originOffset);
			if (distance < nearestDistance) {
				nearestOrigin = candidate;
				nearestDistance = distance;
			}
		}
		return nearestOrigin == null ? Direction.NORTH : horizontal(nearestOrigin.facing);
	}

	private static SurgicalLayPose inferLayoutLayPose(List<Source> sources) {
		if (sources == null || sources.isEmpty())
			return SurgicalLayPose.IDENTITY;
		Source nearestOrigin = null;
		double nearestDistance = Double.POSITIVE_INFINITY;
		for (Source candidate : sources) {
			if (candidate == null)
				continue;
			double distance = horizontalDistanceSqr(candidate.originOffset);
			if (distance < nearestDistance) {
				nearestOrigin = candidate;
				nearestDistance = distance;
			}
		}
		return nearestOrigin == null ? SurgicalLayPose.IDENTITY : nearestOrigin.layPose;
	}

	private static double horizontalDistanceSqr(Vec3 vector) {
		return vector == null ? Double.POSITIVE_INFINITY : vector.x * vector.x + vector.z * vector.z;
	}

	private static int clockwiseTurns(Direction from, Direction to) {
		Direction rotated = horizontal(from);
		Direction target = horizontal(to);
		for (int turns = 0; turns < 4; turns++) {
			if (rotated == target)
				return turns;
			rotated = rotated.getClockWise();
		}
		return 0;
	}

	private static Direction rotateClockwise(Direction direction, int turns) {
		Direction rotated = horizontal(direction);
		for (int step = 0; step < turns; step++)
			rotated = rotated.getClockWise();
		return rotated;
	}

	private static Vec3 rotateClockwise(Vec3 vector, int turns) {
		Vec3 rotated = vector;
		for (int step = 0; step < turns; step++)
			rotated = new Vec3(-rotated.z, rotated.y, rotated.x);
		return rotated;
	}

	private static Direction horizontal(Direction direction) {
		return direction != null && direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
	}

	private static Map<Integer, Vec3> sanitizeOffsets(Map<Integer, Vec3> offsets, int cubeCount,
		BitSet presentCubes) {
		if (offsets == null || offsets.size() > cubeCount)
			return Map.of();
		Map<Integer, Vec3> sanitized = new HashMap<>();
		for (Map.Entry<Integer, Vec3> entry : offsets.entrySet()) {
			Integer cube = entry.getKey();
			if (cube == null || cube < 0 || cube >= cubeCount || !presentCubes.get(cube)
				|| !finiteVector(entry.getValue()))
				return Map.of();
			if (!entry.getValue().equals(Vec3.ZERO))
				sanitized.put(cube, entry.getValue());
		}
		return Map.copyOf(sanitized);
	}

	private static Map<Integer, SurgicalCubeRotation> sanitizeRotations(
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

	private static void writeOffsets(CompoundTag tag, Map<Integer, Vec3> offsets) {
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
		tag.putIntArray(OFFSET_CUBES_TAG, cubes);
		tag.putLongArray(OFFSET_X_TAG, x);
		tag.putLongArray(OFFSET_Y_TAG, y);
		tag.putLongArray(OFFSET_Z_TAG, z);
	}

	private static Map<Integer, Vec3> readOffsets(CompoundTag tag, int cubeCount, BitSet present) {
		if (!tag.contains(OFFSET_CUBES_TAG, Tag.TAG_INT_ARRAY)
			|| !tag.contains(OFFSET_X_TAG, Tag.TAG_LONG_ARRAY)
			|| !tag.contains(OFFSET_Z_TAG, Tag.TAG_LONG_ARRAY))
			return Map.of();
		int[] cubes = tag.getIntArray(OFFSET_CUBES_TAG);
		long[] x = tag.getLongArray(OFFSET_X_TAG);
		long[] z = tag.getLongArray(OFFSET_Z_TAG);
		long[] y = tag.contains(OFFSET_Y_TAG, Tag.TAG_LONG_ARRAY)
			? tag.getLongArray(OFFSET_Y_TAG) : new long[cubes.length];
		if (cubes.length != x.length || cubes.length != y.length || cubes.length != z.length
			|| cubes.length > cubeCount)
			return Map.of();
		Map<Integer, Vec3> offsets = new HashMap<>();
		for (int index = 0; index < cubes.length; index++) {
			Vec3 offset = new Vec3(Double.longBitsToDouble(x[index]), Double.longBitsToDouble(y[index]),
				Double.longBitsToDouble(z[index]));
			if (cubes[index] < 0 || cubes[index] >= cubeCount || !present.get(cubes[index])
				|| !finiteVector(offset) || offsets.put(cubes[index], offset) != null)
				return Map.of();
		}
		return Map.copyOf(offsets);
	}

	private static void writeRotations(CompoundTag tag, Map<Integer, SurgicalCubeRotation> rotations) {
		if (rotations.isEmpty())
			return;
		ListTag encoded = new ListTag();
		for (Map.Entry<Integer, SurgicalCubeRotation> entry : rotations.entrySet().stream()
			.sorted(Map.Entry.comparingByKey()).toList()) {
			CompoundTag value = new CompoundTag();
			value.putInt(ROTATION_CUBE_TAG, entry.getKey());
			value.put(ROTATION_VALUE_TAG, entry.getValue().save());
			encoded.add(value);
		}
		tag.put(ROTATIONS_TAG, encoded);
	}

	private static Map<Integer, SurgicalCubeRotation> readRotations(CompoundTag tag, int cubeCount,
		BitSet present) {
		if (!tag.contains(ROTATIONS_TAG, Tag.TAG_LIST))
			return Map.of();
		ListTag encoded = tag.getList(ROTATIONS_TAG, Tag.TAG_COMPOUND);
		if (encoded.size() > cubeCount)
			return Map.of();
		Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>();
		for (int index = 0; index < encoded.size(); index++) {
			CompoundTag value = encoded.getCompound(index);
			int cube = value.getInt(ROTATION_CUBE_TAG);
			SurgicalCubeRotation rotation = value.contains(ROTATION_VALUE_TAG, Tag.TAG_COMPOUND)
				? SurgicalCubeRotation.load(value.getCompound(ROTATION_VALUE_TAG)) : null;
			if (cube < 0 || cube >= cubeCount || !present.get(cube) || rotation == null
				|| rotations.putIfAbsent(cube, rotation) != null)
				return Map.of();
		}
		return sanitizeRotations(rotations, cubeCount, present);
	}
}
