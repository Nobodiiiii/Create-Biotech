package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombination;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueContact;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueJoint;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueTransform;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableFailure;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableGluePacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableSymmetryPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalSymmetryController implementation; public entry points remain on SurgicalTableClientHandler. */
final class SurgicalSymmetryController {
	private SurgicalSymmetryController() {}

	@Nullable
	static SurgicalGlueController.PendingGlue pendingSymmetry;
	@Nullable
	static SymmetryReference pendingSymmetryReference;
	@Nullable
	static SymmetryPlaneCandidate symmetryPlaneSelection;

	/** Selects the moving glue point, the installed reference, and finally one of its three centre planes. */
	static boolean handleSymmetryClick(LocalPlayer player, ClientLevel level, InteractionHand hand,
		@Nullable SurgicalSelectionResolver.CubeHit hit) {
		if (pendingSymmetry == null) {
			SurgicalSelectionResolver.CubeHit glueHit = SurgicalSelectionResolver.snapGlueHit(hit);
			if (glueHit == null)
				return false;
			SurgicalSelectionResolver.Selection selected = SurgicalSelectionResolver.findConnectedComponentSelection(glueHit);
			if (selected == null)
				return false;
			SurgicalGlueController.clearPendingGlue();
			Vec3 localHit = glueHit.location().subtract(Vec3.atLowerCornerOf(glueHit.tablePos()));
			pendingSymmetry = new SurgicalGlueController.PendingGlue(selected, localHit, hand, glueHit.faceIndex(), glueHit.gluePoint());
			SurgicalSelectionResolver.componentSelection = null;
			SurgicalInteractionPresentation.clearSeamHighlight();
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.symmetry_first"), true);
			AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(glueHit.location()), 0.5f, 1.1f, false);
			return true;
		}

		SurgicalGlueController.PendingGlue first = pendingSymmetry;
		if (first.hand() != hand) {
			clearPendingSymmetry();
			return true;
		}
		if (pendingSymmetryReference == null) {
			SymmetryReference reference = findSymmetryReference(hit);
			if (reference == null || !first.selection().tablePos().equals(reference.referenceHit.tablePos())) {
				player.displayClientMessage(Component.translatable(
					"message.create_biotech.surgical_table.symmetry_target_required"), true);
				return true;
			}
			pendingSymmetryReference = reference;
			SurgicalSelectionResolver.symmetrySelection = reference.selection(false);
			symmetryPlaneSelection = null;
			SurgicalGlueController.gluePreview = null;
			SurgicalSelectionResolver.componentSelection = null;
			SurgicalInteractionPresentation.clearSeamHighlight();
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.symmetry_reference"), true);
			AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(reference.referenceHit.location()),
				0.5f, 1.15f, false);
			return true;
		}

		boolean singleCube = pendingSymmetryReference.singleCube(Screen.hasControlDown());
		SymmetryPlaneCandidate selectedPlane = findSymmetryPlane(SurgicalSelectionResolver.playerRay(player),
			pendingSymmetryReference.mirrorBounds(singleCube), pendingSymmetryReference.planes(singleCube));
		SymmetryTarget target = selectedPlane == null ? null
			: symmetryTarget(pendingSymmetryReference, selectedPlane, singleCube);
		if (target == null) {
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.symmetry_plane_required"), true);
			return true;
		}
		SurgicalGluePreviewPlanner.GluePreview preview = SurgicalGluePreviewPlanner.planSymmetryPreview(level, first, target);
		SurgicalTableLayout.Proposal firstLayout = SurgicalGluePreviewPlanner.currentGlueLayout(level, first.selection());
		SurgicalSelectionResolver.Selection anchorSelection = SurgicalSelectionResolver.findCubeSelection(target.mirroredAnchorHit);
		SurgicalTableLayout.Proposal anchorLayout = anchorSelection == null ? null
			: SurgicalGluePreviewPlanner.currentGlueLayout(level, anchorSelection);
		SurgicalTablePlane.Plane plane = SurgicalSelectionResolver.clientPlane(level, target.referenceHit.tablePos());
		SurgicalTableBlockEntity table = level.getBlockEntity(target.referenceHit.tablePos())
			instanceof SurgicalTableBlockEntity found ? found : null;
		SurgicalSubject firstSubject = table == null ? null : table.getSubject(first.selection().subjectId());
		SurgicalSubject anchorSubject = table == null || anchorSelection == null ? null
			: table.getSubject(anchorSelection.subjectId());
		if (preview == null || firstLayout == null || anchorLayout == null || table == null
			|| firstSubject == null || anchorSubject == null || anchorSelection == null) {
			clearPendingSymmetry();
			SurgicalTableClientHandler.showFailure(player);
			return true;
		}
		SurgicalTableGluePacket.Endpoint firstEndpoint = SurgicalGluePreviewPlanner.glueEndpoint(first.selection(), first.hit(),
			firstLayout, first.faceIndex());
		Vec3 anchorHit = target.mirroredAnchorHit.location()
			.subtract(Vec3.atLowerCornerOf(target.mirroredAnchorHit.tablePos()));
		SurgicalTableGluePacket.Endpoint secondEndpoint = SurgicalGluePreviewPlanner.glueEndpoint(anchorSelection, anchorHit,
			anchorLayout, target.mirroredAnchorHit.faceIndex());
		if (firstEndpoint == null || secondEndpoint == null) {
			clearPendingSymmetry();
			SurgicalTableClientHandler.showFailure(player);
			return true;
		}
		if (!SurgicalGluePreviewPlanner.glueEndpointsConnectWithinTolerance(preview.subjects(), firstEndpoint, secondEndpoint)) {
			clearPendingSymmetry();
			SurgicalTableFailure.GLUE_CONTACT_REQUIRED.display(player);
			return true;
		}
		if (SurgicalVisualCommitCoordinator.glueTopologyKnown(firstSubject, anchorSubject)
				&& !table.canGlueComponents(player.getItemInHand(hand), firstEndpoint.subjectId(),
					firstEndpoint.cubeId(), secondEndpoint.subjectId(), secondEndpoint.cubeId(),
					preview.targetPose(), preview.moves(), preview.anchorMoves(), target.replayTransform,
					plane, firstLayout, anchorLayout)) {
			clearPendingSymmetry();
			SurgicalTableClientHandler.showFailure(player);
			return true;
		}

		SurgicalTableSymmetryPacket.Reference reference = new SurgicalTableSymmetryPacket.Reference(
			target.referenceHit.geometry().subjectId, target.referenceHit.cubeId(),
			target.referenceHit.geometry().observedCubeCount, target.referenceHit.geometry().seams,
			target.referenceAnchor.subjectKey(), target.referenceAnchor.cubeId(), target.singleCube);
		CBPackets.sendToServer(new SurgicalTableSymmetryPacket(target.referenceHit.tablePos(), hand,
			firstEndpoint, secondEndpoint, reference, preview.targetPose(), preview.moves(), preview.anchorMoves(),
			target.replayTransform));
		AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(target.mirroredAnchorHit.location()),
			0.5f, 1.2f, false);
		SurgicalGluePreviewPlanner.commitSymmetryPreview(level, preview);
		return true;
	}

	@Nullable
	static SurgicalTableFailure finalGluePreviewFailure(@Nullable SurgicalTableBlockEntity table,
		SurgicalGluePreviewPlanner.GluePreview preview) {
		if (table == null || table.clientDataRevision() != preview.tableRevision())
			return SurgicalTableFailure.PREVIEW_CHANGED;
		for (SurgicalGluePreviewPlanner.GlueSubjectPreview subjectPreview : preview.subjects()) {
			if (!subjectPreview.editable)
				continue;
			SurgicalSubject subject = table.getSubject(subjectPreview.subjectId);
			if (subject == null)
				return SurgicalTableFailure.PREVIEW_CHANGED;
			List<SurgicalModelRenderContext.CubeGeometry> rotated = SurgicalGluePreviewPlanner.transformCubes(
				subjectPreview.baseCubes, subjectPreview.rotations, Map.of());
			if (!SurgicalVisualCommitCoordinator.validEditedVertical(subjectPreview.cubes, rotated, subjectPreview.offsets,
				preview.workArea().surfaceY() + SurgicalTablePoseResolver.TABLE_CLEARANCE))
				return SurgicalTableFailure.EDIT_LIMIT;
			List<SurgicalTableLayout.Footprint> footprints = new ArrayList<>();
			for (int cubeId = subjectPreview.cubes.nextSetBit(0); cubeId >= 0;
				cubeId = subjectPreview.cubes.nextSetBit(cubeId + 1)) {
				SurgicalModelRenderContext.CubeGeometry cube = SurgicalGluePreviewPlanner.previewCube(subjectPreview, cubeId);
				if (cube == null)
					return SurgicalTableFailure.OPERATION_FAILED;
				AABB bounds = SurgicalGluePreviewPlanner.cubeBounds(cube);
				footprints.add(new SurgicalTableLayout.Footprint(cubeId, bounds.minX, bounds.minZ,
					bounds.maxX, bounds.maxZ, SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED));
			}
			if (SurgicalTableLayout.lacksSpace(preview.workArea(), footprints, preview.obstacles()))
				return SurgicalTableFailure.NO_SPACE;
			if (SurgicalClientTopology.preserveCompositeLayout(subject.cubeCount(), subjectPreview.cubes,
					subject.seams(), subject.cutSeamsForRender(), rotated, subjectPreview.offsets,
					preview.workArea(), preview.obstacles()) == null)
				return SurgicalTableFailure.OPERATION_FAILED;
		}
		return null;
	}

	static void clearPendingSymmetry() {
		pendingSymmetry = null;
		pendingSymmetryReference = null;
		symmetryPlaneSelection = null;
		SurgicalSelectionResolver.symmetrySelection = null;
		SurgicalGlueController.hoveredGluePoint = null;
		SurgicalGlueController.gluePreview = null;
		SurgicalInteractionPresentation.GLUE_POINT_OUTLINE.clear();
		SurgicalInteractionPresentation.CUBE_OUTLINE.clear();
		SurgicalInteractionPresentation.COMBINATION_OUTLINE.clear();
		SurgicalInteractionPresentation.GLUE_JOINT_OUTLINE.clear();
		SurgicalInteractionPresentation.clearSymmetryPlaneHighlight();
	}

	/** Locks the installed reference joint and both bounds used by the third-stage plane picker. */
	@Nullable
	static SymmetryReference findSymmetryReference(@Nullable SurgicalSelectionResolver.CubeHit hit) {
		ClientLevel level = Minecraft.getInstance().level;
		if (hit == null || level == null
			|| !(level.getBlockEntity(hit.tablePos()) instanceof SurgicalTableBlockEntity table))
			return null;
		SurgicalSubject referenceSubject = table.getSubject(hit.geometry().subjectId);
		SurgicalModelRenderContext.CubeGeometry referenceCube = hit.geometry().cubesById.get(hit.cubeId());
		if (referenceSubject == null || referenceCube == null
			|| !SurgicalGeometryStore.geometryReadyForUse(table, referenceSubject, hit.geometry()))
			return null;

		SymmetryReference best = null;
		double bestDistance = Double.MAX_VALUE;
		SurgicalGlueJoint.Endpoint referenceEndpoint = new SurgicalGlueJoint.Endpoint(
			referenceSubject.persistentId(), hit.cubeId());
		for (int jointId = 0; jointId < referenceSubject.glueJoints().size(); jointId++) {
			SurgicalGlueJoint joint = referenceSubject.glueJoints().get(jointId);
			if (!joint.touches(referenceSubject.persistentId(), hit.cubeId())
				|| table.isInternalCombinationJoint(joint))
				continue;
			SurgicalGlueJoint.Endpoint anchorEndpoint = joint.other(referenceEndpoint);
			SurgicalSubject anchorSubject = anchorEndpoint == null ? null
				: table.getSubjectByPersistentId(anchorEndpoint.subjectKey());
			SurgicalCombination combination = anchorSubject == null ? null
				: anchorSubject.combinationContaining(anchorEndpoint.cubeId());
			if (anchorSubject == null)
				continue;
			SurgicalGlueTransform recordedReplay = joint.replayFrom(referenceEndpoint);
			// Recorded joints remember which side was originally moved. Selecting their anchor cannot
			// reproduce the forward A -> B operation; unrecorded slime seams use the identity fallback.
			if (joint.replay() != null && recordedReplay == null)
				continue;
			SurgicalSelectionResolver.GlueJointSelection jointSelection = SurgicalSelectionResolver.glueJointSelection(hit.tablePos(), table, referenceSubject,
				hit.geometry(), jointId);
			if (jointSelection == null || jointSelection.contact() == null)
				continue;
			double distance = SurgicalSelectionResolver.pointToContactDistance(hit.location(), jointSelection.contact());
			if (distance >= bestDistance)
				continue;

			SurgicalGeometryStore.TableGeometry anchorGeometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(hit.tablePos(), anchorSubject.id()));
			SurgicalModelRenderContext.CubeGeometry anchorCube = anchorGeometry == null ? null
				: anchorGeometry.cubesById.get(anchorEndpoint.cubeId());
			AABB singleCubeBounds = anchorCube == null ? null : SurgicalGluePreviewPlanner.cubeBounds(anchorCube);
			AABB honeyBounds = anchorCube == null ? null : combination == null ? singleCubeBounds
				: combinationBounds(hit.tablePos(), table, combination);
			if (anchorCube == null || singleCubeBounds == null || honeyBounds == null)
				continue;

			Vec3 originalAnchorContact = joint.replay() == null ? contactCenter(jointSelection.contact())
				: SurgicalGluePreviewPlanner.glueContactPoint(anchorCube, joint.replay().anchorContact());
			if (originalAnchorContact == null)
				continue;
			List<SurgicalClientTopology.Edge> singleCubeEdges = SurgicalClientTopology.cubeEdges(anchorCube);
			List<SurgicalClientTopology.Edge> honeyEdges = combination == null ? singleCubeEdges
				: SurgicalInteractionPresentation.combinationCubeEdges(hit.tablePos(), table, combination);
			SurgicalSelectionResolver.Selection honeySelection = new SurgicalSelectionResolver.Selection(hit.tablePos(), hit.geometry().subjectId, hit.cubeId(),
				hit.geometry().observedCubeCount, hit.geometry().seams, List.of(), jointSelection.contact().edges(),
				SurgicalClientTopology.cubeEdges(referenceCube), honeyEdges, List.of(), false, false);
			SurgicalSelectionResolver.Selection singleCubeSelection = new SurgicalSelectionResolver.Selection(hit.tablePos(), hit.geometry().subjectId, hit.cubeId(),
				hit.geometry().observedCubeCount, hit.geometry().seams, List.of(), jointSelection.contact().edges(),
				SurgicalClientTopology.cubeEdges(referenceCube), singleCubeEdges, List.of(), false, false);
			bestDistance = distance;
			best = new SymmetryReference(honeySelection, singleCubeSelection, hit, anchorEndpoint,
				combination, honeyBounds, singleCubeBounds, symmetryPlanes(honeyBounds),
				symmetryPlanes(singleCubeBounds), recordedReplay, originalAnchorContact,
				table.clientDataRevision());
		}
		return best;
	}

	@Nullable
	static SymmetryTarget symmetryTarget(SymmetryReference reference,
		SymmetryPlaneCandidate plane, boolean singleCube) {
		ClientLevel level = Minecraft.getInstance().level;
		if (reference == null || plane == null || level == null
			|| !(level.getBlockEntity(reference.referenceHit.tablePos()) instanceof SurgicalTableBlockEntity table)
			|| table.clientDataRevision() != reference.tableRevision)
			return null;
		SurgicalSubject anchorSubject = table.getSubjectByPersistentId(reference.referenceAnchor.subjectKey());
		SurgicalGeometryStore.TableGeometry anchorGeometry = anchorSubject == null ? null
			: SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(reference.referenceHit.tablePos(), anchorSubject.id()));
		SurgicalModelRenderContext.CubeGeometry anchorCube = anchorGeometry == null ? null
			: anchorGeometry.cubesById.get(reference.referenceAnchor.cubeId());
		if (anchorSubject == null || anchorGeometry == null || anchorCube == null)
			return null;

		Vec3 planeNormal = plane.normal;
		Vec3 planeCenter = plane.center;
		Vec3 anchorCenter = SurgicalGluePreviewPlanner.cubeCenter(anchorCube);
		SurgicalGlueJoint.Endpoint mirroredAnchor = reference.referenceAnchor;
		SurgicalSubject mirroredAnchorSubject = anchorSubject;
		SurgicalGeometryStore.TableGeometry mirroredAnchorGeometry = anchorGeometry;
		SurgicalModelRenderContext.CubeGeometry mirroredAnchorCube = anchorCube;
		if (!singleCube) {
			if (reference.combination == null)
				return null;
			Vec3 expectedCenter = reflectPoint(anchorCenter, planeCenter, planeNormal);
			SurgicalCombination.Member nearest = nearestCombinationMember(reference.referenceHit.tablePos(),
				table, reference.combination, expectedCenter);
			if (nearest == null)
				return null;
			mirroredAnchor = new SurgicalGlueJoint.Endpoint(nearest.subjectKey(), nearest.cubeId());
			mirroredAnchorSubject = table.getSubjectByPersistentId(nearest.subjectKey());
			mirroredAnchorGeometry = mirroredAnchorSubject == null ? null
				: SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(reference.referenceHit.tablePos(), mirroredAnchorSubject.id()));
			mirroredAnchorCube = mirroredAnchorGeometry == null ? null
				: mirroredAnchorGeometry.cubesById.get(nearest.cubeId());
			if (mirroredAnchorSubject == null || mirroredAnchorGeometry == null || mirroredAnchorCube == null)
				return null;
		}

		Vec3 mirroredLocalContact = reflectVector(
			reference.originalAnchorContact.subtract(anchorCenter), planeNormal);
		Vec3 mirroredContact = SurgicalGluePreviewPlanner.cubeCenter(mirroredAnchorCube).add(mirroredLocalContact);
		SurgicalGlueContact mirroredContactData = SurgicalGluePreviewPlanner.glueContact(mirroredAnchorCube, -1, mirroredContact);
		if (mirroredContactData == null)
			return null;
		mirroredContact = SurgicalGluePreviewPlanner.glueContactPoint(mirroredAnchorCube, mirroredContactData);
		if (mirroredContact == null)
			return null;
		SurgicalSelectionResolver.CubeHit mirroredHit = new SurgicalSelectionResolver.CubeHit(reference.referenceHit.tablePos(), mirroredAnchorGeometry,
			mirroredAnchor.cubeId(), mirroredContactData.faceIndex(), mirroredContact, null);
		SurgicalGlueTransform mirroredReplay = reference.recordedReplay == null
			? SurgicalGlueTransform.IDENTITY : reference.recordedReplay.mirrorAcross(planeNormal);
		return new SymmetryTarget(reference.selection(singleCube), reference.referenceHit, mirroredHit,
			reference.referenceAnchor, singleCube, mirroredReplay);
	}

	@Nullable
	static AABB combinationBounds(BlockPos tablePos, SurgicalTableBlockEntity table,
		SurgicalCombination combination) {
		AABB bounds = null;
		for (SurgicalCombination.Member member : combination.members()) {
			SurgicalSubject subject = table.getSubjectByPersistentId(member.subjectKey());
			SurgicalGeometryStore.TableGeometry geometry = subject == null ? null : SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, subject.id()));
			SurgicalModelRenderContext.CubeGeometry cube = geometry == null ? null
				: geometry.cubesById.get(member.cubeId());
			if (cube == null)
				return null;
			AABB cubeBounds = SurgicalGluePreviewPlanner.cubeBounds(cube);
			bounds = bounds == null ? cubeBounds : bounds.minmax(cubeBounds);
		}
		return bounds;
	}

	static List<SymmetryPlaneCandidate> symmetryPlanes(AABB bounds) {
		return List.of(symmetryPlane(bounds, Direction.Axis.X), symmetryPlane(bounds, Direction.Axis.Y),
			symmetryPlane(bounds, Direction.Axis.Z));
	}

	static SymmetryPlaneCandidate symmetryPlane(AABB bounds, Direction.Axis axis) {
		AABB expanded = bounds.inflate(SurgicalTableClientHandler.SYMMETRY_PLANE_MARGIN);
		Vec3 center = bounds.getCenter();
		Vec3 first;
		Vec3 second;
		Vec3 third;
		Vec3 fourth;
		Vec3 normal;
		switch (axis) {
			case X -> {
				normal = new Vec3(1.0d, 0.0d, 0.0d);
				first = new Vec3(center.x, expanded.minY, expanded.minZ);
				second = new Vec3(center.x, expanded.maxY, expanded.minZ);
				third = new Vec3(center.x, expanded.maxY, expanded.maxZ);
				fourth = new Vec3(center.x, expanded.minY, expanded.maxZ);
			}
			case Y -> {
				normal = new Vec3(0.0d, 1.0d, 0.0d);
				first = new Vec3(expanded.minX, center.y, expanded.minZ);
				second = new Vec3(expanded.maxX, center.y, expanded.minZ);
				third = new Vec3(expanded.maxX, center.y, expanded.maxZ);
				fourth = new Vec3(expanded.minX, center.y, expanded.maxZ);
			}
			case Z -> {
				normal = new Vec3(0.0d, 0.0d, 1.0d);
				first = new Vec3(expanded.minX, expanded.minY, center.z);
				second = new Vec3(expanded.maxX, expanded.minY, center.z);
				third = new Vec3(expanded.maxX, expanded.maxY, center.z);
				fourth = new Vec3(expanded.minX, expanded.maxY, center.z);
			}
			default -> throw new IllegalStateException("Unexpected symmetry axis " + axis);
		}
		return new SymmetryPlaneCandidate(axis, center, normal, List.of(
			new SurgicalClientTopology.Edge(first, second),
			new SurgicalClientTopology.Edge(second, third),
			new SurgicalClientTopology.Edge(third, fourth),
			new SurgicalClientTopology.Edge(fourth, first)));
	}

	@Nullable
	static SymmetryPlaneCandidate findSymmetryPlane(SurgicalSelectionResolver.Ray ray, AABB mirrorBounds,
		List<SymmetryPlaneCandidate> planes) {
		AABB bounds = mirrorBounds.inflate(SurgicalTableClientHandler.SYMMETRY_PLANE_MARGIN * 2.0d);
		Vec3 aimPoint = bounds.contains(ray.start()) ? ray.start() : bounds.clip(ray.start(), ray.end()).orElse(null);
		Vec3 view = ray.end().subtract(ray.start());
		if (aimPoint == null || view.lengthSqr() <= 1.0e-18d)
			return null;
		view = view.normalize();
		SymmetryPlaneCandidate best = null;
		double bestAlignment = Double.MAX_VALUE;
		double bestAimOffset = Double.MAX_VALUE;
		for (SymmetryPlaneCandidate plane : planes) {
			// A plane is parallel to the player's view when its normal is perpendicular to it.
			double alignment = Math.abs(view.dot(plane.normal));
			double aimOffset = Math.abs(aimPoint.subtract(plane.center).dot(plane.normal));
			if (alignment > bestAlignment + 1.0e-9d
				|| Math.abs(alignment - bestAlignment) <= 1.0e-9d && aimOffset >= bestAimOffset)
				continue;
			bestAlignment = alignment;
			bestAimOffset = aimOffset;
			best = plane;
		}
		return best;
	}

	@Nullable
	static SurgicalCombination.Member nearestCombinationMember(BlockPos tablePos,
		SurgicalTableBlockEntity table, SurgicalCombination combination, Vec3 expectedCenter) {
		SurgicalCombination.Member nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (SurgicalCombination.Member member : combination.members()) {
			SurgicalSubject subject = table.getSubjectByPersistentId(member.subjectKey());
			SurgicalGeometryStore.TableGeometry geometry = subject == null ? null : SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, subject.id()));
			SurgicalModelRenderContext.CubeGeometry cube = geometry == null ? null
				: geometry.cubesById.get(member.cubeId());
			if (cube == null)
				continue;
			double distance = SurgicalGluePreviewPlanner.cubeCenter(cube).distanceToSqr(expectedCenter);
			if (distance >= nearestDistance)
				continue;
			nearestDistance = distance;
			nearest = member;
		}
		return nearest;
	}

	@Nullable
	static Vec3 contactCenter(SurgicalClientTopology.Contact contact) {
		Vec3 sum = Vec3.ZERO;
		int count = 0;
		for (List<Vec3> face : contact.faces())
			for (Vec3 point : face) {
				sum = sum.add(point);
				count++;
			}
		return count == 0 ? null : sum.scale(1.0d / count);
	}

	static Vec3 reflectPoint(Vec3 point, Vec3 planeCenter, Vec3 planeNormal) {
		return planeCenter.add(reflectVector(point.subtract(planeCenter), planeNormal));
	}

	static Vec3 reflectVector(Vec3 vector, Vec3 planeNormal) {
		return vector.subtract(planeNormal.scale(2.0d * vector.dot(planeNormal)));
	}

	record SymmetryReference(SurgicalSelectionResolver.Selection honeySelection, SurgicalSelectionResolver.Selection singleCubeSelection,
		SurgicalSelectionResolver.CubeHit referenceHit, SurgicalGlueJoint.Endpoint referenceAnchor,
		@Nullable SurgicalCombination combination, AABB honeyBounds, AABB singleCubeBounds,
		List<SymmetryPlaneCandidate> honeyPlanes, List<SymmetryPlaneCandidate> singleCubePlanes,
		@Nullable SurgicalGlueTransform recordedReplay,
		Vec3 originalAnchorContact, int tableRevision) {
		SymmetryReference {
			honeyPlanes = List.copyOf(honeyPlanes);
			singleCubePlanes = List.copyOf(singleCubePlanes);
		}

		boolean singleCube(boolean controlDown) {
			return controlDown || combination == null;
		}

		SurgicalSelectionResolver.Selection selection(boolean singleCube) {
			return singleCube ? singleCubeSelection : honeySelection;
		}

		AABB mirrorBounds(boolean singleCube) {
			return singleCube ? singleCubeBounds : honeyBounds;
		}

		List<SymmetryPlaneCandidate> planes(boolean singleCube) {
			return singleCube ? singleCubePlanes : honeyPlanes;
		}
	}

	record SymmetryPlaneCandidate(Direction.Axis axis, Vec3 center, Vec3 normal,
		List<SurgicalClientTopology.Edge> edges) {
		SymmetryPlaneCandidate {
			edges = List.copyOf(edges);
		}
	}

	record SymmetryTarget(SurgicalSelectionResolver.Selection selection, SurgicalSelectionResolver.CubeHit referenceHit, SurgicalSelectionResolver.CubeHit mirroredAnchorHit,
		SurgicalGlueJoint.Endpoint referenceAnchor, boolean singleCube,
		SurgicalGlueTransform replayTransform) {}
}
