package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalProfiler;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlock;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlacementPacket;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlacementResult;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalVolumeSampler;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.network.CBPackets;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.ponder.api.PonderPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Internal SurgicalPlacementController implementation; public entry points remain on SurgicalTableClientHandler. */
final class SurgicalPlacementController {
	private SurgicalPlacementController() {}

	static long lastPlacementOutlineTick = Long.MIN_VALUE;
	static long lastShovelTileOutlineTick = Long.MIN_VALUE;
	@Nullable
	static BlockPos shovelTileOutlinePos;
	@Nullable
	static PlacementCandidate placementCandidate;
	static boolean temporaryMoveCacheInitialized;
	static ItemStack temporaryMoveCacheStack = ItemStack.EMPTY;
	@Nullable
	static SurgicalKitItem.TemporaryMove temporaryMoveCache;
	@Nullable
	static PlacementPreview placementPreview;
	static SurgicalTablePlacementResult placementPreviewResult = SurgicalTablePlacementResult.OPERATION_FAILED;
	@Nullable
	static PlacementSuppression placementSuppression;

	/**
	 * The cubes of {@code present} a held temporary box is not carrying. Called per subject per frame,
	 * so the common case returns {@code present} itself: treat the result as read-only.
	 */
	static BitSet temporaryMoveVisibleCubes(SurgicalSubject subject, BitSet present) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null)
			return present;
		for (InteractionHand hand : SurgicalTableClientHandler.HANDS) {
			SurgicalKitItem.TemporaryMove move = cachedTemporaryMove(minecraft.player.getItemInHand(hand));
			if (move == null || !minecraft.level.dimension().location().equals(move.dimension()))
				continue;
			BitSet moved = move.component(subject.persistentId());
			if (moved == null || moved.isEmpty())
				continue;
			BitSet visible = (BitSet) present.clone();
			visible.andNot(moved);
			return visible;
		}
		return present;
	}

	/**
	 * {@link SurgicalKitItem#hasPlacementCapture} answered from the parsed-move memo, so the per-frame
	 * placement and prompt probes do not decode the kit's stored move again.
	 */
	static boolean hasPlacementCapture(ItemStack stack) {
		return cachedTemporaryMove(stack) != null
			|| SurgicalKitItem.isCubeCopier(stack) && CapturedEntityBoxHelper.hasCapturedEntity(stack);
	}

	@Nullable
	static SurgicalKitItem.TemporaryMove cachedTemporaryMove(ItemStack stack) {
		// A stored move remains dormant when another logical kit tool is selected. This mirrors the
		// physical box interaction: only the box currently in hand suppresses its source rendering.
		if (!SurgicalKitItem.isTemporaryBox(stack) || !CapturedEntityBoxHelper.hasCapturedEntity(stack))
			return null;
		if (temporaryMoveCacheInitialized
			&& ItemStack.isSameItemSameComponents(temporaryMoveCacheStack, stack))
			return temporaryMoveCache;
		temporaryMoveCacheInitialized = true;
		temporaryMoveCacheStack = stack.copy();
		temporaryMoveCache = SurgicalKitItem.temporaryMove(stack);
		return temporaryMoveCache;
	}

	/** Draws the Ctrl-shovel target with the same thin tabletop box used by placement previews. */
	static void updateShovelTileOutline() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		boolean holdingShovel = player != null && (SurgicalKitItem.isShovel(player.getMainHandItem())
			|| SurgicalKitItem.isShovel(player.getOffhandItem()));
		BlockPos target = holdingShovel && Screen.hasControlDown() && level != null
			? targetedTableTile(minecraft, level) : null;
		if (target == null) {
			clearShovelTileOutline();
			return;
		}
		long tick = level.getGameTime();
		if (target.equals(shovelTileOutlinePos)) {
			if (tick != lastShovelTileOutlineTick) {
				Outliner.getInstance().keep(SurgicalInteractionPresentation.SHOVEL_TILE_OUTLINE_SLOT);
				lastShovelTileOutlineTick = tick;
			}
			return;
		}

		double surfaceY = SurgicalTablePlane.surfaceY(target.getY());
		Outliner.getInstance().showAABB(SurgicalInteractionPresentation.SHOVEL_TILE_OUTLINE_SLOT,
			new AABB(target.getX(), surfaceY + 0.002d, target.getZ(),
				target.getX() + 1.0d, surfaceY + 0.012d, target.getZ() + 1.0d))
			.colored(PonderPalette.RED.getColor())
			.disableLineNormals()
			.lineWidth(SurgicalTableClientHandler.HIGHLIGHT_LINE_WIDTH);
		shovelTileOutlinePos = target.immutable();
		lastShovelTileOutlineTick = tick;
	}

	static void clearShovelTileOutline() {
		if (shovelTileOutlinePos != null)
			Outliner.getInstance().remove(SurgicalInteractionPresentation.SHOVEL_TILE_OUTLINE_SLOT);
		shovelTileOutlinePos = null;
		lastShovelTileOutlineTick = Long.MIN_VALUE;
	}

	/** The actual vanilla-targeted table tile; independent of renderable surgical model geometry. */
	@Nullable
	static BlockPos targetedTableTile(Minecraft minecraft, ClientLevel level) {
		if (minecraft.screen != null || !(minecraft.hitResult instanceof BlockHitResult hit))
			return null;
		BlockPos target = hit.getBlockPos();
		if (!(level.getBlockState(target).getBlock() instanceof SurgicalTableBlock))
			return null;
		SurgicalTablePlane.Plane plane = SurgicalSelectionResolver.clientPlane(level, target);
		return plane.valid() && plane.workArea().containsTile(target) ? target : null;
	}

	static void updatePlacementPreview() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (SurgicalVisualCommitCoordinator.retainsPlacementPreview()) {
			placementPreviewResult = SurgicalTablePlacementResult.SUCCESS;
			if (level != null && level.getGameTime() != lastPlacementOutlineTick) {
				Outliner.getInstance().keep(SurgicalInteractionPresentation.PLACEMENT_OUTLINE_SLOT);
				lastPlacementOutlineTick = level.getGameTime();
			}
			return;
		}
		if (placementSuppression != null && player != null && level != null) {
			PlacementSuppression suppression = placementSuppression;
			boolean inventoryUpdated = !ItemStack.isSameItemSameComponents(suppression.sourceBox,
				player.getItemInHand(suppression.hand));
			if (inventoryUpdated || level.getGameTime() >= suppression.expiresAtTick) {
				placementSuppression = null;
			} else {
				rejectPlacementPreview(SurgicalTablePlacementResult.OPERATION_FAILED);
				return;
			}
		}
		if (player == null || level == null || minecraft.screen != null
			|| SurgicalCutController.pendingCut != null || SurgicalCutController.pendingGlueCut != null
			|| !(minecraft.hitResult instanceof BlockHitResult hit)
			|| !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof SurgicalTableBlock)) {
			rejectPlacementPreview(SurgicalTablePlacementResult.INVALID_TABLE);
			return;
		}

		InteractionHand hand = null;
		ItemStack heldBox = ItemStack.EMPTY;
		PlacementSource source = null;
		for (InteractionHand candidateHand : SurgicalTableClientHandler.HANDS) {
			ItemStack held = player.getItemInHand(candidateHand);
			if (!SurgicalTableBlockEntity.isPlacementContainer(held)
				&& !hasPlacementCapture(held)
				|| !CapturedEntityBoxHelper.hasCapturedEntity(held))
				continue;
			PlacementCandidate prepared = placementCandidateFor(held, level);
			if (prepared.source() != null) {
				hand = candidateHand;
				heldBox = held;
				source = prepared.source();
				break;
			}
		}
		if (hand == null || source == null) {
			rejectPlacementPreview(SurgicalTablePlacementResult.UNSUPPORTED_SUBJECT);
			return;
		}

		SurgicalTablePlane.Plane plane = SurgicalSelectionResolver.clientPlane(level, hit.getBlockPos());
		if (!plane.valid() || plane.source() == null || plane.workArea().isEmpty()) {
			rejectPlacementPreview(SurgicalTablePlacementResult.INVALID_TABLE);
			return;
		}
		Vec3 target = SurgicalSelectionResolver.tableSurfaceTarget(SurgicalSelectionResolver.playerRay(player), plane.workArea().surfaceY() + 0.01d);
		BlockPos ownerPos = plane.source();
		Direction placementFacing = player.getDirection();
		SurgicalTableBlockEntity controller = level.getBlockEntity(ownerPos) instanceof SurgicalTableBlockEntity table
			? table : null;
		if (controller == null) {
			rejectPlacementPreview(SurgicalTablePlacementResult.INVALID_TABLE);
			return;
		}
		if (effectivePlacementSubjectCount(controller, heldBox)
			> SurgicalTableBlockEntity.MAX_SUBJECTS - source.subjectSlots()) {
			rejectPlacementPreview(SurgicalTablePlacementResult.TABLE_FULL);
			return;
		}
		boolean projectSourceGeometry = controller.clientProjectsSourceGeometry();
		int tableRevision = controller.clientDataRevision();
		PlacementGeometry placementGeometry = source.measure(placementFacing, projectSourceGeometry,
			Screen.hasControlDown() ? SurgicalLayPose.RotationAxis.values()[source.groundOrientation] : null);
		if (placementGeometry == null) {
			rejectPlacementPreview(SurgicalTablePlacementResult.MODEL_UNAVAILABLE);
			return;
		}
		PlacementPreview previous = placementPreview;
		boolean reusablePreview = previous != null && previous.ownerPos.equals(ownerPos) && previous.hand == hand
			&& previous.source == source && previous.facing == placementFacing
			&& previous.geometry == placementGeometry
			&& previous.layPose.equals(placementGeometry.layPose())
			&& previous.projectSourceGeometry == projectSourceGeometry
			&& previous.tableRevision == tableRevision && previous.workArea == plane.workArea();
		if (reusablePreview && SurgicalSelectionResolver.sameHorizontalTarget(target, previous.targetX, previous.targetZ)) {
			placementPreviewResult = SurgicalTablePlacementResult.SUCCESS;
			refreshPlacementFeedback(player, level);
			return;
		}
		List<SurgicalTableLayout.Footprint> occupied = placementOccupiedFootprints(level, plane, controller,
			heldBox);
		if (occupied == null) {
			rejectPlacementPreview(SurgicalTablePlacementResult.INVALID_TABLE);
			return;
		}
		SurgicalClientTopology.PlacementPlan plan;
		DiscoveredPlacement discovered = DiscoveredPlacement.EMPTY;
		AABB localBounds = placementGeometry.bounds();
		if (!Double.isFinite(target.x) || !Double.isFinite(target.z)
			|| occupied.stream().anyMatch(footprint -> !SurgicalTableLayout.validStoredFootprint(footprint))
			|| !SurgicalGeometryStore.finiteCubeGeometry(SurgicalSelectionResolver.boundsGeometry(localBounds, Vec3.ZERO))) {
			rejectPlacementPreview(SurgicalTablePlacementResult.OPERATION_FAILED);
			return;
		}
		SurgicalModelRenderContext.CubeGeometry renderedBounds = SurgicalSelectionResolver.boundsGeometry(localBounds,
			Vec3.atLowerCornerOf(ownerPos));
		if (source.assembly == null) {
			List<SurgicalModelRenderContext.CubeGeometry> worldCubes = SurgicalSelectionResolver.translateCubes(
				placementGeometry.discoveredCubes(), Vec3.atLowerCornerOf(ownerPos));
			int count = placementGeometry.discoveredCubeCount();
			if (count <= 0 || !SurgicalAssembly.validTopology(count, placementGeometry.discoveredSeams())
				|| worldCubes.size() != count || worldCubes.stream().anyMatch(cube -> cube.cubeId() < 0
					|| cube.cubeId() >= count || !SurgicalGeometryStore.finiteCubeGeometry(cube))
				|| worldCubes.stream().map(SurgicalModelRenderContext.CubeGeometry::cubeId).distinct().count() != count) {
				rejectPlacementPreview(SurgicalTablePlacementResult.MODEL_UNAVAILABLE);
				return;
			}
			SurgicalClientTopology.DiscoveredPlacementPlan discoveredPlan =
				SurgicalClientTopology.planDiscoveredPlacement(placementGeometry.discoveredCubeCount(),
					placementGeometry.discoveredSeams(), renderedBounds, worldCubes, plane.workArea(), target.x,
					target.z, occupied);
			if (discoveredPlan == null) {
				rejectPlacementPreview(SurgicalTablePlacementResult.NO_SPACE);
				return;
			}
			plan = discoveredPlan.placement();
			discovered = new DiscoveredPlacement(placementGeometry.discoveredCubeCount(),
				placementGeometry.discoveredSeams(),
				discoveredAnatomy(placementGeometry.discoveredCubes(),
					placementGeometry.discoveredCubeCount()),
				discoveredPlan.componentFootprints());
		} else {
			plan = SurgicalClientTopology.planInitialPlacement(List.of(renderedBounds),
				plane.workArea(), target.x, target.z, occupied);
		}
		if (plan == null) {
			rejectPlacementPreview(SurgicalTablePlacementResult.NO_SPACE);
			return;
		}
		plan = exactInitialPlacement(source, placementGeometry, plan);
		if (reusablePreview && previous.plan.equals(plan) && previous.discovered.equals(discovered)
			&& previous.occupied.equals(occupied)) {
			// The pointer moved within the same snap result. All inputs to the source
			// layouts and validation are unchanged, so retain the already verified plan.
			placementPreview = previous.withTarget(target.x, target.z);
			placementPreviewResult = SurgicalTablePlacementResult.SUCCESS;
			SurgicalProfiler.count("placementLayout(cacheHit)");
			refreshPlacementFeedback(player, level);
			return;
		}
		List<SurgicalTableLayout.Proposal> sourceLayouts = compositeSourceLayouts(source, placementGeometry,
			ownerPos, plan, plane, occupied);
		if (sourceLayouts == null || !SurgicalTableLayout.validateSubjectPlacement(plane, source.assembly,
			placementFacing, placementGeometry.layPose(), plan.originOffsetX(), plan.originOffsetZ(),
			plan.proposal(), discovered.cubeCount(), discovered.seams(), discovered.footprints(),
			sourceLayouts, occupied)) {
			rejectPlacementPreview(SurgicalTablePlacementResult.invalidLayout(plane, plan.proposal(),
				discovered.footprints(), sourceLayouts == null ? List.of() : sourceLayouts, occupied));
			return;
		}

		placementPreviewResult = SurgicalTablePlacementResult.SUCCESS;
		placementPreview = new PlacementPreview(ownerPos, hand, source, placementFacing,
			placementGeometry.layPose(), plan, placementGeometry.cubeOffsets(),
			placementGeometry.sources(), discovered, sourceLayouts, projectSourceGeometry,
			plane.workArea(), target.x, target.z, tableRevision, placementGeometry, occupied);
		SurgicalTableLayout.Footprint footprint = plan.proposal().footprints().getFirst();
		Outliner.getInstance().showAABB(SurgicalInteractionPresentation.PLACEMENT_OUTLINE_SLOT,
			new AABB(footprint.minX(), plane.workArea().surfaceY() + 0.002d, footprint.minZ(),
				footprint.maxX(), plane.workArea().surfaceY() + 0.012d, footprint.maxZ()))
			.colored(PonderPalette.GREEN.getColor())
			.disableLineNormals()
			.lineWidth(SurgicalTableClientHandler.HIGHLIGHT_LINE_WIDTH);
		lastPlacementOutlineTick = level.getGameTime();
	}

	static SurgicalClientTopology.PlacementPlan exactInitialPlacement(PlacementSource source,
		PlacementGeometry geometry, SurgicalClientTopology.PlacementPlan plan) {
		if (source.assembly == null || source.isComposite())
			return plan;
		List<SurgicalTableLayout.CubeOffset> offsets = new ArrayList<>(source.assembly.presentCubes().cardinality());
		BitSet present = source.assembly.presentCubes();
		for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
			Vec3 offset = geometry.cubeOffsets().getOrDefault(cube, Vec3.ZERO);
			offsets.add(new SurgicalTableLayout.CubeOffset(cube, offset.x, offset.y, offset.z));
		}
		SurgicalTableLayout.Proposal exact = new SurgicalTableLayout.Proposal(offsets,
			plan.proposal().footprints());
		return new SurgicalClientTopology.PlacementPlan(plan.originOffsetX(), plan.originOffsetZ(), exact);
	}

	@Nullable
	static List<SurgicalTableLayout.Proposal> compositeSourceLayouts(PlacementSource source,
		PlacementGeometry geometry, BlockPos ownerPos, SurgicalClientTopology.PlacementPlan plan,
		SurgicalTablePlane.Plane plane, List<SurgicalTableLayout.Footprint> occupied) {
		if (!source.isComposite())
			return List.of();
		if (source.assembly == null || geometry.sources().size() != source.assembly.sources().size())
			return null;
		Vec3 placementOffset = Vec3.atLowerCornerOf(ownerPos)
			.add(plan.originOffsetX(), 0.0d, plan.originOffsetZ());
		List<SurgicalTableLayout.Proposal> layouts = new ArrayList<>(geometry.sources().size());
		for (SourcePlacementGeometry sourceGeometry : geometry.sources()) {
			SurgicalAssembly.PlacedSource placedSource = sourceGeometry.placedSource();
			SurgicalAssembly.Source assemblySource = placedSource.source();
			List<SurgicalModelRenderContext.CubeGeometry> worldCubes = SurgicalSelectionResolver.translateCubes(
				sourceGeometry.baseCubes(), placementOffset);
			SurgicalClientTopology.PlannedLayout planned = SurgicalClientTopology.preserveCompositeLayout(
				assemblySource.cubeCount(), assemblySource.presentCubes(), assemblySource.seams(),
				assemblySource.cutSeams(), worldCubes, sourceGeometry.renderOffsets(), plane.workArea(), occupied);
			if (planned == null)
				return null;
			layouts.add(planned.proposal());
		}
		return List.copyOf(layouts);
	}

	static PlacementCandidate placementCandidateFor(ItemStack held, ClientLevel level) {
		if (placementCandidate != null && ItemStack.isSameItemSameComponents(placementCandidate.box(), held))
			return placementCandidate;
		SurgicalAssembly packed = CapturedEntityBoxHelper.getCapturedAssembly(held);
		if (packed != null)
			return cachePlacementCandidate(held,
				new PlacementSource(held.copyWithCount(1), packed.profile(), packed), SurgicalTablePlacementResult.SUCCESS);
		Entity captured = CapturedEntityBoxHelper.createCapturedEntity(held, level);
		if (captured == null)
			return cachePlacementCandidate(held, null, SurgicalTablePlacementResult.INVALID_CAPTURE);
		MimicProfile profile;
		SurgicalAssembly assembly = null;
		if (captured instanceof SlimeBionicEntity bionic) {
			assembly = bionic.getAssembly();
			if (assembly == null)
				return cachePlacementCandidate(held, null, SurgicalTablePlacementResult.INVALID_ASSEMBLY);
			profile = assembly.profile();
		} else if (captured instanceof LivingEntity living && SlimeMimicHandler.isSlimeMimic(living)) {
			profile = MimicProfile.capture(living);
			if (profile == null)
				return cachePlacementCandidate(held, null, SurgicalTablePlacementResult.INVALID_CAPTURE);
		} else if (captured instanceof LivingEntity)
			return cachePlacementCandidate(held, null, SurgicalTablePlacementResult.UNSUPPORTED_SUBJECT);
		else
			return cachePlacementCandidate(held, null, SurgicalTablePlacementResult.INVALID_CAPTURE);
		return cachePlacementCandidate(held,
			new PlacementSource(held.copyWithCount(1), profile, assembly), SurgicalTablePlacementResult.SUCCESS);
	}

	static int effectivePlacementSubjectCount(SurgicalTableBlockEntity table, ItemStack held) {
		SurgicalKitItem.TemporaryMove move = cachedTemporaryMove(held);
		if (!temporaryMoveAppliesToTable(move, table))
			return table.getSubjects().size();
		int freed = 0;
		Map<UUID, BitSet> components = move.components();
		for (SurgicalSubject subject : table.getSubjects()) {
			BitSet moved = components.get(subject.persistentId());
			if (moved != null && moved.equals(subject.presentCubes()))
				freed++;
		}
		return table.getSubjects().size() - freed;
	}

	@Nullable
	static List<SurgicalTableLayout.Footprint> placementOccupiedFootprints(ClientLevel level,
		SurgicalTablePlane.Plane plane, SurgicalTableBlockEntity table, ItemStack held) {
		SurgicalKitItem.TemporaryMove move = cachedTemporaryMove(held);
		if (!temporaryMoveAppliesToTable(move, table))
			return SurgicalTablePlane.occupiedFootprints(level, plane, -1);
		Map<UUID, BitSet> components = move.components();
		List<SurgicalTableLayout.Footprint> occupied = new ArrayList<>();
		for (SurgicalSubject subject : table.getSubjects()) {
			if (subject.occupiedFootprints().isEmpty())
				return null;
			BitSet moved = components.get(subject.persistentId());
			for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints())
				if (!subject.containsFootprint(moved, footprint))
					occupied.add(footprint);
		}
		return List.copyOf(occupied);
	}

	static boolean temporaryMoveAppliesToTable(@Nullable SurgicalKitItem.TemporaryMove move,
		SurgicalTableBlockEntity table) {
		if (move == null || table.getLevel() == null
			|| !table.getLevel().dimension().location().equals(move.dimension()))
			return false;
		for (SurgicalSubject subject : table.getSubjects())
			if (move.component(subject.persistentId()) != null)
				return true;
		return false;
	}

	static PlacementCandidate cachePlacementCandidate(ItemStack held, @Nullable PlacementSource source,
		SurgicalTablePlacementResult result) {
		if (placementCandidate != null && placementCandidate.source() != null) placementCandidate.source().cancel();
		placementCandidate = new PlacementCandidate(held.copyWithCount(1), source, result);
		return placementCandidate;
	}

	static void clearPlacementPreview() {
		if (placementPreview == null && lastPlacementOutlineTick == Long.MIN_VALUE)
			return;
		placementPreview = null;
		Outliner.getInstance().remove(SurgicalInteractionPresentation.PLACEMENT_OUTLINE_SLOT);
		lastPlacementOutlineTick = Long.MIN_VALUE;
	}

	static void rejectPlacementPreview(SurgicalTablePlacementResult result) {
		clearPlacementPreview();
		placementPreviewResult = result;
	}

	static void refreshPlacementFeedback(LocalPlayer player, ClientLevel level) {
		long tick = level.getGameTime();
		if (tick != lastPlacementOutlineTick) {
			Outliner.getInstance().keep(SurgicalInteractionPresentation.PLACEMENT_OUTLINE_SLOT);
			lastPlacementOutlineTick = tick;
		}
	}

	@SubscribeEvent
	static void renderPlacementPreview(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || placementPreview == null)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null)
			return;
		PlacementPreview placement = placementPreview;

		Vec3 camera = event.getCamera().getPosition();
		PoseStack poseStack = event.getPoseStack();
		poseStack.pushPose();
		poseStack.translate(placement.ownerPos.getX() - camera.x, placement.ownerPos.getY() - camera.y,
			placement.ownerPos.getZ() - camera.z);
		poseStack.translate(placement.plan.originOffsetX(), 0.0d, placement.plan.originOffsetZ());
		MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
		int light = LevelRenderer.getLightColor(minecraft.level, placement.ownerPos);
		if (Screen.hasControlDown() && placement.source.measuredGeometry != null) {
			com.mojang.blaze3d.vertex.VertexConsumer plane =
				buffer.getBuffer(net.minecraft.client.renderer.RenderType.debugQuads());
			for (Vec3 point : placement.source.measuredGeometry.groundPlane())
				plane.addVertex(poseStack.last().pose(), (float) point.x, (float) point.y, (float) point.z)
					.setColor(48, 144, 255, 85);
		}
		float partialTicks = AnimationTickHolder.getPartialTicks();
		if (placement.source.isComposite()) {
			for (int sourceIndex = 0; sourceIndex < placement.sourceGeometries.size(); sourceIndex++) {
				SourcePlacementGeometry sourceGeometry = placement.sourceGeometries.get(sourceIndex);
				SurgicalAssembly.PlacedSource placedSource = sourceGeometry.placedSource();
				SurgicalAssembly.Source assemblySource = placedSource.source();
				LivingEntity preview = placement.source.preview(assemblySource.profile());
				if (preview == null)
					continue;
				poseStack.pushPose();
				poseStack.translate(placedSource.originOffset().x, placedSource.originOffset().y,
					placedSource.originOffset().z);
				SurgicalTablePoseResolver.resolve(placedSource.layPose()).apply(poseStack);
				SurgicalSourceModelRenderer.render(preview, assemblySource.cubeCount(),
					assemblySource.presentCubes(), sourceGeometry.previewOffsets(),
					sourceGeometry.renderRotations(),
					poseStack, buffer, light,
					0.0f, partialTicks, false, camera, placement.projectSourceGeometry, 1.0f, null, false);
				poseStack.popPose();
			}
		} else {
			LivingEntity preview = placement.source.preview();
			if (preview != null) {
				SurgicalTablePoseResolver.resolve(placement.layPose).apply(poseStack);
				SurgicalSourceModelRenderer.render(preview, placement.source.cubeCount(),
					placement.source.presentCubes(), placement.renderOffsets, Map.of(), poseStack, buffer, light,
					0.0f, partialTicks, false, camera, placement.projectSourceGeometry, 1.0f, null, false);
			}
		}
		poseStack.popPose();
		buffer.endBatch();
	}

	@SubscribeEvent
	static void renderPickupGroundPlane(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !Screen.hasControlDown())
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
			|| placementPreview != null || SurgicalSelectionResolver.cubeSelection == null)
			return;
		InteractionHand hand = SurgicalInteractionPresentation.interactionPromptHand(minecraft.player);
		if (hand == null || !SurgicalSelectionResolver.isEmptyBox(minecraft.player.getItemInHand(hand)))
			return;
		SurgicalSelectionResolver.Selection selected = SurgicalSelectionResolver.cubeSelection;
		if (!(minecraft.level.getBlockEntity(selected.tablePos()) instanceof SurgicalTableBlockEntity table))
			return;
		SurgicalSubject anchor = table.getSubject(selected.subjectId());
		if (anchor == null)
			return;
		SurgicalLayPose pose = anchor.layPose();
		Vec3 origin = Vec3.atLowerCornerOf(selected.tablePos());
		AABB upright = null;
		Map<Integer, BitSet> components = table.connectedComponents(selected.subjectId(), selected.targetId(),
			selected.observedCubeCount(), selected.seams());
		for (Map.Entry<Integer, BitSet> entry : components.entrySet()) {
			SurgicalSubject subject = table.getSubject(entry.getKey());
			SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(selected.tablePos(), entry.getKey()));
			if (subject == null || !SurgicalGeometryStore.geometryReadyForUse(table, subject, geometry))
				return;
			for (int cube = entry.getValue().nextSetBit(0); cube >= 0; cube = entry.getValue().nextSetBit(cube + 1)) {
				SurgicalModelRenderContext.CubeGeometry shape = geometry.cubesById.get(cube);
				if (shape == null)
					return;
				for (Vec3 corner : shape.corners()) {
					Vec3 point = pose.inverseRotate(corner.subtract(origin));
					AABB pointBounds = new AABB(point, point);
					upright = upright == null ? pointBounds : upright.minmax(pointBounds);
				}
			}
		}
		if (upright == null)
			return;
		List<Vec3> corners = groundPlaneCorners(upright, pose);
		PoseStack stack = event.getPoseStack();
		Vec3 camera = event.getCamera().getPosition();
		MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
		com.mojang.blaze3d.vertex.VertexConsumer vertices =
			buffer.getBuffer(net.minecraft.client.renderer.RenderType.debugQuads());
		for (Vec3 corner : corners) {
			Vec3 point = corner.add(origin).subtract(camera);
			vertices.addVertex(stack.last().pose(), (float) point.x, (float) point.y, (float) point.z)
				.setColor(48, 144, 255, 85);
		}
		buffer.endBatch(net.minecraft.client.renderer.RenderType.debugQuads());
	}

	static List<Vec3> groundPlaneCorners(AABB upright, SurgicalLayPose pose) {
		// Less than the model/table gap: the downward preview must stay above the tabletop.
		double y = upright.minY - SurgicalTablePoseResolver.TABLE_CLEARANCE * 0.25d;
		double padding = 0.08d;
		return List.of(new Vec3(upright.minX - padding, y, upright.minZ - padding),
			new Vec3(upright.maxX + padding, y, upright.minZ - padding),
			new Vec3(upright.maxX + padding, y, upright.maxZ + padding),
			new Vec3(upright.minX - padding, y, upright.maxZ + padding)).stream().map(pose::rotate).toList();
	}

	@Nullable
	static AABB groundPlaneBounds(@Nullable AABB bounds,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> offsets, SurgicalLayPose pose) {
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			for (Vec3 corner : cube.corners()) {
				Vec3 point = pose.inverseRotate(corner.add(offsets.getOrDefault(cube.cubeId(), Vec3.ZERO)));
				AABB pointBounds = new AABB(point, point);
				bounds = bounds == null ? pointBounds : bounds.minmax(pointBounds);
			}
		return bounds;
	}

	@SubscribeEvent
	static void renderGluePreview(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SurgicalGlueController.gluePreview == null)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		SurgicalGluePreviewPlanner.GluePreview preview = SurgicalGlueController.gluePreview;
		if (level == null
			|| !(level.getBlockEntity(preview.ownerPos()) instanceof SurgicalTableBlockEntity table))
			return;

		Vec3 camera = event.getCamera().getPosition();
		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
		int light = LevelRenderer.getLightColor(level, preview.ownerPos());
		float partialTicks = AnimationTickHolder.getPartialTicks();
		boolean projectSourceGeometry = SurgicalTableRenderer.projectsSourceGeometry(table);
		for (SurgicalGluePreviewPlanner.GlueSubjectPreview moved : preview.subjects()) {
			SurgicalSubject subject = table.getSubject(moved.subjectId);
			if (subject == null)
				continue;
			LivingEntity entity = SurgicalSourceModelRenderer.preview(subject.profile());
			if (entity == null)
				continue;
			poseStack.pushPose();
			poseStack.translate(preview.ownerPos().getX() - camera.x + subject.originOffsetX(),
				preview.ownerPos().getY() - camera.y,
				preview.ownerPos().getZ() - camera.z + subject.originOffsetZ());
			SurgicalTablePoseResolver.resolve(moved.pose).apply(poseStack);
			SurgicalSourceModelRenderer.render(entity, subject.cubeCount(), moved.cubes, moved.offsets,
				moved.rotations,
				poseStack, buffer, light, 0.0f, partialTicks, false, camera, projectSourceGeometry,
				moved.editable ? SurgicalTableClientHandler.GLUE_FIRST_PREVIEW_ALPHA : 1.0f, null, false);
			poseStack.popPose();
		}
		buffer.endBatch();
	}

	static boolean tryPlaceSubject(LocalPlayer player, ClientLevel level, InteractionHand hand,
		ItemStack held) {
		if (!SurgicalTableBlockEntity.isPlacementContainer(held)
			&& !hasPlacementCapture(held)
			|| !(Minecraft.getInstance().hitResult instanceof BlockHitResult hit)
			|| !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof SurgicalTableBlock))
			return false;
		PlacementCandidate candidate = placementCandidateFor(held, level);
		if (!candidate.result().succeeded()) {
			candidate.result().display(player);
			return true;
		}
		updatePlacementPreview();
		PlacementPreview placement = placementPreview;
		if (placement == null || placement.hand != hand
			|| !ItemStack.isSameItemSameComponents(placement.source.box, held)) {
			SurgicalTablePlacementResult result = placementPreviewResult.succeeded()
				? SurgicalTablePlacementResult.OPERATION_FAILED : placementPreviewResult;
			result.display(player);
			return true;
		}

		CBPackets.sendToServer(new SurgicalTablePlacementPacket(placement.ownerPos, hand, placement.facing,
			placement.plan.originOffsetX(), placement.plan.originOffsetZ(), placement.layPose,
			placement.plan.proposal(), placement.discovered.cubeCount(), placement.discovered.seams(),
			placement.discovered.anatomy(),
			placement.discovered.footprints(),
			placement.sourceLayouts, placement.source.preyModels == null ? List.of() : placement.source.preyModels.observations()));
		SurgicalVisualCommitCoordinator.beginVisualCommit(level, placement.ownerPos, placement.tableRevision, List.of(), true, false);
		return true;
	}


	record PlacementCandidate(ItemStack box, @Nullable PlacementSource source,
		SurgicalTablePlacementResult result) {
		PlacementCandidate {
			box = box.copyWithCount(1);
		}
	}

	record PlacementPreview(BlockPos ownerPos, InteractionHand hand, PlacementSource source,
		Direction facing, SurgicalLayPose layPose, SurgicalClientTopology.PlacementPlan plan,
		Map<Integer, Vec3> renderOffsets,
		List<SourcePlacementGeometry> sourceGeometries, DiscoveredPlacement discovered,
		List<SurgicalTableLayout.Proposal> sourceLayouts, boolean projectSourceGeometry,
		SurgicalTablePlane.WorkArea workArea, double targetX, double targetZ, int tableRevision,
		PlacementGeometry geometry, List<SurgicalTableLayout.Footprint> occupied) {
		PlacementPreview {
			renderOffsets = Map.copyOf(renderOffsets);
			sourceGeometries = List.copyOf(sourceGeometries);
			sourceLayouts = List.copyOf(sourceLayouts);
			occupied = List.copyOf(occupied);
		}

		private PlacementPreview withTarget(double x, double z) {
			return new PlacementPreview(ownerPos, hand, source, facing, layPose, plan, renderOffsets,
				sourceGeometries, discovered, sourceLayouts, projectSourceGeometry, workArea, x, z,
				tableRevision, geometry, occupied);
		}
	}

	record PlacementSuppression(InteractionHand hand, ItemStack sourceBox, long expiresAtTick) {
		PlacementSuppression {
			sourceBox = sourceBox.copy();
		}
	}

	record PlacementGeometry(AABB bounds, SurgicalLayPose layPose,
		Map<Integer, Vec3> cubeOffsets, List<SourcePlacementGeometry> sources,
		int discoveredCubeCount, List<SurgicalAssembly.Seam> discoveredSeams,
		List<SurgicalModelRenderContext.CubeGeometry> discoveredCubes, List<Vec3> groundPlane) {
		PlacementGeometry {
			cubeOffsets = Map.copyOf(cubeOffsets);
			sources = List.copyOf(sources);
			discoveredSeams = List.copyOf(discoveredSeams);
			discoveredCubes = List.copyOf(discoveredCubes);
			groundPlane = List.copyOf(groundPlane);
		}
	}

	record SourcePlacementGeometry(SurgicalAssembly.PlacedSource placedSource,
		List<SurgicalModelRenderContext.CubeGeometry> baseCubes,
		List<SurgicalModelRenderContext.CubeGeometry> unrotatedCubes, Map<Integer, Vec3> renderOffsets,
		Map<Integer, Vec3> previewOffsets,
		Map<Integer, SurgicalCubeRotation> renderRotations) {
		SourcePlacementGeometry {
			baseCubes = List.copyOf(baseCubes);
			unrotatedCubes = List.copyOf(unrotatedCubes);
			renderOffsets = Map.copyOf(renderOffsets);
			previewOffsets = Map.copyOf(previewOffsets);
			renderRotations = Map.copyOf(renderRotations);
		}
	}

	static final class PlacementSource {
		final ItemStack box;
		private final MimicProfile profile;
		@Nullable
		private SurgicalAssembly assembly;
		private final DevouredPlacementModels preyModels;
		@Nullable
		private LivingEntity preview;
		@Nullable
		private Direction measuredFacing;
		private boolean measuredSourceGeometry;
		private SurgicalLayPose.RotationAxis measuredGroundAxis;
		int groundOrientation;
		@Nullable
		private PlacementGeometry measuredGeometry;

		private PlacementSource(ItemStack box, MimicProfile profile, @Nullable SurgicalAssembly assembly) {
			this.box = box;
			this.profile = profile;
			this.assembly = assembly;
			this.preyModels = assembly == null ? null : new DevouredPlacementModels(assembly);
		}

		@Nullable
		private LivingEntity preview() {
			if (preview == null)
				preview = preview(profile);
			return preview;
		}

		@Nullable
		private LivingEntity preview(MimicProfile sourceProfile) {
			return SurgicalSourceModelRenderer.previewDeferred(sourceProfile);
		}

		private boolean isComposite() {
			return assembly != null && (assembly.preservesLayout() || assembly.sources().size() != 1);
		}

		private int subjectSlots() {
			return assembly != null && isComposite() ? assembly.sources().size() : 1;
		}

		private int measuredResourceGeneration = -1;
		private final List<SourcePlacementGeometry> preparingSources = new ArrayList<>();
		private List<SurgicalAssembly.PlacedSource> placedSources;
		private SurgicalClientJob<PlacementGeometry> measurementJob;

		@Nullable
		private PlacementGeometry measure(Direction facing, boolean projectSourceGeometry, @Nullable SurgicalLayPose.RotationAxis groundAxis) {
			int resources = SurgicalSourceModelRenderer.resourceGeneration();
			if (measuredFacing != facing || measuredSourceGeometry != projectSourceGeometry
				|| measuredGroundAxis != groundAxis || measuredResourceGeneration != resources) {
				if (measurementJob != null) measurementJob.cancel();
				measurementJob = null;
				measuredGeometry = null;
				preparingSources.clear();
				placedSources = null;
				measuredFacing = facing;
				measuredSourceGeometry = projectSourceGeometry;
				measuredGroundAxis = groundAxis;
				measuredResourceGeneration = resources;
			}
			if (preyModels != null) {
				var prepared = preyModels.prepare(resources);
				if (prepared == null) return null;
				assembly = prepared;
			}
			if (measuredGeometry != null) return measuredGeometry;
			if (measurementJob != null) return measuredGeometry = measurementJob.poll();
			if (isComposite()) return measureComposite(facing, groundAxis);
			LivingEntity entity = preview();
			if (entity == null) return null;
			// Prime the native plan before pose selection can enter its renderer measurement.
			if (SurgicalSourceModelRenderer.geometryDeferred(entity, 0, new BitSet(), Map.of(), Map.of(),
				new PoseStack(), 0, null) == null || !SurgicalPreparationBudget.begin(false)) return null;
			try {
				var resolved = SurgicalTablePoseResolver.resolve(this, profile, entity, facing, groundAxis);
				PoseStack poseStack = new PoseStack();
				resolved.apply(poseStack);
				var snapshot = SurgicalSourceModelRenderer.geometryDeferred(entity, cubeCount(), presentCubes(),
					Map.of(), Map.of(), poseStack, 0, null);
				if (snapshot == null || snapshot.cubes().isEmpty()) return null;
				measurementJob = new SurgicalClientJob<>("placementGeometry(async)",
					() -> finishSingleMeasurement(snapshot, resolved));
				return measurementJob.poll();
			} finally { SurgicalPreparationBudget.end(); }
		}

		private PlacementGeometry finishSingleMeasurement(SurgicalModelRenderContext.Snapshot snapshot,
			SurgicalTablePoseResolver.SurgicalPose resolved) {
			int discoveredCubeCount = 0;
			List<SurgicalAssembly.Seam> discoveredSeams = List.of();
			List<SurgicalModelRenderContext.CubeGeometry> discoveredCubes = List.of();
			if (assembly == null) {
				discoveredCubeCount = snapshot.observedCubeCount();
				if (!SurgicalAssembly.validCubeCount(discoveredCubeCount)
					|| snapshot.cubes().size() != discoveredCubeCount) return null;
				var topology = SurgicalClientTopology.buildContactTopology(discoveredCubeCount, snapshot.cubes());
				discoveredSeams = topology.seams();
				discoveredCubes = snapshot.cubes();
			}
			Map<Integer, Vec3> offsets = groundedOffsets(snapshot, discoveredCubeCount, discoveredSeams);
			AABB bounds = SurgicalSelectionResolver.logicalBounds(snapshot.cubes(), offsets);
			return bounds == null ? null : new PlacementGeometry(bounds, resolved.layPose(), offsets, List.of(),
				discoveredCubeCount, discoveredSeams, discoveredCubes, groundPlaneCorners(
					groundPlaneBounds(null, snapshot.cubes(), offsets, resolved.layPose()), resolved.layPose()));
		}

		@Nullable
		private PlacementGeometry measureComposite(Direction facing, @Nullable SurgicalLayPose.RotationAxis groundAxis) {
			SurgicalLayPose targetPose = groundAxis != null ? assembly.groundPlacementPose(facing, groundAxis)
				: assembly.placedLayPose(facing);
			if (placedSources == null) placedSources = assembly.placedSources(facing, targetPose);
			while (preparingSources.size() < placedSources.size()) {
				var placed = placedSources.get(preparingSources.size());
				var source = placed.source();
				LivingEntity entity = preview(source.profile());
				if (entity == null || !SurgicalPreparationBudget.begin(false)) return null;
				try {
					var snapshot = SurgicalSourceModelRenderer.geometryDeferred(entity, source.cubeCount(),
						source.presentCubes(), Map.of(), Map.of(), sourcePose(placed, entity), 0, null);
					if (snapshot == null) return null;
					if (snapshot.observedCubeCount() != source.cubeCount()
						|| snapshot.cubes().size() != source.presentCubes().cardinality()) return null;
					preparingSources.add(new SourcePlacementGeometry(placed,
						SurgicalGluePreviewPlanner.transformCubes(snapshot.cubes(), placed.cubeRotations(), Map.of()), snapshot.cubes(),
						placed.cubeOffsets(), placed.cubeOffsets(), placed.cubeRotations()));
				} finally { SurgicalPreparationBudget.end(); }
			}
			List<SourcePlacementGeometry> frozen = List.copyOf(preparingSources);
			measurementJob = new SurgicalClientJob<>("placementGeometry(async)",
				() -> finishCompositeMeasurement(assembly, targetPose, frozen));
			return measurementJob.poll();
		}

		private static PlacementGeometry finishCompositeMeasurement(SurgicalAssembly assembly,
			SurgicalLayPose targetPose, List<SourcePlacementGeometry> geometries) {
			List<SurgicalClientTopology.GroundingBody<Integer>> bodies = new ArrayList<>(geometries.size());
			for (int sourceId = 0; sourceId < geometries.size(); sourceId++) {
				SourcePlacementGeometry geometry = geometries.get(sourceId);
				SurgicalAssembly.Source source = geometry.placedSource().source();
				bodies.add(new SurgicalClientTopology.GroundingBody<>(sourceId, source.cubeCount(),
					source.presentCubes(), source.seams(), source.cutSeams(), geometry.baseCubes(),
					geometry.renderOffsets()));
			}
			List<SurgicalClientTopology.GroundingLink<Integer>> links = new ArrayList<>(assembly.joints().stream()
				.map(joint -> new SurgicalClientTopology.GroundingLink<>(joint.firstSource(), joint.firstCube(),
					joint.secondSource(), joint.secondCube()))
				.toList());
			for (SurgicalAssembly.Combination combination : assembly.combinations()) {
				SurgicalAssembly.CombinationMember anchor = combination.members().getFirst();
				for (SurgicalAssembly.CombinationMember member : combination.members().subList(1,
					combination.members().size()))
					links.add(new SurgicalClientTopology.GroundingLink<>(anchor.source(), anchor.cube(),
						member.source(), member.cube()));
			}
			Map<Integer, Map<Integer, Vec3>> grounded = SurgicalClientTopology.groundConnectedBodies(
				bodies, links, SurgicalTablePlane.SURFACE_HEIGHT + SurgicalTablePoseResolver.TABLE_CLEARANCE);
			if (grounded == null || grounded.size() != geometries.size())
				return null;
			List<SourcePlacementGeometry> previewGeometries = new ArrayList<>(geometries.size());
			for (int sourceId = 0; sourceId < geometries.size(); sourceId++) {
				SourcePlacementGeometry geometry = geometries.get(sourceId);
				Map<Integer, Vec3> previewOffsets = grounded.get(sourceId);
				if (previewOffsets == null)
					return null;
				previewGeometries.add(new SourcePlacementGeometry(geometry.placedSource(), geometry.baseCubes(), geometry.unrotatedCubes(),
					geometry.renderOffsets(), previewOffsets, geometry.renderRotations()));
			}
			AABB bounds = null;
			for (SourcePlacementGeometry geometry : previewGeometries) {
				AABB sourceBounds = SurgicalSelectionResolver.logicalBounds(geometry.baseCubes(), geometry.previewOffsets());
				if (sourceBounds == null)
					return null;
				bounds = bounds == null ? sourceBounds : bounds.minmax(sourceBounds);
			}
			if (bounds == null)
				return null;
			AABB uprightBounds = null;
			for (SourcePlacementGeometry geometry : previewGeometries)
				uprightBounds = groundPlaneBounds(uprightBounds, geometry.baseCubes(), geometry.previewOffsets(), targetPose);
			return new PlacementGeometry(bounds, targetPose, Map.of(),
				previewGeometries, 0, List.of(), List.of(), groundPlaneCorners(uprightBounds, targetPose));
		}

		private PoseStack sourcePose(SurgicalAssembly.PlacedSource placedSource, LivingEntity entity) {
			SurgicalAssembly.Source source = placedSource.source();
			PoseStack poseStack = new PoseStack();
			poseStack.translate(placedSource.originOffset().x, placedSource.originOffset().y,
				placedSource.originOffset().z);
			SurgicalTablePoseResolver.resolve(placedSource.layPose()).apply(poseStack);
			return poseStack;
		}

		private Map<Integer, Vec3> groundedOffsets(SurgicalModelRenderContext.Snapshot snapshot,
			int discoveredCubeCount, List<SurgicalAssembly.Seam> discoveredSeams) {
			int groundedCubeCount = assembly == null ? discoveredCubeCount : assembly.cubeCount();
			if (snapshot.observedCubeCount() != groundedCubeCount)
				return Map.of();
			BitSet groundedCubes;
			List<SurgicalAssembly.Seam> groundedSeams;
			BitSet groundedCuts;
			if (assembly == null) {
				groundedCubes = new BitSet(groundedCubeCount);
				groundedCubes.set(0, groundedCubeCount);
				groundedSeams = discoveredSeams;
				groundedCuts = new BitSet();
			} else {
				groundedCubes = assembly.presentCubes();
				groundedSeams = assembly.seams();
				groundedCuts = assembly.cutSeams();
			}
			// The committed subject always grounds every resulting component, including an uncut one.
			// Apply that same correction to the hand-held preview so renderers whose component bounds do
			// not share the complete renderer's minimum Y (notably slimes) do not jump after placement.
			return SurgicalClientTopology.groundAllComponents(groundedCubeCount, groundedCubes,
				groundedSeams, groundedCuts, snapshot.cubes(), Map.of(),
				SurgicalTablePlane.SURFACE_HEIGHT + SurgicalTablePoseResolver.TABLE_CLEARANCE);
		}

		void cancel() {
			if (measurementJob != null) measurementJob.cancel();
			if (preyModels != null) preyModels.cancel();
		}

		private int cubeCount() {
			return assembly == null ? 0 : assembly.cubeCount();
		}

		private BitSet presentCubes() {
			return assembly == null ? new BitSet() : assembly.presentCubes();
		}
	}


	static BionicAnatomySnapshot discoveredAnatomy(
		List<SurgicalModelRenderContext.CubeGeometry> cubes, int cubeCount) {
		Map<Integer, java.util.Set<String>> parts = new HashMap<>();
		Map<Integer, java.util.Set<com.nobodiiiii.createbiotech.entity.trait.BionicModelNode>> nodes = new HashMap<>();
		float[] volumes = new float[cubeCount];
		BitSet measured = new BitSet(cubeCount);
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes) {
			if (!cube.partNames().isEmpty())
				parts.put(cube.cubeId(), cube.partNames());
			if (!cube.nodes().isEmpty()) nodes.put(cube.cubeId(), cube.nodes());
			if (cube.cubeId() >= 0 && cube.cubeId() < cubeCount) {
				volumes[cube.cubeId()] = (float) SurgicalVolumeSampler.cubeVolume(cube.corners());
				measured.set(cube.cubeId());
			}
		}
		BionicAnatomySnapshot snapshot = BionicAnatomySnapshot.ofParts(parts, nodes, cubeCount);
		if (snapshot == null)
			return BionicAnatomySnapshot.EMPTY;
		// Trait coverage is volume weighted; a partial measurement leaves the body unmeasured.
		BionicAnatomySnapshot weighted = measured.cardinality() == cubeCount
			? snapshot.withVolumes(volumes) : null;
		return weighted == null ? snapshot : weighted;
	}

	record DiscoveredPlacement(int cubeCount, List<SurgicalAssembly.Seam> seams,
		BionicAnatomySnapshot anatomy,
		List<SurgicalTableLayout.Footprint> footprints) {
		private static final DiscoveredPlacement EMPTY =
			new DiscoveredPlacement(0, List.of(), BionicAnatomySnapshot.EMPTY, List.of());

		DiscoveredPlacement {
			seams = List.copyOf(seams);
			footprints = List.copyOf(footprints);
		}
	}
}
