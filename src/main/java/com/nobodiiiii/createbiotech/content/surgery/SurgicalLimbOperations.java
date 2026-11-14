package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** SurgicalLimbOperations algorithms over the canonical SurgicalTableBlockEntity state. */
final class SurgicalLimbOperations {
	private SurgicalLimbOperations() {}


	/**
	 * Installs one anatomical joint. Click order selects the rotating child part and parent part;
	 * when either is a honey combination, the stored endpoints are narrowed to the two cubes that
	 * actually share the seam or glue joint between those parts.
	 */
	static boolean attachLimb(SurgicalTableBlockEntity operationOwner, Player player, ItemStack jointItem, InteractionHand hand,
		SurgicalLimbType type, int childSubjectId, int childCubeId, int parentSubjectId, int parentCubeId,
		int childCubeCount, List<SurgicalAssembly.Seam> childSeams, int parentCubeCount,
		List<SurgicalAssembly.Seam> parentSeams) {
		SurgicalSubject childSubject = operationOwner.getSubject(childSubjectId);
		SurgicalSubject parentSubject = operationOwner.getSubject(parentSubjectId);
		if (type == null || SurgicalKitItem.limbType(jointItem) != type
			|| childSubject == null || parentSubject == null
			|| !childSubject.matchesObservedTopology(childCubeCount, childSeams)
			|| !parentSubject.matchesObservedTopology(parentCubeCount, parentSeams)
			|| !childSubject.validPresentCube(childCubeId)
			|| !parentSubject.validPresentCube(parentCubeId)
			|| childSubject == parentSubject && childCubeId == parentCubeId)
			return false;

		SurgicalGlueJoint.Endpoint selectedChild = new SurgicalGlueJoint.Endpoint(childSubject.persistentId(),
			childCubeId);
		SurgicalGlueJoint.Endpoint selectedParent = new SurgicalGlueJoint.Endpoint(parentSubject.persistentId(),
			parentCubeId);
		SurgicalCombination childCombination = childSubject.combinationContaining(childCubeId);
		if (childCombination != null
			&& childCombination.contains(selectedParent.subjectKey(), selectedParent.cubeId()))
			return SurgicalLimbOperations.refuse(operationOwner, player, "limb_same_combination");
		SurgicalTableBlockEntity.DirectConnection connection = SurgicalLimbOperations.directConnection(operationOwner, selectedChild, selectedParent);
		if (connection == null)
			return SurgicalLimbOperations.refuse(operationOwner, player, "limb_not_connected");
		SurgicalGlueJoint.Endpoint child = connection.child();
		SurgicalGlueJoint.Endpoint parent = connection.parent();

		SurgicalTableBlockEntity.ComponentGroup body = SurgicalTableConnections.connectedGroup(operationOwner, parentSubject, parentCubeId);
		Set<SurgicalLimbJoint> installed = operationOwner.limbsWithin(body);
		int used = 0;
		for (SurgicalLimbJoint existing : installed) {
			if (type.primary() && existing.type() == type)
				used++;
			if (SurgicalLimbOperations.rotatesTogether(operationOwner, existing.child(), child))
				return SurgicalLimbOperations.refuse(operationOwner, player, "limb_already_driven");
		}
		if (type.primary() && used >= type.maxPerBody())
			return SurgicalLimbOperations.refuse(operationOwner, player, "limb_limit");

		SurgicalLimbJoint installedJoint = new SurgicalLimbJoint(type, child, parent);
		Set<SurgicalLimbJoint> prospective = new java.util.LinkedHashSet<>(operationOwner.allLimbJoints());
		prospective.add(installedJoint);
		SurgicalTableBlockEntity.TableLimbTopology topology = SurgicalLimbOperations.limbTopology(operationOwner, prospective);
		if (type.primary() && (!topology.effective().contains(installedJoint)
			|| SurgicalLimbOperations.primaryTouchesDrivenPart(installedJoint, topology)))
			return SurgicalLimbOperations.refuse(operationOwner, player, "limb_primary_conflict");
		if (SurgicalLimbOperations.exceedsSecondaryCapacity(installedJoint, prospective, topology))
			return SurgicalLimbOperations.refuse(operationOwner, player, "limb_secondary_limit_" + type.id());
		operationOwner.attachLimbJoint(installedJoint);
		if (!player.getAbilities().instabuild)
			jointItem.shrink(1);
		operationOwner.setChangedAndSync();
		if (operationOwner.getLevel() != null)
			operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.1f);
		String result = type.secondary() && !topology.effective().contains(installedJoint)
			? "limb_attached_pending_" : "limb_attached_";
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table." + result + type.id()), true);
		return true;
	}

	/** Removes one server-authoritative joint with shears and returns its item. */
	static boolean detachLimb(SurgicalTableBlockEntity operationOwner, Player player, SurgicalLimbJoint joint) {
		if (joint == null || !operationOwner.allLimbJoints().contains(joint))
			return false;
		SurgicalSubject child = operationOwner.getSubjectByPersistentId(joint.child().subjectKey());
		SurgicalSubject parent = operationOwner.getSubjectByPersistentId(joint.parent().subjectKey());
		if (child == null || parent == null || !child.validPresentCube(joint.child().cubeId())
			|| !parent.validPresentCube(joint.parent().cubeId()))
			return false;

		Set<SurgicalLimbJoint> removed = Set.of(joint);
		for (SurgicalSubject subject : operationOwner.subjects)
			subject.removeLimbJoints(removed);
		if (!player.getAbilities().instabuild)
			player.getInventory().placeItemBackInInventory(SurgicalJointItem.createStack(joint.type()));
		operationOwner.setChangedAndSync();
		if (operationOwner.getLevel() != null)
			IWrenchable.playRemoveSound(operationOwner.getLevel(), operationOwner.getBlockPos());
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table.limb_detached_" + joint.type().id()), true);
		return true;
	}

	static boolean refuse(SurgicalTableBlockEntity operationOwner, Player player, String message) {
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table." + message), true);
		return false;
	}

	/**
	 * Finds the real cube pair through which two selected parts physically touch.
	 *
	 * <p>Comparison happens between whole rotating groups rather than single cubes: a honey
	 * combination behaves as one part, so clicking any of its cubes has to find the seam or glue
	 * joint that actually holds the combination against the body.</p>
	 */
	static @Nullable
	SurgicalTableBlockEntity.DirectConnection directConnection(SurgicalTableBlockEntity operationOwner, SurgicalGlueJoint.Endpoint first,
		SurgicalGlueJoint.Endpoint second) {
		List<SurgicalGlueJoint.Endpoint> firstGroup = SurgicalLimbOperations.rotatingGroup(operationOwner, first);
		List<SurgicalGlueJoint.Endpoint> secondGroup = SurgicalLimbOperations.rotatingGroup(operationOwner, second);
		SurgicalConnectionGraph<UUID> graph = SurgicalTableConnections.connectionGraph(operationOwner, Set.of(), Map.of(), false);
		if (graph == null)
			return null;
		if (SurgicalLimbOperations.directlyConnected(operationOwner, first, second, graph))
			return new SurgicalTableBlockEntity.DirectConnection(first, second);
		for (SurgicalGlueJoint.Endpoint child : firstGroup)
			for (SurgicalGlueJoint.Endpoint parent : secondGroup)
				if (SurgicalLimbOperations.directlyConnected(operationOwner, child, parent, graph))
					return new SurgicalTableBlockEntity.DirectConnection(child, parent);
		return null;
	}

	/** Client-safe preview check used to hide a joint's invalid second target before it is clicked. */
	static boolean canConnectLimbTargets(SurgicalTableBlockEntity operationOwner, int firstSubjectId, int firstCubeId,
		int secondSubjectId, int secondCubeId) {
		SurgicalSubject firstSubject = operationOwner.getSubject(firstSubjectId);
		SurgicalSubject secondSubject = operationOwner.getSubject(secondSubjectId);
		if (firstSubject == null || secondSubject == null
			|| !firstSubject.validPresentCube(firstCubeId)
			|| !secondSubject.validPresentCube(secondCubeId))
			return false;
		SurgicalGlueJoint.Endpoint first = new SurgicalGlueJoint.Endpoint(firstSubject.persistentId(),
			firstCubeId);
		SurgicalGlueJoint.Endpoint second = new SurgicalGlueJoint.Endpoint(secondSubject.persistentId(),
			secondCubeId);
		return !SurgicalLimbOperations.rotatesTogether(operationOwner, first, second) && SurgicalLimbOperations.directConnection(operationOwner, first, second) != null;
	}

	static boolean directlyConnected(SurgicalTableBlockEntity operationOwner, SurgicalGlueJoint.Endpoint first, SurgicalGlueJoint.Endpoint second,
		SurgicalConnectionGraph<UUID> graph) {
		return graph.directConnections(first.subjectKey(), first.cubeId())
			.contains(second.subjectKey(), second.cubeId());
	}

	/** The cubes that would turn together with {@code endpoint}: its combination, or itself. */
	static List<SurgicalGlueJoint.Endpoint> rotatingGroup(SurgicalTableBlockEntity operationOwner, SurgicalGlueJoint.Endpoint endpoint) {
		SurgicalSubject subject = operationOwner.getSubjectByPersistentId(endpoint.subjectKey());
		SurgicalCombination combination = subject == null ? null
			: subject.combinationContaining(endpoint.cubeId());
		if (combination == null)
			return List.of(endpoint);
		List<SurgicalGlueJoint.Endpoint> members = new ArrayList<>(combination.members().size());
		for (SurgicalCombination.Member member : combination.members())
			members.add(new SurgicalGlueJoint.Endpoint(member.subjectKey(), member.cubeId()));
		return List.copyOf(members);
	}

	/** Whether two endpoints already move as one part because honey fused their combination. */
	static boolean rotatesTogether(SurgicalTableBlockEntity operationOwner, SurgicalGlueJoint.Endpoint first, SurgicalGlueJoint.Endpoint second) {
		return first.equals(second) || SurgicalLimbOperations.sameCombination(operationOwner, first, second);
	}

	/**
	 * Whether honey fuses two cubes into one part. A combination is stored on every subject that owns
	 * one of its members, so the first endpoint's owner alone can answer without collecting the
	 * whole table's combinations.
	 */
	static boolean sameCombination(SurgicalTableBlockEntity operationOwner, SurgicalGlueJoint.Endpoint first, SurgicalGlueJoint.Endpoint second) {
		SurgicalSubject owner = operationOwner.getSubjectByPersistentId(first.subjectKey());
		SurgicalCombination combination = owner == null ? null : owner.combinationContaining(first.cubeId());
		return combination != null && combination.contains(second.subjectKey(), second.cubeId());
	}

	/** A first-level joint may use an existing second-level parent, but never a joint-driven island. */
	static boolean primaryTouchesDrivenPart(SurgicalLimbJoint candidate,
		SurgicalTableBlockEntity.TableLimbTopology topology) {
		Integer child = topology.components().get(candidate.child());
		Integer parent = topology.components().get(candidate.parent());
		if (child == null || parent == null)
			return true;
		return SurgicalLimbOperations.hasOtherChildAttachment(topology.attachments().get(child), candidate)
			|| SurgicalLimbOperations.hasOtherChildAttachment(topology.attachments().get(parent), candidate);
	}

	/** Enforces the matching second-level allowance on only the arm or leg being edited. */
	static boolean exceedsSecondaryCapacity(SurgicalLimbJoint candidate,
		Set<SurgicalLimbJoint> prospective, SurgicalTableBlockEntity.TableLimbTopology topology) {
		SurgicalLimbType primaryType = candidate.type().primary()
			? candidate.type() : candidate.type().matchingPrimary();
		if (primaryType == null || primaryType.secondaryCapacity() <= 0)
			return false;
		SurgicalGlueJoint.Endpoint primaryChild = candidate.type().primary()
			? candidate.child() : candidate.parent();
		Integer primaryChildComponent = topology.components().get(primaryChild);
		if (primaryChildComponent == null)
			return false;

		int used = 0;
		for (SurgicalLimbJoint joint : prospective) {
			if (joint.type().matchingPrimary() != primaryType)
				continue;
			Integer secondaryParentComponent = topology.components().get(joint.parent());
			if (primaryChildComponent.equals(secondaryParentComponent)) {
				used += joint.type().secondaryCost();
				if (used > primaryType.secondaryCapacity())
					return true;
			}
		}
		return false;
	}

	static boolean hasOtherChildAttachment(@Nullable List<SurgicalTableBlockEntity.TableLimbAttachment> attachments,
		SurgicalLimbJoint candidate) {
		if (attachments == null)
			return false;
		for (SurgicalTableBlockEntity.TableLimbAttachment attachment : attachments)
			if (attachment.child() && !attachment.limb().equals(candidate))
				return true;
		return false;
	}

	/**
	 * Resolves automatic cube/combination ownership for the currently installed anatomical joints.
	 * Second-level joints are retained even when absent from {@link TableLimbTopology#effective}; a
	 * later matching first-level joint can activate them without reinstalling the knee or elbow.
	 */
	static SurgicalTableBlockEntity.TableLimbTopology limbTopology(SurgicalTableBlockEntity operationOwner, Set<SurgicalLimbJoint> installed) {
		if (installed.isEmpty())
			return SurgicalTableBlockEntity.TableLimbTopology.EMPTY;
		// Normal seams and glue do not merge anatomical ownership. Only an explicit honey combination
		// turns several cubes into one unit for the rules below.
		Map<SurgicalGlueJoint.Endpoint, Integer> componentIds = new HashMap<>();
		int nextComponentId = 0;
		for (SurgicalCombination combination : SurgicalTableConnections.allCombinations(operationOwner)) {
			int componentId = nextComponentId++;
			for (SurgicalCombination.Member member : combination.members())
				componentIds.put(new SurgicalGlueJoint.Endpoint(member.subjectKey(), member.cubeId()),
					componentId);
		}
		for (SurgicalSubject subject : operationOwner.subjects) {
			for (int cubeId = subject.presentCubes.nextSetBit(0); cubeId >= 0;
				cubeId = subject.presentCubes.nextSetBit(cubeId + 1)) {
				SurgicalGlueJoint.Endpoint endpoint =
					new SurgicalGlueJoint.Endpoint(subject.persistentId(), cubeId);
				if (!componentIds.containsKey(endpoint))
					componentIds.put(endpoint, nextComponentId++);
			}
		}

		Map<Integer, List<SurgicalTableBlockEntity.TableLimbAttachment>> attachments = new HashMap<>();
		Map<SurgicalLimbJoint, Integer> children = new HashMap<>();
		Map<SurgicalLimbJoint, Integer> parents = new HashMap<>();
		for (SurgicalLimbJoint limb : installed) {
			Integer child = componentIds.get(limb.child());
			Integer parent = componentIds.get(limb.parent());
			if (child == null || parent == null || child.equals(parent))
				continue;
			children.put(limb, child);
			parents.put(limb, parent);
			attachments.computeIfAbsent(child, ignored -> new ArrayList<>())
				.add(new SurgicalTableBlockEntity.TableLimbAttachment(limb, true));
			attachments.computeIfAbsent(parent, ignored -> new ArrayList<>())
				.add(new SurgicalTableBlockEntity.TableLimbAttachment(limb, false));
		}

		Map<Integer, SurgicalLimbJoint> owners = new HashMap<>();
		for (Map.Entry<Integer, List<SurgicalTableBlockEntity.TableLimbAttachment>> entry : attachments.entrySet()) {
			List<SurgicalTableBlockEntity.TableLimbAttachment> attached = entry.getValue();
			List<SurgicalLimbJoint> primaryChildren = new ArrayList<>();
			boolean primaryParent = false;
			List<SurgicalLimbJoint> secondaryChildren = new ArrayList<>();
			for (SurgicalTableBlockEntity.TableLimbAttachment attachment : attached) {
				if (attachment.child() && attachment.limb().type().primary())
					primaryChildren.add(attachment.limb());
				else if (!attachment.child() && attachment.limb().type().primary())
					primaryParent = true;
				else if (attachment.child() && attachment.limb().type().secondary())
					secondaryChildren.add(attachment.limb());
			}
			if (primaryChildren.size() == 1 && !primaryParent)
				owners.put(entry.getKey(), primaryChildren.getFirst());
			else if (primaryChildren.isEmpty() && !primaryParent && secondaryChildren.size() == 1
				&& attached.size() == 1)
				owners.put(entry.getKey(), secondaryChildren.getFirst());
		}

		Set<SurgicalLimbJoint> effective = new java.util.LinkedHashSet<>();
		for (SurgicalLimbJoint limb : installed) {
			Integer child = children.get(limb);
			Integer parent = parents.get(limb);
			if (child == null || parent == null || !limb.equals(owners.get(child)))
				continue;
			if (limb.type().primary() && attachments.getOrDefault(parent, List.of()).stream()
				.anyMatch(attachment -> attachment.child() && attachment.limb().type().primary()))
				continue;
			if (limb.type().secondary()) {
				SurgicalLimbJoint primary = owners.get(parent);
				if (primary == null || primary.type() != limb.type().matchingPrimary())
					continue;
			}
			effective.add(limb);
		}
		Map<Integer, List<SurgicalTableBlockEntity.TableLimbAttachment>> frozenAttachments = new HashMap<>();
		attachments.forEach((component, attached) ->
			frozenAttachments.put(component, List.copyOf(attached)));
		return new SurgicalTableBlockEntity.TableLimbTopology(Set.copyOf(effective), Map.copyOf(componentIds),
			Map.copyOf(frozenAttachments));
	}
}
