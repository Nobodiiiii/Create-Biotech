package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** SurgicalGlueOperations algorithms over the canonical SurgicalTableBlockEntity state. */
final class SurgicalGlueOperations {
	private SurgicalGlueOperations() {}


	static boolean combineConnected(SurgicalTableBlockEntity operationOwner, Player player, ItemStack honeyBottle, InteractionHand hand,
		int subjectId, int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !SurgicalKitItem.isHoneyBottle(honeyBottle)
			|| !subject.matchesObservedTopology(observedCubeCount, observedSeams)
			|| !subject.validPresentCube(cubeId))
			return false;
		SurgicalTableBlockEntity.ComponentGroup connected = SurgicalTableConnections.connectedGroup(operationOwner, subject, cubeId);
		List<SurgicalCombination.Member> members = new ArrayList<>();
		for (Map.Entry<UUID, BitSet> entry : connected.components().entrySet())
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
				cube = entry.getValue().nextSetBit(cube + 1))
				members.add(new SurgicalCombination.Member(entry.getKey(), cube));
		if (members.size() < 2)
			return false;

		Set<SurgicalCombination> replaced = new HashSet<>();
		for (SurgicalCombination existing : SurgicalTableConnections.allCombinations(operationOwner)) {
			boolean intersects = false;
			for (SurgicalCombination.Member member : existing.members())
				if (connected.contains(member)) {
					intersects = true;
					break;
				}
			if (intersects)
				replaced.add(existing);
		}
		if (replaced.size() == 1) {
			SurgicalCombination existing = replaced.iterator().next();
			if (existing.members().size() == members.size() && existing.members().containsAll(members))
				return false;
		}
		SurgicalCombination combination = SurgicalCombination.create(members);
		if (combination == null || !SurgicalGlueOperations.canPayInteractionCost(honeyBottle, 1, player))
			return false;
		for (SurgicalSubject candidate : operationOwner.subjects)
			candidate.removeCombinations(replaced);
		SurgicalGlueOperations.attachCombination(operationOwner, combination);
		SurgicalGlueOperations.consumeHoneyBottle(player, honeyBottle, hand);
		operationOwner.clientRenderBounds = null;
		operationOwner.setChangedAndSync();
		if (operationOwner.getLevel() != null)
			operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.HONEY_DRINK, SoundSource.BLOCKS, 0.8f, 1.1f);
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table.combine_success"), true);
		return true;
	}

	static boolean breakCombination(SurgicalTableBlockEntity operationOwner, Player player, ItemStack shears, InteractionHand hand,
		int subjectId, int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !SurgicalKitItem.isShears(shears)
			|| !subject.matchesObservedTopology(observedCubeCount, observedSeams))
			return false;
		SurgicalCombination combination = subject.combinationContaining(cubeId);
		if (combination == null || !SurgicalTableConnections.externalCombinationJoints(operationOwner, combination).isEmpty()
			|| !SurgicalGlueOperations.canPayInteractionCost(shears, 1, player))
			return false;
		Set<SurgicalCombination> removed = Set.of(combination);
		for (SurgicalSubject candidate : operationOwner.subjects)
			candidate.removeCombinations(removed);
		SurgicalGlueOperations.damageInteractionTool(shears, 1, player, hand);
		operationOwner.setChangedAndSync();
		if (operationOwner.getLevel() != null)
			operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.05f);
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table.combination_broken"), true);
		return true;
	}

	static boolean detachCombination(SurgicalTableBlockEntity operationOwner, Player player, ItemStack shears, InteractionHand hand,
		int subjectId, int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !SurgicalKitItem.isShears(shears)
			|| !subject.matchesObservedTopology(observedCubeCount, observedSeams))
			return false;
		SurgicalCombination combination = subject.combinationContaining(cubeId);
		if (combination == null || !SurgicalGlueOperations.canPayInteractionCost(shears, 1, player))
			return false;
		List<SurgicalGlueJoint> external = SurgicalTableConnections.externalCombinationJoints(operationOwner, combination);
		if (external.isEmpty())
			return false;
		Set<SurgicalGlueJoint> removed = Set.copyOf(external);
		for (SurgicalSubject candidate : operationOwner.subjects)
			candidate.removeGlueJoints(removed);
		SurgicalGlueOperations.damageInteractionTool(shears, 1, player, hand);
		operationOwner.clientRenderBounds = null;
		operationOwner.setChangedAndSync();
		if (operationOwner.getLevel() != null)
			operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.1f);
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table.combination_detached"), true);
		return true;
	}

	static void consumeHoneyBottle(Player player, ItemStack honeyBottle, InteractionHand hand) {
		if (!SurgicalGlueOperations.consumeInteractionItems() || player.getAbilities().instabuild)
			return;
		if (SurgicalKitItem.isKit(honeyBottle)) {
			SurgicalGlueOperations.damageInteractionTool(honeyBottle, 1, player, hand);
			return;
		}
		honeyBottle.shrink(1);
		ItemStack emptyBottle = new ItemStack(Items.GLASS_BOTTLE);
		if (honeyBottle.isEmpty()) {
			player.setItemInHand(hand, emptyBottle);
			return;
		}
		if (!player.getInventory().add(emptyBottle))
			player.drop(emptyBottle, false);
	}

	/**
	 * Adds an in-place glue edge between two physically touching cubes. The new seam may close a cycle in
	 * the model's native/glue graph, but it must not duplicate an existing native seam or glue joint.
	 */
	static boolean addSlimeSeam(SurgicalTableBlockEntity operationOwner, Player player, ItemStack slimeBall, InteractionHand hand,
		int firstSubjectId, int firstCubeId, int firstObservedCubeCount,
		List<SurgicalAssembly.Seam> firstObservedSeams,
		int secondSubjectId, int secondCubeId, int secondObservedCubeCount,
		List<SurgicalAssembly.Seam> secondObservedSeams) {
		SurgicalSubject first = operationOwner.getSubject(firstSubjectId);
		SurgicalSubject second = operationOwner.getSubject(secondSubjectId);
		if (!SurgicalKitItem.isSlimeBall(slimeBall) || first == null || second == null
			|| !first.matchesObservedTopology(firstObservedCubeCount, firstObservedSeams)
			|| !second.matchesObservedTopology(secondObservedCubeCount, secondObservedSeams)
			|| !SurgicalGlueOperations.canAddSlimeSeamTargets(operationOwner, firstSubjectId, firstCubeId, secondSubjectId, secondCubeId)
			|| !SurgicalGlueOperations.canPayInteractionCost(slimeBall, 1, player))
			return false;

		SurgicalGlueJoint joint = SurgicalGlueJoint.of(
			new SurgicalGlueJoint.Endpoint(first.persistentId(), firstCubeId),
			new SurgicalGlueJoint.Endpoint(second.persistentId(), secondCubeId));
		SurgicalGlueOperations.attachJoint(operationOwner, joint);
		if (SurgicalGlueOperations.consumeInteractionItems() && !player.getAbilities().instabuild)
			SurgicalGlueOperations.consumeMaterial(slimeBall, player, hand);
		operationOwner.setChangedAndSync();
		if (operationOwner.getLevel() != null)
			operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.SLIME_BLOCK_PLACE,
				SoundSource.BLOCKS, 0.6f, 1.05f);
		player.displayClientMessage(Component.translatable(
			"message.create_biotech.surgical_table.slime_seam_success"), true);
		return true;
	}

	/** Client-safe structural half of slime-seam validation; current cube geometry is checked client-side. */
	static boolean canAddSlimeSeamTargets(SurgicalTableBlockEntity operationOwner, int firstSubjectId, int firstCubeId,
		int secondSubjectId, int secondCubeId) {
		SurgicalSubject first = operationOwner.getSubject(firstSubjectId);
		SurgicalSubject second = operationOwner.getSubject(secondSubjectId);
		if (first == null || second == null || !first.validPresentCube(firstCubeId)
			|| !second.validPresentCube(secondCubeId)
			|| first == second && firstCubeId == secondCubeId
			|| SurgicalTableConnections.allGlueJoints(operationOwner).size() >= SurgicalAssembly.MAX_SEAMS)
			return false;
		SurgicalConnectionGraph<UUID> abstractGraph = SurgicalTableConnections.connectionGraph(operationOwner, Set.of(), Map.of(), false);
		return abstractGraph != null
			&& !abstractGraph.directConnections(first.persistentId(), firstCubeId)
				.contains(second.persistentId(), secondCubeId);
	}

	static boolean glueComponents(SurgicalTableBlockEntity operationOwner, Player player, ItemStack glue, InteractionHand hand,
		int firstSubjectId, int firstCubeId, int secondSubjectId, int secondCubeId,
		SurgicalLayPose targetPose, List<SurgicalTableGluePacket.Move> moves,
		List<SurgicalTableGluePacket.AnchorMove> anchorMoves, SurgicalGlueTransform replayTransform,
		SurgicalGlueContact anchorContact,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal firstLayout,
		SurgicalTableLayout.Proposal secondLayout) {
		boolean smartGlue = SurgicalKitItem.isSmartGlue(glue);
		if (replayTransform == null || anchorContact == null
			|| !SurgicalGlueTransform.withinEditRange(replayTransform.translation())
			|| !smartGlue && !replayTransform.isIdentity()
			|| !SurgicalGlueOperations.canPayInteractionCost(glue, 1, player))
			return false;
		SurgicalTableBlockEntity.ValidatedGluePlan plan = SurgicalGlueOperations.validateGluePlan(operationOwner, firstSubjectId, firstCubeId,
			secondSubjectId, secondCubeId, targetPose, moves, anchorMoves, plane, firstLayout, secondLayout,
			smartGlue);
		if (plan == null)
			return false;
		return SurgicalGlueOperations.applyGluePlan(operationOwner, player, glue, hand, firstCubeId, secondCubeId, targetPose,
			firstLayout, secondLayout, plan, replayTransform, anchorContact, true);
	}

	/**
	 * Applies the same movement and joint transaction as strong glue after verifying the chosen
	 * reference joint. A normal click requires its anchor to belong to the selected honey combination;
	 * Ctrl mirrors around that one anchor cube directly. The selected centre plane is already expressed
	 * by the mirrored endpoint and replay transform. The wand itself is neither consumed nor damaged.
	 */
	static boolean symmetryGlueComponents(SurgicalTableBlockEntity operationOwner, Player player, ItemStack wand, InteractionHand hand,
		int firstSubjectId, int firstCubeId, int secondSubjectId, int secondCubeId,
		int referenceSubjectId, int referenceCubeId, UUID referenceAnchorSubjectKey,
		int referenceAnchorCubeId, boolean singleCube, SurgicalLayPose targetPose,
		List<SurgicalTableGluePacket.Move> moves, List<SurgicalTableGluePacket.AnchorMove> anchorMoves,
		SurgicalGlueTransform replayTransform, SurgicalGlueContact anchorContact,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal firstLayout,
		SurgicalTableLayout.Proposal secondLayout) {
		SurgicalSubject referenceSubject = operationOwner.getSubject(referenceSubjectId);
		SurgicalSubject referenceAnchorSubject = operationOwner.getSubjectByPersistentId(referenceAnchorSubjectKey);
		SurgicalSubject mirroredAnchorSubject = operationOwner.getSubject(secondSubjectId);
		if (referenceSubject == null || referenceAnchorSubject == null || mirroredAnchorSubject == null
			|| !referenceSubject.validPresentCube(referenceCubeId)
			|| !referenceAnchorSubject.validPresentCube(referenceAnchorCubeId))
			return false;
		SurgicalGlueJoint.Endpoint referenceEndpoint =
			new SurgicalGlueJoint.Endpoint(referenceSubject.persistentId(), referenceCubeId);
		SurgicalGlueJoint.Endpoint referenceAnchor =
			new SurgicalGlueJoint.Endpoint(referenceAnchorSubjectKey, referenceAnchorCubeId);
		SurgicalGlueJoint referenceJoint = SurgicalGlueOperations.findGlueJoint(referenceSubject, referenceEndpoint, referenceAnchor);
		if (referenceJoint == null || replayTransform == null || anchorContact == null
			|| SurgicalTableConnections.isInternalCombinationJoint(operationOwner, referenceJoint))
			return false;
		SurgicalGlueTransform recorded = referenceJoint.replayFrom(referenceEndpoint);
		if ((referenceJoint.replay() != null && recorded == null)
			|| (recorded == null && !replayTransform.isIdentity())
			|| (recorded != null && !recorded.mirrorMagnitudeMatches(replayTransform)))
			return false;
		SurgicalCombination combination = referenceAnchorSubject.combinationContaining(referenceAnchorCubeId);
		boolean validAnchor = singleCube
			? mirroredAnchorSubject.persistentId().equals(referenceAnchorSubjectKey)
				&& secondCubeId == referenceAnchorCubeId
			: combination != null && combination.contains(mirroredAnchorSubject.persistentId(), secondCubeId);
		if (!validAnchor)
			return false;

		SurgicalTableBlockEntity.ValidatedGluePlan plan = SurgicalGlueOperations.validateGluePlan(operationOwner, firstSubjectId, firstCubeId,
			secondSubjectId, secondCubeId, targetPose, moves, anchorMoves, plane, firstLayout, secondLayout,
			!replayTransform.isIdentity());
		if (plan == null)
			return false;
		return SurgicalGlueOperations.applyGluePlan(operationOwner, player, wand, hand, firstCubeId, secondCubeId, targetPose,
			firstLayout, secondLayout, plan, replayTransform, anchorContact, false);
	}

	static @Nullable
	SurgicalGlueJoint findGlueJoint(SurgicalSubject subject,
		SurgicalGlueJoint.Endpoint first, SurgicalGlueJoint.Endpoint second) {
		for (SurgicalGlueJoint joint : subject.glueJoints())
			if (joint.touches(first.subjectKey(), first.cubeId())
				&& joint.touches(second.subjectKey(), second.cubeId()))
				return joint;
		return null;
	}

	static boolean applyGluePlan(SurgicalTableBlockEntity operationOwner, Player player, ItemStack tool, InteractionHand hand,
		int firstCubeId, int secondCubeId, SurgicalLayPose targetPose,
		SurgicalTableLayout.Proposal firstLayout, SurgicalTableLayout.Proposal secondLayout,
		SurgicalTableBlockEntity.ValidatedGluePlan plan, SurgicalGlueTransform replayTransform,
		SurgicalGlueContact anchorContact, boolean damageTool) {
		SurgicalSubject first = plan.first();
		SurgicalSubject second = plan.second();
		SurgicalTableBlockEntity.ComponentGroup moving = plan.moving();
		Map<UUID, SurgicalTableBlockEntity.ValidatedGlueMove> validated = plan.moves();
		Map<UUID, Map<Integer, Vec3>> validatedAnchors = plan.anchorMoves();

		// Endpoint normalization is part of the same accepted plan. Apply it only after every move
		// and anchor has passed validation, so a rejected packet cannot partially change the table.
		first.applyLayout(firstLayout);
		if (first != second)
			second.applyLayout(secondLayout);

		Set<SurgicalGlueJoint> existingJoints = SurgicalTableConnections.allGlueJoints(operationOwner);
		Set<SurgicalCombination> existingCombinations = SurgicalTableConnections.allCombinations(operationOwner);
		Set<SurgicalLimbJoint> existingLimbs = operationOwner.allLimbJoints();
		Map<UUID, SurgicalTableBlockEntity.ExtractedSubject> extracted = new HashMap<>();
		for (Map.Entry<UUID, BitSet> entry : moving.components().entrySet()) {
			SurgicalSubject original = operationOwner.getSubjectByPersistentId(entry.getKey());
			SurgicalTableBlockEntity.ValidatedGlueMove move = validated.get(entry.getKey());
			if (original == null || move == null)
				return false;
			BitSet selected = entry.getValue();
			SurgicalSubject moved = original;
			if (!selected.equals(original.presentCubes)) {
				moved = original.extract(operationOwner.allocateSubjectId(), selected);
				operationOwner.addSubject(moved);
				extracted.put(original.persistentId(), new SurgicalTableBlockEntity.ExtractedSubject((BitSet) selected.clone(), moved));
			}
			moved.applyGlueMove(second.placementFacing(), targetPose, move.offsets(), move.rotations(),
				move.layout().footprints());
		}
		for (Map.Entry<UUID, Map<Integer, Vec3>> entry : validatedAnchors.entrySet()) {
			SurgicalSubject anchoredSubject = operationOwner.getSubjectByPersistentId(entry.getKey());
			BitSet selected = plan.anchored().components().get(entry.getKey());
			if (anchoredSubject != null && selected != null)
				anchoredSubject.applyComponentOffsets(selected, entry.getValue());
		}

		List<SurgicalGlueJoint> remappedJoints = existingJoints.stream()
			.map(joint -> SurgicalGlueOperations.remapJoint(joint, extracted)).toList();
		for (SurgicalSubject subject : operationOwner.subjects)
			subject.replaceGlueJoints(List.of());
		for (SurgicalGlueJoint joint : remappedJoints)
			SurgicalGlueOperations.attachJoint(operationOwner, joint);
		// Glue joins motion components but never fuses their honey identities. Reattach every
		// pre-existing combination separately so the blue glue boundary remains meaningful.
		for (SurgicalSubject subject : operationOwner.subjects)
			subject.replaceCombinations(List.of());
		for (SurgicalCombination combination : existingCombinations) {
			SurgicalCombination remapped = SurgicalGlueOperations.remapCombination(combination, extracted);
			if (remapped != null)
				SurgicalGlueOperations.attachCombination(operationOwner, remapped);
		}
		for (SurgicalSubject subject : operationOwner.subjects)
			subject.replaceLimbJoints(List.of());
		for (SurgicalLimbJoint limb : existingLimbs)
			operationOwner.attachLimbJoint(new SurgicalLimbJoint(limb.type(), SurgicalGlueOperations.remapEndpoint(limb.child(), extracted),
				SurgicalGlueOperations.remapEndpoint(limb.parent(), extracted)));

		SurgicalSubject movedFirst = SurgicalGlueOperations.remappedSubject(first, firstCubeId, extracted);
		SurgicalGlueJoint joint = SurgicalGlueJoint.attached(
			new SurgicalGlueJoint.Endpoint(movedFirst.persistentId(), firstCubeId),
			new SurgicalGlueJoint.Endpoint(second.persistentId(), secondCubeId), replayTransform, anchorContact);
		SurgicalGlueOperations.attachJoint(operationOwner, joint);
		if (damageTool)
			SurgicalGlueOperations.damageInteractionTool(tool, 1, player, hand);
		else
			player.getCooldowns().addCooldown(tool.getItem(), 5);
		operationOwner.clientRenderBounds = null;
		operationOwner.setChangedAndSync();
		if (operationOwner.getLevel() != null) {
			operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 1.0f);
			operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.5f, 0.9f);
			if (!damageTool)
				operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5f, 1.35f);
		}
		return true;
	}

	static boolean consumeInteractionItems() {
		return CBConfigs.SERVER.surgicalTable.consumeInteractionItems.get();
	}

	static boolean canPayInteractionCost(ItemStack stack, int amount, Player player) {
		return !SurgicalGlueOperations.consumeInteractionItems() || player.getAbilities().instabuild
			|| SurgicalKitItem.hasDurability(stack, amount);
	}

	static void consumeMaterial(ItemStack stack, Player player, InteractionHand hand) {
		if (SurgicalKitItem.isKit(stack))
			SurgicalGlueOperations.damageInteractionTool(stack, 1, player, hand);
		else
			stack.shrink(1);
	}

	static void damageInteractionTool(ItemStack tool, int amount, Player player,
		InteractionHand hand) {
		if (!SurgicalGlueOperations.consumeInteractionItems())
			return;
		SurgicalKitBlockEntity loan = player instanceof ServerPlayer serverPlayer && SurgicalKitItem.isKit(tool)
			? SurgicalKitBlockEntity.findLoan(serverPlayer.serverLevel(), tool) : null;
		tool.hurtAndBreak(amount, player, LivingEntity.getSlotForHand(hand));
		if (loan != null && tool.isEmpty())
			loan.getLevel().destroyBlock(loan.getBlockPos(), false);
	}

	/**
	 * Builds the immutable assembly that packing would create, without changing table state.
	 * The client uses this exact source ordering while baking generated combat geometry.
	 */
	static @Nullable
	SurgicalAssembly previewPackedAssembly(SurgicalTableBlockEntity operationOwner, int subjectId, int cubeId, int observedCubeCount,
		List<SurgicalAssembly.Seam> observedSeams) {
		SurgicalTableBlockEntity.ComponentGroup group = SurgicalPackingOperations.previewPackedGroup(operationOwner, subjectId, cubeId, observedCubeCount, observedSeams);
		return group == null ? null : SurgicalTableConnections.packedAssembly(operationOwner, group,
			SurgicalTableConnections.packingAnchor(operationOwner, group));
	}

	/** Uses the exact same non-mutating acceptance path as {@link #glueComponents}. */
	static boolean canGlueComponents(SurgicalTableBlockEntity operationOwner, ItemStack glue, int firstSubjectId, int firstCubeId,
		int secondSubjectId, int secondCubeId, SurgicalLayPose targetPose,
		List<SurgicalTableGluePacket.Move> moves, List<SurgicalTableGluePacket.AnchorMove> anchorMoves,
		SurgicalGlueTransform replayTransform,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal firstLayout,
		SurgicalTableLayout.Proposal secondLayout) {
		if (replayTransform == null || !SurgicalGlueTransform.withinEditRange(replayTransform.translation()))
			return false;
		return SurgicalGlueOperations.validateGluePlan(operationOwner, firstSubjectId, firstCubeId, secondSubjectId, secondCubeId,
			targetPose, moves, anchorMoves, plane, firstLayout, secondLayout,
			SurgicalKitItem.isSmartGlue(glue) || !replayTransform.isIdentity()) != null;
	}

	static @Nullable
	SurgicalTableBlockEntity.ValidatedGluePlan validateGluePlan(SurgicalTableBlockEntity operationOwner, int firstSubjectId, int firstCubeId,
		int secondSubjectId, int secondCubeId, SurgicalLayPose targetPose,
		List<SurgicalTableGluePacket.Move> moves, List<SurgicalTableGluePacket.AnchorMove> anchorMoves,
		SurgicalTablePlane.Plane plane, SurgicalTableLayout.Proposal firstLayout,
		SurgicalTableLayout.Proposal secondLayout, boolean allowEditedTransforms) {
		SurgicalSubject first = operationOwner.getSubject(firstSubjectId);
		SurgicalSubject second = operationOwner.getSubject(secondSubjectId);
		if (first == null || second == null || !first.validPresentCube(firstCubeId)
			|| !second.validPresentCube(secondCubeId) || targetPose == null
			|| !targetPose.equals(second.layPose()) || !plane.valid()
			|| !SurgicalGlueOperations.validateGlueLayout(operationOwner, first, plane, firstLayout)
			|| first != second && !SurgicalGlueOperations.validateGlueLayout(operationOwner, second, plane, secondLayout))
			return null;

		SurgicalTableBlockEntity.ComponentGroup moving = SurgicalTableConnections.connectedGroup(operationOwner, first, firstCubeId);
		SurgicalTableBlockEntity.ComponentGroup anchored = SurgicalTableConnections.connectedGroup(operationOwner, second, secondCubeId);
		Map<UUID, SurgicalTableBlockEntity.ValidatedGlueMove> validated = SurgicalGlueOperations.validateGlueMoves(operationOwner, moving, anchored, moves, plane,
			targetPose, allowEditedTransforms);
		Map<UUID, Map<Integer, Vec3>> validatedAnchors = SurgicalGlueOperations.validateGlueAnchors(operationOwner, anchored, anchorMoves);
		if (moving.intersects(anchored) || validated == null || validatedAnchors == null)
			return null;
		return new SurgicalTableBlockEntity.ValidatedGluePlan(first, second, moving, anchored, validated, validatedAnchors);
	}

	static @Nullable
	Map<UUID, SurgicalTableBlockEntity.ValidatedGlueMove> validateGlueMoves(SurgicalTableBlockEntity operationOwner, SurgicalTableBlockEntity.ComponentGroup moving, SurgicalTableBlockEntity.ComponentGroup anchored,
		List<SurgicalTableGluePacket.Move> moves, SurgicalTablePlane.Plane plane,
		SurgicalLayPose targetPose, boolean allowPerCubeOffsets) {
		if (moves == null || moves.size() != moving.components().size())
			return null;
		int requiredSplits = 0;
		Map<UUID, SurgicalTableBlockEntity.ValidatedGlueMove> validated = new HashMap<>();
		List<SurgicalTableLayout.Footprint> obstacles = SurgicalGlueOperations.glueMoveObstacles(operationOwner, moving, anchored);
		for (SurgicalTableGluePacket.Move move : moves) {
			SurgicalSubject subject = operationOwner.getSubject(move.subjectId());
			if (subject == null || validated.containsKey(subject.persistentId()))
				return null;
			BitSet selected = moving.components().get(subject.persistentId());
			if (selected == null || selected.isEmpty())
				return null;
			Map<Integer, Vec3> offsets = new HashMap<>();
			Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>();
			for (SurgicalTableGluePacket.CubeTranslation translation : move.translations())
				if (!translation.valid() || !selected.get(translation.cubeId())
					|| offsets.putIfAbsent(translation.cubeId(), translation.offset()) != null
					|| rotations.putIfAbsent(translation.cubeId(), translation.rotation()) != null)
					return null;
			boolean permitPerCubeOffsets = allowPerCubeOffsets
				|| SurgicalGlueOperations.hasNonUniformStoredOffsets(subject, selected);
			if (offsets.size() != selected.cardinality()
				|| !SurgicalGlueOperations.layoutMatchesTranslations(move.layout(), offsets)
				|| !permitPerCubeOffsets && !SurgicalGlueOperations.componentYTranslationsMatch(subject, selected, offsets)
				|| !SurgicalGlueOperations.componentRotationsMatch(subject, selected, rotations, targetPose)
				|| !(permitPerCubeOffsets
					? SurgicalTableLayout.validateEditedGlueComponents(plane, subject.cubeCount, selected,
						subject.seams, subject.cutSeams, move.layout(), obstacles)
					: SurgicalTableLayout.validateGlueComponents(plane, subject.cubeCount, selected,
						subject.seams, subject.cutSeams, move.layout(), obstacles)))
				return null;
			if (!selected.equals(subject.presentCubes))
				requiredSplits++;
			validated.put(subject.persistentId(), new SurgicalTableBlockEntity.ValidatedGlueMove(Map.copyOf(offsets),
				Map.copyOf(rotations), move.layout()));
		}
		return validated.keySet().equals(moving.components().keySet())
			&& operationOwner.subjects.size() <= SurgicalTableBlockEntity.MAX_SUBJECTS - requiredSplits ? Map.copyOf(validated) : null;
	}

	static @Nullable
	Map<UUID, Map<Integer, Vec3>> validateGlueAnchors(SurgicalTableBlockEntity operationOwner, SurgicalTableBlockEntity.ComponentGroup anchored,
		List<SurgicalTableGluePacket.AnchorMove> moves) {
		if (moves == null || moves.size() != anchored.components().size())
			return null;
		Map<UUID, Map<Integer, Vec3>> validated = new HashMap<>();
		Double commonLift = null;
		for (SurgicalTableGluePacket.AnchorMove move : moves) {
			SurgicalSubject subject = operationOwner.getSubject(move.subjectId());
			if (subject == null || validated.containsKey(subject.persistentId()))
				return null;
			BitSet selected = anchored.components().get(subject.persistentId());
			if (selected == null || selected.isEmpty())
				return null;
			Map<Integer, Vec3> offsets = new HashMap<>();
			for (SurgicalTableGluePacket.CubeTranslation translation : move.translations()) {
				Vec3 existing = subject.componentOffsets.getOrDefault(translation.cubeId(), Vec3.ZERO);
				SurgicalCubeRotation existingRotation = subject.componentRotations.getOrDefault(
					translation.cubeId(), SurgicalCubeRotation.IDENTITY);
				double lift = translation.offset().y - existing.y;
				if (!translation.valid() || !selected.get(translation.cubeId())
					|| Math.abs(translation.offset().x - existing.x) > 1.0e-6d
					|| Math.abs(translation.offset().z - existing.z) > 1.0e-6d
					|| commonLift != null && Math.abs(lift - commonLift) > 1.0e-6d
					|| !translation.rotation().approximatelyEquals(existingRotation, 1.0e-6d)
					|| offsets.putIfAbsent(translation.cubeId(), translation.offset()) != null)
					return null;
				if (commonLift == null)
					commonLift = lift;
			}
			if (offsets.size() != selected.cardinality())
				return null;
			validated.put(subject.persistentId(), Map.copyOf(offsets));
		}
		return validated.keySet().equals(anchored.components().keySet()) ? Map.copyOf(validated) : null;
	}

	static boolean layoutMatchesTranslations(SurgicalTableLayout.Proposal layout,
		Map<Integer, Vec3> offsets) {
		if (layout.offsets().size() != offsets.size())
			return false;
		Set<Integer> seen = new HashSet<>();
		for (SurgicalTableLayout.CubeOffset proposed : layout.offsets()) {
			Vec3 expected = offsets.get(proposed.cubeId());
			if (expected == null || !seen.add(proposed.cubeId())
				|| Math.abs(expected.x - proposed.x()) > 1.0e-6d
				|| Math.abs(expected.y - proposed.y()) > 1.0e-6d
				|| Math.abs(expected.z - proposed.z()) > 1.0e-6d)
				return false;
		}
		return true;
	}

	static boolean componentYTranslationsMatch(SurgicalSubject subject, BitSet selected,
		Map<Integer, Vec3> offsets) {
		for (BitSet component : SurgicalAssembly.components(subject.cubeCount, selected,
			subject.seams, subject.cutSeams)) {
			double expected = offsets.get(component.nextSetBit(0)).y;
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1))
				if (Math.abs(offsets.get(cube).y - expected) > 1.0e-6d)
					return false;
		}
		return true;
	}

	static boolean canApplyComponentLayout(SurgicalTableBlockEntity operationOwner, int subjectId, BitSet cutSeams,
		SurgicalTableLayout.Proposal proposal, SurgicalTablePlane.Plane plane) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		List<SurgicalTableLayout.Footprint> occupied = subject == null ? null
			: operationOwner.occupiedForValidation(plane, SurgicalTableConnections.connectedSubjectIds(operationOwner, subjectId));
		return subject != null && occupied != null
			&& SurgicalGlueOperations.validateComponentLayout(subject, cutSeams, proposal, plane, occupied);
	}

	static boolean validateComponentLayout(SurgicalSubject subject, BitSet cutSeams,
		SurgicalTableLayout.Proposal proposal, SurgicalTablePlane.Plane plane,
		List<SurgicalTableLayout.Footprint> occupied) {
		return SurgicalGlueOperations.hasNonUniformStoredOffsets(subject, subject.presentCubes)
			? SurgicalTableLayout.validateTransformedComponents(plane, subject.cubeCount,
				subject.presentCubes, subject.seams, cutSeams, proposal, occupied)
			: SurgicalTableLayout.validateComponents(plane, subject.cubeCount,
				subject.presentCubes, subject.seams, cutSeams, proposal, occupied);
	}

	/** Whether a previously edited native component already needs distinct per-cube translations. */
	static boolean hasNonUniformStoredOffsets(SurgicalSubject subject, BitSet selected) {
		for (BitSet component : SurgicalAssembly.components(subject.cubeCount, selected,
			subject.seams, subject.cutSeams)) {
			Vec3 expected = subject.componentOffsets.getOrDefault(component.nextSetBit(0), Vec3.ZERO);
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
				Vec3 actual = subject.componentOffsets.getOrDefault(cube, Vec3.ZERO);
				if (Math.abs(actual.x - expected.x) > 1.0e-6d
					|| Math.abs(actual.y - expected.y) > 1.0e-6d
					|| Math.abs(actual.z - expected.z) > 1.0e-6d)
					return true;
			}
		}
		return false;
	}

	/** Glue moves each native component rigidly while preserving its existing per-cube orientations. */
	static boolean componentRotationsMatch(SurgicalSubject subject, BitSet selected,
		Map<Integer, SurgicalCubeRotation> rotations, SurgicalLayPose targetPose) {
		for (BitSet component : SurgicalAssembly.components(subject.cubeCount, selected,
			subject.seams, subject.cutSeams)) {
			int root = component.nextSetBit(0);
			SurgicalCubeRotation proposedRoot = rotations.get(root);
			if (proposedRoot == null)
				return false;
			SurgicalCubeRotation storedRoot = subject.componentRotations
				.getOrDefault(root, SurgicalCubeRotation.IDENTITY).reframe(subject.layPose, targetPose);
			SurgicalCubeRotation inverseRoot = new SurgicalCubeRotation(
				-storedRoot.x(), -storedRoot.y(), -storedRoot.z(), storedRoot.w());
			SurgicalCubeRotation delta = inverseRoot.then(proposedRoot);
			for (int cube = root; cube >= 0; cube = component.nextSetBit(cube + 1)) {
				SurgicalCubeRotation expected = subject.componentRotations
					.getOrDefault(cube, SurgicalCubeRotation.IDENTITY)
					.reframe(subject.layPose, targetPose).then(delta);
				if (!expected.approximatelyEquals(rotations.get(cube), 1.0e-6d))
					return false;
			}
		}
		return true;
	}

	static List<SurgicalTableLayout.Footprint> glueMoveObstacles(SurgicalTableBlockEntity operationOwner, SurgicalTableBlockEntity.ComponentGroup moving,
		SurgicalTableBlockEntity.ComponentGroup anchored) {
		List<SurgicalTableLayout.Footprint> obstacles = new ArrayList<>();
		for (SurgicalSubject subject : operationOwner.subjects) {
			BitSet moved = moving.components().get(subject.persistentId());
			BitSet fixed = anchored.components().get(subject.persistentId());
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints())
				if (!subject.containsFootprint(moved, footprint)
					&& !subject.containsFootprint(fixed, footprint))
					obstacles.add(footprint);
		}
		return List.copyOf(obstacles);
	}

	static SurgicalGlueJoint remapJoint(SurgicalGlueJoint joint,
		Map<UUID, SurgicalTableBlockEntity.ExtractedSubject> extracted) {
		SurgicalGlueJoint.Replay replay = joint.replay() == null ? null
			: new SurgicalGlueJoint.Replay(SurgicalGlueOperations.remapEndpoint(joint.replay().moving(), extracted),
				joint.replay().transform(), joint.replay().anchorContact());
		return SurgicalGlueJoint.of(SurgicalGlueOperations.remapEndpoint(joint.first(), extracted),
			SurgicalGlueOperations.remapEndpoint(joint.second(), extracted), replay);
	}

	static SurgicalGlueJoint.Endpoint remapEndpoint(SurgicalGlueJoint.Endpoint endpoint,
		Map<UUID, SurgicalTableBlockEntity.ExtractedSubject> extracted) {
		SurgicalTableBlockEntity.ExtractedSubject moved = extracted.get(endpoint.subjectKey());
		return moved != null && moved.cubes().get(endpoint.cubeId())
			? new SurgicalGlueJoint.Endpoint(moved.subject().persistentId(), endpoint.cubeId()) : endpoint;
	}

	static @Nullable
	SurgicalCombination remapCombination(SurgicalCombination combination,
		Map<UUID, SurgicalTableBlockEntity.ExtractedSubject> extracted) {
		List<SurgicalCombination.Member> members = new ArrayList<>(combination.members().size());
		for (SurgicalCombination.Member member : combination.members()) {
			SurgicalTableBlockEntity.ExtractedSubject moved = extracted.get(member.subjectKey());
			UUID subjectKey = moved != null && moved.cubes().get(member.cubeId())
				? moved.subject().persistentId() : member.subjectKey();
			members.add(new SurgicalCombination.Member(subjectKey, member.cubeId()));
		}
		return SurgicalCombination.create(combination.id(), members);
	}

	static SurgicalSubject remappedSubject(SurgicalSubject original, int cubeId,
		Map<UUID, SurgicalTableBlockEntity.ExtractedSubject> extracted) {
		SurgicalTableBlockEntity.ExtractedSubject moved = extracted.get(original.persistentId());
		return moved != null && moved.cubes().get(cubeId) ? moved.subject() : original;
	}

	static void attachJoint(SurgicalTableBlockEntity operationOwner, SurgicalGlueJoint joint) {
		SurgicalSubject first = operationOwner.getSubjectByPersistentId(joint.first().subjectKey());
		SurgicalSubject second = operationOwner.getSubjectByPersistentId(joint.second().subjectKey());
		if (first == null || second == null)
			return;
		first.addGlueJoint(joint);
		if (second != first)
			second.addGlueJoint(joint);
	}

	static void attachCombination(SurgicalTableBlockEntity operationOwner, SurgicalCombination combination) {
		Set<UUID> attached = new HashSet<>();
		for (SurgicalCombination.Member member : combination.members()) {
			if (!attached.add(member.subjectKey()))
				continue;
			SurgicalSubject subject = operationOwner.getSubjectByPersistentId(member.subjectKey());
			if (subject != null)
				subject.addCombination(combination);
		}
	}

	/** Validates the complete snapshot before calculating any wrench mutation. */
	static SurgicalWrenchRotation.Plan planWrenchRotation(SurgicalTableBlockEntity operationOwner, UUID subjectId, int cubeId,
		SurgicalCubeRotation groupRotation, SurgicalCubeRotation selectedRotation,
		Map<Integer, BitSet> expectedGroup, List<SurgicalWrenchRotation.Body> bodies, SurgicalTablePlane.Plane plane,
		Vec3 groupTranslation, Vec3 selectedTranslation) {
		SurgicalWrenchRotation.Plan invalid = SurgicalWrenchRotation.Plan.fail(SurgicalWrenchRotation.Failure.INVALID);
		SurgicalSubject selected = operationOwner.getSubjectByPersistentId(subjectId);
		if (selected == null || !selected.validPresentCube(cubeId) || !plane.valid()
			|| !operationOwner.getBlockPos().equals(plane.source()) || bodies.isEmpty() || bodies.size() > SurgicalAssembly.MAX_SOURCES)
			return invalid;
		Map<Integer, BitSet> group = SurgicalTableConnections.connectedComponents(operationOwner, selected.id(), cubeId);
		if (!group.equals(expectedGroup))
			return invalid;
		Set<Integer> seen = new HashSet<>();
		int total = 0;
		for (SurgicalWrenchRotation.Body body : bodies) {
			SurgicalSubject subject = operationOwner.getSubject(body.id());
			if (subject == null || !subject.persistentId().equals(body.key()) || !seen.add(body.id())
				|| !group.containsKey(body.id()) || !subject.matchesObservedTopology(body.cubeCount(), body.seams()))
				return invalid;
			BitSet cubes = new BitSet();
			for (SurgicalWrenchRotation.Cube cube : body.cubes()) {
				if (!cube.valid() || !subject.validPresentCube(cube.id()) || cubes.get(cube.id())
					|| !subject.componentRotations.getOrDefault(cube.id(), SurgicalCubeRotation.IDENTITY)
						.approximatelyEquals(cube.rotation(), 1.0e-8d))
					return invalid;
				cubes.set(cube.id());
			}
			total += cubes.cardinality();
			if (total > SurgicalAssembly.MAX_CUBES || !cubes.equals(subject.presentCubes)
				|| !SurgicalGlueOperations.layoutMatchesStoredOffsets(subject, body.layout(subject.presentCubes, subject.cutSeams)))
				return invalid;
		}
		if (!seen.equals(group.keySet()))
			return invalid;
		Map<Integer, BitSet> neighbors = new HashMap<>();
		SurgicalTableConnections.directConnections(operationOwner, selected.id(), cubeId, selected.cubeCount, selected.seams)
			.forEach((id, cubes) -> neighbors.put(id, (BitSet) cubes.clone()));
		// The connectivity graph encodes honey groups as a star; all members are logical neighbors,
		// regardless of which member happens to be the graph's anchor.
		SurgicalCombination combination = selected.combinationContaining(cubeId);
		if (combination != null)
			for (SurgicalCombination.Member member : combination.members()) {
				SurgicalSubject owner = operationOwner.getSubjectByPersistentId(member.subjectKey());
				if (owner != null)
					neighbors.computeIfAbsent(owner.id(), ignored -> new BitSet()).set(member.cubeId());
			}
		for (SurgicalLimbJoint joint : operationOwner.allLimbJoints()) {
			SurgicalGlueJoint.Endpoint endpoint = joint.child().matches(selected.persistentId(), cubeId)
				? joint.parent() : joint.parent().matches(selected.persistentId(), cubeId) ? joint.child() : null;
			if (endpoint != null) {
				SurgicalSubject owner = operationOwner.getSubjectByPersistentId(endpoint.subjectKey());
				if (owner == null)
					return invalid;
				neighbors.computeIfAbsent(owner.id(), ignored -> new BitSet()).set(endpoint.cubeId());
			}
		}
		SurgicalWrenchRotation.Plan plan = SurgicalWrenchRotation.plan(bodies, group, neighbors, selected.id(), cubeId,
			groupRotation, selectedRotation, plane.workArea(), operationOwner.occupiedForValidation(plane, seen),
			groupTranslation, selectedTranslation);
		if (!plan.valid())
			return plan;
		SurgicalTableBlockEntity.WrenchDisconnections cuts = SurgicalGlueOperations.wrenchDisconnections(operationOwner, plan);
		if (!cuts.limbs().isEmpty())
			return SurgicalWrenchRotation.Plan.fail(SurgicalWrenchRotation.Failure.JOINT_DISCONNECTED);
		if (SurgicalTableConnections.allGlueJoints(operationOwner).size() - cuts.glue().size() + cuts.addedGlue().size() > SurgicalAssembly.MAX_SEAMS)
			return invalid;
		for (SurgicalWrenchRotation.Body body : plan.bodies()) {
			SurgicalSubject subject = operationOwner.getSubject(body.id());
			BitSet cutSeams = cuts.cuts().getOrDefault(body.id(), subject.cutSeams);
			if (!SurgicalTableLayout.validateEditedGlueComponents(plane, subject.cubeCount, subject.presentCubes,
				subject.seams, cutSeams, body.layout(subject.presentCubes, cutSeams), List.of()))
				return invalid;
		}
		return plan;
	}

	/** Commits every subject together, only after the complete plan has passed validation. */
	static void applyWrenchRotation(SurgicalTableBlockEntity operationOwner, SurgicalWrenchRotation.Plan plan) {
		if (!plan.valid())
			return;
		SurgicalTableBlockEntity.WrenchDisconnections cuts = SurgicalGlueOperations.wrenchDisconnections(operationOwner, plan);
		if (!cuts.limbs().isEmpty())
			return;
		for (SurgicalSubject subject : operationOwner.subjects) {
			BitSet cutSeams = cuts.cuts().get(subject.id());
			if (cutSeams != null) {
				subject.setCutSeams(cutSeams);
				subject.cutOrder = SurgicalAssembly.normalizeCutOrder(subject.cutOrder, cutSeams, subject.seams.size());
			}
			subject.removeGlueJoints(cuts.glue());
			subject.removeCombinations(cuts.combinations());
		}
		for (SurgicalCombination combination : cuts.remainingCombinations())
			SurgicalGlueOperations.attachCombination(operationOwner, combination);
		for (SurgicalGlueJoint joint : cuts.addedGlue()) {
			operationOwner.getSubjectByPersistentId(joint.first().subjectKey()).addGlueJoint(joint);
			operationOwner.getSubjectByPersistentId(joint.second().subjectKey()).addGlueJoint(joint);
		}
		for (SurgicalWrenchRotation.Body body : plan.bodies()) {
			SurgicalSubject subject = operationOwner.getSubject(body.id());
			Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>();
			for (SurgicalWrenchRotation.Cube cube : body.cubes())
				if (!cube.rotation().isIdentity())
					rotations.put(cube.id(), cube.rotation());
			subject.applyLayout(body.layout(subject.presentCubes, subject.cutSeams));
			subject.componentRotations = Map.copyOf(rotations);
		}
		operationOwner.clientRenderBounds = null;
		operationOwner.setChangedAndSync();
	}

	/** Stage topology edits before validation so rejected rotations never sever connections. */
	static SurgicalTableBlockEntity.WrenchDisconnections wrenchDisconnections(SurgicalTableBlockEntity operationOwner, SurgicalWrenchRotation.Plan plan) {
		Map<Integer, BitSet> cuts = new HashMap<>();
		Set<SurgicalGlueJoint> glue = new HashSet<>();
		Set<SurgicalLimbJoint> limbs = new HashSet<>();
		Set<SurgicalCombination> combinations = new HashSet<>();
		List<SurgicalCombination> remaining = new ArrayList<>();
		Set<SurgicalGlueJoint> added = new HashSet<>();
		SurgicalTableBlockEntity.WrenchDisconnections result = new SurgicalTableBlockEntity.WrenchDisconnections(cuts, glue, limbs, combinations, remaining, added);
		if (plan.disconnected().isEmpty())
			return result;
		SurgicalSubject selected = operationOwner.getSubject(plan.selected().body());
		int cube = plan.selected().cube();
		SurgicalGlueJoint.Endpoint anchor = new SurgicalGlueJoint.Endpoint(selected.persistentId(), cube);
		Set<SurgicalGlueJoint.Endpoint> detached = new HashSet<>();
		for (SurgicalWrenchRotation.Endpoint endpoint : plan.disconnected())
			detached.add(new SurgicalGlueJoint.Endpoint(operationOwner.getSubject(endpoint.body()).persistentId(), endpoint.cube()));
		BitSet cutSeams = (BitSet) selected.cutSeams.clone();
		for (int seamId = 0; seamId < selected.seams.size(); seamId++) {
			SurgicalAssembly.Seam seam = selected.seams.get(seamId);
			int other = seam.first() == cube ? seam.second() : seam.second() == cube ? seam.first() : -1;
			if (other >= 0 && detached.contains(new SurgicalGlueJoint.Endpoint(selected.persistentId(), other)))
				cutSeams.set(seamId);
		}
		cuts.put(selected.id(), cutSeams);
		Set<SurgicalGlueJoint> existingGlue = SurgicalTableConnections.allGlueJoints(operationOwner);
		for (SurgicalGlueJoint joint : existingGlue)
			if (detached.contains(joint.other(anchor)))
				glue.add(joint);
		for (SurgicalLimbJoint joint : operationOwner.allLimbJoints())
			if (joint.child().equals(anchor) && detached.contains(joint.parent())
				|| joint.parent().equals(anchor) && detached.contains(joint.child()))
				limbs.add(joint);
		SurgicalCombination combination = selected.combinationContaining(cube);
		if (combination != null && combination.members().stream().anyMatch(member ->
			detached.contains(new SurgicalGlueJoint.Endpoint(member.subjectKey(), member.cubeId())))) {
			// Honey has no removable pairwise edges. Extract the rotated cube, preserve the rest,
			// and represent its surviving honey contacts as ordinary explicit connections.
			combinations.add(combination);
			List<SurgicalCombination.Member> members = combination.members().stream()
				.filter(member -> !member.subjectKey().equals(anchor.subjectKey()) || member.cubeId() != cube).toList();
			if (members.size() >= 2)
				remaining.add(SurgicalCombination.create(combination.id(), members));
			for (SurgicalCombination.Member member : members) {
				SurgicalGlueJoint.Endpoint other = new SurgicalGlueJoint.Endpoint(member.subjectKey(), member.cubeId());
				if (detached.contains(other))
					continue;
				boolean nativeSeam = other.subjectKey().equals(anchor.subjectKey())
					&& java.util.stream.IntStream.range(0, selected.seams.size()).anyMatch(id ->
						!cutSeams.get(id) && selected.seams.get(id).equals(SurgicalAssembly.Seam.of(cube, other.cubeId())));
				if (!nativeSeam && existingGlue.stream().noneMatch(joint -> other.equals(joint.other(anchor))))
					added.add(SurgicalGlueJoint.of(anchor, other));
			}
		}
		return result;
	}

	/** Validates an existing endpoint snapshot; destination clearance is checked by validateGlueMoves. */
	static boolean validateGlueLayout(SurgicalTableBlockEntity operationOwner, SurgicalSubject subject, SurgicalTablePlane.Plane plane,
		SurgicalTableLayout.Proposal proposal) {
		// Existing overlaps must not prevent moving a component out to a clear destination.
		return SurgicalGlueOperations.layoutMatchesStoredOffsets(subject, proposal)
			&& SurgicalTableLayout.validateEditedGlueComponents(plane, subject.cubeCount,
				subject.presentCubes, subject.seams, subject.cutSeams, proposal, List.of());
	}

	/** The endpoint layout is a refreshed footprint for current server state, not another move. */
	static boolean layoutMatchesStoredOffsets(SurgicalSubject subject,
		SurgicalTableLayout.Proposal proposal) {
		if (proposal.offsets().size() != subject.presentCubes.cardinality())
			return false;
		Map<Integer, Vec3> proposedOffsets = new HashMap<>();
		for (SurgicalTableLayout.CubeOffset proposed : proposal.offsets()) {
			if (!subject.presentCubes.get(proposed.cubeId())
				|| proposedOffsets.putIfAbsent(proposed.cubeId(),
					new Vec3(proposed.x(), proposed.y(), proposed.z())) != null)
				return false;
			Vec3 expected = subject.componentOffsets.getOrDefault(proposed.cubeId(), Vec3.ZERO);
			if (Math.abs(proposed.x() - expected.x) > 1.0e-6d
				|| Math.abs(proposed.z() - expected.z) > 1.0e-6d)
				return false;
		}
		for (BitSet component : SurgicalAssembly.components(subject.cubeCount, subject.presentCubes,
			subject.seams, subject.cutSeams)) {
			int root = component.nextSetBit(0);
			double lift = proposedOffsets.get(root).y
				- subject.componentOffsets.getOrDefault(root, Vec3.ZERO).y;
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
				double cubeLift = proposedOffsets.get(cube).y
					- subject.componentOffsets.getOrDefault(cube, Vec3.ZERO).y;
				if (Math.abs(cubeLift - lift) > 1.0e-6d)
					return false;
			}
		}
		return true;
	}
}
