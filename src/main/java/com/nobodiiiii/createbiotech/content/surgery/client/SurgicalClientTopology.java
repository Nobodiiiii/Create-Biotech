package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalConnectionGraph;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTableLayout;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Deterministic client geometry helpers; no result is trusted without server validation. */
public final class SurgicalClientTopology {
	public static final int[][] CUBE_FACES = {
		{0, 4, 6, 2}, {1, 3, 7, 5},
		{0, 1, 5, 4}, {2, 6, 7, 3},
		{0, 2, 3, 1}, {4, 5, 7, 6}
	};
	private static final double COMPONENT_OFFSET = 1.0d / 16.0d;
	private static final int MAX_BATCH_LAYOUT_SEARCH_NODES = 262_144;
	private static final double BATCH_CUT_CLEARANCE = SurgicalTableLayout.COMPONENT_CLEARANCE;
	private static final double BATCH_CUT_MIN_TRAVEL = 1.0d / 16.0d;
	private static final double DISTANCE_EPSILON = 1.0e-9d;
	private static final double INTERSECTION_EPSILON = 1.0e-12d;
	private static final double DEGENERATE_EPSILON = 1.0e-18d;
	private static final double POLYHEDRON_EPSILON = 1.0e-8d;
	private static final double VERTEX_MERGE_DISTANCE_SQR = 1.0e-14d;
	private static final double CONTACT_TOLERANCE = 1.0d / 64.0d;
	private static final double MIN_CONTACT_AREA = 1.0e-8d;

	private SurgicalClientTopology() {}

	/** Builds one stable seam for every intersecting or tolerance-adjacent pair of model cubes. */
	public static ContactTopology buildContactTopology(int cubeCount,
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		if (!SurgicalAssembly.validCubeCount(cubeCount))
			return ContactTopology.EMPTY;
		Map<Integer, PreparedCube> byId = preparedById(cubes);
		if (byId.size() != cubeCount)
			return ContactTopology.EMPTY;

		// Broad-phase sweep avoids clipping every possible pair. Large models usually
		// contain many distant decorative cubes, so only AABBs close enough to touch
		// advance to the comparatively expensive oriented-face clipping below.
		List<PreparedCube> sweep = new ArrayList<>(byId.values());
		sweep.sort(Comparator.comparingDouble((PreparedCube cube) -> cube.bounds.minX)
			.thenComparingInt(cube -> cube.geometry.cubeId()));
		List<BitSet> candidates = new ArrayList<>(cubeCount);
		for (int cube = 0; cube < cubeCount; cube++)
			candidates.add(new BitSet(cubeCount));
		for (int firstIndex = 0; firstIndex < sweep.size(); firstIndex++) {
			PreparedCube first = sweep.get(firstIndex);
			for (int secondIndex = firstIndex + 1; secondIndex < sweep.size(); secondIndex++) {
				PreparedCube second = sweep.get(secondIndex);
				if (second.bounds.minX > first.bounds.maxX + CONTACT_TOLERANCE)
					break;
				if (!first.bounds.overlapsWithin(second.bounds, CONTACT_TOLERANCE))
					continue;
				int lower = Math.min(first.geometry.cubeId(), second.geometry.cubeId());
				int upper = Math.max(first.geometry.cubeId(), second.geometry.cubeId());
				candidates.get(lower).set(upper);
			}
		}

		List<SurgicalAssembly.Seam> seams = new ArrayList<>();
		List<Contact> contacts = new ArrayList<>();
		for (int first = 0; first < cubeCount && seams.size() < SurgicalAssembly.MAX_SEAMS; first++) {
			for (int second = candidates.get(first).nextSetBit(first + 1);
				second >= 0 && seams.size() < SurgicalAssembly.MAX_SEAMS;
				second = candidates.get(first).nextSetBit(second + 1)) {
				SurgicalAssembly.Seam seam = SurgicalAssembly.Seam.of(first, second);
				Contact contact = contactBetween(seam, byId);
				if (contact == null)
					continue;
				seams.add(seam);
				contacts.add(contact);
			}
		}
		return new ContactTopology(seams, contacts);
	}

	public static List<Contact> contactsFor(List<SurgicalAssembly.Seam> seams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		Map<Integer, PreparedCube> byId = preparedById(cubes);
		List<Contact> contacts = new ArrayList<>(seams.size());
		for (SurgicalAssembly.Seam seam : seams) {
			Contact contact = contactBetween(seam, byId);
			if (contact != null)
				contacts.add(contact);
		}
		return List.copyOf(contacts);
	}

	@Nullable
	public static Contact contactBetween(SurgicalAssembly.Seam seam,
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		return contactBetween(seam, preparedById(cubes));
	}

	/** True only for a real shared convex solid or face, without the seam builder's adjacency tolerance. */
	public static boolean cubesActuallyIntersect(SurgicalModelRenderContext.CubeGeometry firstGeometry,
		SurgicalModelRenderContext.CubeGeometry secondGeometry) {
		if (firstGeometry == null || secondGeometry == null)
			return false;
		PreparedCube first = PreparedCube.of(firstGeometry);
		PreparedCube second = PreparedCube.of(secondGeometry);
		if (!first.bounds.overlapsWithin(second.bounds, POLYHEDRON_EPSILON))
			return false;
		List<Plane> intersectionPlanes = new ArrayList<>(first.planes.size() + second.planes.size());
		intersectionPlanes.addAll(first.planes);
		intersectionPlanes.addAll(second.planes);
		return !convexIntersectionFaces(intersectionPlanes).isEmpty();
	}

	/** Uses the same overlap and planar adjacency tolerance as automatic seam discovery. */
	public static boolean cubesConnectWithinTolerance(
		SurgicalModelRenderContext.CubeGeometry firstGeometry,
		SurgicalModelRenderContext.CubeGeometry secondGeometry) {
		if (firstGeometry == null || secondGeometry == null)
			return false;
		PreparedCube first = PreparedCube.of(firstGeometry);
		PreparedCube second = PreparedCube.of(secondGeometry);
		PreparedCube smaller = first.volume <= second.volume ? first : second;
		PreparedCube other = smaller == first ? second : first;
		return !contactFaces(smaller, other).isEmpty();
	}

	@Nullable
	private static Contact contactBetween(SurgicalAssembly.Seam seam,
		Map<Integer, PreparedCube> byId) {
		PreparedCube first = byId.get(seam.first());
		PreparedCube second = byId.get(seam.second());
		if (first == null || second == null)
			return null;

		PreparedCube smaller = first.volume <= second.volume ? first : second;
		PreparedCube other = smaller == first ? second : first;
		List<List<Vec3>> faces = contactFaces(smaller, other);
		return faces.isEmpty() ? null : new Contact(seam, smaller.geometry.cubeId(), faces);
	}

	/** Shared by seam discovery and smart-glue editing so both accept the same contact boundary. */
	private static List<List<Vec3>> contactFaces(PreparedCube smaller, PreparedCube other) {
		if (!smaller.bounds.overlapsWithin(other.bounds, CONTACT_TOLERANCE))
			return List.of();
		// A pair whose bounds are disjoint cannot share an intersection solid, and
		// convexIntersectionFaces is a C(12,3) triple loop allocating several Vec3 per triple. The
		// seam builder deliberately admits merely tolerance-adjacent neighbours, so the empty result
		// is the common case; those pairs now fall straight through to the planar fallback below,
		// which is where they were always resolved. Same rejection cubesActuallyIntersect makes.
		if (smaller.bounds.overlapsWithin(other.bounds, POLYHEDRON_EPSILON)) {
			List<Plane> intersectionPlanes = new ArrayList<>(smaller.planes.size() + other.planes.size());
			intersectionPlanes.addAll(smaller.planes);
			intersectionPlanes.addAll(other.planes);
			List<List<Vec3>> intersectionFaces = convexIntersectionFaces(intersectionPlanes);
			if (!intersectionFaces.isEmpty())
				return intersectionFaces;
		}

		// Keep near-adjacent model parts connected. This is deliberately a planar
		// fallback: only real overlap is represented by the complete intersection solid.
		List<Vec3> bestContact = List.of();
		double bestCenterDistance = Double.MAX_VALUE;
		double bestArea = 0.0d;
		for (int[] indices : CUBE_FACES) {
			List<Vec3> face = List.of(smaller.geometry.corners().get(indices[0]),
				smaller.geometry.corners().get(indices[1]), smaller.geometry.corners().get(indices[2]),
				smaller.geometry.corners().get(indices[3]));
			List<Vec3> contact = clipAgainstPlanes(face, other.planes, CONTACT_TOLERANCE);
			double area = polygonArea(contact);
			if (area < MIN_CONTACT_AREA)
				continue;
			double centerDistance = faceCenter(face).distanceToSqr(other.center);
			if (centerDistance < bestCenterDistance - DISTANCE_EPSILON
				|| Math.abs(centerDistance - bestCenterDistance) <= DISTANCE_EPSILON
					&& area > bestArea + MIN_CONTACT_AREA) {
				bestCenterDistance = centerDistance;
				bestArea = area;
				bestContact = contact;
			}
		}
		return bestContact.isEmpty() ? List.of() : List.of(bestContact);
	}

	/** Builds every boundary face of the convex polyhedron shared by all half-spaces. */
	private static List<List<Vec3>> convexIntersectionFaces(List<Plane> planes) {
		List<Vec3> vertices = new ArrayList<>();
		for (int first = 0; first < planes.size(); first++) {
			for (int second = first + 1; second < planes.size(); second++) {
				for (int third = second + 1; third < planes.size(); third++) {
					Vec3 vertex = intersect(planes.get(first), planes.get(second), planes.get(third));
					if (vertex == null || !insideAll(vertex, planes) || containsPoint(vertices, vertex))
						continue;
					vertices.add(vertex);
				}
			}
		}
		if (vertices.size() < 3)
			return List.of();

		List<List<Vec3>> faces = new ArrayList<>();
		Set<List<Integer>> uniqueFaces = new HashSet<>();
		for (Plane plane : planes) {
			List<Integer> onPlane = new ArrayList<>();
			for (int vertex = 0; vertex < vertices.size(); vertex++)
				if (Math.abs(plane.signedDistance(vertices.get(vertex))) <= POLYHEDRON_EPSILON)
					onPlane.add(vertex);
			if (onPlane.size() < 3)
				continue;

			List<Integer> ordered = orderFace(onPlane, vertices, plane.normal);
			ordered = removeCollinearVertices(ordered, vertices);
			if (ordered.size() < 3)
				continue;
			List<Vec3> polygon = new ArrayList<>(ordered.size());
			for (int vertex : ordered)
				polygon.add(vertices.get(vertex));
			if (polygonArea(polygon) < MIN_CONTACT_AREA)
				continue;

			List<Integer> faceKey = new ArrayList<>(ordered);
			faceKey.sort(Integer::compareTo);
			if (uniqueFaces.add(List.copyOf(faceKey)))
				faces.add(List.copyOf(polygon));
		}
		return List.copyOf(faces);
	}

	@Nullable
	private static Vec3 intersect(Plane first, Plane second, Plane third) {
		Vec3 secondCrossThird = second.normal.cross(third.normal);
		double determinant = first.normal.dot(secondCrossThird);
		if (Math.abs(determinant) <= INTERSECTION_EPSILON)
			return null;
		return secondCrossThird.scale(first.maximum)
			.add(third.normal.cross(first.normal).scale(second.maximum))
			.add(first.normal.cross(second.normal).scale(third.maximum))
			.scale(1.0d / determinant);
	}

	private static boolean insideAll(Vec3 point, List<Plane> planes) {
		for (Plane plane : planes)
			if (plane.signedDistance(point) > POLYHEDRON_EPSILON)
				return false;
		return true;
	}

	private static boolean containsPoint(List<Vec3> points, Vec3 candidate) {
		for (Vec3 point : points)
			if (point.distanceToSqr(candidate) <= VERTEX_MERGE_DISTANCE_SQR)
				return true;
		return false;
	}

	private static List<Integer> orderFace(List<Integer> face, List<Vec3> vertices, Vec3 normal) {
		Vec3 center = Vec3.ZERO;
		for (int vertex : face)
			center = center.add(vertices.get(vertex));
		center = center.scale(1.0d / face.size());
		final Vec3 faceCenter = center;

		Vec3 axisU = vertices.get(face.getFirst()).subtract(faceCenter);
		if (axisU.lengthSqr() <= DEGENERATE_EPSILON)
			return List.of();
		axisU = axisU.normalize();
		final Vec3 faceAxisU = axisU;
		final Vec3 faceAxisV = normal.cross(faceAxisU).normalize();
		List<Integer> ordered = new ArrayList<>(face);
		ordered.sort(Comparator.comparingDouble(vertex -> {
			Vec3 relative = vertices.get(vertex).subtract(faceCenter);
			return Math.atan2(relative.dot(faceAxisV), relative.dot(faceAxisU));
		}));

		Vec3 polygonNormal = vertices.get(ordered.get(1)).subtract(vertices.get(ordered.getFirst()))
			.cross(vertices.get(ordered.get(2)).subtract(vertices.get(ordered.getFirst())));
		if (polygonNormal.dot(normal) < 0.0d)
			java.util.Collections.reverse(ordered);
		return ordered;
	}

	private static List<Integer> removeCollinearVertices(List<Integer> face, List<Vec3> vertices) {
		List<Integer> simplified = new ArrayList<>(face);
		boolean changed;
		do {
			changed = false;
			for (int current = 0; current < simplified.size() && simplified.size() > 3; current++) {
				Vec3 previousPoint = vertices.get(simplified.get((current + simplified.size() - 1)
					% simplified.size()));
				Vec3 currentPoint = vertices.get(simplified.get(current));
				Vec3 nextPoint = vertices.get(simplified.get((current + 1) % simplified.size()));
				if (currentPoint.subtract(previousPoint).cross(nextPoint.subtract(currentPoint))
					.lengthSqr() > VERTEX_MERGE_DISTANCE_SQR)
					continue;
				simplified.remove(current);
				changed = true;
				break;
			}
		} while (changed);
		return List.copyOf(simplified);
	}

	public static Map<Integer, Vec3> componentOffsets(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		if (!SurgicalAssembly.validTopology(cubeCount, seams) || cutSeams.isEmpty())
			return Map.of();
		List<BitSet> components = SurgicalAssembly.components(cubeCount, presentCubes, seams, cutSeams);
		if (components.size() <= 1)
			return Map.of();

		Map<Integer, SurgicalModelRenderContext.CubeGeometry> byId = byId(cubes);
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
	private static Bounds unionBounds(BitSet cubes, Map<Integer, Bounds> baseBounds, Map<Integer, Vec3> offsets) {
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

		for (GridCell cell : orderedCells(workArea, targetX, targetZ)) {
			double offsetX = cell.centerX() - bounds.centerX();
			double offsetZ = cell.centerZ() - bounds.centerZ();
			Bounds placed = bounds.translate(new Vec3(offsetX, 0.0d, offsetZ));
			if (!fits(workArea, placed))
				continue;
			SurgicalTableLayout.Footprint footprint = footprint(-1, placed, cell);
			if (overlapsAny(footprint, occupiedFootprints))
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
		for (GridCell cell : orderedCells(workArea, targetX, targetZ)) {
			double deltaX = cell.centerX() - centerX;
			double deltaZ = cell.centerZ() - centerZ;
			if (!workArea.contains(minX + deltaX, minZ + deltaZ,
				maxX + deltaX, maxZ + deltaZ, DISTANCE_EPSILON))
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
				return new ConnectedPlacement(new Vec3(deltaX, 0.0d, deltaZ));
		}
		return null;
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
		if (!SurgicalAssembly.validTopology(cubeCount, seams) || workArea.isEmpty()
			|| envelopeGeometry == null || occupiedFootprints == null)
			return null;
		BitSet present = new BitSet(cubeCount);
		present.set(0, cubeCount);
		Map<Integer, Bounds> cubeBounds = layoutBounds(present, cubes);
		if (cubeBounds.size() != cubeCount)
			return null;
		List<BitSet> components = SurgicalAssembly.components(cubeCount, present, seams, new BitSet());
		if (components.isEmpty())
			return null;
		List<Integer> componentRoots = new ArrayList<>(components.size());
		List<Bounds> componentBounds = new ArrayList<>(components.size());
		for (BitSet component : components) {
			Bounds bounds = unionBounds(component, cubeBounds, Map.of());
			if (bounds == null)
				return null;
			componentRoots.add(component.nextSetBit(0));
			componentBounds.add(bounds);
		}
		Bounds envelope = Bounds.of(envelopeGeometry);
		if (envelope == null)
			return null;

		for (GridCell cell : orderedCells(workArea, targetX, targetZ)) {
			Vec3 delta = new Vec3(cell.centerX() - envelope.centerX(), 0.0d,
				cell.centerZ() - envelope.centerZ());
			Bounds placedEnvelope = envelope.translate(delta);
			if (!fits(workArea, placedEnvelope))
				continue;
			List<SurgicalTableLayout.Footprint> componentFootprints = new ArrayList<>(components.size());
			boolean blocked = false;
			for (int componentId = 0; componentId < componentBounds.size(); componentId++) {
				SurgicalTableLayout.Footprint footprint = footprint(componentRoots.get(componentId),
					componentBounds.get(componentId).translate(delta), null);
				if (overlapsAny(footprint, occupiedFootprints)) {
					blocked = true;
					break;
				}
				componentFootprints.add(footprint);
			}
			if (blocked)
				continue;
			SurgicalTableLayout.Footprint envelopeFootprint = footprint(-1, placedEnvelope, cell);
			PlacementPlan placement = new PlacementPlan(delta.x, delta.z,
				new SurgicalTableLayout.Proposal(List.of(), List.of(envelopeFootprint)));
			return new DiscoveredPlacementPlan(placement, componentFootprints);
		}
		return null;
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
			|| footprintsCollide(groups.getFirst(), occupiedFootprints, BATCH_CUT_CLEARANCE))
			return null;

		List<Vec3> stationary = new ArrayList<>(groups.size());
		for (int group = 0; group < groups.size(); group++)
			stationary.add(Vec3.ZERO);
		if (groups.size() == 1)
			return new ConnectedGroupLayout(stationary);

		return searchConnectedGroupLayout(groups, workArea, occupiedFootprints,
			BATCH_CUT_CLEARANCE, BATCH_CUT_MIN_TRAVEL);
	}

	@Nullable
	private static ConnectedGroupLayout searchConnectedGroupLayout(
		List<List<SurgicalTableLayout.Footprint>> groups, SurgicalTablePlane.WorkArea workArea,
		List<SurgicalTableLayout.Footprint> occupiedFootprints, double clearance, double minimumTravel) {
		List<SurgicalTableLayout.Footprint> permanent = new ArrayList<>(occupiedFootprints.size()
			+ groups.getFirst().size());
		permanent.addAll(occupiedFootprints);
		permanent.addAll(groups.getFirst());
		List<BatchPlacementRequest> requests = new ArrayList<>(groups.size() - 1);
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
			List<BatchPlacementCandidate> candidates = new ArrayList<>();
			for (GridCell cell : orderedCells(workArea, centerX, centerZ)) {
				Vec3 delta = new Vec3(cell.centerX() - centerX, 0.0d, cell.centerZ() - centerZ);
				if (delta.horizontalDistanceSqr() + DISTANCE_EPSILON < minimumTravelSqr)
					continue;
				List<SurgicalTableLayout.Footprint> translated = translateFootprints(footprints, delta);
				if (!footprintsFit(workArea, translated)
					|| footprintsCollide(translated, permanent, clearance))
					continue;
				candidates.add(new BatchPlacementCandidate(delta, translated));
			}
			if (candidates.isEmpty())
				return null;
			double area = Math.max(0.0d, maxX - minX) * Math.max(0.0d, maxZ - minZ);
			requests.add(new BatchPlacementRequest(groupId, area, List.copyOf(candidates)));
		}
		// Most-constrained and then physically largest groups go first. Candidate order itself remains
		// nearest-first, so the first complete arrangement stays compact and deterministic.
		requests.sort(Comparator.comparingInt((BatchPlacementRequest request) -> request.candidates.size())
			.thenComparing(Comparator.comparingDouble(BatchPlacementRequest::area).reversed())
			.thenComparingInt(BatchPlacementRequest::groupId));
		List<Vec3> deltas = new ArrayList<>(groups.size());
		for (int group = 0; group < groups.size(); group++)
			deltas.add(Vec3.ZERO);
		List<SurgicalTableLayout.Footprint> placed = new ArrayList<>(permanent);
		int[] examined = { 0 };
		if (!placeConnectedGroups(requests, 0, placed, deltas, clearance, examined))
			return null;
		return new ConnectedGroupLayout(deltas);
	}

	private static boolean placeConnectedGroups(List<BatchPlacementRequest> requests, int requestIndex,
		List<SurgicalTableLayout.Footprint> placed, List<Vec3> deltas, double clearance, int[] examined) {
		if (requestIndex >= requests.size())
			return true;
		if (examined[0] >= MAX_BATCH_LAYOUT_SEARCH_NODES)
			return false;
		BatchPlacementRequest request = requests.get(requestIndex);
		for (BatchPlacementCandidate candidate : request.candidates) {
			if (++examined[0] > MAX_BATCH_LAYOUT_SEARCH_NODES)
				return false;
			if (footprintsCollide(candidate.footprints, placed, clearance))
				continue;
			int previousSize = placed.size();
			placed.addAll(candidate.footprints);
			deltas.set(request.groupId, candidate.delta);
			if (placeConnectedGroups(requests, requestIndex + 1, placed, deltas, clearance, examined))
				return true;
			placed.subList(previousSize, placed.size()).clear();
			deltas.set(request.groupId, Vec3.ZERO);
		}
		return false;
	}

	private static List<SurgicalTableLayout.Footprint> translateFootprints(
		List<SurgicalTableLayout.Footprint> footprints, Vec3 delta) {
		return footprints.stream().map(footprint -> new SurgicalTableLayout.Footprint(
			footprint.componentRoot(), footprint.minX() + delta.x, footprint.minZ() + delta.z,
			footprint.maxX() + delta.x, footprint.maxZ() + delta.z,
			SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED)).toList();
	}

	private static boolean footprintsFit(SurgicalTablePlane.WorkArea workArea,
		List<SurgicalTableLayout.Footprint> footprints) {
		for (SurgicalTableLayout.Footprint footprint : footprints)
			if (!workArea.contains(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ(),
				DISTANCE_EPSILON))
				return false;
		return true;
	}

	private static boolean footprintsCollide(List<SurgicalTableLayout.Footprint> first,
		List<SurgicalTableLayout.Footprint> second, double clearance) {
		for (SurgicalTableLayout.Footprint candidate : first)
			for (SurgicalTableLayout.Footprint obstacle : second)
				if (candidate.minX() < obstacle.maxX() + clearance - DISTANCE_EPSILON
					&& candidate.maxX() > obstacle.minX() - clearance + DISTANCE_EPSILON
					&& candidate.minZ() < obstacle.maxZ() + clearance - DISTANCE_EPSILON
					&& candidate.maxZ() > obstacle.minZ() - clearance + DISTANCE_EPSILON)
					return true;
		return false;
	}

	/** Builds a proposal for a cut that does not create a new detached component. */
	@Nullable
	public static PlannedLayout currentLayout(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet proposedCuts,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return planSnappedLayout(cubeCount, presentCubes, seams, proposedCuts, cubes, currentOffsets,
			workArea, List.of(), occupiedFootprints, false, false);
	}

	/** Preserves the exact overlapping layout of one source in a packed glued assembly. */
	@Nullable
	public static PlannedLayout preserveCompositeLayout(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return planSnappedLayout(cubeCount, presentCubes, seams, cutSeams, cubes, currentOffsets,
			workArea, List.of(), occupiedFootprints, true, false);
	}

	/** Snaps one detached component to the legal slot nearest the supplied world-space target. */
	@Nullable
	public static PlannedLayout snapComponent(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet proposedCuts,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, BitSet movingComponent, double targetX, double targetZ,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		return planSnappedLayout(cubeCount, presentCubes, seams, proposedCuts, cubes, currentOffsets,
			workArea, List.of(new SnapRequest((BitSet) movingComponent.clone(), targetX, targetZ)),
			occupiedFootprints, false, true);
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
		Map<Integer, Bounds> baseBounds = layoutBounds(presentCubes, cubes);
		if (baseBounds.size() < presentCubes.cardinality())
			return null;
		List<SnapRequest> requests = new ArrayList<>(movingComponents.size());
		for (BitSet component : movingComponents) {
			Bounds bounds = unionBounds(component, baseBounds, currentOffsets);
			if (bounds == null)
				return null;
			requests.add(new SnapRequest((BitSet) component.clone(), bounds.centerX(), bounds.centerZ()));
		}
		return planSnappedLayout(cubeCount, presentCubes, seams, proposedCuts, cubes, currentOffsets,
			workArea, requests, occupiedFootprints, false, false);
	}

	/** Applies one rigid horizontal delta to a union of complete components and rebuilds its layout. */
	@Nullable
	public static PlannedLayout translateComponents(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet proposedCuts,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, BitSet translatedCubes, Vec3 delta,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		if (!SurgicalAssembly.validTopology(cubeCount, seams) || workArea.isEmpty()
			|| translatedCubes == null || delta == null || occupiedFootprints == null
			|| !Double.isFinite(delta.x) || !Double.isFinite(delta.y) || !Double.isFinite(delta.z)
			|| Math.abs(delta.y) > DISTANCE_EPSILON)
			return null;
		Map<Integer, Bounds> baseBounds = layoutBounds(presentCubes, cubes);
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
			Bounds bounds = unionBounds(component, baseBounds, plannedOffsets);
			if (bounds == null || !fits(workArea, bounds))
				return null;
			footprints.addAll(footprints(root, component, baseBounds, plannedOffsets, null));
		}
		for (SurgicalTableLayout.Footprint footprint : footprints)
			if (overlapsAny(footprint, occupiedFootprints))
				return null;

		List<SurgicalTableLayout.CubeOffset> offsets = new ArrayList<>(presentCubes.cardinality());
		for (int cube = presentCubes.nextSetBit(0); cube >= 0; cube = presentCubes.nextSetBit(cube + 1)) {
			Vec3 offset = plannedOffsets.getOrDefault(cube, Vec3.ZERO);
			offsets.add(new SurgicalTableLayout.CubeOffset(cube, offset.x, offset.y, offset.z));
		}
		return new PlannedLayout(Map.copyOf(plannedOffsets),
			new SurgicalTableLayout.Proposal(offsets, footprints));
	}

	/** Aligns every detached component so its rendered outer bounds touch the table surface. */
	public static Map<Integer, Vec3> groundComponents(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> horizontalOffsets,
		double surfaceY) {
		return groundComponents(cubeCount, presentCubes, seams, cutSeams, cubes, horizontalOffsets,
			surfaceY, false);
	}

	/** Grounds even an uncut component, used after its last glue connection has been severed. */
	public static Map<Integer, Vec3> groundAllComponents(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> horizontalOffsets,
		double surfaceY) {
		return groundComponents(cubeCount, presentCubes, seams, cutSeams, cubes, horizontalOffsets,
			surfaceY, true);
	}

	/** Grounds each component from the same unified connection graph used by selection and packing. */
	@Nullable
	public static <K> Map<K, Map<Integer, Vec3>> groundConnectedBodies(List<GroundingBody<K>> bodies,
		List<GroundingLink<K>> links, double surfaceY) {
		if (bodies.isEmpty() || !Double.isFinite(surfaceY))
			return null;
		Map<K, GroundingBody<K>> indexed = new LinkedHashMap<>();
		Map<K, Map<Integer, SurgicalModelRenderContext.CubeGeometry>> cubes = new HashMap<>();
		Map<K, Map<Integer, Vec3>> grounded = new LinkedHashMap<>();
		List<SurgicalConnectionGraph.Body<K>> connectionBodies = new ArrayList<>(bodies.size());
		for (GroundingBody<K> body : bodies) {
			if (body == null || body.key() == null || indexed.putIfAbsent(body.key(), body) != null
				|| !SurgicalAssembly.validTopology(body.cubeCount(), body.seams()))
				return null;
			Map<Integer, SurgicalModelRenderContext.CubeGeometry> bodyCubes = indexCubeGeometry(body.cubes());
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
		for (GroundingLink<K> link : links) {
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
				GroundingBody<K> body = indexed.get(entry.getKey());
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
				GroundingBody<K> body = indexed.get(entry.getKey());
				Map<Integer, Vec3> bodyGrounded = grounded.get(entry.getKey());
				for (int cube = entry.getValue().nextSetBit(0); cube >= 0;
					cube = entry.getValue().nextSetBit(cube + 1)) {
					Vec3 adjusted = body.offsets().getOrDefault(cube, Vec3.ZERO).add(0.0d, lift, 0.0d);
					if (adjusted.lengthSqr() <= DISTANCE_EPSILON)
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

	private static Map<Integer, Vec3> groundComponents(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet cutSeams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> horizontalOffsets,
		double surfaceY, boolean includeUncut) {
		if (!Double.isFinite(surfaceY) || !SurgicalAssembly.validTopology(cubeCount, seams)
			|| !includeUncut && cutSeams.isEmpty())
			return Map.copyOf(horizontalOffsets);

		List<BitSet> components = SurgicalAssembly.components(cubeCount, presentCubes, seams, cutSeams);
		if (components.isEmpty())
			return Map.copyOf(horizontalOffsets);
		Map<Integer, Bounds> baseBounds = layoutBounds(presentCubes, cubes);
		if (baseBounds.size() < presentCubes.cardinality())
			return Map.copyOf(horizontalOffsets);

		Map<Integer, Vec3> grounded = new HashMap<>();
		for (BitSet component : components) {
			double bottomY = Double.POSITIVE_INFINITY;
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
				Bounds bounds = baseBounds.get(cube);
				if (bounds == null)
					continue;
				bottomY = Math.min(bottomY,
					bounds.minY + horizontalOffsets.getOrDefault(cube, Vec3.ZERO).y);
			}
			if (!Double.isFinite(bottomY))
				continue;
			double groundDelta = surfaceY - bottomY;
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
				Vec3 offset = horizontalOffsets.getOrDefault(cube, Vec3.ZERO);
				Vec3 adjusted = new Vec3(offset.x, offset.y + groundDelta, offset.z);
				if (adjusted.lengthSqr() > DISTANCE_EPSILON)
					grounded.put(cube, adjusted);
			}
		}
		return Map.copyOf(grounded);
	}

	@Nullable
	private static PlannedLayout planSnappedLayout(int cubeCount, BitSet presentCubes,
		List<SurgicalAssembly.Seam> seams, BitSet proposedCuts,
		List<SurgicalModelRenderContext.CubeGeometry> cubes, Map<Integer, Vec3> currentOffsets,
		SurgicalTablePlane.WorkArea workArea, List<SnapRequest> requests,
		List<SurgicalTableLayout.Footprint> occupiedFootprints, boolean allowComponentOverlap,
		boolean groundComponents) {
		if (!SurgicalAssembly.validTopology(cubeCount, seams) || workArea.isEmpty())
			return null;
		Map<Integer, Bounds> baseBounds = layoutBounds(presentCubes, cubes);
		if (baseBounds.size() < presentCubes.cardinality())
			return null;
		List<BitSet> components = SurgicalAssembly.components(cubeCount, presentCubes, seams, proposedCuts);
		Map<Integer, Vec3> plannedOffsets = new HashMap<>();
		for (int cube = presentCubes.nextSetBit(0); cube >= 0; cube = presentCubes.nextSetBit(cube + 1))
			plannedOffsets.put(cube, currentOffsets.getOrDefault(cube, Vec3.ZERO));
		Map<Integer, GridCell> snapped = new HashMap<>();

		for (SnapRequest request : requests) {
			BitSet moving = matchingComponent(components, request.component);
			if (moving == null)
				return null;
			Bounds movingBounds = unionBounds(moving, baseBounds, plannedOffsets);
			if (movingBounds == null)
				return null;
			GridCell selected = null;
			Vec3 selectedDelta = Vec3.ZERO;
			for (GridCell cell : orderedCells(workArea, request.targetX, request.targetZ)) {
				Vec3 delta = new Vec3(cell.centerX() - movingBounds.centerX(), 0.0d,
					cell.centerZ() - movingBounds.centerZ());
				Bounds candidate = movingBounds.translate(delta);
				if (!fits(workArea, candidate)
					|| collidesHorizontally(candidate, moving, components, baseBounds, plannedOffsets, delta,
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
					Bounds bounds = baseBounds.get(cube);
					if (bounds != null)
						lowestY = Math.min(lowestY,
							bounds.minY + plannedOffsets.getOrDefault(cube, Vec3.ZERO).y);
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
			Bounds bounds = unionBounds(component, baseBounds, plannedOffsets);
			if (bounds == null || !fits(workArea, bounds))
				return null;
			GridCell cell = snapped.get(root);
			footprints.addAll(footprints(root, component, baseBounds, plannedOffsets, cell));
		}
		if (!allowComponentOverlap)
			for (int first = 0; first < footprints.size(); first++)
				for (int second = first + 1; second < footprints.size(); second++)
					if (footprints.get(first).componentRoot() != footprints.get(second).componentRoot()
						&& footprints.get(first).conflictsWith(footprints.get(second)))
						return null;
		for (SurgicalTableLayout.Footprint footprint : footprints)
			if (overlapsAny(footprint, occupiedFootprints))
				return null;

		List<SurgicalTableLayout.CubeOffset> offsets = new ArrayList<>(presentCubes.cardinality());
		for (int cube = presentCubes.nextSetBit(0); cube >= 0; cube = presentCubes.nextSetBit(cube + 1)) {
			Vec3 offset = plannedOffsets.getOrDefault(cube, Vec3.ZERO);
			offsets.add(new SurgicalTableLayout.CubeOffset(cube, offset.x, offset.y, offset.z));
		}
		return new PlannedLayout(Map.copyOf(plannedOffsets),
			new SurgicalTableLayout.Proposal(offsets, footprints));
	}

	private static Map<Integer, Bounds> layoutBounds(BitSet presentCubes,
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		Map<Integer, Bounds> bounds = new HashMap<>();
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			if (presentCubes.get(cube.cubeId()))
				bounds.putIfAbsent(cube.cubeId(), Bounds.of(cube));
		return bounds;
	}

	private static Map<Integer, SurgicalModelRenderContext.CubeGeometry> indexCubeGeometry(
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		Map<Integer, SurgicalModelRenderContext.CubeGeometry> indexed = new HashMap<>();
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			if (cube != null)
				indexed.putIfAbsent(cube.cubeId(), cube);
		return Map.copyOf(indexed);
	}

	@Nullable
	private static BitSet matchingComponent(List<BitSet> components, BitSet requested) {
		for (BitSet component : components)
			if (component.equals(requested))
				return component;
		return null;
	}

	private static boolean collidesHorizontally(Bounds candidate, BitSet moving, List<BitSet> components,
		Map<Integer, Bounds> baseBounds, Map<Integer, Vec3> offsets, Vec3 delta,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		for (BitSet component : components) {
			if (component.equals(moving))
				continue;
			Bounds obstacleEnvelope = unionBounds(component, baseBounds, offsets);
			if (obstacleEnvelope == null || !candidate.conflictsHorizontally(obstacleEnvelope))
				continue;
			for (int movingCube = moving.nextSetBit(0); movingCube >= 0;
				movingCube = moving.nextSetBit(movingCube + 1)) {
				Bounds movingBounds = baseBounds.get(movingCube);
				if (movingBounds == null)
					continue;
				movingBounds = movingBounds.translate(offsets.getOrDefault(movingCube, Vec3.ZERO).add(delta));
				for (int obstacleCube = component.nextSetBit(0); obstacleCube >= 0;
					obstacleCube = component.nextSetBit(obstacleCube + 1)) {
					Bounds obstacleBounds = baseBounds.get(obstacleCube);
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
			Bounds movingBounds = baseBounds.get(movingCube);
			if (movingBounds == null)
				continue;
			movingBounds = movingBounds.translate(offsets.getOrDefault(movingCube, Vec3.ZERO).add(delta));
			for (SurgicalTableLayout.Footprint occupied : occupiedFootprints)
				if (overlapsHorizontally(movingBounds, occupied))
					return true;
		}
		return false;
	}

	private static boolean overlapsAny(SurgicalTableLayout.Footprint footprint,
		List<SurgicalTableLayout.Footprint> occupiedFootprints) {
		for (SurgicalTableLayout.Footprint occupied : occupiedFootprints)
			if (footprint.conflictsWith(occupied))
				return true;
		return false;
	}

	private static boolean overlapsHorizontally(Bounds bounds, SurgicalTableLayout.Footprint footprint) {
		return bounds.minX < footprint.maxX() + SurgicalTableLayout.COMPONENT_CLEARANCE - DISTANCE_EPSILON
			&& bounds.maxX > footprint.minX() - SurgicalTableLayout.COMPONENT_CLEARANCE + DISTANCE_EPSILON
			&& bounds.minZ < footprint.maxZ() + SurgicalTableLayout.COMPONENT_CLEARANCE - DISTANCE_EPSILON
			&& bounds.maxZ > footprint.minZ() - SurgicalTableLayout.COMPONENT_CLEARANCE + DISTANCE_EPSILON;
	}

	private static boolean fits(SurgicalTablePlane.WorkArea workArea, Bounds bounds) {
		return workArea.contains(bounds.minX, bounds.minZ, bounds.maxX, bounds.maxZ, DISTANCE_EPSILON);
	}

	private static List<GridCell> orderedCells(SurgicalTablePlane.WorkArea workArea, double targetX,
		double targetZ) {
		List<GridCell> cells = new ArrayList<>(workArea.tileArea() * SurgicalTableLayout.SLOTS_PER_TILE);
		for (BlockPos tile : workArea.tiles()) {
			int minGridX = tile.getX() * SurgicalTableLayout.SUBDIVISIONS;
			int minGridZ = tile.getZ() * SurgicalTableLayout.SUBDIVISIONS;
			for (int xOffset = 0; xOffset < SurgicalTableLayout.SUBDIVISIONS; xOffset++)
				for (int zOffset = 0; zOffset < SurgicalTableLayout.SUBDIVISIONS; zOffset++)
					cells.add(new GridCell(minGridX + xOffset, minGridZ + zOffset));
		}
		cells.sort(Comparator.comparingDouble((GridCell cell) -> cell.distanceToSqr(targetX, targetZ))
			.thenComparingInt(GridCell::gridX).thenComparingInt(GridCell::gridZ));
		return cells;
	}

	private static SurgicalTableLayout.Footprint footprint(int root, Bounds bounds, @Nullable GridCell cell) {
		return new SurgicalTableLayout.Footprint(root, bounds.minX, bounds.minZ, bounds.maxX, bounds.maxZ,
			cell == null ? SurgicalTableLayout.UNSNAPPED : cell.gridX,
			cell == null ? SurgicalTableLayout.UNSNAPPED : cell.gridZ);
	}

	private static List<SurgicalTableLayout.Footprint> footprints(int root, BitSet component,
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

	private static List<Vec3> clipAgainstPlanes(List<Vec3> polygon,
		List<Plane> planes, double tolerance) {
		List<Vec3> clipped = List.copyOf(polygon);
		for (Plane plane : planes) {
			if (clipped.isEmpty())
				break;
			List<Vec3> next = new ArrayList<>();
			Vec3 previous = clipped.getLast();
			double previousDistance = plane.signedDistance(previous) - tolerance;
			boolean previousInside = previousDistance <= INTERSECTION_EPSILON;
			for (Vec3 current : clipped) {
				double currentDistance = plane.signedDistance(current) - tolerance;
				boolean currentInside = currentDistance <= INTERSECTION_EPSILON;
				if (previousInside != currentInside) {
					double denominator = previousDistance - currentDistance;
					if (Math.abs(denominator) > INTERSECTION_EPSILON) {
						double amount = previousDistance / denominator;
						next.add(previous.add(current.subtract(previous).scale(amount)));
					}
				}
				if (currentInside)
					next.add(current);
				previous = current;
				previousDistance = currentDistance;
				previousInside = currentInside;
			}
			clipped = simplifyPolygon(next);
		}
		return List.copyOf(clipped);
	}

	private static List<Plane> clipPlanes(SurgicalModelRenderContext.CubeGeometry cube) {
		List<Vec3> corners = cube.corners();
		Vec3 cubeCenter = center(cube);
		List<Plane> planes = new ArrayList<>(6);
		for (int[] indices : CUBE_FACES) {
			Vec3 first = corners.get(indices[0]);
			Vec3 faceCenter = first.add(corners.get(indices[1]))
				.add(corners.get(indices[2])).add(corners.get(indices[3])).scale(0.25d);
			Vec3 normal = corners.get(indices[1]).subtract(first)
				.cross(corners.get(indices[3]).subtract(first));
			if (normal.lengthSqr() <= DEGENERATE_EPSILON)
				continue;
			normal = normal.normalize();
			if (normal.dot(faceCenter.subtract(cubeCenter)) < 0.0d)
				normal = normal.scale(-1.0d);
			planes.add(new Plane(normal, normal.dot(first)));
		}
		if (planes.size() == 6)
			return planes;

		// Zero-thickness model cubes have degenerate side faces. Treat them as a
		// tolerance-thick oriented box so their visible rectangle can still form a seam.
		List<Vec3> axes = orthonormalAxes(corners.get(1).subtract(corners.get(0)),
			corners.get(2).subtract(corners.get(0)), corners.get(4).subtract(corners.get(0)));
		planes.clear();
		for (Vec3 axis : axes) {
			double minimum = Double.POSITIVE_INFINITY;
			double maximum = Double.NEGATIVE_INFINITY;
			for (Vec3 corner : corners) {
				double projection = axis.dot(corner);
				minimum = Math.min(minimum, projection);
				maximum = Math.max(maximum, projection);
			}
			planes.add(new Plane(axis, maximum));
			planes.add(new Plane(axis.scale(-1.0d), -minimum));
		}
		return planes;
	}

	private static List<Vec3> orthonormalAxes(Vec3... candidates) {
		List<Vec3> axes = new ArrayList<>(3);
		for (Vec3 candidate : candidates) {
			Vec3 axis = candidate;
			for (Vec3 existing : axes)
				axis = axis.subtract(existing.scale(axis.dot(existing)));
			if (axis.lengthSqr() > DEGENERATE_EPSILON)
				axes.add(axis.normalize());
		}
		if (axes.isEmpty())
			axes.add(new Vec3(1.0d, 0.0d, 0.0d));
		if (axes.size() == 1) {
			Vec3 first = axes.getFirst();
			Vec3 helper = Math.abs(first.y) < 0.9d ? new Vec3(0.0d, 1.0d, 0.0d)
				: new Vec3(1.0d, 0.0d, 0.0d);
			axes.add(first.cross(helper).normalize());
		}
		if (axes.size() == 2)
			axes.add(axes.get(0).cross(axes.get(1)).normalize());
		return List.copyOf(axes.subList(0, 3));
	}

	private static List<Vec3> simplifyPolygon(List<Vec3> polygon) {
		if (polygon.size() < 2)
			return polygon;
		List<Vec3> simplified = new ArrayList<>(polygon.size());
		for (Vec3 point : polygon)
			if (simplified.isEmpty() || simplified.getLast().distanceToSqr(point) > DEGENERATE_EPSILON)
				simplified.add(point);
		if (simplified.size() > 1
			&& simplified.getFirst().distanceToSqr(simplified.getLast()) <= DEGENERATE_EPSILON)
			simplified.removeLast();
		return simplified;
	}

	private static double polygonArea(List<Vec3> polygon) {
		if (polygon.size() < 3)
			return 0.0d;
		Vec3 origin = polygon.getFirst();
		double area = 0.0d;
		for (int i = 1; i + 1 < polygon.size(); i++)
			area += polygon.get(i).subtract(origin).cross(polygon.get(i + 1).subtract(origin)).length() * 0.5d;
		return area;
	}

	private static double volume(SurgicalModelRenderContext.CubeGeometry cube) {
		List<Vec3> corners = cube.corners();
		return corners.get(0).distanceTo(corners.get(1))
			* corners.get(0).distanceTo(corners.get(2))
			* corners.get(0).distanceTo(corners.get(4));
	}

	private static Vec3 faceCenter(List<Vec3> face) {
		return face.get(0).add(face.get(1)).add(face.get(2)).add(face.get(3)).scale(0.25d);
	}

	private static Map<Integer, SurgicalModelRenderContext.CubeGeometry> byId(
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		Map<Integer, SurgicalModelRenderContext.CubeGeometry> result = new HashMap<>();
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			result.putIfAbsent(cube.cubeId(), cube);
		return result;
	}

	private static Map<Integer, PreparedCube> preparedById(
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		Map<Integer, PreparedCube> result = new HashMap<>();
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			result.computeIfAbsent(cube.cubeId(), ignored -> PreparedCube.of(cube));
		return result;
	}

	public record ContactTopology(List<SurgicalAssembly.Seam> seams, List<Contact> contacts) {
		private static final ContactTopology EMPTY = new ContactTopology(List.of(), List.of());

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

	private record PreparedCube(SurgicalModelRenderContext.CubeGeometry geometry, Vec3 center,
		double volume, List<Plane> planes, Bounds bounds) {
		private static PreparedCube of(SurgicalModelRenderContext.CubeGeometry geometry) {
			return new PreparedCube(geometry, SurgicalClientTopology.center(geometry),
				SurgicalClientTopology.volume(geometry),
				List.copyOf(clipPlanes(geometry)), Bounds.of(geometry));
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

	private record SnapRequest(BitSet component, double targetX, double targetZ) {}
	private record BatchPlacementRequest(int groupId, double area,
		List<BatchPlacementCandidate> candidates) {}
	private record BatchPlacementCandidate(Vec3 delta,
		List<SurgicalTableLayout.Footprint> footprints) {}

	private record GridCell(int gridX, int gridZ) {
		private double centerX() {
			return SurgicalTableLayout.gridCenter(gridX);
		}

		private double centerZ() {
			return SurgicalTableLayout.gridCenter(gridZ);
		}

		private double distanceToSqr(double x, double z) {
			double dx = centerX() - x;
			double dz = centerZ() - z;
			return dx * dx + dz * dz;
		}
	}

	private record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		private static Bounds of(SurgicalModelRenderContext.CubeGeometry geometry) {
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

		private boolean overlapsWithin(Bounds other, double tolerance) {
			return minX <= other.maxX + tolerance && maxX + tolerance >= other.minX
				&& minY <= other.maxY + tolerance && maxY + tolerance >= other.minY
				&& minZ <= other.maxZ + tolerance && maxZ + tolerance >= other.minZ;
		}

		private Bounds translate(Vec3 offset) {
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

		private boolean conflictsHorizontally(Bounds other) {
			return minX < other.maxX + SurgicalTableLayout.COMPONENT_CLEARANCE - DISTANCE_EPSILON
				&& maxX > other.minX - SurgicalTableLayout.COMPONENT_CLEARANCE + DISTANCE_EPSILON
				&& minZ < other.maxZ + SurgicalTableLayout.COMPONENT_CLEARANCE - DISTANCE_EPSILON
				&& maxZ > other.minZ - SurgicalTableLayout.COMPONENT_CLEARANCE + DISTANCE_EPSILON;
		}

		private double centerX() {
			return (minX + maxX) * 0.5d;
		}

		private double centerZ() {
			return (minZ + maxZ) * 0.5d;
		}
	}

	private record Plane(Vec3 normal, double maximum) {
		private double signedDistance(Vec3 point) {
			return normal.dot(point) - maximum;
		}
	}
}
