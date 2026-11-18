package com.nobodiiiii.createbiotech.entity.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalConnectionGraph;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Arm;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Bone;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.LegStyle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Internal BionicRigBuilder implementation; public entry points remain on SlimeBionicAnimator. */
final class BionicRigBuilder {
	private BionicRigBuilder() {}


	/**
	 * Groups every limb with the cubes that move as one part and works out where it hinges.
	 *
	 * <p>Honey combinations still move as one rigid part. Their physical hinge comes only from the
	 * two cubes that really share the boundary seam or glue joint, while the pose direction follows
	 * the centre of the complete driven group. That distinction matters for an asymmetric limb whose
	 * selected connection cube lies on the opposite side of the hinge from most of its visible mass.</p>
	 */
	static List<SlimeBionicAnimator.ResolvedLimb> resolveLimbs(SurgicalAssembly assembly, List<SlimeBionicAnimator.SourceState> sources) {
		SurgicalConnectionGraph<Integer> connections = connectionGraph(assembly);
		if (connections == null)
			return List.of();
		double bodyCenterX = bodyCenter(sources, SlimeBionicAnimator.AXIS_X);
		List<SlimeBionicAnimator.LimbGeometry> geometries = new ArrayList<>();
		for (SurgicalAssembly.Limb limb : assembly.effectiveLimbs()) {
			SlimeBionicAnimator.Member selectedChild = new SlimeBionicAnimator.Member(limb.childSource(), limb.childCube());
			SlimeBionicAnimator.Member selectedParent = new SlimeBionicAnimator.Member(limb.parentSource(), limb.parentCube());
			List<SlimeBionicAnimator.Member> childMembers = group(assembly, selectedChild.source(), selectedChild.cube());
			List<SlimeBionicAnimator.Member> parentMembers = group(assembly, selectedParent.source(), selectedParent.cube());
			SlimeBionicAnimator.Connection connection = connection(connections, selectedChild, selectedParent,
				childMembers, parentMembers);
			if (connection == null)
				continue;
			SlimeBionicAnimator.CubeBox child = box(sources, connection.child());
			SlimeBionicAnimator.CubeBox parent = box(sources, connection.parent());
			if (child == null || parent == null)
				continue;
			geometries.add(new SlimeBionicAnimator.LimbGeometry(limb.type(), childMembers, selectedChild,
				connection.parent(), child, parent));
		}

		List<SlimeBionicAnimator.ResolvedLimb> resolved = new ArrayList<>(geometries.size());
		for (SlimeBionicAnimator.LimbGeometry geometry : geometries) {
			Vec3 pivot = pivot(geometry.type(), geometry.child(), geometry.parentBox(),
				secondaryPivot(geometry, geometries));
			Vec3 drivenCenter = groupCenter(geometry.members(), sources);
			Vec3 restDirection = (drivenCenter == null ? geometry.child().center() : drivenCenter)
				.subtract(pivot);
			if (restDirection.lengthSqr() < SlimeBionicAnimator.GEOMETRY_EPSILON)
				continue;
			// EndermanModel keeps the head as an unrotated root part at its neck attachment: yaw uses the
			// body's vertical model Y axis and pitch uses model X. Preserve that fixed frame for surgical
			// heads as well; aligning it to an off-centre head's rest direction would tilt horizontal yaw.
			SurgicalCubeRotation restAlignment = geometry.type() == SurgicalLimbType.NECK
				? SurgicalCubeRotation.IDENTITY : SlimeBionicAnimator.BODY_SPACE.restAlignment(geometry.type(), restDirection);
			resolved.add(new SlimeBionicAnimator.ResolvedLimb(geometry.type(), geometry.members(), geometry.anchor(),
				geometry.parent(), pivot, restDirection,
				SlimeBionicAnimator.BODY_SPACE.project(geometry.child().center(), SlimeBionicAnimator.AXIS_X) - bodyCenterX,
				SlimeBionicAnimator.BODY_SPACE.project(geometry.child().center(), SlimeBionicAnimator.AXIS_Z),
				restAlignment, null, -1, null, null));
		}
		List<SlimeBionicAnimator.ResolvedLimb> linked = linkHierarchy(assignBones(resolved));
		return assignLegGaits(linked, BionicMobilityMeasurements.groundedHipIndices(linked, sources));
	}

	/** Finds the elbow/knee that hangs from this upper limb and resolves its physical hinge. */
	@Nullable
	static Vec3 secondaryPivot(SlimeBionicAnimator.LimbGeometry primary, List<SlimeBionicAnimator.LimbGeometry> geometries) {
		SurgicalLimbType secondaryType = switch (primary.type()) {
		case SHOULDER -> SurgicalLimbType.ELBOW;
		case HIP -> SurgicalLimbType.KNEE;
		default -> null;
		};
		if (secondaryType == null)
			return null;
		for (SlimeBionicAnimator.LimbGeometry candidate : geometries)
			if (candidate.type() == secondaryType && primary.members().contains(candidate.parent()))
				return pivot(candidate.type(), candidate.child(), candidate.parentBox(), null);
		return null;
	}

	/** Assigns every arm to a stable left/right animation channel from its resting position. */
	static List<SlimeBionicAnimator.ResolvedLimb> assignBones(List<SlimeBionicAnimator.ResolvedLimb> limbs) {
		List<SlimeBionicAnimator.ResolvedLimb> assigned = new ArrayList<>(limbs);
		Map<SurgicalLimbType, List<Integer>> byType = new EnumMap<>(SurgicalLimbType.class);
		for (int index = 0; index < limbs.size(); index++)
			byType.computeIfAbsent(limbs.get(index).type(), ignored -> new ArrayList<>()).add(index);
		byType.forEach((type, indices) -> {
			indices.sort((first, second) -> Double.compare(limbs.get(first).side(), limbs.get(second).side()));
			if (type == SurgicalLimbType.HIP || type == SurgicalLimbType.KNEE
				|| type == SurgicalLimbType.ELBOW)
				return;
			if (type == SurgicalLimbType.NECK) {
				if (!indices.isEmpty()) {
					int index = indices.getFirst();
					assigned.set(index, assigned.get(index).withBone(Bone.HEAD));
				}
				return;
			}
			List<Integer> right = new ArrayList<>();
			List<Integer> left = new ArrayList<>();
			List<Integer> centre = new ArrayList<>();
			for (int index : indices) {
				double side = limbs.get(index).side();
				if (side < -SlimeBionicAnimator.GEOMETRY_EPSILON)
					right.add(index);
				else if (side > SlimeBionicAnimator.GEOMETRY_EPSILON)
					left.add(index);
				else
					centre.add(index);
			}
			for (int index : centre)
				(right.size() <= left.size() ? right : left).add(index);
			java.util.Comparator<Integer> frontToBack = java.util.Comparator
				.comparingDouble((Integer index) -> limbs.get(index).longitudinal())
				.thenComparingDouble(index -> limbs.get(index).side())
				.thenComparingInt(Integer::intValue);
			right.sort(frontToBack);
			left.sort(frontToBack);
			assignArmSide(assigned, type, right, false);
			assignArmSide(assigned, type, left, true);
		});
		return List.copyOf(assigned);
	}

	static void assignArmSide(List<SlimeBionicAnimator.ResolvedLimb> assigned, SurgicalLimbType type,
		List<Integer> indices, boolean left) {
		for (int slot = 0; slot < indices.size(); slot++) {
			int index = indices.get(slot);
			float phase = ((slot + (left ? 0 : 1)) & 1) * Mth.PI;
			assigned.set(index, assigned.get(index).withBone(bone(type, left))
				.withArm(new SlimeBionicAnimator.ArmChannel(left, slot, phase)));
		}
	}

	@Nullable
	static Bone bone(SurgicalLimbType type, boolean left) {
		return switch (type) {
		case SHOULDER -> left ? Bone.LEFT_SHOULDER : Bone.RIGHT_SHOULDER;
		case ELBOW -> left ? Bone.LEFT_ELBOW : Bone.RIGHT_ELBOW;
		case HIP, KNEE -> null;
		case NECK -> Bone.HEAD;
		};
	}

	/**
	 * Connects lower bones to the effective matching upper joint whose child-side rigid island contains
	 * their parent endpoint. Unmatched elbows and knees remain in the assembly but are filtered out before
	 * this stage.
	 */
	static List<SlimeBionicAnimator.ResolvedLimb> linkHierarchy(List<SlimeBionicAnimator.ResolvedLimb> limbs) {
		List<SlimeBionicAnimator.ResolvedLimb> linked = new ArrayList<>(limbs);
		for (int index = 0; index < limbs.size(); index++) {
			SlimeBionicAnimator.ResolvedLimb limb = limbs.get(index);
			SurgicalLimbType parentType = switch (limb.type()) {
			case ELBOW -> SurgicalLimbType.SHOULDER;
			case KNEE -> SurgicalLimbType.HIP;
			default -> null;
			};
			if (parentType == null)
				continue;
			for (int candidateIndex = 0; candidateIndex < limbs.size(); candidateIndex++) {
				SlimeBionicAnimator.ResolvedLimb candidate = limbs.get(candidateIndex);
				if (candidate.type() == parentType && candidate.members().contains(limb.parent())) {
					linked.set(index, limb.withBone(childBone(limb.type(), candidate.bone()))
						.withArm(candidate.arm())
						.withParent(candidateIndex));
					break;
				}
			}
		}
		return List.copyOf(linked);
	}

	@Nullable
	static Bone childBone(SurgicalLimbType type, @Nullable Bone parent) {
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
	static List<SlimeBionicAnimator.ResolvedLimb> assignLegGaits(List<SlimeBionicAnimator.ResolvedLimb> limbs, Set<Integer> groundedHips) {
		List<Integer> hips = new ArrayList<>(groundedHips);
		hips.sort(Integer::compareTo);
		if (hips.size() < 2)
			return limbs;
		List<Integer> humanoidHips = new ArrayList<>();
		List<Integer> spiderHips = new ArrayList<>();
		for (int index : hips) {
			SlimeBionicAnimator.ResolvedLimb hip = limbs.get(index);
			(legStyle(hip) == LegStyle.SPIDER ? spiderHips : humanoidHips).add(index);
		}

		List<SlimeBionicAnimator.ResolvedLimb> assigned = new ArrayList<>(limbs);
		assignHumanoidGaits(assigned, limbs, humanoidHips);
		assignSpiderGaits(assigned, limbs, spiderHips);

		// A knee is a child-local hinge. It must inherit the exact style, side and phase of the hip
		// whose rotating group owns its parent endpoint, especially on a mixed humanoid/spider body.
		for (int index = 0; index < assigned.size(); index++) {
			SlimeBionicAnimator.ResolvedLimb limb = assigned.get(index);
			if (limb.type() != SurgicalLimbType.KNEE || limb.parentIndex() < 0)
				continue;
			SlimeBionicAnimator.ResolvedLimb parent = assigned.get(limb.parentIndex());
			if (parent.type() == SurgicalLimbType.HIP && parent.gait() != null)
				assigned.set(index, limb.withGait(parent.gait()));
		}
		return List.copyOf(assigned);
	}

	/**
	 * A pronounced sideways reach marks a spider leg. The tolerance deliberately includes the
	 * vanilla spider's 45-degree front and hind legs without catching ordinarily hanging legs.
	 */
	static LegStyle legStyle(SlimeBionicAnimator.ResolvedLimb hip) {
		Vec3 direction = hip.restDirection();
		double vertical = SlimeBionicAnimator.BODY_SPACE.project(direction, SlimeBionicAnimator.AXIS_Y);
		double lateral = SlimeBionicAnimator.BODY_SPACE.project(direction, SlimeBionicAnimator.AXIS_X);
		double longitudinal = SlimeBionicAnimator.BODY_SPACE.project(direction, SlimeBionicAnimator.AXIS_Z);
		double minimumHorizontal = vertical * SlimeBionicAnimator.SPIDER_HORIZONTAL_TO_VERTICAL_RATIO;
		return lateral * lateral + longitudinal * longitudinal
			>= minimumHorizontal * minimumHorizontal
			? LegStyle.SPIDER : LegStyle.HUMANOID;
	}

	/** The pre-existing gait assignment, applied only to this body's humanoid-like hips. */
	static void assignHumanoidGaits(List<SlimeBionicAnimator.ResolvedLimb> assigned,
		List<SlimeBionicAnimator.ResolvedLimb> limbs, List<Integer> hips) {
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
			if (side < -SlimeBionicAnimator.GEOMETRY_EPSILON)
				right.add(index);
			else if (side > SlimeBionicAnimator.GEOMETRY_EPSILON)
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
					.withGait(new SlimeBionicAnimator.GaitChannel(LegStyle.HUMANOID, isLeft, phaseStep * slot)));
			}
		}
	}

	/** Spider legs share phases across the body and mirror their lift across its sagittal plane. */
	static void assignSpiderGaits(List<SlimeBionicAnimator.ResolvedLimb> assigned,
		List<SlimeBionicAnimator.ResolvedLimb> limbs, List<Integer> hips) {
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
			if (side < -SlimeBionicAnimator.GEOMETRY_EPSILON)
				right.add(index);
			else if (side > SlimeBionicAnimator.GEOMETRY_EPSILON)
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

	static void assignAlternatingHumanoidSide(List<SlimeBionicAnimator.ResolvedLimb> assigned,
		List<Integer> indices, boolean left) {
		for (int row = 0; row < indices.size(); row++) {
			int index = indices.get(row);
			int group = (row + (left ? 1 : 0)) & 1;
			assigned.set(index, assigned.get(index)
				.withGait(new SlimeBionicAnimator.GaitChannel(LegStyle.HUMANOID, left, group * Mth.PI)));
		}
	}

	static void assignSpiderSide(List<SlimeBionicAnimator.ResolvedLimb> assigned,
		List<Integer> indices, boolean left, int rowCount) {
		for (int row = 0; row < indices.size(); row++) {
			int index = indices.get(row);
			assigned.set(index, assigned.get(index)
				.withGait(new SlimeBionicAnimator.GaitChannel(LegStyle.SPIDER, left, spiderPhase(row, rowCount))));
		}
	}

	/** The four-row case is the exact front-to-back phase order used by SpiderModel. */
	static float spiderPhase(int row, int rowCount) {
		if (rowCount == 4)
			return switch (row) {
			case 0 -> Mth.PI * 1.5f;
			case 1 -> Mth.HALF_PI;
			case 2 -> Mth.PI;
			default -> 0.0f;
			};
		return rowCount <= 1 ? 0.0f : Mth.TWO_PI * row / rowCount;
	}

	/** Uses the preferred arm side, falling back when the body has arms only on the other side. */
	static Arm attackArm(List<SlimeBionicAnimator.ResolvedLimb> limbs, Arm preferred) {
		boolean right = false;
		boolean left = false;
		for (SlimeBionicAnimator.ResolvedLimb limb : limbs) {
			if (limb.type() != SurgicalLimbType.SHOULDER)
				continue;
			if (limb.bone() == Bone.RIGHT_SHOULDER)
				right = true;
			if (limb.bone() == Bone.LEFT_SHOULDER)
				left = true;
		}
		if ((preferred == Arm.LEFT && left) || (preferred == Arm.RIGHT && right))
			return preferred;
		return right ? Arm.RIGHT : left ? Arm.LEFT : Arm.NONE;
	}

	/** Centre of the complete rigid part that an installed joint rotates. */
	@Nullable
	static Vec3 groupCenter(List<SlimeBionicAnimator.Member> members, List<SlimeBionicAnimator.SourceState> sources) {
		Vec3 sum = Vec3.ZERO;
		int count = 0;
		for (SlimeBionicAnimator.Member member : members) {
			SlimeBionicAnimator.CubeBox box = box(sources, member);
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
	static double bodyCenter(List<SlimeBionicAnimator.SourceState> sources, int axis) {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (SlimeBionicAnimator.SourceState source : sources)
			for (SlimeBionicAnimator.CubeBox box : source.boxes().values()) {
				min = Math.min(min, box.min()[axis]);
				max = Math.max(max, box.max()[axis]);
			}
		return Double.isFinite(min) && Double.isFinite(max) ? (min + max) * 0.5d : 0.0d;
	}

	/**
	 * Uses the centre of the installed hip hinges as the torso rotation point.
	 *
	 * <p>The midpoint between the installed hip roots is the anatomical waist. The complete
	 * model-bounds centre is higher on an ordinary humanoid because it includes the head and legs;
	 * rotating around that point pulls the torso away from the planted hip groups. Bodies without
	 * hips retain the bounds-centre fallback because they have no anatomical waist.</p>
	 */
	static Vec3 bodyPivot(List<SlimeBionicAnimator.ResolvedLimb> limbs, List<SlimeBionicAnimator.SourceState> sources) {
		Vec3 hipSum = Vec3.ZERO;
		int hipCount = 0;
		for (SlimeBionicAnimator.ResolvedLimb limb : limbs)
			if (limb.type() == SurgicalLimbType.HIP) {
				hipSum = hipSum.add(limb.pivot());
				hipCount++;
			}
		if (hipCount > 0)
			return hipSum.scale(1.0d / hipCount);
		return SlimeBionicAnimator.BODY_SPACE.point(bodyCenter(sources, SlimeBionicAnimator.AXIS_X), bodyCenter(sources, SlimeBionicAnimator.AXIS_Y),
			bodyCenter(sources, SlimeBionicAnimator.AXIS_Z));
	}

	/** The cubes that depend on {@code cube} to reach the hinge's parent side. */
	static List<SlimeBionicAnimator.Member> group(SurgicalAssembly assembly, int source, int cube) {
		return assembly.rotatingGroup(source, cube).stream()
			.map(member -> new SlimeBionicAnimator.Member(member.source(), member.cube())).toList();
	}

	@Nullable
	static SlimeBionicAnimator.CubeBox box(List<SlimeBionicAnimator.SourceState> sources, SlimeBionicAnimator.Member member) {
		return member.source() < 0 || member.source() >= sources.size() ? null
			: sources.get(member.source()).boxes().get(member.cube());
	}

	/** Resolves old arbitrary combination endpoints as well as newly stored physical endpoints. */
	@Nullable
	static SlimeBionicAnimator.Connection connection(SurgicalConnectionGraph<Integer> connections, SlimeBionicAnimator.Member selectedChild,
		SlimeBionicAnimator.Member selectedParent, List<SlimeBionicAnimator.Member> childMembers, List<SlimeBionicAnimator.Member> parentMembers) {
		if (directlyConnected(connections, selectedChild, selectedParent))
			return new SlimeBionicAnimator.Connection(selectedChild, selectedParent);
		for (SlimeBionicAnimator.Member child : childMembers)
			for (SlimeBionicAnimator.Member parent : parentMembers)
				if (directlyConnected(connections, child, parent))
					return new SlimeBionicAnimator.Connection(child, parent);
		return null;
	}

	static boolean directlyConnected(SurgicalConnectionGraph<Integer> connections,
		SlimeBionicAnimator.Member first, SlimeBionicAnimator.Member second) {
		return connections.directConnections(first.source(), first.cube())
			.contains(second.source(), second.cube());
	}

	@Nullable
	static SurgicalConnectionGraph<Integer> connectionGraph(SurgicalAssembly assembly) {
		// A packed assembly is immutable, so its connection graph is too. Building it per call put a
		// full graph construction on every rendered frame of every bionic slime — twice, because
		// effectiveLegLength resolves the limbs again.
		SurgicalConnectionGraph<Integer> cached = SlimeBionicAnimator.CONNECTION_GRAPHS.get(assembly);
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
			SlimeBionicAnimator.CONNECTION_GRAPHS.put(assembly, graph);
		return graph;
	}

	/**
	 * Places the hinge on the appropriate end of the rotating child.
	 *
	 * <p>An elbow or knee gives a compact upper limb its lengthwise direction. For an elongated limb
	 * mounted beside the parent, the secondary joint identifies the distal end even when the parent's
	 * bounds happen to reach closer to it. End-mounted limbs use the end nearest the parent.</p>
	 */
	static Vec3 pivot(SurgicalLimbType type, SlimeBionicAnimator.CubeBox child, SlimeBionicAnimator.CubeBox parent,
		@Nullable Vec3 secondaryPivot) {
		// Head pitch and yaw must originate at the neck connection itself. Using an endpoint of an
		// elongated or unusually shaped head makes it orbit around its own centre instead of nodding.
		if (type == SurgicalLimbType.NECK)
			return child.contactCenter(parent, SlimeBionicAnimator.BODY_SPACE);
		Vec3 principal = child.principalAxis(SlimeBionicAnimator.BODY_SPACE);
		Vec3 fromSecondary = secondaryPivot == null ? Vec3.ZERO : child.center().subtract(secondaryPivot);
		if (principal == null) {
			Vec3 target = fromSecondary.lengthSqr() > SlimeBionicAnimator.GEOMETRY_EPSILON
				? child.center().add(fromSecondary) : parent.center();
			return child.surfaceToward(target, type, SlimeBionicAnimator.BODY_SPACE);
		}

		double radius = child.supportRadius(principal);
		Vec3 first = child.center().add(principal.scale(radius));
		Vec3 second = child.center().subtract(principal.scale(radius));
		double towardProximalEnd = fromSecondary.dot(principal);
		boolean hasDistalEnd = Math.abs(towardProximalEnd) > SlimeBionicAnimator.GEOMETRY_EPSILON;
		double parentAlongAxis = Math.abs(parent.center().subtract(child.center()).dot(principal));
		if (hasDistalEnd && parentAlongAxis <= parent.supportRadius(principal) + SlimeBionicAnimator.GEOMETRY_EPSILON)
			return towardProximalEnd > 0.0d ? first : second;

		double firstDistance = parent.distanceToSqr(first, SlimeBionicAnimator.BODY_SPACE);
		double secondDistance = parent.distanceToSqr(second, SlimeBionicAnimator.BODY_SPACE);
		if (Math.abs(firstDistance - secondDistance) > SlimeBionicAnimator.GEOMETRY_EPSILON)
			return firstDistance < secondDistance ? first : second;

		if (hasDistalEnd)
			return towardProximalEnd > 0.0d ? first : second;

		// BODY_SPACE Y follows model-space Y and therefore increases downward.
		double firstY = SlimeBionicAnimator.BODY_SPACE.project(first, SlimeBionicAnimator.AXIS_Y);
		double secondY = SlimeBionicAnimator.BODY_SPACE.project(second, SlimeBionicAnimator.AXIS_Y);
		return firstY <= secondY ? first : second;
	}
}
