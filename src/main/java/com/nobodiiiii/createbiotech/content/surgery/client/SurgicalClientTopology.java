package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;

import net.minecraft.world.phys.Vec3;

/** Deterministic client geometry helpers; no result is trusted without server validation. */
public final class SurgicalClientTopology {
	public static final int[][] CUBE_FACES = {
		{0, 4, 6, 2}, {1, 3, 7, 5},
		{0, 1, 5, 4}, {2, 6, 7, 3},
		{0, 2, 3, 1}, {4, 5, 7, 6}
	};
	private static final double COMPONENT_OFFSET = 1.0d / 16.0d;
	static final int MAX_BATCH_LAYOUT_SEARCH_NODES = 262_144;
	static final double BATCH_CUT_CLEARANCE = SurgicalTableLayout.COMPONENT_CLEARANCE;
	static final double BATCH_CUT_MIN_TRAVEL = 1.0d / 16.0d;
	static final double DISTANCE_EPSILON = 1.0e-9d;
	static final double INTERSECTION_EPSILON = 1.0e-12d;
	static final double DEGENERATE_EPSILON = 1.0e-18d;
	static final double POLYHEDRON_EPSILON = 1.0e-8d;
	static final double VERTEX_MERGE_DISTANCE_SQR = 1.0e-14d;
	static final double CONTACT_TOLERANCE = 1.0d / 64.0d;
	static final double MIN_CONTACT_AREA = 1.0e-8d;

	private SurgicalClientTopology() {}

	/** Builds one stable seam for every intersecting or tolerance-adjacent pair of model cubes. */
	public static ContactTopology buildContactTopology(int cubeCount,
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		return SurgicalContactGeometry.buildContactTopology(cubeCount, cubes);
	}

	public static List<Contact> contactsFor(List<SurgicalAssembly.Seam> seams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		return SurgicalContactGeometry.contactsFor(seams, cubes);
	}

	@Nullable
	public static Contact contactBetween(SurgicalAssembly.Seam seam,
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		return SurgicalContactGeometry.contactBetween(seam, cubes);
	}

	/** Exact boundary lines, including edge-only contact of zero-thickness model wings. */
	public static List<Edge> intersectionEdges(SurgicalModelRenderContext.CubeGeometry first,
		SurgicalModelRenderContext.CubeGeometry second) {
		return SurgicalContactGeometry.intersectionEdges(first, second);
	}

	/** Accepts real solid, face, edge or point contact without the seam builder's adjacency tolerance. */
	public static boolean cubesActuallyIntersect(SurgicalModelRenderContext.CubeGeometry firstGeometry,
		SurgicalModelRenderContext.CubeGeometry secondGeometry) {
		return SurgicalContactGeometry.cubesActuallyIntersect(firstGeometry, secondGeometry);
	}

	/** Uses seam discovery's adjacency tolerance without requiring a drawable contact polygon. */
	public static boolean cubesConnectWithinTolerance(
		SurgicalModelRenderContext.CubeGeometry firstGeometry,
		SurgicalModelRenderContext.CubeGeometry secondGeometry) {
		return SurgicalContactGeometry.cubesConnectWithinTolerance(firstGeometry, secondGeometry);
	}

	public static Map<Integer, Vec3> componentOffsets(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		if (!SurgicalAssembly.validTopology(cubeCount, seams) || cutSeams.isEmpty())
			return Map.of();
		List<BitSet> components = SurgicalAssembly.components(cubeCount, presentCubes, seams, cutSeams);
		if (components.size() <= 1)
			return Map.of();

		Map<Integer, SurgicalModelRenderContext.CubeGeometry> byId = SurgicalContactGeometry.byId(cubes);
		Vec3 mainCenter = componentCenter(components.getFirst(), byId);
		Map<Integer, Vec3> offsets = new HashMap<>();
		for (int componentId = 1; componentId < components.size(); componentId++) {
			BitSet component = components.get(componentId);
			Vec3 direction = componentCenter(component, byId).subtract(mainCenter);
			if (direction.lengthSqr() < 1.0e-9d)
				direction = new Vec3(0.0d, 1.0d, 0.0d);
			Vec3 offset = direction.normalize().scale(COMPONENT_OFFSET);
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1))
				offsets.put(cube, offset);
		}
		return Map.copyOf(offsets);
	}

	@Nullable
	static Bounds unionBounds(BitSet cubes, Map<Integer, Bounds> baseBounds, Map<Integer, Vec3> offsets) {
		Bounds result = null;
		for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1)) {
			Bounds bounds = baseBounds.get(cube);
			if (bounds == null)
				continue;
			bounds = bounds.translate(offsets.getOrDefault(cube, Vec3.ZERO));
			result = result == null ? bounds : result.union(bounds);
		}
		return result;
	}

	/** Places a new subject on the nearest free 1/4-block slot to the supplied world-space target. */
	@Nullable
	public static PlacementPlan planInitialPlacement(List<SurgicalModelRenderContext.CubeGeometry> cubes,
		SurgicalTablePlane.WorkArea workArea, double targetX, double targetZ,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		Bounds bounds = null;
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes) {
			Bounds cubeBounds = Bounds.of(cube);
			bounds = bounds == null ? cubeBounds : bounds.union(cubeBounds);
		}
		if (bounds == null || workArea.isEmpty())
			return null;

		for (GridCell cell : SurgicalPlacementSearch.orderedCells(workArea, targetX, targetZ)) {
			double offsetX = cell.centerX() - bounds.centerX();
			double offsetZ = cell.centerZ() - bounds.centerZ();
			Bounds placed = bounds.translate(new Vec3(offsetX, 0.0d, offsetZ));
			if (!SurgicalPlacementSearch.fits(workArea, placed))
				continue;
			SurgicalTableLayout.Footprint footprint = footprint(-1, placed, cell);
			if (SurgicalPlacementSearch.overlapsAny(footprint, occupiedFootprints))
				continue;
			return new PlacementPlan(offsetX, offsetZ,
				new SurgicalTableLayout.Proposal(List.of(), List.of(footprint)));
		}
		return null;
	}

	/** Moves a multi-subject glued group as one rigid horizontal unit to the nearest legal slot. */
	@Nullable
	public static ConnectedPlacement snapConnectedGroup(List<SurgicalTableLayout.Footprint> movingFootprints,
		SurgicalTablePlane.WorkArea workArea, double targetX, double targetZ,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return SurgicalPlacementSearch.snapConnectedGroup(movingFootprints, workArea, targetX, targetZ, occupiedFootprints);
	}

	/**
	 * Places a newly captured mimic whose model already contains multiple disconnected components.
	 * The complete model envelope still determines its snapped anchor and table fit, while collision
	 * checks use one tight envelope per native component so empty space between them stays available.
	 */
	@Nullable
	public static DiscoveredPlacementPlan planDiscoveredPlacement(int cubeCount,
		List<SurgicalAssembly.Seam> seams, SurgicalModelRenderContext.CubeGeometry envelopeGeometry,
		List<SurgicalModelRenderContext.CubeGeometry> cubes,
		SurgicalTablePlane.WorkArea workArea, double targetX, double targetZ,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return SurgicalPlacementSearch.planDiscoveredPlacement(cubeCount, seams, envelopeGeometry, cubes, workArea, targetX, targetZ, occupiedFootprints);
	}

	/**
	 * Places every connected group produced by one batch cut as a whole. Group zero is the stable,
	 * largest remainder. Unlike the component-at-a-time layout path, this searches the complete
	 * arrangement and backtracks when a tempting nearby slot would strand a later group.
	 */
	@Nullable
	public static ConnectedGroupLayout autoSnapConnectedGroups(
		List<List<SurgicalTableLayout.Footprint>> groups, SurgicalTablePlane.WorkArea workArea,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return SurgicalPlacementSearch.autoSnapConnectedGroups(groups, workArea, occupiedFootprints);
	}

	/** Builds a proposal for a cut that does not create a new detached component. */
	@Nullable
	public static PlannedLayout currentLayout(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet proposedCuts,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return SurgicalPlacementSearch.currentLayout(cubeCount, presentCubes, seams, proposedCuts, cubes, currentOffsets, workArea, occupiedFootprints);
	}

	/** Preserves the exact overlapping layout of one source in a packed glued assembly. */
	@Nullable
	public static PlannedLayout preserveCompositeLayout(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return SurgicalPlacementSearch.preserveCompositeLayout(cubeCount, presentCubes, seams, cutSeams, cubes, currentOffsets, workArea, occupiedFootprints);
	}

	/** Snaps one detached component to the legal slot nearest the supplied world-space target. */
	@Nullable
	public static PlannedLayout snapComponent(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet proposedCuts,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, BitSet movingComponent, double targetX, double targetZ,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return SurgicalPlacementSearch.snapComponent(cubeCount, presentCubes, seams, proposedCuts, cubes, currentOffsets, workArea, movingComponent, targetX, targetZ, occupiedFootprints);
	}

	/**
	 * Sequentially places every newly detached batch-cut component at its nearest legal slot.
	 * Vertical grounding is left to the caller because retained glue can join these native
	 * components to bodies whose geometry lives in another subject.
	 */
	@Nullable
	public static PlannedLayout autoSnapComponents(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet proposedCuts,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, List<BitSet> movingComponents,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return SurgicalPlacementSearch.autoSnapComponents(cubeCount, presentCubes, seams, proposedCuts, cubes, currentOffsets, workArea, movingComponents, occupiedFootprints);
	}

	/** Applies one rigid horizontal delta to a union of complete components and rebuilds its layout. */
	@Nullable
	public static PlannedLayout translateComponents(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet proposedCuts,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, BitSet translatedCubes, Vec3 delta,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return SurgicalPlacementSearch.translateComponents(cubeCount, presentCubes, seams, proposedCuts, cubes, currentOffsets, workArea, translatedCubes, delta, occupiedFootprints);
	}

	/** Aligns every detached component so its rendered outer bounds touch the table surface. */
	public static Map<Integer, Vec3> groundComponents(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> horizontalOffsets,
		double surfaceY) {
		return SurgicalGroundingSolver.groundComponents(cubeCount, presentCubes, seams, cutSeams, cubes, horizontalOffsets, surfaceY);
	}

	/** Grounds even an uncut component, used after its last glue connection has been severed. */
	public static Map<Integer, Vec3> groundAllComponents(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> horizontalOffsets,
		double surfaceY) {
		return SurgicalGroundingSolver.groundAllComponents(cubeCount, presentCubes, seams, cutSeams, cubes, horizontalOffsets, surfaceY);
	}

	/** Grounds each component from the same unified connection graph used by selection and packing. */
	@Nullable
	public static <K> Map<K, Map<Integer, Vec3>> groundConnectedBodies(List<GroundingBody<K>> bodies,
		List<GroundingLink<K>> links, double surfaceY) {
		return SurgicalGroundingSolver.groundConnectedBodies(bodies, links, surfaceY);
	}

	static final class NearestCells implements Iterator<GridCell> {
		private final GridCell[] cells;
		private final double[] distances;
		private final int[] heap;
		private int size;

		NearestCells(GridCell[] cells, double x, double z) {
			this.cells = cells;
			size = cells.length;
			distances = new double[size];
			heap = new int[size];
			for (int index = 0; index < size; index++) {
				heap[index] = index;
				distances[index] = cells[index].distanceToSqr(x, z);
			}
			for (int parent = size / 2 - 1; parent >= 0; parent--)
				siftDown(parent);
		}

		@Override
		public boolean hasNext() {
			return size > 0;
		}

		@Override
		public GridCell next() {
			if (!hasNext())
				throw new NoSuchElementException();
			GridCell result = cells[heap[0]];
			heap[0] = heap[--size];
			if (size > 0)
				siftDown(0);
			return result;
		}

		private void siftDown(int parent) {
			int value = heap[parent];
			while (parent < size / 2) {
				int child = parent * 2 + 1;
				if (child + 1 < size && compare(heap[child + 1], heap[child]) < 0)
					child++;
				if (compare(value, heap[child]) <= 0)
					break;
				heap[parent] = heap[child];
				parent = child;
			}
			heap[parent] = value;
		}

		private int compare(int first, int second) {
			int distance = Double.compare(distances[first], distances[second]);
			if (distance != 0) return distance;
			int x = Integer.compare(cells[first].gridX, cells[second].gridX);
			return x != 0 ? x : Integer.compare(cells[first].gridZ, cells[second].gridZ);
		}
	}

	static SurgicalTableLayout.Footprint footprint(int root, Bounds bounds, @Nullable GridCell cell) {
		return new SurgicalTableLayout.Footprint(root, bounds.minX, bounds.minZ, bounds.maxX, bounds.maxZ,
			cell == null ? SurgicalTableLayout.UNSNAPPED : cell.gridX,
			cell == null ? SurgicalTableLayout.UNSNAPPED : cell.gridZ);
	}

	static List<SurgicalTableLayout.Footprint> footprints(int root, BitSet component,
		Map<Integer, Bounds> baseBounds, Map<Integer, Vec3> offsets, @Nullable GridCell cell) {
		List<SurgicalTableLayout.Footprint> footprints = new ArrayList<>(component.cardinality());
		for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
			Bounds bounds = baseBounds.get(cube);
			if (bounds != null)
				footprints.add(footprint(root, bounds.translate(offsets.getOrDefault(cube, Vec3.ZERO)), cell));
		}
		return footprints;
	}

	public static Vec3 center(SurgicalModelRenderContext.CubeGeometry cube) {
		Vec3 total = Vec3.ZERO;
		for (Vec3 corner : cube.corners())
			total = total.add(corner);
		return total.scale(1.0d / cube.corners().size());
	}

	public static List<Edge> cubeEdges(SurgicalModelRenderContext.CubeGeometry cube) {
		List<Edge> edges = new ArrayList<>(12);
		for (int[] face : CUBE_FACES) {
			for (int vertex = 0; vertex < face.length; vertex++) {
				Edge candidate = new Edge(cube.corners().get(face[vertex]),
					cube.corners().get(face[(vertex + 1) % face.length]));
				if (candidate.start().distanceToSqr(candidate.end()) <= DEGENERATE_EPSILON)
					continue;
				boolean duplicate = false;
				for (Edge edge : edges) {
					if (edge.sameUndirected(candidate)) {
						duplicate = true;
						break;
					}
				}
				if (!duplicate)
					edges.add(candidate);
			}
		}
		return List.copyOf(edges);
	}

	private static Vec3 componentCenter(BitSet component,
		Map<Integer, SurgicalModelRenderContext.CubeGeometry> byId) {
		return componentCenter(component, byId, Map.of());
	}

	private static Vec3 componentCenter(BitSet component,
		Map<Integer, SurgicalModelRenderContext.CubeGeometry> byId, Map<Integer, Vec3> offsets) {
		Vec3 total = Vec3.ZERO;
		int count = 0;
		for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
			SurgicalModelRenderContext.CubeGeometry geometry = byId.get(cube);
			if (geometry == null)
				continue;
			total = total.add(center(geometry).add(offsets.getOrDefault(cube, Vec3.ZERO)));
			count++;
		}
		return count == 0 ? Vec3.ZERO : total.scale(1.0d / count);
	}

	public record ContactTopology(List<SurgicalAssembly.Seam> seams, List<Contact> contacts) {
		static final ContactTopology EMPTY = new ContactTopology(List.of(), List.of());

		public ContactTopology {
			seams = List.copyOf(seams);
			contacts = List.copyOf(contacts);
		}
	}

	public record Contact(SurgicalAssembly.Seam seam, int anchorCubeId, List<List<Vec3>> faces) {
		public Contact {
			List<List<Vec3>> copiedFaces = new ArrayList<>(faces.size());
			for (List<Vec3> face : faces) {
				if (face.size() < 3)
					throw new IllegalArgumentException("A surgical contact face requires at least three vertices");
				copiedFaces.add(List.copyOf(face));
			}
			if (copiedFaces.isEmpty())
				throw new IllegalArgumentException("A surgical contact requires at least one face");
			faces = List.copyOf(copiedFaces);
		}

		public List<Edge> edges() {
			List<Edge> edges = new ArrayList<>();
			for (List<Vec3> face : faces) {
				for (int vertex = 0; vertex < face.size(); vertex++) {
					Edge candidate = new Edge(face.get(vertex), face.get((vertex + 1) % face.size()));
					boolean duplicate = false;
					for (Edge edge : edges) {
						if (edge.sameUndirected(candidate)) {
							duplicate = true;
							break;
						}
					}
					if (!duplicate)
						edges.add(candidate);
				}
			}
			return List.copyOf(edges);
		}
	}

	public record Edge(Vec3 start, Vec3 end) {
		private boolean sameUndirected(Edge other) {
			return samePoint(start, other.start) && samePoint(end, other.end)
				|| samePoint(start, other.end) && samePoint(end, other.start);
		}

		private static boolean samePoint(Vec3 first, Vec3 second) {
			return first.distanceToSqr(second) <= VERTEX_MERGE_DISTANCE_SQR;
		}
	}

	record PreparedCube(SurgicalModelRenderContext.CubeGeometry geometry, Vec3 center,
		double volume, List<Plane> planes, Bounds bounds) {
		static PreparedCube of(SurgicalModelRenderContext.CubeGeometry geometry) {
			return new PreparedCube(geometry, SurgicalClientTopology.center(geometry),
				SurgicalContactGeometry.volume(geometry),
				List.copyOf(SurgicalContactGeometry.clipPlanes(geometry)), Bounds.of(geometry));
		}
	}

	public record PlacementPlan(double originOffsetX, double originOffsetZ,
		SurgicalTableLayout.Proposal proposal) {}

	public record DiscoveredPlacementPlan(PlacementPlan placement,
		List<SurgicalTableLayout.Footprint> componentFootprints) {
		public DiscoveredPlacementPlan {
			componentFootprints = List.copyOf(componentFootprints);
		}
	}

	public record ConnectedPlacement(Vec3 delta) {}

	public record ConnectedGroupLayout(List<Vec3> deltas) {
		public ConnectedGroupLayout {
			deltas = List.copyOf(deltas);
		}
	}

	public record PlannedLayout(Map<Integer, Vec3> offsets, SurgicalTableLayout.Proposal proposal) {
		public PlannedLayout {
			offsets = Map.copyOf(offsets);
		}
	}

	public record GroundingBody<K>(K key, int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> offsets) {
		public GroundingBody {
			presentCubes = (BitSet) presentCubes.clone();
			seams = List.copyOf(seams);
			cutSeams = (BitSet) cutSeams.clone();
			cubes = List.copyOf(cubes);
			offsets = Map.copyOf(offsets);
		}
	}

	public record GroundingLink<K>(K firstBody, int firstCube, K secondBody, int secondCube) {}

	record SnapRequest(BitSet component, double targetX, double targetZ) {}
	record BatchPlacementRequest(int groupId, double area,
		List<BatchPlacementCandidate> candidates) {}
	record BatchPlacementCandidate(Vec3 delta,
		List<SurgicalTableLayout.Footprint> footprints) {}

	record GridCell(int gridX, int gridZ) {
		double centerX() {
			return SurgicalTableLayout.gridCenter(gridX);
		}

		double centerZ() {
			return SurgicalTableLayout.gridCenter(gridZ);
		}

		private double distanceToSqr(double x, double z) {
			double dx = centerX() - x;
			double dz = centerZ() - z;
			return dx * dx + dz * dz;
		}
	}

	record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		static Bounds of(SurgicalModelRenderContext.CubeGeometry geometry) {
			double minX = Double.POSITIVE_INFINITY;
			double minY = Double.POSITIVE_INFINITY;
			double minZ = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY;
			double maxY = Double.NEGATIVE_INFINITY;
			double maxZ = Double.NEGATIVE_INFINITY;
			for (Vec3 corner : geometry.corners()) {
				minX = Math.min(minX, corner.x);
				minY = Math.min(minY, corner.y);
				minZ = Math.min(minZ, corner.z);
				maxX = Math.max(maxX, corner.x);
				maxY = Math.max(maxY, corner.y);
				maxZ = Math.max(maxZ, corner.z);
			}
			return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
		}

		boolean overlapsWithin(Bounds other, double tolerance) {
			return minX <= other.maxX + tolerance && maxX + tolerance >= other.minX
				&& minY <= other.maxY + tolerance && maxY + tolerance >= other.minY
				&& minZ <= other.maxZ + tolerance && maxZ + tolerance >= other.minZ;
		}

		Bounds translate(Vec3 offset) {
			return new Bounds(minX + offset.x, minY + offset.y, minZ + offset.z,
				maxX + offset.x, maxY + offset.y, maxZ + offset.z);
		}

		private Bounds union(Bounds other) {
			return new Bounds(Math.min(minX, other.minX), Math.min(minY, other.minY),
				Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
				Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
		}

		private boolean overlapsStrictly(Bounds other) {
			return minX < other.maxX - DISTANCE_EPSILON && maxX > other.minX + DISTANCE_EPSILON
				&& minY < other.maxY - DISTANCE_EPSILON && maxY > other.minY + DISTANCE_EPSILON
				&& minZ < other.maxZ - DISTANCE_EPSILON && maxZ > other.minZ + DISTANCE_EPSILON;
		}

		private boolean overlapsHorizontally(Bounds other) {
			return minX < other.maxX - DISTANCE_EPSILON && maxX > other.minX + DISTANCE_EPSILON
				&& minZ < other.maxZ - DISTANCE_EPSILON && maxZ > other.minZ + DISTANCE_EPSILON;
		}

		boolean conflictsHorizontally(Bounds other) {
			return minX < other.maxX + SurgicalTableLayout.COMPONENT_CLEARANCE - DISTANCE_EPSILON
				&& maxX > other.minX - SurgicalTableLayout.COMPONENT_CLEARANCE + DISTANCE_EPSILON
				&& minZ < other.maxZ + SurgicalTableLayout.COMPONENT_CLEARANCE - DISTANCE_EPSILON
				&& maxZ > other.minZ - SurgicalTableLayout.COMPONENT_CLEARANCE + DISTANCE_EPSILON;
		}

		double centerX() {
			return (minX + maxX) * 0.5d;
		}

		double centerZ() {
			return (minZ + maxZ) * 0.5d;
		}
	}

	record Plane(Vec3 normal, double maximum) {
		double signedDistance(Vec3 point) {
			return normal.dot(point) - maximum;
		}
	}
}
