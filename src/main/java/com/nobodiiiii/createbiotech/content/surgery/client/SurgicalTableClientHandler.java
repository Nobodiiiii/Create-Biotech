package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxStatsTooltip;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalPackingAlignment;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueTransform;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBatchCutPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableFailure;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableInteractionPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLimbRemovalPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableCubeRotationPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalWrenchRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlacementResult;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableReleaseGeometryPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableShovelPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableTileShovelPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.ponder.api.PonderPalette;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class SurgicalTableClientHandler {
	static final int SEAM_HIGHLIGHT_COLOR = PonderPalette.RED.getColor();
	static final int CUBE_HIGHLIGHT_COLOR = PonderPalette.BLUE.getColor();
	static final int HONEY_HIGHLIGHT_COLOR = 0xE8A43A;
	static final int LIMB_JOINT_HIGHLIGHT_COLOR = 0xB46CFF;
	static final int RESERVED_HIGHLIGHT_COLOR = PonderPalette.GREEN.getColor();
	static final float HIGHLIGHT_LINE_WIDTH = 1.0f / 32.0f;
	static final float SEAM_HIGHLIGHT_LINE_WIDTH = HIGHLIGHT_LINE_WIDTH * 1.25f;
	static final float GLUE_POINT_LINE_WIDTH = HIGHLIGHT_LINE_WIDTH / 4.0f;
	static final float GLUE_FIRST_PREVIEW_ALPHA = 0.5f;
	static final double MAX_SELECTION_THRESHOLD = 3.0d / 16.0d;
	static final double GLUE_EDIT_TRANSLATION_STEP = 1.0d / 32.0d;
	static final double GLUE_EDIT_ROTATION_STEP = 5.0d;
	static final int GLUE_EDIT_CIRCLE_SEGMENTS = 32;
	static final double GLUE_EDIT_GUIDE_MARGIN = 1.0d / 16.0d;
	static final double MODEL_PIXEL_SIZE = 1.0d / 16.0d;
	static final double GLUE_POINT_SURFACE_OFFSET = 1.0d / 1024.0d;
	static final double GLUE_POINT_CROSS_HALF_LENGTH_PIXELS = 0.3d;
	static final double GLUE_POINT_PIXEL_EPSILON = 1.0e-4d;
	static final double SYMMETRY_PLANE_MARGIN = 1.0d / 32.0d;
	static final int ASYNC_TOPOLOGY_CUBE_THRESHOLD = 32;
	/** How long a confirmed topology contradiction stands before the model is observed again. */
	static final int RESERVED_REOBSERVE_TICKS = 20;
	/** Approximate retained cube/vertex/contact units; currently about eight maximum-size subjects. */
	private static final long MAX_GEOMETRY_CACHE_WEIGHT = 262_144L;
	/** RenderFrame follows ClientTick.Post, so protect a short window rather than only equal game-time. */
	private static final int GEOMETRY_CACHE_RECENT_TICKS = 20;
	/** Lets a geometry survive the old-controller-empty -> new-controller-full packet handoff. */
	private static final int GEOMETRY_OWNER_HANDOFF_TICKS = 40;
	static final int VISUAL_COMMIT_TIMEOUT_TICKS = 40;
	static final float BATCH_CUT_ANIMATION_TICKS = 5.0f;
	static final int BATCH_CUT_ANIMATION_TIMEOUT_TICKS = 40;
	static final InteractionHand[] HANDS = { InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND };
	private static boolean preparedForWorldRender;
	private static boolean suppressWrenchUseUntilRelease;
	private static boolean suppressCopyUseUntilRelease;
	/** Keeps the cancel click from falling through into vanilla's same-tick/held block attack pass. */
	private static boolean suppressAttackUntilRelease;

	private SurgicalTableClientHandler() {}

	/** Returns true only when the immutable source-model geometry has to be captured again. */
	public static boolean needsGeometryUpdate(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		return SurgicalGeometryStore.needsGeometryUpdate(table, subject);
	}

	public static boolean isRenderReady(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		return SurgicalGeometryStore.isRenderReady(table, subject);
	}

	@Nullable
	public static AABB cachedRenderBounds(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		return SurgicalGeometryStore.cachedRenderBounds(table, subject);
	}

	/** Replies once with the current world-space cuboids for an entire unsupported release batch. */
	public static void reportReleasedGeometry(
		SurgicalTableReleaseGeometryPacket.ClientBoundRequest request) {
		SurgicalGeometryStore.reportReleasedGeometry(request);
	}

	public static void updateGeometry(SurgicalTableBlockEntity table, SurgicalSubject subject,
		SurgicalModelRenderContext.Snapshot snapshot) {
		SurgicalGeometryStore.updateGeometry(table, subject, snapshot);
	}

	/**
	 * Whether this subject's stored topology is known to contradict the model this client observes.
	 *
	 * <p>Such a subject can never be rendered from its stored present-set, and asking for that set
	 * would filter the observation down to an arbitrary prefix of the replacement model's components.
	 * The renderer uses this to observe the whole model instead, so the reserved column can be raised
	 * to the height the source model actually measures.</p>
	 */
	public static boolean isReserved(SurgicalSubject subject) {
		return SurgicalGeometryStore.isReserved(subject);
	}

	public static Map<Integer, Vec3> offsetsFor(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		return SurgicalGeometryStore.offsetsFor(table, subject);
	}

	public static Map<Integer, SurgicalCubeRotation> rotationsFor(SurgicalTableBlockEntity table,
		SurgicalSubject subject) {
		return SurgicalGeometryStore.rotationsFor(table, subject);
	}

	public static BitSet presentCubesFor(SurgicalTableBlockEntity table, SurgicalSubject subject,
		int observedCubeCount) {
		return SurgicalGeometryStore.presentCubesFor(table, subject, observedCubeCount);
	}

	public static void clear() {
		suppressWrenchUseUntilRelease = false;
		suppressCopyUseUntilRelease = false;
		SurgicalPackedPreviewService.pendingPacking = null;
		SurgicalCutController.pendingCut = null;
		SurgicalCutController.pendingGlueCut = null;
		SurgicalGlueController.pendingGlue = null;
		SurgicalJointController.pendingSlimeSeam = null;
		SurgicalSymmetryController.pendingSymmetry = null;
		SurgicalSymmetryController.pendingSymmetryReference = null;
		SurgicalSymmetryController.symmetryPlaneSelection = null;
		SurgicalGlueController.hoveredGluePoint = null;
		SurgicalGlueController.gluePreview = null;
		SurgicalGlueController.glueEditor = null;
		if (SurgicalPlacementController.placementCandidate != null && SurgicalPlacementController.placementCandidate.source() != null) SurgicalPlacementController.placementCandidate.source().cancel();
		SurgicalPlacementController.placementCandidate = null;
		SurgicalPlacementController.placementPreviewResult = SurgicalTablePlacementResult.OPERATION_FAILED;
		SurgicalPlacementController.placementSuppression = null;
		SurgicalVisualCommitCoordinator.pendingVisualCommit = null;
		SurgicalCutController.batchCutAnimation = null;
		SurgicalPackedPreviewService.PACKED_BODY_PREVIEWS.clear();
		SurgicalPackedPreviewService.PACKED_MEASUREMENTS.values().forEach(SurgicalPackedPreviewService.PackedPreparation::cancel);
		SurgicalPackedPreviewService.PACKED_MEASUREMENTS.clear();
		SurgicalGroundingPreparation.GROUP_GROUNDING.clear();
		SurgicalGroundingPreparation.GROUP_PREPARATIONS.values().forEach(SurgicalGroundingPreparation.GroupPreparationCache::cancel);
		SurgicalGroundingPreparation.GROUP_PREPARATIONS.clear();
		suppressAttackUntilRelease = false;
		SurgicalPlacementController.clearPlacementPreview();
		SurgicalGeometryStore.SUBJECT_GEOMETRIES.values().forEach(SurgicalGeometryStore.TableGeometry::dispose);
		SurgicalGeometryStore.TABLES.clear();
		SurgicalGeometryStore.SUBJECT_GEOMETRIES.clear();
		// A resource reload runs through here, and that is exactly when a replaced model may have gone
		// back to the one the stored topology was measured against. Retire every marker so the next
		// observation decides again.
		SurgicalGeometryStore.RESERVED_SUBJECTS.clear();
		SurgicalInteractionPresentation.RESERVED_OUTLINE.clear();
		SurgicalInteractionPresentation.RESERVED_SELECTION_OUTLINE.clear();
		SurgicalPlacementController.clearShovelTileOutline();
		SurgicalGeometryStore.validatedOwnerHandoff = null;
		SurgicalGeometryStore.SAFE_OWNER_HANDOFFS.clear();
		SurgicalGeometryStore.UNSAFE_OWNER_HANDOFFS.clear();
		SurgicalGeometryStore.geometryGeneration++;
		SurgicalSelectionResolver.lastSelectionRay = null;
		SurgicalSelectionResolver.lastSelectionPendingGlue = null;
		SurgicalSelectionResolver.lastSelectionPendingSymmetry = null;
		SurgicalSelectionResolver.lastSelectionPendingLimb = null;
		SurgicalSelectionResolver.lastSelectionPendingSlimeSeam = null;
		SurgicalJointController.pendingLimb = null;
		SurgicalTablePoseResolver.clear();
		SurgicalInteractionPresentation.GLUE_EDIT_OUTLINE.clear();
		SurgicalInteractionPresentation.GLUE_POINT_OUTLINE.clear();
		SurgicalInteractionPresentation.clearSymmetryPlaneHighlight();
		SurgicalSelectionResolver.clearSelections();
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (suppressWrenchUseUntilRelease && !minecraft.options.keyUse.isDown())
			suppressWrenchUseUntilRelease = false;
		if (suppressCopyUseUntilRelease && !minecraft.options.keyUse.isDown())
			suppressCopyUseUntilRelease = false;
		if (suppressAttackUntilRelease && !minecraft.options.keyAttack.isDown())
			suppressAttackUntilRelease = false;
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.screen != null) {
			SurgicalVisualCommitCoordinator.cancelPendingVisualCommit(level, true);
			SurgicalCutController.batchCutAnimation = null;
			SurgicalCutController.abortPendingCut();
			SurgicalCutController.abortPendingGlueCut();
			SurgicalGlueController.pendingGlue = null;
			SurgicalJointController.pendingSlimeSeam = null;
			SurgicalSymmetryController.pendingSymmetry = null;
			SurgicalSymmetryController.pendingSymmetryReference = null;
			SurgicalSymmetryController.symmetryPlaneSelection = null;
			SurgicalGlueController.hoveredGluePoint = null;
			SurgicalGlueController.gluePreview = null;
			SurgicalGlueController.glueEditor = null;
			SurgicalInteractionPresentation.GLUE_EDIT_OUTLINE.clear();
			SurgicalInteractionPresentation.GLUE_POINT_OUTLINE.clear();
			SurgicalPlacementController.clearPlacementPreview();
			SurgicalSelectionResolver.clearSelections();
			return;
		}

		long now = level.getGameTime();
		SurgicalVisualCommitCoordinator.updatePendingVisualCommit(level);
		SurgicalCutController.updateBatchCutAnimation(level);
		boolean removedGeometry = false;
		for (java.util.Iterator<Map.Entry<SurgicalGeometryStore.SubjectKey, SurgicalGeometryStore.TableGeometry>> iterator = SurgicalGeometryStore.TABLES.entrySet().iterator();
			iterator.hasNext();) {
			Map.Entry<SurgicalGeometryStore.SubjectKey, SurgicalGeometryStore.TableGeometry> entry = iterator.next();
			SurgicalGeometryStore.TableGeometry geometry = entry.getValue();
			SurgicalSubject subject = level.getBlockEntity(entry.getKey().tablePos())
				instanceof SurgicalTableBlockEntity table ? table.getSubject(entry.getKey().subjectId()) : null;
			if (subject != null && geometry.persistentId.equals(subject.persistentId()))
				continue;
			iterator.remove();
			geometry.orphan(now);
			removedGeometry = true;
		}
		for (SurgicalGeometryStore.TableGeometry geometry : List.copyOf(SurgicalGeometryStore.SUBJECT_GEOMETRIES.values())) {
			if (geometry.ownerKey != null || geometry.orphanedAtTick == Long.MIN_VALUE
				|| now - geometry.orphanedAtTick <= GEOMETRY_OWNER_HANDOFF_TICKS)
				continue;
			SurgicalGeometryStore.removeGeometry(geometry);
			removedGeometry = true;
		}
		long retainedWeight = SurgicalGeometryStore.SUBJECT_GEOMETRIES.values().stream()
			.mapToLong(SurgicalGeometryStore.TableGeometry::cacheWeight)
			.sum();
		// Last-use ordering evicts the coldest live geometries first. A short grace window protects
		// visible geometry across ClientTick.Post -> RenderFrame ordering and controller handoffs.
		if (retainedWeight > MAX_GEOMETRY_CACHE_WEIGHT) {
			List<SurgicalGeometryStore.TableGeometry> evictionCandidates = SurgicalGeometryStore.SUBJECT_GEOMETRIES.values().stream()
				.filter(geometry -> geometry.lastSeenTick < now - GEOMETRY_CACHE_RECENT_TICKS)
				.sorted(java.util.Comparator.comparingLong(geometry -> geometry.lastSeenTick))
				.toList();
			for (SurgicalGeometryStore.TableGeometry geometry : evictionCandidates) {
				if (retainedWeight <= MAX_GEOMETRY_CACHE_WEIGHT)
					break;
				retainedWeight -= geometry.cacheWeight();
				SurgicalGeometryStore.removeGeometry(geometry);
				removedGeometry = true;
			}
		}
		if (removedGeometry)
			SurgicalGeometryStore.geometryGeneration++;
		if (SurgicalCutController.pendingCut != null && !SurgicalGeometryStore.TABLES.containsKey(new SurgicalGeometryStore.SubjectKey(SurgicalCutController.pendingCut.tablePos, SurgicalCutController.pendingCut.subjectId)))
			SurgicalCutController.abortPendingCut();
		if (SurgicalCutController.pendingGlueCut != null
			&& !SurgicalGeometryStore.TABLES.containsKey(new SurgicalGeometryStore.SubjectKey(SurgicalCutController.pendingGlueCut.tablePos, SurgicalCutController.pendingGlueCut.subjectId)))
			SurgicalCutController.abortPendingGlueCut();
		if (SurgicalGlueController.pendingGlue != null && (!SurgicalGeometryStore.TABLES.containsKey(new SurgicalGeometryStore.SubjectKey(SurgicalGlueController.pendingGlue.selection().tablePos(),
			SurgicalGlueController.pendingGlue.selection().subjectId())) || !SurgicalSelectionResolver.isSurgicalGlue(player.getItemInHand(SurgicalGlueController.pendingGlue.hand()))))
			SurgicalGlueController.clearPendingGlue();
		if (SurgicalJointController.pendingSlimeSeam != null
			&& (!SurgicalGeometryStore.TABLES.containsKey(new SurgicalGeometryStore.SubjectKey(SurgicalJointController.pendingSlimeSeam.selection().tablePos(),
				SurgicalJointController.pendingSlimeSeam.selection().subjectId()))
				|| !SurgicalKitItem.isSlimeBall(player.getItemInHand(SurgicalJointController.pendingSlimeSeam.hand()))))
			SurgicalJointController.clearPendingSlimeSeam();
		if (SurgicalSymmetryController.pendingSymmetry != null && (!SurgicalGeometryStore.TABLES.containsKey(new SurgicalGeometryStore.SubjectKey(SurgicalSymmetryController.pendingSymmetry.selection().tablePos(),
			SurgicalSymmetryController.pendingSymmetry.selection().subjectId()))
			|| !SurgicalSelectionResolver.isSymmetryWand(player.getItemInHand(SurgicalSymmetryController.pendingSymmetry.hand()))))
			SurgicalSymmetryController.clearPendingSymmetry();
		if (SurgicalSymmetryController.pendingSymmetryReference != null
			&& (!(level.getBlockEntity(SurgicalSymmetryController.pendingSymmetryReference.referenceHit().tablePos())
				instanceof SurgicalTableBlockEntity table)
				|| table.clientDataRevision() != SurgicalSymmetryController.pendingSymmetryReference.tableRevision()))
			SurgicalSymmetryController.clearPendingSymmetry();
		if (SurgicalGlueController.glueEditor != null) {
			if (!(level.getBlockEntity(SurgicalGlueController.glueEditor.preview.ownerPos()) instanceof SurgicalTableBlockEntity table)
				|| table.clientDataRevision() != SurgicalGlueController.glueEditor.preview.tableRevision()
				|| !SurgicalSelectionResolver.isSmartGlue(player.getItemInHand(SurgicalGlueController.glueEditor.hand))) {
				SurgicalGlueController.clearPendingGlue();
			} else
				SurgicalGluePreviewPlanner.refreshGlueEditGuide(player, level, SurgicalGlueController.glueEditor);
		}
	}

	@SubscribeEvent
	public static void onRenderFrame(RenderFrameEvent.Pre event) {
		beginRenderPreparation();
		preparedForWorldRender = true;
		if (Minecraft.getInstance().level != null) {
			SurgicalVisualCommitCoordinator.updatePendingVisualCommit(Minecraft.getInstance().level);
			SurgicalCutController.updateBatchCutAnimation(Minecraft.getInstance().level);
		}
		SurgicalPlacementController.updatePlacementPreview();
		SurgicalSelectionResolver.updateSelections();
		SurgicalPackedPreviewService.updatePendingPacking();
		SurgicalPlacementController.updateShovelTileOutline();
		SurgicalInteractionPresentation.updateReservedOutline();
	}

	@SubscribeEvent
	public static void onWorldRender(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY)
			return;
		// Replay export calls GameRenderer directly and skips NeoForge's outer frame event.
		// Refresh the preparation budget and publish completed plans before the world draws subjects.
		if (!preparedForWorldRender)
			beginRenderPreparation();
		preparedForWorldRender = false;
	}

	@SubscribeEvent
	public static void onRenderFrameFinished(RenderFrameEvent.Post event) {
		preparedForWorldRender = false;
	}

	private static void beginRenderPreparation() {
		SurgicalCapturedRenderPlan.beginFrame();
		SurgicalTableRenderer.beginFrame();
	}

	@SubscribeEvent
	public static void renderPlacementPreview(RenderLevelStageEvent event) {
		SurgicalPlacementController.renderPlacementPreview(event);
	}

	@SubscribeEvent
	public static void renderPickupGroundPlane(RenderLevelStageEvent event) {
		SurgicalPlacementController.renderPickupGroundPlane(event);
	}

	@SubscribeEvent
	public static void renderGluePreview(RenderLevelStageEvent event) {
		SurgicalPlacementController.renderGluePreview(event);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void onSlimeSeamClickInput(InputEvent.InteractionKeyMappingTriggered event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != null || minecraft.player == null || minecraft.level == null)
			return;
		if (event.getKeyMapping() != minecraft.options.keyUse || !event.isUseItem())
			return;

		InteractionHand hand = event.getHand();
		ItemStack held = minecraft.player.getItemInHand(hand);
		if (!SurgicalKitItem.isSlimeBall(held))
			return;

		ClientLevel level = minecraft.level;
		SurgicalVisualCommitCoordinator.updatePendingVisualCommit(level);
		if (SurgicalVisualCommitCoordinator.pendingVisualCommit != null || SurgicalCutController.pendingCut != null || SurgicalCutController.pendingGlueCut != null)
			return;

		SurgicalSelectionResolver.Ray ray = SurgicalSelectionResolver.playerRay(minecraft.player);
		SurgicalSelectionResolver.CubeHit cubeHit = SurgicalSelectionResolver.findNearestSlimeSeamHit(minecraft.player, level, ray,
			SurgicalJointController.pendingSlimeSeam != null && SurgicalJointController.pendingSlimeSeam.hand() == hand ? SurgicalJointController.pendingSlimeSeam : null);
		if (SurgicalJointController.handleSlimeSeamClick(minecraft.player, level, hand, cubeHit))
			consumeInteraction(event, hand);
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != null || minecraft.player == null)
			return;
		KeyMapping key = event.getKeyMapping();
		if (key == minecraft.options.keyUse && (suppressWrenchUseUntilRelease || suppressCopyUseUntilRelease)) {
			consumeInteraction(event, event.getHand());
			return;
		}
		if (key == minecraft.options.keyAttack && suppressAttackUntilRelease) {
			suppressAttack(event);
			return;
		}
		if (SurgicalCutController.batchCutAnimation != null && (event.isUseItem() || key == minecraft.options.keyAttack))
			SurgicalCutController.batchCutAnimation = null;
		if (minecraft.level != null)
			SurgicalVisualCommitCoordinator.updatePendingVisualCommit(minecraft.level);
		if (SurgicalVisualCommitCoordinator.pendingVisualCommit != null && key == minecraft.options.keyUse && event.isUseItem()) {
			consumeInteraction(event, event.getHand());
			return;
		}
		if (key == minecraft.options.keyAttack && cancelPendingInteraction()) {
			suppressAttackUntilRelease = true;
			suppressAttack(event);
			return;
		}
		if (key != minecraft.options.keyUse || !event.isUseItem())
			return;

		InteractionHand hand = event.getHand();
		ItemStack held = minecraft.player.getItemInHand(hand);
		ClientLevel level = minecraft.level;
		if (level == null)
			return;
		if (SurgicalWrenchController.wrenchEditor != null && SurgicalWrenchController.wrenchEditor.hand == hand && SurgicalKitItem.isWrench(held)) {
			if (!SurgicalWrenchController.wrenchEditor.committing) {
				if (Screen.hasShiftDown())
					SurgicalWrenchController.alignAndConfirmWrench(minecraft.player, level, SurgicalWrenchController.wrenchEditor);
				else
					SurgicalWrenchController.confirmWrench(minecraft.player, level, SurgicalWrenchController.wrenchEditor);
			}
			suppressWrenchUseUntilRelease = true;
			consumeInteraction(event, hand);
			return;
		}
		if (SurgicalKitItem.isShears(held)) {
			SurgicalSelectionResolver.Ray jointRay = SurgicalSelectionResolver.playerRay(minecraft.player);
			SurgicalSelectionResolver.LimbJointSelection joint = SurgicalSelectionResolver.findLimbJointSelection(minecraft.player, level, jointRay,
				SurgicalSelectionResolver.findNearestCubeHit(minecraft.player, level, jointRay));
			if (joint != null) {
				SurgicalCutController.abortPendingCut();
				SurgicalCutController.abortPendingGlueCut();
				CBPackets.sendToServer(new SurgicalTableLimbRemovalPacket(joint.selection().tablePos(), hand, joint.joint()));
				SurgicalSelectionResolver.clearSelections();
				consumeInteraction(event, hand);
				return;
			}
		}
		if (SurgicalCutController.pendingCut != null) {
			SurgicalCutController.confirmPendingCut(minecraft.player);
			consumeInteraction(event, hand);
			return;
		}
		if (SurgicalCutController.pendingGlueCut != null) {
			SurgicalCutController.confirmPendingGlueCut(minecraft.player);
			consumeInteraction(event, hand);
			return;
		}
		if (SurgicalKitItem.isSlimeBall(held))
			return;
		if (CapturedEntityBoxHelper.hasCapturedEntity(held)
			&& SurgicalPlacementController.tryPlaceSubject(minecraft.player, level, hand, held)) {
			if (SurgicalKitItem.isCubeCopier(held))
				suppressCopyUseUntilRelease = true;
			consumeInteraction(event, hand);
			return;
		}
		if (SurgicalKitItem.isShovel(held) && Screen.hasControlDown()) {
			BlockPos target = SurgicalPlacementController.targetedTableTile(minecraft, level);
			if (target == null)
				return;
			double volume = SurgicalSelectionResolver.tileShovelVolume(level, target);
			CBPackets.sendToServer(new SurgicalTableTileShovelPacket(target, hand, volume));
			SurgicalSelectionResolver.clearSelections();
			consumeInteraction(event, hand);
			return;
		}

		SurgicalSelectionResolver.Selection selected;
		SurgicalSelectionResolver.Ray ray = SurgicalSelectionResolver.playerRay(minecraft.player);
		SurgicalSelectionResolver.CubeHit cubeHit = SurgicalSelectionResolver.findNearestCubeHit(minecraft.player, level, ray);
		if (SurgicalKitItem.isWrench(held)) {
			if (cubeHit == null) {
				SurgicalWrenchController.wrenchEditor = null;
				SurgicalInteractionPresentation.GLUE_EDIT_OUTLINE.clear();
				return;
			}
			SurgicalSubject subject = level.getBlockEntity(cubeHit.tablePos())
				instanceof SurgicalTableBlockEntity table ? table.getSubject(cubeHit.geometry().subjectId) : null;
			if (subject == null)
				return;
			boolean single = !Screen.hasControlDown() && !Screen.hasShiftDown();
			SurgicalWrenchController.wrenchEditor = beginWrenchEditor(level, cubeHit, subject, hand, single);
			if (SurgicalWrenchController.wrenchEditor != null) {
				if (Screen.hasShiftDown())
					SurgicalWrenchController.alignAndConfirmWrench(minecraft.player, level, SurgicalWrenchController.wrenchEditor);
				SurgicalWrenchController.refreshWrenchEditor(minecraft.player, level);
			}
			suppressWrenchUseUntilRelease = true;
			consumeInteraction(event, hand);
			return;
		}
		SurgicalLimbType limbType = SurgicalSelectionResolver.heldLimbType(held);
		if (limbType != null) {
			if (SurgicalJointController.handleLimbClick(minecraft.player, level, hand, limbType, cubeHit))
				consumeInteraction(event, hand);
			return;
		}
		if (SurgicalSelectionResolver.isSymmetryWand(held)) {
			if (SurgicalSymmetryController.handleSymmetryClick(minecraft.player, level, hand, cubeHit))
				consumeInteraction(event, hand);
			return;
		}
		if (SurgicalKitItem.isShovel(held)) {
			selected = SurgicalSelectionResolver.findConnectedComponentSelection(cubeHit);
			if (selected == null) {
				// Nothing pickable there, but a reserved column may be: it is the one thing a shovel
				// can still address on a subject whose cubes this client cannot name.
				SurgicalSelectionResolver.reservedSelection = SurgicalSelectionResolver.findReservedHit(minecraft.player, level, ray);
				if (SurgicalSelectionResolver.shovelReservedSubject(level, hand))
					consumeInteraction(event, hand);
				return;
			}
			double volume = SurgicalSelectionResolver.connectedComponentVolume(level, cubeHit);
			SurgicalModelRenderContext.CubeGeometry hitGeometry = cubeHit.geometry().cubesById.get(cubeHit.cubeId());
			if (!Double.isFinite(volume) || hitGeometry == null)
				return;
			Vec3 dropPosition = SurgicalClientTopology.center(hitGeometry);
			CBPackets.sendToServer(new SurgicalTableShovelPacket(selected.tablePos(), hand,
				selected.subjectId(), selected.targetId(), selected.observedCubeCount(), selected.seams(),
				volume, dropPosition));
			SurgicalSelectionResolver.clearSelections();
			consumeInteraction(event, hand);
			return;
		}
		if (SurgicalSelectionResolver.isSurgicalGlue(held)) {
			if (SurgicalGlueController.handleGlueClick(minecraft.player, level, hand, cubeHit))
				consumeInteraction(event, hand);
			return;
		} else if (SurgicalKitItem.isShears(held)) {
			if (Screen.hasControlDown()) {
				selected = SurgicalSelectionResolver.findDirectConnectionCutSelection(cubeHit);
				SurgicalSelectionResolver.componentSelection = selected;
				SurgicalSelectionResolver.seamSelection = null;
				if (selected == null)
					return;
				if (selected.combination()) {
					sendInteraction(selected, hand,
						SurgicalTableInteractionPacket.Action.DETACH_COMBINATION,
						SurgicalTableLayout.Proposal.EMPTY);
					consumeInteraction(event, hand);
					return;
				}
				SurgicalCutController.BatchCutLayout planned = SurgicalCutController.planBatchCut(level, selected);
				if (planned == null) {
					showFailure(minecraft.player);
					consumeInteraction(event, hand);
					return;
				}
				SurgicalCutController.beginBatchCutAnimation(level, selected, planned);
				CBPackets.sendToServer(new SurgicalTableBatchCutPacket(selected.tablePos(), hand,
					selected.subjectId(), selected.targetId(), selected.observedCubeCount(), selected.seams(),
					planned.layout().proposal(), planned.groupDeltas()));
			} else {
				selected = SurgicalSelectionResolver.findSeamSelection(minecraft.player, level, ray, cubeHit);
				SurgicalSelectionResolver.seamSelection = selected;
				if (selected == null)
					return;
				if (selected.combination())
					sendInteraction(selected, hand,
						SurgicalTableInteractionPacket.Action.BREAK_COMBINATION,
						SurgicalTableLayout.Proposal.EMPTY);
				else if (selected.glueJoint())
					SurgicalCutController.beginGlueCut(level, selected, hand);
				else
					SurgicalCutController.beginSingleCut(level, selected, hand);
			}
		} else if (SurgicalKitItem.isHoneyBottle(held)) {
			selected = SurgicalSelectionResolver.findConnectedComponentSelection(cubeHit);
			SurgicalSelectionResolver.componentSelection = selected;
			if (selected == null)
				return;
			sendInteraction(selected, hand, SurgicalTableInteractionPacket.Action.COMBINE,
				SurgicalTableLayout.Proposal.EMPTY);
		} else if (SurgicalSelectionResolver.isEmptyBox(held)) {
			selected = SurgicalSelectionResolver.findConnectedComponentSelection(cubeHit);
			SurgicalSelectionResolver.cubeSelection = selected;
			if (selected == null)
				return;
			SurgicalPackedPreviewService.PackedBodyMetrics metrics = SurgicalPackedPreviewService.selectedBodyMetrics(selected);
			if (metrics == null) {
				SurgicalPackedPreviewService.queuePacking(selected, hand, held, SurgicalTableInteractionPacket.Action.PACK);
				consumeInteraction(event, hand);
				return;
			}
			SurgicalPackedPreviewService.pendingPacking = null;
			sendInteraction(selected, hand, SurgicalTableInteractionPacket.Action.PACK,
				SurgicalTableLayout.Proposal.EMPTY, metrics.bodyBounds(), metrics.hitboxGeometry(),
				metrics.bodyVolume(), metrics.attackGeometry(), metrics.packingAlignment());
		} else if (SurgicalKitItem.isEmptyCubeCopier(held)) {
			selected = SurgicalSelectionResolver.findConnectedComponentSelection(cubeHit, true);
			if (selected == null)
				return;
			SurgicalPackedPreviewService.PackedBodyMetrics metrics = SurgicalPackedPreviewService.selectedBodyMetrics(selected);
			if (metrics == null) {
				SurgicalPackedPreviewService.queuePacking(selected, hand, held, SurgicalTableInteractionPacket.Action.COPY_COMPONENT);
				suppressCopyUseUntilRelease = true;
				consumeInteraction(event, hand);
				return;
			}
			SurgicalPackedPreviewService.pendingPacking = null;
			sendInteraction(selected, hand, SurgicalTableInteractionPacket.Action.COPY_COMPONENT,
				SurgicalTableLayout.Proposal.EMPTY, metrics.bodyBounds(), metrics.hitboxGeometry(),
				metrics.bodyVolume(), metrics.attackGeometry(), metrics.packingAlignment());
			suppressCopyUseUntilRelease = true;
		} else {
			return;
		}
		consumeInteraction(event, hand);
	}

	/** Cancels every staged table interaction without letting the attack reach the world. */
	private static boolean cancelPendingInteraction() {
		if (SurgicalWrenchController.wrenchEditor != null && SurgicalWrenchController.wrenchEditor.committing)
			return true;
		if (SurgicalPackedPreviewService.pendingPacking == null && SurgicalCutController.pendingCut == null && SurgicalCutController.pendingGlueCut == null && SurgicalGlueController.pendingGlue == null
			&& SurgicalSymmetryController.pendingSymmetry == null && SurgicalJointController.pendingLimb == null && SurgicalJointController.pendingSlimeSeam == null && SurgicalWrenchController.wrenchEditor == null)
			return false;
		SurgicalPackedPreviewService.pendingPacking = null;
		SurgicalCutController.abortPendingCut();
		SurgicalCutController.abortPendingGlueCut();
		SurgicalGlueController.clearPendingGlue();
		SurgicalSymmetryController.clearPendingSymmetry();
		SurgicalJointController.clearPendingLimb();
		SurgicalJointController.clearPendingSlimeSeam();
		SurgicalSelectionResolver.clearSelections();
		return true;
	}

	private static void suppressAttack(InputEvent.InteractionKeyMappingTriggered event) {
		event.setSwingHand(false);
		event.setCanceled(true);
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.screen != null)
			return;
		if (Screen.hasControlDown() && SurgicalWrenchController.wrenchEditor == null && SurgicalGlueController.glueEditor == null
			&& !SurgicalVisualCommitCoordinator.retainsPlacementPreview() && Math.abs(event.getScrollDeltaY()) > 1.0e-9d) {
			InteractionHand hand = SurgicalInteractionPresentation.capturedSubjectPlacementPromptHand(player, level);
			if (hand != null) {
				SurgicalPlacementController.PlacementCandidate candidate = SurgicalPlacementController.placementCandidateFor(player.getItemInHand(hand), level);
				if (candidate.source() != null) {
					SurgicalPlacementController.PlacementSource source = candidate.source();
					source.groundOrientation = Math.floorMod(source.groundOrientation
						+ (event.getScrollDeltaY() > 0 ? 1 : -1), SurgicalLayPose.RotationAxis.values().length);
					SurgicalPlacementController.updatePlacementPreview();
					event.setCanceled(true);
					return;
				}
			}
		}
		if (SurgicalWrenchController.wrenchEditor != null && SurgicalKitItem.isWrench(player.getItemInHand(SurgicalWrenchController.wrenchEditor.hand))) {
			double scroll = event.getScrollDeltaY();
			int face = SurgicalWrenchController.wrenchPreviewFace(player, level, SurgicalWrenchController.wrenchEditor);
			if (!SurgicalWrenchController.wrenchEditor.committing && Math.abs(scroll) > 1.0e-9d && face >= 0) {
				Vec3 axis = SurgicalGluePreviewPlanner.cubeFaceAxis(SurgicalWrenchController.wrenchEditor.selectedCube(), face);
				if (axis != null)
					SurgicalWrenchController.adjustWrenchPreview(level, SurgicalWrenchController.wrenchEditor, SurgicalWrenchController.scrollAdjustment(axis, scroll), false);
			}
			event.setCanceled(true);
			return;
		}
		if (SurgicalGlueController.glueEditor == null)
			return;
		if (!SurgicalSelectionResolver.isSmartGlue(player.getItemInHand(SurgicalGlueController.glueEditor.hand))) {
			SurgicalGlueController.clearPendingGlue();
			return;
		}
		double scroll = event.getScrollDeltaY();
		if (Math.abs(scroll) <= 1.0e-9d)
			return;
		if (!SurgicalGluePreviewPlanner.updateGlueEditAxis(player, level, SurgicalGlueController.glueEditor)) {
			SurgicalInteractionPresentation.GLUE_EDIT_OUTLINE.clear();
			event.setCanceled(true);
			return;
		}
		SurgicalGlueTransform adjustment = SurgicalWrenchController.scrollAdjustment(SurgicalGlueController.glueEditor.axis, scroll);
		Vec3 translation = adjustment.translation();
		SurgicalCubeRotation rotation = adjustment.rotation();
		SurgicalGluePreviewPlanner.GluePreview adjusted = SurgicalGluePreviewPlanner.adjustGluePreview(level, SurgicalGlueController.glueEditor, translation, rotation);
		if (adjusted != null) {
			SurgicalGlueController.glueEditor.replayTransform = SurgicalGlueController.glueEditor.replayTransform.then(translation, rotation);
			SurgicalGlueController.glueEditor.preview = adjusted;
			SurgicalGlueController.gluePreview = adjusted;
			AllSoundEvents.SCROLL_VALUE.playAt(level, BlockPos.containing(SurgicalGlueController.glueEditor.axisCenter),
				0.35f, Screen.hasControlDown() ? 0.85f : 1.0f, false);
		}
		SurgicalGluePreviewPlanner.refreshGlueEditGuide(player, level, SurgicalGlueController.glueEditor);
		event.setCanceled(true);
	}

	@Nullable
	private static SurgicalWrenchController.WrenchEditor beginWrenchEditor(ClientLevel level, SurgicalSelectionResolver.CubeHit hit, SurgicalSubject subject,
		InteractionHand hand, boolean single) {
		if (!(level.getBlockEntity(hit.tablePos()) instanceof SurgicalTableBlockEntity table))
			return null;
		Map<Integer, BitSet> group = table.connectedComponents(subject.id(), hit.cubeId(),
			hit.geometry().observedCubeCount, hit.geometry().seams);
		if (group.isEmpty())
			return null;
		List<SurgicalWrenchRotation.Body> bodies = new ArrayList<>();
		for (int bodyId : group.keySet().stream().sorted().toList()) {
			SurgicalSubject body = table.getSubject(bodyId);
			SurgicalGeometryStore.TableGeometry captured = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(hit.tablePos(), bodyId));
			if (body == null || !SurgicalGeometryStore.geometryReadyForUse(table, body, captured))
				return null;
			List<SurgicalWrenchRotation.Cube> cubes = new ArrayList<>();
			for (int id = captured.presentCubes.nextSetBit(0); id >= 0; id = captured.presentCubes.nextSetBit(id + 1)) {
				SurgicalModelRenderContext.CubeGeometry cube = captured.cubesById.get(id);
				if (cube == null)
					return null;
				cubes.add(new SurgicalWrenchRotation.Cube(id, cube.corners(),
					captured.offsets.getOrDefault(id, Vec3.ZERO),
					captured.rotations.getOrDefault(id, SurgicalCubeRotation.IDENTITY)));
			}
			bodies.add(new SurgicalWrenchRotation.Body(bodyId, body.persistentId(), captured.observedCubeCount,
				captured.seams, cubes));
		}
		return new SurgicalWrenchController.WrenchEditor(hit.tablePos(), subject.persistentId(), subject.id(), hit.cubeId(), hand, single,
			table.clientDataRevision(), group, List.copyOf(bodies));
	}

	public static void acceptWrenchCommit(SurgicalTableCubeRotationPacket.Result result) {
		SurgicalWrenchController.acceptWrenchCommit(result);
	}

	/** Reuses the accepted preview's neutral geometry; identity and edits remain server-owned. */
	static boolean seedPlacementGeometry(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		return SurgicalVisualCommitCoordinator.seedPlacementGeometry(table, subject);
	}

	/** Switches from the placement preview only after every newly added subject is render-ready. */
	static void completePlacementHandoffIfReady(SurgicalTableBlockEntity table) {
		SurgicalVisualCommitCoordinator.completePlacementHandoffIfReady(table);
	}

	static boolean suppressForPlacementHandoff(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		return SurgicalVisualCommitCoordinator.suppressForPlacementHandoff(table, subject);
	}

	static void prepareGroundingGroups(SurgicalTableBlockEntity table) {
		SurgicalGroundingPreparation.prepareGroundingGroups(table);
	}

	/** Prevents Create's block-area glue selector from consuming clicks aimed at surgical parts. */
	public static boolean shouldOverrideCreateGlue(ItemStack stack) {
		if (!SurgicalSelectionResolver.isSurgicalGlue(stack))
			return false;
		if (SurgicalGlueController.pendingGlue != null)
			return true;
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && minecraft.level != null
			&& SurgicalSelectionResolver.findNearestCubeHit(minecraft.player, minecraft.level, SurgicalSelectionResolver.playerRay(minecraft.player)) != null;
	}

	/** Builds the fixed HUD prompt while aiming at a model cube or placing a compatible captured subject. */
	@Nullable
	public static InteractionPrompt interactionPrompt() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.screen != null || SurgicalVisualCommitCoordinator.pendingVisualCommit != null)
			return null;
		InteractionHand hand = SurgicalInteractionPresentation.interactionPromptHand(player);
		if (hand == null)
			return null;
		ItemStack stack = player.getItemInHand(hand);
		SurgicalSelectionResolver.Ray ray = SurgicalSelectionResolver.playerRay(player);
		boolean editingWrench = SurgicalWrenchController.wrenchEditor != null && SurgicalWrenchController.wrenchEditor.hand == hand && SurgicalKitItem.isWrench(stack);
		// The selection pass already made this exact pick for the same ray and geometry generation.
		SurgicalSelectionResolver.CubeHit cubeHit = editingWrench ? null
			: SurgicalSelectionResolver.lastSelectionPlainPick && SurgicalSelectionResolver.lastSelectionTick == level.getGameTime()
				&& SurgicalSelectionResolver.lastSelectionGeneration == SurgicalGeometryStore.geometryGeneration && ray.equals(SurgicalSelectionResolver.lastSelectionRay)
				? SurgicalSelectionResolver.lastSelectionCubeHit : SurgicalSelectionResolver.findNearestCubeHit(player, level, ray);
		boolean hitsCube = editingWrench || cubeHit != null;
		if (!hitsCube && SurgicalGlueController.glueEditor != null && SurgicalGlueController.glueEditor.hand == hand)
			hitsCube = SurgicalGluePreviewPlanner.findNearestGluePreviewHit(player, level, SurgicalGlueController.glueEditor) != null;
		// A reserved column exposes no cube to hit, but a shovel can still act on it.
		if (!hitsCube && SurgicalKitItem.isShovel(stack))
			hitsCube = SurgicalSelectionResolver.findReservedHit(player, level, ray) != null;
		boolean hitsShovelTile = SurgicalKitItem.isShovel(stack)
			&& SurgicalPlacementController.targetedTableTile(minecraft, level) != null;
		if (!hitsCube && !hitsShovelTile) {
			hand = SurgicalInteractionPresentation.capturedSubjectPlacementPromptHand(player, level);
			if (hand == null)
				return null;
			stack = player.getItemInHand(hand);
		}

		List<Component> tooltip = new ArrayList<>();
		SurgicalKitItem.Tool selectedTool = SurgicalKitItem.selectedTool(stack);
		Component title = selectedTool == null ? stack.getHoverName() : selectedTool.displayName();
		CreateLang.builder()
			.add(title)
			.forGoggles(tooltip);
		if (SurgicalSelectionResolver.isSurgicalGlue(stack)) {
			boolean selectingFirst = SurgicalGlueController.pendingGlue == null && SurgicalGlueController.glueEditor == null;
			boolean selectingSecond = SurgicalGlueController.pendingGlue != null && SurgicalGlueController.glueEditor == null;
			boolean editing = SurgicalGlueController.glueEditor != null;
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_first_glue", selectingFirst);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_second_glue", selectingSecond);
			if (SurgicalSelectionResolver.isSmartGlue(stack)) {
				SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.translatable(
					"create_biotech.gui.surgical_table.control.scroll"),
					"create_biotech.gui.surgical_table.action.move_glue", editing);
				SurgicalInteractionPresentation.addInteractionControl(tooltip, SurgicalInteractionPresentation.combinedControl(Component.translatable(
					"create_biotech.gui.surgical_table.control.ctrl"),
					Component.translatable("create_biotech.gui.surgical_table.control.scroll")),
					"create_biotech.gui.surgical_table.action.rotate_glue", editing);
				SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
					"create_biotech.gui.surgical_table.action.confirm_glue", editing);
			}
			SurgicalInteractionPresentation.addCancelControl(tooltip, SurgicalGlueController.pendingGlue != null);
		} else if (SurgicalSelectionResolver.isSymmetryWand(stack)) {
			boolean selectingFirst = SurgicalSymmetryController.pendingSymmetry == null;
			boolean selectingReference = SurgicalSymmetryController.pendingSymmetry != null && SurgicalSymmetryController.pendingSymmetryReference == null;
			boolean selectingPlane = SurgicalSymmetryController.pendingSymmetryReference != null;
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_first_symmetry", selectingFirst);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_combination_symmetry", selectingReference);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_plane_symmetry", selectingPlane);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, SurgicalInteractionPresentation.combinedControl(Component.translatable(
				"create_biotech.gui.surgical_table.control.ctrl"), Component.keybind("key.use")),
				"create_biotech.gui.surgical_table.action.select_cube_symmetry", selectingPlane);
			SurgicalInteractionPresentation.addCancelControl(tooltip, SurgicalSymmetryController.pendingSymmetry != null);
		} else if (SurgicalSelectionResolver.heldLimbType(stack) != null) {
			boolean continuingLimb = SurgicalJointController.pendingLimb != null && SurgicalJointController.pendingLimb.hand() == hand
				&& SurgicalJointController.pendingLimb.type() == SurgicalSelectionResolver.heldLimbType(stack);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_moving_limb", !continuingLimb);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_pivot_limb", continuingLimb);
			SurgicalInteractionPresentation.addCancelControl(tooltip, SurgicalJointController.pendingLimb != null);
		} else if (SurgicalKitItem.isSlimeBall(stack)) {
			boolean selectingFirst = SurgicalJointController.pendingSlimeSeam == null;
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_first_slime_seam", selectingFirst);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_second_slime_seam", !selectingFirst);
			SurgicalInteractionPresentation.addCancelControl(tooltip, SurgicalJointController.pendingSlimeSeam != null);
		} else if (SurgicalKitItem.isShovel(stack)) {
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.shovel_component", hitsCube);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, SurgicalInteractionPresentation.combinedControl(Component.translatable(
				"create_biotech.gui.surgical_table.control.ctrl"), Component.keybind("key.use")),
				"create_biotech.gui.surgical_table.action.shovel_tile", hitsShovelTile);
		} else if (SurgicalKitItem.isShears(stack)) {
			boolean placingCut = SurgicalCutController.pendingCut != null || SurgicalCutController.pendingGlueCut != null;
			boolean batchCut = Screen.hasControlDown();
			SurgicalSelectionResolver.Selection cutSelection = batchCut ? SurgicalSelectionResolver.componentSelection : SurgicalSelectionResolver.seamSelection;
			boolean selectingCut = !placingCut && SurgicalSelectionResolver.jointRemovalSelection == null && cutSelection != null;
			boolean cuttingConnection = selectingCut && !cutSelection.combination();
			boolean cuttingCombination = selectingCut && cutSelection.combination();
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.remove_joint", SurgicalSelectionResolver.jointRemovalSelection != null);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.cut", cuttingConnection && !batchCut);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.break_combination", cuttingCombination && !batchCut);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, SurgicalInteractionPresentation.combinedControl(Component.translatable(
				"create_biotech.gui.surgical_table.control.ctrl"),
				Component.keybind("key.use")), "create_biotech.gui.surgical_table.action.batch_cut",
				cuttingConnection && batchCut);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, SurgicalInteractionPresentation.combinedControl(Component.translatable(
				"create_biotech.gui.surgical_table.control.ctrl"),
				Component.keybind("key.use")), "create_biotech.gui.surgical_table.action.detach_combination",
				cuttingCombination && batchCut);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.confirm_cut", placingCut);
			SurgicalInteractionPresentation.addCancelControl(tooltip, placingCut);
		} else if (SurgicalKitItem.isHoneyBottle(stack)) {
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.combine");
		} else if (SurgicalKitItem.isCubeCopier(stack)) {
			boolean copying = SurgicalKitItem.isEmptyCubeCopier(stack);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				copying ? "create_biotech.gui.surgical_table.action.copy_cube"
					: "create_biotech.gui.surgical_table.action.place_copied_cube",
				!copying || hitsCube);
		} else if (SurgicalKitItem.isEmptyTemporaryBox(stack)) {
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.move_subject");
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.translatable("create_biotech.gui.surgical_table.control.ctrl"),
				"create_biotech.gui.surgical_table.action.preview_ground");
		} else if (SurgicalSelectionResolver.isEmptyBox(stack)) {
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.pack");
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.translatable("create_biotech.gui.surgical_table.control.ctrl"),
				"create_biotech.gui.surgical_table.action.preview_ground");
			if (CapturedEntityBoxHelper.isEmptyLargeBox(stack))
				SurgicalPackedPreviewService.appendPackedProperties(tooltip, level, cubeHit);
		} else if (SurgicalKitItem.isWrench(stack)) {
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.select_single_rotation_cube", SurgicalWrenchController.wrenchEditor == null);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, SurgicalInteractionPresentation.combinedControl(Component.translatable(
				"create_biotech.gui.surgical_table.control.ctrl"), Component.keybind("key.use")),
				"create_biotech.gui.surgical_table.action.select_rotation_cube", SurgicalWrenchController.wrenchEditor == null);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.translatable("create_biotech.gui.surgical_table.control.scroll"),
				SurgicalWrenchController.wrenchEditor != null && SurgicalWrenchController.wrenchEditor.single ? "create_biotech.gui.surgical_table.action.rotate_single_cube"
					: "create_biotech.gui.surgical_table.action.rotate_cube", SurgicalWrenchController.wrenchEditor != null);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, SurgicalInteractionPresentation.combinedControl(Component.translatable(
				"create_biotech.gui.surgical_table.control.ctrl"), Component.translatable(
				"create_biotech.gui.surgical_table.control.scroll")),
				"create_biotech.gui.surgical_table.action.rotate_cube_quarter", SurgicalWrenchController.wrenchEditor != null);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.confirm_rotation", SurgicalWrenchController.wrenchEditor != null && !SurgicalWrenchController.wrenchEditor.committing);
			SurgicalInteractionPresentation.addInteractionControl(tooltip, SurgicalInteractionPresentation.combinedControl(Component.translatable(
				"create_biotech.gui.surgical_table.control.shift"), Component.keybind("key.use")),
				"create_biotech.gui.surgical_table.action.align_cube");
			SurgicalInteractionPresentation.addCancelControl(tooltip, SurgicalWrenchController.wrenchEditor != null);
		} else if (CapturedEntityBoxHelper.hasCapturedEntity(stack)) {
			SurgicalInteractionPresentation.addInteractionControl(tooltip, Component.keybind("key.use"),
				"create_biotech.gui.surgical_table.action.place_subject");
			SurgicalInteractionPresentation.addInteractionControl(tooltip, SurgicalInteractionPresentation.combinedControl(Component.translatable(
				"create_biotech.gui.surgical_table.control.ctrl"), Component.keybind("key.use")),
				"create_biotech.gui.surgical_table.action.place_ground_down");
			SurgicalInteractionPresentation.addInteractionControl(tooltip, SurgicalInteractionPresentation.combinedControl(Component.translatable(
				"create_biotech.gui.surgical_table.control.ctrl"), Component.translatable(
				"create_biotech.gui.surgical_table.control.scroll")),
				"create_biotech.gui.surgical_table.action.rotate_ground");
			CapturedEntityBoxStatsTooltip.append(stack, level, tooltip);
		} else {
			return null;
		}
		return new InteractionPrompt(stack, tooltip);
	}

	private static void consumeInteraction(InputEvent.InteractionKeyMappingTriggered event, InteractionHand hand) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null)
			minecraft.player.swing(hand);
		event.setSwingHand(false);
		event.setCanceled(true);
	}

	static void sendInteraction(SurgicalSelectionResolver.Selection selected, InteractionHand hand,
		SurgicalTableInteractionPacket.Action action, SurgicalTableLayout.Proposal proposal) {
		sendInteraction(selected, hand, action, proposal, 0.0d, 0.0d, null, null,
			Double.NaN, null, null);
	}

	static void sendInteraction(SurgicalSelectionResolver.Selection selected, InteractionHand hand,
		SurgicalTableInteractionPacket.Action action, SurgicalTableLayout.Proposal proposal,
		@Nullable SurgicalAssembly.BodyBounds bodyBounds,
		@Nullable SurgicalAssembly.HitboxGeometry hitboxGeometry,
		double bodyVolume,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry,
		@Nullable SurgicalPackingAlignment packingAlignment) {
		sendInteraction(selected, hand, action, proposal, 0.0d, 0.0d, bodyBounds,
			hitboxGeometry, bodyVolume, attackGeometry, packingAlignment);
	}

	static void sendInteraction(SurgicalSelectionResolver.Selection selected, InteractionHand hand,
		SurgicalTableInteractionPacket.Action action, SurgicalTableLayout.Proposal proposal,
		double originOffsetX, double originOffsetZ) {
		sendInteraction(selected, hand, action, proposal, originOffsetX, originOffsetZ, null, null,
			Double.NaN, null, null);
	}

	private static void sendInteraction(SurgicalSelectionResolver.Selection selected, InteractionHand hand,
		SurgicalTableInteractionPacket.Action action, SurgicalTableLayout.Proposal proposal,
		double originOffsetX, double originOffsetZ, @Nullable SurgicalAssembly.BodyBounds bodyBounds,
		@Nullable SurgicalAssembly.HitboxGeometry hitboxGeometry,
		double bodyVolume,
		@Nullable SurgicalAssembly.AttackGeometry attackGeometry,
		@Nullable SurgicalPackingAlignment packingAlignment) {
		CBPackets.sendToServer(new SurgicalTableInteractionPacket(selected.tablePos(), hand, action,
			selected.subjectId(), selected.targetId(), selected.observedCubeCount(), selected.seams(),
			originOffsetX, originOffsetZ, proposal, bodyBounds, bodyVolume, hitboxGeometry, attackGeometry, packingAlignment));
	}

	static void showFailure(@Nullable LocalPlayer player) {
		SurgicalTableFailure.OPERATION_FAILED.display(player);
	}

	@SubscribeEvent
	public static void hideVanillaTableOutline(RenderHighlightEvent.Block event) {
		BlockPos target = event.getTarget().getBlockPos();
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && (SurgicalSelectionResolver.belongsToSelectionPlane(level, target, SurgicalSelectionResolver.seamSelection)
			|| SurgicalSelectionResolver.belongsToSelectionPlane(level, target, SurgicalSelectionResolver.cubeSelection)
			|| SurgicalSelectionResolver.belongsToSelectionPlane(level, target, SurgicalSelectionResolver.componentSelection)
			|| SurgicalSelectionResolver.belongsToSelectionPlane(level, target, SurgicalSelectionResolver.symmetrySelection)
			|| SurgicalSelectionResolver.belongsToSelectionPlane(level, target, SurgicalSelectionResolver.limbSelection)
			|| SurgicalSelectionResolver.belongsToSelectionPlane(level, target,
				SurgicalSelectionResolver.jointRemovalSelection == null ? null : SurgicalSelectionResolver.jointRemovalSelection.selection())
			|| target.equals(SurgicalPlacementController.shovelTileOutlinePos)))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (!event.getLevel().isClientSide())
			return;
		clear();
		SurgicalSourceModelRenderer.clear();
	}

	public record InteractionPrompt(ItemStack icon, List<Component> tooltip) {
		public InteractionPrompt {
			icon = icon.copy();
			tooltip = List.copyOf(tooltip);
		}
	}
}
