package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueJoint;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableInteractionPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalCutController implementation; public entry points remain on SurgicalTableClientHandler. */
final class SurgicalCutController {
	private SurgicalCutController() {}

	@Nullable
	static PendingCut pendingCut;
	@Nullable
	static PendingGlueCut pendingGlueCut;
	@Nullable
	static BatchCutAnimation batchCutAnimation;

	static void beginSingleCut(ClientLevel level, SurgicalSelectionResolver.Selection selected, InteractionHand hand) {
		if (!(level.getBlockEntity(selected.tablePos()) instanceof SurgicalTableBlockEntity table))
			return;
		SurgicalTableBlockEntity.SeamCutPlan plan = table.seamCutPlan(selected.subjectId(), selected.targetId());
		if (plan == null)
			return;
		SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(selected.tablePos(), selected.subjectId()));
		SurgicalTablePlane.Plane plane = SurgicalSelectionResolver.clientPlane(level, selected.tablePos());
		if (geometry == null || !plane.valid() || !selected.tablePos().equals(plane.source()))
			return;
		if (!plan.separates()) {
			SurgicalClientTopology.PlannedLayout layout = SurgicalClientTopology.translateComponents(
				geometry.observedCubeCount, geometry.presentCubes, geometry.seams, plan.proposedCuts(),
				geometry.layoutCubes, geometry.serverOffsets, plane.workArea(), new BitSet(), Vec3.ZERO, List.of());
			if (layout == null || !table.canApplySeamCut(selected.subjectId(), selected.targetId(),
				layout.proposal(), 0.0d, 0.0d, plane))
				SurgicalTableClientHandler.showFailure(Minecraft.getInstance().player);
			else
				SurgicalTableClientHandler.sendInteraction(selected, hand, SurgicalTableInteractionPacket.Action.CUT,
					layout.proposal());
			return;
		}
		pendingCut = new PendingCut(selected, hand, plan.proposedCuts(), plan.movingComponents(), null);
		updatePendingCut(Minecraft.getInstance().player, level);
	}

	static void beginGlueCut(ClientLevel level, SurgicalSelectionResolver.Selection selected, InteractionHand hand) {
		if (!(level.getBlockEntity(selected.tablePos()) instanceof SurgicalTableBlockEntity table))
			return;
		SurgicalTableBlockEntity.GlueCutPlan plan = table.glueCutPlan(selected.subjectId(), selected.targetId());
		if (plan == null)
			return;
		if (!plan.separates()) {
			SurgicalTableClientHandler.sendInteraction(selected, hand, SurgicalTableInteractionPacket.Action.CUT_GLUE,
				SurgicalTableLayout.Proposal.EMPTY);
			return;
		}
		pendingGlueCut = new PendingGlueCut(selected, hand, plan.joint(), plan.movingComponents(), null);
		updatePendingGlueCut(Minecraft.getInstance().player, level);
	}

	static void updatePendingGlueCut(@Nullable LocalPlayer player, ClientLevel level) {
		PendingGlueCut pending = pendingGlueCut;
		if (pending == null || player == null)
			return;
		if (!(level.getBlockEntity(pending.tablePos) instanceof SurgicalTableBlockEntity table)
			|| !SurgicalKitItem.isShears(player.getItemInHand(pending.hand))) {
			abortPendingGlueCut();
			return;
		}
		SurgicalTablePlane.Plane plane = SurgicalSelectionResolver.clientPlane(level, pending.tablePos);
		if (!plane.valid() || !pending.tablePos.equals(plane.source())) {
			abortPendingGlueCut();
			return;
		}
		Vec3 target = SurgicalSelectionResolver.tableSurfaceTarget(SurgicalSelectionResolver.playerRay(player), plane.workArea().surfaceY() + 0.01d);
		// The plan is a pure function of synced table data, so it cannot differ from the one validated
		// when this target was planned until the table revision moves.
		if (pending.planned != null && SurgicalSelectionResolver.componentSelection != null
			&& pending.plannedTableRevision == table.clientDataRevision()
			&& SurgicalSelectionResolver.sameHorizontalTarget(target, pending.lastTargetX, pending.lastTargetZ)) {
			SurgicalInteractionPresentation.highlightSelection(SurgicalSelectionResolver.componentSelection);
			return;
		}
		SurgicalTableBlockEntity.GlueCutPlan current = table.glueCutPlan(pending.subjectId, pending.targetId);
		if (current == null || !current.separates() || !pending.joint.equals(current.joint())
			|| !pending.movingComponents.equals(current.movingComponents())) {
			abortPendingGlueCut();
			return;
		}
		FootprintGroups footprints = glueCutFootprints(table, pending.movingComponents);
		if (footprints == null) {
			abortPendingGlueCut();
			return;
		}
		SurgicalClientTopology.ConnectedPlacement planned = SurgicalClientTopology.snapConnectedGroup(
			footprints.moving, plane.workArea(), target.x, target.z, footprints.occupied);
		if (planned == null || !table.canApplyGlueCut(pending.subjectId, pending.targetId,
			planned.delta().x, planned.delta().z, plane)) {
			if (planned == null)
				SurgicalSelectionResolver.failedPlacementSearch(plane.workArea(), footprints.moving, footprints.occupied, target).display(player);
			else
				SurgicalTableClientHandler.showFailure(player);
			abortPendingGlueCut();
			return;
		}

		List<SurgicalGeometryStore.GeometryPreview> previews = new ArrayList<>(pending.movingComponents.size());
		for (Map.Entry<Integer, BitSet> entry : pending.movingComponents.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(pending.tablePos, entry.getKey()));
			if (subject == null || !SurgicalGeometryStore.geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams)) {
				abortPendingGlueCut();
				return;
			}
			// The placement delta is absolute relative to the accepted server layout. Starting from
			// geometry.offsets would reuse the previous frame's preview and add the same delta again,
			// making a freshly cut glued group drift farther away on every preview refresh.
			Map<Integer, Vec3> offsets = new HashMap<>(geometry.serverOffsets);
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
				cube = entry.getValue().nextSetBit(cube + 1))
					offsets.put(cube, offsets.getOrDefault(cube, Vec3.ZERO).add(planned.delta()));
			previews.add(new SurgicalGeometryStore.GeometryPreview(geometry, offsets, geometry.cutSeams, pending.joint));
		}
		applyGeometryPreviews(table, previews);
		SurgicalSelectionResolver.SelectionHighlightEdges highlighted = SurgicalInteractionPresentation.connectedSelectionEdges(
			pending.tablePos, table, pending.movingComponents);
		if (highlighted == null) {
			abortPendingGlueCut();
			return;
		}
		pending.planned = planned;
		pending.lastTargetX = target.x;
		pending.lastTargetZ = target.z;
		pending.plannedTableRevision = table.clientDataRevision();
		SurgicalSelectionResolver.seamSelection = null;
		SurgicalSelectionResolver.cubeSelection = null;
		SurgicalSelectionResolver.componentSelection = new SurgicalSelectionResolver.Selection(pending.tablePos, pending.subjectId, pending.targetId,
			pending.observedCubeCount, pending.seams, List.of(), highlighted.glueJointEdges(),
			highlighted.cubeEdges(), highlighted.combinationEdges(), List.of(), true, false);
		SurgicalInteractionPresentation.highlightSelection(SurgicalSelectionResolver.componentSelection);
	}

	static void confirmPendingGlueCut(LocalPlayer player) {
		PendingGlueCut pending = pendingGlueCut;
		if (pending == null)
			return;
		if (!SurgicalKitItem.isShears(player.getItemInHand(pending.hand)) || pending.planned == null) {
			SurgicalTableClientHandler.showFailure(player);
			abortPendingGlueCut();
			return;
		}
		SurgicalSelectionResolver.Selection selected = new SurgicalSelectionResolver.Selection(pending.tablePos, pending.subjectId, pending.targetId,
			pending.observedCubeCount, pending.seams, List.of(), List.of(), true);
		Vec3 delta = pending.planned.delta();
		SurgicalTableClientHandler.sendInteraction(selected, pending.hand, SurgicalTableInteractionPacket.Action.CUT_GLUE,
			SurgicalTableLayout.Proposal.EMPTY, delta.x, delta.z);
		ClientLevel level = Minecraft.getInstance().level;
		pendingGlueCut = null;
		SurgicalSelectionResolver.componentSelection = null;
		if (level != null)
			SurgicalVisualCommitCoordinator.beginVisualCommit(level, pending.tablePos, pending.plannedTableRevision,
				List.copyOf(pending.movingComponents.keySet()), false, false);
	}

	static void abortPendingGlueCut() {
		PendingGlueCut pending = pendingGlueCut;
		pendingGlueCut = null;
		if (pending == null)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null
			&& minecraft.level.getBlockEntity(pending.tablePos) instanceof SurgicalTableBlockEntity table)
			clearGeometryPreviews(table, pending.tablePos, pending.movingComponents.keySet());
		SurgicalSelectionResolver.componentSelection = null;
	}

	@Nullable
	static FootprintGroups glueCutFootprints(SurgicalTableBlockEntity table,
		Map<Integer, BitSet> movingComponents) {
		List<SurgicalTableLayout.Footprint> moving = new ArrayList<>();
		List<SurgicalTableLayout.Footprint> occupied = new ArrayList<>();
		for (SurgicalSubject subject : table.getSubjects()) {
			if (subject.occupiedFootprints().isEmpty())
				return null;
			BitSet component = movingComponents.get(subject.id());
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints()) {
				if (subject.containsFootprint(component, footprint))
					moving.add(footprint);
				else
					occupied.add(footprint);
			}
		}
		return moving.isEmpty() ? null : new FootprintGroups(List.copyOf(moving), List.copyOf(occupied));
	}

	@Nullable
	static FootprintGroups seamCutFootprints(SurgicalTableBlockEntity table, PendingCut pending,
		SurgicalGeometryStore.TableGeometry editedGeometry, BitSet movedHere, SurgicalTablePlane.Plane plane) {
		SurgicalClientTopology.PlannedLayout current = SurgicalClientTopology.translateComponents(
			editedGeometry.observedCubeCount, editedGeometry.presentCubes, editedGeometry.seams,
			pending.proposedCuts, editedGeometry.layoutCubes, editedGeometry.serverOffsets,
			plane.workArea(), movedHere, Vec3.ZERO, List.of());
		if (current == null)
			return null;
		List<SurgicalTableLayout.Footprint> moving = new ArrayList<>();
		List<SurgicalTableLayout.Footprint> occupied = new ArrayList<>();
		for (SurgicalTableLayout.Footprint footprint : current.proposal().footprints())
			(movedHere.get(footprint.componentRoot()) ? moving : occupied).add(footprint);
		for (SurgicalSubject subject : table.getSubjects()) {
			if (subject.id() == pending.subjectId)
				continue;
			BitSet moved = pending.movingComponents.get(subject.id());
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints())
				(subject.containsFootprint(moved, footprint) ? moving : occupied).add(footprint);
		}
		return moving.isEmpty() ? null : new FootprintGroups(List.copyOf(moving), List.copyOf(occupied));
	}

	@Nullable
	static BatchCutLayout planBatchCut(ClientLevel level, SurgicalSelectionResolver.Selection selected) {
		SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(selected.tablePos(), selected.subjectId()));
		SurgicalTableBlockEntity table = level.getBlockEntity(selected.tablePos())
			instanceof SurgicalTableBlockEntity found ? found : null;
		if (geometry == null || table == null
			|| selected.targetId() < 0 || selected.targetId() >= geometry.observedCubeCount)
			return null;
		SurgicalTableBlockEntity.BatchCutPlan cut = table.batchCutPlan(selected.subjectId(),
			selected.targetId(), selected.observedCubeCount(), selected.seams());
		if (cut == null)
			return null;
		SurgicalTablePlane.Plane plane = SurgicalSelectionResolver.clientPlane(level, selected.tablePos());
		if (!plane.valid() || !selected.tablePos().equals(plane.source()))
			return null;
		SurgicalClientTopology.PlannedLayout base = SurgicalClientTopology.preserveCompositeLayout(
			geometry.observedCubeCount, geometry.presentCubes, geometry.seams, cut.proposedCuts(),
			geometry.layoutCubes, geometry.serverOffsets, plane.workArea(), List.of());
		if (base == null)
			return null;
		BatchCutFootprints footprints = batchCutFootprints(table, selected.subjectId(), cut.groups(),
			base.proposal());
		if (footprints == null)
			return null;
		SurgicalClientTopology.ConnectedGroupLayout groupLayout =
			SurgicalClientTopology.autoSnapConnectedGroups(footprints.groups, plane.workArea(),
				footprints.occupied);
		// The bounded multi-group search can exhaust its budget; null does not prove a lack of space.
		if (groupLayout == null)
			return null;
		SurgicalClientTopology.PlannedLayout planned = translateBatchProposal(base.proposal(),
			cut.groups(), groupLayout.deltas(), selected.subjectId());
		if (planned == null || !table.canApplyBatchCut(selected.subjectId(), selected.targetId(),
			selected.observedCubeCount(), selected.seams(), plane, planned.proposal(), groupLayout.deltas()))
			return null;
		return new BatchCutLayout(planned, groupLayout.deltas(), cut.groups(), cut.proposedCuts());
	}

	@Nullable
	static BatchCutFootprints batchCutFootprints(SurgicalTableBlockEntity table, int editedSubjectId,
		List<Map<Integer, BitSet>> groups, SurgicalTableLayout.Proposal editedLayout) {
		List<List<SurgicalTableLayout.Footprint>> grouped = new ArrayList<>(groups.size());
		for (int groupId = 0; groupId < groups.size(); groupId++)
			grouped.add(new ArrayList<>());
		List<SurgicalTableLayout.Footprint> occupied = new ArrayList<>();
		for (SurgicalSubject subject : table.getSubjects()) {
			List<SurgicalTableLayout.Footprint> footprints = subject.id() == editedSubjectId
				? editedLayout.footprints() : subject.occupiedFootprints();
			if (footprints.isEmpty())
				return null;
			for (SurgicalTableLayout.Footprint footprint : footprints) {
				int groupId = batchGroupContaining(groups, subject.id(), footprint);
				(groupId < 0 ? occupied : grouped.get(groupId)).add(footprint);
			}
		}
		for (List<SurgicalTableLayout.Footprint> group : grouped)
			if (group.isEmpty())
				return null;
		return new BatchCutFootprints(grouped.stream().map(List::copyOf).toList(),
			List.copyOf(occupied));
	}

	@Nullable
	static SurgicalClientTopology.PlannedLayout translateBatchProposal(
		SurgicalTableLayout.Proposal base, List<Map<Integer, BitSet>> groups, List<Vec3> deltas,
		int subjectId) {
		if (groups.size() != deltas.size())
			return null;
		Map<Integer, Vec3> offsets = new HashMap<>();
		List<SurgicalTableLayout.CubeOffset> translatedOffsets = new ArrayList<>(base.offsets().size());
		for (SurgicalTableLayout.CubeOffset offset : base.offsets()) {
			int groupId = batchGroupContaining(groups, subjectId, offset.cubeId());
			Vec3 delta = groupId < 0 ? Vec3.ZERO : deltas.get(groupId);
			Vec3 translated = new Vec3(offset.x() + delta.x, offset.y(), offset.z() + delta.z);
			offsets.put(offset.cubeId(), translated);
			translatedOffsets.add(new SurgicalTableLayout.CubeOffset(offset.cubeId(), translated.x,
				translated.y, translated.z));
		}
		List<SurgicalTableLayout.Footprint> translatedFootprints = new ArrayList<>(base.footprints().size());
		for (SurgicalTableLayout.Footprint footprint : base.footprints()) {
			int groupId = batchGroupContaining(groups, subjectId, footprint);
			Vec3 delta = groupId < 0 ? Vec3.ZERO : deltas.get(groupId);
			translatedFootprints.add(new SurgicalTableLayout.Footprint(footprint.componentRoot(),
				footprint.minX() + delta.x, footprint.minZ() + delta.z,
				footprint.maxX() + delta.x, footprint.maxZ() + delta.z,
				SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED));
		}
		return new SurgicalClientTopology.PlannedLayout(Map.copyOf(offsets),
			new SurgicalTableLayout.Proposal(translatedOffsets, translatedFootprints));
	}

	static int batchGroupContaining(List<Map<Integer, BitSet>> groups, int subjectId,
		int cubeId) {
		if (cubeId < 0)
			return -1;
		for (int groupId = 0; groupId < groups.size(); groupId++) {
			BitSet cubes = groups.get(groupId).get(subjectId);
			if (cubes != null && cubes.get(cubeId))
				return groupId;
		}
		return -1;
	}

	static int batchGroupContaining(List<Map<Integer, BitSet>> groups, int subjectId,
		SurgicalTableLayout.Footprint footprint) {
		if (footprint.componentRoot() >= 0)
			return batchGroupContaining(groups, subjectId, footprint.componentRoot());
		int matched = -1;
		for (int groupId = 0; groupId < groups.size(); groupId++) {
			BitSet cubes = groups.get(groupId).get(subjectId);
			if (cubes == null || cubes.isEmpty())
				continue;
			if (matched >= 0)
				return -1;
			matched = groupId;
		}
		return matched;
	}

	static void beginBatchCutAnimation(ClientLevel level, SurgicalSelectionResolver.Selection selected, BatchCutLayout layout) {
		batchCutAnimation = null;
		if (!(level.getBlockEntity(selected.tablePos()) instanceof SurgicalTableBlockEntity table))
			return;
		Map<Integer, BatchCutMotion> motions = new HashMap<>();
		for (SurgicalSubject subject : table.getSubjects()) {
			SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(selected.tablePos(), subject.id()));
			if (geometry == null || !geometry.matchesModel(table, subject))
				continue;
			BitSet movedCubes = new BitSet(geometry.observedCubeCount);
			Map<Integer, Vec3> targetOffsets = new HashMap<>();
			for (int cube = geometry.presentCubes.nextSetBit(0); cube >= 0;
				cube = geometry.presentCubes.nextSetBit(cube + 1)) {
				int groupId = batchGroupContaining(layout.groups, subject.id(), cube);
				Vec3 delta = groupId < 0 ? Vec3.ZERO : layout.groupDeltas.get(groupId);
				Vec3 target = geometry.serverOffsets.getOrDefault(cube, Vec3.ZERO).add(delta);
				if (target.lengthSqr() > 1.0e-24d)
					targetOffsets.put(cube, target);
				// Group zero is the stable remainder and can have no horizontal delta. It still belongs
				// to this batch cut and may move vertically when the newly separated bodies are grounded.
				if (groupId >= 0)
					movedCubes.set(cube);
			}
			if (!movedCubes.isEmpty())
				motions.put(subject.id(), new BatchCutMotion(movedCubes, geometry.offsets,
					Map.copyOf(targetOffsets), geometry.bounds));
		}
		if (motions.isEmpty())
			return;
		batchCutAnimation = new BatchCutAnimation(selected.tablePos(), table.clientDataRevision(),
			level.getGameTime() + SurgicalTableClientHandler.BATCH_CUT_ANIMATION_TIMEOUT_TICKS, selected.subjectId(),
			layout.proposedCuts, Map.copyOf(motions));
	}

	static void updateBatchCutAnimation(ClientLevel level) {
		BatchCutAnimation animation = batchCutAnimation;
		if (animation == null)
			return;
		if (level.getGameTime() >= animation.expiresAtTick
			|| !(level.getBlockEntity(animation.tablePos) instanceof SurgicalTableBlockEntity table)) {
			batchCutAnimation = null;
			return;
		}
		int revision = table.clientDataRevision();
		if (!animation.acknowledged) {
			if (revision == animation.tableRevision)
				return;
			if (!batchCutStateMatches(table, animation)) {
				batchCutAnimation = null;
				return;
			}
			animation.acknowledged = true;
			animation.acknowledgedRevision = revision;
		} else if (revision != animation.acknowledgedRevision) {
			// Any later edit interrupts this one-shot visual. It is never reconstructed from synced state.
			batchCutAnimation = null;
			return;
		}
		if (!Float.isNaN(animation.startedAt)
			&& AnimationTickHolder.getRenderTime(level) - animation.startedAt >= SurgicalTableClientHandler.BATCH_CUT_ANIMATION_TICKS) {
			batchCutAnimation = null;
			return;
		}
		if (!Float.isNaN(animation.startedAt))
			for (int subjectId : animation.motions.keySet())
				if (!SurgicalGeometryStore.TABLES.containsKey(new SurgicalGeometryStore.SubjectKey(animation.tablePos, subjectId))) {
					batchCutAnimation = null;
					return;
				}
	}

	static boolean batchCutStateMatches(SurgicalTableBlockEntity table,
		BatchCutAnimation animation) {
		SurgicalSubject edited = table.getSubject(animation.editedSubjectId);
		if (edited == null || !edited.cutSeamsForRender().equals(animation.proposedCuts))
			return false;
		for (Map.Entry<Integer, BatchCutMotion> entry : animation.motions.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			if (subject == null || !offsetMapsEqual(subject.componentOffsetsForRender(),
				entry.getValue().targetServerOffsets))
				return false;
		}
		return true;
	}

	static Map<Integer, Vec3> batchCutRenderOffsets(SurgicalTableBlockEntity table,
		SurgicalSubject subject, SurgicalGeometryStore.TableGeometry geometry) {
		BatchCutAnimation animation = batchCutAnimation;
		if (animation == null || !animation.tablePos.equals(table.getBlockPos()))
			return geometry.offsets;
		BatchCutMotion motion = animation.motions.get(subject.id());
		if (motion == null)
			return geometry.offsets;
		if (table.getLevel() instanceof ClientLevel level)
			updateBatchCutAnimation(level);
		animation = batchCutAnimation;
		if (animation == null || animation.motions.get(subject.id()) != motion || !animation.acknowledged)
			return geometry.offsets;
		if (!offsetMapsEqual(geometry.serverOffsets, motion.targetServerOffsets))
			return motion.interpolate(geometry.offsets, 0.0d);
		float renderTime = AnimationTickHolder.getRenderTime(table.getLevel());
		if (Float.isNaN(animation.startedAt))
			animation.startedAt = renderTime;
		double progress = Math.max(0.0d, Math.min(1.0d,
			(renderTime - animation.startedAt) / SurgicalTableClientHandler.BATCH_CUT_ANIMATION_TICKS));
		if (progress >= 1.0d) {
			batchCutAnimation = null;
			return geometry.offsets;
		}
		double remaining = 1.0d - progress;
		double eased = 1.0d - remaining * remaining * remaining;
		return motion.interpolate(geometry.offsets, eased);
	}

	static boolean offsetMapsEqual(Map<Integer, Vec3> first, Map<Integer, Vec3> second) {
		Set<Integer> cubes = new java.util.HashSet<>(first.keySet());
		cubes.addAll(second.keySet());
		for (int cube : cubes)
			if (first.getOrDefault(cube, Vec3.ZERO)
				.distanceToSqr(second.getOrDefault(cube, Vec3.ZERO)) > 1.0e-12d)
				return false;
		return true;
	}

	static void updatePendingCut(@Nullable LocalPlayer player, ClientLevel level) {
		PendingCut pending = pendingCut;
		if (pending == null || player == null)
			return;
		if (!(level.getBlockEntity(pending.tablePos) instanceof SurgicalTableBlockEntity table)
			|| !SurgicalKitItem.isShears(player.getItemInHand(pending.hand))) {
			abortPendingCut();
			return;
		}
		SurgicalTablePlane.Plane plane = SurgicalSelectionResolver.clientPlane(level, pending.tablePos);
		if (!plane.valid() || !pending.tablePos.equals(plane.source())) {
			abortPendingCut();
			return;
		}
		Vec3 target = SurgicalSelectionResolver.tableSurfaceTarget(SurgicalSelectionResolver.playerRay(player), plane.workArea().surfaceY() + 0.01d);
		// This early-out has to come before the cut plan is re-derived. The plan is a function of the
		// synced table data, which `plannedTableRevision` already pins, so re-deriving it while the
		// crosshair sits still only rebuilds the connection graph once per frame for no answer change.
		if (pending.planned != null && SurgicalSelectionResolver.componentSelection != null
			&& pending.plannedTableRevision == table.clientDataRevision()
			&& SurgicalSelectionResolver.sameHorizontalTarget(target, pending.lastTargetX, pending.lastTargetZ)) {
			SurgicalInteractionPresentation.highlightSelection(SurgicalSelectionResolver.componentSelection);
			return;
		}
		SurgicalTableBlockEntity.SeamCutPlan current = table.seamCutPlan(pending.subjectId, pending.targetId);
		if (current == null || !current.separates() || !pending.proposedCuts.equals(current.proposedCuts())
			|| !pending.movingComponents.equals(current.movingComponents())) {
			abortPendingCut();
			return;
		}
		SurgicalGeometryStore.TableGeometry editedGeometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(pending.tablePos, pending.subjectId));
		BitSet movedHere = pending.movingComponents.get(pending.subjectId);
		FootprintGroups footprints = editedGeometry == null || movedHere == null ? null
			: seamCutFootprints(table, pending, editedGeometry, movedHere, plane);
		if (footprints == null) {
			SurgicalTableClientHandler.showFailure(player);
			abortPendingCut();
			return;
		}
		SurgicalClientTopology.ConnectedPlacement planned = SurgicalClientTopology.snapConnectedGroup(
			footprints.moving, plane.workArea(), target.x, target.z, footprints.occupied);
		SurgicalClientTopology.PlannedLayout layout = planned == null ? null
			: SurgicalClientTopology.translateComponents(
				editedGeometry.observedCubeCount, editedGeometry.presentCubes, editedGeometry.seams,
				pending.proposedCuts, editedGeometry.layoutCubes, editedGeometry.serverOffsets,
				plane.workArea(), movedHere, planned.delta(), List.of());
		if (planned == null || layout == null || !table.canApplySeamCut(pending.subjectId, pending.targetId,
			layout.proposal(), planned.delta().x, planned.delta().z, plane)) {
			if (planned == null)
				SurgicalSelectionResolver.failedPlacementSearch(plane.workArea(), footprints.moving, footprints.occupied, target).display(player);
			else
				SurgicalTableClientHandler.showFailure(player);
			abortPendingCut();
			return;
		}

		List<SurgicalGeometryStore.GeometryPreview> previews = new ArrayList<>(pending.movingComponents.size());
		for (Map.Entry<Integer, BitSet> entry : pending.movingComponents.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(pending.tablePos, entry.getKey()));
			if (subject == null || !SurgicalGeometryStore.geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams)) {
				abortPendingCut();
				return;
			}
			Map<Integer, Vec3> offsets = entry.getKey() == pending.subjectId
				? layout.offsets() : new HashMap<>(geometry.serverOffsets);
			if (entry.getKey() != pending.subjectId)
				for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
					cube = entry.getValue().nextSetBit(cube + 1))
					offsets.put(cube, offsets.getOrDefault(cube, Vec3.ZERO).add(planned.delta()));
			BitSet cuts = entry.getKey() == pending.subjectId ? pending.proposedCuts : geometry.cutSeams;
			previews.add(new SurgicalGeometryStore.GeometryPreview(geometry, offsets, cuts, null));
		}
		applyGeometryPreviews(table, previews);
		SurgicalSelectionResolver.SelectionHighlightEdges highlighted = SurgicalInteractionPresentation.connectedSelectionEdges(
			pending.tablePos, table, pending.movingComponents);
		if (highlighted == null) {
			abortPendingCut();
			return;
		}
		pending.planned = planned;
		pending.layout = layout.proposal();
		pending.lastTargetX = target.x;
		pending.lastTargetZ = target.z;
		pending.plannedTableRevision = table.clientDataRevision();
		SurgicalSelectionResolver.seamSelection = null;
		SurgicalSelectionResolver.cubeSelection = null;
		SurgicalSelectionResolver.componentSelection = new SurgicalSelectionResolver.Selection(pending.tablePos, pending.subjectId, pending.targetId,
			pending.observedCubeCount, pending.seams, List.of(), highlighted.glueJointEdges(),
			highlighted.cubeEdges(), highlighted.combinationEdges(), List.of(), false, false);
		SurgicalInteractionPresentation.highlightSelection(SurgicalSelectionResolver.componentSelection);
	}

	static void confirmPendingCut(LocalPlayer player) {
		PendingCut pending = pendingCut;
		if (pending == null)
			return;
		if (!SurgicalKitItem.isShears(player.getItemInHand(pending.hand)) || pending.planned == null
			|| pending.layout == null) {
			SurgicalTableClientHandler.showFailure(player);
			abortPendingCut();
			return;
		}
		SurgicalSelectionResolver.Selection selected = new SurgicalSelectionResolver.Selection(pending.tablePos, pending.subjectId, pending.targetId,
			pending.observedCubeCount, pending.seams, List.of(), List.of());
		Vec3 delta = pending.planned.delta();
		SurgicalTableClientHandler.sendInteraction(selected, pending.hand, SurgicalTableInteractionPacket.Action.CUT,
			pending.layout, delta.x, delta.z);
		ClientLevel level = Minecraft.getInstance().level;
		pendingCut = null;
		SurgicalSelectionResolver.componentSelection = null;
		if (level != null)
			SurgicalVisualCommitCoordinator.beginVisualCommit(level, pending.tablePos, pending.plannedTableRevision,
				List.copyOf(pending.movingComponents.keySet()), false, false);
	}

	static void abortPendingCut() {
		PendingCut pending = pendingCut;
		pendingCut = null;
		if (pending == null)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null
			&& minecraft.level.getBlockEntity(pending.tablePos) instanceof SurgicalTableBlockEntity table)
			clearGeometryPreviews(table, pending.tablePos, pending.movingComponents.keySet());
		SurgicalSelectionResolver.componentSelection = null;
	}

	/** Stages every member before grounding any of them, so cross-subject Y uses one preview snapshot. */
	static void applyGeometryPreviews(SurgicalTableBlockEntity table,
		List<SurgicalGeometryStore.GeometryPreview> previews) {
		for (SurgicalGeometryStore.GeometryPreview preview : previews)
			preview.geometry().stagePreview(preview);
		for (SurgicalGeometryStore.GeometryPreview preview : previews)
			preview.geometry().applyStagedPreview(table);
	}

	/** Drops every staged member first; applying server state one-by-one must not see a half-old preview. */
	static void clearGeometryPreviews(SurgicalTableBlockEntity table, BlockPos tablePos,
		Iterable<Integer> subjectIds) {
		List<SurgicalGeometryStore.TableGeometry> geometries = new ArrayList<>();
		for (int subjectId : subjectIds) {
			SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, subjectId));
			if (geometry != null && table.hasSubject(subjectId))
				geometries.add(geometry);
		}
		for (SurgicalGeometryStore.TableGeometry geometry : geometries)
			geometry.discardStagedPreview();
		for (SurgicalGeometryStore.TableGeometry geometry : geometries)
			geometry.applyServerTransforms(table);
	}

	static final class PendingCut {
		final BlockPos tablePos;
		final int subjectId;
		final InteractionHand hand;
		private final int targetId;
		private final int observedCubeCount;
		private final List<SurgicalAssembly.Seam> seams;
		private final BitSet proposedCuts;
		private final Map<Integer, BitSet> movingComponents;
		@Nullable
		private SurgicalClientTopology.ConnectedPlacement planned;
		@Nullable
		private SurgicalTableLayout.Proposal layout;
		private double lastTargetX = Double.NaN;
		private double lastTargetZ = Double.NaN;
		private int plannedTableRevision = Integer.MIN_VALUE;

		private PendingCut(SurgicalSelectionResolver.Selection selection, InteractionHand hand, BitSet proposedCuts,
			Map<Integer, BitSet> movingComponents,
			@Nullable SurgicalClientTopology.ConnectedPlacement planned) {
			tablePos = selection.tablePos();
			subjectId = selection.subjectId();
			this.hand = hand;
			targetId = selection.targetId();
			observedCubeCount = selection.observedCubeCount();
			seams = List.copyOf(selection.seams());
			this.proposedCuts = (BitSet) proposedCuts.clone();
			Map<Integer, BitSet> frozen = new HashMap<>();
			movingComponents.forEach((key, value) -> frozen.put(key, (BitSet) value.clone()));
			this.movingComponents = Map.copyOf(frozen);
			this.planned = planned;
		}
	}

	static final class PendingGlueCut {
		final BlockPos tablePos;
		final int subjectId;
		final InteractionHand hand;
		private final int targetId;
		private final int observedCubeCount;
		private final List<SurgicalAssembly.Seam> seams;
		private final SurgicalGlueJoint joint;
		private final Map<Integer, BitSet> movingComponents;
		@Nullable
		private SurgicalClientTopology.ConnectedPlacement planned;
		private double lastTargetX = Double.NaN;
		private double lastTargetZ = Double.NaN;
		private int plannedTableRevision = Integer.MIN_VALUE;

		private PendingGlueCut(SurgicalSelectionResolver.Selection selection, InteractionHand hand, SurgicalGlueJoint joint,
			Map<Integer, BitSet> movingComponents,
			@Nullable SurgicalClientTopology.ConnectedPlacement planned) {
			tablePos = selection.tablePos();
			subjectId = selection.subjectId();
			this.hand = hand;
			targetId = selection.targetId();
			observedCubeCount = selection.observedCubeCount();
			seams = List.copyOf(selection.seams());
			this.joint = joint;
			Map<Integer, BitSet> frozen = new HashMap<>();
			movingComponents.forEach((key, value) -> frozen.put(key, (BitSet) value.clone()));
			this.movingComponents = Map.copyOf(frozen);
			this.planned = planned;
		}
	}

	record FootprintGroups(List<SurgicalTableLayout.Footprint> moving,
		List<SurgicalTableLayout.Footprint> occupied) {}

	record BatchCutLayout(SurgicalClientTopology.PlannedLayout layout, List<Vec3> groupDeltas,
		List<Map<Integer, BitSet>> groups, BitSet proposedCuts) {
		BatchCutLayout {
			groupDeltas = List.copyOf(groupDeltas);
			List<Map<Integer, BitSet>> frozenGroups = new ArrayList<>(groups.size());
			for (Map<Integer, BitSet> group : groups) {
				Map<Integer, BitSet> frozenGroup = new HashMap<>();
				group.forEach((subjectId, cubes) -> frozenGroup.put(subjectId, (BitSet) cubes.clone()));
				frozenGroups.add(Map.copyOf(frozenGroup));
			}
			groups = List.copyOf(frozenGroups);
			proposedCuts = (BitSet) proposedCuts.clone();
		}
	}

	record BatchCutFootprints(List<List<SurgicalTableLayout.Footprint>> groups,
		List<SurgicalTableLayout.Footprint> occupied) {
		BatchCutFootprints {
			groups = groups.stream().map(List::copyOf).toList();
			occupied = List.copyOf(occupied);
		}
	}

	/** Ephemeral client-only state; server sync can acknowledge it but can never recreate it. */
	static final class BatchCutAnimation {
		final BlockPos tablePos;
		private final int tableRevision;
		private final long expiresAtTick;
		private final int editedSubjectId;
		private final BitSet proposedCuts;
		final Map<Integer, BatchCutMotion> motions;
		private boolean acknowledged;
		private int acknowledgedRevision = Integer.MIN_VALUE;
		private float startedAt = Float.NaN;

		private BatchCutAnimation(BlockPos tablePos, int tableRevision, long expiresAtTick,
			int editedSubjectId, BitSet proposedCuts, Map<Integer, BatchCutMotion> motions) {
			this.tablePos = tablePos.immutable();
			this.tableRevision = tableRevision;
			this.expiresAtTick = expiresAtTick;
			this.editedSubjectId = editedSubjectId;
			this.proposedCuts = (BitSet) proposedCuts.clone();
			this.motions = Map.copyOf(motions);
		}
	}

	static final class BatchCutMotion {
		private final BitSet movedCubes;
		private final Map<Integer, Vec3> startRenderOffsets;
		private final Map<Integer, Vec3> targetServerOffsets;
		@Nullable
		final AABB startBounds;

		private BatchCutMotion(BitSet movedCubes, Map<Integer, Vec3> startRenderOffsets,
			Map<Integer, Vec3> targetServerOffsets, @Nullable AABB startBounds) {
			this.movedCubes = (BitSet) movedCubes.clone();
			this.startRenderOffsets = Map.copyOf(startRenderOffsets);
			this.targetServerOffsets = Map.copyOf(targetServerOffsets);
			this.startBounds = startBounds;
		}

		private Map<Integer, Vec3> interpolate(Map<Integer, Vec3> logicalOffsets, double progress) {
			Map<Integer, Vec3> rendered = new HashMap<>(logicalOffsets);
			for (int cube = movedCubes.nextSetBit(0); cube >= 0; cube = movedCubes.nextSetBit(cube + 1)) {
				Vec3 start = startRenderOffsets.getOrDefault(cube, Vec3.ZERO);
				Vec3 end = logicalOffsets.getOrDefault(cube, Vec3.ZERO);
				Vec3 offset = start.lerp(end, progress);
				if (offset.lengthSqr() <= 1.0e-24d)
					rendered.remove(cube);
				else
					rendered.put(cube, offset);
			}
			return Map.copyOf(rendered);
		}
	}
}
