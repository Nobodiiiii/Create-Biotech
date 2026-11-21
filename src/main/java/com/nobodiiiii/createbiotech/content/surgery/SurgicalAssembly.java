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
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

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
	// Assemblies contain no entity/level state. Share the immutable decode across box
	// previews, icons and world entities, while bounding retained NBT and topology.
	private static final Cache<CompoundTag, SurgicalAssembly> DECODED = CacheBuilder.newBuilder()
		.concurrencyLevel(1).maximumSize(64).build();
	public static final int MAX_CUBES = 1024;
	public static final int MAX_SEAMS = 4096;
	public static final int MAX_SOURCES = 256;
	/** Geometry capacity also accommodates existing bodies with up to three heads. */
	public static final int MAX_HITBOX_LIMBS = 19;
	public static final double MAX_BODY_SIZE = 64.0d;
	public static final double MIN_BODY_SIZE = 1.0d / 64.0d;
	/**
	 * Rounding slack for spans padded to exactly {@link #MIN_BODY_SIZE}. Zero-thickness sheets are
	 * widened around an arbitrary centre and then re-expressed relative to the body origin, so the
	 * padded span can land a few ulps (or one float ulp) below the minimum.
	 */
	private static final double MIN_BODY_SIZE_TOLERANCE = 1.0e-4d;
	static final int CURRENT_VERSION = 4;
	static final String VERSION_TAG = "Version";
	private static final String PRESENT_CUBES_TAG = "PresentCubes";
	private static final String DONOR_ID_TAG = "DonorId";
	private static final String CUT_SEAMS_TAG = "CutSeams";
	private static final String CUT_ORDER_TAG = "CutOrder";
	static final String SOURCES_TAG = "Sources";
	static final String JOINTS_TAG = "GlueJoints";
	static final String COMBINATIONS_TAG = "Combinations";
	static final String COMBINATION_ID_TAG = "Id";
	static final String COMBINATION_MEMBERS_TAG = "Members";
	static final String MEMBER_SOURCE_TAG = "Source";
	static final String MEMBER_CUBE_TAG = "Cube";
	static final String LIMBS_TAG = "Limbs";
	static final String LIMB_TYPE_TAG = "Type";
	static final String LIMB_CHILD_SOURCE_TAG = "ChildSource";
	static final String LIMB_CHILD_CUBE_TAG = "ChildCube";
	static final String LIMB_PARENT_SOURCE_TAG = "ParentSource";
	static final String LIMB_PARENT_CUBE_TAG = "ParentCube";
	static final String PRESERVE_LAYOUT_TAG = "PreserveLayout";
	static final String LAYOUT_FACING_TAG = "LayoutFacing";
	static final String LAYOUT_LAY_POSE_TAG = "LayoutLayPose";
	static final String BODY_WIDTH_TAG = "BodyWidth";
	static final String BODY_HEIGHT_TAG = "BodyHeight";
	static final String BODY_DEPTH_TAG = "BodyDepth";
	static final String BODY_CENTER_X_TAG = "BodyCenterX";
	static final String BODY_MIN_Y_TAG = "BodyMinY";
	static final String BODY_CENTER_Z_TAG = "BodyCenterZ";
	static final String BODY_EYE_HEIGHT_TAG = "BodyEyeHeight";
	static final String BODY_LEG_LENGTH_TAG = "BodyLegLength";
	static final String BODY_GROUNDED_LEGS_TAG = "BodyGroundedLegCount";
	static final String BODY_GROUNDED_KNEES_TAG = "BodyGroundedKneeCount";
	static final String BODY_LEG_VOLUME_RATIO_TAG = "BodyLegVolumeRatio";
	static final String BODY_VOLUME_TAG = "BodyVolume";
	static final String HITBOX_GEOMETRY_TAG = "HitboxGeometry";
	static final String ATTACK_GEOMETRY_TAG = "AttackGeometry";
	static final String BLOCK_ATTACHMENT_CARRIER_TAG = "BlockAttachmentCarrier";
	private static final String RIGHT_ARMS_TAG = "RightArms";
	private static final String LEFT_ARMS_TAG = "LeftArms";
	private static final String ATTACK_RADIUS_TAG = "Radius";
	private static final String ATTACK_VOLUME_TAG = "Volume";
	private static final String ATTACK_ORIGIN_X_TAG = "OriginX";
	private static final String ATTACK_ORIGIN_Y_TAG = "OriginY";
	private static final String ATTACK_ORIGIN_Z_TAG = "OriginZ";
	private static final String ATTACK_REACH_TAG = "Reach";
	private static final String ATTACK_MINIMUM_Y_TAG = "MinimumY";
	private static final String ATTACK_MAXIMUM_Y_TAG = "MaximumY";
	private static final String ATTACK_REST_DIRECTION_TAG = "RestDirection";
	private static final String ATTACK_HAS_ELBOW_TAG = "HasElbow";
	private static final String FACING_TAG = "Facing";
	private static final String LAY_POSE_TAG = "LayPose";
	static final String POSE_AXIS_TAG = "Axis";
	static final String POSE_YAW_TAG = "Yaw";
	static final String POSE_X_TAG = "TranslateX";
	static final String POSE_Y_TAG = "TranslateY";
	static final String POSE_Z_TAG = "TranslateZ";
	private static final String ORIGIN_X_TAG = "OriginX";
	private static final String ORIGIN_Y_TAG = "OriginY";
	private static final String ORIGIN_Z_TAG = "OriginZ";
	static final String OFFSET_CUBES_TAG = "OffsetCubes";
	static final String OFFSET_X_TAG = "OffsetX";
	static final String OFFSET_Y_TAG = "OffsetY";
	static final String OFFSET_Z_TAG = "OffsetZ";
	static final String ROTATIONS_TAG = "CubeRotations";
	static final String ROTATION_CUBE_TAG = "Cube";
	static final String ROTATION_VALUE_TAG = "Rotation";
	static final String FIRST_SOURCE_TAG = "FirstSource";
	static final String FIRST_CUBE_TAG = "FirstCube";
	static final String SECOND_SOURCE_TAG = "SecondSource";
	static final String SECOND_CUBE_TAG = "SecondCube";
	static final String JOINT_REPLAY_TAG = "GlueReplay";
	static final String MOVING_SOURCE_TAG = "MovingSource";
	static final String MOVING_CUBE_TAG = "MovingCube";
	static final String GLUE_TRANSFORM_TAG = "Transform";
	static final String ANCHOR_CONTACT_TAG = "AnchorContact";

	final List<Source> sources;
	final List<Joint> joints;
	final List<Combination> combinations;
	final List<Limb> limbs;
	@Nullable
	transient LimbTopology limbTopology;
	final boolean preserveLayout;
	final Direction layoutFacing;
	final SurgicalLayPose layoutLayPose;
	@Nullable
	final BodyBounds bodyBounds;
	final double bodyVolume;
	@Nullable
	final HitboxGeometry hitboxGeometry;
	@Nullable
	final AttackGeometry attackGeometry;
	@Nullable
	final CombinationMember blockAttachmentCarrier;

	SurgicalAssembly(List<Source> sources, List<Joint> joints, List<Combination> combinations,
		List<Limb> limbs, boolean preserveLayout,
		Direction layoutFacing, SurgicalLayPose layoutLayPose, @Nullable BodyBounds bodyBounds,
		double bodyVolume, @Nullable HitboxGeometry hitboxGeometry,
		@Nullable AttackGeometry attackGeometry) {
		this(sources, joints, combinations, limbs, preserveLayout, layoutFacing, layoutLayPose,
			bodyBounds, bodyVolume, hitboxGeometry, attackGeometry, null);
	}

	private SurgicalAssembly(List<Source> sources, List<Joint> joints, List<Combination> combinations,
		List<Limb> limbs, boolean preserveLayout, Direction layoutFacing, SurgicalLayPose layoutLayPose,
		@Nullable BodyBounds bodyBounds, double bodyVolume, @Nullable HitboxGeometry hitboxGeometry,
		@Nullable AttackGeometry attackGeometry, @Nullable CombinationMember blockAttachmentCarrier) {
		this.sources = List.copyOf(sources);
		this.joints = List.copyOf(joints);
		this.combinations = List.copyOf(combinations);
		this.limbs = List.copyOf(limbs);
		this.preserveLayout = preserveLayout;
		this.layoutFacing = layoutFacing;
		this.layoutLayPose = layoutLayPose;
		this.bodyBounds = bodyBounds;
		this.bodyVolume = bodyVolume;
		this.hitboxGeometry = hitboxGeometry;
		this.attackGeometry = attackGeometry;
		this.blockAttachmentCarrier = blockAttachmentCarrier;
	}

	@Nullable
	public static SurgicalAssembly createComposite(List<Source> sources, List<Joint> joints,
		List<Combination> combinations, List<Limb> limbs, Direction layoutFacing,
		SurgicalLayPose layoutLayPose) {
		if (sources == null || sources.isEmpty() || sources.size() > MAX_SOURCES || joints == null
			|| joints.size() > MAX_SEAMS || combinations == null || combinations.size() > MAX_CUBES
			|| limbs == null || layoutFacing == null || !layoutFacing.getAxis().isHorizontal()
			|| layoutLayPose == null || !layoutLayPose.valid())
			return null;
		List<Source> frozenSources = new ArrayList<>(sources.size());
		Map<UUID, Source> donorOrigins = new HashMap<>();
		Map<UUID, BitSet> donorCubes = new HashMap<>();
		int totalCubes = 0;
		for (Source source : sources) {
			if (source == null || !source.valid())
				return null;
			Source origin = donorOrigins.putIfAbsent(source.donorId, source);
			if (origin != null && (origin.cubeCount != source.cubeCount
				|| !origin.profile.biologicalKey().equals(source.profile.biologicalKey())
				|| !origin.seams.equals(source.seams)
				|| !origin.anatomy.equals(source.anatomy)))
				return null;
			BitSet seen = donorCubes.computeIfAbsent(source.donorId, ignored -> new BitSet());
			if (seen.intersects(source.presentCubes))
				return null;
			seen.or(source.presentCubes);
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
			layoutFacing, layoutLayPose, null, Double.NaN, null, null);
	}

	/**
	 * Validates the stored endpoints without applying gameplay slot limits. Those limits are enforced
	 * when a joint is connected at the surgical table, so packing cannot reject a body merely because
	 * of how many joints it already contains.
	 */
	@Nullable
	private static List<Limb> normalizeLimbs(List<Limb> limbs, List<Source> sources) {
		if (limbs.isEmpty())
			return List.of();
		Set<CombinationMember> children = new HashSet<>();
		List<Limb> normalized = new ArrayList<>(limbs.size());
		for (Limb limb : limbs) {
			if (limb == null || limb.type() == null || !limb.validFor(sources))
				return null;
			if (!children.add(new CombinationMember(limb.childSource(), limb.childCube())))
				return null;
			normalized.add(limb);
		}
		return List.copyOf(normalized);
	}

	@Nullable
	public static SurgicalAssembly loadCached(CompoundTag tag) {
		SurgicalAssembly cached = DECODED.getIfPresent(tag);
		if (cached != null)
			return cached;
		SurgicalAssembly loaded = SurgicalAssemblyPersistence.load(tag);
		if (loaded != null)
			// The caller owns its mutable NBT; only our private snapshot is a cache key.
			DECODED.put(tag.copy(), loaded);
		return loaded;
	}

	@Nullable
	public static SurgicalAssembly load(CompoundTag tag) {
		return SurgicalAssemblyPersistence.load(tag);
	}

	/** Published formats only; callers can persist the decoded body's current save once. */
	public static boolean needsReleaseMigration(CompoundTag tag) {
		return SurgicalAssemblyPersistence.needsReleaseMigration(tag);
	}

	public CompoundTag save() {
		return SurgicalAssemblyPersistence.save(this);
	}

	public List<Source> sources() { return sources; }
	public List<Joint> joints() { return joints; }
	public List<Combination> combinations() { return combinations; }
	public List<Limb> limbs() { return limbs; }
	/** Cubes connected to the largest assembled body island through uncut seams and attachments. */
	public Set<CombinationMember> connectedMembers() {
		List<SurgicalConnectionGraph.Body<Integer>> bodies = new ArrayList<>(sources.size());
		for (int sourceId = 0; sourceId < sources.size(); sourceId++) {
			Source source = sources.get(sourceId);
			bodies.add(new SurgicalConnectionGraph.Body<>(sourceId, source.cubeCount,
				source.presentCubes, source.seams, source.cutSeams));
		}
		List<SurgicalConnectionGraph.Link<Integer>> links = new ArrayList<>();
		// A whole, never-cut donor is one biological source even if its rendered cubes do not touch.
		for (int sourceId = 0; sourceId < sources.size(); sourceId++) {
			Source source = sources.get(sourceId);
			if (source.presentCubes.cardinality() != source.cubeCount || !source.cutSeams.isEmpty())
				continue;
			int anchor = source.presentCubes.nextSetBit(0);
			for (int cube = source.presentCubes.nextSetBit(anchor + 1); cube >= 0;
				cube = source.presentCubes.nextSetBit(cube + 1))
				links.add(new SurgicalConnectionGraph.Link<>(sourceId, anchor, sourceId, cube));
		}
		for (Joint joint : joints)
			links.add(new SurgicalConnectionGraph.Link<>(joint.firstSource(), joint.firstCube(),
				joint.secondSource(), joint.secondCube()));
		for (Combination combination : combinations) {
			CombinationMember anchor = combination.members().getFirst();
			for (CombinationMember member : combination.members().subList(1, combination.members().size()))
				links.add(new SurgicalConnectionGraph.Link<>(anchor.source(), anchor.cube(),
					member.source(), member.cube()));
		}
		SurgicalConnectionGraph<Integer> graph = SurgicalConnectionGraph.create(bodies, links);
		if (graph == null)
			return Set.of();
		List<SurgicalConnectionGraph.Component<Integer>> components = graph.components();
		if (components.isEmpty())
			return Set.of();
		Set<CombinationMember> connected = new HashSet<>();
		components.getFirst().members().forEach((sourceId, cubes) -> {
			for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1))
				connected.add(new CombinationMember(sourceId, cube));
		});
		return Set.copyOf(connected);
	}

	@Nullable
	public static SurgicalAssembly create(MimicProfile profile, UUID donorId, int cubeCount,
		BitSet presentCubes, BionicAnatomySnapshot anatomy,
		List<Seam> seams, BitSet cutSeams, List<Integer> cutOrder) {
		Source source = Source.create(profile, donorId, cubeCount, presentCubes, anatomy, seams, cutSeams, cutOrder,
			Direction.NORTH, SurgicalLayPose.IDENTITY, Vec3.ZERO, Map.of(), Map.of());
		return source == null ? null
			: new SurgicalAssembly(List.of(source), List.of(), List.of(), List.of(), false,
				Direction.NORTH, SurgicalLayPose.IDENTITY, null, Double.NaN, null, null);
	}
	/** Installed joints that currently satisfy cube ownership and tier-matching rules. */
	public List<Limb> effectiveLimbs() { return SurgicalAssemblyLimbTopology.limbTopology(this).effective; }

	/**
	 * The first-level joint through which a present cube is installed: {@code NECK},
	 * {@code SHOULDER} or {@code HIP}. Cubes driven by an elbow or knee belong to its shoulder or
	 * hip. Null means the cube is on the torso, including every cube of a body without joints.
	 */
	@Nullable
	public SurgicalLimbType mountOf(int source, int cube) {
		Limb limb = primaryLimbOf(source, cube);
		return limb == null ? null : limb.type();
	}

	/** The individual head, arm or leg chain owning a cube, including its elbow/knee tissue. */
	@Nullable
	public Limb primaryLimbOf(int source, int cube) {
		Limb limb = SurgicalAssemblyLimbTopology.limbTopology(this).owners.get(new CombinationMember(source, cube));
		if (limb == null || limb.type().primary())
			return limb;
		Limb primary = SurgicalAssemblyLimbTopology.limbTopology(this).owners.get(new CombinationMember(limb.parentSource(), limb.parentCube()));
		return primary != null && primary.type() == limb.type().matchingPrimary() ? primary : null;
	}
	public boolean preservesLayout() { return preserveLayout; }
	public Direction layoutFacing() { return layoutFacing; }
	public SurgicalLayPose layoutLayPose() { return layoutLayPose; }
	@Nullable
	public BodyBounds bodyBounds() { return bodyBounds; }
	public double bodyVolume() { return bodyVolume; }
	public boolean hasBodyVolume() { return SurgicalHealthCalibration.validVolume(bodyVolume); }
	public boolean isReadyForEntity() {
		return bodyBounds != null && hitboxGeometry != null
			&& SurgicalHealthCalibration.validMeasuredVolume(bodyVolume, hitboxGeometry);
	}
	@Nullable
	public HitboxGeometry hitboxGeometry() { return hitboxGeometry; }
	@Nullable
	public AttackGeometry attackGeometry() { return attackGeometry; }
	@Nullable
	public CombinationMember blockAttachmentCarrier() { return blockAttachmentCarrier; }

	public SurgicalAssembly withBlockAttachmentCarrier(CombinationMember carrier) {
		if (carrier == null || carrier.source() < 0 || carrier.source() >= sources.size()
			|| !sources.get(carrier.source()).containsCube(carrier.cube()))
			throw new IllegalArgumentException("An attachment carrier must belong to the assembly");
		return new SurgicalAssembly(sources, joints, combinations, limbs, preserveLayout, layoutFacing,
			layoutLayPose, bodyBounds, bodyVolume, hitboxGeometry, attackGeometry, carrier);
	}

	public SurgicalAssembly withBodyBounds(BodyBounds bounds) {
		if (bounds == null)
			throw new IllegalArgumentException("A surgical body requires valid bounds");
		return new SurgicalAssembly(sources, joints, combinations, limbs, preserveLayout, layoutFacing,
			layoutLayPose, bounds, bodyVolume, hitboxGeometry, attackGeometry, blockAttachmentCarrier);
	}

	public SurgicalAssembly withHitboxGeometry(HitboxGeometry geometry) {
		if (geometry == null)
			throw new IllegalArgumentException("A surgical hitbox geometry cannot be null");
		return new SurgicalAssembly(sources, joints, combinations, limbs, preserveLayout, layoutFacing,
			layoutLayPose, bodyBounds, bodyVolume, geometry, attackGeometry, blockAttachmentCarrier);
	}

	public SurgicalAssembly withBodyGeometry(BodyBounds bounds, HitboxGeometry geometry) {
		if (bounds == null || geometry == null)
			throw new IllegalArgumentException("A surgical body requires complete physical geometry");
		return new SurgicalAssembly(sources, joints, combinations, limbs, preserveLayout, layoutFacing,
			layoutLayPose, bounds, bodyVolume, geometry, attackGeometry, blockAttachmentCarrier);
	}

	public SurgicalAssembly withBodyGeometry(BodyBounds bounds, HitboxGeometry geometry, double volume) {
		if (bounds == null || geometry == null || !SurgicalHealthCalibration.validMeasuredVolume(volume, geometry))
			throw new IllegalArgumentException("A surgical body requires complete physical geometry");
		return new SurgicalAssembly(sources, joints, combinations, limbs, preserveLayout, layoutFacing,
			layoutLayPose, bounds, volume, geometry, attackGeometry, blockAttachmentCarrier);
	}

	public SurgicalAssembly withBodyVolume(double volume) {
		if (!SurgicalHealthCalibration.validVolume(volume))
			throw new IllegalArgumentException("A surgical body requires a valid volume");
		return new SurgicalAssembly(sources, joints, combinations, limbs, preserveLayout, layoutFacing,
			layoutLayPose, bodyBounds, volume, hitboxGeometry, attackGeometry, blockAttachmentCarrier);
	}

	public SurgicalAssembly withAttackGeometry(AttackGeometry geometry) {
		if (geometry == null)
			throw new IllegalArgumentException("A surgical attack geometry cannot be null");
		List<Source> refreshedSources = sources;
		if (!geometry.carrierParts().isEmpty()) {
			refreshedSources = new ArrayList<>(sources);
			for (int index = 0; index < sources.size(); index++) {
				Source source = sources.get(index);
				Map<Integer, Set<String>> parts = new HashMap<>(source.anatomy().parts());
				for (var entry : geometry.carrierParts().entrySet())
					if (entry.getKey().source() == index && source.validCubeId(entry.getKey().cube()))
						parts.put(entry.getKey().cube(), entry.getValue());
				BionicAnatomySnapshot anatomy = BionicAnatomySnapshot.ofParts(parts, source.anatomy().nodes(), source.cubeCount());
				if (anatomy == null) continue;
				if (source.anatomy().hasVolumes()) anatomy = anatomy.withVolumes(source.anatomy().volumes());
				if (anatomy == null) continue;
				refreshedSources.set(index, new Source(source.profile, source.donorId, source.cubeCount,
					source.presentCubes,
					anatomy, source.seams, source.cutSeams, source.cutOrder, source.facing, source.layPose,
					source.originOffset, source.cubeOffsets, source.cubeRotations));
			}
			// Refreshed labels now live in Sources; the packet metadata need not be saved twice.
			geometry = new AttackGeometry(geometry.right(), geometry.left(), geometry.carrierOrigins(),
				geometry.ridingSeats(), geometry.carrierCubes(), geometry.equipmentBounds());
		}
		return new SurgicalAssembly(refreshedSources, joints, combinations, limbs, preserveLayout, layoutFacing,
			layoutLayPose, bodyBounds, bodyVolume, hitboxGeometry, geometry, blockAttachmentCarrier);
	}

	/**
	 * The child part and every attachment that depends on it to reach the joint's parent side. A part
	 * with another path back to the parent is structural body material and does not follow the joint.
	 */
	public List<CombinationMember> rotatingGroup(int source, int cube) {
		return SurgicalAssemblyLimbTopology.rotatingGroup(this, source, cube);
	}

	record LimbTopology(List<Limb> effective,
		Map<CombinationMember, List<CombinationMember>> groups, Map<CombinationMember, Limb> owners) {
		static final LimbTopology EMPTY = new LimbTopology(List.of(), Map.of(), Map.of());
	}

	record LimbAttachment(Limb limb, boolean child) {}

	record ConnectionKey(int firstSource, int firstCube, int secondSource, int secondCube) {
		static ConnectionKey of(int firstSource, int firstCube, int secondSource, int secondCube) {
			return firstSource < secondSource || firstSource == secondSource && firstCube <= secondCube
				? new ConnectionKey(firstSource, firstCube, secondSource, secondCube)
				: new ConnectionKey(secondSource, secondCube, firstSource, firstCube);
		}
	}

	public SurgicalLayPose placedLayPose(Direction placementFacing) {
		return layoutLayPose.rotateClockwise(clockwiseTurns(layoutFacing, horizontal(placementFacing)));
	}

	public SurgicalLayPose groundDownLayPose(Direction placementFacing) {
		return groundPlacementPose(placementFacing, SurgicalLayPose.RotationAxis.NONE);
	}

	public SurgicalLayPose groundPlacementPose(Direction placementFacing, SurgicalLayPose.RotationAxis axis) {
		return new SurgicalLayPose(axis,
			placedLayPose(placementFacing).yaw(), Vec3.ZERO);
	}

	public List<PlacedSource> placedSources(Direction facing, SurgicalLayPose target) {
		SurgicalLayPose original = placedLayPose(facing);
		List<PlacedSource> placed = placedSources(facing);
		if (original.equals(target))
			return placed;
		return placed.stream().map(source -> {
			Map<Integer, Vec3> offsets = new HashMap<>();
			source.cubeOffsets.forEach((cube, offset) -> offsets.put(cube, original.rotateInto(target, offset)));
			Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>();
			source.cubeRotations.forEach((cube, rotation) -> rotations.put(cube, rotation.reframe(original, target)));
			return new PlacedSource(source.source, source.facing, source.layPose.reframe(original, target),
				original.rotateInto(target, source.originOffset), offsets, rotations);
		}).toList();
	}

	public List<Joint> placedJoints(Direction facing, SurgicalLayPose target) {
		SurgicalLayPose original = placedLayPose(facing);
		if (original.equals(target))
			return placedJoints(facing);
		return placedJoints(facing).stream().map(joint -> joint.replay == null ? joint
			: new Joint(joint.firstSource, joint.firstCube, joint.secondSource, joint.secondCube,
				new JointReplay(joint.replay.movingSource, joint.replay.movingCube,
					new SurgicalGlueTransform(original.rotateInto(target, joint.replay.transform.translation()),
						joint.replay.transform.rotation().reframe(original, target)), joint.replay.anchorContact)))
			.toList();
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

	/** Single-source convenience view used by ordinary assemblies. */
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
		return SurgicalAssemblyPersistence.encodeSeams(seams);
	}

	@Nullable
	public static List<Seam> decodeSeams(int[] encoded) {
		return SurgicalAssemblyPersistence.decodeSeams(encoded);
	}

	public static List<Integer> normalizeCutOrder(List<Integer> input, BitSet cutSeams, int seamCount) {
		return SurgicalAssemblyPersistence.normalizeCutOrder(input, cutSeams, seamCount);
	}

	public record CarrierEquipmentBounds(@Nullable VisualBounds saddle, @Nullable VisualBounds chest) {}

	/** Immutable physical dimensions and articulation for every installed arm, baked from the rest geometry. */
	public record AttackGeometry(List<ArmAttackGeometry> right,
		List<ArmAttackGeometry> left, Map<CombinationMember, Vec3> carrierOrigins,
		Map<CombinationMember, RidingSeat> ridingSeats, Map<CombinationMember, List<Vec3>> carrierCubes,
		Map<CombinationMember, CarrierEquipmentBounds> equipmentBounds,
		Map<CombinationMember, Set<String>> carrierParts) {
		public AttackGeometry(List<ArmAttackGeometry> right, List<ArmAttackGeometry> left,
			Map<CombinationMember, Vec3> origins, Map<CombinationMember, RidingSeat> seats,
			Map<CombinationMember, List<Vec3>> cubes, Map<CombinationMember, CarrierEquipmentBounds> equipment) {
			this(right, left, origins, seats, cubes, equipment, Map.of());
		}
		public AttackGeometry(List<ArmAttackGeometry> right, List<ArmAttackGeometry> left) {
			this(right, left, Map.of());
		}
		public AttackGeometry(List<ArmAttackGeometry> right, List<ArmAttackGeometry> left,
			Map<CombinationMember, Vec3> carrierOrigins) {
			this(right, left, carrierOrigins, Map.of());
		}
		public AttackGeometry(List<ArmAttackGeometry> right, List<ArmAttackGeometry> left,
			Map<CombinationMember, Vec3> origins, Map<CombinationMember, RidingSeat> seats) {
			this(right, left, origins, seats, Map.of());
		}
		public AttackGeometry(List<ArmAttackGeometry> right, List<ArmAttackGeometry> left,
			Map<CombinationMember, Vec3> origins, Map<CombinationMember, RidingSeat> seats,
			Map<CombinationMember, List<Vec3>> cubes) {
			this(right, left, origins, seats, cubes, Map.of());
		}
		private static final double MAX_COORDINATE = MAX_BODY_SIZE * 2.0d;

		public AttackGeometry {
			right = freezeArms(right);
			left = freezeArms(left);
			carrierOrigins = Map.copyOf(carrierOrigins);
			ridingSeats = Map.copyOf(ridingSeats);
			equipmentBounds = Map.copyOf(equipmentBounds);
			carrierParts = carrierParts.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
				Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
			if (!carrierOrigins.keySet().containsAll(carrierParts.keySet()) || carrierParts.values().stream()
				.anyMatch(names -> names.size() > 16 || names.stream()
					.anyMatch(name -> !BionicAnatomySnapshot.validPartName(name))))
				throw new IllegalArgumentException("Invalid carrier model-part labels");
			if (!carrierOrigins.keySet().containsAll(equipmentBounds.keySet()))
				throw new IllegalArgumentException("Equipment bounds require a carrier cube");
			carrierCubes = carrierCubes.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
				Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
			if (!carrierOrigins.keySet().containsAll(carrierCubes.keySet()) || carrierCubes.values().stream()
				.anyMatch(corners -> corners.size() != 8 || corners.stream().anyMatch(point -> !validPoint(point))))
				throw new IllegalArgumentException("Invalid carrier cuboids");
			if (carrierOrigins.size() > MAX_CUBES || carrierOrigins.entrySet().stream().anyMatch(entry ->
				entry.getKey().source() < 0 || entry.getKey().source() >= MAX_SOURCES
				|| entry.getKey().cube() < 0 || entry.getKey().cube() >= MAX_CUBES || !validPoint(entry.getValue())))
				throw new IllegalArgumentException("Invalid carrier geometry");
			if (!carrierOrigins.keySet().containsAll(ridingSeats.keySet()))
				throw new IllegalArgumentException("Riding seats require a carrier cube");
			if (right.isEmpty() && left.isEmpty() && carrierOrigins.isEmpty())
				throw new IllegalArgumentException("Attack geometry requires an arm or carrier");
			if (right.size() + left.size() > SurgicalLimbType.SHOULDER.maxPerBody())
				throw new IllegalArgumentException("Attack geometry exceeds the shoulder limit");
		}
		public AttackGeometry withCarrierParts(Map<CombinationMember, Set<String>> parts) {
			return new AttackGeometry(right, left, carrierOrigins, ridingSeats, carrierCubes, equipmentBounds, parts);
		}

		@Nullable
		public static AttackGeometry create(List<ArmAttackGeometry> right,
			List<ArmAttackGeometry> left) {
			try {
				return new AttackGeometry(right, left);
			} catch (IllegalArgumentException ignored) {
				return null;
			}
		}

		@Nullable
		public static AttackGeometry create(List<ArmAttackGeometry> right, List<ArmAttackGeometry> left,
			Map<CombinationMember, Vec3> origins) {
			try { return new AttackGeometry(right, left, origins); }
			catch (IllegalArgumentException ignored) { return null; }
		}

		@Nullable
		public static AttackGeometry create(List<ArmAttackGeometry> right, List<ArmAttackGeometry> left,
			Map<CombinationMember, Vec3> origins, Map<CombinationMember, RidingSeat> seats) {
			try { return new AttackGeometry(right, left, origins, seats); }
			catch (IllegalArgumentException ignored) { return null; }
		}

		@Nullable
		public static AttackGeometry create(List<ArmAttackGeometry> right, List<ArmAttackGeometry> left,
			Map<CombinationMember, Vec3> origins, Map<CombinationMember, RidingSeat> seats,
			Map<CombinationMember, List<Vec3>> cubes) {
			try { return new AttackGeometry(right, left, origins, seats, cubes); }
			catch (IllegalArgumentException ignored) { return null; }
		}

		@Nullable
		public static AttackGeometry create(List<ArmAttackGeometry> right, List<ArmAttackGeometry> left,
			Map<CombinationMember, Vec3> origins, Map<CombinationMember, RidingSeat> seats,
			Map<CombinationMember, List<Vec3>> cubes, Map<CombinationMember, CarrierEquipmentBounds> equipment) {
			try { return new AttackGeometry(right, left, origins, seats, cubes, equipment); }
			catch (IllegalArgumentException ignored) { return null; }
		}

		private static Map<CombinationMember, CarrierEquipmentBounds> loadEquipmentBounds(CompoundTag tag) {
			Map<CombinationMember, CarrierEquipmentBounds> bounds = new HashMap<>();
			ListTag list = tag.getList("CarrierOrigins", Tag.TAG_COMPOUND);
			if (list.size() > MAX_CUBES) throw new IllegalArgumentException("Too many equipment bounds");
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompound(i);
				VisualBounds saddle = entry.contains("SaddleBounds", Tag.TAG_COMPOUND)
					? VisualBounds.load(entry.getCompound("SaddleBounds")) : null;
				VisualBounds chest = entry.contains("ChestBounds", Tag.TAG_COMPOUND)
					? VisualBounds.load(entry.getCompound("ChestBounds")) : null;
				if (saddle != null || chest != null) bounds.put(new CombinationMember(entry.getInt("Source"), entry.getInt("Cube")),
					new CarrierEquipmentBounds(saddle, chest));
			}
			return bounds;
		}

		private static Map<CombinationMember, List<Vec3>> loadCubes(CompoundTag tag) {
			Map<CombinationMember, List<Vec3>> cubes = new HashMap<>();
			ListTag list = tag.getList("CarrierOrigins", Tag.TAG_COMPOUND);
			if (list.size() > MAX_CUBES) throw new IllegalArgumentException("Too many carrier cuboids");
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompound(i);
				if (!entry.contains("CornerBits")) continue;
				List<Vec3> corners = new ArrayList<>();
				if (!entry.contains("CornerBits", Tag.TAG_LONG_ARRAY))
					throw new IllegalArgumentException("Invalid carrier corner encoding");
				long[] bits = entry.getLongArray("CornerBits");
				if (bits.length != 24)
					throw new IllegalArgumentException("A carrier cuboid requires eight corners");
				for (int j = 0; j < bits.length; j += 3)
					corners.add(new Vec3(Double.longBitsToDouble(bits[j]),
						Double.longBitsToDouble(bits[j + 1]), Double.longBitsToDouble(bits[j + 2])));
				cubes.put(new CombinationMember(entry.getInt("Source"), entry.getInt("Cube")), corners);
			}
			return cubes;
		}

		private ListTag saveOrigins() {
			ListTag list = new ListTag();
			carrierOrigins.entrySet().stream().sorted(java.util.Comparator
				.comparingInt((Map.Entry<CombinationMember, Vec3> e) -> e.getKey().source())
				.thenComparingInt(e -> e.getKey().cube())).forEach(entry -> {
				CompoundTag point = new CompoundTag();
				point.putInt("Source", entry.getKey().source());
				point.putInt("Cube", entry.getKey().cube());
				point.putDouble("X", entry.getValue().x);
				point.putDouble("Y", entry.getValue().y);
				point.putDouble("Z", entry.getValue().z);
				Set<String> names = carrierParts.get(entry.getKey());
				if (names != null) {
					ListTag labels = new ListTag();
					names.stream().sorted().forEach(name -> labels.add(net.minecraft.nbt.StringTag.valueOf(name)));
					point.put("Parts", labels);
				}
				List<Vec3> corners = carrierCubes.get(entry.getKey());
				if (corners != null) {
					// Raw double bits remove per-coordinate tags without rounding collision geometry.
					long[] bits = new long[corners.size() * 3];
					for (int index = 0; index < corners.size(); index++) {
						Vec3 corner = corners.get(index);
						bits[index * 3] = Double.doubleToRawLongBits(corner.x);
						bits[index * 3 + 1] = Double.doubleToRawLongBits(corner.y);
						bits[index * 3 + 2] = Double.doubleToRawLongBits(corner.z);
					}
					point.putLongArray("CornerBits", bits);
				}
				CarrierEquipmentBounds bounds = equipmentBounds.get(entry.getKey());
				if (bounds != null) {
					if (bounds.saddle() != null) point.put("SaddleBounds", bounds.saddle().save());
					if (bounds.chest() != null) point.put("ChestBounds", bounds.chest().save());
				}
				RidingSeat seat = ridingSeats.get(entry.getKey());
				if (seat != null) {
					point.put("Seat", savePoint(seat.first()));
					point.put("RearSeat", savePoint(seat.second()));
					if (seat.rotation() != null)
						point.put("SeatRotation", seat.rotation().save());
				}
				list.add(point);
			});
			return list;
		}
		private static Map<CombinationMember, Set<String>> loadCarrierParts(CompoundTag tag) {
			Map<CombinationMember, Set<String>> parts = new HashMap<>();
			ListTag origins = tag.getList("CarrierOrigins", Tag.TAG_COMPOUND);
			for (int index = 0; index < origins.size(); index++) {
				CompoundTag entry = origins.getCompound(index);
				if (!entry.contains("Parts", Tag.TAG_LIST)) continue;
				ListTag labels = entry.getList("Parts", Tag.TAG_STRING);
				if (labels.size() > 16) throw new IllegalArgumentException("Too many carrier model-part labels");
				Set<String> names = new HashSet<>();
				for (int label = 0; label < labels.size(); label++) names.add(labels.getString(label));
				if (!names.isEmpty()) parts.put(new CombinationMember(entry.getInt("Source"), entry.getInt("Cube")), names);
			}
			return parts;
		}

		private static CompoundTag savePoint(Vec3 point) {
			CompoundTag tag = new CompoundTag();
			tag.putDouble("X", point.x);
			tag.putDouble("Y", point.y);
			tag.putDouble("Z", point.z);
			return tag;
		}

		private static Vec3 loadPoint(CompoundTag tag) {
			return new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
		}

		private static Map<CombinationMember, RidingSeat> loadSeats(CompoundTag tag) {
			Map<CombinationMember, RidingSeat> seats = new HashMap<>();
			ListTag list = tag.getList("CarrierOrigins", Tag.TAG_COMPOUND);
			if (list.size() > MAX_CUBES) throw new IllegalArgumentException("Too many riding seats");
			for (int i = 0; i < list.size(); i++) {
				CompoundTag point = list.getCompound(i);
				if (!point.contains("Seat", Tag.TAG_COMPOUND)) continue;
				if (!point.contains("RearSeat", Tag.TAG_COMPOUND))
					throw new IllegalArgumentException("Missing rear riding seat");
				Vec3 first = loadPoint(point.getCompound("Seat"));
				Vec3 second = loadPoint(point.getCompound("RearSeat"));
				SurgicalCubeRotation rotation = point.contains("SeatRotation", Tag.TAG_COMPOUND)
					? SurgicalCubeRotation.load(point.getCompound("SeatRotation")) : null;
				seats.put(new CombinationMember(point.getInt("Source"), point.getInt("Cube")),
					new RidingSeat(first, second, rotation));
			}
			return seats;
		}

		private static Map<CombinationMember, Vec3> loadOrigins(CompoundTag tag) {
			ListTag list = tag.getList("CarrierOrigins", Tag.TAG_COMPOUND);
			if (list.size() > MAX_CUBES) throw new IllegalArgumentException("Too many carrier origins");
			Map<CombinationMember, Vec3> points = new HashMap<>();
			for (int i = 0; i < list.size(); i++) {
				CompoundTag point = list.getCompound(i);
				CombinationMember member = new CombinationMember(point.getInt("Source"), point.getInt("Cube"));
				if (points.put(member, new Vec3(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z"))) != null)
					throw new IllegalArgumentException("Duplicate carrier origin");
			}
			return points;
		}

		@Nullable
		public ArmAttackGeometry arm(boolean leftSide) {
			return arm(leftSide, 0);
		}

		/** Chooses one arm on the requested side, falling back to the other side when necessary. */
		@Nullable
		public ArmAttackGeometry arm(boolean leftSide, int slot) {
			List<ArmAttackGeometry> preferred = leftSide ? left : right;
			List<ArmAttackGeometry> fallback = leftSide ? right : left;
			List<ArmAttackGeometry> available = preferred.isEmpty() ? fallback : preferred;
			return available.isEmpty() ? null : available.get(Math.floorMod(slot, available.size()));
		}

		public boolean hasArms(boolean leftSide) {
			return !(leftSide ? left : right).isEmpty();
		}

		public int armCount(boolean leftSide) {
			return (leftSide ? left : right).size();
		}

		public int armCount() {
			return right.size() + left.size();
		}

		/** Right slots first, then left. Both sides are immutable, so a one-sided body shares its list. */
		public List<ArmAttackGeometry> arms() {
			if (left.isEmpty())
				return right;
			if (right.isEmpty())
				return left;
			List<ArmAttackGeometry> arms = new ArrayList<>(armCount());
			arms.addAll(right);
			arms.addAll(left);
			return java.util.Collections.unmodifiableList(arms);
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			putArms(tag, RIGHT_ARMS_TAG, right);
			putArms(tag, LEFT_ARMS_TAG, left);
			tag.put("CarrierOrigins", saveOrigins());
			return tag;
		}

		public void write(FriendlyByteBuf buffer) {
			writeArms(buffer, right);
			writeArms(buffer, left);
			CompoundTag origins = new CompoundTag();
			origins.put("CarrierOrigins", saveOrigins());
			buffer.writeNbt(origins);
		}

		@Nullable
		public static AttackGeometry read(FriendlyByteBuf buffer) {
			List<ArmAttackGeometry> right = readArms(buffer);
			List<ArmAttackGeometry> left = readArms(buffer);
			CompoundTag origins = buffer.readNbt();
			if (right == null || left == null || origins == null) return null;
			try { return new AttackGeometry(right, left, loadOrigins(origins), loadSeats(origins), loadCubes(origins),
				loadEquipmentBounds(origins), loadCarrierParts(origins)); }
			catch (IllegalArgumentException ignored) { return null; }
		}

		@Nullable
		static AttackGeometry load(CompoundTag tag) {
			List<ArmAttackGeometry> right = loadArms(tag, RIGHT_ARMS_TAG);
			List<ArmAttackGeometry> left = loadArms(tag, LEFT_ARMS_TAG);
			if (right == null || left == null)
				return null;
			try { return new AttackGeometry(right, left, loadOrigins(tag), loadSeats(tag), loadCubes(tag),
				loadEquipmentBounds(tag), loadCarrierParts(tag)); }
			catch (IllegalArgumentException ignored) { return null; }
		}

		private static List<ArmAttackGeometry> freezeArms(List<ArmAttackGeometry> arms) {
			if (arms == null || arms.size() > SurgicalLimbType.SHOULDER.maxPerBody()
				|| arms.stream().anyMatch(java.util.Objects::isNull))
				throw new IllegalArgumentException("Invalid arm attack geometry list");
			return List.copyOf(arms);
		}

		private static void putArms(CompoundTag tag, String key, List<ArmAttackGeometry> arms) {
			if (arms.isEmpty())
				return;
			ListTag encoded = new ListTag();
			for (ArmAttackGeometry arm : arms)
				encoded.add(arm.save());
			tag.put(key, encoded);
		}

		private static void writeArms(FriendlyByteBuf buffer, List<ArmAttackGeometry> arms) {
			buffer.writeVarInt(arms.size());
			for (ArmAttackGeometry arm : arms)
				arm.write(buffer);
		}

		@Nullable
		private static List<ArmAttackGeometry> readArms(FriendlyByteBuf buffer) {
			int count = buffer.readVarInt();
			if (count < 0 || count > SurgicalLimbType.SHOULDER.maxPerBody())
				return null;
			List<ArmAttackGeometry> arms = new ArrayList<>(count);
			for (int index = 0; index < count; index++) {
				ArmAttackGeometry arm = ArmAttackGeometry.read(buffer);
				if (arm == null)
					return null;
				arms.add(arm);
			}
			return List.copyOf(arms);
		}

		@Nullable
		private static List<ArmAttackGeometry> loadArms(CompoundTag tag, String key) {
			if (!tag.contains(key))
				return List.of();
			if (!tag.contains(key, Tag.TAG_LIST))
				return null;
			ListTag encoded = (ListTag) tag.get(key);
			if (!encoded.isEmpty() && encoded.getElementType() != Tag.TAG_COMPOUND)
				return null;
			if (encoded.size() > SurgicalLimbType.SHOULDER.maxPerBody())
				return null;
			List<ArmAttackGeometry> arms = new ArrayList<>(encoded.size());
			for (int index = 0; index < encoded.size(); index++) {
				ArmAttackGeometry arm = ArmAttackGeometry.load(encoded.getCompound(index));
				if (arm == null)
					return null;
				arms.add(arm);
			}
			return List.copyOf(arms);
		}

		private static boolean validPoint(Vec3 point) {
			return point != null && Double.isFinite(point.x) && Double.isFinite(point.y)
				&& Double.isFinite(point.z) && Math.abs(point.x) <= MAX_COORDINATE
				&& Math.abs(point.y) <= MAX_COORDINATE && Math.abs(point.z) <= MAX_COORDINATE;
		}
	}

	/** Native seat points transformed with their carrier cube into the assembled body's rest frame. */
	public record RidingSeat(Vec3 first, Vec3 second, @Nullable SurgicalCubeRotation rotation) {
		public RidingSeat(Vec3 first, Vec3 second) {
			this(first, second, null);
		}
		public RidingSeat {
			if (!AttackGeometry.validPoint(first) || !AttackGeometry.validPoint(second))
				throw new IllegalArgumentException("Invalid riding seat");
		}
	}

	public record ArmAttackGeometry(Vec3 origin, float reach, float minimumY, float maximumY,
		float radius, float volume, @Nullable Vec3 restDirection, boolean hasElbow,
		int sourceId, int cubeId) {
		private static final float MIN_RADIUS = 0.05f;
		private static final float MAX_RADIUS = 8.0f;
		private static final float MAX_VOLUME = (float) (MAX_BODY_SIZE * MAX_BODY_SIZE * MAX_BODY_SIZE);

		/** Old assemblies and body strikes keep the generic range when no rest direction was saved. */
		public ArmAttackGeometry(Vec3 origin, float reach, float minimumY, float maximumY,
			float radius, float volume) {
			this(origin, reach, minimumY, maximumY, radius, volume, null, false, -1, -1);
		}

		public ArmAttackGeometry(Vec3 origin, float reach, float minimumY, float maximumY,
			float radius, float volume, @Nullable Vec3 restDirection) {
			this(origin, reach, minimumY, maximumY, radius, volume, restDirection, false, -1, -1);
		}

		public ArmAttackGeometry(Vec3 origin, float reach, float minimumY, float maximumY,
			float radius, float volume, @Nullable Vec3 restDirection, boolean hasElbow) {
			this(origin, reach, minimumY, maximumY, radius, volume, restDirection, hasElbow, -1, -1);
		}

		public ArmAttackGeometry {
			if (!AttackGeometry.validPoint(origin) || !Float.isFinite(reach)
				|| reach < MIN_RADIUS || reach > AttackGeometry.MAX_COORDINATE
				|| !Float.isFinite(minimumY) || !Float.isFinite(maximumY)
				|| minimumY > maximumY || Math.abs(minimumY) > AttackGeometry.MAX_COORDINATE
				|| Math.abs(maximumY) > AttackGeometry.MAX_COORDINATE
				|| !Float.isFinite(radius) || radius < MIN_RADIUS || radius > MAX_RADIUS
				|| !Float.isFinite(volume) || volume < 0.0f || volume > MAX_VOLUME)
				throw new IllegalArgumentException("Invalid arm attack geometry");
			if (restDirection != null) {
				double lengthSqr = restDirection.lengthSqr();
				if (!Double.isFinite(lengthSqr) || lengthSqr <= 1.0e-10d)
					throw new IllegalArgumentException("Invalid arm rest direction");
				restDirection = restDirection.scale(1.0d / Math.sqrt(lengthSqr));
			}
			if ((sourceId != -1 || cubeId != -1)
				&& (sourceId < 0 || sourceId >= MAX_SOURCES || cubeId < 0 || cubeId >= MAX_CUBES))
				throw new IllegalArgumentException("Invalid arm donor cube");
		}

		@Nullable
		public static ArmAttackGeometry create(Vec3 origin, float reach, float minimumY,
			float maximumY, float radius, float volume) {
			return create(origin, reach, minimumY, maximumY, radius, volume, null, false);
		}

		@Nullable
		public static ArmAttackGeometry create(Vec3 origin, float reach, float minimumY,
			float maximumY, float radius, float volume, @Nullable Vec3 restDirection) {
			return create(origin, reach, minimumY, maximumY, radius, volume, restDirection, false);
		}

		@Nullable
		public static ArmAttackGeometry create(Vec3 origin, float reach, float minimumY,
			float maximumY, float radius, float volume, @Nullable Vec3 restDirection,
			boolean hasElbow) {
			return create(origin, reach, minimumY, maximumY, radius, volume,
				restDirection, hasElbow, -1, -1);
		}

		@Nullable
		public static ArmAttackGeometry create(Vec3 origin, float reach, float minimumY,
			float maximumY, float radius, float volume, @Nullable Vec3 restDirection,
			boolean hasElbow, int sourceId, int cubeId) {
			try {
				return new ArmAttackGeometry(origin, reach, minimumY, maximumY, radius, volume,
					restDirection, hasElbow, sourceId, cubeId);
			} catch (IllegalArgumentException ignored) {
				return null;
			}
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putFloat(ATTACK_ORIGIN_X_TAG, (float) origin.x);
			tag.putFloat(ATTACK_ORIGIN_Y_TAG, (float) origin.y);
			tag.putFloat(ATTACK_ORIGIN_Z_TAG, (float) origin.z);
			tag.putFloat(ATTACK_REACH_TAG, reach);
			tag.putFloat(ATTACK_MINIMUM_Y_TAG, minimumY);
			tag.putFloat(ATTACK_MAXIMUM_Y_TAG, maximumY);
			tag.putFloat(ATTACK_RADIUS_TAG, radius);
			tag.putFloat(ATTACK_VOLUME_TAG, volume);
			if (restDirection != null) {
				CompoundTag direction = new CompoundTag();
				direction.putFloat("X", (float) restDirection.x);
				direction.putFloat("Y", (float) restDirection.y);
				direction.putFloat("Z", (float) restDirection.z);
				tag.put(ATTACK_REST_DIRECTION_TAG, direction);
			}
			tag.putBoolean(ATTACK_HAS_ELBOW_TAG, hasElbow);
			if (sourceId >= 0) {
				tag.putInt("AttackSourceId", sourceId);
				tag.putInt("AttackCubeId", cubeId);
			}
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
			buffer.writeBoolean(restDirection != null);
			if (restDirection != null) {
				buffer.writeFloat((float) restDirection.x);
				buffer.writeFloat((float) restDirection.y);
				buffer.writeFloat((float) restDirection.z);
			}
			buffer.writeBoolean(hasElbow);
			buffer.writeVarInt(sourceId);
			buffer.writeVarInt(cubeId);
		}

		@Nullable
		private static ArmAttackGeometry read(FriendlyByteBuf buffer) {
			Vec3 origin = new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
			float reach = buffer.readFloat();
			float minimumY = buffer.readFloat();
			float maximumY = buffer.readFloat();
			float radius = buffer.readFloat();
			float volume = buffer.readFloat();
			Vec3 restDirection = buffer.readBoolean()
				? new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()) : null;
			boolean hasElbow = buffer.readBoolean();
			int sourceId = buffer.readVarInt();
			int cubeId = buffer.readVarInt();
			return create(origin, reach, minimumY, maximumY, radius, volume, restDirection,
				hasElbow, sourceId, cubeId);
		}

		@Nullable
		static ArmAttackGeometry load(CompoundTag tag) {
			if (SurgicalAssemblyPersistence.hasWrongType(tag, ATTACK_HAS_ELBOW_TAG, Tag.TAG_BYTE))
				return null;
			if (!tag.contains(ATTACK_ORIGIN_X_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(ATTACK_ORIGIN_Y_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(ATTACK_ORIGIN_Z_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(ATTACK_REACH_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(ATTACK_MINIMUM_Y_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(ATTACK_MAXIMUM_Y_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(ATTACK_RADIUS_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(ATTACK_VOLUME_TAG, Tag.TAG_ANY_NUMERIC))
				return null;
			Vec3 restDirection = null;
			if (tag.contains(ATTACK_REST_DIRECTION_TAG)) {
				if (!tag.contains(ATTACK_REST_DIRECTION_TAG, Tag.TAG_COMPOUND))
					return null;
				CompoundTag direction = tag.getCompound(ATTACK_REST_DIRECTION_TAG);
				if (!direction.contains("X", Tag.TAG_ANY_NUMERIC) || !direction.contains("Y", Tag.TAG_ANY_NUMERIC)
					|| !direction.contains("Z", Tag.TAG_ANY_NUMERIC))
					return null;
				restDirection = new Vec3(direction.getFloat("X"), direction.getFloat("Y"), direction.getFloat("Z"));
			}
			return create(new Vec3(tag.getFloat(ATTACK_ORIGIN_X_TAG),
				tag.getFloat(ATTACK_ORIGIN_Y_TAG), tag.getFloat(ATTACK_ORIGIN_Z_TAG)),
				tag.getFloat(ATTACK_REACH_TAG), tag.getFloat(ATTACK_MINIMUM_Y_TAG),
				tag.getFloat(ATTACK_MAXIMUM_Y_TAG), tag.getFloat(ATTACK_RADIUS_TAG),
				tag.getFloat(ATTACK_VOLUME_TAG), restDirection, tag.getBoolean(ATTACK_HAS_ELBOW_TAG),
				tag.contains("AttackSourceId", Tag.TAG_ANY_NUMERIC) ? tag.getInt("AttackSourceId") : -1,
				tag.contains("AttackCubeId", Tag.TAG_ANY_NUMERIC) ? tag.getInt("AttackCubeId") : -1);
		}
	}

	/** Volume-weighted collision core plus rest-pose eye and ground-locomotion measurements. */
	public record BodyBounds(float width, float height, float depth,
		float centerX, float minY, float centerZ, float eyeHeight, float legLength,
		int groundedLegCount, int groundedKneeCount, float legVolumeRatio) {
		public BodyBounds {
			if (!validSize(width) || !validSize(height) || !validSize(depth)
				|| !validOffset(centerX) || !validVerticalOffset(minY) || !validOffset(centerZ)
				|| !validEyeHeight(eyeHeight) || !validLegLength(legLength) || !validMobility(groundedLegCount,
					groundedKneeCount, legVolumeRatio))
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
			return create(width, height, depth, centerX, minY, centerZ, legLength, 0, 0, 0.0d);
		}

		@Nullable
		public static BodyBounds create(double width, double height, double depth,
			double centerX, double minY, double centerZ, double legLength,
			int groundedLegCount, int groundedKneeCount, double legVolumeRatio) {
			return create(width, height, depth, centerX, minY, centerZ,
				minY + height * 0.85d, legLength, groundedLegCount, groundedKneeCount, legVolumeRatio);
		}

		@Nullable
		public static BodyBounds create(double width, double height, double depth,
			double centerX, double minY, double centerZ, double eyeHeight, double legLength,
			int groundedLegCount, int groundedKneeCount, double legVolumeRatio) {
			if (!validSize(width) || !validSize(height) || !validSize(depth))
				return null;
			if (!validOffset(centerX) || !validVerticalOffset(minY) || !validOffset(centerZ)
				|| !validEyeHeight(eyeHeight) || !validLegLength(legLength)
				|| !validMobility(groundedLegCount, groundedKneeCount, legVolumeRatio))
				return null;
			return new BodyBounds((float) Math.max(MIN_BODY_SIZE, width),
				(float) Math.max(MIN_BODY_SIZE, height), (float) Math.max(MIN_BODY_SIZE, depth),
				(float) centerX, (float) minY, (float) centerZ, (float) eyeHeight, (float) legLength,
				groundedLegCount, groundedKneeCount, (float) legVolumeRatio);
		}

		public BodyBounds withEyeHeight(double measuredEyeHeight) {
			if (!validEyeHeight(measuredEyeHeight))
				throw new IllegalArgumentException("Invalid surgical eye height");
			return new BodyBounds(width, height, depth, centerX, minY, centerZ,
				(float) measuredEyeHeight, legLength, groundedLegCount, groundedKneeCount, legVolumeRatio);
		}

		public BodyBounds withLegLength(double measuredLegLength) {
			if (!validLegLength(measuredLegLength))
				throw new IllegalArgumentException("Invalid surgical leg length");
			return new BodyBounds(width, height, depth, centerX, minY, centerZ,
				eyeHeight, (float) measuredLegLength, groundedLegCount, groundedKneeCount, legVolumeRatio);
		}

		public BodyBounds withMobility(double measuredLegLength, int measuredGroundedLegs,
			int measuredGroundedKnees, double measuredLegVolumeRatio) {
			if (!validLegLength(measuredLegLength)
				|| !validMobility(measuredGroundedLegs, measuredGroundedKnees, measuredLegVolumeRatio))
				throw new IllegalArgumentException("Invalid surgical mobility measurements");
			return new BodyBounds(width, height, depth, centerX, minY, centerZ,
				eyeHeight, (float) measuredLegLength, measuredGroundedLegs, measuredGroundedKnees,
				(float) measuredLegVolumeRatio);
		}

		private static boolean validSize(double value) {
			return Double.isFinite(value) && value >= MIN_BODY_SIZE - MIN_BODY_SIZE_TOLERANCE
				&& value <= MAX_BODY_SIZE;
		}

		private static boolean validOffset(double value) {
			return Double.isFinite(value) && Math.abs(value) <= MAX_BODY_SIZE;
		}

		private static boolean validVerticalOffset(double value) {
			return Double.isFinite(value) && value >= 0.0d && value <= MAX_BODY_SIZE;
		}

		private static boolean validEyeHeight(double value) {
			return Double.isFinite(value) && value >= 0.0d && value <= MAX_BODY_SIZE;
		}

		private static boolean validLegLength(double value) {
			return value == 0.0d || validSize(value);
		}

		private static boolean validMobility(int groundedLegCount, int groundedKneeCount,
			double legVolumeRatio) {
			return groundedLegCount >= 0 && groundedLegCount <= SurgicalLimbType.HIP.maxPerBody()
				&& groundedKneeCount >= 0 && groundedKneeCount <= groundedLegCount
				&& Double.isFinite(legVolumeRatio) && legVolumeRatio >= 0.0d
				&& legVolumeRatio <= 1.0d;
		}
	}

	/** Complete upright render-space AABB after the model has been centred and grounded. */
	public record VisualBounds(float minX, float minY, float minZ,
		float maxX, float maxY, float maxZ) {
		private static final String MIN_X_TAG = "MinX";
		private static final String MIN_Y_TAG = "MinY";
		private static final String MIN_Z_TAG = "MinZ";
		private static final String MAX_X_TAG = "MaxX";
		private static final String MAX_Y_TAG = "MaxY";
		private static final String MAX_Z_TAG = "MaxZ";
		private static final double MAX_COORDINATE = MAX_BODY_SIZE * 2.0d;
		private static final double MAX_SIZE = MAX_BODY_SIZE * 2.0d;
		private static final double CONTAINMENT_EPSILON = 1.0e-3d;

		public VisualBounds {
			if (!valid(minX, minY, minZ, maxX, maxY, maxZ))
				throw new IllegalArgumentException("Invalid surgical visual bounds");
		}

		@Nullable
		public static VisualBounds create(double minX, double minY, double minZ,
			double maxX, double maxY, double maxZ) {
			if (!valid(minX, minY, minZ, maxX, maxY, maxZ))
				return null;
			return new VisualBounds((float) minX, (float) minY, (float) minZ,
				(float) maxX, (float) maxY, (float) maxZ);
		}

		public float size(int axis) {
			return axis == 0 ? maxX - minX : axis == 1 ? maxY - minY : maxZ - minZ;
		}

		public boolean contains(VisualBounds other) {
			return other != null && other.minX >= minX - CONTAINMENT_EPSILON
				&& other.minY >= minY - CONTAINMENT_EPSILON
				&& other.minZ >= minZ - CONTAINMENT_EPSILON
				&& other.maxX <= maxX + CONTAINMENT_EPSILON
				&& other.maxY <= maxY + CONTAINMENT_EPSILON
				&& other.maxZ <= maxZ + CONTAINMENT_EPSILON;
		}

		public void write(FriendlyByteBuf buffer) {
			buffer.writeFloat(minX);
			buffer.writeFloat(minY);
			buffer.writeFloat(minZ);
			buffer.writeFloat(maxX);
			buffer.writeFloat(maxY);
			buffer.writeFloat(maxZ);
		}

		public static VisualBounds read(FriendlyByteBuf buffer) {
			VisualBounds bounds = create(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
				buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
			if (bounds == null)
				throw new IllegalArgumentException("Invalid surgical visual bounds");
			return bounds;
		}

		private CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putFloat(MIN_X_TAG, minX);
			tag.putFloat(MIN_Y_TAG, minY);
			tag.putFloat(MIN_Z_TAG, minZ);
			tag.putFloat(MAX_X_TAG, maxX);
			tag.putFloat(MAX_Y_TAG, maxY);
			tag.putFloat(MAX_Z_TAG, maxZ);
			return tag;
		}

		@Nullable
		private static VisualBounds load(CompoundTag tag) {
			if (!tag.contains(MIN_X_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(MIN_Y_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(MIN_Z_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(MAX_X_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(MAX_Y_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(MAX_Z_TAG, Tag.TAG_ANY_NUMERIC))
				return null;
			return create(tag.getDouble(MIN_X_TAG), tag.getDouble(MIN_Y_TAG), tag.getDouble(MIN_Z_TAG),
				tag.getDouble(MAX_X_TAG), tag.getDouble(MAX_Y_TAG), tag.getDouble(MAX_Z_TAG));
		}

		private static boolean valid(double minX, double minY, double minZ,
			double maxX, double maxY, double maxZ) {
			return finiteCoordinate(minX) && finiteCoordinate(minY) && finiteCoordinate(minZ)
				&& finiteCoordinate(maxX) && finiteCoordinate(maxY) && finiteCoordinate(maxZ)
				&& validSpan(maxX - minX) && validSpan(maxY - minY) && validSpan(maxZ - minZ);
		}

		private static boolean finiteCoordinate(double value) {
			return Double.isFinite(value) && Math.abs(value) <= MAX_COORDINATE;
		}

		private static boolean validSpan(double value) {
			return Double.isFinite(value) && value >= MIN_BODY_SIZE - MIN_BODY_SIZE_TOLERANCE
				&& value <= MAX_SIZE;
		}
	}

	/** Selectable render envelope, rigid body envelope and one envelope per primary limb chain. */
	public record HitboxGeometry(VisualBounds overall, VisualBounds body,
		List<VisualBounds> limbs) {
		private static final String OVERALL_TAG = "Overall";
		private static final String BODY_TAG = "Body";
		private static final String LIMBS_TAG = "Limbs";

		public HitboxGeometry {
			limbs = limbs == null ? List.of() : List.copyOf(limbs);
			if (overall == null || body == null || limbs.size() > MAX_HITBOX_LIMBS
				|| !overall.contains(body) || limbs.stream().anyMatch(limb -> !overall.contains(limb)))
				throw new IllegalArgumentException("Invalid surgical hitbox geometry");
		}

		@Nullable
		public static HitboxGeometry create(VisualBounds overall, VisualBounds body,
			List<VisualBounds> limbs) {
			try {
				return new HitboxGeometry(overall, body, limbs);
			} catch (IllegalArgumentException exception) {
				return null;
			}
		}

		/** Bodies remain ordinary entities until one complete visual axis strictly exceeds the threshold. */
		public boolean requiresMultipart(float threshold) {
			for (int axis = 0; axis < 3; axis++)
				if (overall.size(axis) > threshold)
					return true;
			return false;
		}

		public List<VisualBounds> partBounds(float threshold) {
			if (!requiresMultipart(threshold))
				return List.of();
			if (limbs.isEmpty())
				return List.of(overall);
			List<VisualBounds> split = new ArrayList<>(limbs.size() + 1);
			split.add(body);
			split.addAll(limbs);
			return List.copyOf(split);
		}

		public void write(FriendlyByteBuf buffer) {
			overall.write(buffer);
			body.write(buffer);
			buffer.writeVarInt(limbs.size());
			for (VisualBounds limb : limbs)
				limb.write(buffer);
		}

		public static HitboxGeometry read(FriendlyByteBuf buffer) {
			VisualBounds overall = VisualBounds.read(buffer);
			VisualBounds body = VisualBounds.read(buffer);
			int count = buffer.readVarInt();
			if (count < 0 || count > MAX_HITBOX_LIMBS)
				throw new IllegalArgumentException("Invalid surgical hitbox limb count " + count);
			List<VisualBounds> limbs = new ArrayList<>(count);
			for (int index = 0; index < count; index++)
				limbs.add(VisualBounds.read(buffer));
			HitboxGeometry geometry = create(overall, body, limbs);
			if (geometry == null)
				throw new IllegalArgumentException("Invalid surgical hitbox geometry");
			return geometry;
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.put(OVERALL_TAG, overall.save());
			tag.put(BODY_TAG, body.save());
			ListTag encodedLimbs = new ListTag();
			for (VisualBounds limb : limbs)
				encodedLimbs.add(limb.save());
			tag.put(LIMBS_TAG, encodedLimbs);
			return tag;
		}

		@Nullable
		static HitboxGeometry load(CompoundTag tag) {
			if (!tag.contains(OVERALL_TAG, Tag.TAG_COMPOUND)
				|| !tag.contains(BODY_TAG, Tag.TAG_COMPOUND)
				|| !tag.contains(LIMBS_TAG, Tag.TAG_LIST))
				return null;
			VisualBounds overall = VisualBounds.load(tag.getCompound(OVERALL_TAG));
			VisualBounds body = VisualBounds.load(tag.getCompound(BODY_TAG));
			ListTag encodedLimbs = (ListTag) tag.get(LIMBS_TAG);
			if (!encodedLimbs.isEmpty() && encodedLimbs.getElementType() != Tag.TAG_COMPOUND)
				return null;
			if (encodedLimbs.size() > MAX_HITBOX_LIMBS)
				return null;
			List<VisualBounds> limbs = new ArrayList<>(encodedLimbs.size());
			for (int index = 0; index < encodedLimbs.size(); index++) {
				VisualBounds limb = VisualBounds.load(encodedLimbs.getCompound(index));
				if (limb == null)
					return null;
				limbs.add(limb);
			}
			return create(overall, body, limbs);
		}
	}

	public static final class Source {
		private final MimicProfile profile;
		private final UUID donorId;
		final int cubeCount;
		final BitSet presentCubes;
		private final BionicAnatomySnapshot anatomy;
		final List<Seam> seams;
		private final BitSet cutSeams;
		private final List<Integer> cutOrder;
		private final Direction facing;
		private final SurgicalLayPose layPose;
		private final Vec3 originOffset;
		private final Map<Integer, Vec3> cubeOffsets;
		private final Map<Integer, SurgicalCubeRotation> cubeRotations;

		private Source(MimicProfile profile, UUID donorId, int cubeCount, BitSet presentCubes,
			BionicAnatomySnapshot anatomy,
			List<Seam> seams,
			BitSet cutSeams, List<Integer> cutOrder, Direction facing, SurgicalLayPose layPose,
			Vec3 originOffset,
			Map<Integer, Vec3> cubeOffsets, Map<Integer, SurgicalCubeRotation> cubeRotations) {
			this.profile = profile;
			this.donorId = donorId;
			this.cubeCount = cubeCount;
			this.presentCubes = SurgicalAssemblyPersistence.normalize(presentCubes, cubeCount);
			this.anatomy = anatomy;
			this.seams = List.copyOf(seams);
			this.cutSeams = SurgicalAssemblyPersistence.normalize(cutSeams, seams.size());
			this.cutOrder = SurgicalAssemblyPersistence.normalizeCutOrder(cutOrder, this.cutSeams, seams.size());
			this.facing = facing;
			this.layPose = layPose;
			this.originOffset = originOffset;
			this.cubeOffsets = Map.copyOf(cubeOffsets);
			this.cubeRotations = Map.copyOf(cubeRotations);
		}

		@Nullable
		public static Source create(MimicProfile profile, UUID donorId, int cubeCount,
			BitSet presentCubes,
			BionicAnatomySnapshot anatomy,
			List<Seam> seams, BitSet cutSeams, List<Integer> cutOrder, Direction facing,
			SurgicalLayPose layPose, Vec3 originOffset, Map<Integer, Vec3> cubeOffsets,
			Map<Integer, SurgicalCubeRotation> cubeRotations) {
			if (profile == null || presentCubes == null || seams == null || cutSeams == null
				|| donorId == null
				|| anatomy == null
				|| !anatomy.fits(cubeCount)
				|| cubeOffsets == null || cubeOffsets.size() > cubeCount || cubeRotations == null
				|| cubeRotations.size() > cubeCount || facing == null || !facing.getAxis().isHorizontal()
				|| layPose == null || !layPose.valid() || !SurgicalAssemblyPersistence.finiteVector(originOffset))
				return null;
			for (Map.Entry<Integer, Vec3> entry : cubeOffsets.entrySet()) {
				Integer cube = entry.getKey();
				if (cube == null || cube < 0 || cube >= cubeCount || !presentCubes.get(cube)
					|| !SurgicalAssemblyPersistence.finiteVector(entry.getValue()))
					return null;
			}
			for (Map.Entry<Integer, SurgicalCubeRotation> entry : cubeRotations.entrySet()) {
				Integer cube = entry.getKey();
				if (cube == null || cube < 0 || cube >= cubeCount || !presentCubes.get(cube)
					|| entry.getValue() == null)
					return null;
			}
			Map<Integer, Vec3> sanitized = SurgicalAssemblyPersistence.sanitizeOffsets(cubeOffsets, cubeCount, presentCubes);
			Map<Integer, SurgicalCubeRotation> sanitizedRotations = SurgicalAssemblyPersistence.sanitizeRotations(cubeRotations,
				cubeCount, presentCubes);
			Source source = new Source(profile, donorId, cubeCount, presentCubes, anatomy, seams, cutSeams, cutOrder, facing,
				layPose,
				originOffset, sanitized, sanitizedRotations);
			return source.valid() ? source : null;
		}

		private boolean valid() {
			if (profile == null || !validTopology(cubeCount, seams) || presentCubes.isEmpty()
				|| layPose == null || !layPose.valid() || !SurgicalAssemblyPersistence.finiteVector(originOffset))
				return false;
			for (Map.Entry<Integer, Vec3> entry : cubeOffsets.entrySet())
				if (entry.getKey() == null || !containsCube(entry.getKey()) || !SurgicalAssemblyPersistence.finiteVector(entry.getValue()))
					return false;
			for (Map.Entry<Integer, SurgicalCubeRotation> entry : cubeRotations.entrySet())
				if (entry.getKey() == null || !containsCube(entry.getKey()) || entry.getValue() == null)
					return false;
			return true;
		}

		private Source copy() {
			return new Source(profile, donorId, cubeCount, presentCubes, anatomy, seams, cutSeams, cutOrder, facing, layPose,
				originOffset, cubeOffsets, cubeRotations);
		}

		public MimicProfile profile() { return profile; }
		public boolean isWholePrey() {
			return profile.isDevouredForm() && (!profile.hasNativePreyCubes()
				|| cutSeams.isEmpty() && presentCubes.cardinality() == cubeCount);
		}
		public UUID donorId() { return donorId; }
		public int cubeCount() { return cubeCount; }
		public BitSet presentCubes() { return (BitSet) presentCubes.clone(); }
		public BionicAnatomySnapshot anatomy() { return anatomy; }
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

		CompoundTag save(SurgicalStorage.Writer storage) {
			CompoundTag tag = new CompoundTag();
			SurgicalStorage.writeDonor(tag, storage, profile, cubeCount, seams,
				anatomy);
			tag.putUUID(DONOR_ID_TAG, donorId);
			tag.putLongArray(PRESENT_CUBES_TAG, presentCubes.toLongArray());
			if (!cutSeams.isEmpty())
				tag.putLongArray(CUT_SEAMS_TAG, cutSeams.toLongArray());
			if (!cutOrder.isEmpty())
				tag.putIntArray(CUT_ORDER_TAG, cutOrder.stream().mapToInt(Integer::intValue).toArray());
			tag.putInt(FACING_TAG, facing.get3DDataValue());
			tag.put(LAY_POSE_TAG, SurgicalAssemblyPersistence.writeLayPose(layPose));
			if (!originOffset.equals(Vec3.ZERO)) {
				tag.putDouble(ORIGIN_X_TAG, originOffset.x);
				tag.putDouble(ORIGIN_Y_TAG, originOffset.y);
				tag.putDouble(ORIGIN_Z_TAG, originOffset.z);
			}
			SurgicalAssemblyPersistence.writeOffsets(tag, cubeOffsets);
			SurgicalAssemblyPersistence.writeRotations(tag, cubeRotations);
			return tag;
		}

		@Nullable
		static Source load(CompoundTag tag, SurgicalStorage.Reader storage) {
			SurgicalStorage.Donor donor = SurgicalStorage.readDonor(tag, storage);
			if (donor == null || !tag.contains(PRESENT_CUBES_TAG, Tag.TAG_LONG_ARRAY)
				|| !tag.contains(FACING_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(LAY_POSE_TAG, Tag.TAG_COMPOUND))
				return null;
			if (SurgicalAssemblyPersistence.hasWrongType(tag, CUT_SEAMS_TAG, Tag.TAG_LONG_ARRAY)
				|| SurgicalAssemblyPersistence.hasWrongType(tag, CUT_ORDER_TAG, Tag.TAG_INT_ARRAY))
				return null;
			MimicProfile profile = donor.profile();
			int cubeCount = donor.cubeCount();
			List<Seam> seams = donor.seams();
			BitSet present = BitSet.valueOf(tag.getLongArray(PRESENT_CUBES_TAG));
			BitSet cuts = tag.contains(CUT_SEAMS_TAG, Tag.TAG_LONG_ARRAY)
				? BitSet.valueOf(tag.getLongArray(CUT_SEAMS_TAG)) : new BitSet();
			BionicAnatomySnapshot anatomy = donor.anatomy();
			UUID donorId = tag.hasUUID(DONOR_ID_TAG) ? tag.getUUID(DONOR_ID_TAG) : UUID.randomUUID();
			List<Integer> order = tag.contains(CUT_ORDER_TAG, Tag.TAG_INT_ARRAY)
				? SurgicalAssemblyPersistence.decodeCutOrder(tag.getIntArray(CUT_ORDER_TAG)) : cuts.isEmpty() ? List.of() : null;
			if (order == null || !SurgicalAssemblyPersistence.normalizeCutOrder(order, cuts, seams.size()).equals(order)
				|| present.isEmpty() || present.length() > cubeCount || cuts.length() > seams.size())
				return null;
			Direction facing = Direction.from3DDataValue(tag.getInt(FACING_TAG));
			SurgicalLayPose layPose = SurgicalAssemblyPersistence.readLayPose(tag.getCompound(LAY_POSE_TAG));
			if (!facing.getAxis().isHorizontal() || layPose == null)
				return null;
			boolean hasOrigin = tag.contains(ORIGIN_X_TAG) || tag.contains(ORIGIN_Y_TAG)
				|| tag.contains(ORIGIN_Z_TAG);
			if (hasOrigin && (!tag.contains(ORIGIN_X_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(ORIGIN_Y_TAG, Tag.TAG_ANY_NUMERIC)
				|| !tag.contains(ORIGIN_Z_TAG, Tag.TAG_ANY_NUMERIC)))
				return null;
			Vec3 origin = hasOrigin
				? new Vec3(tag.getDouble(ORIGIN_X_TAG), tag.getDouble(ORIGIN_Y_TAG), tag.getDouble(ORIGIN_Z_TAG))
				: Vec3.ZERO;
			Map<Integer, Vec3> offsets = SurgicalAssemblyPersistence.readOffsets(tag, cubeCount, present);
			Map<Integer, SurgicalCubeRotation> rotations = SurgicalAssemblyPersistence.readRotations(tag, cubeCount, present);
			if (offsets == null || rotations == null)
				return null;
			return create(profile, donorId, cubeCount, present, anatomy,
				seams, cuts, order, facing, layPose, origin,
				offsets, rotations);
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

		boolean matches(int source, int cube) {
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
}
