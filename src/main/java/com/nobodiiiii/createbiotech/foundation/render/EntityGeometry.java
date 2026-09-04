package com.nobodiiiii.createbiotech.foundation.render;

import java.util.concurrent.atomic.AtomicInteger;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;

/**
 * Measures the axis-aligned extent of an entity's actual rendered vertices by
 * running one render pass into a buffer that records positions instead of
 * drawing them.
 *
 * <p>Vertex bounds are used rather than {@link EntityDimensions} because an
 * entity's collision box frequently disagrees with what its model draws. The
 * dimensions are only consulted as a fallback for entities that emit no
 * geometry at all.
 */
public final class EntityGeometry {
	private static final AtomicInteger ACTIVE_BASE_MODEL_MEASUREMENTS = new AtomicInteger();
	private static final ThreadLocal<BaseModelMeasurementScope> CURRENT_BASE_MODEL_MEASUREMENT = new ThreadLocal<>();

	/**
	 * Lower bound applied to auto-scaling divisors, so a very flat or very small
	 * model cannot blow its render scale up to infinity.
	 */
	public static final float MIN_AUTO_SCALE_DIMENSION = 0.75f;

	private EntityGeometry() {
	}

	/**
	 * Measures the entity in its unoriented render pose. Discards vertices as it
	 * goes; use {@link Collector#caching} when the positions must be replayed.
	 */
	private static Bounds measureBounds(LivingEntity entity) {
		Collector collector = Collector.boundsOnly();
		measureInto(entity, collector);
		return collector.bounds();
	}

	/**
	 * Center of the entity's drawn geometry, falling back to the midpoint of its
	 * collision box when nothing is drawn.
	 */
	public static Vector3f measureCenter(LivingEntity entity) {
		Bounds bounds = measureBounds(entity);
		if (bounds.hasVertices())
			return bounds.center();

		EntityDimensions dimensions = entity.getDimensions(entity.getPose());
		return new Vector3f(0.0f, dimensions.height() / 2.0f, 0.0f);
	}

	/**
	 * Runs one unoriented render pass into the given collector, in the entity's
	 * own coordinate space.
	 */
	public static void measureInto(LivingEntity entity, Collector collector) {
		MultiBufferSource measuringBuffer = renderType -> collector;
		EntityRenderHelper.renderUnoriented(entity, new PoseStack(), measuringBuffer, LightTexture.FULL_BRIGHT);
	}

	/**
	 * Resets {@code collector} and measures {@code entity} into it, substituting the
	 * collision box for entities that draw nothing so downstream centering and
	 * scaling still have something to work with.
	 */
	public static Collector measureWithFallback(LivingEntity entity, Collector collector) {
		collector.reset();
		measureInto(entity, collector);
		if (!collector.hasVertices())
			collector.includeEntityDimensions(entity.getDimensions(entity.getPose()));
		return collector;
	}

	/**
	 * Measures only the renderer's primary entity model. {@code RenderLayer} geometry such as
	 * villager clothing, held items, armor and independently rendered shields is deliberately
	 * excluded so optional attachments cannot decide the entity's physical orientation.
	 */
	public static Collector measureBaseModelWithFallback(LivingEntity entity, Collector collector) {
		return measureBaseModelWithFallback(entity, collector, () -> measureInto(entity, collector));
	}

	/**
	 * Measures a caller-supplied render pass while suppressing {@code RenderLayer} geometry.
	 * This variant is used when the primary model needs additional render-time filtering or
	 * transforms that cannot be expressed by rendering the entity directly.
	 */
	public static Collector measureBaseModelWithFallback(LivingEntity entity, Collector collector,
		Runnable renderPass) {
		collector.reset();
		try (BaseModelMeasurementScope ignored = openBaseModelMeasurement()) {
			renderPass.run();
		}
		if (!collector.hasVertices())
			collector.includeEntityDimensions(entity.getDimensions(entity.getPose()));
		return collector;
	}

	/** Used by the living-entity renderer mixin to suppress optional render layers in this scope. */
	public static boolean isBaseModelMeasurement() {
		if (ACTIVE_BASE_MODEL_MEASUREMENTS.get() == 0)
			return false;
		return CURRENT_BASE_MODEL_MEASUREMENT.get() != null;
	}

	private static BaseModelMeasurementScope openBaseModelMeasurement() {
		BaseModelMeasurementScope scope =
			new BaseModelMeasurementScope(Thread.currentThread(), CURRENT_BASE_MODEL_MEASUREMENT.get());
		CURRENT_BASE_MODEL_MEASUREMENT.set(scope);
		ACTIVE_BASE_MODEL_MEASUREMENTS.incrementAndGet();
		return scope;
	}

	private static final class BaseModelMeasurementScope implements AutoCloseable {
		private final Thread owner;
		private final BaseModelMeasurementScope parent;
		private boolean closed;

		private BaseModelMeasurementScope(Thread owner, BaseModelMeasurementScope parent) {
			this.owner = owner;
			this.parent = parent;
		}

		@Override
		public void close() {
			if (closed)
				throw new IllegalStateException("Base-model measurement scope was already closed");
			if (Thread.currentThread() != owner)
				throw new IllegalStateException("Base-model measurement scope closed on a different thread");
			if (CURRENT_BASE_MODEL_MEASUREMENT.get() != this)
				throw new IllegalStateException("Base-model measurement scopes must close in LIFO order");

			closed = true;
			if (parent == null)
				CURRENT_BASE_MODEL_MEASUREMENT.remove();
			else
				CURRENT_BASE_MODEL_MEASUREMENT.set(parent);
			if (ACTIVE_BASE_MODEL_MEASUREMENTS.decrementAndGet() < 0) {
				ACTIVE_BASE_MODEL_MEASUREMENTS.set(0);
				throw new IllegalStateException("Base-model measurement scope count underflow");
			}
		}
	}

	/**
	 * Accumulating axis-aligned bounds.
	 */
	public static final class Bounds {
		private float minX = Float.POSITIVE_INFINITY;
		private float minY = Float.POSITIVE_INFINITY;
		private float minZ = Float.POSITIVE_INFINITY;
		private float maxX = Float.NEGATIVE_INFINITY;
		private float maxY = Float.NEGATIVE_INFINITY;
		private float maxZ = Float.NEGATIVE_INFINITY;

		public void include(Vector3f vertex) {
			include(vertex.x(), vertex.y(), vertex.z());
		}

		public void include(float x, float y, float z) {
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
			maxZ = Math.max(maxZ, z);
		}

		public void reset() {
			minX = Float.POSITIVE_INFINITY;
			minY = Float.POSITIVE_INFINITY;
			minZ = Float.POSITIVE_INFINITY;
			maxX = Float.NEGATIVE_INFINITY;
			maxY = Float.NEGATIVE_INFINITY;
			maxZ = Float.NEGATIVE_INFINITY;
		}

		public boolean hasVertices() {
			return minX != Float.POSITIVE_INFINITY;
		}

		public float minX() {
			return minX;
		}

		public float minY() {
			return minY;
		}

		public float minZ() {
			return minZ;
		}

		public float maxX() {
			return maxX;
		}

		public float maxY() {
			return maxY;
		}

		public float maxZ() {
			return maxZ;
		}

		public float centerX() {
			return (minX + maxX) / 2.0f;
		}

		public float centerY() {
			return (minY + maxY) / 2.0f;
		}

		public float centerZ() {
			return (minZ + maxZ) / 2.0f;
		}

		public Vector3f center() {
			return new Vector3f(centerX(), centerY(), centerZ());
		}

		public float sizeX() {
			return maxX - minX;
		}

		public float sizeY() {
			return maxY - minY;
		}

		public float sizeZ() {
			return maxZ - minZ;
		}

		public float largestDimension() {
			return Math.max(Math.max(sizeX(), sizeY()), sizeZ());
		}
	}

	/**
	 * A {@link VertexConsumer} that records positions instead of drawing them.
	 *
	 * <p>In caching mode it also retains the raw positions so the same render pass
	 * can be re-projected under different transforms without re-running the entity
	 * renderer. Retention stops at {@code maxVertices}; past that the collector
	 * keeps accumulating bounds but {@link #transformBounds} falls back to
	 * projecting the eight corners of the accumulated box.
	 */
	public static final class Collector implements VertexConsumer {
		private static final int INITIAL_FLOAT_CAPACITY = 4_096 * 3;

		private final Bounds bounds = new Bounds();
		private final int maxVertices;
		private float[] vertices;
		private int floatCount;
		private boolean truncated;

		private Collector(int maxVertices) {
			this.maxVertices = maxVertices;
			this.vertices = maxVertices > 0 ? new float[INITIAL_FLOAT_CAPACITY] : new float[0];
		}

		/**
		 * Accumulates bounds only; positions are discarded as they arrive.
		 */
		public static Collector boundsOnly() {
			return new Collector(0);
		}

		/**
		 * Retains up to {@code maxVertices} positions for later re-projection.
		 */
		public static Collector caching(int maxVertices) {
			return new Collector(maxVertices);
		}

		public Bounds bounds() {
			return bounds;
		}

		public boolean hasVertices() {
			return bounds.hasVertices();
		}

		public void reset() {
			bounds.reset();
			floatCount = 0;
			truncated = false;
		}

		public void includeEntityDimensions(EntityDimensions dimensions) {
			float halfWidth = dimensions.width() / 2.0f;
			float height = dimensions.height();
			for (float x : new float[] { -halfWidth, halfWidth })
				for (float y : new float[] { 0.0f, height })
					for (float z : new float[] { -halfWidth, halfWidth })
						store(x, y, z);
		}

		/**
		 * Projects the recorded geometry through {@code transform} and returns its
		 * bounds in the target space.
		 */
		public Bounds transformBounds(Matrix4f transform) {
			Bounds transformed = new Bounds();
			Vector3f scratch = new Vector3f();
			if (truncated || maxVertices <= 0) {
				for (float x : new float[] { bounds.minX, bounds.maxX })
					for (float y : new float[] { bounds.minY, bounds.maxY })
						for (float z : new float[] { bounds.minZ, bounds.maxZ }) {
							transform.transformPosition(x, y, z, scratch);
							transformed.include(scratch);
						}
				return transformed;
			}

			for (int i = 0; i < floatCount; i += 3) {
				transform.transformPosition(vertices[i], vertices[i + 1], vertices[i + 2], scratch);
				transformed.include(scratch);
			}
			return transformed;
		}

		private void store(float x, float y, float z) {
			if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
				return;
			bounds.include(x, y, z);
			if (maxVertices <= 0)
				return;
			if (floatCount / 3 >= maxVertices) {
				truncated = true;
				return;
			}
			ensureCapacity(floatCount + 3);
			vertices[floatCount++] = x;
			vertices[floatCount++] = y;
			vertices[floatCount++] = z;
		}

		private void ensureCapacity(int required) {
			if (required <= vertices.length)
				return;
			int capacity = Math.min(maxVertices * 3, Math.max(required, vertices.length * 2));
			float[] expanded = new float[capacity];
			System.arraycopy(vertices, 0, expanded, 0, floatCount);
			vertices = expanded;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			store(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}
	}
}
