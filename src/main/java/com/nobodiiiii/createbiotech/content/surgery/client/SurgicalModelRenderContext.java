package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
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

	/**
	 * Anchors independently rendered geometry to the first direct source cube of a model part, so an
	 * attachment disappears along with the cube it is mounted on.
	 *
	 * <p>This hook is currently inert: it answered from the capture context that this class no longer
	 * owns, so it has returned {@code true} unconditionally since the capture moved to
	 * {@link SurgicalCapturedRenderPlan}. It is kept as an explicit stub rather than deleted because
	 * its caller expresses a real intent that nothing else implements. Re-wiring it means resolving
	 * the anchor's cube against the active render plan.
	 */
	public static boolean prepareAttachment(ModelPart anchor, PoseStack poseStack) {
		return true;
	}

	public record Snapshot(int observedCubeCount, List<CubeGeometry> cubes) {
		public Snapshot {
			cubes = List.copyOf(cubes);
		}
	}

	public record CubeGeometry(int cubeId, List<Vec3> corners, List<Vec3> modelCorners,
		List<FaceGrid> faceGrids) {
		public CubeGeometry(int cubeId, List<Vec3> corners) {
			this(cubeId, corners, List.of(), List.of());
		}

		public CubeGeometry(int cubeId, List<Vec3> corners, List<FaceGrid> faceGrids) {
			this(cubeId, corners, List.of(), faceGrids);
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
			return new CubeGeometry(cubeId, transformedCorners, modelCorners, faceGrids);
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
