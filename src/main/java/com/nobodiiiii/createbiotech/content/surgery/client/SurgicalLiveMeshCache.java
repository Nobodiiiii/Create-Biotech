package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.client.renderer.RenderType;

/** Render-thread recipes for complete cuboids. Every replay validates all current vertices. */
final class SurgicalLiveMeshCache {
	private final List<Stream> streams = new ArrayList<>();

	void begin(SurgicalCapturedRenderPlan.CapturedInput input) {
		if (streams.size() != input.streams().size()) {
			streams.clear();
			for (SurgicalRenderCapture.CaptureStream stream : input.streams())
				streams.add(new Stream(stream));
			return;
		}
		for (int i = 0; i < streams.size(); i++)
			if (!streams.get(i).matches(input.streams().get(i)))
				streams.set(i, new Stream(input.streams().get(i)));
	}

	@Nullable
	SurgicalCapturedRenderPlan.RecoveredCandidate recover(SurgicalRenderCapture.CaptureStream stream, int cursor) {
		Stream cached = streams.get(stream.id);
		Cuboid recipe = cached.cuboids.get(cursor);
		if (recipe != null) {
			var recovered = recipe.refit(stream.vertices, cursor);
			if (recovered != null) return recovered;
		}
		var recovered = SurgicalMeshRecovery.recoverCandidate(stream.vertices, cursor);
		Cuboid replacement = recovered == null ? null : Cuboid.of(recovered);
		if (replacement == null) cached.cuboids.remove(cursor);
		else cached.cuboids.put(cursor, replacement);
		return recovered;
	}

	private static final class Stream {
		private final RenderType type;
		private final int count;
		private final boolean preserveSource;
		private final Map<Integer, Cuboid> cuboids = new HashMap<>();

		Stream(SurgicalRenderCapture.CaptureStream stream) {
			type = stream.renderType;
			count = stream.vertices.size();
			preserveSource = stream.preserveSource;
		}

		boolean matches(SurgicalRenderCapture.CaptureStream stream) {
			return type == stream.renderType && count == stream.vertices.size()
				&& preserveSource == stream.preserveSource;
		}
	}

	private record Cuboid(int[] cornerVertices, byte[] vertexCorners) {
		@Nullable
		static Cuboid of(SurgicalCapturedRenderPlan.RecoveredCandidate candidate) {
			// Partial faces and arbitrary meshes keep the general recovery path. A full box
			// always wins the general recovery's longest-prefix search as well.
			if (candidate.vertices().size() != 24 || SurgicalMeshRecovery.isFlat(candidate.cuboid()))
				return null;
			int[] indices = new int[8];
			java.util.Arrays.fill(indices, -1);
			byte[] corners = new byte[24];
			for (int i = 0; i < corners.length; i++) {
				var vertex = candidate.vertices().get(i);
				int corner = -1;
				for (int j = 0; j < 8; j++)
					if (matches(vertex, candidate.cuboid().corners().get(j))) {
						corner = j;
						break;
					}
				if (corner < 0) return null;
				corners[i] = (byte) corner;
				if (indices[corner] < 0) indices[corner] = i;
			}
			for (int index : indices) if (index < 0) return null;
			return new Cuboid(indices, corners);
		}

		@Nullable
		SurgicalCapturedRenderPlan.RecoveredCandidate refit(List<SurgicalRenderCapture.CapturedVertex> vertices, int start) {
			if (start + 24 > vertices.size()) return null;
			Vector3f origin = SurgicalMeshRecovery.position(vertices.get(start + cornerVertices[0]));
			Vector3f a = SurgicalMeshRecovery.position(vertices.get(start + cornerVertices[1])).sub(origin);
			Vector3f b = SurgicalMeshRecovery.position(vertices.get(start + cornerVertices[2])).sub(origin);
			Vector3f c = SurgicalMeshRecovery.position(vertices.get(start + cornerVertices[4])).sub(origin);
			Vector3f cross = new Vector3f(a).cross(b);
			if (cross.lengthSquared() <= 1.0e-8f || Math.abs(cross.dot(c)) <= 1.0e-8f)
				return null;
			List<Vector3f> corners = SurgicalMeshRecovery.parallelepiped(origin, a, b, c);
			for (int i = 0; i < 24; i++)
				if (!matches(vertices.get(start + i), corners.get(vertexCorners[i]))) return null;
			return new SurgicalCapturedRenderPlan.RecoveredCandidate(
				new SurgicalCapturedRenderPlan.RecoveredCuboid(corners, a, b, c),
				List.copyOf(vertices.subList(start, start + 24)));
		}

		private static boolean matches(SurgicalRenderCapture.CapturedVertex vertex, Vector3f corner) {
			return Math.abs(vertex.x() - corner.x) <= SurgicalCapturedRenderPlan.POSITION_EPSILON
				&& Math.abs(vertex.y() - corner.y) <= SurgicalCapturedRenderPlan.POSITION_EPSILON
				&& Math.abs(vertex.z() - corner.z) <= SurgicalCapturedRenderPlan.POSITION_EPSILON;
		}
	}
}
