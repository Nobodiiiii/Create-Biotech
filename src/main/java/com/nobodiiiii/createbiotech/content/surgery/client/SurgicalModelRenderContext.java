package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.List;

import net.minecraft.world.phys.Vec3;

/**
 * Geometry records shared by the surgical renderers.
 *
 * <p>This class used to host a ThreadLocal capture context that filtered and collected cubes during
 * entity rendering. That role moved to {@link SurgicalCapturedRenderPlan}, which captures from the
 * raw vertex stream instead, and the context became unreachable; only the geometry records it
 * defined are still in use.
 */
public final class SurgicalModelRenderContext {

	private SurgicalModelRenderContext() {}

	public record Snapshot(int observedCubeCount, List<CubeGeometry> cubes) {
		public Snapshot {
			cubes = List.copyOf(cubes);
		}
	}

	public record CubeGeometry(int cubeId, List<Vec3> corners, List<Vec3> modelCorners,
		List<FaceGrid> faceGrids, boolean head) {
		public CubeGeometry(int cubeId, List<Vec3> corners) {
			this(cubeId, corners, List.of(), List.of(), false);
		}

		public CubeGeometry(int cubeId, List<Vec3> corners, List<FaceGrid> faceGrids) {
			this(cubeId, corners, List.of(), faceGrids, false);
		}

		public CubeGeometry(int cubeId, List<Vec3> corners, List<Vec3> modelCorners,
			List<FaceGrid> faceGrids) {
			this(cubeId, corners, modelCorners, faceGrids, false);
		}

		public CubeGeometry {
			corners = List.copyOf(corners);
			modelCorners = List.copyOf(modelCorners);
			faceGrids = List.copyOf(faceGrids);
			if (corners.size() != 8)
				throw new IllegalArgumentException("A cube geometry requires exactly 8 corners");
			if (!modelCorners.isEmpty() && modelCorners.size() != 8)
				throw new IllegalArgumentException("Model pixel geometry must describe all 8 corners");
			if (!faceGrids.isEmpty() && faceGrids.size() != 6)
				throw new IllegalArgumentException("Cube face grids must describe all 6 faces");
		}

		public CubeGeometry withCorners(List<Vec3> transformedCorners) {
			return new CubeGeometry(cubeId, transformedCorners, modelCorners, faceGrids, head);
		}
	}

	/** Source-texture pixel counts along the two ordered edges of one recovered cube face. */
	public record FaceGrid(double pixelsU, double pixelsV) {
		public boolean valid() {
			return Double.isFinite(pixelsU) && Double.isFinite(pixelsV)
				&& pixelsU > 1.0e-6d && pixelsV > 1.0e-6d;
		}
	}

}
