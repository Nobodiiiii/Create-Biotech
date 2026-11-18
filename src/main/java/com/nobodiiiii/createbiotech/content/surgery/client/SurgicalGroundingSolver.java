package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalConnectionGraph;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalGroundingSolver implementation; public entry points remain on SurgicalClientTopology. */
final class SurgicalGroundingSolver {
	private SurgicalGroundingSolver() {}


	/** Aligns every detached component so its rendered outer bounds touch the table surface. */
	static Map<Integer, Vec3> groundComponents(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> horizontalOffsets,
		double surfaceY) {
		return groundComponents(cubeCount, presentCubes, seams, cutSeams, cubes, horizontalOffsets,
			surfaceY, false);
	}

	/** Grounds even an uncut component, used after its last glue connection has been severed. */
	static Map<Integer, Vec3> groundAllComponents(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> horizontalOffsets,
		double surfaceY) {
		return groundComponents(cubeCount, presentCubes, seams, cutSeams, cubes, horizontalOffsets,
			surfaceY, true);
	}

	/** Grounds each component from the same unified connection graph used by selection and packing. */
	@Nullable
	static <K> Map<K, Map<Integer, Vec3>> groundConnectedBodies(List<SurgicalClientTopology.GroundingBody<K>> bodies,
		List<SurgicalClientTopology.GroundingLink<K>> links, double surfaceY) {
		if (bodies.isEmpty() || !Double.isFinite(surfaceY))
			return null;
		Map<K, SurgicalClientTopology.GroundingBody<K>> indexed = new LinkedHashMap<>();
		Map<K, Map<Integer, SurgicalModelRenderContext.CubeGeometry>> cubes = new HashMap<>();
		Map<K, Map<Integer, Vec3>> grounded = new LinkedHashMap<>();
		List<SurgicalConnectionGraph.Body<K>> connectionBodies = new ArrayList<>(bodies.size());
		for (SurgicalClientTopology.GroundingBody<K> body : bodies) {
			if (body == null || body.key() == null || indexed.putIfAbsent(body.key(), body) != null
				|| !SurgicalAssembly.validTopology(body.cubeCount(), body.seams()))
				return null;
			Map<Integer, SurgicalModelRenderContext.CubeGeometry> bodyCubes = SurgicalPlacementSearch.indexCubeGeometry(body.cubes());
			if (bodyCubes.size() < body.presentCubes().cardinality())
				return null;
			for (int cube = body.presentCubes().nextSetBit(0); cube >= 0;
				cube = body.presentCubes().nextSetBit(cube + 1))
				if (!bodyCubes.containsKey(cube))
					return null;
			cubes.put(body.key(), bodyCubes);
			grounded.put(body.key(), new HashMap<>(body.offsets()));
			connectionBodies.add(new SurgicalConnectionGraph.Body<>(body.key(), body.cubeCount(),
				body.presentCubes(), body.seams(), body.cutSeams()));
		}
		List<SurgicalConnectionGraph.Link<K>> connectionLinks = new ArrayList<>(links.size());
		for (SurgicalClientTopology.GroundingLink<K> link : links) {
			if (link == null)
				return null;
			connectionLinks.add(new SurgicalConnectionGraph.Link<>(link.firstBody(), link.firstCube(),
				link.secondBody(), link.secondCube()));
		}
		SurgicalConnectionGraph<K> graph = SurgicalConnectionGraph.create(connectionBodies, connectionLinks);
		if (graph == null)
			return null;

		for (SurgicalConnectionGraph.Component<K> component : graph.components()) {
			Map<K, BitSet> group = component.members();
			double lowestY = Double.POSITIVE_INFINITY;
			for (Map.Entry<K, BitSet> entry : group.entrySet()) {
				SurgicalClientTopology.GroundingBody<K> body = indexed.get(entry.getKey());
				Map<Integer, SurgicalModelRenderContext.CubeGeometry> bodyCubes = cubes.get(entry.getKey());
				if (body == null || bodyCubes == null)
					return null;
				for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
					cube = entry.getValue().nextSetBit(cube + 1)) {
					SurgicalModelRenderContext.CubeGeometry geometry = bodyCubes.get(cube);
					if (geometry == null)
						return null;
					double offsetY = body.offsets().getOrDefault(cube, Vec3.ZERO).y;
					for (Vec3 corner : geometry.corners())
						lowestY = Math.min(lowestY, corner.y + offsetY);
				}
			}
			if (!Double.isFinite(lowestY))
				return null;
			double lift = surfaceY - lowestY;
			for (Map.Entry<K, BitSet> entry : group.entrySet()) {
				SurgicalClientTopology.GroundingBody<K> body = indexed.get(entry.getKey());
				Map<Integer, Vec3> bodyGrounded = grounded.get(entry.getKey());
				for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
					cube = entry.getValue().nextSetBit(cube + 1)) {
					Vec3 adjusted = body.offsets().getOrDefault(cube, Vec3.ZERO).add(0.0d, lift, 0.0d);
					if (adjusted.lengthSqr() <= SurgicalClientTopology.DISTANCE_EPSILON)
						bodyGrounded.remove(cube);
					else
						bodyGrounded.put(cube, adjusted);
				}
			}
		}

		Map<K, Map<Integer, Vec3>> frozen = new LinkedHashMap<>();
		grounded.forEach((key, value) -> frozen.put(key, Map.copyOf(value)));
		return Map.copyOf(frozen);
	}

	static Map<Integer, Vec3> groundComponents(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> horizontalOffsets,
		double surfaceY, boolean includeUncut) {
		if (!Double.isFinite(surfaceY) || !SurgicalAssembly.validTopology(cubeCount, seams)
			|| !includeUncut && cutSeams.isEmpty())
			return Map.copyOf(horizontalOffsets);

		List<BitSet> components = SurgicalAssembly.components(cubeCount, presentCubes, seams, cutSeams);
		if (components.isEmpty())
			return Map.copyOf(horizontalOffsets);
		Map<Integer, SurgicalClientTopology.Bounds> baseBounds = SurgicalPlacementSearch.layoutBounds(presentCubes, cubes);
		if (baseBounds.size() < presentCubes.cardinality())
			return Map.copyOf(horizontalOffsets);

		Map<Integer, Vec3> grounded = new HashMap<>();
		for (BitSet component : components) {
			double bottomY = Double.POSITIVE_INFINITY;
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
				SurgicalClientTopology.Bounds bounds = baseBounds.get(cube);
				if (bounds == null)
					continue;
				bottomY = Math.min(bottomY,
					bounds.minY() + horizontalOffsets.getOrDefault(cube, Vec3.ZERO).y);
			}
			if (!Double.isFinite(bottomY))
				continue;
			double groundDelta = surfaceY - bottomY;
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
				Vec3 offset = horizontalOffsets.getOrDefault(cube, Vec3.ZERO);
				Vec3 adjusted = new Vec3(offset.x, offset.y + groundDelta, offset.z);
				if (adjusted.lengthSqr() > SurgicalClientTopology.DISTANCE_EPSILON)
					grounded.put(cube, adjusted);
			}
		}
		return Map.copyOf(grounded);
	}

	@Nullable
	static SurgicalClientTopology.PlannedLayout planSnappedLayout(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet proposedCuts,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, List<SurgicalClientTopology.SnapRequest> requests,
		List<SurgicalTableLayout.Footprint> occupiedFootprints, boolean allowComponentOverlap,
		boolean groundComponents) {
		if (!SurgicalAssembly.validTopology(cubeCount, seams) || workArea.isEmpty())
			return null;
		Map<Integer, SurgicalClientTopology.Bounds> baseBounds = SurgicalPlacementSearch.layoutBounds(presentCubes, cubes);
		if (baseBounds.size() < presentCubes.cardinality())
			return null;
		List<BitSet> components = SurgicalAssembly.components(cubeCount, presentCubes, seams, proposedCuts);
		Map<Integer, Vec3> plannedOffsets = new HashMap<>();
		for (int cube = presentCubes.nextSetBit(0); cube >= 0; cube = presentCubes.nextSetBit(cube + 1))
			plannedOffsets.put(cube, currentOffsets.getOrDefault(cube, Vec3.ZERO));
		Map<Integer, SurgicalClientTopology.GridCell> snapped = new HashMap<>();

		for (SurgicalClientTopology.SnapRequest request : requests) {
			BitSet moving = SurgicalPlacementSearch.matchingComponent(components, request.component());
			if (moving == null)
				return null;
			SurgicalClientTopology.Bounds movingBounds = SurgicalClientTopology.unionBounds(moving, baseBounds, plannedOffsets);
			if (movingBounds == null)
				return null;
			SurgicalClientTopology.GridCell selected = null;
			Vec3 selectedDelta = Vec3.ZERO;
			for (SurgicalClientTopology.GridCell cell : SurgicalPlacementSearch.orderedCells(workArea, request.targetX(), request.targetZ())) {
				Vec3 delta = new Vec3(cell.centerX() - movingBounds.centerX(), 0.0d,
					cell.centerZ() - movingBounds.centerZ());
				SurgicalClientTopology.Bounds candidate = movingBounds.translate(delta);
				if (!SurgicalPlacementSearch.fits(workArea, candidate)
					|| SurgicalPlacementSearch.collidesHorizontally(candidate, moving, components, baseBounds, plannedOffsets, delta,
						occupiedFootprints))
					continue;
				selected = cell;
				selectedDelta = delta;
				break;
			}
			if (selected == null)
				return null;
			for (int cube = moving.nextSetBit(0); cube >= 0; cube = moving.nextSetBit(cube + 1))
				plannedOffsets.put(cube, plannedOffsets.getOrDefault(cube, Vec3.ZERO).add(selectedDelta));
			snapped.put(moving.nextSetBit(0), selected);
		}

		if (groundComponents) {
			double surfaceY = workArea.surfaceY() + SurgicalTablePoseResolver.TABLE_CLEARANCE;
			for (BitSet component : components) {
				double lowestY = Double.POSITIVE_INFINITY;
				for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
					SurgicalClientTopology.Bounds bounds = baseBounds.get(cube);
					if (bounds != null)
						lowestY = Math.min(lowestY,
							bounds.minY() + plannedOffsets.getOrDefault(cube, Vec3.ZERO).y);
				}
				if (!Double.isFinite(lowestY))
					return null;
				double lift = surfaceY - lowestY;
				for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1))
					plannedOffsets.put(cube,
						plannedOffsets.getOrDefault(cube, Vec3.ZERO).add(0.0d, lift, 0.0d));
			}
		}

		List<SurgicalTableLayout.Footprint> footprints = new ArrayList<>(presentCubes.cardinality());
		for (BitSet component : components) {
			int root = component.nextSetBit(0);
			SurgicalClientTopology.GridCell cell = snapped.get(root);
			List<SurgicalTableLayout.Footprint> componentFootprints =
				SurgicalClientTopology.footprints(root, component, baseBounds, plannedOffsets, cell);
			// Match wrench and server validation: empty space between cubes does not occupy table tiles.
			for (SurgicalTableLayout.Footprint footprint : componentFootprints)
				if (!workArea.contains(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ(), 1.0e-6d))
					return null;
			footprints.addAll(componentFootprints);
		}
		if (!allowComponentOverlap)
			for (int first = 0; first < footprints.size(); first++)
				for (int second = first + 1; second < footprints.size(); second++)
					if (footprints.get(first).componentRoot() != footprints.get(second).componentRoot()
						&& footprints.get(first).conflictsWith(footprints.get(second)))
						return null;
		for (SurgicalTableLayout.Footprint footprint : footprints)
			if (SurgicalPlacementSearch.overlapsAny(footprint, occupiedFootprints))
				return null;

		List<SurgicalTableLayout.CubeOffset> offsets = new ArrayList<>(presentCubes.cardinality());
		for (int cube = presentCubes.nextSetBit(0); cube >= 0; cube = presentCubes.nextSetBit(cube + 1)) {
			Vec3 offset = plannedOffsets.getOrDefault(cube, Vec3.ZERO);
			offsets.add(new SurgicalTableLayout.CubeOffset(cube, offset.x, offset.y, offset.z));
		}
		return new SurgicalClientTopology.PlannedLayout(Map.copyOf(plannedOffsets),
			new SurgicalTableLayout.Proposal(offsets, footprints));
	}
}
