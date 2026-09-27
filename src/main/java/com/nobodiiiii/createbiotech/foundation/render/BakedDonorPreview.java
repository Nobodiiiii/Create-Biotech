package com.nobodiiiii.createbiotech.foundation.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityRenderTime;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;

/** Bounded, static model capture. The result retains no entity, world, or per-vertex objects. */
public final class BakedDonorPreview {
	private static final int MAX_VERTICES = 16_384;
	private static final int MAX_LAYERS = 32;
	private static final int FLOATS = 8;
	private static final int INTS = 3;
	private final List<Layer> layers;
	private final float centerX;
	private final float centerY;
	private final float centerZ;
	private final float size;
	private final int bytes;

	private BakedDonorPreview(Capture capture) {
		List<Layer> layers = new ArrayList<>();
		int bytes = 128;
		for (var entry : capture.layers.entrySet()) {
			Store store = entry.getValue();
			int count = store.vertices;
			if (!entry.getKey().mode().connectedPrimitives)
				count -= count % entry.getKey().mode().primitiveLength;
			if (count == 0)
				continue;
			layers.add(new Layer(entry.getKey(), Arrays.copyOf(store.geometry, count * FLOATS),
				Arrays.copyOf(store.attributes, count * INTS)));
			bytes += count * (FLOATS + INTS) * 4 + 256;
		}
		if (layers.isEmpty())
			throw new IllegalStateException("Donor renderer produced no geometry");
		this.layers = List.copyOf(layers);
		this.bytes = bytes;
		centerX = (capture.minX + capture.maxX) * .5f;
		centerY = (capture.minY + capture.maxY) * .5f;
		centerZ = (capture.minZ + capture.maxZ) * .5f;
		size = Math.max(.01f, Math.max(capture.maxX - capture.minX, capture.maxY - capture.minY));
	}

	public static BakedDonorPreview capture(LivingEntity entity) {
		Capture capture = new Capture();
		PoseStack pose = new PoseStack();
		pose.mulPose(Axis.XP.rotationDegrees(15));
		pose.mulPose(Axis.YP.rotationDegrees(145));
		try (CapturedEntityRenderTime.Scope ignored = CapturedEntityRenderTime.open()) {
			EntityRenderHelper.renderUnoriented(entity, pose, capture, LightTexture.FULL_BRIGHT);
		}
		return new BakedDonorPreview(capture);
	}

	public int bytes() { return bytes; }

	public void draw(GuiGraphics graphics, int x, int y, int width, int height) {
		graphics.flush();
		PoseStack pose = graphics.pose();
		pose.pushPose();
		Lighting.setupFor3DItems();
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableDepthTest();
		try {
			pose.translate(x + width * .5f, y + height * .5f, 150);
			float scale = (Math.min(width, height) - 3) / size;
			pose.scale(scale, -scale, scale);
			pose.translate(-centerX, -centerY, -centerZ);
			Vector3f position = new Vector3f();
			Vector3f normal = new Vector3f();
			for (Layer layer : layers) {
				VertexConsumer consumer = graphics.bufferSource().getBuffer(layer.type());
				for (int vertex = 0; vertex < layer.attributes().length / INTS; vertex++) {
					int f = vertex * FLOATS;
					int i = vertex * INTS;
					float[] geometry = layer.geometry();
					int[] attributes = layer.attributes();
					pose.last().pose().transformPosition(geometry[f], geometry[f + 1], geometry[f + 2], position);
					pose.last().transformNormal(geometry[f + 5], geometry[f + 6], geometry[f + 7], normal);
					consumer.addVertex(position.x, position.y, position.z).setColor(attributes[i])
						.setUv(geometry[f + 3], geometry[f + 4]).setOverlay(attributes[i + 1])
						.setLight(attributes[i + 2]).setNormal(normal.x, normal.y, normal.z);
				}
			}
			graphics.flush();
		} finally {
			pose.popPose();
			Lighting.setupFor3DItems();
			RenderSystem.setShaderColor(1, 1, 1, 1);
		}
	}

	private record Layer(RenderType type, float[] geometry, int[] attributes) {}

	private static final class Capture implements MultiBufferSource {
		private final Map<RenderType, Store> layers = new LinkedHashMap<>();
		private final long deadline = System.nanoTime() + 100_000_000L;
		private int vertices;
		private float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
		private float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

		@Override
		public VertexConsumer getBuffer(RenderType type) {
			Store existing = layers.get(type);
			if (existing != null)
				return existing;
			if (layers.size() >= MAX_LAYERS)
				throw new IllegalStateException("Donor preview exceeds layer limit");
			Store store = new Store(this);
			layers.put(type, store);
			return store;
		}
	}

	/** Write into the current vertex immediately: third-party consumers may set attributes in any order. */
	private static final class Store implements VertexConsumer {
		private final Capture capture;
		private float[] geometry = new float[128 * FLOATS];
		private int[] attributes = new int[128 * INTS];
		private int vertices;
		private int f;
		private int i;

		private Store(Capture capture) { this.capture = capture; }

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			if (++capture.vertices > MAX_VERTICES || (capture.vertices % 128 == 0 && System.nanoTime() > capture.deadline))
				throw new IllegalStateException("Donor preview exceeds geometry or preparation limit");
			if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
				throw new IllegalStateException("Non-finite donor geometry");
			if ((vertices + 1) * FLOATS > geometry.length) {
				int capacity = Math.min(MAX_VERTICES, vertices * 2);
				geometry = Arrays.copyOf(geometry, capacity * FLOATS);
				attributes = Arrays.copyOf(attributes, capacity * INTS);
			}
			f = vertices * FLOATS;
			i = vertices++ * INTS;
			geometry[f] = x;
			geometry[f + 1] = y;
			geometry[f + 2] = z;
			geometry[f + 6] = 1;
			attributes[i] = -1;
			attributes[i + 2] = LightTexture.FULL_BRIGHT;
			capture.minX = Math.min(capture.minX, x); capture.maxX = Math.max(capture.maxX, x);
			capture.minY = Math.min(capture.minY, y); capture.maxY = Math.max(capture.maxY, y);
			capture.minZ = Math.min(capture.minZ, z); capture.maxZ = Math.max(capture.maxZ, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			attributes[i] = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | b & 255;
			return this;
		}
		@Override
		public VertexConsumer setUv(float u, float v) { geometry[f + 3] = u; geometry[f + 4] = v; return this; }
		@Override
		public VertexConsumer setUv1(int u, int v) { attributes[i + 1] = u | v << 16; return this; }
		@Override
		public VertexConsumer setUv2(int u, int v) { attributes[i + 2] = u | v << 16; return this; }
		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			geometry[f + 5] = x; geometry[f + 6] = y; geometry[f + 7] = z; return this;
		}
	}
}
