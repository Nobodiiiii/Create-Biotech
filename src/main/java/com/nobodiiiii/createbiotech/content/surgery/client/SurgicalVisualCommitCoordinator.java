package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalProfiler;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalSubject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalVisualCommitCoordinator implementation; public entry points remain on SurgicalTableClientHandler. */
final class SurgicalVisualCommitCoordinator {
	private SurgicalVisualCommitCoordinator() {}

	@Nullable
	static PendingVisualCommit pendingVisualCommit;

	static boolean retainsPlacementPreview() {
		return pendingVisualCommit != null && pendingVisualCommit.retainPlacementPreview;
	}

	static boolean retainsGluePreview() {
		return pendingVisualCommit != null && pendingVisualCommit.retainGluePreview;
	}

	static void beginVisualCommit(ClientLevel level, BlockPos tablePos, int tableRevision,
		List<Integer> geometrySubjectIds, boolean retainPlacementPreview, boolean retainGluePreview) {
		cancelPendingVisualCommit(level, true);
		int currentRevision = level.getBlockEntity(tablePos) instanceof SurgicalTableBlockEntity table
			? table.clientDataRevision() : tableRevision;
		pendingVisualCommit = new PendingVisualCommit(tablePos, currentRevision,
			level.getGameTime() + SurgicalTableClientHandler.VISUAL_COMMIT_TIMEOUT_TICKS, geometrySubjectIds,
			retainPlacementPreview, retainGluePreview);
	}

	static void updatePendingVisualCommit(ClientLevel level) {
		PendingVisualCommit commit = pendingVisualCommit;
		if (commit == null)
			return;
		SurgicalTableBlockEntity table = level.getBlockEntity(commit.tablePos)
			instanceof SurgicalTableBlockEntity found ? found : null;
		if (table != null && table.clientDataRevision() != commit.tableRevision) {
			if (commit.retainPlacementPreview)
				commit.serverAcknowledged = true;
			else {
				cancelPendingVisualCommit(level, false);
				return;
			}
		}
		if (table == null || level.getGameTime() >= commit.expiresAtTick)
			cancelPendingVisualCommit(level, !commit.serverAcknowledged);
	}

	/** Reuses the accepted preview's neutral geometry; identity and edits remain server-owned. */
	static boolean seedPlacementGeometry(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		PendingVisualCommit commit = pendingVisualCommit;
		if (commit == null || !commit.serverAcknowledged || !commit.tablePos.equals(table.getBlockPos())
			|| commit.placementResources != SurgicalSourceModelRenderer.resourceGeneration()
			|| commit.placementBaselineSubjectIds.contains(subject.id())) return false;
		PlacementSeed seed = commit.placementSeeds.get(new PlacementSeedKey(subject.profile(), subject.layPose(),
			subject.cubeCount(), subject.presentCubes(), subject.originOffsetX(), subject.originOffsetZ()));
		if (seed == null || !subject.matchesObservedTopology(seed.source().cubeCount(), seed.source().seams())
			|| !subject.cutSeamsForRender().equals(seed.source().cutSeams())
			|| !subject.componentRotationsForRender().equals(seed.rotations())) return false;
		if (!SurgicalPreparationBudget.begin(false)) return false;
		try {
			SurgicalGeometryStore.updateGeometry(table, subject, new SurgicalModelRenderContext.Snapshot(subject.cubeCount(),
				SurgicalSelectionResolver.translateCubes(seed.cubes(), Vec3.atLowerCornerOf(table.getBlockPos()))));
			SurgicalProfiler.count("placementGeometry(handoff)");
			return true;
		} finally { SurgicalPreparationBudget.end(); }
	}

	record PlacementSeedKey(MimicProfile profile, SurgicalLayPose pose, int count, BitSet present,
		double x, double z) {}
	record PlacementSeed(SurgicalAssembly.Source source, List<SurgicalModelRenderContext.CubeGeometry> cubes,
		Map<Integer, SurgicalCubeRotation> rotations) {}

	/** Switches from the placement preview only after every newly added subject is render-ready. */
	static void completePlacementHandoffIfReady(SurgicalTableBlockEntity table) {
		PendingVisualCommit commit = pendingVisualCommit;
		if (commit == null || !commit.retainPlacementPreview || !commit.serverAcknowledged
			|| !commit.tablePos.equals(table.getBlockPos()))
			return;
		boolean foundAddedSubject = false;
		for (SurgicalSubject subject : table.getSubjects()) {
			if (commit.placementBaselineSubjectIds.contains(subject.id()))
				continue;
			foundAddedSubject = true;
			SurgicalGeometryStore.TableGeometry geometry = SurgicalGeometryStore.TABLES.get(new SurgicalGeometryStore.SubjectKey(commit.tablePos, subject.id()));
			if (!SurgicalGeometryStore.geometryReadyForUse(table, subject, geometry))
				return;
		}
		if (foundAddedSubject && table.getLevel() instanceof ClientLevel level)
			cancelPendingVisualCommit(level, false);
	}

	static boolean suppressForPlacementHandoff(SurgicalTableBlockEntity table, SurgicalSubject subject) {
		PendingVisualCommit commit = pendingVisualCommit;
		return commit != null && commit.retainPlacementPreview && commit.serverAcknowledged
			&& commit.tablePos.equals(table.getBlockPos())
			&& !commit.placementBaselineSubjectIds.contains(subject.id());
	}

	static void cancelPendingVisualCommit(@Nullable ClientLevel level, boolean rollbackGeometry) {
		PendingVisualCommit commit = pendingVisualCommit;
		pendingVisualCommit = null;
		if (commit == null)
			return;
		// Both rollback and acknowledgement must retire the preview transforms. On acknowledgement the
		// table already contains the authoritative server state; clearing here keeps a subject disconnected
		// by the edit from retaining an old pending grounding request outside the post-edit refresh group.
		if (level != null
			&& level.getBlockEntity(commit.tablePos) instanceof SurgicalTableBlockEntity table) {
			SurgicalCutController.clearGeometryPreviews(table, commit.tablePos, commit.geometrySubjectIds);
		}
		SurgicalPlacementController.PlacementPreview committedPlacement = commit.retainPlacementPreview ? SurgicalPlacementController.placementPreview : null;
		if (!rollbackGeometry && committedPlacement != null && level != null)
			SurgicalPlacementController.placementSuppression = new SurgicalPlacementController.PlacementSuppression(committedPlacement.hand(),
				committedPlacement.source().box.copy(), level.getGameTime() + SurgicalTableClientHandler.VISUAL_COMMIT_TIMEOUT_TICKS);
		if (commit.retainPlacementPreview)
			SurgicalPlacementController.clearPlacementPreview();
		if (commit.retainGluePreview)
			SurgicalGlueController.gluePreview = null;
		SurgicalGeometryStore.geometryGeneration++;
	}

	static boolean glueTopologyKnown(SurgicalSubject first, SurgicalSubject second) {
		return first.cubeCount() != 0 && second.cubeCount() != 0;
	}

	static boolean validEditedVertical(BitSet cubes,
		List<SurgicalModelRenderContext.CubeGeometry> rotatedBase, Map<Integer, Vec3> offsets, double surfaceY) {
		double range = SurgicalTablePlane.MAX_TILES + 2.0d;
		double minimumY = surfaceY - range;
		double maximumY = surfaceY + range;
		for (SurgicalModelRenderContext.CubeGeometry cube : rotatedBase) {
			if (!cubes.get(cube.cubeId()))
				continue;
			Vec3 offset = offsets.getOrDefault(cube.cubeId(), Vec3.ZERO);
			for (Vec3 corner : cube.corners()) {
				double y = corner.y + offset.y;
				if (!Double.isFinite(y) || y < minimumY || y > maximumY)
					return false;
			}
		}
		return true;
	}

	static double lowestY(SurgicalModelRenderContext.CubeGeometry geometry) {
		double lowest = Double.POSITIVE_INFINITY;
		for (Vec3 corner : geometry.corners())
			lowest = Math.min(lowest, corner.y);
		return lowest;
	}

	static boolean componentMapsIntersect(Map<Integer, BitSet> first, Map<Integer, BitSet> second) {
		for (Map.Entry<Integer, BitSet> entry : first.entrySet()) {
			BitSet other = second.get(entry.getKey());
			if (other != null && entry.getValue().intersects(other))
				return true;
		}
		return false;
	}

	static final class PendingVisualCommit {
		private final BlockPos tablePos;
		private final int tableRevision;
		private final long expiresAtTick;
		private final List<Integer> geometrySubjectIds;
		private final List<Integer> placementBaselineSubjectIds;
		private final boolean retainPlacementPreview;
		private final boolean retainGluePreview;
		private boolean serverAcknowledged;
		private final int placementResources = SurgicalSourceModelRenderer.resourceGeneration();
		private final Map<PlacementSeedKey, PlacementSeed> placementSeeds = new HashMap<>();

		private PendingVisualCommit(BlockPos tablePos, int tableRevision, long expiresAtTick,
			List<Integer> geometrySubjectIds, boolean retainPlacementPreview, boolean retainGluePreview) {
			this.tablePos = tablePos.immutable();
			this.tableRevision = tableRevision;
			this.expiresAtTick = expiresAtTick;
			this.geometrySubjectIds = List.copyOf(geometrySubjectIds);
			Minecraft minecraft = Minecraft.getInstance();
			this.placementBaselineSubjectIds = retainPlacementPreview && minecraft.level != null
				&& minecraft.level.getBlockEntity(tablePos) instanceof SurgicalTableBlockEntity table
				? table.getSubjects().stream().map(SurgicalSubject::id).toList() : List.of();
			this.retainPlacementPreview = retainPlacementPreview;
			this.retainGluePreview = retainGluePreview;
			SurgicalPlacementController.PlacementPreview placement = retainPlacementPreview ? SurgicalPlacementController.placementPreview : null;
			if (placement != null) for (var geometry : placement.sourceGeometries()) {
				var placed = geometry.placedSource();
				var source = placed.source();
				double x = placement.plan().originOffsetX() + placed.originOffset().x;
				double z = placement.plan().originOffsetZ() + placed.originOffset().z;
				var key = new PlacementSeedKey(source.profile(), placed.layPose(), source.cubeCount(), source.presentCubes(), x, z);
				var cubes = SurgicalSelectionResolver.translateCubes(geometry.unrotatedCubes(), new Vec3(placement.plan().originOffsetX(),
					-placed.originOffset().y, placement.plan().originOffsetZ()));
				placementSeeds.put(key, new PlacementSeed(source, cubes, geometry.renderRotations()));
			}
		}
	}
}
