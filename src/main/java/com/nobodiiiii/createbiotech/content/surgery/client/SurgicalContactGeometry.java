package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalContactGeometry implementation; public entry points remain on SurgicalClientTopology. */
final class SurgicalContactGeometry {
	private SurgicalContactGeometry() {}


	/** Builds one stable seam for every intersecting or tolerance-adjacent pair of model cubes. */
	static SurgicalClientTopology.ContactTopology buildContactTopology(int cubeCount,
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		if (!SurgicalAssembly.validCubeCount(cubeCount))
			return SurgicalClientTopology.ContactTopology.EMPTY;
		Map<Integer, SurgicalClientTopology.PreparedCube> byId = preparedById(cubes);
		if (byId.size() != cubeCount)
			return SurgicalClientTopology.ContactTopology.EMPTY;

		// Broad-phase sweep avoids clipping every possible pair. Large models usually
		// contain many distant decorative cubes, so only AABBs close enough to touch
		// advance to the comparatively expensive oriented-face clipping below.
		List<SurgicalClientTopology.PreparedCube> sweep = new ArrayList<>(byId.values());
		sweep.sort(Comparator.comparingDouble((SurgicalClientTopology.PreparedCube cube) -> cube.bounds().minX())
			.thenComparingInt(cube -> cube.geometry().cubeId()));
		List<BitSet> candidates = new ArrayList<>(cubeCount);
		for (int cube = 0; cube < cubeCount; cube++)
			candidates.add(new BitSet(cubeCount));
		for (int firstIndex = 0; firstIndex < sweep.size(); firstIndex++) {
			SurgicalClientTopology.PreparedCube first = sweep.get(firstIndex);
			for (int secondIndex = firstIndex + 1; secondIndex < sweep.size(); secondIndex++) {
				SurgicalClientTopology.PreparedCube second = sweep.get(secondIndex);
				if (second.bounds().minX() > first.bounds().maxX() + SurgicalClientTopology.CONTACT_TOLERANCE)
					break;
				if (!first.bounds().overlapsWithin(second.bounds(), SurgicalClientTopology.CONTACT_TOLERANCE))
					continue;
				int lower = Math.min(first.geometry().cubeId(), second.geometry().cubeId());
				int upper = Math.max(first.geometry().cubeId(), second.geometry().cubeId());
				candidates.get(lower).set(upper);
			}
		}

		List<SurgicalAssembly.Seam> seams = new ArrayList<>();
		List<SurgicalClientTopology.Contact> contacts = new ArrayList<>();
		for (int first = 0; first < cubeCount && seams.size() < SurgicalAssembly.MAX_SEAMS; first++) {
			for (int second = candidates.get(first).nextSetBit(first + 1);
				second >= 0 && seams.size() < SurgicalAssembly.MAX_SEAMS;
				second = candidates.get(first).nextSetBit(second + 1)) {
				SurgicalAssembly.Seam seam = SurgicalAssembly.Seam.of(first, second);
				SurgicalClientTopology.Contact contact = contactBetween(seam, byId);
				if (contact == null)
					continue;
				seams.add(seam);
				contacts.add(contact);
			}
		}
		return new SurgicalClientTopology.ContactTopology(seams, contacts);
	}

	static List<SurgicalClientTopology.Contact> contactsFor(List<SurgicalAssembly.Seam> seams,
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		Map<Integer, SurgicalClientTopology.PreparedCube> byId = preparedById(cubes);
		List<SurgicalClientTopology.Contact> contacts = new ArrayList<>(seams.size());
		for (SurgicalAssembly.Seam seam : seams) {
			SurgicalClientTopology.Contact contact = contactBetween(seam, byId);
			if (contact != null)
				contacts.add(contact);
		}
		return List.copyOf(contacts);
	}

	@Nullable
	static SurgicalClientTopology.Contact contactBetween(SurgicalAssembly.Seam seam,
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		return contactBetween(seam, preparedById(cubes));
	}

	/** Exact boundary lines, including edge-only contact of zero-thickness model wings. */
	static List<SurgicalClientTopology.Edge> intersectionEdges(SurgicalModelRenderContext.CubeGeometry first,
		SurgicalModelRenderContext.CubeGeometry second) {
		List<SurgicalClientTopology.Plane> planes = new ArrayList<>(clipPlanes(first));
		planes.addAll(clipPlanes(second));
		List<Vec3> vertices = convexIntersectionVertices(planes);
		List<SurgicalClientTopology.Edge> edges = new ArrayList<>();
		for (int start = 0; start < vertices.size(); start++)
			for (int end = start + 1; end < vertices.size(); end++) {
				Vec3 firstNormal = null;
				for (SurgicalClientTopology.Plane plane : planes) {
					if (Math.abs(plane.signedDistance(vertices.get(start))) > SurgicalClientTopology.POLYHEDRON_EPSILON
						|| Math.abs(plane.signedDistance(vertices.get(end))) > SurgicalClientTopology.POLYHEDRON_EPSILON)
						continue;
					if (firstNormal == null) {
						firstNormal = plane.normal();
					} else if (firstNormal.cross(plane.normal()).lengthSqr() > SurgicalClientTopology.INTERSECTION_EPSILON) {
						// Two independent boundary planes define a line; a face diagonal has only one.
						edges.add(new SurgicalClientTopology.Edge(vertices.get(start), vertices.get(end)));
						break;
					}
				}
			}
		return List.copyOf(edges);
	}

	/** Accepts real solid, face, edge or point contact without the seam builder's adjacency tolerance. */
	static boolean cubesActuallyIntersect(SurgicalModelRenderContext.CubeGeometry firstGeometry,
		SurgicalModelRenderContext.CubeGeometry secondGeometry) {
		return cubesIntersect(firstGeometry, secondGeometry, 0.0d);
	}

	/** Uses seam discovery's adjacency tolerance without requiring a drawable contact polygon. */
	static boolean cubesConnectWithinTolerance(
		SurgicalModelRenderContext.CubeGeometry firstGeometry,
		SurgicalModelRenderContext.CubeGeometry secondGeometry) {
		return cubesIntersect(firstGeometry, secondGeometry, SurgicalClientTopology.CONTACT_TOLERANCE);
	}

	private static boolean cubesIntersect(SurgicalModelRenderContext.CubeGeometry firstGeometry,
		SurgicalModelRenderContext.CubeGeometry secondGeometry, double tolerance) {
		if (firstGeometry == null || secondGeometry == null)
			return false;
		SurgicalClientTopology.PreparedCube first = SurgicalClientTopology.PreparedCube.of(firstGeometry);
		SurgicalClientTopology.PreparedCube second = SurgicalClientTopology.PreparedCube.of(secondGeometry);
		if (!first.bounds().overlapsWithin(second.bounds(), tolerance + SurgicalClientTopology.POLYHEDRON_EPSILON))
			return false;
		SurgicalClientTopology.PreparedCube smaller = first.volume() <= second.volume() ? first : second;
		SurgicalClientTopology.PreparedCube other = smaller == first ? second : first;
		List<SurgicalClientTopology.Plane> planes = new ArrayList<>(smaller.planes());
		for (SurgicalClientTopology.Plane plane : other.planes())
			planes.add(new SurgicalClientTopology.Plane(plane.normal(), plane.maximum() + tolerance));
		// A line or single vertex is enough to retain a connection. Polygon extraction intentionally
		// drops these zero-area intersections and is only appropriate when drawing the contact itself.
		return !convexIntersectionVertices(planes).isEmpty();
	}

	@Nullable
	static SurgicalClientTopology.Contact contactBetween(SurgicalAssembly.Seam seam,
		Map<Integer, SurgicalClientTopology.PreparedCube> byId) {
		SurgicalClientTopology.PreparedCube first = byId.get(seam.first());
		SurgicalClientTopology.PreparedCube second = byId.get(seam.second());
		if (first == null || second == null)
			return null;

		SurgicalClientTopology.PreparedCube smaller = first.volume() <= second.volume() ? first : second;
		SurgicalClientTopology.PreparedCube other = smaller == first ? second : first;
		List<List<Vec3>> faces = contactFaces(smaller, other);
		return faces.isEmpty() ? null : new SurgicalClientTopology.Contact(seam, smaller.geometry().cubeId(), faces);
	}

	/** Drawable contact polygons for seam discovery and highlighting; zero-area contacts have no face. */
	static List<List<Vec3>> contactFaces(SurgicalClientTopology.PreparedCube smaller, SurgicalClientTopology.PreparedCube other) {
		if (!smaller.bounds().overlapsWithin(other.bounds(), SurgicalClientTopology.CONTACT_TOLERANCE))
			return List.of();
		// A pair whose bounds are disjoint cannot share an intersection solid, and
		// convexIntersectionFaces is a C(12,3) triple loop allocating several Vec3 per triple. The
		// seam builder deliberately admits merely tolerance-adjacent neighbours, so the empty result
		// is the common case; those pairs now fall straight through to the planar fallback below,
		// which is where they were always resolved. Same rejection cubesActuallyIntersect makes.
		if (smaller.bounds().overlapsWithin(other.bounds(), SurgicalClientTopology.POLYHEDRON_EPSILON)) {
			List<SurgicalClientTopology.Plane> intersectionPlanes = new ArrayList<>(smaller.planes().size() + other.planes().size());
			intersectionPlanes.addAll(smaller.planes());
			intersectionPlanes.addAll(other.planes());
			List<List<Vec3>> intersectionFaces = convexIntersectionFaces(intersectionPlanes);
			if (!intersectionFaces.isEmpty())
				return intersectionFaces;
		}

		// Keep near-adjacent model parts connected. This is deliberately a planar
		// fallback: only real overlap is represented by the complete intersection solid.
		List<Vec3> bestContact = List.of();
		double bestCenterDistance = Double.MAX_VALUE;
		double bestArea = 0.0d;
		for (int[] indices : SurgicalClientTopology.CUBE_FACES) {
			List<Vec3> face = List.of(smaller.geometry().corners().get(indices[0]),
				smaller.geometry().corners().get(indices[1]), smaller.geometry().corners().get(indices[2]),
				smaller.geometry().corners().get(indices[3]));
			List<Vec3> contact = clipAgainstPlanes(face, other.planes(), SurgicalClientTopology.CONTACT_TOLERANCE);
			double area = polygonArea(contact);
			if (area < SurgicalClientTopology.MIN_CONTACT_AREA)
				continue;
			double centerDistance = faceCenter(face).distanceToSqr(other.center());
			if (centerDistance < bestCenterDistance - SurgicalClientTopology.DISTANCE_EPSILON
				|| Math.abs(centerDistance - bestCenterDistance) <= SurgicalClientTopology.DISTANCE_EPSILON
					&& area > bestArea + SurgicalClientTopology.MIN_CONTACT_AREA) {
				bestCenterDistance = centerDistance;
				bestArea = area;
				bestContact = contact;
			}
		}
		return bestContact.isEmpty() ? List.of() : List.of(bestContact);
	}

	/** Builds every boundary face of the convex polyhedron shared by all half-spaces. */
	static List<List<Vec3>> convexIntersectionFaces(List<SurgicalClientTopology.Plane> planes) {
		List<Vec3> vertices = convexIntersectionVertices(planes);
		if (vertices.size() < 3)
			return List.of();

		List<List<Vec3>> faces = new ArrayList<>();
		Set<List<Integer>> uniqueFaces = new HashSet<>();
		for (SurgicalClientTopology.Plane plane : planes) {
			List<Integer> onPlane = new ArrayList<>();
			for (int vertex = 0; vertex < vertices.size(); vertex++)
				if (Math.abs(plane.signedDistance(vertices.get(vertex))) <= SurgicalClientTopology.POLYHEDRON_EPSILON)
					onPlane.add(vertex);
			if (onPlane.size() < 3)
				continue;

			List<Integer> ordered = orderFace(onPlane, vertices, plane.normal());
			ordered = removeCollinearVertices(ordered, vertices);
			if (ordered.size() < 3)
				continue;
			List<Vec3> polygon = new ArrayList<>(ordered.size());
			for (int vertex : ordered)
				polygon.add(vertices.get(vertex));
			if (polygonArea(polygon) < SurgicalClientTopology.MIN_CONTACT_AREA)
				continue;

			List<Integer> faceKey = new ArrayList<>(ordered);
			faceKey.sort(Integer::compareTo);
			if (uniqueFaces.add(List.copyOf(faceKey)))
				faces.add(List.copyOf(polygon));
		}
		return List.copyOf(faces);
	}

	static List<Vec3> convexIntersectionVertices(List<SurgicalClientTopology.Plane> planes) {
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
		return vertices;
	}

	@Nullable
	static Vec3 intersect(SurgicalClientTopology.Plane first, SurgicalClientTopology.Plane second, SurgicalClientTopology.Plane third) {
		Vec3 secondCrossThird = second.normal().cross(third.normal());
		double determinant = first.normal().dot(secondCrossThird);
		if (Math.abs(determinant) <= SurgicalClientTopology.INTERSECTION_EPSILON)
			return null;
		return secondCrossThird.scale(first.maximum())
			.add(third.normal().cross(first.normal()).scale(second.maximum()))
			.add(first.normal().cross(second.normal()).scale(third.maximum()))
			.scale(1.0d / determinant);
	}

	static boolean insideAll(Vec3 point, List<SurgicalClientTopology.Plane> planes) {
		for (SurgicalClientTopology.Plane plane : planes)
			if (plane.signedDistance(point) > SurgicalClientTopology.POLYHEDRON_EPSILON)
				return false;
		return true;
	}

	static boolean containsPoint(List<Vec3> points, Vec3 candidate) {
		for (Vec3 point : points)
			if (point.distanceToSqr(candidate) <= SurgicalClientTopology.VERTEX_MERGE_DISTANCE_SQR)
				return true;
		return false;
	}

	static List<Integer> orderFace(List<Integer> face, List<Vec3> vertices, Vec3 normal) {
		Vec3 center = Vec3.ZERO;
		for (int vertex : face)
			center = center.add(vertices.get(vertex));
		center = center.scale(1.0d / face.size());
		final Vec3 faceCenter = center;

		Vec3 axisU = vertices.get(face.getFirst()).subtract(faceCenter);
		if (axisU.lengthSqr() <= SurgicalClientTopology.DEGENERATE_EPSILON)
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

	static List<Integer> removeCollinearVertices(List<Integer> face, List<Vec3> vertices) {
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
					.lengthSqr() > SurgicalClientTopology.VERTEX_MERGE_DISTANCE_SQR)
					continue;
				simplified.remove(current);
				changed = true;
				break;
			}
		} while (changed);
		return List.copyOf(simplified);
	}

	static List<Vec3> clipAgainstPlanes(List<Vec3> polygon,
		List<SurgicalClientTopology.Plane> planes, double tolerance) {
		List<Vec3> clipped = List.copyOf(polygon);
		for (SurgicalClientTopology.Plane plane : planes) {
			if (clipped.isEmpty())
				break;
			List<Vec3> next = new ArrayList<>();
			Vec3 previous = clipped.getLast();
			double previousDistance = plane.signedDistance(previous) - tolerance;
			boolean previousInside = previousDistance <= SurgicalClientTopology.INTERSECTION_EPSILON;
			for (Vec3 current : clipped) {
				double currentDistance = plane.signedDistance(current) - tolerance;
				boolean currentInside = currentDistance <= SurgicalClientTopology.INTERSECTION_EPSILON;
				if (previousInside != currentInside) {
					double denominator = previousDistance - currentDistance;
					if (Math.abs(denominator) > SurgicalClientTopology.INTERSECTION_EPSILON) {
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

	static List<SurgicalClientTopology.Plane> clipPlanes(SurgicalModelRenderContext.CubeGeometry cube) {
		List<Vec3> corners = cube.corners();
		Vec3 cubeCenter = SurgicalClientTopology.center(cube);
		List<SurgicalClientTopology.Plane> planes = new ArrayList<>(6);
		for (int[] indices : SurgicalClientTopology.CUBE_FACES) {
			Vec3 first = corners.get(indices[0]);
			Vec3 faceCenter = first.add(corners.get(indices[1]))
				.add(corners.get(indices[2])).add(corners.get(indices[3])).scale(0.25d);
			Vec3 normal = corners.get(indices[1]).subtract(first)
				.cross(corners.get(indices[3]).subtract(first));
			if (normal.lengthSqr() <= SurgicalClientTopology.DEGENERATE_EPSILON)
				continue;
			normal = normal.normalize();
			if (normal.dot(faceCenter.subtract(cubeCenter)) < 0.0d)
				normal = normal.scale(-1.0d);
			planes.add(new SurgicalClientTopology.Plane(normal, normal.dot(first)));
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
			planes.add(new SurgicalClientTopology.Plane(axis, maximum));
			planes.add(new SurgicalClientTopology.Plane(axis.scale(-1.0d), -minimum));
		}
		return planes;
	}

	static List<Vec3> orthonormalAxes(Vec3... candidates) {
		List<Vec3> axes = new ArrayList<>(3);
		for (Vec3 candidate : candidates) {
			Vec3 axis = candidate;
			for (Vec3 existing : axes)
				axis = axis.subtract(existing.scale(axis.dot(existing)));
			if (axis.lengthSqr() > SurgicalClientTopology.DEGENERATE_EPSILON)
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

	static List<Vec3> simplifyPolygon(List<Vec3> polygon) {
		if (polygon.size() < 2)
			return polygon;
		List<Vec3> simplified = new ArrayList<>(polygon.size());
		for (Vec3 point : polygon)
			if (simplified.isEmpty() || simplified.getLast().distanceToSqr(point) > SurgicalClientTopology.DEGENERATE_EPSILON)
				simplified.add(point);
		if (simplified.size() > 1
			&& simplified.getFirst().distanceToSqr(simplified.getLast()) <= SurgicalClientTopology.DEGENERATE_EPSILON)
			simplified.removeLast();
		return simplified;
	}

	static double polygonArea(List<Vec3> polygon) {
		if (polygon.size() < 3)
			return 0.0d;
		Vec3 origin = polygon.getFirst();
		double area = 0.0d;
		for (int i = 1; i + 1 < polygon.size(); i++)
			area += polygon.get(i).subtract(origin).cross(polygon.get(i + 1).subtract(origin)).length() * 0.5d;
		return area;
	}

	static double volume(SurgicalModelRenderContext.CubeGeometry cube) {
		List<Vec3> corners = cube.corners();
		return corners.get(0).distanceTo(corners.get(1))
			* corners.get(0).distanceTo(corners.get(2))
			* corners.get(0).distanceTo(corners.get(4));
	}

	static Vec3 faceCenter(List<Vec3> face) {
		return face.get(0).add(face.get(1)).add(face.get(2)).add(face.get(3)).scale(0.25d);
	}

	static Map<Integer, SurgicalModelRenderContext.CubeGeometry> byId(
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		Map<Integer, SurgicalModelRenderContext.CubeGeometry> result = new HashMap<>();
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			result.putIfAbsent(cube.cubeId(), cube);
		return result;
	}

	static Map<Integer, SurgicalClientTopology.PreparedCube> preparedById(
		List<SurgicalModelRenderContext.CubeGeometry> cubes) {
		Map<Integer, SurgicalClientTopology.PreparedCube> result = new HashMap<>();
		for (SurgicalModelRenderContext.CubeGeometry cube : cubes)
			result.computeIfAbsent(cube.cubeId(), ignored -> SurgicalClientTopology.PreparedCube.of(cube));
		return result;
	}
}
