package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombination;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueContact;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalGlueJoint;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSlimeDrops;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableFailure;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbJoint;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableShovelPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalVolumeSampler;
import com.nobodiiiii.createbiotech.network.CBPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalSelectionResolver implementation; public entry points remain on SurgicalTableClientHandler. */
final class SurgicalSelectionResolver {
	private SurgicalSelectionResolver() {}

	static long lastSelectionTick = Long.MIN_VALUE;
	static long lastSelectionGeneration = Long.MIN_VALUE;
	static int lastSelectionMode = Integer.MIN_VALUE;
	@Nullable
	static Ray lastSelectionRay;
	/** The plain cube pick of the last full selection update, republished for the HUD prompt. */
	@Nullable
	static CubeHit lastSelectionCubeHit;
	static boolean lastSelectionPlainPick;
	@Nullable
	static SurgicalGlueController.PendingGlue lastSelectionPendingGlue;
	@Nullable
	static SurgicalGlueController.PendingGlue lastSelectionPendingSymmetry;
	@Nullable
	static SurgicalSymmetryController.SymmetryReference lastSelectionPendingSymmetryReference;
	static SurgicalJointController.PendingLimb lastSelectionPendingLimb;
	@Nullable
	static SurgicalJointController.PendingSlimeSeam lastSelectionPendingSlimeSeam;
	@Nullable
	static Selection seamSelection;
	@Nullable
	static Selection cubeSelection;
	@Nullable
	static Selection componentSelection;
	/** Persistent id of the reserved column a held shovel is aimed at, if any. */
	@Nullable
	static UUID reservedSelection;
	@Nullable
	static Selection symmetrySelection;
	static Selection limbSelection;
	@Nullable
	static Selection slimeSeamSelection;
	@Nullable
	static LimbJointSelection jointRemovalSelection;
	@Nullable
	static CubeSelectionCache connectedSelectionCache;
	/** The limb-joint variant of the same selection; one pick can ask for both. */
	@Nullable
	static CubeSelectionCache connectedLimbSelectionCache;
	@Nullable
	static CubeSelectionCache directSelectionCache;

	/**
	 * Nearest reserved column under the ray.
	 *
	 * <p>A reserved subject exposes no cube geometry, so its marker box is the only thing that can be
	 * targeted - and the shovel is the only tool whose meaning survives without cube ids, because it
	 * is the one that addresses a whole body rather than a numbered part of one.</p>
	 */
	@Nullable
	static UUID findReservedHit(LocalPlayer player, ClientLevel level, Ray ray) {
		UUID best = null;
		BlockPos bestTable = null;
		Vec3 bestLocation = null;
		double bestDistance = Double.MAX_VALUE;
		for (Map.Entry<UUID, SurgicalGeometryStore.ReservedSubject> entry : SurgicalGeometryStore.RESERVED_SUBJECTS.entrySet()) {
			SurgicalGeometryStore.ReservedSubject reserved = entry.getValue();
			if (!SurgicalPickingGeometry.rayIntersectsBounds(ray, reserved.bounds(), 1.0e-6d))
				continue;
			// clip() misses when the ray starts inside the box; the entry point is then the eye itself.
			Vec3 location = reserved.bounds().clip(ray.start, ray.end).orElse(ray.start);
			double distance = ray.start.distanceToSqr(location);
			if (distance >= bestDistance)
				continue;
			best = entry.getKey();
			bestTable = reserved.tablePos();
			bestLocation = location;
			bestDistance = distance;
		}
		return best == null || SurgicalPickingGeometry.isOccluded(level, player, ray.start, bestLocation, bestTable) ? null : best;
	}

	/**
	 * Removes a subject whose stored topology this client cannot match.
	 *
	 * <p>The server already treats a topology it cannot confirm as a whole-subject removal, which is
	 * exactly the outcome wanted here, so the observed count is sent honestly and with no seams: it
	 * contradicts the stored one by definition, which is what made the subject reserved in the first
	 * place. The drop lands on the table surface at the centre of the reserved footprints, which the
	 * server's work area always contains.</p>
	 */
	static boolean shovelReservedSubject(ClientLevel level, InteractionHand hand) {
		SurgicalGeometryStore.ReservedSubject reserved = reservedSelection == null ? null
			: SurgicalGeometryStore.RESERVED_SUBJECTS.get(reservedSelection);
		if (reserved == null
			|| !(level.getBlockEntity(reserved.tablePos()) instanceof SurgicalTableBlockEntity table))
			return false;
		SurgicalSubject subject = table.getSubject(reserved.subjectId());
		if (subject == null || subject.cubeCount() != reserved.storedCubeCount())
			return false;
		Vec3 center = reserved.bounds().getCenter();
		CBPackets.sendToServer(new SurgicalTableShovelPacket(reserved.tablePos(), hand,
			reserved.subjectId(), 0, reportableCubeCount(reserved), List.of(),
			SurgicalGeometryStore.reservedVolume(subject, reserved),
			new Vec3(center.x, reserved.bounds().minY, center.z)));
		clearSelections();
		return true;
	}

	/**
	 * A cube count the server will accept and cannot confirm. The observed count is reported as
	 * measured; it only needs clamping into the range the packet validates, and nudging off the
	 * stored count in the one case where clamping could land on it - a replacement model that
	 * recovers more than {@link SurgicalAssembly#MAX_CUBES} cuboids.
	 */
	static int reportableCubeCount(SurgicalGeometryStore.ReservedSubject reserved) {
		int reported = Math.max(1, Math.min(SurgicalAssembly.MAX_CUBES, reserved.observedCubeCount()));
		return reported == reserved.storedCubeCount() ? Math.max(1, reported - 1) : reported;
	}

	/** Pulls a box inward on every side, leaving axes too thin to give it up collapsed, not inverted. */
	static AABB insetBox(AABB box, double inset) {
		return SurgicalPickingGeometry.insetBox(box, inset);
	}

	static double axisInset(double size, double inset) {
		return SurgicalPickingGeometry.axisInset(size, inset);
	}

	static void updateSelections() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.screen != null) {
			SurgicalCutController.abortPendingCut();
			clearSelections();
			return;
		}
		if (SurgicalWrenchController.refreshWrenchEditor(player, level))
			return;
		if (SurgicalVisualCommitCoordinator.retainsGluePreview()) {
			SurgicalGlueController.hoveredGluePoint = null;
			SurgicalInteractionPresentation.GLUE_POINT_OUTLINE.clear();
			SurgicalInteractionPresentation.clearSeamHighlight();
			return;
		}
		if (SurgicalCutController.pendingCut != null) {
			SurgicalGlueController.hoveredGluePoint = null;
			SurgicalInteractionPresentation.GLUE_POINT_OUTLINE.clear();
			SurgicalCutController.updatePendingCut(player, level);
			return;
		}
		if (SurgicalCutController.pendingGlueCut != null) {
			SurgicalGlueController.hoveredGluePoint = null;
			SurgicalInteractionPresentation.GLUE_POINT_OUTLINE.clear();
			SurgicalCutController.updatePendingGlueCut(player, level);
			return;
		}
		if (SurgicalGlueController.glueEditor != null) {
			SurgicalGlueController.hoveredGluePoint = null;
			SurgicalInteractionPresentation.GLUE_POINT_OUTLINE.clear();
			seamSelection = null;
			cubeSelection = null;
			componentSelection = null;
			jointRemovalSelection = null;
			SurgicalInteractionPresentation.clearSeamHighlight();
			SurgicalGluePreviewPlanner.refreshGlueEditGuide(player, level, SurgicalGlueController.glueEditor);
			return;
		}

		boolean holdingShears = SurgicalKitItem.isShears(player.getMainHandItem())
			|| SurgicalKitItem.isShears(player.getOffhandItem());
		boolean holdingEmptyBox = isEmptyBox(player.getMainHandItem()) || isEmptyBox(player.getOffhandItem());
		boolean holdingEmptyLargeBox = isEmptyLargeBox(player.getMainHandItem())
			|| isEmptyLargeBox(player.getOffhandItem());
		boolean holdingGlue = isSurgicalGlue(player.getMainHandItem()) || isSurgicalGlue(player.getOffhandItem());
		boolean holdingSymmetry = isSymmetryWand(player.getMainHandItem())
			|| isSymmetryWand(player.getOffhandItem());
		boolean holdingHoney = SurgicalKitItem.isHoneyBottle(player.getMainHandItem())
			|| SurgicalKitItem.isHoneyBottle(player.getOffhandItem());
		boolean holdingSlimeBall = SurgicalKitItem.isSlimeBall(player.getMainHandItem())
			|| SurgicalKitItem.isSlimeBall(player.getOffhandItem());
		boolean holdingJoint = heldLimbType(player.getMainHandItem()) != null
			|| heldLimbType(player.getOffhandItem()) != null;
		boolean holdingWrench = SurgicalKitItem.isWrench(player.getMainHandItem())
			|| SurgicalKitItem.isWrench(player.getOffhandItem());
		boolean holdingEmptyCopier = SurgicalKitItem.isEmptyCubeCopier(player.getMainHandItem())
			|| SurgicalKitItem.isEmptyCubeCopier(player.getOffhandItem());
		boolean holdingShovel = SurgicalKitItem.isShovel(player.getMainHandItem())
			|| SurgicalKitItem.isShovel(player.getOffhandItem());
		if (!holdingJoint && SurgicalJointController.pendingLimb != null)
			SurgicalJointController.clearPendingLimb();
		if (!holdingSlimeBall && SurgicalJointController.pendingSlimeSeam != null)
			SurgicalJointController.clearPendingSlimeSeam();
		boolean highlightingDirectConnections = holdingShears && Screen.hasControlDown();
		boolean shovelingTile = holdingShovel && Screen.hasControlDown();
		if (!holdingShears && !holdingEmptyBox && !holdingEmptyLargeBox && !holdingGlue && !holdingSymmetry
			&& !holdingHoney && !holdingSlimeBall
			&& !holdingJoint && !holdingWrench && !holdingShovel && !holdingEmptyCopier) {
			SurgicalGlueController.hoveredGluePoint = null;
			SurgicalInteractionPresentation.GLUE_POINT_OUTLINE.clear();
			SurgicalGlueController.gluePreview = null;
			seamSelection = null;
			cubeSelection = null;
			componentSelection = null;
			reservedSelection = null;
			symmetrySelection = null;
			limbSelection = null;
			slimeSeamSelection = null;
			jointRemovalSelection = null;
			lastSelectionMode = Integer.MIN_VALUE;
			lastSelectionRay = null;
			SurgicalInteractionPresentation.clearSeamHighlight();
			return;
		}
		Ray ray = SurgicalPickingGeometry.playerRay(player);
		int selectionMode = (holdingShears ? 1 : 0) | (holdingEmptyBox ? 2 : 0)
			| (holdingEmptyLargeBox ? 4 : 0) | (holdingGlue ? 8 : 0)
			| (highlightingDirectConnections ? 16 : 0) | (holdingHoney ? 32 : 0)
			| (holdingJoint ? 64 : 0) | (holdingWrench ? 128 : 0)
			| (holdingSymmetry ? 256 : 0)
			| (SurgicalSymmetryController.pendingSymmetryReference != null && Screen.hasControlDown() ? 512 : 0)
			| (holdingSlimeBall ? 1024 : 0) | (holdingShovel ? 2048 : 0)
			| (shovelingTile ? 4096 : 0) | (holdingEmptyCopier ? 8192 : 0);
		if (lastSelectionTick == level.getGameTime() && lastSelectionGeneration == SurgicalGeometryStore.geometryGeneration
			&& lastSelectionMode == selectionMode && ray.equals(lastSelectionRay)
			&& SurgicalGlueController.pendingGlue == lastSelectionPendingGlue && SurgicalSymmetryController.pendingSymmetry == lastSelectionPendingSymmetry
			&& SurgicalSymmetryController.pendingSymmetryReference == lastSelectionPendingSymmetryReference
			&& SurgicalJointController.pendingLimb == lastSelectionPendingLimb
			&& SurgicalJointController.pendingSlimeSeam == lastSelectionPendingSlimeSeam) {
			refreshCurrentSelectionHighlight();
			return;
		}
		lastSelectionTick = level.getGameTime();
		lastSelectionGeneration = SurgicalGeometryStore.geometryGeneration;
		lastSelectionMode = selectionMode;
		lastSelectionRay = ray;
		lastSelectionPendingGlue = SurgicalGlueController.pendingGlue;
		lastSelectionPendingSymmetry = SurgicalSymmetryController.pendingSymmetry;
		lastSelectionPendingSymmetryReference = SurgicalSymmetryController.pendingSymmetryReference;
		lastSelectionPendingLimb = SurgicalJointController.pendingLimb;
		lastSelectionPendingSlimeSeam = SurgicalJointController.pendingSlimeSeam;
		InteractionHand promptHand = SurgicalInteractionPresentation.interactionPromptHand(player);
		boolean selectingSlimeSeam = promptHand != null
			&& SurgicalKitItem.isSlimeBall(player.getItemInHand(promptHand));
		boolean picking = holdingShears || holdingEmptyBox || holdingGlue || holdingSymmetry || holdingHoney
			|| holdingJoint || holdingWrench || holdingSlimeBall || holdingEmptyCopier
			|| holdingShovel && !shovelingTile;
		CubeHit cubeHit = !picking ? null
			: selectingSlimeSeam
				? findNearestSlimeSeamHit(player, level, ray, SurgicalJointController.pendingSlimeSeam)
				: findNearestCubeHit(player, level, ray);
		lastSelectionCubeHit = cubeHit;
		lastSelectionPlainPick = picking && !selectingSlimeSeam;
		CubeHit glueHit = holdingGlue || holdingSymmetry && SurgicalSymmetryController.pendingSymmetry == null ? snapGlueHit(cubeHit) : null;
		SurgicalGlueController.hoveredGluePoint = glueHit == null ? null : glueHit.gluePoint;
		symmetrySelection = null;
		SurgicalSymmetryController.symmetryPlaneSelection = null;
		if (SurgicalGlueController.pendingGlue != null && glueHit != null) {
			int revision = level.getBlockEntity(glueHit.tablePos) instanceof SurgicalTableBlockEntity table
				? table.clientDataRevision() : Integer.MIN_VALUE;
			if (SurgicalGlueController.gluePreview == null || !SurgicalGlueController.gluePreview.matches(SurgicalGlueController.pendingGlue, glueHit, revision))
				SurgicalGlueController.gluePreview = SurgicalGluePreviewPlanner.planGluePreview(level, SurgicalGlueController.pendingGlue, glueHit).preview();
		} else if (SurgicalSymmetryController.pendingSymmetryReference != null && SurgicalSymmetryController.pendingSymmetry != null) {
			boolean singleCube = SurgicalSymmetryController.pendingSymmetryReference.singleCube(Screen.hasControlDown());
			SurgicalSymmetryController.symmetryPlaneSelection = SurgicalSymmetryController.findSymmetryPlane(ray,
				SurgicalSymmetryController.pendingSymmetryReference.mirrorBounds(singleCube),
				SurgicalSymmetryController.pendingSymmetryReference.planes(singleCube));
			SurgicalSymmetryController.SymmetryTarget target = SurgicalSymmetryController.symmetryPlaneSelection == null ? null
				: SurgicalSymmetryController.symmetryTarget(SurgicalSymmetryController.pendingSymmetryReference, SurgicalSymmetryController.symmetryPlaneSelection, singleCube);
			symmetrySelection = SurgicalSymmetryController.pendingSymmetryReference.selection(singleCube);
			SurgicalGlueController.gluePreview = target == null ? null : SurgicalGluePreviewPlanner.planSymmetryPreview(level, SurgicalSymmetryController.pendingSymmetry, target);
		} else if (SurgicalSymmetryController.pendingSymmetry != null && cubeHit != null) {
			SurgicalSymmetryController.SymmetryReference reference = SurgicalSymmetryController.findSymmetryReference(cubeHit);
			symmetrySelection = reference == null ? null : reference.selection(false);
			SurgicalGlueController.gluePreview = null;
		} else {
			SurgicalGlueController.gluePreview = null;
		}
		seamSelection = holdingShears && !highlightingDirectConnections
			? findSeamSelection(player, level, ray, cubeHit) : null;
		cubeSelection = holdingWrench ? findCubeSelection(cubeHit)
			: holdingEmptyBox || holdingEmptyCopier ? findConnectedComponentSelection(cubeHit, true) : null;
		limbSelection = holdingJoint ? findLimbTargetSelection(cubeHit) : null;
		slimeSeamSelection = holdingSlimeBall ? findSlimeSeamTargetSelection(level, cubeHit) : null;
		jointRemovalSelection = holdingShears ? findLimbJointSelection(player, level, ray, cubeHit) : null;
		componentSelection = holdingShovel && !shovelingTile
			? findConnectedComponentSelection(cubeHit)
			: holdingHoney
			? findConnectedComponentSelection(cubeHit)
			: holdingGlue
			? (SurgicalGlueController.pendingGlue == null ? findConnectedComponentSelection(glueHit) : findCubeSelection(glueHit))
			: holdingSymmetry && SurgicalSymmetryController.pendingSymmetry == null
			? findConnectedComponentSelection(glueHit)
			: highlightingDirectConnections
			? findDirectConnectionCutSelection(cubeHit)
			: !holdingShears && holdingEmptyLargeBox ? findConnectedComponentSelection(cubeHit) : null;
		// Only reached when nothing pickable is under the ray, so an ordinary component always wins
		// over a reserved column standing in the same place.
		reservedSelection = holdingShovel && !shovelingTile && componentSelection == null
			? findReservedHit(player, level, ray) : null;
		refreshCurrentSelectionHighlight();
	}

	static void refreshCurrentSelectionHighlight() {
		SurgicalInteractionPresentation.refreshGluePointHighlight();
		if (SurgicalInteractionPresentation.highlightGlueTargetCube())
			return;
		if (SurgicalJointController.pendingSlimeSeam != null) {
			List<SurgicalClientTopology.Edge> edges = new ArrayList<>(
				SurgicalJointController.pendingSlimeSeam.selection().cubeEdges);
			if (slimeSeamSelection != null)
				edges.addAll(slimeSeamSelection.cubeEdges);
			SurgicalInteractionPresentation.SEAM_OUTLINE.clear();
			SurgicalInteractionPresentation.COMBINATION_OUTLINE.clear();
			SurgicalInteractionPresentation.GLUE_JOINT_OUTLINE.clear();
			SurgicalInteractionPresentation.LIMB_JOINT_OUTLINE.clear();
			SurgicalInteractionPresentation.clearSymmetryPlaneHighlight();
			SurgicalInteractionPresentation.CUBE_OUTLINE.show(edges, SurgicalTableClientHandler.CUBE_HIGHLIGHT_COLOR);
			return;
		}
		if (SurgicalJointController.pendingLimb != null) {
			List<SurgicalClientTopology.Edge> edges = new ArrayList<>(SurgicalJointController.pendingLimb.selection().cubeEdges);
			if (limbSelection != null)
				edges.addAll(limbSelection.cubeEdges);
			SurgicalInteractionPresentation.SEAM_OUTLINE.clear();
			SurgicalInteractionPresentation.COMBINATION_OUTLINE.clear();
			SurgicalInteractionPresentation.GLUE_JOINT_OUTLINE.clear();
			SurgicalInteractionPresentation.LIMB_JOINT_OUTLINE.clear();
			SurgicalInteractionPresentation.clearSymmetryPlaneHighlight();
			SurgicalInteractionPresentation.CUBE_OUTLINE.show(edges, SurgicalTableClientHandler.CUBE_HIGHLIGHT_COLOR);
			return;
		}
		boolean highlightingCapture = (lastSelectionMode & (2 | 8192)) != 0;
		if (symmetrySelection != null)
			SurgicalInteractionPresentation.highlightSelection(symmetrySelection);
		else if (jointRemovalSelection != null)
			SurgicalInteractionPresentation.highlightSelection(jointRemovalSelection.selection, true);
		else if (limbSelection != null)
			SurgicalInteractionPresentation.highlightSelection(limbSelection);
		else if (slimeSeamSelection != null)
			SurgicalInteractionPresentation.highlightSelection(slimeSeamSelection);
		else if (highlightingCapture && cubeSelection != null)
			SurgicalInteractionPresentation.highlightSelection(cubeSelection, true);
		else if (componentSelection != null)
			SurgicalInteractionPresentation.highlightSelection(componentSelection, highlightingCapture);
		else if (seamSelection != null)
			SurgicalInteractionPresentation.highlightSelection(seamSelection);
		else if (cubeSelection != null)
			SurgicalInteractionPresentation.highlightSelection(cubeSelection, highlightingCapture);
		else
			SurgicalInteractionPresentation.clearSeamHighlight();
		SurgicalInteractionPresentation.refreshSymmetryPlaneHighlight();
	}

	static Vec3 tableSurfaceTarget(Ray ray, double surfaceY) {
		return SurgicalPickingGeometry.tableSurfaceTarget(ray, surfaceY);
	}

	static boolean sameHorizontalTarget(Vec3 target, double previousX, double previousZ) {
		return SurgicalPickingGeometry.sameHorizontalTarget(target, previousX, previousZ);
	}

	static SurgicalTablePlane.Plane clientPlane(ClientLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof SurgicalTableBlockEntity table) {
			SurgicalTablePlane.Plane cached = table.getClientPlane();
			if (cached != null)
				return cached;
		}
		return SurgicalTablePlane.scan(level, pos);
	}

	static SurgicalModelRenderContext.CubeGeometry boundsGeometry(AABB bounds,
		Vec3 worldOffset) {
		return SurgicalPickingGeometry.boundsGeometry(bounds, worldOffset);
	}

	static List<SurgicalModelRenderContext.CubeGeometry> translateCubes(
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Vec3 offset) {
		return SurgicalPickingGeometry.translateCubes(cubes, offset);
	}

	/**
	 * Expands a logical model envelope from captured component corners. Render batches are
	 * deliberately not consulted here: translucent-shell shrink, surface overlays and other
	 * presentation-only geometry must never alter table fit or persisted occupied footprints.
	 */
	@Nullable
	static AABB logicalBounds(List<SurgicalModelRenderContext.CubeGeometry> cubes,
		Map<Integer, Vec3> offsets) {
		return SurgicalPickingGeometry.logicalBounds(cubes, offsets);
	}

	/** A failed search means no space only when its geometry and target were valid. */
	static SurgicalTableFailure failedPlacementSearch(SurgicalTablePlane.WorkArea area,
		List<SurgicalTableLayout.Footprint> moving, List<SurgicalTableLayout.Footprint> occupied, Vec3 target) {
		return !area.isEmpty() && !moving.isEmpty() && Double.isFinite(target.x) && Double.isFinite(target.z)
			&& moving.stream().allMatch(SurgicalTableLayout::validStoredFootprint)
			&& occupied.stream().allMatch(SurgicalTableLayout::validStoredFootprint)
			? SurgicalTableFailure.NO_SPACE : SurgicalTableFailure.OPERATION_FAILED;
	}

	static boolean belongsToSelectionPlane(ClientLevel level, BlockPos target,
		@Nullable Selection selection) {
		if (selection == null)
			return false;
		SurgicalTablePlane.Plane plane = clientPlane(level, target);
		return plane.workArea().containsTile(selection.tablePos);
	}

	@Nullable
	static Selection findSeamSelection(LocalPlayer player, ClientLevel level, Ray ray,
		@Nullable CubeHit cubeHit) {
		if (cubeHit != null && level.getBlockEntity(cubeHit.tablePos) instanceof SurgicalTableBlockEntity table) {
			SurgicalCombination combination = table.combinationContaining(
				cubeHit.geometry.subjectId, cubeHit.cubeId);
			if (combination != null) {
				return table.externalCombinationJoints(combination).isEmpty()
					? combinationSelection(cubeHit, table, combination)
					: findCombinationExternalGlueSelection(cubeHit, table, combination);
			}
		}
		Selection directHit = findDirectSeamSelection(player, level, ray);
		if (directHit != null)
			return directHit;
		if (cubeHit == null)
			return null;

		SurgicalGeometryStore.TableGeometry geometry = cubeHit.geometry;
		Selection best = null;
		double bestDistance = Double.MAX_VALUE;
		for (SurgicalClientTopology.Contact contact : geometry.contactsFor(cubeHit.cubeId)) {
			SurgicalAssembly.Seam seam = contact.seam();
			if (seam.first() != cubeHit.cubeId && seam.second() != cubeHit.cubeId)
				continue;
			Integer seamId = geometry.seamIds.get(seam);
			if (seamId == null || geometry.cutSeams.get(seamId)
				|| !geometry.presentCubes.get(seam.first()) || !geometry.presentCubes.get(seam.second()))
				continue;
			double distance = SurgicalPickingGeometry.pointToContactDistance(cubeHit.location, contact);
			if (distance >= bestDistance)
				continue;
			bestDistance = distance;
			best = new Selection(cubeHit.tablePos, geometry.subjectId, seamId, geometry.observedCubeCount,
				geometry.seams, contact.edges(), geometry.cubeEdges(seam));
		}
		return best != null ? best : findGlueJointSelection(cubeHit);
	}

	static Selection combinationSelection(CubeHit hit, SurgicalTableBlockEntity table,
		SurgicalCombination combination) {
		return new Selection(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams, List.of(),
			SurgicalInteractionPresentation.combinationCubeEdges(hit.tablePos, table, combination), false, true);
	}

	@Nullable
	static Selection findCombinationExternalGlueSelection(CubeHit hit,
		SurgicalTableBlockEntity table, SurgicalCombination combination) {
		Selection best = null;
		double bestDistance = Double.MAX_VALUE;
		for (SurgicalGlueJoint joint : table.externalCombinationJoints(combination)) {
			SurgicalSubject jointSubject = table.getSubjectByPersistentId(joint.first().subjectKey());
			if (jointSubject == null)
				continue;
			int jointId = jointSubject.glueJoints().indexOf(joint);
			SurgicalGeometryStore.TableGeometry jointGeometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(hit.tablePos, jointSubject.id()));
			GlueJointSelection candidate = jointId < 0 || jointGeometry == null ? null
				: glueJointSelection(hit.tablePos, table, jointSubject, jointGeometry, jointId);
			if (candidate == null) {
				jointSubject = table.getSubjectByPersistentId(joint.second().subjectKey());
				if (jointSubject == null)
					continue;
				jointId = jointSubject.glueJoints().indexOf(joint);
				jointGeometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(hit.tablePos, jointSubject.id()));
				candidate = jointId < 0 || jointGeometry == null ? null
					: glueJointSelection(hit.tablePos, table, jointSubject, jointGeometry, jointId);
			}
			if (candidate == null)
				continue;
			double distance = candidate.contact == null
				? distanceToGlueJoint(hit.location, table, hit.tablePos, joint)
				: SurgicalPickingGeometry.pointToContactDistance(hit.location, candidate.contact);
			if (distance >= bestDistance)
				continue;
			bestDistance = distance;
			Selection selected = candidate.selection;
			List<SurgicalClientTopology.Edge> routedEdges = new ArrayList<>();
			List<SurgicalClientTopology.Edge> routedCombinationEdges = new ArrayList<>(
				SurgicalInteractionPresentation.combinationCubeEdges(hit.tablePos, table, combination));
			SurgicalGlueJoint.Endpoint outside = combination.contains(joint.first().subjectKey(),
				joint.first().cubeId()) ? joint.second() : joint.first();
			SurgicalSubject outsideSubject = table.getSubjectByPersistentId(outside.subjectKey());
			SurgicalCombination outsideCombination = outsideSubject == null ? null
				: outsideSubject.combinationContaining(outside.cubeId());
			if (outsideCombination != null)
				routedCombinationEdges.addAll(SurgicalInteractionPresentation.combinationCubeEdges(
					hit.tablePos, table, outsideCombination));
			else if (outsideSubject != null) {
				SurgicalGeometryStore.TableGeometry outsideGeometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(hit.tablePos, outsideSubject.id()));
				SurgicalModelRenderContext.CubeGeometry outsideCube = outsideGeometry == null ? null
					: outsideGeometry.cubesById.get(outside.cubeId());
				if (outsideCube != null)
					routedEdges.addAll(SurgicalClientTopology.cubeEdges(outsideCube));
			}
			best = new Selection(selected.tablePos, selected.subjectId, selected.targetId,
				selected.observedCubeCount, selected.seams, selected.edges, List.of(),
				List.copyOf(routedEdges), List.copyOf(routedCombinationEdges), List.of(), true, false);
		}
		return best;
	}

	static double distanceToGlueJoint(Vec3 point, SurgicalTableBlockEntity table,
		BlockPos tablePos, SurgicalGlueJoint joint) {
		Vec3 first = endpointCubeCenter(table, tablePos, joint.first());
		Vec3 second = endpointCubeCenter(table, tablePos, joint.second());
		if (first == null || second == null)
			return Double.MAX_VALUE;
		return point.distanceTo(first.add(second).scale(0.5d));
	}

	@Nullable
	static Vec3 endpointCubeCenter(SurgicalTableBlockEntity table, BlockPos tablePos,
		SurgicalGlueJoint.Endpoint endpoint) {
		SurgicalSubject subject = table.getSubjectByPersistentId(endpoint.subjectKey());
		SurgicalGeometryStore.TableGeometry geometry = subject == null ? null
			: SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, subject.id()));
		SurgicalModelRenderContext.CubeGeometry cube = geometry == null ? null
			: geometry.cubesById.get(endpoint.cubeId());
		return cube == null ? null : SurgicalGluePreviewPlanner.cubeCenter(cube);
	}

	@Nullable
	static Selection findDirectSeamSelection(LocalPlayer player, ClientLevel level, Ray ray) {
		Selection best = null;
		Vec3 bestHit = null;
		BlockPos bestTablePos = null;
		double bestDistance = Double.MAX_VALUE;
		for (Map.Entry<SurgicalGeometryStore.SubjectKey, SurgicalGeometryStore.TableGeometry> entry : SurgicalGeometryStore.TABLES.entrySet()) {
			SurgicalGeometryStore.TableGeometry geometry = entry.getValue();
			// Same ordering as findNearestCubeHit: the free slab test decides first, so a table the
			// ray misses costs neither a block-entity fetch nor a per-seam topology check.
			if (geometry.bounds == null
				|| !SurgicalPickingGeometry.rayIntersectsBounds(ray, geometry.bounds, SurgicalTableClientHandler.MAX_SELECTION_THRESHOLD))
				continue;
			BlockPos tablePos = entry.getKey().tablePos();
			if (!(level.getBlockEntity(tablePos) instanceof SurgicalTableBlockEntity table)
				|| !table.hasSubject(entry.getKey().subjectId()))
				continue;
			SurgicalSubject subject = table.getSubject(geometry.subjectId);
			if (subject == null || !SurgicalGeometryStore.geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				continue;
			for (SurgicalClientTopology.Contact contact : geometry.contacts) {
				SurgicalAssembly.Seam seam = contact.seam();
				Integer seamId = geometry.seamIds.get(seam);
				if (seamId == null || geometry.cutSeams.get(seamId)
					|| table.isInternalCombinationSeam(geometry.subjectId, seam)
					|| !geometry.presentCubes.get(seam.first()) || !geometry.presentCubes.get(seam.second()))
					continue;
				Vec3 hit = SurgicalPickingGeometry.intersectContact(ray, contact);
				if (hit == null)
					continue;
				double distance = ray.start.distanceToSqr(hit);
				if (distance >= bestDistance)
					continue;
				bestDistance = distance;
				bestHit = hit;
				bestTablePos = tablePos;
				best = new Selection(tablePos, geometry.subjectId, seamId, geometry.observedCubeCount,
					geometry.seams, contact.edges(), geometry.cubeEdges(seam));
			}
			for (int jointId = 0; jointId < subject.glueJoints().size(); jointId++) {
				if (table.isInternalCombinationJoint(subject.glueJoints().get(jointId)))
					continue;
				GlueJointSelection glue = glueJointSelection(tablePos, table, subject, geometry, jointId);
				if (glue == null || glue.contact == null)
					continue;
				Vec3 hit = SurgicalPickingGeometry.intersectContact(ray, glue.contact);
				if (hit == null)
					continue;
				double distance = ray.start.distanceToSqr(hit);
				if (distance >= bestDistance)
					continue;
				bestDistance = distance;
				bestHit = hit;
				bestTablePos = tablePos;
				best = glue.selection;
			}
		}
		return best != null && bestHit != null && bestTablePos != null
			&& SurgicalPickingGeometry.isOccluded(level, player, ray.start, bestHit, bestTablePos) ? null : best;
	}

	@Nullable
	static Selection findGlueJointSelection(CubeHit hit) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !(level.getBlockEntity(hit.tablePos) instanceof SurgicalTableBlockEntity table))
			return null;
		SurgicalSubject subject = table.getSubject(hit.geometry.subjectId);
		if (subject == null)
			return null;
		Selection best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int jointId = 0; jointId < subject.glueJoints().size(); jointId++) {
			SurgicalGlueJoint joint = subject.glueJoints().get(jointId);
			if (!joint.touches(subject.persistentId(), hit.cubeId)
				|| table.isInternalCombinationJoint(joint))
				continue;
			GlueJointSelection candidate = glueJointSelection(hit.tablePos, table, subject,
				hit.geometry, jointId);
			if (candidate == null)
				continue;
			double distance = candidate.contact == null ? 0.0d
				: SurgicalPickingGeometry.pointToContactDistance(hit.location, candidate.contact);
			if (distance >= bestDistance)
				continue;
			bestDistance = distance;
			best = candidate.selection;
		}
		return best;
	}

	@Nullable
	static GlueJointSelection glueJointSelection(BlockPos tablePos, SurgicalTableBlockEntity table,
		SurgicalSubject subject, SurgicalGeometryStore.TableGeometry geometry, int jointId) {
		if (jointId < 0 || jointId >= subject.glueJoints().size())
			return null;
		SurgicalGlueJoint joint = subject.glueJoints().get(jointId);
		if (!joint.touches(subject.persistentId()) || table.isInternalCombinationJoint(joint))
			return null;
		SurgicalSubject firstSubject = table.getSubjectByPersistentId(joint.first().subjectKey());
		SurgicalSubject secondSubject = table.getSubjectByPersistentId(joint.second().subjectKey());
		if (firstSubject == null || secondSubject == null)
			return null;
		SurgicalGeometryStore.TableGeometry firstGeometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, firstSubject.id()));
		SurgicalGeometryStore.TableGeometry secondGeometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, secondSubject.id()));
		if (firstGeometry == null || secondGeometry == null || !firstGeometry.topologyReady()
			|| !secondGeometry.topologyReady()
			|| !firstSubject.matchesObservedTopology(firstGeometry.observedCubeCount, firstGeometry.seams)
			|| !secondSubject.matchesObservedTopology(secondGeometry.observedCubeCount, secondGeometry.seams)
			|| !firstGeometry.presentCubes.get(joint.first().cubeId())
			|| !secondGeometry.presentCubes.get(joint.second().cubeId()))
			return null;
		SurgicalModelRenderContext.CubeGeometry first = firstGeometry.cubesById.get(joint.first().cubeId());
		SurgicalModelRenderContext.CubeGeometry second = secondGeometry.cubesById.get(joint.second().cubeId());
		if (first == null || second == null)
			return null;

		SurgicalClientTopology.Contact contact = SurgicalClientTopology.contactBetween(
			SurgicalAssembly.Seam.of(0, 1), List.of(
				new SurgicalModelRenderContext.CubeGeometry(0, first.corners()),
				new SurgicalModelRenderContext.CubeGeometry(1, second.corners())));
		List<SurgicalClientTopology.Edge> cubeEdges = new ArrayList<>(24);
		cubeEdges.addAll(SurgicalClientTopology.cubeEdges(first));
		cubeEdges.addAll(SurgicalClientTopology.cubeEdges(second));
		Selection selection = new Selection(tablePos, geometry.subjectId, jointId,
			geometry.observedCubeCount, geometry.seams,
			contact == null ? List.of() : contact.edges(), List.of(), List.copyOf(cubeEdges),
			List.of(), List.of(), true, false);
		return new GlueJointSelection(selection, contact);
	}

	@Nullable
	static Vec3 intersectContact(Ray ray, SurgicalClientTopology.Contact contact) {
		return SurgicalPickingGeometry.intersectContact(ray, contact);
	}

	@Nullable
	static Selection findConnectedComponentSelection(@Nullable CubeHit hit) {
		return findConnectedComponentSelection(hit, false);
	}

	@Nullable
	static Selection findConnectedComponentSelection(@Nullable CubeHit hit,
		boolean includeLimbJoints) {
		if (hit == null)
			return null;
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !(level.getBlockEntity(hit.tablePos) instanceof SurgicalTableBlockEntity table))
			return null;
		CubeSelectionCache cached = includeLimbJoints ? connectedLimbSelectionCache : connectedSelectionCache;
		if (cached != null && cached.matches(hit, table.clientDataRevision()))
			return cached.selection;
		Map<Integer, BitSet> components = table.connectedComponents(hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams);
		if (components.isEmpty())
			return null;
		SelectionHighlightEdges edges = SurgicalInteractionPresentation.connectedSelectionEdges(hit.tablePos, table, components,
			includeLimbJoints);
		if (edges == null)
			return null;
		Selection selection = new Selection(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams, List.of(), edges.glueJointEdges,
			edges.cubeEdges, edges.combinationEdges, edges.limbJointEdges, false, false);
		CubeSelectionCache stored = new CubeSelectionCache(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
			table.clientDataRevision(), hit.geometry.renderRevision, selection);
		if (includeLimbJoints)
			connectedLimbSelectionCache = stored;
		else
			connectedSelectionCache = stored;
		return selection;
	}

	/** Measures the exact visible cuboids in the same server-authoritative connectivity group. */
	static double connectedComponentVolume(ClientLevel level, @Nullable CubeHit hit) {
		if (hit == null
			|| !(level.getBlockEntity(hit.tablePos) instanceof SurgicalTableBlockEntity table))
			return Double.NaN;
		Map<Integer, BitSet> components = table.connectedComponents(hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams);
		if (components.isEmpty())
			return Double.NaN;
		List<List<Vec3>> cuboids = new ArrayList<>();
		for (Map.Entry<Integer, BitSet> entry : components.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(hit.tablePos, entry.getKey()));
			if (subject == null || !SurgicalGeometryStore.geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				return Double.NaN;
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
				cube = entry.getValue().nextSetBit(cube + 1)) {
				SurgicalModelRenderContext.CubeGeometry cubeGeometry = geometry.cubesById.get(cube);
				if (cubeGeometry == null)
					return Double.NaN;
				cuboids.add(cubeGeometry.corners());
			}
		}
		return cuboids.isEmpty() ? Double.NaN : SurgicalVolumeSampler.unionVolume(cuboids);
	}

	/**
	 * Measures every server-addressable group intersecting one tile without using ray hits. Exact
	 * cached cuboids are preferred; topology-mismatch markers contribute their measured replacement
	 * volume proportionally, and unavailable geometry falls back to saved projected area.
	 */
	static double tileShovelVolume(ClientLevel level, BlockPos tilePos) {
		SurgicalTablePlane.Plane plane = clientPlane(level, tilePos);
		if (!plane.valid() || plane.source() == null
			|| !(level.getBlockEntity(plane.source()) instanceof SurgicalTableBlockEntity table))
			return 0.0d;

		Map<Integer, BitSet> selected = new HashMap<>();
		for (SurgicalSubject subject : table.getSubjects()) {
			BitSet present = subject.presentCubes();
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints()) {
				if (!footprintIntersectsTile(footprint, tilePos))
					continue;
				int root = footprint.componentRoot();
				if (root >= 0 && present.get(root)) {
					mergeComponents(selected, table.connectedComponents(subject.id(), root));
					continue;
				}
				// A whole-model envelope mirrors wholeSubjectGroup() on the server.
				for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
					BitSet alreadySelected = selected.get(subject.id());
					if (alreadySelected != null && alreadySelected.get(cube))
						continue;
					mergeComponents(selected, table.connectedComponents(subject.id(), cube));
				}
			}
		}

		List<List<Vec3>> measuredCuboids = new ArrayList<>();
		double fallbackVolume = 0.0d;
		boolean usedProjectionFallback = false;
		for (Map.Entry<Integer, BitSet> entry : selected.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			if (subject == null)
				continue;
			SurgicalGeometryStore.ReservedSubject reserved = SurgicalGeometryStore.RESERVED_SUBJECTS.get(subject.persistentId());
			if (reserved != null && reserved.storedCubeCount() == subject.cubeCount()) {
				int presentCount = subject.presentCubes().cardinality();
				if (presentCount > 0)
					fallbackVolume += SurgicalGeometryStore.reservedVolume(subject, reserved)
						* Math.min(1.0d, (double) entry.getValue().cardinality() / presentCount);
				continue;
			}

			SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(table.getBlockPos(), subject.id()));
			List<List<Vec3>> subjectCuboids = new ArrayList<>(entry.getValue().cardinality());
			boolean complete = SurgicalGeometryStore.geometryReadyForUse(table, subject, geometry)
				&& subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams);
			if (complete) {
				for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
					cube = entry.getValue().nextSetBit(cube + 1)) {
					SurgicalModelRenderContext.CubeGeometry cubeGeometry = geometry.cubesById.get(cube);
					if (cubeGeometry == null) {
						complete = false;
						break;
					}
					subjectCuboids.add(cubeGeometry.corners());
				}
			}
			if (complete) {
				measuredCuboids.addAll(subjectCuboids);
			} else {
				fallbackVolume += projectedArea(subject, entry.getValue());
				usedProjectionFallback = true;
			}
		}
		double measuredVolume = measuredCuboids.isEmpty() ? 0.0d
			: SurgicalVolumeSampler.unionVolume(measuredCuboids);
		if (!Double.isFinite(measuredVolume) || !Double.isFinite(fallbackVolume))
			return 0.0d;
		if (usedProjectionFallback)
			fallbackVolume = Math.max(fallbackVolume, SurgicalSlimeDrops.ONE_BALL_VOLUME);
		return Math.max(0.0d, measuredVolume + fallbackVolume);
	}

	static void mergeComponents(Map<Integer, BitSet> selected,
		Map<Integer, BitSet> added) {
		for (Map.Entry<Integer, BitSet> entry : added.entrySet())
			selected.computeIfAbsent(entry.getKey(), ignored -> new BitSet()).or(entry.getValue());
	}

	static boolean footprintIntersectsTile(SurgicalTableLayout.Footprint footprint,
		BlockPos tilePos) {
		return footprint.minX() < tilePos.getX() + 1.0d - 1.0e-6d
			&& footprint.maxX() > tilePos.getX() + 1.0e-6d
			&& footprint.minZ() < tilePos.getZ() + 1.0d - 1.0e-6d
			&& footprint.maxZ() > tilePos.getZ() + 1.0e-6d;
	}

	static double projectedArea(SurgicalSubject subject, BitSet selected) {
		double area = 0.0d;
		for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints())
			if (subject.containsFootprint(selected, footprint))
				area += Math.max(0.0d, footprint.maxX() - footprint.minX())
					* Math.max(0.0d, footprint.maxZ() - footprint.minZ());
		return area;
	}

	/** Selects one model cube without expanding through seams, glue joints, or combinations. */
	@Nullable
	static Selection findCubeSelection(@Nullable CubeHit hit) {
		if (hit == null)
			return null;
		SurgicalModelRenderContext.CubeGeometry cube = hit.geometry.cubesById.get(hit.cubeId);
		if (cube == null)
			return null;
		return new Selection(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams, List.of(),
			List.copyOf(SurgicalClientTopology.cubeEdges(cube)));
	}

	@Nullable
	static Selection findSlimeSeamTargetSelection(ClientLevel level, @Nullable CubeHit hit) {
		if (hit == null
			|| !(level.getBlockEntity(hit.tablePos) instanceof SurgicalTableBlockEntity table))
			return null;
		if (SurgicalJointController.pendingSlimeSeam == null) {
			if (!validSlimeSeamStartCube(level, hit.tablePos, hit.geometry, hit.cubeId))
				return null;
		} else if (!validSlimeSeamCube(level, SurgicalJointController.pendingSlimeSeam,
			hit.tablePos, hit.geometry, hit.cubeId))
			return null;
		return findCubeSelection(hit);
	}

	static boolean selectedEndpoint(SurgicalTableBlockEntity table, Map<Integer, BitSet> components,
		SurgicalGlueJoint.Endpoint endpoint) {
		SurgicalSubject subject = table.getSubjectByPersistentId(endpoint.subjectKey());
		BitSet selected = subject == null ? null : components.get(subject.id());
		return selected != null && selected.get(endpoint.cubeId());
	}

	static boolean combinationFullySelected(SurgicalTableBlockEntity table,
		Map<Integer, BitSet> components, SurgicalCombination combination) {
		for (SurgicalCombination.Member member : combination.members()) {
			SurgicalSubject subject = table.getSubjectByPersistentId(member.subjectKey());
			BitSet selected = subject == null ? null : components.get(subject.id());
			if (selected == null || !selected.get(member.cubeId()))
				return false;
		}
		return true;
	}

	@Nullable
	static Selection findDirectConnectionSelection(@Nullable CubeHit hit) {
		if (hit == null)
			return null;
		ClientLevel level = Minecraft.getInstance().level;
		SurgicalTableBlockEntity table = level != null
			&& level.getBlockEntity(hit.tablePos) instanceof SurgicalTableBlockEntity found ? found : null;
		SurgicalCombination combination = table == null ? null
			: table.combinationContaining(hit.geometry.subjectId, hit.cubeId);
		if (combination != null)
			return combinationSelection(hit, table, combination);
		if (table != null && directSelectionCache != null
			&& directSelectionCache.matches(hit, table.clientDataRevision()))
			return directSelectionCache.selection;
		if (table == null)
			return null;
		Map<Integer, BitSet> direct = table.directConnections(hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams);
		List<SurgicalClientTopology.Edge> connectedEdges = direct.isEmpty() ? null
			: SurgicalInteractionPresentation.directConnectionEdges(hit, table, direct);
		SurgicalModelRenderContext.CubeGeometry hitCube = hit.geometry.cubesById.get(hit.cubeId);
		if (connectedEdges == null || hitCube == null)
			return null;
		Selection selection = new Selection(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams,
			SurgicalClientTopology.cubeEdges(hitCube), connectedEdges);
		directSelectionCache = new CubeSelectionCache(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
			table.clientDataRevision(), hit.geometry.renderRevision, selection);
		return selection;
	}

	@Nullable
	static Selection findDirectConnectionCutSelection(@Nullable CubeHit hit) {
		ClientLevel level = Minecraft.getInstance().level;
		SurgicalTableBlockEntity table = hit != null && level != null
			&& level.getBlockEntity(hit.tablePos) instanceof SurgicalTableBlockEntity found ? found : null;
		SurgicalCombination combination = table == null ? null
			: table.combinationContaining(hit.geometry.subjectId, hit.cubeId);
		if (combination != null)
			return table.externalCombinationJoints(combination).isEmpty()
				? null : combinationSelection(hit, table, combination);
		if (hit == null || table == null)
			return null;
		Map<Integer, BitSet> direct = table.directConnections(hit.geometry.subjectId, hit.cubeId,
			hit.geometry.observedCubeCount, hit.geometry.seams);
		int connectedCubes = direct.values().stream().mapToInt(BitSet::cardinality).sum();
		return connectedCubes <= 1 ? null : findDirectConnectionSelection(hit);
	}

	@Nullable
	static CubeHit findNearestCubeHit(LocalPlayer player, ClientLevel level, Ray ray) {
		return findNearestCubeHit(player, level, ray, null, false);
	}

	@Nullable
	static CubeHit findNearestSlimeSeamHit(LocalPlayer player, ClientLevel level, Ray ray,
		@Nullable SurgicalJointController.PendingSlimeSeam pending) {
		return findNearestCubeHit(player, level, ray, pending, true);
	}

	@Nullable
	static CubeHit findNearestCubeHit(LocalPlayer player, ClientLevel level, Ray ray,
		@Nullable SurgicalJointController.PendingSlimeSeam pendingSlime, boolean slimeSeamSelection) {
		CubeHit best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Map.Entry<SurgicalGeometryStore.SubjectKey, SurgicalGeometryStore.TableGeometry> entry : SurgicalGeometryStore.TABLES.entrySet()) {
			SurgicalGeometryStore.TableGeometry geometry = entry.getValue();
			// The slab test allocates nothing and touches neither the world nor the subject, while
			// matchesObservedTopology boxes a Long per seam. Both are pure rejections, so the free
			// one runs first and the rest of the loop body is skipped for every table the ray misses.
			if (geometry.bounds == null || !SurgicalPickingGeometry.rayIntersectsBounds(ray, geometry.bounds, 1.0e-6d))
				continue;
			BlockPos pos = entry.getKey().tablePos();
			if (!(level.getBlockEntity(pos) instanceof SurgicalTableBlockEntity table)
				|| !table.hasSubject(entry.getKey().subjectId()))
				continue;
			SurgicalSubject subject = table.getSubject(geometry.subjectId);
			if (subject == null || !SurgicalGeometryStore.geometryReadyForUse(table, subject, geometry)
				|| !subject.matchesObservedTopology(geometry.observedCubeCount, geometry.seams))
				continue;
			BitSet visibleCubes = SurgicalPlacementController.temporaryMoveVisibleCubes(subject, geometry.presentCubes);
			for (CubeTarget target : geometry.cubeTargets) {
				SurgicalModelRenderContext.CubeGeometry cube = target.geometry;
				if (!visibleCubes.get(cube.cubeId()))
					continue;
				if (!SurgicalPickingGeometry.rayIntersectsBounds(ray, target.bounds, 1.0e-6d))
					continue;
				if (slimeSeamSelection && (pendingSlime != null
					? !validSlimeSeamCube(level, pendingSlime, pos, geometry, cube.cubeId())
					: !validSlimeSeamStartCube(level, pos, geometry, cube.cubeId())))
					continue;
				for (int faceIndex = 0; faceIndex < SurgicalClientTopology.CUBE_FACES.length; faceIndex++) {
					int[] faceIndices = SurgicalClientTopology.CUBE_FACES[faceIndex];
					Vec3 hit = SurgicalPickingGeometry.intersectQuad(ray.start, ray.end, cube, faceIndices);
					if (hit == null)
						continue;
					double distance = ray.start.distanceToSqr(hit);
					if (distance >= bestDistance)
						continue;
					bestDistance = distance;
					best = new CubeHit(pos, geometry, cube.cubeId(), faceIndex, hit, null);
				}
			}
		}
		return best != null && SurgicalPickingGeometry.isOccluded(level, player, ray.start, best.location, best.tablePos) ? null : best;
	}

	/** A first click is useful only when that cube has at least one physically touching missing edge. */
	static boolean validSlimeSeamStartCube(ClientLevel level, BlockPos tablePos,
		SurgicalGeometryStore.TableGeometry firstGeometry, int firstCubeId) {
		if (!(level.getBlockEntity(tablePos) instanceof SurgicalTableBlockEntity table))
			return false;
		for (SurgicalSubject secondSubject : table.getSubjects()) {
			SurgicalGeometryStore.TableGeometry secondGeometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, secondSubject.id()));
			if (secondGeometry == null)
				continue;
			for (int secondCubeId = secondGeometry.presentCubes.nextSetBit(0); secondCubeId >= 0;
				secondCubeId = secondGeometry.presentCubes.nextSetBit(secondCubeId + 1))
				if (validSlimeSeamPair(level, tablePos,
					firstGeometry.subjectId, firstCubeId, firstGeometry.observedCubeCount, firstGeometry.seams,
					secondGeometry, secondCubeId))
					return true;
		}
		return false;
	}

	static boolean validSlimeSeamCube(ClientLevel level, SurgicalJointController.PendingSlimeSeam pending,
		BlockPos tablePos, SurgicalGeometryStore.TableGeometry secondGeometry, int secondCubeId) {
		Selection first = pending.selection();
		return first.tablePos.equals(tablePos) && validSlimeSeamPair(level, tablePos,
			first.subjectId, first.targetId, first.observedCubeCount, first.seams,
			secondGeometry, secondCubeId);
	}

	static boolean validSlimeSeamPair(ClientLevel level, BlockPos tablePos,
		int firstSubjectId, int firstCubeId, int firstObservedCubeCount,
		List<SurgicalAssembly.Seam> firstSeams, SurgicalGeometryStore.TableGeometry secondGeometry, int secondCubeId) {
		if (firstSubjectId == secondGeometry.subjectId && firstCubeId == secondCubeId
			|| !(level.getBlockEntity(tablePos) instanceof SurgicalTableBlockEntity table)
			|| !table.canAddSlimeSeamTargets(firstSubjectId, firstCubeId,
				secondGeometry.subjectId, secondCubeId))
			return false;
		SurgicalSubject firstSubject = table.getSubject(firstSubjectId);
		SurgicalSubject secondSubject = table.getSubject(secondGeometry.subjectId);
		SurgicalGeometryStore.TableGeometry firstGeometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, firstSubjectId));
		if (firstSubject == null || secondSubject == null || firstGeometry == null
			|| !SurgicalGeometryStore.geometryReadyForUse(table, firstSubject, firstGeometry)
			|| !SurgicalGeometryStore.geometryReadyForUse(table, secondSubject, secondGeometry)
			|| !firstSubject.matchesObservedTopology(firstObservedCubeCount, firstSeams)
			|| !secondSubject.matchesObservedTopology(secondGeometry.observedCubeCount, secondGeometry.seams))
			return false;
		return SurgicalClientTopology.cubesConnectWithinTolerance(
			firstGeometry.cubesById.get(firstCubeId), secondGeometry.cubesById.get(secondCubeId));
	}

	@Nullable
	static CubeHit snapGlueHit(@Nullable CubeHit hit) {
		if (hit == null || hit.gluePoint != null)
			return hit;
		SurgicalModelRenderContext.CubeGeometry cube = hit.geometry.cubesById.get(hit.cubeId);
		GluePoint point = cube == null ? null : SurgicalPickingGeometry.snapGluePoint(cube, hit.faceIndex, hit.location);
		return point == null ? null : new CubeHit(hit.tablePos, hit.geometry, hit.cubeId,
			hit.faceIndex, point.location, point);
	}

	/**
	 * Divides the selected face into model-pixel cells. A hit snaps either to the
	 * containing cell or to its nearest grid intersection, including intersections on
	 * the outer cube boundary.
	 */
	@Nullable
	static GluePoint snapGluePoint(SurgicalModelRenderContext.CubeGeometry cube,
		int faceIndex, Vec3 rawHit) {
		return SurgicalPickingGeometry.snapGluePoint(cube, faceIndex, rawHit);
	}

	static double nearestEndpoint(double progress, PixelInterval interval) {
		return SurgicalPickingGeometry.nearestEndpoint(progress, interval);
	}

	static double normalizedPixelLength(double length) {
		return SurgicalPickingGeometry.normalizedPixelLength(length);
	}

	@Nullable
	static ModelPixelSpan modelPixelSpan(Vec3 first, Vec3 second) {
		return SurgicalPickingGeometry.modelPixelSpan(first, second);
	}

	@Nullable
	static PixelInterval pixelInterval(double progress, double spanStart, double spanEnd) {
		return SurgicalPickingGeometry.pixelInterval(progress, spanStart, spanEnd);
	}

	static Ray playerRay(LocalPlayer player) {
		return SurgicalPickingGeometry.playerRay(player);
	}

	static Map<SurgicalAssembly.Seam, Integer> seamIds(List<SurgicalAssembly.Seam> seams) {
		Map<SurgicalAssembly.Seam, Integer> result = new HashMap<>();
		for (int seamId = 0; seamId < seams.size(); seamId++)
			result.put(seams.get(seamId), seamId);
		return Map.copyOf(result);
	}

	static boolean isOccluded(ClientLevel level, LocalPlayer player, Vec3 start, Vec3 hit,
		BlockPos tablePos) {
		return SurgicalPickingGeometry.isOccluded(level, player, start, hit, tablePos);
	}

	/** Allocation-free segment/AABB broad phase used before oriented cube/contact intersection. */
	static boolean rayIntersectsBounds(Ray ray, AABB bounds, double inflation) {
		return SurgicalPickingGeometry.rayIntersectsBounds(ray, bounds, inflation);
	}

	@Nullable
	static Vec3 intersectQuad(Vec3 start, Vec3 end,
		SurgicalModelRenderContext.CubeGeometry cube, int[] indices) {
		return SurgicalPickingGeometry.intersectQuad(start, end, cube, indices);
	}

	@Nullable
	static Vec3 intersectPolygon(Vec3 start, Vec3 end, List<Vec3> polygon) {
		return SurgicalPickingGeometry.intersectPolygon(start, end, polygon);
	}

	static double pointToContactDistance(Vec3 point, SurgicalClientTopology.Contact contact) {
		return SurgicalPickingGeometry.pointToContactDistance(point, contact);
	}

	static double pointToPolygonDistance(Vec3 point, List<Vec3> polygon) {
		return SurgicalPickingGeometry.pointToPolygonDistance(point, polygon);
	}

	static boolean insideConvexPolygon(Vec3 point, List<Vec3> polygon, Vec3 normal) {
		return SurgicalPickingGeometry.insideConvexPolygon(point, polygon, normal);
	}

	static double pointToSegmentDistance(Vec3 point, Vec3 start, Vec3 end) {
		return SurgicalPickingGeometry.pointToSegmentDistance(point, start, end);
	}

	static boolean isEmptyBox(ItemStack stack) {
		return isEmptyLargeBox(stack)
			|| SurgicalKitItem.isEmptyTemporaryBox(stack);
	}

	static boolean isEmptyLargeBox(ItemStack stack) {
		return CapturedEntityBoxHelper.isEmptyLargeBox(stack);
	}

	static boolean isSmartGlue(ItemStack stack) {
		return SurgicalKitItem.isSmartGlue(stack);
	}

	/** Smart super glue is a super glue item, so every glue the kit knows is covered by this test. */
	static boolean isSurgicalGlue(ItemStack stack) {
		return SurgicalKitItem.isGlue(stack);
	}

	static boolean isSymmetryWand(ItemStack stack) {
		return SurgicalKitItem.isSymmetryWand(stack);
	}

	@Nullable
	static SurgicalLimbType heldLimbType(ItemStack stack) {
		return SurgicalKitItem.limbType(stack);
	}

	@Nullable
	static LimbJointSelection findLimbJointSelection(LocalPlayer player, ClientLevel level, Ray ray,
		@Nullable CubeHit cubeHit) {
		LimbJointSelection direct = findDirectLimbJointSelection(player, level, ray);
		if (direct != null)
			return direct;
		if (cubeHit == null
			|| !(level.getBlockEntity(cubeHit.tablePos) instanceof SurgicalTableBlockEntity table))
			return null;
		SurgicalSubject hitSubject = table.getSubject(cubeHit.geometry.subjectId);
		if (hitSubject == null)
			return null;

		LimbJointSelection best = null;
		double bestDistance = Double.MAX_VALUE;
		for (SurgicalLimbJoint joint : table.limbJoints()) {
			if (!joint.touches(hitSubject.persistentId(), cubeHit.cubeId))
				continue;
			LimbJointSelection candidate = limbJointSelection(cubeHit.tablePos, table, joint);
			if (candidate == null)
				continue;
			double distance = candidate.contact == null ? 0.0d
				: SurgicalPickingGeometry.pointToContactDistance(cubeHit.location, candidate.contact);
			if (distance >= bestDistance)
				continue;
			bestDistance = distance;
			best = candidate;
		}
		return best;
	}

	@Nullable
	static LimbJointSelection findDirectLimbJointSelection(LocalPlayer player, ClientLevel level,
		Ray ray) {
		Set<BlockPos> checkedTables = new java.util.HashSet<>();
		LimbJointSelection best = null;
		Vec3 bestHit = null;
		double bestDistance = Double.MAX_VALUE;
		for (Map.Entry<SurgicalGeometryStore.SubjectKey, SurgicalGeometryStore.TableGeometry> entry : SurgicalGeometryStore.TABLES.entrySet()) {
			SurgicalGeometryStore.TableGeometry geometry = entry.getValue();
			if (geometry.bounds == null
				|| !SurgicalPickingGeometry.rayIntersectsBounds(ray, geometry.bounds, SurgicalTableClientHandler.MAX_SELECTION_THRESHOLD))
				continue;
			BlockPos tablePos = entry.getKey().tablePos();
			if (!checkedTables.add(tablePos)
				|| !(level.getBlockEntity(tablePos) instanceof SurgicalTableBlockEntity table))
				continue;
			for (SurgicalLimbJoint joint : table.limbJoints()) {
				LimbJointSelection candidate = limbJointSelection(tablePos, table, joint);
				if (candidate == null || candidate.contact == null)
					continue;
				Vec3 hit = SurgicalPickingGeometry.intersectContact(ray, candidate.contact);
				if (hit == null)
					continue;
				double distance = ray.start.distanceToSqr(hit);
				if (distance >= bestDistance)
					continue;
				bestDistance = distance;
				bestHit = hit;
				best = candidate;
			}
		}
		return best != null && bestHit != null
			&& SurgicalPickingGeometry.isOccluded(level, player, ray.start, bestHit, best.selection.tablePos) ? null : best;
	}

	@Nullable
	static LimbJointSelection limbJointSelection(BlockPos tablePos, SurgicalTableBlockEntity table,
		SurgicalLimbJoint joint) {
		SurgicalSubject childSubject = table.getSubjectByPersistentId(joint.child().subjectKey());
		SurgicalSubject parentSubject = table.getSubjectByPersistentId(joint.parent().subjectKey());
		if (childSubject == null || parentSubject == null)
			return null;
		SurgicalGeometryStore.TableGeometry childGeometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, childSubject.id()));
		SurgicalGeometryStore.TableGeometry parentGeometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(tablePos, parentSubject.id()));
		if (childGeometry == null || parentGeometry == null || !childGeometry.topologyReady()
			|| !parentGeometry.topologyReady()
			|| !childSubject.matchesObservedTopology(childGeometry.observedCubeCount, childGeometry.seams)
			|| !parentSubject.matchesObservedTopology(parentGeometry.observedCubeCount, parentGeometry.seams)
			|| !childGeometry.presentCubes.get(joint.child().cubeId())
			|| !parentGeometry.presentCubes.get(joint.parent().cubeId()))
			return null;
		SurgicalModelRenderContext.CubeGeometry child = childGeometry.cubesById.get(joint.child().cubeId());
		SurgicalModelRenderContext.CubeGeometry parent = parentGeometry.cubesById.get(joint.parent().cubeId());
		if (child == null || parent == null)
			return null;

		SurgicalClientTopology.Contact contact = SurgicalClientTopology.contactBetween(
			SurgicalAssembly.Seam.of(0, 1), List.of(
				new SurgicalModelRenderContext.CubeGeometry(0, child.corners()),
				new SurgicalModelRenderContext.CubeGeometry(1, parent.corners())));
		List<SurgicalClientTopology.Edge> cubeEdges = new ArrayList<>(24);
		cubeEdges.addAll(SurgicalClientTopology.cubeEdges(child));
		cubeEdges.addAll(SurgicalClientTopology.cubeEdges(parent));
		Selection selection = new Selection(tablePos, childSubject.id(), joint.child().cubeId(),
			childGeometry.observedCubeCount, childGeometry.seams, List.of(), List.copyOf(cubeEdges),
			List.of(), contact == null ? List.of() : contact.edges(), false, false);
		return new LimbJointSelection(selection, joint, contact);
	}

	/**
	 * Highlights exactly what a joint would move: a honey combination behaves as one rigid part, so
	 * the whole combination lights up, while a loose cube highlights on its own.
	 */
	@Nullable
	static Selection findLimbTargetSelection(@Nullable CubeHit hit) {
		if (hit == null)
			return null;
		ClientLevel level = Minecraft.getInstance().level;
		SurgicalTableBlockEntity table = level != null
			&& level.getBlockEntity(hit.tablePos) instanceof SurgicalTableBlockEntity found ? found : null;
		SurgicalCombination combination = table == null ? null
			: table.combinationContaining(hit.geometry.subjectId, hit.cubeId);
		Selection selected;
		if (combination != null) {
			selected = combinationSelection(hit, table, combination);
		} else {
			SurgicalModelRenderContext.CubeGeometry cube = hit.geometry.cubesById.get(hit.cubeId);
			if (cube == null)
				return null;
			selected = new Selection(hit.tablePos, hit.geometry.subjectId, hit.cubeId,
				hit.geometry.observedCubeCount, hit.geometry.seams, List.of(),
				List.copyOf(SurgicalClientTopology.cubeEdges(cube)));
		}
		return validSecondLimbTarget(selected, table) ? selected : null;
	}

	/** The first target stays highlighted, but a physically disconnected second target never does. */
	static boolean validSecondLimbTarget(Selection selected,
		@Nullable SurgicalTableBlockEntity table) {
		SurgicalJointController.PendingLimb first = SurgicalJointController.pendingLimb;
		if (first == null)
			return true;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null
			|| heldLimbType(minecraft.player.getItemInHand(first.hand())) != first.type())
			return true;
		return table != null && first.selection().tablePos().equals(selected.tablePos())
			&& table.canConnectLimbTargets(first.selection().subjectId(), first.selection().targetId(),
				selected.subjectId(), selected.targetId());
	}

	static void clearSelections() {
		SurgicalWrenchController.wrenchEditor = null;
		SurgicalInteractionPresentation.GLUE_EDIT_OUTLINE.clear();
		seamSelection = null;
		cubeSelection = null;
		componentSelection = null;
		reservedSelection = null;
		symmetrySelection = null;
		limbSelection = null;
		slimeSeamSelection = null;
		jointRemovalSelection = null;
		SurgicalGlueController.hoveredGluePoint = null;
		connectedSelectionCache = null;
		connectedLimbSelectionCache = null;
		directSelectionCache = null;
		lastSelectionMode = Integer.MIN_VALUE;
		lastSelectionRay = null;
		lastSelectionPendingGlue = null;
		lastSelectionPendingSymmetry = null;
		lastSelectionPendingSymmetryReference = null;
		lastSelectionPendingLimb = null;
		lastSelectionPendingSlimeSeam = null;
		SurgicalInteractionPresentation.GLUE_POINT_OUTLINE.clear();
		SurgicalInteractionPresentation.clearSeamHighlight();
		SurgicalInteractionPresentation.clearSymmetryPlaneHighlight();
	}

	record SelectionHighlightEdges(List<SurgicalClientTopology.Edge> cubeEdges,
		List<SurgicalClientTopology.Edge> combinationEdges,
		List<SurgicalClientTopology.Edge> glueJointEdges,
		List<SurgicalClientTopology.Edge> limbJointEdges) {
		SelectionHighlightEdges {
			cubeEdges = List.copyOf(cubeEdges);
			combinationEdges = List.copyOf(combinationEdges);
			glueJointEdges = List.copyOf(glueJointEdges);
			limbJointEdges = List.copyOf(limbJointEdges);
		}
	}

	record Selection(BlockPos tablePos, int subjectId, int targetId, int observedCubeCount,
		List<SurgicalAssembly.Seam> seams, List<SurgicalClientTopology.Edge> edges,
		List<SurgicalClientTopology.Edge> glueJointEdges,
		List<SurgicalClientTopology.Edge> cubeEdges,
		List<SurgicalClientTopology.Edge> combinationEdges,
		List<SurgicalClientTopology.Edge> limbJointEdges, boolean glueJoint, boolean combination) {
		Selection(BlockPos tablePos, int subjectId, int targetId, int observedCubeCount,
			List<SurgicalAssembly.Seam> seams, List<SurgicalClientTopology.Edge> edges,
			List<SurgicalClientTopology.Edge> cubeEdges,
			List<SurgicalClientTopology.Edge> combinationEdges,
			List<SurgicalClientTopology.Edge> limbJointEdges, boolean glueJoint, boolean combination) {
			this(tablePos, subjectId, targetId, observedCubeCount, seams, edges, List.of(), cubeEdges,
				combinationEdges, limbJointEdges, glueJoint, combination);
		}

		Selection(BlockPos tablePos, int subjectId, int targetId, int observedCubeCount,
			List<SurgicalAssembly.Seam> seams, List<SurgicalClientTopology.Edge> edges,
			List<SurgicalClientTopology.Edge> cubeEdges,
			List<SurgicalClientTopology.Edge> combinationEdges, boolean glueJoint, boolean combination) {
			this(tablePos, subjectId, targetId, observedCubeCount, seams, edges, List.of(), cubeEdges,
				combinationEdges, List.of(), glueJoint, combination);
		}

		Selection(BlockPos tablePos, int subjectId, int targetId, int observedCubeCount,
			List<SurgicalAssembly.Seam> seams, List<SurgicalClientTopology.Edge> edges,
			List<SurgicalClientTopology.Edge> cubeEdges) {
			this(tablePos, subjectId, targetId, observedCubeCount, seams, edges, List.of(), cubeEdges,
				List.of(), List.of(), false, false);
		}

		Selection(BlockPos tablePos, int subjectId, int targetId, int observedCubeCount,
			List<SurgicalAssembly.Seam> seams, List<SurgicalClientTopology.Edge> edges,
			List<SurgicalClientTopology.Edge> cubeEdges, boolean glueJoint) {
			this(tablePos, subjectId, targetId, observedCubeCount, seams, edges, List.of(), cubeEdges,
				List.of(), List.of(), glueJoint, false);
		}

		Selection(BlockPos tablePos, int subjectId, int targetId, int observedCubeCount,
			List<SurgicalAssembly.Seam> seams, List<SurgicalClientTopology.Edge> edges,
			List<SurgicalClientTopology.Edge> cubeEdges, boolean glueJoint, boolean combination) {
			this(tablePos, subjectId, targetId, observedCubeCount, seams, edges, List.of(),
				combination ? List.of() : cubeEdges, combination ? cubeEdges : List.of(), List.of(),
				glueJoint, combination);
		}
	}

	record GlueJointSelection(Selection selection,
		@Nullable SurgicalClientTopology.Contact contact) {}

	record LimbJointSelection(Selection selection, SurgicalLimbJoint joint,
		@Nullable SurgicalClientTopology.Contact contact) {}

	record Ray(Vec3 start, Vec3 end) {}

	record CubeHit(BlockPos tablePos, SurgicalGeometryStore.TableGeometry geometry, int cubeId, int faceIndex, Vec3 location,
		@Nullable GluePoint gluePoint) {}

	record GluePoint(Vec3 location, List<SurgicalClientTopology.Edge> markerEdges) {
		GluePoint {
			markerEdges = List.copyOf(markerEdges);
		}
	}

	record PixelInterval(double start, double end, double center) {}

	record ModelPixelSpan(double start, double end) {
		double length() {
			return Math.abs(end - start);
		}
	}

	record ContactProjection(SurgicalGlueContact contact, double distance) {}

	record CubeTarget(SurgicalModelRenderContext.CubeGeometry geometry, AABB bounds) {}

	record CubeSelectionCache(BlockPos tablePos, int subjectId, int cubeId, int tableRevision,
		int renderRevision, Selection selection) {
		private boolean matches(CubeHit hit, int revision) {
			return tablePos.equals(hit.tablePos) && subjectId == hit.geometry.subjectId && cubeId == hit.cubeId
				&& tableRevision == revision && renderRevision == hit.geometry.renderRevision;
		}
	}
}
