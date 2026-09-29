package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared client/server planning over captured model cuboids; never mutates the table. */
public final class SurgicalWrenchRotation {
	public static final double TABLE_CLEARANCE = 1.0d / 1024.0d;
	private static final double CONTACT_TOLERANCE = 1.0d / 64.0d;

	private SurgicalWrenchRotation() {}

	public record Cube(int id, List<Vec3> corners, Vec3 offset, SurgicalCubeRotation rotation) {
		public Cube {
			corners = List.copyOf(corners);
		}

		public Vec3 center() {
			Vec3 sum = Vec3.ZERO;
			for (Vec3 corner : corners)
				sum = sum.add(corner);
			return sum.scale(1.0d / corners.size());
		}

		public boolean valid() {
			return id >= 0 && corners.size() == 8 && finite(offset) && rotation != null
				&& corners.stream().allMatch(SurgicalWrenchRotation::finite);
		}

		Cube transform(Vec3 pivot, SurgicalCubeRotation delta, double lift) {
			Vec3 center = center();
			Vec3 nextCenter = pivot.add(delta.rotate(center.subtract(pivot))).add(0, lift, 0);
			List<Vec3> transformed = corners.stream()
				.map(corner -> pivot.add(delta.rotate(corner.subtract(pivot))).add(0, lift, 0)).toList();
			return new Cube(id, transformed, offset.add(nextCenter.subtract(center)), rotation.then(delta));
		}

		public SurgicalTableLayout.Footprint footprint(int root) {
			AABB bounds = bounds();
			return new SurgicalTableLayout.Footprint(root, bounds.minX, bounds.minZ, bounds.maxX, bounds.maxZ,
				SurgicalTableLayout.UNSNAPPED, SurgicalTableLayout.UNSNAPPED);
		}

		Cube lift(double lift) {
			return Math.abs(lift) < 1.0e-12d ? this : new Cube(id,
				corners.stream().map(corner -> corner.add(0, lift, 0)).toList(), offset.add(0, lift, 0), rotation);
		}

		AABB bounds() {
			double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
			double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
			for (Vec3 corner : corners) {
				minX = Math.min(minX, corner.x); minY = Math.min(minY, corner.y); minZ = Math.min(minZ, corner.z);
				maxX = Math.max(maxX, corner.x); maxY = Math.max(maxY, corner.y); maxZ = Math.max(maxZ, corner.z);
			}
			return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
		}
	}

	public record Body(int id, UUID key, int cubeCount, List<SurgicalAssembly.Seam> seams, List<Cube> cubes) {
		public Body {
			seams = List.copyOf(seams);
			cubes = List.copyOf(cubes);
		}

		public Cube cube(int id) {
			for (Cube cube : cubes)
				if (cube.id == id)
					return cube;
			return null;
		}

		public SurgicalTableLayout.Proposal layout(BitSet present, BitSet cuts) {
			Map<Integer, Integer> roots = new HashMap<>();
			for (BitSet component : SurgicalAssembly.components(cubeCount, present, seams, cuts))
				for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1))
					roots.put(cube, component.nextSetBit(0));
			List<SurgicalTableLayout.CubeOffset> offsets = new ArrayList<>();
			List<SurgicalTableLayout.Footprint> footprints = new ArrayList<>();
			for (Cube cube : cubes) {
				offsets.add(new SurgicalTableLayout.CubeOffset(cube.id, cube.offset.x, cube.offset.y, cube.offset.z));
				footprints.add(cube.footprint(roots.getOrDefault(cube.id, -1)));
			}
			return new SurgicalTableLayout.Proposal(offsets, footprints);
		}
	}

	public static SurgicalCubeRotation delta(Cube anchor, Vec3 axis, double degrees, boolean straighten) {
		if (!straighten)
			return SurgicalCubeRotation.around(axis, degrees);
		List<Vec3> edges = edges(anchor);
		Vec3 x = edges.get(0), y = edges.get(1), z = edges.get(2);
		if (x.lengthSqr() <= 1.0e-18d)
			x = y.cross(z);
		if (y.lengthSqr() <= 1.0e-18d)
			y = z.cross(x);
		return SurgicalCubeRotation.nearestAxisAlignment(x, y);
	}

	public enum Failure { NONE, INVALID, NO_SPACE, DISCONNECTED, JOINT_DISCONNECTED }
	public record Endpoint(int body, int cube) {}
	public record Plan(List<Body> bodies, Failure failure, Endpoint selected, List<Endpoint> disconnected) {
		public Plan(List<Body> bodies, Failure failure) { this(bodies, failure, null, List.of()); }
		public Plan { bodies = List.copyOf(bodies); disconnected = List.copyOf(disconnected); }
		static Plan fail(Failure failure) { return new Plan(List.of(), failure); }
		public boolean valid() { return failure == Failure.NONE; }
	}

	/** Pure preview: O(cubes), with no table scan, footprint checks or connectivity traversal. */
	public static List<Body> preview(List<Body> before, Map<Integer, BitSet> group,
		int selectedBody, int selectedCube, SurgicalCubeRotation groupRotation,
		SurgicalCubeRotation selectedRotation, double surfaceY) {
		Cube anchor = find(before, selectedBody, selectedCube);
		Vec3 pivot = anchor.center();
		List<Body> rotated = new ArrayList<>(before.size());
		double lowest = Double.POSITIVE_INFINITY;
		for (Body body : before) {
			BitSet moving = group.get(body.id);
			List<Cube> cubes = new ArrayList<>(body.cubes.size());
			for (Cube cube : body.cubes) {
				Cube next = cube;
				if (moving.get(cube.id)) {
					SurgicalCubeRotation rotation = body.id == selectedBody && cube.id == selectedCube
						? selectedRotation : groupRotation;
					next = rotation.isIdentity() ? cube : cube.transform(pivot, rotation, 0);
					for (Vec3 corner : next.corners)
						lowest = Math.min(lowest, corner.y);
				}
				cubes.add(next);
			}
			rotated.add(new Body(body.id, body.key, body.cubeCount, body.seams, cubes));
		}
		double lift = surfaceY + TABLE_CLEARANCE - lowest;
		List<Body> grounded = new ArrayList<>(rotated.size());
		for (Body body : rotated) {
			BitSet moving = group.get(body.id);
			grounded.add(new Body(body.id, body.key, body.cubeCount, body.seams,
				body.cubes.stream().map(cube -> moving.get(cube.id) ? cube.lift(lift) : cube).toList()));
		}
		return List.copyOf(grounded);
	}

	/** Checks only the final pose at commit; intermediate preview poses may overlap or leave the table. */
	public static Plan plan(List<Body> before, Map<Integer, BitSet> group, Map<Integer, BitSet> neighbors,
		int selectedBody, int selectedCube, SurgicalCubeRotation groupRotation, SurgicalCubeRotation selectedRotation,
		SurgicalTablePlane.WorkArea area, List<SurgicalTableLayout.Footprint> obstacles) {
		if (find(before, selectedBody, selectedCube) == null || group.isEmpty() || area.isEmpty() || obstacles == null)
			return Plan.fail(Failure.INVALID);
		List<Body> grounded = preview(before, group, selectedBody, selectedCube, groupRotation, selectedRotation,
			area.surfaceY());
		List<SurgicalTableLayout.Footprint> stationary = new ArrayList<>(obstacles);
		List<SurgicalTableLayout.Footprint> movingFootprints = new ArrayList<>();
		for (Body body : grounded) {
			for (Cube cube : body.cubes) {
				if (!cube.valid())
					return Plan.fail(Failure.INVALID);
				SurgicalTableLayout.Footprint footprint = cube.footprint(cube.id);
				if (group.get(body.id).get(cube.id)) {
					if (!area.contains(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ(), 1.0e-6d))
						return Plan.fail(Failure.NO_SPACE);
					movingFootprints.add(footprint);
				} else
					stationary.add(footprint);
			}
		}
		for (SurgicalTableLayout.Footprint moved : movingFootprints)
			for (SurgicalTableLayout.Footprint obstacle : stationary)
				if (moved.conflictsWith(obstacle))
					return Plan.fail(Failure.NO_SPACE);
		List<Endpoint> disconnected = new ArrayList<>();
		if (!groupRotation.approximatelyEquals(selectedRotation, 1.0e-10d)) {
			Cube rotated = find(grounded, selectedBody, selectedCube);
			int neighborCount = 0;
			for (Map.Entry<Integer, BitSet> entry : neighbors.entrySet())
				for (int cube = entry.getValue().nextSetBit(0); cube >= 0; cube = entry.getValue().nextSetBit(cube + 1)) {
					if (entry.getKey() == selectedBody && cube == selectedCube)
						continue;
					Cube neighbor = find(grounded, entry.getKey(), cube);
					if (neighbor == null)
						return Plan.fail(Failure.INVALID);
					neighborCount++;
					if (!touches(rotated, neighbor))
						disconnected.add(new Endpoint(entry.getKey(), cube));
				}
			// An isolated cube may rotate freely; an attached one must retain an original neighbor.
			if (neighborCount > 0 && disconnected.size() == neighborCount)
				return Plan.fail(Failure.DISCONNECTED);
		}
		return new Plan(grounded, Failure.NONE, new Endpoint(selectedBody, selectedCube), disconnected);
	}

	private static Cube find(List<Body> bodies, int bodyId, int cubeId) {
		return bodies.stream().filter(body -> body.id == bodyId).findFirst().map(body -> body.cube(cubeId)).orElse(null);
	}

	/** Separating-axis check for oriented cuboids, including planar model cubes. */
	private static boolean touches(Cube first, Cube second) {
		List<Vec3> a = edges(first);
		List<Vec3> b = edges(second);
		List<Vec3> axes = new ArrayList<>(List.of(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)));
		axes.addAll(a);
		axes.addAll(b);
		for (int i = 0; i < 3; i++) {
			axes.add(a.get(i).cross(a.get((i + 1) % 3)));
			axes.add(b.get(i).cross(b.get((i + 1) % 3)));
			for (int j = 0; j < 3; j++)
				axes.add(a.get(i).cross(b.get(j)));
		}
		for (Vec3 raw : axes) {
			if (raw.lengthSqr() <= 1.0e-18d)
				continue;
			Vec3 axis = raw.normalize();
			double aMin = Double.POSITIVE_INFINITY, aMax = Double.NEGATIVE_INFINITY;
			double bMin = Double.POSITIVE_INFINITY, bMax = Double.NEGATIVE_INFINITY;
			for (Vec3 corner : first.corners) {
				aMin = Math.min(aMin, corner.dot(axis));
				aMax = Math.max(aMax, corner.dot(axis));
			}
			for (Vec3 corner : second.corners) {
				bMin = Math.min(bMin, corner.dot(axis));
				bMax = Math.max(bMax, corner.dot(axis));
			}
			if (aMax < bMin - CONTACT_TOLERANCE || bMax < aMin - CONTACT_TOLERANCE)
				return false;
		}
		return true;
	}

	private static List<Vec3> edges(Cube cube) {
		Vec3 origin = cube.corners.getFirst();
		return List.of(cube.corners.get(1).subtract(origin), cube.corners.get(2).subtract(origin),
			cube.corners.get(4).subtract(origin));
	}

	private static boolean finite(Vec3 vector) {
		return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
	}
}
