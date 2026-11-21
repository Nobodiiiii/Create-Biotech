package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.capturenet.CaptureNetItem;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicCarriedDrops;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** SurgicalPlacementOperations algorithms over the canonical SurgicalTableBlockEntity state. */
final class SurgicalPlacementOperations {
	private SurgicalPlacementOperations() {}


	static SurgicalTablePlacementResult tryPlaceSubject(SurgicalTableBlockEntity operationOwner, ItemStack box, SurgicalTablePlane.Plane plane,
		Direction placementFacing,
		SurgicalLayPose layPose, double placedOriginOffsetX, double placedOriginOffsetZ,
		SurgicalTableLayout.Proposal proposal,
		int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		BionicAnatomySnapshot anatomy,
		List<SurgicalTableLayout.Footprint> componentFootprints,
		List<SurgicalTableLayout.Proposal> sourceLayouts, List<DevouredDonorTopology> preyModels) {
		SurgicalTableBlockEntity.TemporaryMoveSource temporaryMove = SurgicalKitItem.hasTemporaryCapture(box)
			? SurgicalPlacementOperations.resolveTemporaryMove(operationOwner, box) : null;
		if (SurgicalKitItem.isTemporaryBox(box) && temporaryMove == null)
			return SurgicalTablePlacementResult.INVALID_CAPTURE;
		List<SurgicalTableLayout.Footprint> occupied = temporaryMove != null
			&& temporaryMove.sourceTable() == operationOwner
			? SurgicalPlacementOperations.occupiedOutside(operationOwner, temporaryMove.group()) : operationOwner.occupiedForValidation(plane, -1);
		if (operationOwner.getLevel() == null || operationOwner.getLevel().isClientSide || !operationOwner.getBlockPos().equals(plane.source()) || occupied == null)
			return SurgicalTablePlacementResult.INVALID_TABLE;
		if (!SurgicalTableBlockEntity.isPlacementContainer(box) && !SurgicalKitItem.hasPlacementCapture(box)
			|| !CapturedEntityBoxHelper.hasCapturedEntity(box) || observedSeams == null
			|| anatomy == null || componentFootprints == null || sourceLayouts == null
			|| layPose == null || !layPose.valid())
			return SurgicalTablePlacementResult.INVALID_CAPTURE;

		Entity captured = CapturedEntityBoxHelper.createCapturedEntity(box, operationOwner.getLevel());
		if (captured == null)
			return SurgicalTablePlacementResult.INVALID_CAPTURE;
		SurgicalAssembly placementAssembly = null;
		MimicProfile profile;
		int cubeCount;
		BitSet present;
		UUID donorId;
		BionicAnatomySnapshot originalAnatomy;
		List<SurgicalAssembly.Seam> seams;
		BitSet cuts;
		List<Integer> cutOrder;
		List<SurgicalTableLayout.Footprint> storedFootprints;
		if (captured instanceof SlimeBionicEntity bionic) {
			SurgicalAssembly boxed = bionic.getAssemblyForPlacement();
			SurgicalAssembly assembly = boxed == null ? null : DevouredDonorTopology.expand(boxed, preyModels);
			if (assembly == null)
				return SurgicalTablePlacementResult.INVALID_ASSEMBLY;
			placementAssembly = assembly;
			if (assembly.preservesLayout() || assembly.sources().size() != 1) {
				if (!SurgicalTableLayout.validateSubjectPlacement(plane, assembly, placementFacing, layPose,
					placedOriginOffsetX, placedOriginOffsetZ, proposal, observedCubeCount, observedSeams,
					componentFootprints, sourceLayouts, occupied))
					return SurgicalTablePlacementResult.invalidLayout(plane, proposal, componentFootprints, sourceLayouts, occupied);
				SurgicalTablePlacementResult result = SurgicalPlacementOperations.tryPlaceComposite(operationOwner, box, placementFacing, layPose, placedOriginOffsetX, placedOriginOffsetZ,
					sourceLayouts, assembly, temporaryMove);
				if (result.succeeded()) SurgicalPlacementOperations.dropPlacedCarriedItems(operationOwner, captured, placedOriginOffsetX, placedOriginOffsetZ);
				return result;
			}
			if (!sourceLayouts.isEmpty())
				return SurgicalTablePlacementResult.INVALID_ASSEMBLY;
			profile = assembly.profile();
			cubeCount = assembly.cubeCount();
			present = assembly.presentCubes();
			SurgicalAssembly.Source originalSource = assembly.sources().getFirst();
			donorId = originalSource.donorId();
			originalAnatomy = originalSource.anatomy();
			seams = assembly.seams();
			cuts = assembly.cutSeams();
			cutOrder = assembly.cutOrder();
			storedFootprints = proposal.footprints();
		} else if (captured instanceof LivingEntity living && SlimeMimicHandler.isSlimeMimic(living)) {
			if (!sourceLayouts.isEmpty() || !preyModels.isEmpty())
				return SurgicalTablePlacementResult.INVALID_CAPTURE;
			if (!SurgicalAssembly.validTopology(observedCubeCount, observedSeams))
				return SurgicalTablePlacementResult.INVALID_CAPTURE;
			profile = MimicProfile.capture(living);
			if (profile == null)
				return SurgicalTablePlacementResult.INVALID_CAPTURE;
			cubeCount = observedCubeCount;
			present = new BitSet(cubeCount);
			if (cubeCount > 0)
				present.set(0, cubeCount);
			donorId = UUID.randomUUID();
			originalAnatomy = anatomy;
			seams = List.copyOf(observedSeams);
			cuts = new BitSet();
			cutOrder = List.of();
			storedFootprints = List.copyOf(componentFootprints);
		} else if (captured instanceof LivingEntity)
			return SurgicalTablePlacementResult.UNSUPPORTED_SUBJECT;
		else
			return SurgicalTablePlacementResult.INVALID_CAPTURE;
		if (SurgicalPlacementOperations.effectiveSubjectCount(operationOwner, temporaryMove) >= SurgicalTableBlockEntity.MAX_SUBJECTS)
			return SurgicalTablePlacementResult.TABLE_FULL;
		if (!SurgicalTableLayout.validateSubjectPlacement(plane, placementAssembly, placementFacing, layPose,
			placedOriginOffsetX, placedOriginOffsetZ, proposal, observedCubeCount, observedSeams,
			componentFootprints, sourceLayouts, occupied))
			return SurgicalTablePlacementResult.invalidLayout(plane, proposal, componentFootprints, sourceLayouts, occupied);

		if (placementAssembly != null)
			donorId = SurgicalPlacementOperations.placementDonorIds(operationOwner, placementAssembly, temporaryMove, SurgicalKitItem.isCubeCopier(box))
				.get(donorId);
		SurgicalSubject subject = new SurgicalSubject(operationOwner.allocateSubjectId(), UUID.randomUUID(), donorId,
			originalAnatomy, profile, placementFacing, layPose, cubeCount,
			present, seams, cuts, cutOrder, placedOriginOffsetX, placedOriginOffsetZ,
			SurgicalPlacementOperations.placementOffsets(proposal), Map.of(), storedFootprints, List.of(), List.of(), List.of());
		SurgicalPlacementOperations.commitTemporaryMove(temporaryMove);
		operationOwner.addSubject(subject);
		SurgicalPlacementOperations.finishSubjectPlacement(operationOwner, box, temporaryMove);
		SurgicalPlacementOperations.dropPlacedCarriedItems(operationOwner, captured, placedOriginOffsetX, placedOriginOffsetZ);
		return SurgicalTablePlacementResult.SUCCESS;
	}

	static void dropPlacedCarriedItems(SurgicalTableBlockEntity operationOwner, Entity captured, double originOffsetX, double originOffsetZ) {
		if (!(captured instanceof LivingEntity living))
			return;
		for (ItemStack stack : SlimeMimicCarriedDrops.removeCarriedItems(living))
			operationOwner.getLevel().addFreshEntity(new ItemEntity(operationOwner.getLevel(), operationOwner.getBlockPos().getX() + originOffsetX,
				SurgicalTablePlane.surfaceY(operationOwner.getBlockPos().getY()) + 0.1d, operationOwner.getBlockPos().getZ() + originOffsetZ, stack));
	}

	static SurgicalTablePlacementResult tryPlaceComposite(SurgicalTableBlockEntity operationOwner, ItemStack box, Direction placementFacing, SurgicalLayPose layPose,
		double placedOriginOffsetX, double placedOriginOffsetZ,
		List<SurgicalTableLayout.Proposal> sourceLayouts, SurgicalAssembly assembly,
		@Nullable SurgicalTableBlockEntity.TemporaryMoveSource temporaryMove) {
		List<SurgicalAssembly.Source> assemblySources = assembly.sources();
		List<SurgicalAssembly.PlacedSource> placedSources = assembly.placedSources(placementFacing, layPose);
		if (sourceLayouts.size() != assemblySources.size()
			|| placedSources.size() != assemblySources.size())
			return SurgicalTablePlacementResult.INVALID_ASSEMBLY;
		if (SurgicalPlacementOperations.effectiveSubjectCount(operationOwner, temporaryMove) > SurgicalTableBlockEntity.MAX_SUBJECTS - assemblySources.size())
			return SurgicalTablePlacementResult.TABLE_FULL;

		// Restore sources as normal table subjects so each source keeps its own model topology and
		// automatically participates in the existing ray selection and shears workflow.
		List<SurgicalSubject> restored = new ArrayList<>(assemblySources.size());
		Map<UUID, UUID> donorIds = SurgicalPlacementOperations.placementDonorIds(operationOwner, assembly, temporaryMove, SurgicalKitItem.isCubeCopier(box));
		for (int sourceId = 0; sourceId < assemblySources.size(); sourceId++) {
			SurgicalAssembly.PlacedSource placed = placedSources.get(sourceId);
			SurgicalAssembly.Source source = placed.source();
			SurgicalSubject subject = new SurgicalSubject(operationOwner.allocateSubjectId(), UUID.randomUUID(),
				donorIds.get(source.donorId()),
				source.anatomy(),
				source.profile(), placed.facing(), placed.layPose(), source.cubeCount(),
				source.presentCubes(), source.seams(), source.cutSeams(), source.cutOrder(),
				placedOriginOffsetX + placed.originOffset().x,
				placedOriginOffsetZ + placed.originOffset().z, SurgicalPlacementOperations.restoredOffsets(placed), placed.cubeRotations(),
				sourceLayouts.get(sourceId).footprints(), List.of(), List.of(), List.of());
			restored.add(subject);
		}
		for (SurgicalAssembly.Joint encoded : assembly.placedJoints(placementFacing, layPose)) {
			SurgicalSubject first = restored.get(encoded.firstSource());
			SurgicalSubject second = restored.get(encoded.secondSource());
			SurgicalGlueJoint.Endpoint firstEndpoint =
				new SurgicalGlueJoint.Endpoint(first.persistentId(), encoded.firstCube());
			SurgicalGlueJoint.Endpoint secondEndpoint =
				new SurgicalGlueJoint.Endpoint(second.persistentId(), encoded.secondCube());
			SurgicalGlueJoint.Replay replay = null;
			if (encoded.replay() != null) {
				SurgicalSubject moving = restored.get(encoded.replay().movingSource());
				replay = new SurgicalGlueJoint.Replay(new SurgicalGlueJoint.Endpoint(
					moving.persistentId(), encoded.replay().movingCube()), encoded.replay().transform(),
					encoded.replay().anchorContact());
			}
			SurgicalGlueJoint joint = SurgicalGlueJoint.of(firstEndpoint, secondEndpoint, replay);
			first.addGlueJoint(joint);
			if (second != first)
				second.addGlueJoint(joint);
		}
		for (SurgicalAssembly.Combination encoded : assembly.combinations()) {
			List<SurgicalCombination.Member> members = new ArrayList<>(encoded.members().size());
			for (SurgicalAssembly.CombinationMember member : encoded.members()) {
				SurgicalSubject restoredSubject = restored.get(member.source());
				members.add(new SurgicalCombination.Member(restoredSubject.persistentId(), member.cube()));
			}
			SurgicalCombination combination = SurgicalCombination.create(members);
			if (combination == null)
				return SurgicalTablePlacementResult.INVALID_ASSEMBLY;
			for (int source : encoded.members().stream()
				.mapToInt(SurgicalAssembly.CombinationMember::source).distinct().toArray())
				restored.get(source).addCombination(combination);
		}
		for (SurgicalAssembly.Limb encoded : assembly.limbs()) {
			SurgicalSubject child = restored.get(encoded.childSource());
			SurgicalSubject parent = restored.get(encoded.parentSource());
			SurgicalLimbJoint limb = new SurgicalLimbJoint(encoded.type(),
				new SurgicalGlueJoint.Endpoint(child.persistentId(), encoded.childCube()),
				new SurgicalGlueJoint.Endpoint(parent.persistentId(), encoded.parentCube()));
			child.addLimbJoint(limb);
			if (parent != child)
				parent.addLimbJoint(limb);
		}
		SurgicalPlacementOperations.commitTemporaryMove(temporaryMove);
		operationOwner.addSubjects(restored);
		SurgicalPlacementOperations.finishSubjectPlacement(operationOwner, box, temporaryMove);
		return SurgicalTablePlacementResult.SUCCESS;
	}

	/** Keep complementary fragments related; creative copies always receive an independent donor. */
	static Map<UUID, UUID> placementDonorIds(SurgicalTableBlockEntity operationOwner, SurgicalAssembly assembly,
		@Nullable SurgicalTableBlockEntity.TemporaryMoveSource move, boolean copied) {
		Map<UUID, BitSet> occupied = new HashMap<>();
		for (SurgicalSubject subject : operationOwner.subjects) {
			BitSet cubes = subject.presentCubes();
			if (move != null && move.sourceTable() == operationOwner) {
				BitSet removed = move.group().components().get(subject.persistentId());
				if (removed != null)
					cubes.andNot(removed);
			}
			occupied.computeIfAbsent(subject.donorId(), ignored -> new BitSet()).or(cubes);
		}
		Map<UUID, BitSet> incoming = new HashMap<>();
		for (SurgicalAssembly.Source source : assembly.sources())
			incoming.computeIfAbsent(source.donorId(), ignored -> new BitSet()).or(source.presentCubes());
		Set<UUID> used = new HashSet<>(occupied.keySet());
		used.addAll(incoming.keySet());
		Map<UUID, UUID> result = new HashMap<>();
		for (Map.Entry<UUID, BitSet> entry : incoming.entrySet()) {
			UUID id = entry.getKey();
			BitSet existing = occupied.get(id);
			if (copied || existing != null && existing.intersects(entry.getValue())) {
				do {
					id = UUID.randomUUID();
				} while (!used.add(id));
			}
			result.put(entry.getKey(), id);
		}
		return result;
	}

	static @Nullable
	SurgicalTableBlockEntity.TemporaryMoveSource resolveTemporaryMove(SurgicalTableBlockEntity operationOwner, ItemStack kit) {
		SurgicalKitItem.TemporaryMove move = SurgicalKitItem.temporaryMove(kit);
		if (move == null || !(operationOwner.getLevel() instanceof ServerLevel sourceLevel)
			|| !operationOwner.getLevel().dimension().location().equals(move.dimension()) || !operationOwner.getLevel().isLoaded(move.tablePos()))
			return null;
		SurgicalTableBlockEntity.TemporaryMoveSource resolved = SurgicalPlacementOperations.resolveTemporaryMoveSource(sourceLevel, move);
		if (resolved == null)
			SurgicalKitItem.clearTemporaryCapture(kit);
		return resolved;
	}

	/** Performs a read-only validity check once the source chunk is available. */
	static boolean isTemporaryMoveValid(ServerLevel sourceLevel,
		SurgicalKitItem.TemporaryMove move) {
		return sourceLevel != null && move != null && sourceLevel.isLoaded(move.tablePos())
			&& SurgicalPlacementOperations.resolveTemporaryMoveSource(sourceLevel, move) != null;
	}

	static @Nullable
	SurgicalTableBlockEntity.TemporaryMoveSource resolveTemporaryMoveSource(ServerLevel sourceLevel,
		SurgicalKitItem.TemporaryMove move) {
		if (!sourceLevel.dimension().location().equals(move.dimension()))
			return null;
		SurgicalTablePlane.Plane sourcePlane = SurgicalTablePlane.scan(sourceLevel, move.tablePos());
		SurgicalTableBlockEntity sourceTable = SurgicalTableBlockEntity.controller(sourceLevel, sourcePlane);
		if (sourceTable == null)
			return null;
		Map<UUID, BitSet> components = move.components();
		SurgicalSubject anchor = sourceTable.getSubjectByPersistentId(move.anchorSubject());
		int anchorCube = move.anchorCube();
		BitSet storedAnchorComponent = components.get(move.anchorSubject());
		if (anchor == null || storedAnchorComponent == null || !storedAnchorComponent.get(anchorCube)
			|| !anchor.validPresentCube(anchorCube))
			return null;
		for (Map.Entry<UUID, BitSet> entry : components.entrySet()) {
			SurgicalSubject candidate = sourceTable.getSubjectByPersistentId(entry.getKey());
			int firstCube = entry.getValue().nextSetBit(0);
			CompoundTag sourceState = move.sourceSubject(entry.getKey());
			if (candidate == null || sourceState == null || !candidate.save().equals(sourceState)
				|| firstCube < 0 || !candidate.validPresentCube(firstCube))
				return null;
		}
		SurgicalTableBlockEntity.ComponentGroup group = SurgicalTableConnections.connectedGroup(sourceTable, anchor, anchorCube);
		if (!group.components().equals(components))
			return null;
		SurgicalAssembly current = SurgicalTableConnections.packedAssembly(sourceTable, group, anchor);
		if (current == null || !current.save().equals(move.sourceAssembly()))
			return null;
		return new SurgicalTableBlockEntity.TemporaryMoveSource(sourceTable, group, SurgicalPlacementOperations.freedSubjectSlots(sourceTable, group));
	}

	static @Nullable
	List<SurgicalTableLayout.Footprint> occupiedOutside(SurgicalTableBlockEntity operationOwner, SurgicalTableBlockEntity.ComponentGroup group) {
		List<SurgicalTableLayout.Footprint> occupied = new ArrayList<>();
		for (SurgicalSubject subject : operationOwner.subjects) {
			BitSet moved = group.components().get(subject.persistentId());
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints())
				if (!subject.containsFootprint(moved, footprint))
					occupied.add(footprint);
		}
		return List.copyOf(occupied);
	}

	static int freedSubjectSlots(SurgicalTableBlockEntity operationOwner, SurgicalTableBlockEntity.ComponentGroup group) {
		int freed = 0;
		for (SurgicalSubject subject : operationOwner.subjects) {
			BitSet moved = group.components().get(subject.persistentId());
			if (moved != null && moved.equals(subject.presentCubes))
				freed++;
		}
		return freed;
	}

	static int effectiveSubjectCount(SurgicalTableBlockEntity operationOwner, @Nullable SurgicalTableBlockEntity.TemporaryMoveSource move) {
		return operationOwner.subjects.size() - (move != null && move.sourceTable() == operationOwner ? move.freedSubjects() : 0);
	}

	static void commitTemporaryMove(@Nullable SurgicalTableBlockEntity.TemporaryMoveSource move) {
		if (move != null)
			SurgicalPlacementOperations.removeGroup(move.sourceTable(), move.group());
	}

	/** Removes a connectivity-closed group together with every joint, combination and limb inside it. */
	static void removeGroup(SurgicalTableBlockEntity operationOwner, SurgicalTableBlockEntity.ComponentGroup group) {
		Set<SurgicalGlueJoint> removedJoints = SurgicalTableConnections.jointsWithin(operationOwner, group);
		Set<SurgicalCombination> removedCombinations = SurgicalTableConnections.combinationsWithin(operationOwner, group);
		Set<SurgicalLimbJoint> removedLimbs = operationOwner.limbsWithin(group);
		for (SurgicalSubject groupedSubject : List.copyOf(operationOwner.subjects)) {
			BitSet removed = group.components().get(groupedSubject.persistentId());
			if (removed != null)
				groupedSubject.removeComponent(removed);
			groupedSubject.removeGlueJoints(removedJoints);
			groupedSubject.removeCombinations(removedCombinations);
			groupedSubject.removeLimbJoints(removedLimbs);
			if (groupedSubject.isEmpty())
				operationOwner.removeSubject(groupedSubject);
		}
		operationOwner.clientRenderBounds = null;
	}

	/**
	 * Removes the complete server-authoritative connectivity group selected with a shovel.
	 *
	 * <p>When the client's live model no longer matches the saved topology its cube ids may name
	 * different parts, so per-cube selection is dropped and every component of the subject is
	 * removed instead, closed over the connection graph. That degraded path reads only server-side
	 * state, which keeps a body with a changed model recoverable rather than stranded on the table.
	 * The reported volume is still used for the slime yield: the degraded group covers at least the
	 * group the client measured, so an untrusted measurement can only under-reward.</p>
	 */
	static boolean shovelConnectedGroup(SurgicalTableBlockEntity operationOwner, Player player, ItemStack shovel, InteractionHand hand,
		int subjectId, int cubeId, int observedCubeCount, List<SurgicalAssembly.Seam> observedSeams,
		double volume, Vec3 dropPosition) {
		SurgicalSubject subject = operationOwner.getSubject(subjectId);
		if (subject == null || !SurgicalKitItem.isShovel(shovel)
			|| !Double.isFinite(volume) || volume < 0.0d
			|| dropPosition == null || !Double.isFinite(dropPosition.x) || !Double.isFinite(dropPosition.y)
			|| !Double.isFinite(dropPosition.z) || !SurgicalGlueOperations.canPayInteractionCost(shovel, 1, player) || operationOwner.getLevel() == null)
			return false;

		boolean trustedTopology = subject.matchesObservedTopology(observedCubeCount, observedSeams);
		if (trustedTopology && !subject.validPresentCube(cubeId))
			return false;
		SurgicalTableBlockEntity.ComponentGroup group = trustedTopology ? SurgicalTableConnections.connectedGroup(operationOwner, subject, cubeId)
			: SurgicalPlacementOperations.wholeSubjectGroup(operationOwner, subject);
		if (group.components().isEmpty())
			return false;

		SurgicalGlueOperations.damageInteractionTool(shovel, 1, player, hand);
		SurgicalPlacementOperations.removeGroup(operationOwner, group);
		operationOwner.setChangedAndSync();
		int drops = SurgicalSlimeDrops.roll(volume, operationOwner.getLevel().getRandom());
		if (drops > 0)
			Containers.dropItemStack(operationOwner.getLevel(), dropPosition.x, dropPosition.y, dropPosition.z,
				new ItemStack(Items.SLIME_BALL, drops));
		operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.SLIME_BLOCK_BREAK,
			SoundSource.BLOCKS, 0.8f, 1.0f);
		return true;
	}

	/**
	 * Removes every complete connectivity group whose saved horizontal projection has positive-area
	 * overlap with {@code tilePos}. Selection uses only server-owned footprints and topology, so it
	 * also reaches subjects whose model is unavailable, whose model no longer matches its saved cube
	 * ids, including subjects whose placement is represented by one whole-model envelope footprint.
	 */
	static boolean shovelIntersectingTile(SurgicalTableBlockEntity operationOwner, Player player, ItemStack shovel, InteractionHand hand,
		BlockPos tilePos, SurgicalTablePlane.Plane plane, double volume) {
		if (operationOwner.getLevel() == null || operationOwner.getLevel().isClientSide || tilePos == null || plane == null || !plane.valid()
			|| !operationOwner.getBlockPos().equals(plane.source()) || !plane.workArea().containsTile(tilePos)
			|| !SurgicalKitItem.isShovel(shovel) || !Double.isFinite(volume) || volume < 0.0d)
			return false;

		Map<UUID, BitSet> selected = new HashMap<>();
		int interactionCost = 0;
		for (SurgicalSubject subject : operationOwner.subjects) {
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints()) {
				if (!SurgicalPlacementOperations.footprintIntersectsTile(footprint, tilePos))
					continue;
				int root = footprint.componentRoot();
				SurgicalTableBlockEntity.ComponentGroup group = root >= 0 && subject.validPresentCube(root)
					? SurgicalTableConnections.connectedGroup(operationOwner, subject, root) : SurgicalPlacementOperations.wholeSubjectGroup(operationOwner, subject);
				if (SurgicalPlacementOperations.mergeNewComponents(selected, group))
					interactionCost++;
			}
		}
		if (selected.isEmpty() || interactionCost == 0
			|| !SurgicalGlueOperations.canPayInteractionCost(shovel, interactionCost, player))
			return false;

		double fallbackVolume = SurgicalPlacementOperations.selectedProjectionArea(operationOwner, selected);
		SurgicalGlueOperations.damageInteractionTool(shovel, interactionCost, player, hand);
		SurgicalPlacementOperations.removeGroup(operationOwner, new SurgicalTableBlockEntity.ComponentGroup(selected));
		operationOwner.setChangedAndSync();
		double dropVolume = volume > 0.0d ? volume
			: Math.max(fallbackVolume, SurgicalSlimeDrops.ONE_BALL_VOLUME);
		int drops = SurgicalSlimeDrops.roll(dropVolume, operationOwner.getLevel().getRandom());
		if (drops > 0)
			Containers.dropItemStack(operationOwner.getLevel(), tilePos.getX() + 0.5d, plane.workArea().surfaceY(),
				tilePos.getZ() + 0.5d, new ItemStack(Items.SLIME_BALL, drops));
		operationOwner.getLevel().playSound(null, tilePos, SoundEvents.SLIME_BLOCK_BREAK,
			SoundSource.BLOCKS, 0.8f, 1.0f);
		return true;
	}

	/** Conservative volume fallback for clients that cannot measure any model geometry at all. */
	static double selectedProjectionArea(SurgicalTableBlockEntity operationOwner, Map<UUID, BitSet> selected) {
		double area = 0.0d;
		for (SurgicalSubject subject : operationOwner.subjects) {
			BitSet cubes = selected.get(subject.persistentId());
			if (cubes == null)
				continue;
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints())
				if (subject.containsFootprint(cubes, footprint))
					area += Math.max(0.0d, footprint.maxX() - footprint.minX())
						* Math.max(0.0d, footprint.maxZ() - footprint.minZ());
		}
		return Double.isFinite(area) ? area : 0.0d;
	}

	static boolean footprintIntersectsTile(SurgicalTableLayout.Footprint footprint,
		BlockPos tilePos) {
		return footprint.minX() < tilePos.getX() + 1.0d - 1.0e-6d
			&& footprint.maxX() > tilePos.getX() + 1.0e-6d
			&& footprint.minZ() < tilePos.getZ() + 1.0d - 1.0e-6d
			&& footprint.maxZ() > tilePos.getZ() + 1.0e-6d;
	}

	/** Adds only previously unselected members and reports whether this is a new removal group. */
	static boolean mergeNewComponents(Map<UUID, BitSet> selected, SurgicalTableBlockEntity.ComponentGroup group) {
		boolean added = false;
		for (Map.Entry<UUID, BitSet> entry : group.components().entrySet()) {
			BitSet existing = selected.get(entry.getKey());
			if (existing == null) {
				selected.put(entry.getKey(), (BitSet) entry.getValue().clone());
				added = true;
				continue;
			}
			int previousSize = existing.cardinality();
			existing.or(entry.getValue());
			added |= existing.cardinality() != previousSize;
		}
		return added;
	}

	/**
	 * Everything still on the table that belongs to {@code subject}, closed over the connection
	 * graph so anything glued or jointed to it comes along. Addressed purely from server-side
	 * state: the subject's own present cubes seed the walk, never a client-supplied cube id.
	 *
	 * <p>The closure matters because {@link #jointsWithin} and {@link #limbsWithin} only collect a
	 * joint when <em>both</em> of its endpoints are in the group. Removing one subject of a glued
	 * pair on its own would leave the joint behind, dangling onto cubes that no longer exist.</p>
	 */
	static SurgicalTableBlockEntity.ComponentGroup wholeSubjectGroup(SurgicalTableBlockEntity operationOwner, SurgicalSubject subject) {
		Map<UUID, BitSet> members = new HashMap<>();
		BitSet present = subject.presentCubes;
		for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
			BitSet covered = members.get(subject.persistentId());
			if (covered != null && covered.get(cube))
				continue;
			// One walk per disconnected component of the subject, not one per cube.
			for (Map.Entry<UUID, BitSet> entry : SurgicalTableConnections.connectedGroup(operationOwner, subject, cube).components().entrySet())
				members.computeIfAbsent(entry.getKey(), key -> new BitSet()).or(entry.getValue());
		}
		return new SurgicalTableBlockEntity.ComponentGroup(members);
	}

	static void finishSubjectPlacement(SurgicalTableBlockEntity operationOwner, ItemStack box, @Nullable SurgicalTableBlockEntity.TemporaryMoveSource move) {
		operationOwner.clientRenderBounds = null;
		if (box.getItem() instanceof CaptureNetItem)
			CaptureNetItem.clearCapture(box);
		else
			CapturedEntityBoxHelper.clearCapturedEntity(box);
		if (move != null)
			SurgicalKitItem.clearTemporaryMove(box);
		operationOwner.setChangedAndSync();
		if (move != null && move.sourceTable() != operationOwner)
			move.sourceTable().setChangedAndSync();
		operationOwner.getLevel().playSound(null, operationOwner.getBlockPos(), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8f, 0.9f);
	}

	static Map<Integer, Vec3> placementOffsets(SurgicalTableLayout.Proposal proposal) {
		Map<Integer, Vec3> offsets = new HashMap<>();
		for (SurgicalTableLayout.CubeOffset offset : proposal.offsets()) {
			Vec3 value = new Vec3(offset.x(), offset.y(), offset.z());
			if (value.lengthSqr() > 1.0e-24d)
				offsets.put(offset.cubeId(), value);
		}
		return Map.copyOf(offsets);
	}

	static Map<Integer, Vec3> restoredOffsets(SurgicalAssembly.PlacedSource placed) {
		SurgicalAssembly.Source source = placed.source();
		Map<Integer, Vec3> restored = new HashMap<>();
		BitSet present = source.presentCubes();
		for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
			Vec3 offset = placed.cubeOffsets().getOrDefault(cube, Vec3.ZERO)
				.add(0.0d, placed.originOffset().y, 0.0d);
			if (offset.lengthSqr() > 1.0e-24d)
				restored.put(cube, offset);
		}
		return Map.copyOf(restored);
	}
}
