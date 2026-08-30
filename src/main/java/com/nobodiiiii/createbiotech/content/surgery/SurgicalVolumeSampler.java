package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

/** Deterministic overlap-aware union-volume sampling shared by surgical gameplay metrics. */
public final class SurgicalVolumeSampler {
	private static final double GEOMETRY_EPSILON = 1.0e-10d;
	private static final double OVERLAP_EPSILON = 1.0e-7d;
	private static final int SAMPLES_PER_BOX = 64;
	private static final int MAX_SAMPLE_POINTS = 32_768;
	private static final long MAX_COVERAGE_TESTS = 8_000_000L;

	private SurgicalVolumeSampler() {}

	/**
	 * Measures the geometric union of capture-ordered oriented cuboids. Isolated cuboids are exact;
	 * only broad-phase overlap components use deterministic low-discrepancy sampling.
	 */
	public static double unionVolume(List<List<Vec3>> cuboids) {
		if (cuboids == null || cuboids.isEmpty())
			return 0.0d;
		List<VolumeBox> boxes = new ArrayList<>(cuboids.size());
		for (List<Vec3> cuboid : cuboids) {
			VolumeBox box = VolumeBox.of(cuboid);
			if (box != null)
				boxes.add(box);
		}
		return unionBoxes(boxes);
	}

	/**
	 * Adds isolated cuboids exactly and samples only broad-phase overlap components. For a sampled
	 * point covered by {@code n} cuboids, its source cuboid contributes {@code 1 / n}; summing every
	 * source therefore estimates the geometric union instead of counting overlap repeatedly.
	 */
	private static double unionBoxes(List<VolumeBox> boxes) {
		if (boxes.isEmpty())
			return 0.0d;
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
				volume += boxes.get(component.getFirst()).volume();
				continue;
			}
			volume += sampledUnionVolume(boxes, component, sampledBoxes);
		}
		return volume;
	}

	private static double sampledUnionVolume(List<VolumeBox> boxes, List<Integer> component,
		int sampledBoxes) {
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
				volume += sampleWeight / Math.max(1, coverage);
			}
		}
		return volume;
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
	private record VolumeBox(Vec3 origin, Vec3 a, Vec3 b, Vec3 c,
		Vec3 reciprocalA, Vec3 reciprocalB, Vec3 reciprocalC,
		double volume, double[] minimum, double[] maximum) {
		@Nullable
		private static VolumeBox of(List<Vec3> points) {
			if (points == null || points.size() != 8 || points.stream().anyMatch(java.util.Objects::isNull))
				return null;
			Vec3 origin = points.getFirst();
			Vec3 a = points.get(1).subtract(origin);
			Vec3 b = points.get(2).subtract(origin);
			Vec3 c = points.get(4).subtract(origin);
			Vec3 bCrossC = b.cross(c);
			double determinant = a.dot(bCrossC);
			if (!Double.isFinite(determinant) || Math.abs(determinant) < GEOMETRY_EPSILON)
				return null;
			double inverseDeterminant = 1.0d / determinant;
			double[] minimum = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
				Double.POSITIVE_INFINITY };
			double[] maximum = { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
				Double.NEGATIVE_INFINITY };
			for (Vec3 point : points) {
				minimum[0] = Math.min(minimum[0], point.x);
				minimum[1] = Math.min(minimum[1], point.y);
				minimum[2] = Math.min(minimum[2], point.z);
				maximum[0] = Math.max(maximum[0], point.x);
				maximum[1] = Math.max(maximum[1], point.y);
				maximum[2] = Math.max(maximum[2], point.z);
			}
			return new VolumeBox(origin, a, b, c, bCrossC.scale(inverseDeterminant),
				c.cross(a).scale(inverseDeterminant), a.cross(b).scale(inverseDeterminant),
				Math.abs(determinant), minimum, maximum);
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
