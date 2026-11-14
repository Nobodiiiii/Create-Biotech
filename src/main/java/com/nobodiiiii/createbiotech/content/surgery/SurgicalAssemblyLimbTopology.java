package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** SurgicalAssemblyLimbTopology algorithms over the canonical SurgicalAssembly state. */
final class SurgicalAssemblyLimbTopology {
	private SurgicalAssemblyLimbTopology() {}


	/**
	 * The child part and every attachment that depends on it to reach the joint's parent side. A part
	 * with another path back to the parent is structural body material and does not follow the joint.
	 */
	static List<SurgicalAssembly.CombinationMember> rotatingGroup(SurgicalAssembly operationOwner, int source, int cube) {
		SurgicalAssembly.CombinationMember selected = new SurgicalAssembly.CombinationMember(source, cube);
		List<SurgicalAssembly.CombinationMember> automatic = SurgicalAssemblyLimbTopology.limbTopology(operationOwner).groups().get(selected);
		if (automatic != null)
			return automatic;
		for (SurgicalAssembly.Combination combination : operationOwner.combinations)
			if (combination.members().contains(selected))
				return combination.members();
		return List.of(selected);
	}

	static SurgicalAssembly.LimbTopology limbTopology(SurgicalAssembly operationOwner) {
		SurgicalAssembly.LimbTopology cached = operationOwner.limbTopology;
		if (cached != null)
			return cached;
		cached = SurgicalAssemblyLimbTopology.buildLimbTopology(operationOwner);
		operationOwner.limbTopology = cached;
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
	static SurgicalAssembly.LimbTopology buildLimbTopology(SurgicalAssembly operationOwner) {
		if (operationOwner.limbs.isEmpty())
			return SurgicalAssembly.LimbTopology.EMPTY;
		// Ordinary model seams and glue do not decide anatomical ownership. Honey combinations are the
		// sole explicit way to make several cubes one ownership unit.
		Map<SurgicalAssembly.CombinationMember, Integer> componentIds = new HashMap<>();
		List<List<SurgicalAssembly.CombinationMember>> components = new ArrayList<>();
		for (SurgicalAssembly.Combination combination : operationOwner.combinations) {
			int componentId = components.size();
			components.add(combination.members());
			for (SurgicalAssembly.CombinationMember member : combination.members())
				componentIds.put(member, componentId);
		}
		for (int sourceId = 0; sourceId < operationOwner.sources.size(); sourceId++) {
			SurgicalAssembly.Source source = operationOwner.sources.get(sourceId);
			for (int cubeId = source.presentCubes.nextSetBit(0); cubeId >= 0;
				cubeId = source.presentCubes.nextSetBit(cubeId + 1)) {
				SurgicalAssembly.CombinationMember member = new SurgicalAssembly.CombinationMember(sourceId, cubeId);
				if (componentIds.containsKey(member))
					continue;
				int componentId = components.size();
				componentIds.put(member, componentId);
				components.add(List.of(member));
			}
		}

		Map<Integer, List<SurgicalAssembly.LimbAttachment>> attachments = new HashMap<>();
		Map<SurgicalAssembly.Limb, Integer> childComponents = new HashMap<>();
		Map<SurgicalAssembly.Limb, Integer> parentComponents = new HashMap<>();
		for (SurgicalAssembly.Limb limb : operationOwner.limbs) {
			Integer child = componentIds.get(new SurgicalAssembly.CombinationMember(limb.childSource(), limb.childCube()));
			Integer parent = componentIds.get(new SurgicalAssembly.CombinationMember(limb.parentSource(), limb.parentCube()));
			if (child == null || parent == null || child.equals(parent))
				continue;
			childComponents.put(limb, child);
			parentComponents.put(limb, parent);
			attachments.computeIfAbsent(child, ignored -> new ArrayList<>())
				.add(new SurgicalAssembly.LimbAttachment(limb, true));
			attachments.computeIfAbsent(parent, ignored -> new ArrayList<>())
				.add(new SurgicalAssembly.LimbAttachment(limb, false));
		}

		Map<Integer, SurgicalAssembly.Limb> owners = new HashMap<>();
		for (Map.Entry<Integer, List<SurgicalAssembly.LimbAttachment>> entry : attachments.entrySet()) {
			List<SurgicalAssembly.LimbAttachment> attached = entry.getValue();
			List<SurgicalAssembly.Limb> primaryChildren = new ArrayList<>();
			boolean primaryParent = false;
			List<SurgicalAssembly.Limb> secondaryChildren = new ArrayList<>();
			for (SurgicalAssembly.LimbAttachment attachment : attached) {
				if (attachment.child() && attachment.limb().type().primary())
					primaryChildren.add(attachment.limb());
				else if (!attachment.child() && attachment.limb().type().primary())
					primaryParent = true;
				else if (attachment.child() && attachment.limb().type().secondary())
					secondaryChildren.add(attachment.limb());
			}
			// Inactive second-level joints may be installed anywhere and therefore do not steal a rigid
			// island from its one first-level child joint. A primary-to-primary chain remains ambiguous.
			if (primaryChildren.size() == 1 && !primaryParent)
				owners.put(entry.getKey(), primaryChildren.getFirst());
			else if (primaryChildren.isEmpty() && !primaryParent && secondaryChildren.size() == 1
				&& attached.size() == 1)
				owners.put(entry.getKey(), secondaryChildren.getFirst());
		}

		List<SurgicalAssembly.Limb> effective = new ArrayList<>();
		for (SurgicalAssembly.Limb limb : operationOwner.limbs) {
			Integer child = childComponents.get(limb);
			Integer parent = parentComponents.get(limb);
			if (child == null || parent == null || owners.get(child) != limb)
				continue;
			if (limb.type().primary() && attachments.getOrDefault(parent, List.of()).stream()
				.anyMatch(attachment -> attachment.child() && attachment.limb().type().primary()))
				continue;
			if (limb.type().secondary()) {
				SurgicalAssembly.Limb primary = owners.get(parent);
				if (primary == null || primary.type() != limb.type().matchingPrimary())
					continue;
			}
			effective.add(limb);
		}

		SurgicalConnectionGraph<Integer> motionGraph = SurgicalAssemblyLimbTopology.buildMotionGraph(operationOwner);
		Map<SurgicalAssembly.CombinationMember, List<SurgicalAssembly.CombinationMember>> groups = new HashMap<>();
		Map<SurgicalAssembly.CombinationMember, SurgicalAssembly.Limb> groupOwners = new HashMap<>();
		for (SurgicalAssembly.Limb limb : effective) {
			Integer ownershipChild = childComponents.get(limb);
			List<SurgicalAssembly.CombinationMember> ownershipGroup = components.get(ownershipChild);
			List<SurgicalAssembly.CombinationMember> group = SurgicalAssemblyLimbTopology.drivenMotionGroup(motionGraph, limb, ownershipGroup,
				operationOwner.limbs, childComponents, components);
			for (SurgicalAssembly.CombinationMember member : group) {
				groups.put(member, group);
				groupOwners.put(member, limb);
			}
		}
		return new SurgicalAssembly.LimbTopology(List.copyOf(effective), Map.copyOf(groups), Map.copyOf(groupOwners));
	}

	/**
	 * Builds the rigid graph with every anatomical hinge removed. Per-joint movement then uses this
	 * graph to retain direct and indirect attachments while rejecting parts with a bypass to the
	 * parent side.
	 */
	static @Nullable
	SurgicalConnectionGraph<Integer> buildMotionGraph(SurgicalAssembly operationOwner) {
		Set<SurgicalAssembly.ConnectionKey> hingeEdges = new HashSet<>();
		for (SurgicalAssembly.Limb limb : operationOwner.limbs)
			hingeEdges.add(SurgicalAssembly.ConnectionKey.of(limb.childSource(), limb.childCube(),
				limb.parentSource(), limb.parentCube()));

		List<SurgicalConnectionGraph.Body<Integer>> bodies = new ArrayList<>(operationOwner.sources.size());
		for (int sourceId = 0; sourceId < operationOwner.sources.size(); sourceId++) {
			SurgicalAssembly.Source source = operationOwner.sources.get(sourceId);
			BitSet rigidCuts = source.cutSeams();
			for (int seamId = 0; seamId < source.seams.size(); seamId++) {
				SurgicalAssembly.Seam seam = source.seams.get(seamId);
				if (hingeEdges.contains(SurgicalAssembly.ConnectionKey.of(sourceId, seam.first(), sourceId, seam.second())))
					rigidCuts.set(seamId);
			}
			bodies.add(new SurgicalConnectionGraph.Body<>(sourceId, source.cubeCount,
				source.presentCubes, source.seams, rigidCuts));
		}

		List<SurgicalConnectionGraph.Link<Integer>> links = new ArrayList<>();
		for (SurgicalAssembly.Joint joint : operationOwner.joints)
			if (!hingeEdges.contains(SurgicalAssembly.ConnectionKey.of(joint.firstSource(), joint.firstCube(),
				joint.secondSource(), joint.secondCube())))
				links.add(new SurgicalConnectionGraph.Link<>(joint.firstSource(), joint.firstCube(),
					joint.secondSource(), joint.secondCube()));
		// Honey remains an explicit rigid constraint. Honey across a hinge therefore rejoins both sides,
		// and the fallback below prevents that closed rigid loop from dragging the parent body around.
		for (SurgicalAssembly.Combination combination : operationOwner.combinations) {
			SurgicalAssembly.CombinationMember anchor = combination.members().getFirst();
			for (SurgicalAssembly.CombinationMember member : combination.members().subList(1, combination.members().size()))
				links.add(new SurgicalConnectionGraph.Link<>(anchor.source(), anchor.cube(),
					member.source(), member.cube()));
		}
		SurgicalConnectionGraph<Integer> graph = SurgicalConnectionGraph.create(bodies, links);
		return graph;
	}

	/**
	 * A member follows {@code limb} only when its rigid route to the parent side depends on the child
	 * ownership group. The parent side and every other joint child are outside anchors. Removing an A
	 * child group exposes bypasses: if A-B-C are pairwise connected and C also reaches the D parent,
	 * both B and C remain reachable from D and are excluded. Likewise, two zombie legs joined by a
	 * model seam remain separate because each leg is the child of its own joint. Without an outside
	 * route or foreign joint child, a direct or indirect branch remains dependent on A and follows it.
	 */
	static List<SurgicalAssembly.CombinationMember> drivenMotionGroup(
		@Nullable SurgicalConnectionGraph<Integer> graph, SurgicalAssembly.Limb limb,
		List<SurgicalAssembly.CombinationMember> ownershipGroup, List<SurgicalAssembly.Limb> allLimbs,
		Map<SurgicalAssembly.Limb, Integer> childComponents, List<List<SurgicalAssembly.CombinationMember>> components) {
		if (graph == null || ownershipGroup == null || ownershipGroup.isEmpty())
			return ownershipGroup == null ? List.of() : ownershipGroup;

		SurgicalConnectionGraph.Component<Integer> childSide =
			graph.componentContaining(limb.childSource(), limb.childCube());
		if (childSide.isEmpty())
			return ownershipGroup;
		Set<SurgicalConnectionGraph.Endpoint<Integer>> blocked = new HashSet<>();
		for (SurgicalAssembly.CombinationMember member : ownershipGroup)
			blocked.add(new SurgicalConnectionGraph.Endpoint<>(member.source(), member.cube()));
		Set<SurgicalConnectionGraph.Endpoint<Integer>> outsideAnchors = new HashSet<>();
		outsideAnchors.add(new SurgicalConnectionGraph.Endpoint<>(limb.parentSource(), limb.parentCube()));
		for (SurgicalAssembly.Limb other : allLimbs) {
			if (other.equals(limb))
				continue;
			Integer childComponent = childComponents.get(other);
			if (childComponent == null)
				continue;
			for (SurgicalAssembly.CombinationMember member : components.get(childComponent))
				outsideAnchors.add(new SurgicalConnectionGraph.Endpoint<>(member.source(), member.cube()));
		}
		SurgicalConnectionGraph.Component<Integer> outsideReachable =
			graph.componentContainingAnyExcluding(outsideAnchors, blocked);
		Map<Integer, BitSet> outsideMembers = outsideReachable.members();
		List<SurgicalAssembly.CombinationMember> driven = new ArrayList<>();
		for (Map.Entry<Integer, BitSet> entry : childSide.members().entrySet()) {
			BitSet dependent = (BitSet) entry.getValue().clone();
			BitSet bypass = outsideMembers.get(entry.getKey());
			if (bypass != null)
				dependent.andNot(bypass);
			for (int cube = dependent.nextSetBit(0); cube >= 0;
				cube = dependent.nextSetBit(cube + 1))
				driven.add(new SurgicalAssembly.CombinationMember(entry.getKey(), cube));
		}
		return driven.containsAll(ownershipGroup) ? List.copyOf(driven) : ownershipGroup;
	}
}
