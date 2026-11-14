package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** SurgicalCutOperations algorithms over the canonical SurgicalTableBlockEntity state. */
final class SurgicalCutOperations {
	private SurgicalCutOperations() {}


	static boolean cutSeam(SurgicalTableBlockEntity operationOwner, Player player, ItemStack shears, InteractionHand hand, int subjectId, int seamId,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams, SurgicalTablePlane.Plane plane,
		SurgicalTableLayout.Proposal proposal, double moveX, double moveZ) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !subject.matchesObservedTopology(observedCubeCount, observedSeams)
			|| seamId < 0 || seamId >= subject.seams.size() || subject.cutSeams.get(seamId))
			return false;
		SurgicalAssembly.Seam seam = subject.seams.get(seamId);
		if (!subject.validPresentCube(seam.first()) || !subject.validPresentCube(seam.second()))
			return false;
		SurgicalCombination protectedCombination = subject.combinationContaining(seam.first());
		if (protectedCombination != null
			&& protectedCombination.contains(subject.persistentId(), seam.second()))
			return false;
		SurgicalTableBlockEntity.SeamCutState cut = SurgicalCutOperations.seamCutState(operationOwner, subject, seamId);
		if (cut == null || !SurgicalCutOperations.validateSeamCut(operationOwner, subject, cut, proposal, moveX, moveZ, plane)
			|| !SurgicalGlueOperations.canPayInteractionCost(shears, 1, player))
			return false;

		subject.setCutSeams(cut.proposedCuts());
		List<Integer> updatedOrder = new ArrayList<>(subject.cutOrder);
		updatedOrder.add(seamId);
		subject.cutOrder = SurgicalAssembly.normalizeCutOrder(updatedOrder, subject.cutSeams,
			subject.seams.size());
		subject.applyLayout(proposal);
		if (cut.separates())
			SurgicalCutOperations.translateOtherSubjects(operationOwner, cut.moving(), subject.persistentId(), new Vec3(moveX, 0.0d, moveZ));
		SurgicalGlueOperations.damageInteractionTool(shears, 1, player, hand);
		operationOwner.clientRenderBounds = null;
		operationOwner.setChangedAndSync();
		if (operationOwner.getLevel() != null)
			operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.15f);
		return true;
	}

	static boolean cutGlueJoint(SurgicalTableBlockEntity operationOwner, Player player, ItemStack shears, InteractionHand hand, int subjectId,
		int glueJointId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		SurgicalTablePlane.Plane plane, double moveX, double moveZ) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !subject.matchesObservedTopology(observedCubeCount, observedSeams)
			|| !SurgicalCutOperations.canApplyGlueCut(operationOwner, subjectId, glueJointId, moveX, moveZ, plane)
			|| !SurgicalGlueOperations.canPayInteractionCost(shears, 1, player))
			return false;
		SurgicalTableBlockEntity.GlueCutState cut = SurgicalCutOperations.glueCutState(operationOwner, subject, glueJointId);
		Vec3 delta = new Vec3(moveX, 0.0d, moveZ);

		Set<SurgicalGlueJoint> removed = Set.of(cut.joint());
		for (SurgicalSubject connected : operationOwner.subjects)
			connected.removeGlueJoints(removed);
		if (cut.separates())
			for (Map.Entry<UUID, BitSet> entry : cut.moving().components().entrySet()) {
				SurgicalSubject moved = operationOwner.getSubjectByPersistentId(entry.getKey());
				if (moved != null)
					moved.translateComponent(entry.getValue(), delta);
			}
		SurgicalGlueOperations.damageInteractionTool(shears, 1, player, hand);
		operationOwner.clientRenderBounds = null;
		operationOwner.setChangedAndSync();
		if (operationOwner.getLevel() != null)
			operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.15f);
		return true;
	}

	static boolean cutCubeConnections(SurgicalTableBlockEntity operationOwner, Player player, ItemStack shears, InteractionHand hand, int subjectId,
		int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal proposal) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !subject.matchesObservedTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId))
			return false;
		if (subject.combinationContaining(cubeId) != null)
			return false;

		List<Integer> updatedOrder = new ArrayList<>(subject.cutOrder);
		BitSet proposedCuts = (BitSet) subject.cutSeams.clone();
		int seamCutCount = 0;
		for (int seamId = 0; seamId < subject.seams.size(); seamId++) {
			if (subject.cutSeams.get(seamId))
				continue;
			SurgicalAssembly.Seam seam = subject.seams.get(seamId);
			if (seam.first() != cubeId && seam.second() != cubeId
				|| !subject.validPresentCube(seam.first()) || !subject.validPresentCube(seam.second()))
				continue;
			proposedCuts.set(seamId);
			updatedOrder.add(seamId);
			seamCutCount++;
		}
		Set<SurgicalGlueJoint> glueCuts = new HashSet<>();
		for (SurgicalGlueJoint joint : subject.glueJoints())
			if (joint.touches(subject.persistentId(), cubeId))
				glueCuts.add(joint);
		int cutCount = seamCutCount + glueCuts.size();
		if (cutCount == 0)
			return false;
		if (!SurgicalGlueOperations.canApplyComponentLayout(operationOwner, subjectId, proposedCuts, proposal, plane)
			|| !SurgicalGlueOperations.canPayInteractionCost(shears, cutCount, player))
			return false;

		subject.setCutSeams(proposedCuts);
		subject.cutOrder = SurgicalAssembly.normalizeCutOrder(updatedOrder, subject.cutSeams,
			subject.seams.size());
		subject.applyLayout(proposal);
		if (!glueCuts.isEmpty())
			for (SurgicalSubject connected : operationOwner.subjects)
				connected.removeGlueJoints(glueCuts);
		SurgicalGlueOperations.damageInteractionTool(shears, cutCount, player, hand);
		operationOwner.setChangedAndSync();
		if (operationOwner.getLevel() != null)
			operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.15f);
		return true;
	}

	/**
	 * Server-authoritative batch cut for layouts that may move retained glue/combination groups across
	 * several subjects. The client supplies only one delta per deterministically ordered post-cut
	 * group; group membership is always rebuilt from server topology before validation or mutation.
	 */
	static boolean cutCubeConnections(SurgicalTableBlockEntity operationOwner, Player player, ItemStack shears, InteractionHand hand, int subjectId,
		int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal proposal, List<Vec3> groupDeltas) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !subject.matchesObservedTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId) || subject.combinationContaining(cubeId) != null)
			return false;
		SurgicalTableBlockEntity.BatchCutState cut = SurgicalCutOperations.batchCutState(operationOwner, subject, cubeId);
		if (cut == null || !SurgicalCutOperations.validateBatchCut(operationOwner, subject, cut, proposal, groupDeltas, plane)
			|| !SurgicalGlueOperations.canPayInteractionCost(shears, cut.cutCount(), player))
			return false;

		subject.setCutSeams(cut.proposedCuts());
		List<Integer> updatedOrder = new ArrayList<>(subject.cutOrder);
		updatedOrder.addAll(cut.cutSeamIds());
		subject.cutOrder = SurgicalAssembly.normalizeCutOrder(updatedOrder, subject.cutSeams,
			subject.seams.size());
		subject.applyLayout(proposal);
		for (int groupId = 0; groupId < cut.groups().size(); groupId++) {
			Vec3 delta = groupDeltas.get(groupId);
			if (delta.horizontalDistanceSqr() <= 1.0e-24d)
				continue;
			for (Map.Entry<UUID, BitSet> entry : cut.groups().get(groupId).components().entrySet()) {
				if (entry.getKey().equals(subject.persistentId()))
					continue;
				SurgicalSubject moved = operationOwner.getSubjectByPersistentId(entry.getKey());
				if (moved != null)
					moved.translateComponent(entry.getValue(), delta);
			}
		}
		if (!cut.glueCuts().isEmpty())
			for (SurgicalSubject connected : operationOwner.subjects)
				connected.removeGlueJoints(cut.glueCuts());
		SurgicalGlueOperations.damageInteractionTool(shears, cut.cutCount(), player, hand);
		operationOwner.clientRenderBounds = null;
		operationOwner.setChangedAndSync();
		if (operationOwner.getLevel() != null)
			operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.15f);
		return true;
	}

	static boolean canApplyBatchCut(SurgicalTableBlockEntity operationOwner, int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams, SurgicalTablePlane.Plane plane,
		SurgicalTableLayout.Proposal proposal, List<Vec3> groupDeltas) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !subject.matchesObservedTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId) || subject.combinationContaining(cubeId) != null)
			return false;
		SurgicalTableBlockEntity.BatchCutState cut = SurgicalCutOperations.batchCutState(operationOwner, subject, cubeId);
		return cut != null && SurgicalCutOperations.validateBatchCut(operationOwner, subject, cut, proposal, groupDeltas, plane);
	}

	static boolean validateBatchCut(SurgicalTableBlockEntity operationOwner, SurgicalSubject edited, SurgicalTableBlockEntity.BatchCutState cut,
		SurgicalTableLayout.Proposal proposal, List<Vec3> groupDeltas, SurgicalTablePlane.Plane plane) {
		if (!plane.valid() || groupDeltas == null || groupDeltas.size() != cut.groups().size()
			|| groupDeltas.isEmpty())
			return false;
		for (int groupId = 0; groupId < groupDeltas.size(); groupId++) {
			Vec3 delta = groupDeltas.get(groupId);
			if (delta == null || !SurgicalCutOperations.validCutDelta(delta.x, delta.z) || Math.abs(delta.y) > 1.0e-9d
				|| groupId == 0 && delta.horizontalDistanceSqr() > 1.0e-18d
				|| groupId > 0 && delta.horizontalDistanceSqr() <= 1.0e-18d)
				return false;
		}

		SurgicalTableBlockEntity.BatchGroupIndex groupIndex = new SurgicalTableBlockEntity.BatchGroupIndex(cut.groups());
		Map<Integer, Vec3> expectedOffsets = new HashMap<>();
		for (int cube = edited.presentCubes.nextSetBit(0); cube >= 0;
			cube = edited.presentCubes.nextSetBit(cube + 1)) {
			int groupId = groupIndex.groupOf(edited.persistentId(), cube);
			Vec3 delta = groupId < 0 ? Vec3.ZERO : groupDeltas.get(groupId);
			expectedOffsets.put(cube, edited.componentOffsets.getOrDefault(cube, Vec3.ZERO).add(delta));
		}
		if (!SurgicalGlueOperations.layoutMatchesTranslations(proposal, expectedOffsets)
			|| !SurgicalTableLayout.validateEditedGlueComponents(plane, edited.cubeCount,
				edited.presentCubes, edited.seams, cut.proposedCuts(), proposal, List.of()))
			return false;

		List<List<SurgicalTableLayout.Footprint>> groupFootprints = new ArrayList<>(cut.groups().size());
		for (int groupId = 0; groupId < cut.groups().size(); groupId++)
			groupFootprints.add(new ArrayList<>());
		List<SurgicalTableLayout.Footprint> obstacles = new ArrayList<>();
		for (SurgicalTableLayout.Footprint footprint : proposal.footprints()) {
			int groupId = groupIndex.groupOf(edited.persistentId(), footprint.componentRoot());
			(groupId < 0 ? obstacles : groupFootprints.get(groupId)).add(footprint);
		}
		for (SurgicalSubject other : operationOwner.subjects) {
			if (other == edited)
				continue;
			// A footprint with no component root stands for the whole subject, which is a per-subject
			// question rather than a per-footprint one; resolve it once instead of inside the loop.
			int wholeSubjectGroup = groupIndex.wholeSubjectGroupOf(cut.groups(), other);
			for (SurgicalTableLayout.Footprint footprint : other.occupiedFootprints()) {
				int groupId = footprint.componentRoot() < 0 ? wholeSubjectGroup
					: groupIndex.groupOf(other.persistentId(), footprint.componentRoot());
				if (groupId < 0) {
					obstacles.add(footprint);
					continue;
				}
				Vec3 delta = groupDeltas.get(groupId);
				SurgicalTableLayout.Footprint translated = SurgicalCutOperations.translateFootprint(footprint, delta);
				if (!plane.workArea().contains(translated.minX(), translated.minZ(), translated.maxX(),
					translated.maxZ(), 1.0e-6d))
					return false;
				groupFootprints.get(groupId).add(translated);
			}
		}
		for (List<SurgicalTableLayout.Footprint> group : groupFootprints)
			if (group.isEmpty())
				return false;
		return SurgicalCutOperations.noCrossGroupConflict(groupFootprints, obstacles);
	}

	/**
	 * Rejects any pair of footprints carrying different group tags. Obstacles all share one tag and
	 * same-group pairs were never compared, so this is exactly the pair of nested loops it replaces -
	 * group against obstacle, and group against every other group - expressed over one tagged set.
	 *
	 * <p>Sorting by {@code minX} lets the inner scan stop at the first footprint that starts beyond
	 * the outer one's reach, since none of the ones after it start any earlier. Cube counts, and
	 * therefore footprint counts, are bounded only by {@link SurgicalAssembly#MAX_CUBES}, and the
	 * whole set arrives from the client.
	 */
	static boolean noCrossGroupConflict(List<List<SurgicalTableLayout.Footprint>> groupFootprints,
		List<SurgicalTableLayout.Footprint> obstacles) {
		List<SurgicalTableBlockEntity.TaggedFootprint> tagged = new ArrayList<>(obstacles.size());
		for (SurgicalTableLayout.Footprint obstacle : obstacles)
			tagged.add(new SurgicalTableBlockEntity.TaggedFootprint(-1, obstacle));
		for (int groupId = 0; groupId < groupFootprints.size(); groupId++)
			for (SurgicalTableLayout.Footprint footprint : groupFootprints.get(groupId))
				tagged.add(new SurgicalTableBlockEntity.TaggedFootprint(groupId, footprint));
		tagged.sort(Comparator.comparingDouble(entry -> entry.footprint().minX()));

		for (int first = 0; first < tagged.size(); first++) {
			SurgicalTableBlockEntity.TaggedFootprint start = tagged.get(first);
			// Deliberately not subtracting conflictsWith's epsilon: a slightly long reach only costs a
			// few extra exact tests, while a short one could skip a real conflict.
			double reach = start.footprint().maxX() + SurgicalTableLayout.COMPONENT_CLEARANCE;
			for (int second = first + 1; second < tagged.size(); second++) {
				SurgicalTableBlockEntity.TaggedFootprint candidate = tagged.get(second);
				if (candidate.footprint().minX() >= reach)
					break;
				if (start.group() != candidate.group()
					&& start.footprint().conflictsWith(candidate.footprint()))
					return false;
			}
		}
		return true;
	}

	static SurgicalTableLayout.Footprint translateFootprint(SurgicalTableLayout.Footprint footprint,
		Vec3 delta) {
		return new SurgicalTableLayout.Footprint(footprint.componentRoot(),
			footprint.minX() + delta.x, footprint.minZ() + delta.z,
			footprint.maxX() + delta.x, footprint.maxZ() + delta.z,
			SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED);
	}


	static boolean canApplyGlueCut(SurgicalTableBlockEntity operationOwner, int subjectId, int glueJointId, double moveX, double moveZ,
		SurgicalTablePlane.Plane plane) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		SurgicalTableBlockEntity.GlueCutState cut = subject == null ? null : SurgicalCutOperations.glueCutState(operationOwner, subject, glueJointId);
		return cut != null && SurgicalCutOperations.canApplySeparatedCut(operationOwner, cut.moving(), cut.separates(), moveX, moveZ, plane);
	}

	static boolean canApplySeamCut(SurgicalTableBlockEntity operationOwner, int subjectId, int seamId, SurgicalTableLayout.Proposal proposal,
		double moveX, double moveZ, SurgicalTablePlane.Plane plane) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		SurgicalTableBlockEntity.SeamCutState cut = subject == null ? null : SurgicalCutOperations.seamCutState(operationOwner, subject, seamId);
		return cut != null && SurgicalCutOperations.validateSeamCut(operationOwner, subject, cut, proposal, moveX, moveZ, plane);
	}

	static boolean canApplySeparatedCut(SurgicalTableBlockEntity operationOwner, SurgicalTableBlockEntity.ComponentGroup moving, boolean separates, double moveX, double moveZ,
		SurgicalTablePlane.Plane plane) {
		if (!plane.valid() || !SurgicalCutOperations.validCutDelta(moveX, moveZ)
			|| !separates && (Math.abs(moveX) > 1.0e-9d || Math.abs(moveZ) > 1.0e-9d))
			return false;
		return !separates || SurgicalCutOperations.canPlaceSeparatedGroup(operationOwner, moving, new Vec3(moveX, 0.0d, moveZ), plane);
	}

	static boolean validateSeamCut(SurgicalTableBlockEntity operationOwner, SurgicalSubject subject, SurgicalTableBlockEntity.SeamCutState cut,
		SurgicalTableLayout.Proposal proposal, double moveX, double moveZ, SurgicalTablePlane.Plane plane) {
		if (!plane.valid() || !SurgicalCutOperations.validCutDelta(moveX, moveZ)
			|| !cut.separates() && (Math.abs(moveX) > 1.0e-9d || Math.abs(moveZ) > 1.0e-9d))
			return false;
		BitSet movedHere = cut.moving().components().getOrDefault(subject.persistentId(), new BitSet());
		Map<Integer, Vec3> expected = new HashMap<>();
		Vec3 delta = new Vec3(moveX, 0.0d, moveZ);
		for (int cube = subject.presentCubes.nextSetBit(0); cube >= 0;
			cube = subject.presentCubes.nextSetBit(cube + 1)) {
			Vec3 offset = subject.componentOffsets.getOrDefault(cube, Vec3.ZERO);
			expected.put(cube, movedHere.get(cube) ? offset.add(delta) : offset);
		}
		if (!SurgicalGlueOperations.layoutMatchesTranslations(proposal, expected)
			|| !SurgicalTableLayout.validateEditedGlueComponents(plane, subject.cubeCount,
				subject.presentCubes, subject.seams, cut.proposedCuts(), proposal, List.of()))
			return false;

		List<SurgicalTableLayout.Footprint> moving = new ArrayList<>();
		List<SurgicalTableLayout.Footprint> fixed = new ArrayList<>();
		for (SurgicalTableLayout.Footprint footprint : proposal.footprints())
			(movedHere.get(footprint.componentRoot()) ? moving : fixed).add(footprint);
		for (SurgicalSubject other : operationOwner.subjects) {
			if (other == subject)
				continue;
			BitSet moved = cut.moving().components().get(other.persistentId());
			for (SurgicalTableLayout.Footprint footprint : other.occupiedFootprints()) {
				if (!other.containsFootprint(moved, footprint)) {
					fixed.add(footprint);
					continue;
				}
				SurgicalTableLayout.Footprint translated = new SurgicalTableLayout.Footprint(
					footprint.componentRoot(), footprint.minX() + moveX, footprint.minZ() + moveZ,
					footprint.maxX() + moveX, footprint.maxZ() + moveZ,
					SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED);
				if (!plane.workArea().contains(translated.minX(), translated.minZ(), translated.maxX(),
					translated.maxZ(), 1.0e-6d))
					return false;
				moving.add(translated);
			}
		}
		for (SurgicalTableLayout.Footprint moved : moving)
			for (SurgicalTableLayout.Footprint obstacle : fixed)
				if (moved.conflictsWith(obstacle))
					return false;
		return true;
	}

	static void translateOtherSubjects(SurgicalTableBlockEntity operationOwner, SurgicalTableBlockEntity.ComponentGroup moving, UUID currentSubject, Vec3 delta) {
		for (Map.Entry<UUID, BitSet> entry : moving.components().entrySet()) {
			if (entry.getKey().equals(currentSubject))
				continue;
			SurgicalSubject subject = operationOwner.getSubjectByPersistentId(entry.getKey());
			if (subject != null)
				subject.translateComponent(entry.getValue(), delta);
		}
	}

	/** Connectivity after Ctrl-shears removes every native and glue edge touching {@code cutCube}. */
	static Map<Integer, BitSet> connectedComponentsAfterCuttingCube(SurgicalTableBlockEntity operationOwner, int subjectId, int startCube, int cutCube,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !subject.matchesObservedTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(startCube) || !subject.validPresentCube(cutCube))
			return Map.of();
		BitSet proposedCuts = (BitSet) subject.cutSeams.clone();
		for (int seamId = 0; seamId < subject.seams.size(); seamId++) {
			SurgicalAssembly.Seam seam = subject.seams.get(seamId);
			if (seam.first() == cutCube || seam.second() == cutCube)
				proposedCuts.set(seamId);
		}
		Set<SurgicalGlueJoint> removedJoints = new HashSet<>();
		for (SurgicalGlueJoint joint : SurgicalTableConnections.allGlueJoints(operationOwner))
			if (joint.touches(subject.persistentId(), cutCube))
				removedJoints.add(joint);
		SurgicalConnectionGraph<UUID> graph = SurgicalTableConnections.connectionGraph(operationOwner, removedJoints,
			Map.of(subject.persistentId(), proposedCuts));
		return graph == null ? Map.of()
			: operationOwner.componentsBySubjectId(new SurgicalTableBlockEntity.ComponentGroup(
				graph.componentContaining(subject.persistentId(), startCube).members()));
	}

	/** Complete post-cut groups for the Ctrl-shears operation, largest and stable remainder first. */
	static @Nullable
	SurgicalTableBlockEntity.BatchCutPlan batchCutPlan(SurgicalTableBlockEntity operationOwner, int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !subject.matchesObservedTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId) || subject.combinationContaining(cubeId) != null)
			return null;
		SurgicalTableBlockEntity.BatchCutState state = SurgicalCutOperations.batchCutState(operationOwner, subject, cubeId);
		if (state == null)
			return null;
		List<Map<Integer, BitSet>> groups = state.groups().stream()
			.map(operationOwner::componentsBySubjectId).toList();
		return new SurgicalTableBlockEntity.BatchCutPlan(state.proposedCuts(), groups);
	}

	static @Nullable
	SurgicalTableBlockEntity.BatchCutState batchCutState(SurgicalTableBlockEntity operationOwner, SurgicalSubject subject, int cubeId) {
		BitSet proposedCuts = (BitSet) subject.cutSeams.clone();
		List<Integer> seamIds = new ArrayList<>();
		for (int seamId = 0; seamId < subject.seams.size(); seamId++) {
			if (subject.cutSeams.get(seamId))
				continue;
			SurgicalAssembly.Seam seam = subject.seams.get(seamId);
			if ((seam.first() == cubeId || seam.second() == cubeId)
				&& subject.validPresentCube(seam.first()) && subject.validPresentCube(seam.second())) {
				proposedCuts.set(seamId);
				seamIds.add(seamId);
			}
		}
		Set<SurgicalGlueJoint> glueCuts = new HashSet<>();
		for (SurgicalGlueJoint joint : SurgicalTableConnections.allGlueJoints(operationOwner))
			if (joint.touches(subject.persistentId(), cubeId))
				glueCuts.add(joint);
		int cutCount = seamIds.size() + glueCuts.size();
		if (cutCount == 0)
			return null;

		SurgicalConnectionGraph<UUID> before = SurgicalTableConnections.connectionGraph(operationOwner, null);
		SurgicalConnectionGraph<UUID> after = SurgicalTableConnections.connectionGraph(operationOwner, glueCuts,
			Map.of(subject.persistentId(), proposedCuts));
		if (before == null || after == null)
			return null;
		SurgicalTableBlockEntity.ComponentGroup affected = new SurgicalTableBlockEntity.ComponentGroup(
			before.componentContaining(subject.persistentId(), cubeId).members());
		if (affected.components().isEmpty())
			return null;
		List<SurgicalTableBlockEntity.ComponentGroup> groups = new ArrayList<>();
		int covered = 0;
		for (SurgicalConnectionGraph.Component<UUID> component : after.components()) {
			SurgicalTableBlockEntity.ComponentGroup group = new SurgicalTableBlockEntity.ComponentGroup(component.members());
			if (!group.intersects(affected))
				continue;
			groups.add(group);
			covered += SurgicalCutOperations.componentSize(group);
		}
		if (groups.isEmpty() || covered != SurgicalCutOperations.componentSize(affected)
			|| groups.size() > SurgicalAssembly.MAX_CUBES)
			return null;
		return new SurgicalTableBlockEntity.BatchCutState(proposedCuts, seamIds, glueCuts, groups, cutCount);
	}

	static @Nullable
	SurgicalTableBlockEntity.SeamCutPlan seamCutPlan(SurgicalTableBlockEntity operationOwner, int subjectId, int seamId) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		SurgicalTableBlockEntity.SeamCutState state = subject == null ? null : SurgicalCutOperations.seamCutState(operationOwner, subject, seamId);
		return state == null ? null : new SurgicalTableBlockEntity.SeamCutPlan(operationOwner.componentsBySubjectId(state.moving()),
			state.proposedCuts(), state.separates());
	}

	static @Nullable
	SurgicalTableBlockEntity.SeamCutState seamCutState(SurgicalTableBlockEntity operationOwner, SurgicalSubject subject, int seamId) {
		if (seamId < 0 || seamId >= subject.seams.size() || subject.cutSeams.get(seamId))
			return null;
		SurgicalAssembly.Seam seam = subject.seams.get(seamId);
		if (!subject.validPresentCube(seam.first()) || !subject.validPresentCube(seam.second())
			|| SurgicalTableConnections.isInternalCombinationSeam(operationOwner, subject.id(), seam))
			return null;
		BitSet proposedCuts = (BitSet) subject.cutSeams.clone();
		proposedCuts.set(seamId);
		SurgicalConnectionGraph<UUID> graph = SurgicalTableConnections.connectionGraph(operationOwner, Set.of(),
			Map.of(subject.persistentId(), proposedCuts));
		if (graph == null)
			return null;
		SurgicalTableBlockEntity.ComponentGroup first = new SurgicalTableBlockEntity.ComponentGroup(
			graph.componentContaining(subject.persistentId(), seam.first()).members());
		SurgicalTableBlockEntity.ComponentGroup second = new SurgicalTableBlockEntity.ComponentGroup(
			graph.componentContaining(subject.persistentId(), seam.second()).members());
		if (first.intersects(second))
			return new SurgicalTableBlockEntity.SeamCutState(proposedCuts, new SurgicalTableBlockEntity.ComponentGroup(Map.of()), false);
		SurgicalTableBlockEntity.ComponentGroup moving = SurgicalCutOperations.componentSize(first) < SurgicalCutOperations.componentSize(second) ? first : second;
		return new SurgicalTableBlockEntity.SeamCutState(proposedCuts, moving, true);
	}

	static @Nullable
	SurgicalTableBlockEntity.GlueCutPlan glueCutPlan(SurgicalTableBlockEntity operationOwner, int subjectId, int glueJointId) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		SurgicalTableBlockEntity.GlueCutState state = subject == null ? null : SurgicalCutOperations.glueCutState(operationOwner, subject, glueJointId);
		return state == null ? null : new SurgicalTableBlockEntity.GlueCutPlan(state.joint(),
			operationOwner.componentsBySubjectId(state.moving()), state.separates());
	}

	static @Nullable
	SurgicalTableBlockEntity.GlueCutState glueCutState(SurgicalTableBlockEntity operationOwner, SurgicalSubject subject, int glueJointId) {
		if (glueJointId < 0 || glueJointId >= subject.glueJoints().size())
			return null;
		SurgicalGlueJoint joint = subject.glueJoints().get(glueJointId);
		if (!joint.touches(subject.persistentId()))
			return null;
		if (SurgicalTableConnections.isInternalCombinationJoint(operationOwner, joint))
			return null;
		SurgicalSubject firstSubject = operationOwner.getSubjectByPersistentId(joint.first().subjectKey());
		SurgicalSubject secondSubject = operationOwner.getSubjectByPersistentId(joint.second().subjectKey());
		if (firstSubject == null || secondSubject == null
			|| !firstSubject.validPresentCube(joint.first().cubeId())
			|| !secondSubject.validPresentCube(joint.second().cubeId()))
			return null;
		SurgicalTableBlockEntity.ComponentGroup first = SurgicalTableConnections.connectedGroup(operationOwner, firstSubject, joint.first().cubeId(), joint);
		SurgicalTableBlockEntity.ComponentGroup second = SurgicalTableConnections.connectedGroup(operationOwner, secondSubject, joint.second().cubeId(), joint);
		if (first.intersects(second))
			return new SurgicalTableBlockEntity.GlueCutState(joint, new SurgicalTableBlockEntity.ComponentGroup(Map.of()), false);
		SurgicalTableBlockEntity.ComponentGroup moving = SurgicalCutOperations.componentSize(first) < SurgicalCutOperations.componentSize(second) ? first : second;
		return new SurgicalTableBlockEntity.GlueCutState(joint, moving, true);
	}

	static Map<Integer, BitSet> componentsBySubjectId(SurgicalTableBlockEntity operationOwner, SurgicalTableBlockEntity.ComponentGroup group) {
		Map<Integer, BitSet> result = new HashMap<>();
		for (SurgicalSubject subject : operationOwner.subjects) {
			BitSet component = group.components().get(subject.persistentId());
			if (component != null && !component.isEmpty())
				result.put(subject.id(), (BitSet) component.clone());
		}
		return Map.copyOf(result);
	}

	static int componentSize(SurgicalTableBlockEntity.ComponentGroup group) {
		int size = 0;
		for (BitSet component : group.components().values())
			size += component.cardinality();
		return size;
	}

	static boolean canPlaceSeparatedGroup(SurgicalTableBlockEntity operationOwner, SurgicalTableBlockEntity.ComponentGroup moving, Vec3 delta,
		SurgicalTablePlane.Plane plane) {
		List<SurgicalTableLayout.Footprint> obstacles = new ArrayList<>();
		List<SurgicalTableLayout.Footprint> translated = new ArrayList<>();
		for (SurgicalSubject subject : operationOwner.subjects) {
			BitSet moved = moving.components().get(subject.persistentId());
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints()) {
				if (!subject.containsFootprint(moved, footprint)) {
					obstacles.add(footprint);
					continue;
				}
				SurgicalTableLayout.Footprint placed = new SurgicalTableLayout.Footprint(
					footprint.componentRoot(), footprint.minX() + delta.x, footprint.minZ() + delta.z,
					footprint.maxX() + delta.x, footprint.maxZ() + delta.z,
					SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED);
				if (!plane.workArea().contains(placed.minX(), placed.minZ(), placed.maxX(), placed.maxZ(), 1.0e-6d))
					return false;
				translated.add(placed);
			}
		}
		if (translated.isEmpty())
			return false;
		for (SurgicalTableLayout.Footprint placed : translated)
			for (SurgicalTableLayout.Footprint obstacle : obstacles)
				if (placed.conflictsWith(obstacle))
					return false;
		return true;
	}

	static boolean validCutDelta(double x, double z) {
		double bound = SurgicalTablePlane.MAX_TILES + 2.0d;
		return Double.isFinite(x) && Double.isFinite(z) && Math.abs(x) <= bound && Math.abs(z) <= bound;
	}
}
