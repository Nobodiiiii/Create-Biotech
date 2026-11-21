package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.Vec3;

/** SurgicalTableConnections algorithms over the canonical SurgicalTableBlockEntity state. */
final class SurgicalTableConnections {
	private SurgicalTableConnections() {}


	static SurgicalTableBlockEntity.ComponentGroup connectedGroup(SurgicalTableBlockEntity operationOwner, SurgicalSubject startSubject, int startCube) {
		return SurgicalTableConnections.connectedGroup(operationOwner, startSubject, startCube, null);
	}

	static SurgicalTableBlockEntity.ComponentGroup connectedGroup(SurgicalTableBlockEntity operationOwner, SurgicalSubject startSubject, int startCube,
		@Nullable SurgicalGlueJoint excluded) {
		SurgicalConnectionGraph<UUID> graph = SurgicalTableConnections.connectionGraph(operationOwner, excluded);
		return graph == null ? new SurgicalTableBlockEntity.ComponentGroup(Map.of())
			: new SurgicalTableBlockEntity.ComponentGroup(graph.componentContaining(startSubject.persistentId(), startCube).members());
	}

	/** All cubes joined through the unified native-seam/glue/combination graph, keyed by subject id. */
	static Map<Integer, BitSet> connectedComponents(SurgicalTableBlockEntity operationOwner, int subjectId, int cubeId) {
		SurgicalSubject start = operationOwner.getSubject(subjectId);
		if (start == null || !start.validPresentCube(cubeId))
			return Map.of();
		SurgicalTableBlockEntity.ComponentGroup group = SurgicalTableConnections.connectedGroup(operationOwner, start, cubeId);
		Map<Integer, BitSet> result = new HashMap<>();
		for (SurgicalSubject subject : operationOwner.subjects) {
			BitSet included = group.components().get(subject.persistentId());
			if (included != null && !included.isEmpty())
				result.put(subject.id(), (BitSet) included.clone());
		}
		return Map.copyOf(result);
	}

	/** Client-side topology-aware variant that rejects a model differing from the stored cube ids. */
	static Map<Integer, BitSet> connectedComponents(SurgicalTableBlockEntity operationOwner, int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject start = operationOwner.getSubject(subjectId);
		if (start == null || !start.matchesObservedTopology(observedCubeCount, observedSeams))
			return Map.of();
		return SurgicalTableConnections.connectedComponents(operationOwner, subjectId, cubeId);
	}

	/** The clicked cube and all cubes joined to it by one native or explicit connection edge. */
	static Map<Integer, BitSet> directConnections(SurgicalTableBlockEntity operationOwner, int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject start = operationOwner.getSubject(subjectId);
		if (start == null || !start.matchesObservedTopology(observedCubeCount, observedSeams)
			|| !start.validPresentCube(cubeId))
			return Map.of();
		SurgicalConnectionGraph<UUID> graph = SurgicalTableConnections.connectionGraph(operationOwner, null);
		return graph == null ? Map.of()
			: operationOwner.componentsBySubjectId(new SurgicalTableBlockEntity.ComponentGroup(
				graph.directConnections(start.persistentId(), cubeId).members()));
	}

	static @Nullable
	SurgicalConnectionGraph<UUID> connectionGraph(SurgicalTableBlockEntity operationOwner, @Nullable SurgicalGlueJoint excluded) {
		if (excluded != null)
			return SurgicalTableConnections.connectionGraph(operationOwner, Set.of(excluded), Map.of());
		if (operationOwner.cachedConnectionValid)
			return operationOwner.cachedConnectionGraph;
		operationOwner.cachedConnectionGraph = SurgicalTableConnections.buildConnectionGraph(operationOwner, Set.of(), Map.of(), true);
		operationOwner.cachedConnectionValid = true;
		return operationOwner.cachedConnectionGraph;
	}

	static @Nullable
	SurgicalConnectionGraph<UUID> connectionGraph(SurgicalTableBlockEntity operationOwner, Set<SurgicalGlueJoint> excluded,
		Map<UUID, BitSet> cutOverrides) {
		return SurgicalTableConnections.connectionGraph(operationOwner, excluded, cutOverrides, true);
	}

	/**
	 * A one-entry memo for the parameterized graphs. The cut and glue paths ask for the same excluded
	 * joints and cut overrides two to four times inside a single edit — {@code cutGlueJoint} alone runs
	 * {@code glueCutState} twice, each building two graphs — and the client previews repeat the same
	 * request every frame while a cut is held.
	 */
	static @Nullable
	SurgicalConnectionGraph<UUID> connectionGraph(SurgicalTableBlockEntity operationOwner, Set<SurgicalGlueJoint> excluded,
		Map<UUID, BitSet> cutOverrides, boolean includeCombinations) {
		if (operationOwner.variantConnectionValid
			&& operationOwner.variantConnectionCombinations == includeCombinations
			&& operationOwner.variantConnectionExcluded.equals(excluded)
			&& operationOwner.variantConnectionOverrides.equals(cutOverrides))
			return operationOwner.variantConnectionGraph;
		operationOwner.variantConnectionGraph = SurgicalTableConnections.buildConnectionGraph(operationOwner, excluded, cutOverrides, includeCombinations);
		operationOwner.variantConnectionExcluded = Set.copyOf(excluded);
		// The override BitSets belong to the caller, so they are copied rather than aliased.
		Map<UUID, BitSet> overrides = new HashMap<>();
		cutOverrides.forEach((key, cuts) -> overrides.put(key, (BitSet) cuts.clone()));
		operationOwner.variantConnectionOverrides = overrides;
		operationOwner.variantConnectionCombinations = includeCombinations;
		operationOwner.variantConnectionValid = true;
		return operationOwner.variantConnectionGraph;
	}

	/** All topology writes and subject ownership changes invalidate the same derived caches. */
	static void invalidateConnections(SurgicalTableBlockEntity operationOwner) {
		operationOwner.cachedConnectionValid = false;
		operationOwner.cachedConnectionGraph = null;
		operationOwner.cachedConnectedSubjectGroupsValid = false;
		operationOwner.cachedConnectedSubjectGroups = Map.of();
		operationOwner.variantConnectionValid = false;
		operationOwner.variantConnectionGraph = null;
		operationOwner.variantConnectionExcluded = Set.of();
		operationOwner.variantConnectionOverrides = Map.of();
	}

	static @Nullable
	SurgicalConnectionGraph<UUID> buildConnectionGraph(SurgicalTableBlockEntity operationOwner, Set<SurgicalGlueJoint> excluded,
		Map<UUID, BitSet> cutOverrides, boolean includeCombinations) {
		List<SurgicalConnectionGraph.Body<UUID>> bodies = new ArrayList<>();
		for (SurgicalSubject subject : operationOwner.subjects)
			if (SurgicalAssembly.validTopology(subject.cubeCount, subject.seams))
				bodies.add(new SurgicalConnectionGraph.Body<>(subject.persistentId(), subject.cubeCount,
					subject.presentCubes, subject.seams,
					cutOverrides.getOrDefault(subject.persistentId(), subject.cutSeams)));
		if (bodies.isEmpty())
			return null;

		List<SurgicalConnectionGraph.Link<UUID>> links = new ArrayList<>();
		for (SurgicalGlueJoint joint : SurgicalTableConnections.allGlueJoints(operationOwner)) {
			if (excluded.contains(joint))
				continue;
			links.add(new SurgicalConnectionGraph.Link<>(joint.first().subjectKey(), joint.first().cubeId(),
				joint.second().subjectKey(), joint.second().cubeId()));
		}
		if (includeCombinations)
			for (SurgicalCombination combination : SurgicalTableConnections.allCombinations(operationOwner)) {
				SurgicalCombination.Member anchor = combination.members().getFirst();
				for (SurgicalCombination.Member member : combination.members().subList(1,
					combination.members().size()))
					links.add(new SurgicalConnectionGraph.Link<>(anchor.subjectKey(), anchor.cubeId(),
						member.subjectKey(), member.cubeId()));
			}
		return SurgicalConnectionGraph.create(bodies, links);
	}

	static Set<SurgicalGlueJoint> allGlueJoints(SurgicalTableBlockEntity operationOwner) {
		Set<SurgicalGlueJoint> joints = new HashSet<>();
		for (SurgicalSubject subject : operationOwner.subjects)
			joints.addAll(subject.glueJoints());
		return joints;
	}

	static Set<SurgicalCombination> allCombinations(SurgicalTableBlockEntity operationOwner) {
		Set<SurgicalCombination> combinations = new HashSet<>();
		for (SurgicalSubject subject : operationOwner.subjects)
			combinations.addAll(subject.combinations());
		return combinations;
	}

	static @Nullable
	SurgicalCombination combinationContaining(SurgicalTableBlockEntity operationOwner, int subjectId, int cubeId) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		return subject == null ? null : subject.combinationContaining(cubeId);
	}

	static boolean isInternalCombinationJoint(SurgicalTableBlockEntity operationOwner, SurgicalGlueJoint joint) {
		return SurgicalLimbOperations.sameCombination(operationOwner, joint.first(), joint.second());
	}

	static boolean isInternalCombinationSeam(SurgicalTableBlockEntity operationOwner, int subjectId, SurgicalAssembly.Seam seam) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || seam == null)
			return false;
		SurgicalCombination first = subject.combinationContaining(seam.first());
		return first != null && first.contains(subject.persistentId(), seam.second());
	}

	static List<SurgicalGlueJoint> externalCombinationJoints(SurgicalTableBlockEntity operationOwner, SurgicalCombination combination) {
		if (combination == null)
			return List.of();
		List<SurgicalGlueJoint> external = new ArrayList<>();
		for (SurgicalGlueJoint joint : SurgicalTableConnections.allGlueJoints(operationOwner)) {
			boolean first = combination.contains(joint.first().subjectKey(), joint.first().cubeId());
			boolean second = combination.contains(joint.second().subjectKey(), joint.second().cubeId());
			if (first != second)
				external.add(joint);
		}
		return List.copyOf(external);
	}

	/** Subject ids that currently form one logical editing group through glue or combinations. */
	static Set<Integer> connectedSubjectIds(SurgicalTableBlockEntity operationOwner, int subjectId) {
		return SurgicalTableConnections.connectedSubjectGroups(operationOwner).getOrDefault(subjectId, Set.of());
	}

	/**
	 * Projects cube-level links onto subject ownership and closes that graph transitively. A subject may
	 * own several disconnected native components, so reaching any one of them also has to pull in links
	 * stored on its other components; otherwise grounding can collect a link whose endpoint body was
	 * omitted. Topology edits invalidate this projection with the connection graph; lookups within
	 * each group share the same immutable member set.
	 */
	static Map<Integer, Set<Integer>> connectedSubjectGroups(SurgicalTableBlockEntity operationOwner) {
		if (operationOwner.cachedConnectedSubjectGroupsValid)
			return operationOwner.cachedConnectedSubjectGroups;

		Map<UUID, Set<UUID>> adjacency = new HashMap<>();
		for (SurgicalSubject subject : operationOwner.subjects)
			adjacency.put(subject.persistentId(), new HashSet<>());
		for (SurgicalGlueJoint joint : SurgicalTableConnections.allGlueJoints(operationOwner))
			SurgicalTableConnections.connectSubjectOwners(adjacency, joint.first().subjectKey(), joint.second().subjectKey());
		for (SurgicalCombination combination : SurgicalTableConnections.allCombinations(operationOwner)) {
			UUID anchor = combination.members().getFirst().subjectKey();
			for (SurgicalCombination.Member member : combination.members().subList(1,
				combination.members().size()))
				SurgicalTableConnections.connectSubjectOwners(adjacency, anchor, member.subjectKey());
		}

		Map<Integer, Set<Integer>> groups = new HashMap<>();
		Set<UUID> visited = new HashSet<>();
		for (SurgicalSubject start : operationOwner.subjects) {
			if (!visited.add(start.persistentId()))
				continue;
			Set<Integer> ids = new HashSet<>();
			ArrayDeque<UUID> pending = new ArrayDeque<>();
			pending.addLast(start.persistentId());
			while (!pending.isEmpty()) {
				UUID current = pending.removeFirst();
				SurgicalSubject subject = operationOwner.subjectsByPersistentId.get(current);
				if (subject != null)
					ids.add(subject.id());
				for (UUID neighbour : adjacency.getOrDefault(current, Set.of()))
					if (visited.add(neighbour))
						pending.addLast(neighbour);
			}
			Set<Integer> frozen = Set.copyOf(ids);
			for (int id : frozen)
				groups.put(id, frozen);
		}
		operationOwner.cachedConnectedSubjectGroups = Map.copyOf(groups);
		operationOwner.cachedConnectedSubjectGroupsValid = true;
		return operationOwner.cachedConnectedSubjectGroups;
	}

	static void connectSubjectOwners(Map<UUID, Set<UUID>> adjacency, UUID first, UUID second) {
		if (first.equals(second) || !adjacency.containsKey(first) || !adjacency.containsKey(second))
			return;
		adjacency.get(first).add(second);
		adjacency.get(second).add(first);
	}

	static Set<SurgicalGlueJoint> jointsWithin(SurgicalTableBlockEntity operationOwner, SurgicalTableBlockEntity.ComponentGroup group) {
		Set<SurgicalGlueJoint> joints = new HashSet<>();
		for (SurgicalGlueJoint joint : SurgicalTableConnections.allGlueJoints(operationOwner))
			if (group.contains(joint.first()) && group.contains(joint.second()))
				joints.add(joint);
		return joints;
	}

	static Set<SurgicalCombination> combinationsWithin(SurgicalTableBlockEntity operationOwner, SurgicalTableBlockEntity.ComponentGroup group) {
		Set<SurgicalCombination> combinations = new HashSet<>();
		for (SurgicalCombination combination : SurgicalTableConnections.allCombinations(operationOwner)) {
			boolean included = true;
			for (SurgicalCombination.Member member : combination.members())
				if (!group.contains(member)) {
					included = false;
					break;
				}
			if (included)
				combinations.add(combination);
		}
		return combinations;
	}

	/** Restored sources keep their table order. The first retained source defines the packed body's upright frame. */
	static SurgicalSubject packingAnchor(SurgicalTableBlockEntity table, SurgicalTableBlockEntity.ComponentGroup group) {
		return table.subjects.stream().filter(s -> group.components().containsKey(s.persistentId()))
			.findFirst().orElseThrow();
	}

	/**
	 * Encodes {@code group} as the assembly packing would create it, laid out relative to
	 * {@code anchor}. A single disconnected component still has an edited pose: wrench rotations and
	 * former glue transforms survive after its last joint is cut, so the complete source layout is
	 * packed even without joints and both the entity renderer and table placement restore them.
	 */
	static @Nullable
	SurgicalAssembly packedAssembly(SurgicalTableBlockEntity operationOwner, SurgicalTableBlockEntity.ComponentGroup group, SurgicalSubject anchor) {
		List<SurgicalAssembly.Source> sources = new ArrayList<>();
		Map<UUID, Integer> sourceIds = new HashMap<>();
		for (SurgicalSubject grouped : operationOwner.subjects) {
			BitSet included = group.components().get(grouped.persistentId());
			if (included == null || included.isEmpty())
				continue;
			Map<Integer, Vec3> offsets = new HashMap<>();
			Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>();
			for (int cube = included.nextSetBit(0); cube >= 0; cube = included.nextSetBit(cube + 1)) {
				Vec3 offset = grouped.componentOffsets.get(cube);
				if (offset != null)
					offsets.put(cube, offset);
				SurgicalCubeRotation rotation = grouped.componentRotations.get(cube);
				if (rotation != null)
					rotations.put(cube, rotation);
			}
			SurgicalAssembly.Source source = SurgicalAssembly.Source.create(grouped.profile(),
				grouped.donorId(), grouped.cubeCount, included, grouped.anatomy(),
				grouped.seams, grouped.cutSeams,
				grouped.cutOrder, grouped.placementFacing(),
				grouped.layPose(),
				new Vec3(grouped.originOffsetX() - anchor.originOffsetX(), 0.0d,
					grouped.originOffsetZ() - anchor.originOffsetZ()), offsets, rotations);
			if (source == null)
				return null;
			sourceIds.put(grouped.persistentId(), sources.size());
			sources.add(source);
		}
		List<SurgicalAssembly.Joint> encodedJoints = new ArrayList<>();
		for (SurgicalGlueJoint joint : SurgicalTableConnections.jointsWithin(operationOwner, group)) {
			Integer firstSource = sourceIds.get(joint.first().subjectKey());
			Integer secondSource = sourceIds.get(joint.second().subjectKey());
			if (firstSource == null || secondSource == null)
				return null;
			SurgicalAssembly.JointReplay replay = null;
			if (joint.replay() != null) {
				Integer movingSource = sourceIds.get(joint.replay().moving().subjectKey());
				if (movingSource == null)
					return null;
				replay = new SurgicalAssembly.JointReplay(movingSource,
					joint.replay().moving().cubeId(), joint.replay().transform(),
					joint.replay().anchorContact());
			}
			encodedJoints.add(new SurgicalAssembly.Joint(firstSource, joint.first().cubeId(),
				secondSource, joint.second().cubeId(), replay));
		}
		List<SurgicalAssembly.Combination> encodedCombinations = new ArrayList<>();
		for (SurgicalCombination combination : SurgicalTableConnections.combinationsWithin(operationOwner, group)) {
			List<SurgicalAssembly.CombinationMember> members = new ArrayList<>();
			for (SurgicalCombination.Member member : combination.members()) {
				Integer source = sourceIds.get(member.subjectKey());
				if (source == null)
					return null;
				members.add(new SurgicalAssembly.CombinationMember(source, member.cubeId()));
			}
			encodedCombinations.add(new SurgicalAssembly.Combination(combination.id(), members));
		}
		List<SurgicalAssembly.Limb> encodedLimbs = new ArrayList<>();
		for (SurgicalLimbJoint limb : operationOwner.limbsWithin(group)) {
			Integer childSource = sourceIds.get(limb.child().subjectKey());
			Integer parentSource = sourceIds.get(limb.parent().subjectKey());
			if (childSource == null || parentSource == null)
				return null;
			encodedLimbs.add(new SurgicalAssembly.Limb(limb.type(), childSource, limb.child().cubeId(),
				parentSource, limb.parent().cubeId()));
		}
		return SurgicalAssembly.createComposite(sources, encodedJoints, encodedCombinations, encodedLimbs,
			anchor.placementFacing(), anchor.layPose());
	}
}
