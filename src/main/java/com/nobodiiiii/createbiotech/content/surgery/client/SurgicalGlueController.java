package com.nobodiiiii.createbiotech.content.surgery.client;

import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueTransform;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableFailure;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableGluePacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalGlueController implementation; public entry points remain on SurgicalTableClientHandler. */
final class SurgicalGlueController {
	private SurgicalGlueController() {}

	@Nullable
	static PendingGlue pendingGlue;
	@Nullable
	static SurgicalSelectionResolver.GluePoint hoveredGluePoint;
	@Nullable
	static SurgicalGluePreviewPlanner.GluePreview gluePreview;
	@Nullable
	static GlueEditor glueEditor;

	static boolean handleGlueClick(LocalPlayer player, ClientLevel level, InteractionHand hand,
		@Nullable SurgicalSelectionResolver.CubeHit hit) {
		if (glueEditor != null) {
			if (glueEditor.hand != hand || !SurgicalSelectionResolver.isSmartGlue(player.getItemInHand(hand))) {
				clearPendingGlue();
				return true;
			}
			confirmGlue(player, level, glueEditor);
			return true;
		}
		hit = SurgicalSelectionResolver.snapGlueHit(hit);
		if (hit == null)
			return pendingGlue != null;
		SurgicalSelectionResolver.Selection selected = SurgicalSelectionResolver.findConnectedComponentSelection(hit);
		if (selected == null)
			return false;
		Vec3 localHit = hit.location().subtract(Vec3.atLowerCornerOf(hit.tablePos()));
		if (pendingGlue == null) {
			SurgicalSymmetryController.clearPendingSymmetry();
			pendingGlue = new PendingGlue(selected, localHit, hand, hit.faceIndex(), hit.gluePoint());
			SurgicalSelectionResolver.componentSelection = null;
			SurgicalInteractionPresentation.clearSeamHighlight();
			player.displayClientMessage(Component.translatable(
				"message.create_biotech.surgical_table.glue_first"), true);
			AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(hit.location()), 0.5f, 0.85f, false);
			return true;
		}

		PendingGlue first = pendingGlue;
		if (first.hand != hand) {
			clearPendingGlue();
			return true;
		}
		if (!first.selection.tablePos().equals(selected.tablePos())) {
			clearPendingGlue();
			SurgicalTableFailure.DIFFERENT_TABLE.display(player);
			return true;
		}
		SurgicalGluePreviewPlanner.GluePreviewPlan planned = SurgicalGluePreviewPlanner.planGluePreview(level, first, hit);
		SurgicalGluePreviewPlanner.GluePreview preview = planned.preview();
		if (preview == null) {
			clearPendingGlue();
			planned.failure().display(player);
			return true;
		}
		SurgicalTableLayout.Proposal firstLayout = SurgicalGluePreviewPlanner.currentGlueLayout(level, first.selection);
		SurgicalTableLayout.Proposal secondLayout = SurgicalGluePreviewPlanner.currentGlueLayout(level, selected);
		SurgicalTablePlane.Plane gluePlane = SurgicalSelectionResolver.clientPlane(level, selected.tablePos());
		SurgicalTableBlockEntity glueTable = level.getBlockEntity(selected.tablePos())
			instanceof SurgicalTableBlockEntity table ? table : null;
		SurgicalSubject firstSubject = glueTable == null ? null : glueTable.getSubject(first.selection.subjectId());
		SurgicalSubject secondSubject = glueTable == null ? null : glueTable.getSubject(selected.subjectId());
		if (firstLayout == null || secondLayout == null || glueTable == null
			|| firstSubject == null || secondSubject == null
			|| firstSubject.cubeCount() != 0
				&& !glueTable.validateGlueLayout(firstSubject, gluePlane, firstLayout)
			|| firstSubject != secondSubject
				&& secondSubject.cubeCount() != 0
				&& !glueTable.validateGlueLayout(secondSubject, gluePlane, secondLayout)) {
			clearPendingGlue();
			SurgicalTableClientHandler.showFailure(player);
			return true;
		}
		SurgicalTableGluePacket.Endpoint firstEndpoint = SurgicalGluePreviewPlanner.glueEndpoint(first.selection, first.hit,
			firstLayout, first.faceIndex);
		SurgicalTableGluePacket.Endpoint secondEndpoint = SurgicalGluePreviewPlanner.glueEndpoint(selected, localHit,
			secondLayout, hit.faceIndex());
		if (firstEndpoint == null || secondEndpoint == null) {
			clearPendingGlue();
			SurgicalTableClientHandler.showFailure(player);
			return true;
		}
		if (SurgicalVisualCommitCoordinator.glueTopologyKnown(firstSubject, secondSubject)
			&& !glueTable.canGlueComponents(player.getItemInHand(hand), firstEndpoint.subjectId(),
				firstEndpoint.cubeId(), secondEndpoint.subjectId(), secondEndpoint.cubeId(),
				preview.targetPose(), preview.moves(), preview.anchorMoves(), SurgicalGlueTransform.IDENTITY,
				gluePlane, firstLayout, secondLayout)) {
			clearPendingGlue();
			SurgicalTableClientHandler.showFailure(player);
			return true;
		}
		if (SurgicalSelectionResolver.isSmartGlue(player.getItemInHand(hand))) {
			SurgicalModelRenderContext.CubeGeometry editCube = SurgicalGluePreviewPlanner.previewCube(preview.subjects(),
				firstEndpoint.subjectId(), firstEndpoint.cubeId());
			Vec3 editAxis = SurgicalGluePreviewPlanner.cubeFaceAxis(editCube, first.faceIndex);
			Vec3 axisCenter = editCube == null ? null : SurgicalGluePreviewPlanner.cubeCenter(editCube);
			Vec3 faceCenter = SurgicalGluePreviewPlanner.cubeFaceCenter(editCube, first.faceIndex);
			if (editAxis == null || axisCenter == null || faceCenter == null) {
				clearPendingGlue();
				SurgicalTableClientHandler.showFailure(player);
				return true;
			}
			if (!SurgicalGluePreviewPlanner.glueEndpointsActuallyIntersect(preview.subjects(), firstEndpoint, secondEndpoint)) {
				clearPendingGlue();
				SurgicalTableFailure.GLUE_CONTACT_REQUIRED.display(player);
				return true;
			}
			gluePreview = preview;
			glueEditor = new GlueEditor(hand, firstEndpoint, secondEndpoint, preview,
				axisCenter, faceCenter, editAxis, SurgicalGluePreviewPlanner.glueEditGuideRadius(editCube, axisCenter, editAxis));
			hoveredGluePoint = null;
			SurgicalInteractionPresentation.GLUE_POINT_OUTLINE.clear();
			SurgicalInteractionPresentation.clearSeamHighlight();
			SurgicalGluePreviewPlanner.refreshGlueEditGuide(player, level, glueEditor);
			AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(hit.location()), 0.5f, 0.9f, false);
			return true;
		}
		CBPackets.sendToServer(new SurgicalTableGluePacket(selected.tablePos(), hand,
			firstEndpoint, secondEndpoint, preview.targetPose(),
			preview.moves(), preview.anchorMoves(), SurgicalGlueTransform.IDENTITY));
		AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(hit.location()), 0.5f, 0.95f, false);
		SurgicalGluePreviewPlanner.commitGluePreview(level, preview);
		return true;
	}

	static void confirmGlue(LocalPlayer player, ClientLevel level, GlueEditor editor) {
		SurgicalGluePreviewPlanner.GluePreview preview = editor.preview;
		SurgicalTableBlockEntity table = level.getBlockEntity(preview.ownerPos())
			instanceof SurgicalTableBlockEntity found ? found : null;
		SurgicalSubject firstSubject = table == null ? null : table.getSubject(editor.first.subjectId());
		SurgicalSubject secondSubject = table == null ? null : table.getSubject(editor.second.subjectId());
		SurgicalTablePlane.Plane plane = SurgicalSelectionResolver.clientPlane(level, preview.ownerPos());
		if (table == null || firstSubject == null || secondSubject == null
			|| table.clientDataRevision() != preview.tableRevision()) {
			SurgicalTableFailure.PREVIEW_CHANGED.display(player);
			return;
		}
		if (!SurgicalGlueTransform.withinEditRange(editor.replayTransform.translation())) {
			SurgicalTableFailure.EDIT_LIMIT.display(player);
			return;
		}
		if (!SurgicalGluePreviewPlanner.glueEndpointsConnectWithinTolerance(preview.subjects(), editor.first, editor.second)) {
			SurgicalTableFailure.EDIT_LIMIT.display(player);
			return;
		}
		SurgicalTableFailure previewFailure = SurgicalSymmetryController.finalGluePreviewFailure(table, preview);
		if (previewFailure != null) {
			previewFailure.display(player);
			return;
		}
		if (SurgicalVisualCommitCoordinator.glueTopologyKnown(firstSubject, secondSubject)
				&& !table.canGlueComponents(player.getItemInHand(editor.hand), editor.first.subjectId(),
					editor.first.cubeId(), editor.second.subjectId(), editor.second.cubeId(),
					preview.targetPose(), preview.moves(), preview.anchorMoves(), editor.replayTransform,
					plane, editor.first.layout(), editor.second.layout())) {
			SurgicalTableClientHandler.showFailure(player);
			return;
		}
		CBPackets.sendToServer(new SurgicalTableGluePacket(preview.ownerPos(), editor.hand,
			editor.first, editor.second, preview.targetPose(),
			preview.moves(), preview.anchorMoves(), editor.replayTransform));
		AllSoundEvents.SLIME_ADDED.playAt(level, BlockPos.containing(preview.targetHit()), 0.5f, 0.95f, false);
		SurgicalGluePreviewPlanner.commitGluePreview(level, preview);
	}

	static void clearPendingGlue() {
		pendingGlue = null;
		hoveredGluePoint = null;
		gluePreview = null;
		glueEditor = null;
		SurgicalInteractionPresentation.GLUE_EDIT_OUTLINE.clear();
		SurgicalInteractionPresentation.GLUE_POINT_OUTLINE.clear();
		SurgicalInteractionPresentation.CUBE_OUTLINE.clear();
		SurgicalInteractionPresentation.COMBINATION_OUTLINE.clear();
		SurgicalInteractionPresentation.GLUE_JOINT_OUTLINE.clear();
	}

	record PendingGlue(SurgicalSelectionResolver.Selection selection, Vec3 hit, InteractionHand hand, int faceIndex,
		SurgicalSelectionResolver.GluePoint point) {}

	static final class GlueEditor {
		final InteractionHand hand;
		final SurgicalTableGluePacket.Endpoint first;
		private final SurgicalTableGluePacket.Endpoint second;
		Vec3 axis;
		double guideRadius;
		SurgicalGluePreviewPlanner.GluePreview preview;
		Vec3 axisCenter;
		Vec3 faceCenter;
		SurgicalGlueTransform replayTransform = SurgicalGlueTransform.IDENTITY;

		private GlueEditor(InteractionHand hand, SurgicalTableGluePacket.Endpoint first,
			SurgicalTableGluePacket.Endpoint second, SurgicalGluePreviewPlanner.GluePreview preview, Vec3 axisCenter,
			Vec3 faceCenter, Vec3 axis, double guideRadius) {
			this.hand = hand;
			this.first = first;
			this.second = second;
			this.preview = preview;
			this.axisCenter = axisCenter;
			this.faceCenter = faceCenter;
			this.axis = axis.normalize();
			this.guideRadius = guideRadius;
		}
	}
}
