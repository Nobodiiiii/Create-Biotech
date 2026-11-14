package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalPlacementSearch implementation; public entry points remain on SurgicalClientTopology. */
final class SurgicalPlacementSearch {
	private SurgicalPlacementSearch() {}

	static final Map<SurgicalTablePlane.WorkArea, SurgicalClientTopology.GridCell[]> GRID_CELLS =
		Collections.synchronizedMap(new WeakHashMap<>());

	/** Moves a multi-subject glued group as one rigid horizontal unit to the nearest legal slot. */
	@Nullable
	static SurgicalClientTopology.ConnectedPlacement snapConnectedGroup(List<SurgicalTableLayout.Footprint> movingFootprints,
		SurgicalTablePlane.WorkArea workArea, double targetX, double targetZ,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		if (movingFootprints.isEmpty() || workArea.isEmpty()
			|| !Double.isFinite(targetX) || !Double.isFinite(targetZ))
			return null;
		double minX = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (SurgicalTableLayout.Footprint footprint : movingFootprints) {
			if (!SurgicalTableLayout.validStoredFootprint(footprint))
				return null;
			minX = Math.min(minX, footprint.minX());
			minZ = Math.min(minZ, footprint.minZ());
			maxX = Math.max(maxX, footprint.maxX());
			maxZ = Math.max(maxZ, footprint.maxZ());
		}
		double centerX = (minX + maxX) * 0.5d;
		double centerZ = (minZ + maxZ) * 0.5d;
		for (SurgicalClientTopology.GridCell cell : orderedCells(workArea, targetX, targetZ)) {
			double deltaX = cell.centerX() - centerX;
			double deltaZ = cell.centerZ() - centerZ;
			if (!workArea.contains(minX + deltaX, minZ + deltaZ,
				maxX + deltaX, maxZ + deltaZ, SurgicalClientTopology.DISTANCE_EPSILON))
				continue;
			boolean blocked = false;
			for (SurgicalTableLayout.Footprint footprint : movingFootprints) {
				SurgicalTableLayout.Footprint placed = new SurgicalTableLayout.Footprint(
					footprint.componentRoot(), footprint.minX() + deltaX, footprint.minZ() + deltaZ,
					footprint.maxX() + deltaX, footprint.maxZ() + deltaZ,
					SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED);
				if (overlapsAny(placed, occupiedFootprints)) {
					blocked = true;
					break;
				}
			}
			if (!blocked)
				return new SurgicalClientTopology.ConnectedPlacement(new Vec3(deltaX, 0.0d, deltaZ));
		}
		return null;
	}

	/**
	 * Places a newly captured mimic whose model already contains multiple disconnected components.
	 * The complete model envelope still determines its snapped anchor and table fit, while collision
	 * checks use one tight envelope per native component so empty space between them stays available.
	 */
	@Nullable
	static SurgicalClientTopology.DiscoveredPlacementPlan planDiscoveredPlacement(int cubeCount,
		List<SurgicalAssembly.Seam> seams, SurgicalModelRenderContext.CubeGeometry envelopeGeometry,
		List<SurgicalModelRenderContext.CubeGeometry> cubes,
		SurgicalTablePlane.WorkArea workArea, double targetX, double targetZ,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		if (!SurgicalAssembly.validTopology(cubeCount, seams) || workArea.isEmpty()
			|| envelopeGeometry == null || occupiedFootprints == null)
			return null;
		BitSet present = new BitSet(cubeCount);
		present.set(0, cubeCount);
		Map<Integer, SurgicalClientTopology.Bounds> cubeBounds = layoutBounds(present, cubes);
		if (cubeBounds.size() != cubeCount)
			return null;
		List<BitSet> components = SurgicalAssembly.components(cubeCount, present, seams, new BitSet());
		if (components.isEmpty())
			return null;
		List<Integer> componentRoots = new ArrayList<>(components.size());
		List<SurgicalClientTopology.Bounds> componentBounds = new ArrayList<>(components.size());
		for (BitSet component : components) {
			SurgicalClientTopology.Bounds bounds = SurgicalClientTopology.unionBounds(component, cubeBounds, Map.of());
			if (bounds == null)
				return null;
			componentRoots.add(component.nextSetBit(0));
			componentBounds.add(bounds);
		}
		SurgicalClientTopology.Bounds envelope = SurgicalClientTopology.Bounds.of(envelopeGeometry);
		if (envelope == null)
			return null;

		for (SurgicalClientTopology.GridCell cell : orderedCells(workArea, targetX, targetZ)) {
			Vec3 delta = new Vec3(cell.centerX() - envelope.centerX(), 0.0d,
				cell.centerZ() - envelope.centerZ());
			SurgicalClientTopology.Bounds placedEnvelope = envelope.translate(delta);
			if (!fits(workArea, placedEnvelope))
				continue;
			List<SurgicalTableLayout.Footprint> componentFootprints = new ArrayList<>(components.size());
			boolean blocked = false;
			for (int componentId = 0; componentId < componentBounds.size(); componentId++) {
				SurgicalTableLayout.Footprint footprint = SurgicalClientTopology.footprint(componentRoots.get(componentId),
					componentBounds.get(componentId).translate(delta), null);
				if (overlapsAny(footprint, occupiedFootprints)) {
					blocked = true;
					break;
				}
				componentFootprints.add(footprint);
			}
			if (blocked)
				continue;
			SurgicalTableLayout.Footprint envelopeFootprint = SurgicalClientTopology.footprint(-1, placedEnvelope, cell);
			SurgicalClientTopology.PlacementPlan placement = new SurgicalClientTopology.PlacementPlan(delta.x, delta.z,
				new SurgicalTableLayout.Proposal(List.of(), List.of(envelopeFootprint)));
			return new SurgicalClientTopology.DiscoveredPlacementPlan(placement, componentFootprints);
		}
		return null;
	}

	/**
	 * Places every connected group produced by one batch cut as a whole. Group zero is the stable,
	 * largest remainder. Unlike the component-at-a-time layout path, this searches the complete
	 * arrangement and backtracks when a tempting nearby slot would strand a later group.
	 */
	@Nullable
	static SurgicalClientTopology.ConnectedGroupLayout autoSnapConnectedGroups(
		List<List<SurgicalTableLayout.Footprint>> groups, SurgicalTablePlane.WorkArea workArea,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		if (groups == null || groups.isEmpty() || groups.size() > SurgicalAssembly.MAX_CUBES
			|| workArea.isEmpty() || occupiedFootprints == null)
			return null;
		for (List<SurgicalTableLayout.Footprint> group : groups)
			if (group == null || group.isEmpty()
				|| group.stream().anyMatch(footprint -> !SurgicalTableLayout.validStoredFootprint(footprint)))
				return null;
		if (occupiedFootprints.stream()
			.anyMatch(footprint -> !SurgicalTableLayout.validStoredFootprint(footprint))
			|| !footprintsFit(workArea, groups.getFirst())
			|| footprintsCollide(groups.getFirst(), occupiedFootprints, SurgicalClientTopology.BATCH_CUT_CLEARANCE))
			return null;

		List<Vec3> stationary = new ArrayList<>(groups.size());
		for (int group = 0; group < groups.size(); group++)
			stationary.add(Vec3.ZERO);
		if (groups.size() == 1)
			return new SurgicalClientTopology.ConnectedGroupLayout(stationary);

		return searchConnectedGroupLayout(groups, workArea, occupiedFootprints,
			SurgicalClientTopology.BATCH_CUT_CLEARANCE, SurgicalClientTopology.BATCH_CUT_MIN_TRAVEL);
	}

	@Nullable
	static SurgicalClientTopology.ConnectedGroupLayout searchConnectedGroupLayout(
		List<List<SurgicalTableLayout.Footprint>> groups, SurgicalTablePlane.WorkArea workArea,
		List<SurgicalTableLayout.Footprint> occupiedFootprints, double clearance, double minimumTravel) {
		List<SurgicalTableLayout.Footprint> permanent = new ArrayList<>(occupiedFootprints.size()
			+ groups.getFirst().size());
		permanent.addAll(occupiedFootprints);
		permanent.addAll(groups.getFirst());
		List<SurgicalClientTopology.BatchPlacementRequest> requests = new ArrayList<>(groups.size() - 1);
		double minimumTravelSqr = minimumTravel * minimumTravel;
		for (int groupId = 1; groupId < groups.size(); groupId++) {
			List<SurgicalTableLayout.Footprint> footprints = groups.get(groupId);
			double minX = footprints.stream().mapToDouble(SurgicalTableLayout.Footprint::minX).min()
				.orElse(Double.NaN);
			double minZ = footprints.stream().mapToDouble(SurgicalTableLayout.Footprint::minZ).min()
				.orElse(Double.NaN);
			double maxX = footprints.stream().mapToDouble(SurgicalTableLayout.Footprint::maxX).max()
				.orElse(Double.NaN);
			double maxZ = footprints.stream().mapToDouble(SurgicalTableLayout.Footprint::maxZ).max()
				.orElse(Double.NaN);
			if (!Double.isFinite(minX) || !Double.isFinite(minZ)
				|| !Double.isFinite(maxX) || !Double.isFinite(maxZ))
				return null;
			double centerX = (minX + maxX) * 0.5d;
			double centerZ = (minZ + maxZ) * 0.5d;
			List<SurgicalClientTopology.BatchPlacementCandidate> candidates = new ArrayList<>();
			for (SurgicalClientTopology.GridCell cell : orderedCells(workArea, centerX, centerZ)) {
				Vec3 delta = new Vec3(cell.centerX() - centerX, 0.0d, cell.centerZ() - centerZ);
				if (delta.horizontalDistanceSqr() + SurgicalClientTopology.DISTANCE_EPSILON < minimumTravelSqr)
					continue;
				List<SurgicalTableLayout.Footprint> translated = translateFootprints(footprints, delta);
				if (!footprintsFit(workArea, translated)
					|| footprintsCollide(translated, permanent, clearance))
					continue;
				candidates.add(new SurgicalClientTopology.BatchPlacementCandidate(delta, translated));
			}
			if (candidates.isEmpty())
				return null;
			double area = Math.max(0.0d, maxX - minX) * Math.max(0.0d, maxZ - minZ);
			requests.add(new SurgicalClientTopology.BatchPlacementRequest(groupId, area, List.copyOf(candidates)));
		}
		// Most-constrained and then physically largest groups go first. Candidate order itself remains
		// nearest-first, so the first complete arrangement stays compact and deterministic.
		requests.sort(Comparator.comparingInt((SurgicalClientTopology.BatchPlacementRequest request) -> request.candidates().size())
			.thenComparing(Comparator.comparingDouble(SurgicalClientTopology.BatchPlacementRequest::area).reversed())
			.thenComparingInt(SurgicalClientTopology.BatchPlacementRequest::groupId));
		List<Vec3> deltas = new ArrayList<>(groups.size());
		for (int group = 0; group < groups.size(); group++)
			deltas.add(Vec3.ZERO);
		List<SurgicalTableLayout.Footprint> placed = new ArrayList<>(permanent);
		int[] examined = { 0 };
		if (!placeConnectedGroups(requests, 0, placed, deltas, clearance, examined))
			return null;
		return new SurgicalClientTopology.ConnectedGroupLayout(deltas);
	}

	static boolean placeConnectedGroups(List<SurgicalClientTopology.BatchPlacementRequest> requests, int requestIndex,
		List<SurgicalTableLayout.Footprint> placed, List<Vec3> deltas, double clearance, int[] examined) {
		if (requestIndex >= requests.size())
			return true;
		if (examined[0] >= SurgicalClientTopology.MAX_BATCH_LAYOUT_SEARCH_NODES)
			return false;
		SurgicalClientTopology.BatchPlacementRequest request = requests.get(requestIndex);
		for (SurgicalClientTopology.BatchPlacementCandidate candidate : request.candidates()) {
			if (++examined[0] > SurgicalClientTopology.MAX_BATCH_LAYOUT_SEARCH_NODES)
				return false;
			if (footprintsCollide(candidate.footprints(), placed, clearance))
				continue;
			int previousSize = placed.size();
			placed.addAll(candidate.footprints());
			deltas.set(request.groupId(), candidate.delta());
			if (placeConnectedGroups(requests, requestIndex + 1, placed, deltas, clearance, examined))
				return true;
			placed.subList(previousSize, placed.size()).clear();
			deltas.set(request.groupId(), Vec3.ZERO);
		}
		return false;
	}

	static List<SurgicalTableLayout.Footprint> translateFootprints(
		List<SurgicalTableLayout.Footprint> footprints, Vec3 delta) {
		return footprints.stream().map(footprint -> new SurgicalTableLayout.Footprint(
			footprint.componentRoot(), footprint.minX() + delta.x, footprint.minZ() + delta.z,
			footprint.maxX() + delta.x, footprint.maxZ() + delta.z,
			SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED)).toList();
	}

	static boolean footprintsFit(SurgicalTablePlane.WorkArea workArea,
		List<SurgicalTableLayout.Footprint> footprints) {
		for (SurgicalTableLayout.Footprint footprint : footprints)
			if (!workArea.contains(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ(),
				SurgicalClientTopology.DISTANCE_EPSILON))
				return false;
		return true;
	}

	static boolean footprintsCollide(List<SurgicalTableLayout.Footprint> first,
		List<SurgicalTableLayout.Footprint> second, double clearance) {
		for (SurgicalTableLayout.Footprint candidate : first)
			for (SurgicalTableLayout.Footprint obstacle : second)
				if (candidate.minX() < obstacle.maxX() + clearance - SurgicalClientTopology.DISTANCE_EPSILON
					&& candidate.maxX() > obstacle.minX() - clearance + SurgicalClientTopology.DISTANCE_EPSILON
					&& candidate.minZ() < obstacle.maxZ() + clearance - SurgicalClientTopology.DISTANCE_EPSILON
					&& candidate.maxZ() > obstacle.minZ() - clearance + SurgicalClientTopology.DISTANCE_EPSILON)
					return true;
		return false;
	}

	/** Builds a proposal for a cut that does not create a new detached component. */
	@Nullable
	static SurgicalClientTopology.PlannedLayout currentLayout(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet proposedCuts,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return SurgicalGroundingSolver.planSnappedLayout(cubeCount, presentCubes, seams, proposedCuts, cubes, currentOffsets,
			workArea, List.of(), occupiedFootprints, false, false);
	}

	/** Preserves the exact overlapping layout of one source in a packed glued assembly. */
	@Nullable
	static SurgicalClientTopology.PlannedLayout preserveCompositeLayout(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return SurgicalGroundingSolver.planSnappedLayout(cubeCount, presentCubes, seams, cutSeams, cubes, currentOffsets,
			workArea, List.of(), occupiedFootprints, true, false);
	}

	/** Snaps one detached component to the legal slot nearest the supplied world-space target. */
	@Nullable
	static SurgicalClientTopology.PlannedLayout snapComponent(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet proposedCuts,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, BitSet movingComponent, double targetX, double targetZ,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return SurgicalGroundingSolver.planSnappedLayout(cubeCount, presentCubes, seams, proposedCuts, cubes, currentOffsets,
			workArea, List.of(new SurgicalClientTopology.SnapRequest((BitSet) movingComponent.clone(), targetX, targetZ)),
			occupiedFootprints, false, true);
	}

	/**
	 * Sequentially places every newly detached batch-cut component at its nearest legal slot.
	 * Vertical grounding is left to the caller because retained glue can join these native
	 * components to bodies whose geometry lives in another subject.
	 */
	@Nullable
	static SurgicalClientTopology.PlannedLayout autoSnapComponents(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet proposedCuts,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, List<BitSet> movingComponents,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		Map<Integer, SurgicalClientTopology.Bounds> baseBounds = layoutBounds(presentCubes, cubes);
		if (baseBounds.size() < presentCubes.cardinality())
			return null;
		List<SurgicalClientTopology.SnapRequest> requests = new ArrayList<>(movingComponents.size());
		for (BitSet component : movingComponents) {
			SurgicalClientTopology.Bounds bounds = SurgicalClientTopology.unionBounds(component, baseBounds, currentOffsets);
			if (bounds == null)
				return null;
			requests.add(new SurgicalClientTopology.SnapRequest((BitSet) component.clone(), bounds.centerX(), bounds.centerZ()));
		}
		return SurgicalGroundingSolver.planSnappedLayout(cubeCount, presentCubes, seams, proposedCuts, cubes, currentOffsets,
			workArea, requests, occupiedFootprints, false, false);
	}

	/** Applies one rigid horizontal delta to a union of complete components and rebuilds its layout. */
	@Nullable
	static SurgicalClientTopology.PlannedLayout translateComponents(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet proposedCuts,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, BitSet translatedCubes, Vec3 delta,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		if (!SurgicalAssembly.validTopology(cubeCount, seams) || workArea.isEmpty()
			|| translatedCubes == null || delta == null || occupiedFootprints == null
			|| !Double.isFinite(delta.x) || !Double.isFinite(delta.y) || !Double.isFinite(delta.z)
			|| Math.abs(delta.y) > SurgicalClientTopology.DISTANCE_EPSILON)
			return null;
		Map<Integer, SurgicalClientTopology.Bounds> baseBounds = layoutBounds(presentCubes, cubes);
		if (baseBounds.size() < presentCubes.cardinality())
			return null;
		List<BitSet> components = SurgicalAssembly.components(cubeCount, presentCubes, seams, proposedCuts);
		BitSet covered = new BitSet(cubeCount);
		for (BitSet component : components) {
			BitSet intersection = (BitSet) component.clone();
			intersection.and(translatedCubes);
			if (!intersection.isEmpty() && !intersection.equals(component))
				return null;
			covered.or(intersection);
		}
		BitSet outside = (BitSet) translatedCubes.clone();
		outside.andNot(presentCubes);
		if (!outside.isEmpty() || !covered.equals(translatedCubes))
			return null;

		Map<Integer, Vec3> plannedOffsets = new HashMap<>();
		for (int cube = presentCubes.nextSetBit(0); cube >= 0; cube = presentCubes.nextSetBit(cube + 1)) {
			Vec3 offset = currentOffsets.getOrDefault(cube, Vec3.ZERO);
			plannedOffsets.put(cube, translatedCubes.get(cube) ? offset.add(delta) : offset);
		}
		List<SurgicalTableLayout.Footprint> footprints = new ArrayList<>(presentCubes.cardinality());
		for (BitSet component : components) {
			int root = component.nextSetBit(0);
			SurgicalClientTopology.Bounds bounds = SurgicalClientTopology.unionBounds(component, baseBounds, plannedOffsets);
			if (bounds == null || !fits(workArea, bounds))
				return null;
			footprints.addAll(SurgicalClientTopology.footprints(root, component, baseBounds, plannedOffsets, null));
		}
		for (SurgicalTableLayout.Footprint footprint : footprints)
			if (overlapsAny(footprint, occupiedFootprints))
				return null;

		List<SurgicalTableLayout.CubeOffset> offsets = new ArrayList<>(presentCubes.cardinality());
		for (int cube = presentCubes.nextSetBit(0); cube >= 0; cube = presentCubes.nextSetBit(cube + 1)) {
			Vec3 offset = plannedOffsets.getOrDefault(cube, Vec3.ZERO);
			offsets.add(new SurgicalTableLayout.CubeOffset(cube, offset.x, offset.y, offset.z));
		}
		return new SurgicalClientTopology.PlannedLayout(Map.copyOf(plannedOffsets),
			new SurgicalTableLayout.Proposal(offsets, footprints));
	}

	static Map<Integer, SurgicalClientTopology.Bounds> layoutBounds(BitSet presentCubes,
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		Map<Integer, SurgicalClientTopology.Bounds> bounds = new HashMap<>();
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			if (presentCubes.get(cube.cubeId()))
				bounds.putIfAbsent(cube.cubeId(), SurgicalClientTopology.Bounds.of(cube));
		return bounds;
	}

	static Map<Integer, SurgicalModelRenderContext.CubeGeometry> indexCubeGeometry(
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		Map<Integer, SurgicalModelRenderContext.CubeGeometry> indexed = new HashMap<>();
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			if (cube != null)
				indexed.putIfAbsent(cube.cubeId(), cube);
		return Map.copyOf(indexed);
	}

	@Nullable
	static BitSet matchingComponent(List<BitSet> components, BitSet requested) {
		for (BitSet component : components)
			if (component.equals(requested))
				return component;
		return null;
	}

	static boolean collidesHorizontally(SurgicalClientTopology.Bounds candidate, BitSet moving, List<BitSet> components,
		Map<Integer, SurgicalClientTopology.Bounds> baseBounds, Map<Integer, Vec3> offsets, Vec3 delta,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		for (BitSet component : components) {
			if (component.equals(moving))
				continue;
			SurgicalClientTopology.Bounds obstacleEnvelope = SurgicalClientTopology.unionBounds(component, baseBounds, offsets);
			if (obstacleEnvelope == null || !candidate.conflictsHorizontally(obstacleEnvelope))
				continue;
			for (int movingCube = moving.nextSetBit(0); movingCube >= 0;
				movingCube = moving.nextSetBit(movingCube + 1)) {
				SurgicalClientTopology.Bounds movingBounds = baseBounds.get(movingCube);
				if (movingBounds == null)
					continue;
				movingBounds = movingBounds.translate(offsets.getOrDefault(movingCube, Vec3.ZERO).add(delta));
				for (int obstacleCube = component.nextSetBit(0); obstacleCube >= 0;
					obstacleCube = component.nextSetBit(obstacleCube + 1)) {
					SurgicalClientTopology.Bounds obstacleBounds = baseBounds.get(obstacleCube);
					if (obstacleBounds == null)
						continue;
					obstacleBounds = obstacleBounds.translate(offsets.getOrDefault(obstacleCube, Vec3.ZERO));
					if (movingBounds.conflictsHorizontally(obstacleBounds))
						return true;
				}
			}
		}
		for (int movingCube = moving.nextSetBit(0); movingCube >= 0;
			movingCube = moving.nextSetBit(movingCube + 1)) {
			SurgicalClientTopology.Bounds movingBounds = baseBounds.get(movingCube);
			if (movingBounds == null)
				continue;
			movingBounds = movingBounds.translate(offsets.getOrDefault(movingCube, Vec3.ZERO).add(delta));
			for (SurgicalTableLayout.Footprint occupied : occupiedFootprints)
				if (overlapsHorizontally(movingBounds, occupied))
					return true;
		}
		return false;
	}

	static boolean overlapsAny(SurgicalTableLayout.Footprint footprint,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		for (SurgicalTableLayout.Footprint occupied : occupiedFootprints)
			if (footprint.conflictsWith(occupied))
				return true;
		return false;
	}

	static boolean overlapsHorizontally(SurgicalClientTopology.Bounds bounds, SurgicalTableLayout.Footprint footprint) {
		return bounds.minX() < footprint.maxX() + SurgicalTableLayout.COMPONENT_CLEARANCE - SurgicalClientTopology.DISTANCE_EPSILON
			&& bounds.maxX() > footprint.minX() - SurgicalTableLayout.COMPONENT_CLEARANCE + SurgicalClientTopology.DISTANCE_EPSILON
			&& bounds.minZ() < footprint.maxZ() + SurgicalTableLayout.COMPONENT_CLEARANCE - SurgicalClientTopology.DISTANCE_EPSILON
			&& bounds.maxZ() > footprint.minZ() - SurgicalTableLayout.COMPONENT_CLEARANCE + SurgicalClientTopology.DISTANCE_EPSILON;
	}

	static boolean fits(SurgicalTablePlane.WorkArea workArea, SurgicalClientTopology.Bounds bounds) {
		return workArea.contains(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), SurgicalClientTopology.DISTANCE_EPSILON);
	}

	static Iterable<SurgicalClientTopology.GridCell> orderedCells(SurgicalTablePlane.WorkArea workArea, double targetX,
		double targetZ) {
		SurgicalClientTopology.GridCell[] cells = GRID_CELLS.computeIfAbsent(workArea, SurgicalPlacementSearch::gridCells);
		// Most searches stop at their first free cell. Heapify once in O(N), then
		// produce only the cells actually visited, in exactly the former sort order.
		return () -> new SurgicalClientTopology.NearestCells(cells, targetX, targetZ);
	}

	static SurgicalClientTopology.GridCell[] gridCells(SurgicalTablePlane.WorkArea workArea) {
		List<SurgicalClientTopology.GridCell> cells = new ArrayList<>(workArea.tileArea() * SurgicalTableLayout.SLOTS_PER_TILE);
		for (BlockPos tile : workArea.tiles()) {
			int minGridX = tile.getX() * SurgicalTableLayout.SUBDIVISIONS;
			int minGridZ = tile.getZ() * SurgicalTableLayout.SUBDIVISIONS;
			for (int xOffset = 0; xOffset < SurgicalTableLayout.SUBDIVISIONS; xOffset++)
				for (int zOffset = 0; zOffset < SurgicalTableLayout.SUBDIVISIONS; zOffset++)
					cells.add(new SurgicalClientTopology.GridCell(minGridX + xOffset, minGridZ + zOffset));
		}
		return cells.toArray(SurgicalClientTopology.GridCell[]::new);
	}
}
