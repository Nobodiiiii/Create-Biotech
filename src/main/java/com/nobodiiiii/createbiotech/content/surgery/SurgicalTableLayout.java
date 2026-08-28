package com.nobodiiiii.createbiotech.content.surgery;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Side-safe wire data and validation for surgical-table component placement. */
public final class SurgicalTableLayout {
	public static final int SUBDIVISIONS = 4;
	public static final int SLOTS_PER_TILE = SUBDIVISIONS * SUBDIVISIONS;
	public static final int UNSNAPPED = Integer.MIN_VALUE;
	/** Minimum horizontal air gap between independently placeable parts. */
	public static final double COMPONENT_CLEARANCE = 1.0d / 16.0d;
	private static final double EPSILON = 1.0e-6d;
	private static final double MAX_OFFSET = SurgicalTablePlane.MAX_TILES + 2.0d;

	private SurgicalTableLayout() {}

	public static double gridCenter(int gridCoordinate) {
		return (gridCoordinate + 0.5d) / SUBDIVISIONS;
	}

	public static int gridCoordinate(double position) {
		return (int) Math.floor(position * SUBDIVISIONS);
	}

	public static boolean validatePlacement(SurgicalTablePlane.Plane plane, double originOffsetX,
		double originOffsetZ, Proposal proposal, List<Footprint> occupiedFootprints) {
		return validatePlacementBounds(plane, originOffsetX, originOffsetZ, proposal)
			&& doesNotOverlap(proposal.footprints(), occupiedFootprints);
	}

	private static boolean validatePlacementBounds(SurgicalTablePlane.Plane plane, double originOffsetX,
		double originOffsetZ, Proposal proposal) {
		if (!plane.valid() || plane.workArea().isEmpty() || !finiteBounded(originOffsetX)
			|| !finiteBounded(originOffsetZ) || indexOffsets(proposal.offsets()) == null
			|| proposal.footprints().size() != 1)
			return false;
		Footprint footprint = proposal.footprints().getFirst();
		return footprint.componentRoot() == -1
			&& validFootprints(plane.workArea(), List.of(footprint), true);
	}

	/**
	 * Canonical validation for a placement preview and its eventual server-side commit.
	 * The client renders and the server stores the same facing, component layout and composite
	 * transform. Effective render Y is subsequently grounded through the shared connected-body
	 * path because packed data deliberately preserves relative pre-boxing offsets.
	 */
	public static boolean validateSubjectPlacement(SurgicalTablePlane.Plane plane,
		@Nullable SurgicalAssembly assembly, Direction placementFacing, SurgicalLayPose layPose,
		double originOffsetX, double originOffsetZ, Proposal envelope,
		int discoveredCubeCount, List<SurgicalAssembly.Seam> discoveredSeams,
		List<Footprint> discoveredFootprints,
		List<Proposal> sourceLayouts, List<Footprint> occupiedFootprints) {
		if (placementFacing == null || !placementFacing.getAxis().isHorizontal()
			|| layPose == null || !layPose.valid() || sourceLayouts == null
			|| discoveredSeams == null || discoveredFootprints == null
			|| !validatePlacementBounds(plane, originOffsetX, originOffsetZ, envelope))
			return false;
		if (assembly == null)
			return sourceLayouts.isEmpty() && envelope.offsets().isEmpty()
				&& validateDiscoveredPlacement(plane, discoveredCubeCount, discoveredSeams,
					discoveredFootprints, envelope.footprints().getFirst(), occupiedFootprints);
		if (discoveredCubeCount != 0 || !discoveredSeams.isEmpty() || !discoveredFootprints.isEmpty()
			|| !doesNotOverlap(envelope.footprints(), occupiedFootprints))
			return false;

		boolean composite = assembly.preservesLayout() || assembly.sources().size() != 1;
		if (!composite)
			return sourceLayouts.isEmpty() && assembly.cubeRotations().isEmpty()
				&& validateInitialOffsets(assembly, envelope.offsets());
		if (!envelope.offsets().isEmpty() || sourceLayouts.size() != assembly.sources().size()
			|| !layPose.equals(assembly.placedLayPose(placementFacing)))
			return false;

		List<SurgicalAssembly.PlacedSource> placedSources = assembly.placedSources(placementFacing);
		if (placedSources.size() != sourceLayouts.size())
			return false;
		Footprint assemblyEnvelope = envelope.footprints().getFirst();
		for (int sourceId = 0; sourceId < placedSources.size(); sourceId++) {
			SurgicalAssembly.PlacedSource placed = placedSources.get(sourceId);
			SurgicalAssembly.Source source = placed.source();
			Proposal sourceLayout = sourceLayouts.get(sourceId);
			if (!finiteBounded(originOffsetX + placed.originOffset().x)
				|| !finiteBounded(originOffsetZ + placed.originOffset().z)
				|| !finiteBounded(placed.originOffset().y)
				|| !matchesSourceOffsets(placed, sourceLayout)
				|| !validateCompositeComponents(plane, source.cubeCount(), source.presentCubes(),
					source.seams(), source.cutSeams(), sourceLayout, occupiedFootprints, assemblyEnvelope))
				return false;
		}
		return true;
	}

	private static boolean validateDiscoveredPlacement(SurgicalTablePlane.Plane plane, int cubeCount,
		List<SurgicalAssembly.Seam> seams, List<Footprint> footprints, Footprint envelope,
		List<Footprint> occupiedFootprints) {
		if (!SurgicalAssembly.validTopology(cubeCount, seams) || occupiedFootprints == null)
			return false;
		BitSet present = new BitSet(cubeCount);
		present.set(0, cubeCount);
		List<BitSet> components = SurgicalAssembly.components(cubeCount, present, seams, new BitSet());
		if (components.isEmpty() || footprints.size() != components.size())
			return false;
		Set<Integer> expectedRoots = new HashSet<>();
		for (BitSet component : components)
			expectedRoots.add(component.nextSetBit(0));
		Set<Integer> actualRoots = new HashSet<>();
		for (Footprint footprint : footprints) {
			if (footprint == null || footprint.gridX() != UNSNAPPED || footprint.gridZ() != UNSNAPPED
				|| !expectedRoots.contains(footprint.componentRoot())
				|| !actualRoots.add(footprint.componentRoot())
				|| !validFootprintBounds(plane.workArea(), footprint)
				|| !contains(envelope, footprint))
				return false;
		}
		return actualRoots.equals(expectedRoots) && doesNotOverlap(footprints, occupiedFootprints);
	}

	private static boolean validateInitialOffsets(SurgicalAssembly assembly, List<CubeOffset> proposed) {
		BitSet present = assembly.presentCubes();
		Map<Integer, CubeOffset> offsets = indexOffsets(proposed);
		if (offsets == null || offsets.size() != present.cardinality())
			return false;
		for (BitSet component : SurgicalAssembly.components(assembly.cubeCount(), present,
			assembly.seams(), assembly.cutSeams())) {
			CubeOffset root = offsets.get(component.nextSetBit(0));
			if (root == null)
				return false;
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
				CubeOffset offset = offsets.get(cube);
				if (offset == null || Math.abs(offset.x()) > EPSILON || Math.abs(offset.z()) > EPSILON
					|| Math.abs(offset.y() - root.y()) > EPSILON)
					return false;
			}
		}
		return true;
	}

	private static boolean matchesSourceOffsets(SurgicalAssembly.PlacedSource placed, Proposal layout) {
		SurgicalAssembly.Source source = placed.source();
		Map<Integer, CubeOffset> proposed = indexOffsets(layout.offsets());
		if (proposed == null || proposed.size() != source.presentCubes().cardinality())
			return false;
		BitSet present = source.presentCubes();
		for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
			Vec3 expected = placed.cubeOffsets().getOrDefault(cube, Vec3.ZERO);
			CubeOffset actual = proposed.get(cube);
			if (actual == null || Math.abs(actual.x() - expected.x) > EPSILON
				|| Math.abs(actual.y() - expected.y) > EPSILON
				|| Math.abs(actual.z() - expected.z) > EPSILON)
				return false;
		}
		return true;
	}

	public static boolean validateComponents(SurgicalTablePlane.Plane plane, int cubeCount,
		BitSet presentCubes, List<SurgicalAssembly.Seam> seams, BitSet cutSeams, Proposal proposal,
		List<Footprint> occupiedFootprints) {
		return validateComponents(plane, cubeCount, presentCubes, seams, cutSeams, proposal,
			occupiedFootprints, null, false, false);
	}

	/** Validates cut/move layouts that already contain exact per-cube smart-glue transforms. */
	public static boolean validateTransformedComponents(SurgicalTablePlane.Plane plane, int cubeCount,
		BitSet presentCubes, List<SurgicalAssembly.Seam> seams, BitSet cutSeams, Proposal proposal,
		List<Footprint> occupiedFootprints) {
		return validateComponents(plane, cubeCount, presentCubes, seams, cutSeams, proposal,
			occupiedFootprints, null, false, true);
	}

	/**
	 * Validates one source restored from a packed glued assembly. Components and sources in the
	 * same assembly may overlap because the glue points themselves can be inside both models.
	 * Smart-glue rotation can also leave different cubes in one native component with different
	 * translations; the placement path separately verifies those translations against the packed
	 * server-owned assembly before calling this method.
	 */
	public static boolean validateCompositeComponents(SurgicalTablePlane.Plane plane, int cubeCount,
		BitSet presentCubes, List<SurgicalAssembly.Seam> seams, BitSet cutSeams, Proposal proposal,
		List<Footprint> occupiedFootprints, Footprint assemblyEnvelope) {
		return validateComponents(plane, cubeCount, presentCubes, seams, cutSeams, proposal,
			occupiedFootprints, assemblyEnvelope, true, true);
	}

	/** Validates exact glue-preview placement while allowing the already-glued native components to overlap. */
	public static boolean validateGlueComponents(SurgicalTablePlane.Plane plane, int cubeCount,
		BitSet presentCubes, List<SurgicalAssembly.Seam> seams, BitSet cutSeams, Proposal proposal,
		List<Footprint> occupiedFootprints) {
		return validateComponents(plane, cubeCount, presentCubes, seams, cutSeams, proposal,
			occupiedFootprints, null, true, false);
	}

	/** Validates a smart-glue rigid-body preview whose rotation can give each cube a distinct offset. */
	public static boolean validateEditedGlueComponents(SurgicalTablePlane.Plane plane, int cubeCount,
		BitSet presentCubes, List<SurgicalAssembly.Seam> seams, BitSet cutSeams, Proposal proposal,
		List<Footprint> occupiedFootprints) {
		return validateComponents(plane, cubeCount, presentCubes, seams, cutSeams, proposal,
			occupiedFootprints, null, true, true);
	}

	private static boolean validateComponents(SurgicalTablePlane.Plane plane, int cubeCount,
		BitSet presentCubes, List<SurgicalAssembly.Seam> seams, BitSet cutSeams, Proposal proposal,
		List<Footprint> occupiedFootprints, Footprint assemblyEnvelope, boolean allowComponentOverlap,
		boolean allowPerCubeOffsets) {
		if (!plane.valid() || plane.workArea().isEmpty()
			|| !SurgicalAssembly.validTopology(cubeCount, seams)
			|| proposal.offsets().size() != presentCubes.cardinality())
			return false;

		Map<Integer, CubeOffset> offsets = indexOffsets(proposal.offsets());
		if (offsets == null)
			return false;
		for (CubeOffset offset : offsets.values()) {
			if (offset.cubeId() >= cubeCount || !presentCubes.get(offset.cubeId()))
				return false;
		}

		List<BitSet> components = SurgicalAssembly.components(cubeCount, presentCubes, seams, cutSeams);
		if (proposal.footprints().size() != presentCubes.cardinality())
			return false;
		Map<Integer, List<Footprint>> footprints = new HashMap<>();
		for (Footprint footprint : proposal.footprints()) {
			if (footprint == null || !validFootprintBounds(plane.workArea(), footprint)
				|| assemblyEnvelope != null && !contains(assemblyEnvelope, footprint))
				return false;
			footprints.computeIfAbsent(footprint.componentRoot(), ignored -> new java.util.ArrayList<>())
				.add(footprint);
		}

		Set<Integer> expectedRoots = new HashSet<>();
		for (BitSet component : components) {
			int root = component.nextSetBit(0);
			expectedRoots.add(root);
			CubeOffset componentOffset = offsets.get(root);
			List<Footprint> componentFootprints = footprints.get(root);
			if (componentOffset == null || componentFootprints == null
				|| componentFootprints.size() != component.cardinality()
				|| !validFootprints(plane.workArea(), componentFootprints, false))
				return false;
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
				CubeOffset offset = offsets.get(cube);
				if (offset == null || !allowPerCubeOffsets
					&& (Math.abs(offset.x() - componentOffset.x()) > EPSILON
						|| Math.abs(offset.y() - componentOffset.y()) > EPSILON
						|| Math.abs(offset.z() - componentOffset.z()) > EPSILON))
					return false;
			}
		}
		if (!footprints.keySet().equals(expectedRoots))
			return false;

		if (!allowComponentOverlap) {
			List<Integer> roots = List.copyOf(expectedRoots);
			for (int firstRoot = 0; firstRoot < roots.size(); firstRoot++)
				for (int secondRoot = firstRoot + 1; secondRoot < roots.size(); secondRoot++)
					for (Footprint first : footprints.get(roots.get(firstRoot)))
						for (Footprint second : footprints.get(roots.get(secondRoot)))
							if (first.conflictsWith(second))
								return false;
		}
		return doesNotOverlap(proposal.footprints(), occupiedFootprints);
	}

	private static boolean contains(Footprint envelope, Footprint footprint) {
		return footprint.minX() >= envelope.minX() - EPSILON
			&& footprint.minZ() >= envelope.minZ() - EPSILON
			&& footprint.maxX() <= envelope.maxX() + EPSILON
			&& footprint.maxZ() <= envelope.maxZ() + EPSILON;
	}

	public static boolean validStoredFootprint(Footprint footprint) {
		if (footprint == null || !Double.isFinite(footprint.minX()) || !Double.isFinite(footprint.minZ())
			|| !Double.isFinite(footprint.maxX()) || !Double.isFinite(footprint.maxZ())
			|| footprint.maxX() < footprint.minX() || footprint.maxZ() < footprint.minZ())
			return false;
		boolean snappedX = footprint.gridX() != UNSNAPPED;
		boolean snappedZ = footprint.gridZ() != UNSNAPPED;
		return snappedX == snappedZ;
	}

	private static boolean validFootprintBounds(SurgicalTablePlane.WorkArea area, Footprint footprint) {
		if (!validStoredFootprint(footprint)
			|| !area.contains(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ(), EPSILON))
			return false;
		return true;
	}

	private static boolean validFootprints(SurgicalTablePlane.WorkArea area, List<Footprint> footprints,
		boolean requireSnapped) {
		if (footprints.isEmpty())
			return false;
		Footprint first = footprints.getFirst();
		if (!validFootprintBounds(area, first))
			return false;
		boolean snapped = first.gridX() != UNSNAPPED;
		if (requireSnapped && !snapped)
			return false;
		double minX = first.minX();
		double minZ = first.minZ();
		double maxX = first.maxX();
		double maxZ = first.maxZ();
		for (int index = 1; index < footprints.size(); index++) {
			Footprint footprint = footprints.get(index);
			if (!validFootprintBounds(area, footprint)
				|| footprint.componentRoot() != first.componentRoot()
				|| (footprint.gridX() != UNSNAPPED) != snapped
				|| snapped && (footprint.gridX() != first.gridX() || footprint.gridZ() != first.gridZ()))
				return false;
			minX = Math.min(minX, footprint.minX());
			minZ = Math.min(minZ, footprint.minZ());
			maxX = Math.max(maxX, footprint.maxX());
			maxZ = Math.max(maxZ, footprint.maxZ());
		}
		if (!snapped)
			return true;
		double gridCenterX = gridCenter(first.gridX());
		double gridCenterZ = gridCenter(first.gridZ());
		return Math.abs((minX + maxX) * 0.5d - gridCenterX) <= EPSILON
			&& Math.abs((minZ + maxZ) * 0.5d - gridCenterZ) <= EPSILON
			&& gridCenterX >= area.minX() && gridCenterX < area.maxXExclusive()
			&& gridCenterZ >= area.minZ() && gridCenterZ < area.maxZExclusive();
	}

	private static boolean doesNotOverlap(List<Footprint> proposed, List<Footprint> occupied) {
		for (Footprint footprint : proposed)
			for (Footprint obstacle : occupied)
				if (footprint.conflictsWith(obstacle))
					return false;
		return true;
	}

	private static boolean finiteBounded(double value) {
		return Double.isFinite(value) && Math.abs(value) <= MAX_OFFSET;
	}

	@Nullable
	private static Map<Integer, CubeOffset> indexOffsets(List<CubeOffset> proposed) {
		if (proposed == null || proposed.size() > SurgicalAssembly.MAX_CUBES)
			return null;
		Map<Integer, CubeOffset> offsets = new HashMap<>();
		for (CubeOffset offset : proposed)
			if (offset == null || offset.cubeId() < 0 || !finiteBounded(offset.x())
				|| !finiteBounded(offset.y()) || !finiteBounded(offset.z())
				|| offsets.putIfAbsent(offset.cubeId(), offset) != null)
				return null;
		return offsets;
	}

	public record CubeOffset(int cubeId, double x, double y, double z) {}

	public record Footprint(int componentRoot, double minX, double minZ, double maxX, double maxZ,
		int gridX, int gridZ) {
		public boolean overlapsStrictly(Footprint other) {
			return minX < other.maxX - EPSILON && maxX > other.minX + EPSILON
				&& minZ < other.maxZ - EPSILON && maxZ > other.minZ + EPSILON;
		}

		/** Treats each other part as occupying its bounds plus the global 1/16-block margin. */
		public boolean conflictsWith(Footprint other) {
			return minX < other.maxX + COMPONENT_CLEARANCE - EPSILON
				&& maxX > other.minX - COMPONENT_CLEARANCE + EPSILON
				&& minZ < other.maxZ + COMPONENT_CLEARANCE - EPSILON
				&& maxZ > other.minZ - COMPONENT_CLEARANCE + EPSILON;
		}
	}

	public record Proposal(List<CubeOffset> offsets, List<Footprint> footprints) {
		public static final Proposal EMPTY = new Proposal(List.of(), List.of());

		public Proposal {
			offsets = List.copyOf(offsets);
			footprints = List.copyOf(footprints);
		}
	}
}
