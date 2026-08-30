package com.nobodiiiii.createbiotech.entity.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalConnectionGraph;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalModelRenderContext;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Arm;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.AttackStyle;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Bone;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Context;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.LegStyle;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Pose;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Rotation;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Drives a stitched body from its installed anatomical joints.
 *
 * <p>The captured source geometry stays a still frame. Every joint instead contributes a rigid
 * rotation of one cube group around a pivot, expressed through the per-cube offset and rotation
 * maps the surgical render path already understands, so no part of the capture pipeline has to run
 * again per frame. Animation sampling lives in {@link SlimeBionicAnimations}; this class only
 * resolves geometry and retargets the sampled bone hierarchy onto the installed joints.</p>
 */
public final class SlimeBionicAnimator {
	private static final int AXIS_X = 0;
	private static final int AXIS_Y = 1;
	private static final int AXIS_Z = 2;
	private static final double GEOMETRY_EPSILON = 1.0e-10d;
	private static final double VOLUME_OVERLAP_EPSILON = 1.0e-7d;
	/** Includes SpiderModel's outer legs, whose rest pose is exactly 45 degrees from horizontal. */
	private static final double SPIDER_HORIZONTAL_TO_VERTICAL_RATIO = 0.75d;
	private static final int ARM_VOLUME_SAMPLES_PER_BOX = 64;
	private static final int MAX_ARM_VOLUME_SAMPLE_POINTS = 32_768;
	private static final long MAX_ARM_VOLUME_COVERAGE_TESTS = 8_000_000L;
	/**
	 * Connection graphs keyed by the immutable assembly they describe. Weak so a despawned body's
	 * graph is collected with it.
	 */
	private static final Map<SurgicalAssembly, SurgicalConnectionGraph<Integer>> CONNECTION_GRAPHS =
		java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
	private static final double PRINCIPAL_AXIS_SHARE = 0.55d;
	private static final Basis BODY_SPACE = Basis.bodySpace();

	private SlimeBionicAnimator() {}

	public static void clearCache() {
		SlimeBionicAnimations.clearCache();
	}

	/**
	 * Reduces one source's captured cubes to what the joint maths needs.
	 *
	 * <p>Snapshots are measured in the body's yaw-zero render frame. Keeping this rest geometry out
	 * of world space makes the selected side, hinge and animation axes stable while the creature
	 * turns.</p>
	 */
	public static Map<Integer, CubeBox> measure(SurgicalModelRenderContext.Snapshot snapshot) {
		Map<Integer, CubeBox> boxes = new HashMap<>();
		for (SurgicalModelRenderContext.CubeGeometry cube : snapshot.cubes()) {
			CubeBox box = CubeBox.of(cube.corners(), BODY_SPACE);
			if (box != null)
				boxes.put(cube.cubeId(), box);
		}
		return Map.copyOf(boxes);
	}

	/** Bakes immutable arm dimensions for both articulated and single-piece arms. */
	@Nullable
	public static SurgicalAssembly.AttackGeometry bakeAttackGeometry(SurgicalAssembly assembly,
		List<SourceState> sources, Vec3 bodyOrigin) {
		if (assembly == null || sources == null || bodyOrigin == null
			|| sources.size() != assembly.sources().size())
			return null;
		List<ResolvedLimb> limbs = resolveLimbs(assembly, sources);
		if (limbs.isEmpty())
			return null;
		SurgicalAssembly.ArmAttackGeometry right = bakeArm(limbs, sources, bodyOrigin,
			Bone.RIGHT_SHOULDER, Bone.RIGHT_ELBOW);
		SurgicalAssembly.ArmAttackGeometry left = bakeArm(limbs, sources, bodyOrigin,
			Bone.LEFT_SHOULDER, Bone.LEFT_ELBOW);
		return SurgicalAssembly.AttackGeometry.create(right, left);
	}

	@Nullable
	private static SurgicalAssembly.ArmAttackGeometry bakeArm(List<ResolvedLimb> limbs,
		List<SourceState> sources, Vec3 bodyOrigin, Bone shoulderBone, Bone elbowBone) {
		int shoulderIndex = -1;
		int elbowIndex = -1;
		for (int index = 0; index < limbs.size(); index++) {
			if (limbs.get(index).type() == SurgicalLimbType.SHOULDER
				&& limbs.get(index).bone() == shoulderBone)
				shoulderIndex = index;
			if (limbs.get(index).type() == SurgicalLimbType.ELBOW
				&& limbs.get(index).bone() == elbowBone)
				elbowIndex = index;
		}
		if (shoulderIndex < 0 && elbowIndex < 0)
			return null;
		ResolvedLimb distal = limbs.get(elbowIndex >= 0 ? elbowIndex : shoulderIndex);
		TipGeometry tip = distalTip(distal, sources);
		if (tip == null)
			return null;
		Vec3 attackOrigin = shoulderIndex >= 0 ? limbs.get(shoulderIndex).pivot() : distal.pivot();
		double boneLength = elbowIndex >= 0 && shoulderIndex >= 0
			? attackOrigin.distanceTo(distal.pivot()) + distal.pivot().distanceTo(tip.center())
			: attackOrigin.distanceTo(tip.center());
		float reach = (float) boneLength + tip.radius();
		float minimumY = (float) Math.min(attackOrigin.y,
			Math.min(distal.pivot().y, tip.center().y)) - tip.radius();
		float maximumY = (float) Math.max(attackOrigin.y,
			Math.max(distal.pivot().y, tip.center().y)) + tip.radius();
		float volume = armVolume(limbs, sources, shoulderIndex, elbowIndex);
		return SurgicalAssembly.ArmAttackGeometry.create(attackOrigin.subtract(bodyOrigin), reach,
			minimumY - (float) bodyOrigin.y, maximumY - (float) bodyOrigin.y, tip.radius(), volume);
	}

	/** Coverage-weighted union of the distinct cuboids driven by this arm's joints. */
	private static float armVolume(List<ResolvedLimb> limbs, List<SourceState> sources,
		int shoulderIndex, int elbowIndex) {
		Set<Member> members = new HashSet<>();
		if (shoulderIndex >= 0)
			members.addAll(limbs.get(shoulderIndex).members());
		if (elbowIndex >= 0)
			members.addAll(limbs.get(elbowIndex).members());
		List<VolumeBox> boxes = new ArrayList<>(members.size());
		for (Member member : members) {
			CubeBox box = box(sources, member);
			VolumeBox volumeBox = box == null ? null : VolumeBox.of(box);
			if (volumeBox != null)
				boxes.add(volumeBox);
		}
		double volume = unionVolume(boxes);
		return (float) Mth.clamp(volume, 0.0d,
			SurgicalAssembly.MAX_BODY_SIZE * SurgicalAssembly.MAX_BODY_SIZE
				* SurgicalAssembly.MAX_BODY_SIZE);
	}

	/**
	 * Adds isolated cuboids exactly and samples only broad-phase overlap components. For a sampled
	 * point covered by {@code n} cuboids, its source cuboid contributes {@code 1 / n}; summing every
	 * source therefore estimates the geometric union instead of counting overlap repeatedly.
	 */
	private static double unionVolume(List<VolumeBox> boxes) {
		if (boxes.isEmpty())
			return 0.0d;
		int[] parents = new int[boxes.size()];
		for (int index = 0; index < parents.length; index++)
			parents[index] = index;
		for (int first = 0; first < boxes.size(); first++)
			for (int second = first + 1; second < boxes.size(); second++)
				if (boxes.get(first).overlapsEnvelope(boxes.get(second)))
					union(parents, first, second);

		List<List<Integer>> components = new ArrayList<>(boxes.size());
		for (int index = 0; index < boxes.size(); index++)
			components.add(new ArrayList<>());
		for (int index = 0; index < boxes.size(); index++)
			components.get(findRoot(parents, index)).add(index);
		int sampledBoxes = 0;
		for (List<Integer> component : components)
			if (component.size() > 1)
				sampledBoxes += component.size();

		double volume = 0.0d;
		for (List<Integer> component : components) {
			if (component.isEmpty())
				continue;
			if (component.size() == 1) {
				volume += boxes.get(component.getFirst()).volume();
				continue;
			}
			volume += sampledUnionVolume(boxes, component, sampledBoxes);
		}
		return volume;
	}

	private static double sampledUnionVolume(List<VolumeBox> boxes, List<Integer> component,
		int sampledBoxes) {
		int pointBudget = Math.max(1, MAX_ARM_VOLUME_SAMPLE_POINTS / Math.max(1, sampledBoxes));
		long componentPairs = (long) component.size() * component.size();
		int coverageBudget = (int) Math.max(1L,
			MAX_ARM_VOLUME_COVERAGE_TESTS / Math.max(1L, componentPairs));
		int samplesPerBox = Math.min(ARM_VOLUME_SAMPLES_PER_BOX,
			Math.min(pointBudget, coverageBudget));
		double volume = 0.0d;
		for (int sourceIndex : component) {
			VolumeBox source = boxes.get(sourceIndex);
			double sampleWeight = source.volume() / samplesPerBox;
			for (int sample = 0; sample < samplesPerBox; sample++) {
				Vec3 point = source.sample(sample);
				int coverage = 0;
				for (int candidateIndex : component)
					if (boxes.get(candidateIndex).contains(point))
						coverage++;
				volume += sampleWeight / Math.max(1, coverage);
			}
		}
		return volume;
	}

	/** Low-discrepancy coordinate in {@code (0, 1)} for deterministic interior sampling. */
	private static double halton(int index, int base) {
		double fraction = 1.0d;
		double result = 0.0d;
		while (index > 0) {
			fraction /= base;
			result += fraction * (index % base);
			index /= base;
		}
		return result;
	}

	private static void union(int[] parents, int first, int second) {
		int firstRoot = findRoot(parents, first);
		int secondRoot = findRoot(parents, second);
		if (firstRoot != secondRoot)
			parents[secondRoot] = firstRoot;
	}

	private static int findRoot(int[] parents, int index) {
		int root = index;
		while (parents[root] != root)
			root = parents[root];
		while (parents[index] != index) {
			int next = parents[index];
			parents[index] = root;
			index = next;
		}
		return root;
	}

	@Nullable
	private static TipGeometry distalTip(ResolvedLimb limb, List<SourceState> sources) {
		Vec3 center = groupCenter(limb.members(), sources);
		if (center == null)
			return null;
		Vec3 direction = center.subtract(limb.pivot());
		if (direction.lengthSqr() < GEOMETRY_EPSILON)
			return null;
		direction = direction.normalize();
		double minimum = Double.POSITIVE_INFINITY;
		double maximum = Double.NEGATIVE_INFINITY;
		List<Vec3> points = new ArrayList<>();
		for (Member member : limb.members()) {
			CubeBox box = box(sources, member);
			if (box == null)
				continue;
			for (Vec3 point : box.points()) {
				points.add(point);
				double projection = point.subtract(limb.pivot()).dot(direction);
				minimum = Math.min(minimum, projection);
				maximum = Math.max(maximum, projection);
			}
		}
		if (points.isEmpty() || !Double.isFinite(minimum) || !Double.isFinite(maximum))
			return null;
		// An irregular multi-cube forearm may have only one mathematically furthest corner. Treat its
		// distal tenth as the hand so the baked radius remains representative instead of collapsing.
		double tolerance = Math.max(1.0e-5d, (maximum - minimum) * 0.10d);
		Vec3 sum = Vec3.ZERO;
		int tipPoints = 0;
		for (Vec3 point : points)
			if (maximum - point.subtract(limb.pivot()).dot(direction) <= tolerance) {
				sum = sum.add(point);
				tipPoints++;
			}
		if (tipPoints == 0)
			return null;
		Vec3 tipCenter = sum.scale(1.0d / tipPoints);
		double radius = 0.0d;
		for (Vec3 point : points)
			if (maximum - point.subtract(limb.pivot()).dot(direction) <= tolerance)
				radius = Math.max(radius, point.distanceTo(tipCenter));
		return new TipGeometry(tipCenter, (float) Mth.clamp(radius, 0.05d, 8.0d));
	}

	/**
	 * Resolves one animation frame into per-source cube transforms.
	 *
	 * <p>{@code sources} is indexed like {@link SurgicalAssembly#sources()}; each returned frame only
	 * contains the cubes an installed joint actually moves.</p>
	 */
	/**
	 * The limb solve and the leg-length curve derived from it. Both read only the assembly and the
	 * rest-pose sources - never the entity, the pose or the partial tick - yet they ran on every
	 * frame, and the pivot search behind them is a fixed 16-iteration power method per limb plus a
	 * second one per shoulder and hip. The renderer holds one of these for as long as the body is
	 * unchanged, which is exactly how long it stays valid.
	 */
	public static final class Rig {
		private final SurgicalAssembly assembly;
		private final List<SourceState> sources;
		private final List<ResolvedLimb> limbs;
		private final float legLength;

		private Rig(SurgicalAssembly assembly, List<SourceState> sources, List<ResolvedLimb> limbs,
			float legLength) {
			this.assembly = assembly;
			this.sources = sources;
			this.limbs = limbs;
			this.legLength = legLength;
		}

		private boolean matches(SurgicalAssembly otherAssembly, List<SourceState> otherSources) {
			return assembly == otherAssembly && sources == otherSources;
		}

		public float legLength() {
			return legLength;
		}
	}

	public static Rig rig(SurgicalAssembly assembly, List<SourceState> sources) {
		List<ResolvedLimb> limbs = resolveLimbs(assembly, sources);
		return new Rig(assembly, sources, limbs, effectiveLegLength(limbs, sources));
	}

	public static List<Frame> resolve(SlimeBionicEntity entity, SurgicalAssembly assembly,
		List<SourceState> sources, @Nullable Rig rig, float partialTick) {
		int sourceCount = assembly.sources().size();
		List<Frame> frames = new ArrayList<>(sourceCount);
		for (int source = 0; source < sourceCount; source++)
			frames.add(Frame.EMPTY);
		if (assembly.effectiveLimbs().isEmpty() || sources.size() != sourceCount)
			return frames;
		Rig resolved = rig != null && rig.matches(assembly, sources) ? rig : rig(assembly, sources);
		List<ResolvedLimb> limbs = resolved.limbs;
		if (limbs.isEmpty())
			return frames;
		boolean weaponAttack = entity.isAttackAnimationWeapon();
		Arm preferredAttackArm = entity.isAttackAnimationLeft() ? Arm.LEFT : Arm.RIGHT;
		Context context = animationContext(entity, partialTick, resolved.legLength,
			attackArm(limbs, preferredAttackArm),
			weaponAttack ? AttackStyle.WEAPON : AttackStyle.EMPTY_HAND);
		Pose pose = SlimeBionicAnimations.sample(context);

		Map<Integer, Map<Integer, Vec3>> offsets = new HashMap<>();
		Map<Integer, Map<Integer, SurgicalCubeRotation>> rotations = new HashMap<>();
		Rotation bodyPose = pose.rotation(Bone.BODY);
		SurgicalCubeRotation bodyRotation = BODY_SPACE.reframe(SurgicalCubeRotation.IDENTITY,
			bodyPose.z(), bodyPose.y(), bodyPose.x());
		Transform bodyTransform = Transform.IDENTITY.rotateAround(bodyPivot(sources), bodyRotation);
		if (!bodyTransform.isIdentity()) {
			Set<Member> legMembers = legMembers(limbs);
			for (int source = 0; source < sources.size(); source++)
				for (int cube : sources.get(source).boxes().keySet()) {
					Member member = new Member(source, cube);
					if (!legMembers.contains(member))
						applyTransform(member, bodyTransform, sources, offsets, rotations);
				}
		}
		Transform[] transforms = new Transform[limbs.size()];
		boolean[] resolving = new boolean[limbs.size()];
		Map<Member, Integer> appliedDepths = new HashMap<>();
		for (int limbIndex = 0; limbIndex < limbs.size(); limbIndex++) {
			ResolvedLimb limb = limbs.get(limbIndex);
			if (limb.bone() == null && limb.gait() == null)
				continue;
			Transform transform = resolveTransform(limbIndex, limbs, pose, context, bodyTransform,
				transforms, resolving);
			if (transform.isIdentity())
				continue;
			int depth = hierarchyDepth(limbIndex, limbs);
			for (Member member : limb.members()) {
				Integer appliedDepth = appliedDepths.get(member);
				if (appliedDepth != null && appliedDepth > depth)
					continue;
				applyTransform(member, transform, sources, offsets, rotations);
				appliedDepths.put(member, depth);
			}
		}
		for (int source = 0; source < sourceCount; source++) {
			Map<Integer, Vec3> sourceOffsets = offsets.get(source);
			Map<Integer, SurgicalCubeRotation> sourceRotations = rotations.get(source);
			if (sourceOffsets != null || sourceRotations != null)
				frames.set(source, new Frame(
					sourceOffsets == null ? Map.of() : Map.copyOf(sourceOffsets),
					sourceRotations == null ? Map.of() : Map.copyOf(sourceRotations)));
		}
		return frames;
	}

	/** Ender Golem's torso twists above planted legs, so hip and knee groups stay outside this set. */
	private static Set<Member> legMembers(List<ResolvedLimb> limbs) {
		Set<Member> members = new HashSet<>();
		for (ResolvedLimb limb : limbs)
			if (limb.type() == SurgicalLimbType.HIP || limb.type() == SurgicalLimbType.KNEE)
				members.addAll(limb.members());
		return members;
	}

	private static void applyTransform(Member member, Transform transform, List<SourceState> sources,
		Map<Integer, Map<Integer, Vec3>> offsets,
		Map<Integer, Map<Integer, SurgicalCubeRotation>> rotations) {
		if (member.source() < 0 || member.source() >= sources.size())
			return;
		SourceState state = sources.get(member.source());
		CubeBox box = state.boxes().get(member.cube());
		if (box == null)
			return;
		// The measured centre already includes its static offset. The animation frame therefore
		// contributes only the hierarchical centre delta plus the inherited final orientation.
		Vec3 staticOffset = state.offsets().getOrDefault(member.cube(), Vec3.ZERO);
		SurgicalCubeRotation staticRotation = state.rotations()
			.getOrDefault(member.cube(), SurgicalCubeRotation.IDENTITY);
		offsets.computeIfAbsent(member.source(), ignored -> new HashMap<>())
			.put(member.cube(), staticOffset.add(transform.apply(box.center()).subtract(box.center())));
		rotations.computeIfAbsent(member.source(), ignored -> new HashMap<>())
			.put(member.cube(), staticRotation.then(transform.rotation()));
	}

	/**
	 * Groups every limb with the cubes that move as one part and works out where it hinges.
	 *
	 * <p>Honey combinations still move as one rigid part. Their physical hinge comes only from the
	 * two cubes that really share the boundary seam or glue joint, while the pose direction follows
	 * the centre of the complete driven group. That distinction matters for an asymmetric limb whose
	 * selected connection cube lies on the opposite side of the hinge from most of its visible mass.</p>
	 */
	private static List<ResolvedLimb> resolveLimbs(SurgicalAssembly assembly, List<SourceState> sources) {
		SurgicalConnectionGraph<Integer> connections = connectionGraph(assembly);
		if (connections == null)
			return List.of();
		double bodyCenterX = bodyCenter(sources, AXIS_X);
		List<LimbGeometry> geometries = new ArrayList<>();
		for (SurgicalAssembly.Limb limb : assembly.effectiveLimbs()) {
			Member selectedChild = new Member(limb.childSource(), limb.childCube());
			Member selectedParent = new Member(limb.parentSource(), limb.parentCube());
			List<Member> childMembers = group(assembly, selectedChild.source(), selectedChild.cube());
			List<Member> parentMembers = group(assembly, selectedParent.source(), selectedParent.cube());
			Connection connection = connection(connections, selectedChild, selectedParent,
				childMembers, parentMembers);
			if (connection == null)
				continue;
			CubeBox child = box(sources, connection.child());
			CubeBox parent = box(sources, connection.parent());
			if (child == null || parent == null)
				continue;
			geometries.add(new LimbGeometry(limb.type(), childMembers, connection.parent(), child, parent));
		}

		List<ResolvedLimb> resolved = new ArrayList<>(geometries.size());
		for (LimbGeometry geometry : geometries) {
			Vec3 pivot = pivot(geometry.type(), geometry.child(), geometry.parentBox(),
				secondaryPivot(geometry, geometries));
			Vec3 drivenCenter = groupCenter(geometry.members(), sources);
			Vec3 restDirection = (drivenCenter == null ? geometry.child().center() : drivenCenter)
				.subtract(pivot);
			if (restDirection.lengthSqr() < GEOMETRY_EPSILON)
				continue;
			resolved.add(new ResolvedLimb(geometry.type(), geometry.members(), geometry.parent(), pivot,
				restDirection,
				BODY_SPACE.project(geometry.child().center(), AXIS_X) - bodyCenterX,
				BODY_SPACE.project(geometry.child().center(), AXIS_Z),
				BODY_SPACE.restAlignment(geometry.type(), restDirection), null, -1, null));
		}
		return assignLegGaits(linkHierarchy(assignBones(resolved)));
	}

	/** Finds the elbow/knee that hangs from this upper limb and resolves its physical hinge. */
	@Nullable
	private static Vec3 secondaryPivot(LimbGeometry primary, List<LimbGeometry> geometries) {
		SurgicalLimbType secondaryType = switch (primary.type()) {
		case SHOULDER -> SurgicalLimbType.ELBOW;
		case HIP -> SurgicalLimbType.KNEE;
		default -> null;
		};
		if (secondaryType == null)
			return null;
		for (LimbGeometry candidate : geometries)
			if (candidate.type() == secondaryType && primary.members().contains(candidate.parent()))
				return pivot(candidate.type(), candidate.child(), candidate.parentBox(), null);
		return null;
	}

	/** Assigns stable left/right animation channels independently at every anatomical level. */
	private static List<ResolvedLimb> assignBones(List<ResolvedLimb> limbs) {
		List<ResolvedLimb> assigned = new ArrayList<>(limbs);
		Map<SurgicalLimbType, List<Integer>> byType = new EnumMap<>(SurgicalLimbType.class);
		for (int index = 0; index < limbs.size(); index++)
			byType.computeIfAbsent(limbs.get(index).type(), ignored -> new ArrayList<>()).add(index);
		byType.forEach((type, indices) -> {
			indices.sort((first, second) -> Double.compare(limbs.get(first).side(), limbs.get(second).side()));
			if (type == SurgicalLimbType.HIP || type == SurgicalLimbType.KNEE)
				return;
			if (type == SurgicalLimbType.NECK) {
				if (!indices.isEmpty()) {
					int index = indices.getFirst();
					assigned.set(index, assigned.get(index).withBone(Bone.HEAD));
				}
				return;
			}
			if (indices.size() == 1) {
				int index = indices.getFirst();
				assigned.set(index, assigned.get(index).withBone(bone(type, limbs.get(index).side() > 0.0d)));
				return;
			}
			for (int slot = 0; slot < indices.size() && slot < 2; slot++) {
				int index = indices.get(slot);
				assigned.set(index, assigned.get(index).withBone(bone(type, slot == 1)));
			}
		});
		return List.copyOf(assigned);
	}

	@Nullable
	private static Bone bone(SurgicalLimbType type, boolean left) {
		return switch (type) {
		case SHOULDER -> left ? Bone.LEFT_SHOULDER : Bone.RIGHT_SHOULDER;
		case ELBOW -> left ? Bone.LEFT_ELBOW : Bone.RIGHT_ELBOW;
		case HIP, KNEE -> null;
		case NECK -> Bone.HEAD;
		};
	}

	/**
	 * Connects lower bones to the effective matching upper joint whose automatically owned cube or
	 * honey combination contains their parent endpoint. Unmatched elbows and knees remain in the assembly
	 * but filtered out before this stage.
	 */
	private static List<ResolvedLimb> linkHierarchy(List<ResolvedLimb> limbs) {
		List<ResolvedLimb> linked = new ArrayList<>(limbs);
		for (int index = 0; index < limbs.size(); index++) {
			ResolvedLimb limb = limbs.get(index);
			SurgicalLimbType parentType = switch (limb.type()) {
			case ELBOW -> SurgicalLimbType.SHOULDER;
			case KNEE -> SurgicalLimbType.HIP;
			default -> null;
			};
			if (parentType == null)
				continue;
			for (int candidateIndex = 0; candidateIndex < limbs.size(); candidateIndex++) {
				ResolvedLimb candidate = limbs.get(candidateIndex);
				if (candidate.type() == parentType && candidate.members().contains(limb.parent())) {
					linked.set(index, limb.withBone(childBone(limb.type(), candidate.bone()))
						.withParent(candidateIndex));
					break;
				}
			}
		}
		return List.copyOf(linked);
	}

	@Nullable
	private static Bone childBone(SurgicalLimbType type, @Nullable Bone parent) {
		if (type == SurgicalLimbType.ELBOW)
			return parent == Bone.LEFT_SHOULDER ? Bone.LEFT_ELBOW : Bone.RIGHT_ELBOW;
		if (type == SurgicalLimbType.KNEE)
			return null;
		return parent;
	}

	/**
	 * Classifies and phases every resolved hip independently. A mostly downward upper leg retains the
	 * existing humanoid pendulum gait; a mostly horizontal upper leg uses a SpiderModel-style gait.
	 * Separating the two lists is what lets one body carry both kinds without an unrelated spider leg
	 * changing the phase assignment of its humanoid legs.
	 */
	private static List<ResolvedLimb> assignLegGaits(List<ResolvedLimb> limbs) {
		List<Integer> hips = new ArrayList<>();
		for (int index = 0; index < limbs.size(); index++)
			if (limbs.get(index).type() == SurgicalLimbType.HIP)
				hips.add(index);
		if (hips.size() < 2)
			return limbs;
		List<Integer> humanoidHips = new ArrayList<>();
		List<Integer> spiderHips = new ArrayList<>();
		for (int index : hips) {
			ResolvedLimb hip = limbs.get(index);
			(legStyle(hip) == LegStyle.SPIDER ? spiderHips : humanoidHips).add(index);
		}

		List<ResolvedLimb> assigned = new ArrayList<>(limbs);
		assignHumanoidGaits(assigned, limbs, humanoidHips);
		assignSpiderGaits(assigned, limbs, spiderHips);

		// A knee is a child-local hinge. It must inherit the exact style, side and phase of the hip
		// whose rotating group owns its parent endpoint, especially on a mixed humanoid/spider body.
		for (int index = 0; index < assigned.size(); index++) {
			ResolvedLimb limb = assigned.get(index);
			if (limb.type() != SurgicalLimbType.KNEE || limb.parentIndex() < 0)
				continue;
			ResolvedLimb parent = assigned.get(limb.parentIndex());
			if (parent.type() == SurgicalLimbType.HIP && parent.gait() != null)
				assigned.set(index, limb.withGait(parent.gait()));
		}
		return List.copyOf(assigned);
	}

	/**
	 * A pronounced sideways reach marks a spider leg. The tolerance deliberately includes the
	 * vanilla spider's 45-degree front and hind legs without catching ordinarily hanging legs.
	 */
	private static LegStyle legStyle(ResolvedLimb hip) {
		Vec3 direction = hip.restDirection();
		double vertical = BODY_SPACE.project(direction, AXIS_Y);
		double lateral = BODY_SPACE.project(direction, AXIS_X);
		double longitudinal = BODY_SPACE.project(direction, AXIS_Z);
		double minimumHorizontal = vertical * SPIDER_HORIZONTAL_TO_VERTICAL_RATIO;
		return lateral * lateral + longitudinal * longitudinal
			>= minimumHorizontal * minimumHorizontal
			? LegStyle.SPIDER : LegStyle.HUMANOID;
	}

	/** The pre-existing gait assignment, applied only to this body's humanoid-like hips. */
	private static void assignHumanoidGaits(List<ResolvedLimb> assigned,
		List<ResolvedLimb> limbs, List<Integer> hips) {
		if (hips.isEmpty())
			return;

		java.util.Comparator<Integer> frontToBack = java.util.Comparator
			.comparingDouble((Integer index) -> limbs.get(index).longitudinal())
			.thenComparingDouble(index -> limbs.get(index).side())
			.thenComparingInt(Integer::intValue);
		List<Integer> right = new ArrayList<>();
		List<Integer> left = new ArrayList<>();
		List<Integer> center = new ArrayList<>();
		for (int index : hips) {
			double side = limbs.get(index).side();
			if (side < -GEOMETRY_EPSILON)
				right.add(index);
			else if (side > GEOMETRY_EPSILON)
				left.add(index);
			else
				center.add(index);
		}
		center.sort(frontToBack);
		for (int index : center) {
			if (right.size() < left.size())
				right.add(index);
			else
				left.add(index);
		}
		right.sort(frontToBack);
		left.sort(frontToBack);

		if ((hips.size() & 1) == 0) {
			assignAlternatingHumanoidSide(assigned, right, false);
			assignAlternatingHumanoidSide(assigned, left, true);
		} else {
			List<Integer> perimeter = new ArrayList<>(hips.size());
			perimeter.addAll(right);
			for (int index = left.size() - 1; index >= 0; index--)
				perimeter.add(left.get(index));
			float phaseStep = Mth.TWO_PI / hips.size();
			for (int slot = 0; slot < perimeter.size(); slot++) {
				int index = perimeter.get(slot);
				boolean isLeft = left.contains(index);
				assigned.set(index, assigned.get(index)
					.withGait(new GaitChannel(LegStyle.HUMANOID, isLeft, slot,
						phaseStep * slot)));
			}
		}
	}

	/** Spider legs share phases across the body and mirror their lift across its sagittal plane. */
	private static void assignSpiderGaits(List<ResolvedLimb> assigned,
		List<ResolvedLimb> limbs, List<Integer> hips) {
		if (hips.isEmpty())
			return;
		java.util.Comparator<Integer> frontToBack = java.util.Comparator
			.comparingDouble((Integer index) -> limbs.get(index).longitudinal())
			.thenComparingDouble(index -> limbs.get(index).side())
			.thenComparingInt(Integer::intValue);
		List<Integer> right = new ArrayList<>();
		List<Integer> left = new ArrayList<>();
		List<Integer> center = new ArrayList<>();
		for (int index : hips) {
			double side = limbs.get(index).side();
			if (side < -GEOMETRY_EPSILON)
				right.add(index);
			else if (side > GEOMETRY_EPSILON)
				left.add(index);
			else
				center.add(index);
		}
		center.sort(frontToBack);
		for (int index : center) {
			if (right.size() < left.size())
				right.add(index);
			else
				left.add(index);
		}
		right.sort(frontToBack);
		left.sort(frontToBack);
		int rowCount = Math.max(right.size(), left.size());
		assignSpiderSide(assigned, right, false, rowCount);
		assignSpiderSide(assigned, left, true, rowCount);
	}

	private static void assignAlternatingHumanoidSide(List<ResolvedLimb> assigned,
		List<Integer> indices, boolean left) {
		for (int row = 0; row < indices.size(); row++) {
			int index = indices.get(row);
			int group = (row + (left ? 1 : 0)) & 1;
			assigned.set(index, assigned.get(index)
				.withGait(new GaitChannel(LegStyle.HUMANOID, left, row, group * Mth.PI)));
		}
	}

	private static void assignSpiderSide(List<ResolvedLimb> assigned,
		List<Integer> indices, boolean left, int rowCount) {
		for (int row = 0; row < indices.size(); row++) {
			int index = indices.get(row);
			assigned.set(index, assigned.get(index)
				.withGait(new GaitChannel(LegStyle.SPIDER, left, row,
					spiderPhase(row, rowCount))));
		}
	}

	/** The four-row case is the exact front-to-back phase order used by SpiderModel. */
	private static float spiderPhase(int row, int rowCount) {
		if (rowCount == 4)
			return switch (row) {
			case 0 -> Mth.PI * 1.5f;
			case 1 -> Mth.HALF_PI;
			case 2 -> Mth.PI;
			default -> 0.0f;
			};
		return rowCount <= 1 ? 0.0f : Mth.TWO_PI * row / rowCount;
	}

	/** Uses the preferred articulated arm, falling back to the only installed elbow if necessary. */
	private static Arm attackArm(List<ResolvedLimb> limbs, Arm preferred) {
		boolean right = false;
		boolean left = false;
		for (ResolvedLimb limb : limbs) {
			if (limb.type() != SurgicalLimbType.ELBOW)
				continue;
			if (limb.bone() == Bone.RIGHT_ELBOW)
				right = true;
			if (limb.bone() == Bone.LEFT_ELBOW)
				left = true;
		}
		if ((preferred == Arm.LEFT && left) || (preferred == Arm.RIGHT && right))
			return preferred;
		return right ? Arm.RIGHT : left ? Arm.LEFT : Arm.NONE;
	}

	private static Transform resolveTransform(int index, List<ResolvedLimb> limbs, Pose pose,
		Context context, Transform bodyTransform, Transform[] cache, boolean[] resolving) {
		if (cache[index] != null)
			return cache[index];
		if (resolving[index])
			return Transform.IDENTITY;
		resolving[index] = true;
		ResolvedLimb limb = limbs.get(index);
		Transform parent = limb.parentIndex() < 0
			? inheritsBodyRotation(limb) ? bodyTransform : Transform.IDENTITY
			: resolveTransform(limb.parentIndex(), limbs, pose, context, bodyTransform, cache, resolving);
		Rotation sampled = limb.gait() == null ? pose.rotation(limb.bone())
			: SlimeBionicAnimations.sampleLeg(context,
				limb.type() == SurgicalLimbType.KNEE, limb.gait().style(),
				limb.gait().left(), limb.gait().phase());
		SurgicalCubeRotation local = limb.gait() != null
			&& limb.gait().style() == LegStyle.SPIDER && limb.type() == SurgicalLimbType.HIP
				? BODY_SPACE.spiderReframe(limb.restDirection(), limb.gait().left(),
					sampled.z(), sampled.y())
				: BODY_SPACE.reframe(limb.restAlignment(),
					sampled.z(), sampled.y(), sampled.x());
		SurgicalCubeRotation inheritedLocal = conjugate(parent.rotation(), local);
		Transform resolved = parent.rotateAround(parent.apply(limb.pivot()), inheritedLocal);
		resolving[index] = false;
		cache[index] = resolved;
		return resolved;
	}

	private static boolean inheritsBodyRotation(ResolvedLimb limb) {
		return limb.type() != SurgicalLimbType.HIP && limb.type() != SurgicalLimbType.KNEE;
	}

	/** Changes a child-local rotation into the already rotated parent frame. */
	private static SurgicalCubeRotation conjugate(SurgicalCubeRotation parent,
		SurgicalCubeRotation local) {
		if (parent.isIdentity() || local.isIdentity())
			return local;
		SurgicalCubeRotation inverse = new SurgicalCubeRotation(
			-parent.x(), -parent.y(), -parent.z(), parent.w());
		return inverse.then(local).then(parent);
	}

	private static int hierarchyDepth(int index, List<ResolvedLimb> limbs) {
		int depth = 0;
		for (int cursor = limbs.get(index).parentIndex(); cursor >= 0 && depth < limbs.size();
			cursor = limbs.get(cursor).parentIndex())
			depth++;
		return depth;
	}

	/**
	 * Measures the effective leg length used by movement and animation retargeting.
	 *
	 * <p>A vanilla humanoid's hip is twelve model pixels above its sole. For each installed hip we
	 * instead measure from the resolved hinge down to the lowest point of the complete rotating leg
	 * group. Averaging both sides keeps an asymmetric body on one shared alternating gait.</p>
	 */
	public static float effectiveLegLength(SurgicalAssembly assembly, List<SourceState> sources) {
		if (assembly == null || sources == null || sources.size() != assembly.sources().size())
			return 0.0f;
		return effectiveLegLength(resolveLimbs(assembly, sources), sources);
	}

	private static float effectiveLegLength(List<ResolvedLimb> limbs, List<SourceState> sources) {
		double totalLength = 0.0d;
		int measuredLegs = 0;
		for (int hipIndex = 0; hipIndex < limbs.size(); hipIndex++) {
			ResolvedLimb limb = limbs.get(hipIndex);
			if (limb.type() != SurgicalLimbType.HIP)
				continue;
			double pivotHeight = BODY_SPACE.project(limb.pivot(), AXIS_Y);
			double soleHeight = Double.NEGATIVE_INFINITY;
			for (int candidateIndex = 0; candidateIndex < limbs.size(); candidateIndex++)
				if (isDescendantOrSelf(candidateIndex, hipIndex, limbs))
					for (Member member : limbs.get(candidateIndex).members()) {
						CubeBox box = box(sources, member);
						if (box != null)
							soleHeight = Math.max(soleHeight, box.max()[AXIS_Y]);
					}
			double legLength = soleHeight - pivotHeight;
			if (!Double.isFinite(legLength) || legLength <= GEOMETRY_EPSILON)
				continue;
			totalLength += legLength;
			measuredLegs++;
		}
		if (measuredLegs == 0)
			return 0.0f;
		return (float) (totalLength / measuredLegs);
	}

	private static boolean isDescendantOrSelf(int candidate, int ancestor,
		List<ResolvedLimb> limbs) {
		int cursor = candidate;
		for (int depth = 0; cursor >= 0 && depth <= limbs.size(); depth++) {
			if (cursor == ancestor)
				return true;
			cursor = limbs.get(cursor).parentIndex();
		}
		return false;
	}

	/** Centre of the complete rigid part that an installed joint rotates. */
	@Nullable
	private static Vec3 groupCenter(List<Member> members, List<SourceState> sources) {
		Vec3 sum = Vec3.ZERO;
		int count = 0;
		for (Member member : members) {
			CubeBox box = box(sources, member);
			if (box == null)
				continue;
			sum = sum.add(box.center());
			count++;
		}
		return count == 0 ? null : sum.scale(1.0d / count);
	}

	/**
	 * Finds the same geometric centre the renderer later moves onto the entity origin.
	 *
	 * <p>A limb's side belongs to its position in the complete body, not to the direction from an
	 * arbitrarily placed parent cube.</p>
	 */
	private static double bodyCenter(List<SourceState> sources, int axis) {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (SourceState source : sources)
			for (CubeBox box : source.boxes().values()) {
				min = Math.min(min, box.min()[axis]);
				max = Math.max(max, box.max()[axis]);
			}
		return Double.isFinite(min) && Double.isFinite(max) ? (min + max) * 0.5d : 0.0d;
	}

	private static Vec3 bodyPivot(List<SourceState> sources) {
		return BODY_SPACE.point(bodyCenter(sources, AXIS_X), bodyCenter(sources, AXIS_Y),
			bodyCenter(sources, AXIS_Z));
	}

	/** The cubes that rotate with {@code cube}, including automatic joint-owned rigid islands. */
	private static List<Member> group(SurgicalAssembly assembly, int source, int cube) {
		return assembly.rotatingGroup(source, cube).stream()
			.map(member -> new Member(member.source(), member.cube())).toList();
	}

	@Nullable
	private static CubeBox box(List<SourceState> sources, Member member) {
		return member.source() < 0 || member.source() >= sources.size() ? null
			: sources.get(member.source()).boxes().get(member.cube());
	}

	/** Resolves old arbitrary combination endpoints as well as newly stored physical endpoints. */
	@Nullable
	private static Connection connection(SurgicalConnectionGraph<Integer> connections, Member selectedChild,
		Member selectedParent, List<Member> childMembers, List<Member> parentMembers) {
		if (directlyConnected(connections, selectedChild, selectedParent))
			return new Connection(selectedChild, selectedParent);
		for (Member child : childMembers)
			for (Member parent : parentMembers)
				if (directlyConnected(connections, child, parent))
					return new Connection(child, parent);
		return null;
	}

	private static boolean directlyConnected(SurgicalConnectionGraph<Integer> connections,
		Member first, Member second) {
		return connections.directConnections(first.source(), first.cube())
			.contains(second.source(), second.cube());
	}

	@Nullable
	private static SurgicalConnectionGraph<Integer> connectionGraph(SurgicalAssembly assembly) {
		// A packed assembly is immutable, so its connection graph is too. Building it per call put a
		// full graph construction on every rendered frame of every bionic slime — twice, because
		// effectiveLegLength resolves the limbs again.
		SurgicalConnectionGraph<Integer> cached = CONNECTION_GRAPHS.get(assembly);
		if (cached != null)
			return cached;
		List<SurgicalConnectionGraph.Body<Integer>> bodies = new ArrayList<>(assembly.sources().size());
		for (int sourceId = 0; sourceId < assembly.sources().size(); sourceId++) {
			SurgicalAssembly.Source source = assembly.sources().get(sourceId);
			bodies.add(new SurgicalConnectionGraph.Body<>(sourceId, source.cubeCount(), source.presentCubes(),
				source.seams(), source.cutSeams()));
		}
		List<SurgicalConnectionGraph.Link<Integer>> links = assembly.joints().stream()
			.map(joint -> new SurgicalConnectionGraph.Link<>(joint.firstSource(), joint.firstCube(),
				joint.secondSource(), joint.secondCube()))
			.toList();
		SurgicalConnectionGraph<Integer> graph = SurgicalConnectionGraph.create(bodies, links);
		if (graph != null)
			CONNECTION_GRAPHS.put(assembly, graph);
		return graph;
	}

	/**
	 * Places the hinge on the appropriate end of the rotating child.
	 *
	 * <p>Elongated parts use their principal geometric axis, so the calculation follows an arm or leg
	 * after the player lays it flat or points it upward. Cube-like parts use the surface reached by a
	 * ray toward the parent, which is the stable choice for heads and other compact pieces. When a
	 * flush side contact makes both elongated-part ends equally close to the parent, the end farther
	 * from its elbow or knee wins; without that secondary joint, the upper end wins.</p>
	 */
	private static Vec3 pivot(SurgicalLimbType type, CubeBox child, CubeBox parent,
		@Nullable Vec3 secondaryPivot) {
		// Head pitch and yaw must originate at the neck connection itself. Using an endpoint of an
		// elongated or unusually shaped head makes it orbit around its own centre instead of nodding.
		if (type == SurgicalLimbType.NECK)
			return child.contactCenter(parent, BODY_SPACE);
		Vec3 principal = child.principalAxis(BODY_SPACE);
		if (principal == null)
			return child.surfaceToward(parent.center(), type, BODY_SPACE);

		double radius = child.supportRadius(principal);
		Vec3 first = child.center().add(principal.scale(radius));
		Vec3 second = child.center().subtract(principal.scale(radius));
		double firstDistance = parent.distanceToSqr(first, BODY_SPACE);
		double secondDistance = parent.distanceToSqr(second, BODY_SPACE);
		if (Math.abs(firstDistance - secondDistance) > GEOMETRY_EPSILON)
			return firstDistance < secondDistance ? first : second;

		if (secondaryPivot != null) {
			double firstSecondaryDistance = first.distanceToSqr(secondaryPivot);
			double secondSecondaryDistance = second.distanceToSqr(secondaryPivot);
			if (Math.abs(firstSecondaryDistance - secondSecondaryDistance) > GEOMETRY_EPSILON)
				return firstSecondaryDistance > secondSecondaryDistance ? first : second;
		}

		// BODY_SPACE Y follows model-space Y and therefore increases downward.
		double firstY = BODY_SPACE.project(first, AXIS_Y);
		double secondY = BODY_SPACE.project(second, AXIS_Y);
		return firstY <= secondY ? first : second;
	}


	/** Adapts entity state to the animation-only module's narrow, immutable input contract. */
	private static Context animationContext(SlimeBionicEntity entity, float partialTick,
		float legLength, Arm attackArm, AttackStyle attackStyle) {
		float bodyRot = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
		float headRot = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
		float netHeadYaw = Mth.wrapDegrees(headRot - bodyRot);
		float headPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
		float ageInTicks = entity.tickCount + partialTick;
		float limbSwing = 0.0f;
		float limbSwingAmount = 0.0f;
		float walkWeight = 0.0f;
		float vanillaLimbSwing = 0.0f;
		float vanillaLimbSwingAmount = 0.0f;
		if (!entity.isPassenger() && entity.isAlive()) {
			vanillaLimbSwing = entity.walkAnimation.position(partialTick);
			vanillaLimbSwingAmount = Math.min(entity.walkAnimation.speed(partialTick), 1.0f);
			// WalkAnimation.position keeps accumulating at the full movement-derived speed. Capping only
			// the amount therefore converts speed beyond this point into faster steps, not wider swings.
			float maximumAmount = SurgicalGait.maximumHumanoidSwingAmount(legLength);
			limbSwingAmount = Math.min(vanillaLimbSwingAmount, maximumAmount);
			walkWeight = maximumAmount <= 0.0f ? 0.0f : limbSwingAmount / maximumAmount;
			limbSwing = vanillaLimbSwing * SurgicalGait.animationFrequencyScale(legLength);
		}
		return new Context(entity, limbSwing, limbSwingAmount, walkWeight, vanillaLimbSwing,
			vanillaLimbSwingAmount, ageInTicks, netHeadYaw, headPitch,
			entity.getAttackAnim(partialTick), entity.isPassenger(), entity.getSwimAmount(partialTick),
			entity.getAttackAnimationTick(), entity.getAttackAnimationDuration(), partialTick,
			attackArm, attackStyle);
	}

	private record Member(int source, int cube) {}
	private record Connection(Member child, Member parent) {}
	private record LimbGeometry(SurgicalLimbType type, List<Member> members, Member parent,
		CubeBox child, CubeBox parentBox) {}
	private record TipGeometry(Vec3 center, float radius) {}
	private record GaitChannel(LegStyle style, boolean left, int row, float phase) {}

	private record ResolvedLimb(SurgicalLimbType type, List<Member> members, Member parent,
		Vec3 pivot, Vec3 restDirection, double side, double longitudinal,
		SurgicalCubeRotation restAlignment,
		@Nullable Bone bone, int parentIndex, @Nullable GaitChannel gait) {
		private ResolvedLimb withBone(@Nullable Bone bone) {
			return new ResolvedLimb(type, members, parent, pivot, restDirection, side, longitudinal,
				restAlignment, bone, parentIndex, gait);
		}

		private ResolvedLimb withParent(int parentIndex) {
			return new ResolvedLimb(type, members, parent, pivot, restDirection, side, longitudinal,
				restAlignment, bone, parentIndex, gait);
		}

		private ResolvedLimb withGait(GaitChannel gait) {
			return new ResolvedLimb(type, members, parent, pivot, restDirection, side, longitudinal,
				restAlignment, bone, parentIndex, gait);
		}
	}

	/** Precomputed oriented-box basis used only while baking overlap-aware arm volume. */
	private record VolumeBox(Vec3 origin, Vec3 a, Vec3 b, Vec3 c,
		Vec3 reciprocalA, Vec3 reciprocalB, Vec3 reciprocalC,
		double volume, double[] minimum, double[] maximum) {
		@Nullable
		private static VolumeBox of(CubeBox box) {
			if (box.points().size() != 8)
				return null;
			Vec3 origin = box.points().get(0);
			Vec3 a = box.points().get(1).subtract(origin);
			Vec3 b = box.points().get(2).subtract(origin);
			Vec3 c = box.points().get(4).subtract(origin);
			Vec3 bCrossC = b.cross(c);
			double determinant = a.dot(bCrossC);
			if (!Double.isFinite(determinant) || Math.abs(determinant) < GEOMETRY_EPSILON)
				return null;
			double inverseDeterminant = 1.0d / determinant;
			double[] minimum = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
				Double.POSITIVE_INFINITY };
			double[] maximum = { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
				Double.NEGATIVE_INFINITY };
			for (Vec3 point : box.points()) {
				minimum[AXIS_X] = Math.min(minimum[AXIS_X], point.x);
				minimum[AXIS_Y] = Math.min(minimum[AXIS_Y], point.y);
				minimum[AXIS_Z] = Math.min(minimum[AXIS_Z], point.z);
				maximum[AXIS_X] = Math.max(maximum[AXIS_X], point.x);
				maximum[AXIS_Y] = Math.max(maximum[AXIS_Y], point.y);
				maximum[AXIS_Z] = Math.max(maximum[AXIS_Z], point.z);
			}
			return new VolumeBox(origin, a, b, c, bCrossC.scale(inverseDeterminant),
				c.cross(a).scale(inverseDeterminant), a.cross(b).scale(inverseDeterminant),
				Math.abs(determinant), minimum, maximum);
		}

		private boolean overlapsEnvelope(VolumeBox other) {
			for (int axis = AXIS_X; axis <= AXIS_Z; axis++)
				if (Math.min(maximum[axis], other.maximum[axis])
					- Math.max(minimum[axis], other.minimum[axis]) <= VOLUME_OVERLAP_EPSILON)
					return false;
			return true;
		}

		private Vec3 sample(int sample) {
			double u = halton(sample + 1, 2);
			double v = halton(sample + 1, 3);
			double w = halton(sample + 1, 5);
			return new Vec3(origin.x + a.x * u + b.x * v + c.x * w,
				origin.y + a.y * u + b.y * v + c.y * w,
				origin.z + a.z * u + b.z * v + c.z * w);
		}

		private boolean contains(Vec3 point) {
			if (point.x < minimum[AXIS_X] - VOLUME_OVERLAP_EPSILON
				|| point.x > maximum[AXIS_X] + VOLUME_OVERLAP_EPSILON
				|| point.y < minimum[AXIS_Y] - VOLUME_OVERLAP_EPSILON
				|| point.y > maximum[AXIS_Y] + VOLUME_OVERLAP_EPSILON
				|| point.z < minimum[AXIS_Z] - VOLUME_OVERLAP_EPSILON
				|| point.z > maximum[AXIS_Z] + VOLUME_OVERLAP_EPSILON)
				return false;
			double dx = point.x - origin.x;
			double dy = point.y - origin.y;
			double dz = point.z - origin.z;
			double u = dx * reciprocalA.x + dy * reciprocalA.y + dz * reciprocalA.z;
			double v = dx * reciprocalB.x + dy * reciprocalB.y + dz * reciprocalB.z;
			double w = dx * reciprocalC.x + dy * reciprocalC.y + dz * reciprocalC.z;
			return u >= -VOLUME_OVERLAP_EPSILON && u <= 1.0d + VOLUME_OVERLAP_EPSILON
				&& v >= -VOLUME_OVERLAP_EPSILON && v <= 1.0d + VOLUME_OVERLAP_EPSILON
				&& w >= -VOLUME_OVERLAP_EPSILON && w <= 1.0d + VOLUME_OVERLAP_EPSILON;
		}
	}

	/** One affine body-space transform accumulated through a parent-to-child joint chain. */
	private record Transform(SurgicalCubeRotation rotation, Vec3 translation) {
		private static final Transform IDENTITY =
			new Transform(SurgicalCubeRotation.IDENTITY, Vec3.ZERO);

		private Vec3 apply(Vec3 point) {
			return rotation.rotate(point).add(translation);
		}

		private Transform rotateAround(Vec3 pivot, SurgicalCubeRotation delta) {
			if (delta.isIdentity())
				return this;
			Vec3 nextTranslation = pivot.add(delta.rotate(translation.subtract(pivot)));
			return new Transform(rotation.then(delta), nextTranslation);
		}

		private boolean isIdentity() {
			return rotation.isIdentity() && translation.lengthSqr() < GEOMETRY_EPSILON;
		}
	}

	/** The rest state of one source, exactly as the visible render receives it. */
	public record SourceState(Map<Integer, CubeBox> boxes, Map<Integer, Vec3> offsets,
		Map<Integer, SurgicalCubeRotation> rotations) {}

	/** One source's cube transforms for the current frame, in the yaw-zero body frame. */
	public record Frame(Map<Integer, Vec3> offsets, Map<Integer, SurgicalCubeRotation> rotations) {
		public static final Frame EMPTY = new Frame(Map.of(), Map.of());

		public boolean isEmpty() {
			return offsets.isEmpty() && rotations.isEmpty();
		}

		public Map<Integer, Vec3> mergeOffsets(Map<Integer, Vec3> base) {
			if (offsets.isEmpty())
				return base;
			Map<Integer, Vec3> merged = new HashMap<>(base);
			merged.putAll(offsets);
			return merged;
		}

		public Map<Integer, SurgicalCubeRotation> mergeRotations(Map<Integer, SurgicalCubeRotation> base) {
			if (rotations.isEmpty())
				return base;
			Map<Integer, SurgicalCubeRotation> merged = new HashMap<>(base);
			merged.putAll(rotations);
			return merged;
		}
	}

	/**
	 * The body-local frame the capture baked into its vertices.
	 *
	 * <p>{@code LivingEntityRenderer} turns the model by {@code 180° - yaw} and then mirrors it with
	 * {@code scale(-1, -1, 1)}, so vanilla model space arrives rotated and flipped: model {@code +X}
	 * is the body's left, {@code +Y} points down and {@code +Z} points backwards. Reproducing that
	 * exact frame is what lets zombie joint angles apply unchanged.</p>
	 */
	private record Basis(Quaternionf modelToWorld, Vec3 modelX, Vec3 modelY, Vec3 modelZ) {
		private static Basis bodySpace() {
			return new Basis(new Quaternionf().rotateY((float) Math.PI).rotateZ((float) Math.PI),
				new Vec3(1.0d, 0.0d, 0.0d), new Vec3(0.0d, -1.0d, 0.0d),
				new Vec3(0.0d, 0.0d, -1.0d));
		}

		private Vec3 axis(int index) {
			return index == AXIS_Y ? modelY : index == AXIS_Z ? modelZ : modelX;
		}

		private double project(Vec3 vector, int index) {
			return vector.dot(axis(index));
		}

		private Vec3 point(double x, double y, double z) {
			return modelX.scale(x).add(modelY.scale(y)).add(modelZ.scale(z));
		}

		/** Builds the rest-frame rotation that maps a canonical humanoid limb onto this child. */
		private SurgicalCubeRotation restAlignment(SurgicalLimbType type, Vec3 restDirection) {
			Vec3 actual = new Vec3(project(restDirection, AXIS_X), project(restDirection, AXIS_Y),
				project(restDirection, AXIS_Z)).normalize();
			Vector3f canonical = new Vector3f(0.0f,
				type == SurgicalLimbType.NECK ? -1.0f : 1.0f, 0.0f);
			Vector3f actualVector = new Vector3f((float) actual.x, (float) actual.y, (float) actual.z);
			Quaternionf alignment = new Quaternionf().rotationTo(canonical, actualVector);

			// rotationTo fixes the long direction but leaves twist underdetermined. Define the
			// swing axis geometrically: it is perpendicular to the installed limb and body forward,
			// just as canonical humanoid X is perpendicular to a hanging limb and model Z.
			Vec3 targetX = actual.cross(new Vec3(0.0d, 0.0d, 1.0d))
				.scale(type == SurgicalLimbType.NECK ? -1.0d : 1.0d);
			if (targetX.lengthSqr() < GEOMETRY_EPSILON)
				targetX = new Vec3(1.0d, 0.0d, 0.0d)
					.subtract(actual.scale(actual.x));
			targetX = targetX.normalize();
			Vector3f mapped = alignment.transform(new Vector3f(1.0f, 0.0f, 0.0f));
			Vec3 mappedX = new Vec3(mapped.x, mapped.y, mapped.z).normalize();
			double sine = actual.dot(mappedX.cross(targetX));
			double cosine = Mth.clamp(mappedX.dot(targetX), -1.0d, 1.0d);
			float twistAngle = (float) Math.atan2(sine, cosine);
			if (Math.abs(twistAngle) > 1.0e-6f) {
				Quaternionf twist = new Quaternionf().rotationAxis(twistAngle, actualVector);
				alignment = twist.mul(alignment);
			}
			return new SurgicalCubeRotation(alignment.x(), alignment.y(), alignment.z(), alignment.w());
		}

		/** Retargets vanilla {@link ModelPart} Euler angles through the measured rest frame. */
		private SurgicalCubeRotation reframe(SurgicalCubeRotation restAlignment,
			float zRot, float yRot, float xRot) {
			if (zRot == 0.0f && yRot == 0.0f && xRot == 0.0f)
				return SurgicalCubeRotation.IDENTITY;
			Quaternionf alignment = new Quaternionf((float) restAlignment.x(),
				(float) restAlignment.y(), (float) restAlignment.z(), (float) restAlignment.w());
			Quaternionf modelRotation = new Quaternionf(alignment)
				.mul(new Quaternionf().rotationZYX(zRot, yRot, xRot))
				.mul(new Quaternionf(alignment).conjugate());
			Quaternionf world = new Quaternionf(modelToWorld)
				.mul(modelRotation)
				.mul(new Quaternionf(modelToWorld).conjugate());
			return new SurgicalCubeRotation(world.x(), world.y(), world.z(), world.w());
		}

		/**
		 * Applies SpiderModel's Y/Z Euler additions relative to the measured static leg pose. Vanilla
		 * spider legs originate along model X rather than humanoid model Y, so passing these angles
		 * through {@link #reframe} would rotate them around the wrong inferred axes. The measured
		 * direction uniquely recovers the base Y/Z angles of an outward-pointing left or right leg;
		 * {@code animated * inverse(rest)} then gives the exact pose delta to apply to baked geometry.
		 */
		private SurgicalCubeRotation spiderReframe(Vec3 restDirection, boolean left,
			float zRot, float yRot) {
			if (zRot == 0.0f && yRot == 0.0f)
				return SurgicalCubeRotation.IDENTITY;
			Vec3 actual = new Vec3(project(restDirection, AXIS_X),
				project(restDirection, AXIS_Y), project(restDirection, AXIS_Z)).normalize();
			float side = left ? 1.0f : -1.0f;
			float horizontal = (float) Math.sqrt(actual.x * actual.x + actual.y * actual.y);
			float baseY = (float) Math.atan2(-side * actual.z, horizontal);
			float baseZ = (float) Math.atan2(side * actual.y, side * actual.x);
			Quaternionf rest = new Quaternionf().rotationZYX(baseZ, baseY, 0.0f);
			Quaternionf animated = new Quaternionf().rotationZYX(baseZ + zRot,
				baseY + yRot, 0.0f);
			Quaternionf modelDelta = animated.mul(new Quaternionf(rest).conjugate());
			Quaternionf world = new Quaternionf(modelToWorld)
				.mul(modelDelta)
				.mul(new Quaternionf(modelToWorld).conjugate());
			return new SurgicalCubeRotation(world.x(), world.y(), world.z(), world.w());
		}
	}

	/**
	 * One rest-state cube: its world centre plus its span along the body-local axes.
	 *
	 * <p>The centre is the mean of the transformed corners, which is exactly the point the render
	 * path rotates a cube around once its static offset is included.</p>
	 */
	public record CubeBox(Vec3 center, double[] min, double[] max, List<Vec3> points) {
		public CubeBox {
			points = List.copyOf(points);
		}

		@Nullable
		private static CubeBox of(List<Vec3> corners, Basis basis) {
			if (corners.isEmpty())
				return null;
			double[] min = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE };
			double[] max = { -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };
			Vec3 sum = Vec3.ZERO;
			for (Vec3 corner : corners) {
				for (int axis = AXIS_X; axis <= AXIS_Z; axis++) {
					double projected = basis.project(corner, axis);
					min[axis] = Math.min(min[axis], projected);
					max[axis] = Math.max(max[axis], projected);
				}
				sum = sum.add(corner);
			}
			return new CubeBox(sum.scale(1.0d / corners.size()), min, max, corners);
		}

		/** Longest covariance axis, or null when the group is too cube-like to define one. */
		@Nullable
		private Vec3 principalAxis(Basis basis) {
			if (points.size() < 2)
				return null;
			Vec3 mean = Vec3.ZERO;
			for (Vec3 point : points)
				mean = mean.add(point);
			mean = mean.scale(1.0d / points.size());
			double xx = 0.0d;
			double xy = 0.0d;
			double xz = 0.0d;
			double yy = 0.0d;
			double yz = 0.0d;
			double zz = 0.0d;
			for (Vec3 point : points) {
				Vec3 delta = point.subtract(mean);
				xx += delta.x * delta.x;
				xy += delta.x * delta.y;
				xz += delta.x * delta.z;
				yy += delta.y * delta.y;
				yz += delta.y * delta.z;
				zz += delta.z * delta.z;
			}
			double trace = xx + yy + zz;
			if (trace < GEOMETRY_EPSILON)
				return null;
			int seedAxis = AXIS_X;
			for (int axis = AXIS_Y; axis <= AXIS_Z; axis++)
				if (max[axis] - min[axis] > max[seedAxis] - min[seedAxis])
					seedAxis = axis;
			Vec3 axis = basis.axis(seedAxis);
			for (int iteration = 0; iteration < 16; iteration++) {
				Vec3 next = new Vec3(xx * axis.x + xy * axis.y + xz * axis.z,
					xy * axis.x + yy * axis.y + yz * axis.z,
					xz * axis.x + yz * axis.y + zz * axis.z);
				if (next.lengthSqr() < GEOMETRY_EPSILON)
					return null;
				axis = next.normalize();
			}
			Vec3 multiplied = new Vec3(xx * axis.x + xy * axis.y + xz * axis.z,
				xy * axis.x + yy * axis.y + yz * axis.z,
				xz * axis.x + yz * axis.y + zz * axis.z);
			double eigenvalue = axis.dot(multiplied);
			return eigenvalue / trace >= PRINCIPAL_AXIS_SHARE ? axis : null;
		}

		private double supportRadius(Vec3 axis) {
			double radius = 0.0d;
			for (Vec3 point : points)
				radius = Math.max(radius, Math.abs(point.subtract(center).dot(axis)));
			return radius;
		}

		private double distanceToSqr(Vec3 point, Basis basis) {
			double distance = 0.0d;
			for (int axis = AXIS_X; axis <= AXIS_Z; axis++) {
				double projected = basis.project(point, axis);
				double outside = projected < min[axis] ? min[axis] - projected
					: projected > max[axis] ? projected - max[axis] : 0.0d;
				distance += outside * outside;
			}
			return distance;
		}

		/** Centre of the nearest shared/connecting region between this cube and another. */
		private Vec3 contactCenter(CubeBox other, Basis basis) {
			double[] coordinate = new double[3];
			for (int axis = AXIS_X; axis <= AXIS_Z; axis++) {
				if (max[axis] < other.min[axis])
					coordinate[axis] = (max[axis] + other.min[axis]) * 0.5d;
				else if (other.max[axis] < min[axis])
					coordinate[axis] = (min[axis] + other.max[axis]) * 0.5d;
				else
					coordinate[axis] = (Math.max(min[axis], other.min[axis])
						+ Math.min(max[axis], other.max[axis])) * 0.5d;
			}
			return basis.point(coordinate[AXIS_X], coordinate[AXIS_Y], coordinate[AXIS_Z]);
		}

		private Vec3 surfaceToward(Vec3 target, SurgicalLimbType type, Basis basis) {
			Vec3 direction = target.subtract(center);
			if (direction.lengthSqr() < GEOMETRY_EPSILON)
				direction = basis.axis(AXIS_Y).scale(type == SurgicalLimbType.NECK ? 1.0d : -1.0d);
			double scale = Double.POSITIVE_INFINITY;
			for (int axis = AXIS_X; axis <= AXIS_Z; axis++) {
				double component = basis.project(direction, axis);
				if (Math.abs(component) <= GEOMETRY_EPSILON)
					continue;
				double centerProjection = basis.project(center, axis);
				double boundary = component > 0.0d ? max[axis] : min[axis];
				scale = Math.min(scale, (boundary - centerProjection) / component);
			}
			return Double.isFinite(scale) ? center.add(direction.scale(scale)) : center;
		}
	}
}
