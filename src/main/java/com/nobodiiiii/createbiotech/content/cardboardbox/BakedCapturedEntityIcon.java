package com.nobodiiiii.createbiotech.content.cardboardbox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Immutable pre-clipped icon geometry for one captured entity on one box face.
 * <p>
 * The captured entity never ticks and renders with a pinned partial tick, so
 * the clipped vertex stream the live pipeline used to emit every frame is
 * frame-invariant in box-local space. It is captured once at bake time and
 * replayed here under the caller's current pose, which removes the per-frame
 * entity renderer traversal, clipping, and allocation churn entirely.
 * <p>
 * Vertices whose packed light equals {@link #LIGHT_SENTINEL} were lit by the
 * bake-time light argument and receive the frame's packed light on replay; any
 * other value (emissive layers using constant light) is kept as baked.
 */
final class BakedCapturedEntityIcon {
	/**
	 * Passed as packed light while baking. Real packed light only carries
	 * multiples of 16 in its block/sky bytes, so this value cannot occur
	 * naturally and safely marks "replace with the frame's light".
	 */
	static final int LIGHT_SENTINEL = 0x00FE00FE;

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int FLOATS_PER_VERTEX = 8;
	private static final int INTS_PER_VERTEX = 3;
	// Replay only runs on the render thread, matching the rest of the icon
	// pipeline's static scratch usage.
	private static final Vector3f POSITION_SCRATCH = new Vector3f();
	private static final Vector3f NORMAL_SCRATCH = new Vector3f();

	private final RenderType[] renderTypes;
	private final int[] groupVertexEnds;
	private final float[] geometry;
	private final int[] attributes;

	private BakedCapturedEntityIcon(RenderType[] renderTypes, int[] groupVertexEnds, float[] geometry,
		int[] attributes) {
		this.renderTypes = renderTypes;
		this.groupVertexEnds = groupVertexEnds;
		this.geometry = geometry;
		this.attributes = attributes;
	}

	void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		if (renderTypes.length == 0)
			return;

		PoseStack.Pose pose = poseStack.last();
		Matrix4f matrix = pose.pose();
		int vertex = 0;
		for (int group = 0; group < renderTypes.length; group++) {
			VertexConsumer consumer = buffer.getBuffer(renderTypes[group]);
			for (int end = groupVertexEnds[group]; vertex < end; vertex++) {
				int floatBase = vertex * FLOATS_PER_VERTEX;
				int intBase = vertex * INTS_PER_VERTEX;
				matrix.transformPosition(geometry[floatBase], geometry[floatBase + 1], geometry[floatBase + 2],
					POSITION_SCRATCH);
				pose.normal()
					.transform(geometry[floatBase + 5], geometry[floatBase + 6], geometry[floatBase + 7],
						NORMAL_SCRATCH);
				int color = attributes[intBase];
				int light = attributes[intBase + 1];
				consumer.vertex(POSITION_SCRATCH.x(), POSITION_SCRATCH.y(), POSITION_SCRATCH.z())
					.color(color >>> 24, color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF)
					.uv(geometry[floatBase + 3], geometry[floatBase + 4])
					.overlayCoords(attributes[intBase + 2])
					.uv2(light == LIGHT_SENTINEL ? packedLight : light)
					.normal(NORMAL_SCRATCH.x(), NORMAL_SCRATCH.y(), NORMAL_SCRATCH.z())
					.endVertex();
			}
		}
	}

	static Builder builder() {
		return new Builder();
	}

	static final class Builder {
		private static final int MAX_VERTICES = 65_536;
		private static final int INITIAL_VERTEX_CAPACITY = 256;

		private final Map<RenderType, VertexStore> stores = new LinkedHashMap<>();
		private int totalVertices;
		private boolean truncated;

		private Builder() {
		}

		VertexConsumer target(RenderType renderType) {
			return stores.computeIfAbsent(renderType, type -> new VertexStore());
		}

		BakedCapturedEntityIcon build(String iconId) {
			List<Map.Entry<RenderType, VertexStore>> groups = new ArrayList<>();
			int vertexTotal = 0;
			for (Map.Entry<RenderType, VertexStore> entry : stores.entrySet()) {
				int vertices = entry.getValue()
					.completeQuadVertices();
				if (vertices == 0)
					continue;
				groups.add(entry);
				vertexTotal += vertices;
			}

			RenderType[] renderTypes = new RenderType[groups.size()];
			int[] groupVertexEnds = new int[groups.size()];
			float[] geometry = new float[vertexTotal * FLOATS_PER_VERTEX];
			int[] attributes = new int[vertexTotal * INTS_PER_VERTEX];
			int vertexOffset = 0;
			for (int group = 0; group < groups.size(); group++) {
				Map.Entry<RenderType, VertexStore> entry = groups.get(group);
				VertexStore store = entry.getValue();
				int vertices = store.completeQuadVertices();
				System.arraycopy(store.geometry, 0, geometry, vertexOffset * FLOATS_PER_VERTEX,
					vertices * FLOATS_PER_VERTEX);
				System.arraycopy(store.attributes, 0, attributes, vertexOffset * INTS_PER_VERTEX,
					vertices * INTS_PER_VERTEX);
				vertexOffset += vertices;
				renderTypes[group] = entry.getKey();
				groupVertexEnds[group] = vertexOffset;
			}

			if (truncated)
				LOGGER.warn("Captured entity icon for {} exceeded {} vertices and was truncated", iconId, MAX_VERTICES);
			return new BakedCapturedEntityIcon(renderTypes, groupVertexEnds, geometry, attributes);
		}

		/**
		 * Records the canonical per-vertex call sequence produced by the face
		 * clipping consumer; a vertex is committed by its trailing endVertex.
		 * Truncation may strand a partial quad, so readers use
		 * {@link #completeQuadVertices()} to keep primitive alignment.
		 */
		private final class VertexStore implements VertexConsumer {
			private float[] geometry = new float[INITIAL_VERTEX_CAPACITY * FLOATS_PER_VERTEX];
			private int[] attributes = new int[INITIAL_VERTEX_CAPACITY * INTS_PER_VERTEX];
			private int vertices;
			private float x;
			private float y;
			private float z;
			private float u;
			private float v;
			private int color;
			private int light;
			private int overlay;
			private float normalX;
			private float normalY;
			private float normalZ;
			private int defaultColor = 0xFFFFFFFF;

			private int completeQuadVertices() {
				return vertices - vertices % 4;
			}

			@Override
			public VertexConsumer vertex(double x, double y, double z) {
				this.x = (float) x;
				this.y = (float) y;
				this.z = (float) z;
				u = 0.0f;
				v = 0.0f;
				color = defaultColor;
				light = 0;
				overlay = 0;
				normalX = 0.0f;
				normalY = 1.0f;
				normalZ = 0.0f;
				return this;
			}

			@Override
			public VertexConsumer color(int red, int green, int blue, int alpha) {
				color = (red & 0xFF) << 24 | (green & 0xFF) << 16 | (blue & 0xFF) << 8 | alpha & 0xFF;
				return this;
			}

			@Override
			public VertexConsumer uv(float u, float v) {
				this.u = u;
				this.v = v;
				return this;
			}

			@Override
			public VertexConsumer overlayCoords(int u, int v) {
				overlay = u | v << 16;
				return this;
			}

			@Override
			public VertexConsumer uv2(int u, int v) {
				light = u | v << 16;
				return this;
			}

			@Override
			public VertexConsumer normal(float normalX, float normalY, float normalZ) {
				this.normalX = normalX;
				this.normalY = normalY;
				this.normalZ = normalZ;
				return this;
			}

			@Override
			public void endVertex() {
				if (totalVertices >= MAX_VERTICES) {
					truncated = true;
					return;
				}

				ensureCapacity(vertices + 1);
				int floatBase = vertices * FLOATS_PER_VERTEX;
				geometry[floatBase] = x;
				geometry[floatBase + 1] = y;
				geometry[floatBase + 2] = z;
				geometry[floatBase + 3] = u;
				geometry[floatBase + 4] = v;
				geometry[floatBase + 5] = normalX;
				geometry[floatBase + 6] = normalY;
				geometry[floatBase + 7] = normalZ;
				int intBase = vertices * INTS_PER_VERTEX;
				attributes[intBase] = color;
				attributes[intBase + 1] = light;
				attributes[intBase + 2] = overlay;
				vertices++;
				totalVertices++;
			}

			/**
			 * Baked as an explicit per-vertex color, so a model that only sets a default
			 * color still replays with the color it was drawn in.
			 */
			@Override
			public void defaultColor(int red, int green, int blue, int alpha) {
				defaultColor = (red & 0xFF) << 24 | (green & 0xFF) << 16 | (blue & 0xFF) << 8 | alpha & 0xFF;
			}

			@Override
			public void unsetDefaultColor() {
				defaultColor = 0xFFFFFFFF;
			}

			private void ensureCapacity(int requiredVertices) {
				if (requiredVertices * FLOATS_PER_VERTEX <= geometry.length)
					return;

				int capacity = Math.min(MAX_VERTICES, Math.max(requiredVertices, vertices * 2));
				float[] expandedGeometry = new float[capacity * FLOATS_PER_VERTEX];
				System.arraycopy(geometry, 0, expandedGeometry, 0, vertices * FLOATS_PER_VERTEX);
				geometry = expandedGeometry;
				int[] expandedAttributes = new int[capacity * INTS_PER_VERTEX];
				System.arraycopy(attributes, 0, expandedAttributes, 0, vertices * INTS_PER_VERTEX);
				attributes = expandedAttributes;
			}
		}
	}
}
