package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

/** Deterministic overlap-aware union-volume sampling shared by surgical gameplay metrics. */
public final class SurgicalVolumeSampler {
	private static final double GEOMETRY_EPSILON = 1.0e-10d;
	private static final double OVERLAP_EPSILON = 1.0e-7d;
	private static final int SAMPLES_PER_BOX = 64;
	private static final int MAX_SAMPLE_POINTS = 32_768;
	private static final long MAX_COVERAGE_TESTS = 8_000_000L;
	/** One vanilla model pixel, so planar wings, fins and gills still weigh something. */
	private static final double MIN_CUBE_EDGE = 1.0d / 16.0d;

	private SurgicalVolumeSampler() {}

	/**
	 * Volume of one capture-ordered cuboid for weighting trait coverage. Edges shorter than one model
	 * pixel count as one pixel, so a zero-thickness plane is treated as a one-pixel sheet.
	 */
	public static double cubeVolume(List<Vec3> corners) {
		if (corners == null || corners.size() != 8 || corners.stream().anyMatch(java.util.Objects::isNull))
			return Double.NaN;
		Vec3 origin = corners.getFirst();
		return Math.max(MIN_CUBE_EDGE, corners.get(1).distanceTo(origin))
			* Math.max(MIN_CUBE_EDGE, corners.get(2).distanceTo(origin))
			* Math.max(MIN_CUBE_EDGE, corners.get(4).distanceTo(origin));
	}

	/**
	 * Measures the geometric union of capture-ordered oriented cuboids. Isolated cuboids are exact;
	 * only broad-phase overlap components use deterministic low-discrepancy sampling.
	 */
	public static double unionVolume(List<List<Vec3>> cuboids) {
		return prepare(cuboids).union(false).volume();
	}

	/** Geometry shared by body-volume and collision-core measurements in one rest pose. */
	static PreparedGeometry prepare(List<List<Vec3>> cuboids) {
		return new PreparedGeometry(cuboids);
	}

	static final class PreparedGeometry {
		private final Map<List<Vec3>, Cuboid> byCorners = new HashMap<>();
		private final List<Cuboid> cubes = new ArrayList<>();
		private final List<VolumeBox> solids = new ArrayList<>();
		@Nullable
		private UnionMeasure measured;

		private PreparedGeometry(List<List<Vec3>> cuboids) {
			if (cuboids == null)
				return;
			for (List<Vec3> corners : cuboids) {
				Cuboid cube = byCorners.get(corners);
				if (cube == null)
					cube = Cuboid.of(corners);
				if (cube == null)
					continue;
				byCorners.put(corners, cube);
				cubes.add(cube);
				if (cube.solid() != null)
					solids.add(cube.solid());
			}
		}

		List<Cuboid> cubes() { return cubes; }

		List<Cuboid> select(List<List<Vec3>> corners) {
			List<Cuboid> selected = new ArrayList<>();
			if (corners != null)
				for (List<Vec3> points : corners) {
					Cuboid cube = byCorners.get(points);
					if (cube != null)
						selected.add(cube);
				}
			return selected;
		}

		UnionMeasure union(boolean projections) {
			if (measured == null || (projections && measured.masses() == null))
				measured = unionBoxes(solids, projections);
			return measured;
		}

		UnionMeasure collisionUnion(List<VolumeBox> boxes) {
			// Contained cubes cannot change the union's spatial distribution. Removing them also
			// keeps duplicate/inner model layers from changing sampling density or its error.
			List<VolumeBox> exposed = new ArrayList<>();
			for (VolumeBox box : boxes) {
				boolean contained = false;
				for (VolumeBox candidate : exposed)
					if (candidate.contains(box)) {
						contained = true;
						break;
					}
				if (contained)
					continue;
				exposed.removeIf(box::contains);
				exposed.add(box);
			}
			return exposed.equals(solids) ? union(true) : unionBoxes(exposed, true);
		}
	}

	/**
	 * Adds isolated cuboids exactly and samples only broad-phase overlap components. For a sampled
	 * point covered by {@code n} cuboids, its source cuboid contributes {@code 1 / n}; summing every
	 * source therefore estimates the geometric union instead of counting overlap repeatedly.
	 */
	private static UnionMeasure unionBoxes(List<VolumeBox> boxes, boolean projections) {
		List<Mass> masses = projections ? new ArrayList<>() : null;
		if (boxes.isEmpty())
			return new UnionMeasure(0.0d, masses);
		int[] parents = new int[boxes.size()];
		for (int index = 0; index < parents.length; index++)
			parents[index] = index;
		for (int first = 0; first < boxes.size(); first++)
			for (int second = first + 1; second < boxes.size(); second++)
				if (boxes.get(first).overlapsEnvelope(boxes.get(second)))
					union(parents, first, second);

		List<List<Integer>> components = new ArrayList<>(boxes.size());
		for (int index = 0; index < boxes.size(); index++)
			components.add(new ArrayList<>());
		for (int index = 0; index < boxes.size(); index++)
			components.get(findRoot(parents, index)).add(index);
		int sampledBoxes = 0;
		for (List<Integer> component : components)
			if (component.size() > 1)
				sampledBoxes += component.size();

		double volume = 0.0d;
		for (List<Integer> component : components) {
			if (component.isEmpty())
				continue;
			if (component.size() == 1) {
				VolumeBox box = boxes.get(component.getFirst());
				volume += box.volume();
				if (masses != null)
					masses.add(new Mass(box.minimum[0], box.maximum[0], box.minimum[2],
						box.maximum[2], box.volume()));
				continue;
			}
			volume += sampledUnionVolume(boxes, component, sampledBoxes, masses);
		}
		return new UnionMeasure(volume, masses);
	}

	private static double sampledUnionVolume(List<VolumeBox> boxes, List<Integer> component,
		int sampledBoxes, @Nullable List<Mass> masses) {
		int pointBudget = Math.max(1, MAX_SAMPLE_POINTS / Math.max(1, sampledBoxes));
		long componentPairs = (long) component.size() * component.size();
		int coverageBudget = (int) Math.max(1L,
			MAX_COVERAGE_TESTS / Math.max(1L, componentPairs));
		int samplesPerBox = Math.min(SAMPLES_PER_BOX,
			Math.min(pointBudget, coverageBudget));
		double volume = 0.0d;
		for (int sourceIndex : component) {
			VolumeBox source = boxes.get(sourceIndex);
			double sampleWeight = source.volume() / samplesPerBox;
			for (int sample = 0; sample < samplesPerBox; sample++) {
				Vec3 point = source.sample(sample);
				int coverage = 0;
				for (int candidateIndex : component)
					if (boxes.get(candidateIndex).contains(point))
						coverage++;
				double weight = sampleWeight / Math.max(1, coverage);
				volume += weight;
				if (masses != null)
					masses.add(new Mass(point.x, point.x, point.z, point.z, weight));
			}
		}
		return volume;
	}

	/** Uniform envelope projections for isolated cubes; overlap-corrected point masses otherwise. */
	private record Mass(double minX, double maxX, double minZ, double maxZ, double weight) {}
	private record ProjectionEvent(double coordinate, double densityDelta, double mass) {}

	record UnionMeasure(double volume, @Nullable List<Mass> masses) {
		double[] quantiles(int axis, double lower, double upper) {
			if (masses == null || !(volume > GEOMETRY_EPSILON))
				return new double[] { Double.NaN, Double.NaN };
			List<ProjectionEvent> events = new ArrayList<>(masses.size() * 2);
			for (Mass mass : masses) {
				double min = axis == 0 ? mass.minX : mass.minZ;
				double max = axis == 0 ? mass.maxX : mass.maxZ;
				if (max - min <= GEOMETRY_EPSILON) {
					events.add(new ProjectionEvent(min, 0.0d, mass.weight));
				} else {
					double density = mass.weight / (max - min);
					events.add(new ProjectionEvent(min, density, 0.0d));
					events.add(new ProjectionEvent(max, -density, 0.0d));
				}
			}
			events.sort(Comparator.comparingDouble(ProjectionEvent::coordinate));
			return new double[] { quantile(events, volume * lower), quantile(events, volume * upper) };
		}

		private static double quantile(List<ProjectionEvent> events, double target) {
			double previous = events.getFirst().coordinate, density = 0.0d, accumulated = 0.0d;
			for (ProjectionEvent event : events) {
				double segment = Math.max(0.0d, density) * (event.coordinate - previous);
				if (segment > 0.0d && accumulated + segment >= target)
					return previous + (target - accumulated) / density;
				accumulated += segment + event.mass;
				if (accumulated >= target)
					return event.coordinate;
				density += event.densityDelta;
				previous = event.coordinate;
			}
			return events.getLast().coordinate;
		}
	}

	/** Original envelope and the two volume policies, prepared from the same edges and face normals. */
	record Cuboid(double[] minimum, double[] maximum,
		@Nullable VolumeBox solid, @Nullable VolumeBox collision) {
		@Nullable
		private static Cuboid of(List<Vec3> points) {
			if (points == null || points.size() != 8)
				return null;
			double[] min = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
			double[] max = { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY };
			for (Vec3 point : points) {
				if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y)
					|| !Double.isFinite(point.z))
					return null;
				min[0] = Math.min(min[0], point.x);
				max[0] = Math.max(max[0], point.x);
				min[1] = Math.min(min[1], point.y);
				max[1] = Math.max(max[1], point.y);
				min[2] = Math.min(min[2], point.z);
				max[2] = Math.max(max[2], point.z);
			}
			Vec3 origin = points.getFirst();
			Vec3[] edges = { points.get(1).subtract(origin), points.get(2).subtract(origin),
				points.get(4).subtract(origin) };
			Vec3[] normals = { edges[1].cross(edges[2]), edges[2].cross(edges[0]), edges[0].cross(edges[1]) };
			double determinant = edges[0].dot(normals[0]);
			VolumeBox solid = VolumeBox.of(origin, edges, normals, determinant, min, max);
			int thinAxis = 0;
			for (int axis = 1; axis < 3; axis++)
				if (normals[axis].lengthSqr() > normals[thinAxis].lengthSqr())
					thinAxis = axis;
			double area = normals[thinAxis].length();
			VolumeBox collision = solid;
			if (area > GEOMETRY_EPSILON && Math.abs(determinant) < area * SurgicalAssembly.MIN_BODY_SIZE) {
				// Give a sheet real thickness along its face normal, centred on its original surface.
				// Only collision weighting uses this padding; health retains the unpadded geometry.
				Vec3 normal = normals[thinAxis].scale(1.0d / area);
				double height = edges[thinAxis].dot(normal);
				Vec3 padding = normal.scale(Math.copySign(SurgicalAssembly.MIN_BODY_SIZE, height) - height);
				origin = origin.subtract(padding.scale(0.5d));
				edges[thinAxis] = edges[thinAxis].add(padding);
				Vec3[] paddedNormals = { edges[1].cross(edges[2]), edges[2].cross(edges[0]), edges[0].cross(edges[1]) };
				double[] paddedMin = { origin.x, origin.y, origin.z };
				double[] paddedMax = paddedMin.clone();
				for (Vec3 edge : edges) {
					paddedMin[0] += Math.min(0.0d, edge.x);
					paddedMax[0] += Math.max(0.0d, edge.x);
					paddedMin[1] += Math.min(0.0d, edge.y);
					paddedMax[1] += Math.max(0.0d, edge.y);
					paddedMin[2] += Math.min(0.0d, edge.z);
					paddedMax[2] += Math.max(0.0d, edge.z);
				}
				collision = VolumeBox.of(origin, edges, paddedNormals, edges[0].dot(paddedNormals[0]),
					paddedMin, paddedMax);
			}
			return new Cuboid(min, max, solid, collision);
		}
	}

	/** Low-discrepancy coordinate in {@code (0, 1)} for deterministic interior sampling. */
	private static double halton(int index, int base) {
		double fraction = 1.0d;
		double result = 0.0d;
		while (index > 0) {
			fraction /= base;
			result += fraction * (index % base);
			index /= base;
		}
		return result;
	}

	private static void union(int[] parents, int first, int second) {
		int firstRoot = findRoot(parents, first);
		int secondRoot = findRoot(parents, second);
		if (firstRoot != secondRoot)
			parents[secondRoot] = firstRoot;
	}

	private static int findRoot(int[] parents, int index) {
		int root = index;
		while (parents[root] != root)
			root = parents[root];
		while (parents[index] != index) {
			int next = parents[index];
			parents[index] = root;
			index = next;
		}
		return root;
	}

	/** Capture orders corners by x, then y, then z, so 1, 2 and 4 are the box edges. */
	record VolumeBox(Vec3 origin, Vec3 a, Vec3 b, Vec3 c,
		Vec3 reciprocalA, Vec3 reciprocalB, Vec3 reciprocalC,
		double volume, double[] minimum, double[] maximum) {
		@Nullable
		private static VolumeBox of(Vec3 origin, Vec3[] edges, Vec3[] normals,
			double determinant, double[] minimum, double[] maximum) {
			if (!Double.isFinite(determinant) || Math.abs(determinant) < GEOMETRY_EPSILON)
				return null;
			double inverseDeterminant = 1.0d / determinant;
			return new VolumeBox(origin, edges[0], edges[1], edges[2], normals[0].scale(inverseDeterminant),
				normals[1].scale(inverseDeterminant), normals[2].scale(inverseDeterminant),
				Math.abs(determinant), minimum, maximum);
		}

		private boolean contains(VolumeBox other) {
			for (int axis = 0; axis < 3; axis++)
				if (other.minimum[axis] < minimum[axis] - OVERLAP_EPSILON
					|| other.maximum[axis] > maximum[axis] + OVERLAP_EPSILON)
					return false;
			for (int corner = 0; corner < 8; corner++)
				if (!contains(other.origin.add(other.a.scale(corner & 1))
					.add(other.b.scale((corner >> 1) & 1)).add(other.c.scale((corner >> 2) & 1))))
					return false;
			return true;
		}

		private boolean overlapsEnvelope(VolumeBox other) {
			for (int axis = 0; axis < 3; axis++)
				if (Math.min(maximum[axis], other.maximum[axis])
					- Math.max(minimum[axis], other.minimum[axis]) <= OVERLAP_EPSILON)
					return false;
			return true;
		}

		private Vec3 sample(int sample) {
			double u = halton(sample + 1, 2);
			double v = halton(sample + 1, 3);
			double w = halton(sample + 1, 5);
			return new Vec3(origin.x + a.x * u + b.x * v + c.x * w,
				origin.y + a.y * u + b.y * v + c.y * w,
				origin.z + a.z * u + b.z * v + c.z * w);
		}

		private boolean contains(Vec3 point) {
			if (point.x < minimum[0] - OVERLAP_EPSILON
				|| point.x > maximum[0] + OVERLAP_EPSILON
				|| point.y < minimum[1] - OVERLAP_EPSILON
				|| point.y > maximum[1] + OVERLAP_EPSILON
				|| point.z < minimum[2] - OVERLAP_EPSILON
				|| point.z > maximum[2] + OVERLAP_EPSILON)
				return false;
			double dx = point.x - origin.x;
			double dy = point.y - origin.y;
			double dz = point.z - origin.z;
			double u = dx * reciprocalA.x + dy * reciprocalA.y + dz * reciprocalA.z;
			double v = dx * reciprocalB.x + dy * reciprocalB.y + dz * reciprocalB.z;
			double w = dx * reciprocalC.x + dy * reciprocalC.y + dz * reciprocalC.z;
			return u >= -OVERLAP_EPSILON && u <= 1.0d + OVERLAP_EPSILON
				&& v >= -OVERLAP_EPSILON && v <= 1.0d + OVERLAP_EPSILON
				&& w >= -OVERLAP_EPSILON && w <= 1.0d + OVERLAP_EPSILON;
		}
	}
}
