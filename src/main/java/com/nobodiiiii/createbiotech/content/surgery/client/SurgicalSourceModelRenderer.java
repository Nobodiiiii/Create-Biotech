package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalProfiler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class SurgicalSourceModelRenderer {
	private static final int MAX_RENDER_PLANS = 512;
	private static final int MAX_PENDING_RENDER_PLANS = 64;
	/**
	 * One preview per distinct appearance. A preview entity is a pure function of its profile - it is
	 * never added to the level, carries no owner state, and {@link MimicProfile#createPreviewEntity}
	 * fully determines it - so keying this per owner meant every part cut from one creature built and
	 * held its own identical entity. Profiles are interned, so equal appearances land on one entry.
	 */
	private static final Map<MimicProfile, CachedPreview> PREVIEWS = new WeakHashMap<>();
	private static final Map<LivingEntity, MimicProfile> PREVIEW_PROFILES = new WeakHashMap<>();
	private static final Map<RenderPlanKey, SurgicalCapturedRenderPlan> RENDER_PLANS =
		new LinkedHashMap<>(64, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<RenderPlanKey, SurgicalCapturedRenderPlan> eldest) {
				return size() > MAX_RENDER_PLANS;
			}
		};
	private static final Map<RenderPlanKey, PendingRenderPlan> PENDING_RENDER_PLANS = new HashMap<>();
	private static final Map<LivingEntity, CachedRenderPlan> FALLBACK_RENDER_PLANS = new WeakHashMap<>();
	private static int resourceGeneration;

	private SurgicalSourceModelRenderer() {}

	enum RenderPlanState {
		READY,
		PENDING,
		MISSING
	}

	@Nullable
	public static LivingEntity preview(MimicProfile profile) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null)
			return null;

		CachedPreview cached = PREVIEWS.get(profile);
		if (cached != null && cached.level == level)
			return cached.entity;

		long started = SurgicalProfiler.begin();
		LivingEntity entity = profile.createPreviewEntity(level);
		SurgicalProfiler.end("createPreviewEntity", started);
		if (entity == null)
			return null;
		PREVIEWS.put(profile, new CachedPreview(level, entity));
		PREVIEW_PROFILES.put(entity, profile);
		return entity;
	}

	public static SurgicalModelRenderContext.Snapshot render(MimicProfile profile, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> cubeOffsets, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float yaw, float partialTick, boolean collectGeometry, @Nullable Vec3 cameraPosition) {
		return render(profile, cubeCount, presentCubes, cubeOffsets, Map.of(), poseStack, buffer, packedLight,
			yaw, partialTick, collectGeometry, cameraPosition);
	}

	public static SurgicalModelRenderContext.Snapshot render(MimicProfile profile, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> cubeOffsets,
		Map<Integer, SurgicalCubeRotation> cubeRotations, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float yaw, float partialTick, boolean collectGeometry, @Nullable Vec3 cameraPosition) {
		LivingEntity preview = preview(profile);
		if (preview == null)
			return new SurgicalModelRenderContext.Snapshot(0, java.util.List.of());
		return render(preview, cubeCount, presentCubes, cubeOffsets, cubeRotations, poseStack, buffer, packedLight,
			yaw, partialTick, collectGeometry, cameraPosition);
	}

	public static SurgicalModelRenderContext.Snapshot render(LivingEntity preview, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> cubeOffsets, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float yaw, float partialTick, boolean collectGeometry, @Nullable Vec3 cameraPosition) {
		return render(preview, cubeCount, presentCubes, cubeOffsets, Map.of(), poseStack, buffer, packedLight,
			yaw, partialTick, collectGeometry, cameraPosition, false);
	}

	public static SurgicalModelRenderContext.Snapshot render(LivingEntity preview, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> cubeOffsets,
		Map<Integer, SurgicalCubeRotation> cubeRotations, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float yaw, float partialTick, boolean collectGeometry, @Nullable Vec3 cameraPosition) {
		return render(preview, cubeCount, presentCubes, cubeOffsets, cubeRotations, poseStack, buffer, packedLight,
			yaw, partialTick, collectGeometry, cameraPosition, false);
	}

	public static SurgicalModelRenderContext.Snapshot render(LivingEntity preview, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> cubeOffsets, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float yaw, float partialTick, boolean collectGeometry, @Nullable Vec3 cameraPosition,
		boolean renderSourceGeometry) {
		return render(preview, cubeCount, presentCubes, cubeOffsets, Map.of(), poseStack, buffer, packedLight,
			yaw, partialTick, collectGeometry, cameraPosition, renderSourceGeometry);
	}

	public static SurgicalModelRenderContext.Snapshot render(LivingEntity preview, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> cubeOffsets,
		Map<Integer, SurgicalCubeRotation> cubeRotations, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float yaw, float partialTick, boolean collectGeometry, @Nullable Vec3 cameraPosition,
		boolean renderSourceGeometry) {
		return render(preview, cubeCount, presentCubes, cubeOffsets, cubeRotations, poseStack, buffer, packedLight,
			yaw, partialTick, collectGeometry, cameraPosition, renderSourceGeometry, 1.0f);
	}

	public static SurgicalModelRenderContext.Snapshot render(LivingEntity preview, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> cubeOffsets,
		Map<Integer, SurgicalCubeRotation> cubeRotations, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float yaw, float partialTick, boolean collectGeometry, @Nullable Vec3 cameraPosition,
		boolean renderSourceGeometry, float alpha) {
		preparePreview(preview, yaw);
		SurgicalCapturedRenderPlan plan = plan(preview, yaw, partialTick);
		float clampedAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
		MultiBufferSource renderBuffer = clampedAlpha < 1.0f
			? new AlphaBufferSource(buffer, clampedAlpha) : buffer;
		return plan.render(poseStack, renderBuffer, packedLight, cubeCount, presentCubes, cubeOffsets, cubeRotations,
			collectGeometry, cameraPosition, renderSourceGeometry);
	}

	/** Renders exactly one captured cuboid without duplicating model-wide non-cuboid extras. */
	public static SurgicalModelRenderContext.Snapshot renderSingleCube(MimicProfile profile,
		int cube, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		LivingEntity preview = preview(profile);
		if (preview == null)
			return new SurgicalModelRenderContext.Snapshot(0, java.util.List.of());
		preparePreview(preview, 0.0f);
		return plan(preview, 0.0f, 0.0f).renderSingleCube(poseStack, buffer, packedLight, cube);
	}

	/** Returns the neutral-pose source geometry used to align one released cuboid to its entity. */
	@Nullable
	public static SurgicalModelRenderContext.CubeGeometry singleCubeGeometry(
		MimicProfile profile, int cube) {
		LivingEntity preview = preview(profile);
		if (preview == null)
			return null;
		preparePreview(preview, 0.0f);
		return plan(preview, 0.0f, 0.0f).singleCubeGeometry(cube);
	}

	private static SurgicalCapturedRenderPlan plan(LivingEntity preview, float yaw, float partialTick) {
		EntityRenderer<LivingEntity> renderer = renderer(preview);
		MimicProfile profile = PREVIEW_PROFILES.get(preview);
		if (profile != null) {
			RenderPlanKey key = new RenderPlanKey(profile, renderer, Float.floatToIntBits(yaw));
			SurgicalCapturedRenderPlan plan = completedPlan(key);
			if (plan == null) {
				PendingRenderPlan pending = PENDING_RENDER_PLANS.remove(key);
				if (pending != null)
					pending.future.cancel(false);
				long started = SurgicalProfiler.begin();
				plan = SurgicalCapturedRenderPlan.capture(renderer, preview, yaw, partialTick, true);
				SurgicalProfiler.end("capture(plan)", started);
				RENDER_PLANS.put(key, plan);
			}
			return plan;
		}

		CachedRenderPlan cached = FALLBACK_RENDER_PLANS.get(preview);
		if (cached == null || cached.renderer != renderer
			|| Float.floatToIntBits(cached.yaw) != Float.floatToIntBits(yaw)) {
			SurgicalCapturedRenderPlan plan =
				SurgicalCapturedRenderPlan.capture(renderer, preview, yaw, partialTick, true);
			cached = new CachedRenderPlan(renderer, yaw, plan);
			FALLBACK_RENDER_PLANS.put(preview, cached);
		}
		return cached.plan;
	}

	static RenderPlanState renderPlanState(LivingEntity preview, float yaw) {
		EntityRenderer<LivingEntity> renderer = renderer(preview);
		MimicProfile profile = PREVIEW_PROFILES.get(preview);
		if (profile == null)
			return RenderPlanState.MISSING;
		RenderPlanKey key = new RenderPlanKey(profile, renderer, Float.floatToIntBits(yaw));
		if (RENDER_PLANS.containsKey(key))
			return RenderPlanState.READY;
		return PENDING_RENDER_PLANS.containsKey(key) ? RenderPlanState.PENDING : RenderPlanState.MISSING;
	}

	/**
	 * Captures the renderer on the client thread, then recovers cuboids and texture metadata on the
	 * bounded surgical worker pool. A null result means the immutable plan is still being built and
	 * the caller should retry on a later frame.
	 */
	@Nullable
	public static SurgicalModelRenderContext.Snapshot captureGeometryDeferred(LivingEntity preview, int cubeCount,
		BitSet presentCubes, PoseStack poseStack, float yaw, float partialTick,
		@Nullable Vec3 cameraPosition) {
		preparePreview(preview, yaw);
		EntityRenderer<LivingEntity> renderer = renderer(preview);
		MimicProfile profile = PREVIEW_PROFILES.get(preview);
		if (profile == null)
			return captureGeometry(preview, cubeCount, presentCubes, poseStack, LightTexture.FULL_BRIGHT,
				yaw, partialTick, cameraPosition, false);

		RenderPlanKey key = new RenderPlanKey(profile, renderer, Float.floatToIntBits(yaw));
		SurgicalCapturedRenderPlan plan = completedPlan(key);
		if (plan == null) {
			PendingRenderPlan pending = PENDING_RENDER_PLANS.get(key);
			if (pending == null) {
				if (PENDING_RENDER_PLANS.size() >= MAX_PENDING_RENDER_PLANS)
					return null;
				long started = SurgicalProfiler.begin();
				SurgicalCapturedRenderPlan.CapturedInput captured =
					SurgicalCapturedRenderPlan.captureInput(renderer, preview, yaw, partialTick, true);
				SurgicalProfiler.end("capture(plan-input)", started);
				int generation = resourceGeneration;
				pending = new PendingRenderPlan(generation,
					SurgicalClientExecutors.submit(() -> buildPlan(captured)));
				PENDING_RENDER_PLANS.put(key, pending);
				// Publish only from the next frame. Besides keeping the handoff deterministic, this
				// prevents a full worker queue from causing several same-frame recaptures of one profile.
				return null;
			}
			plan = completedPlan(key);
			if (plan == null)
				return null;
		}

		long started = SurgicalProfiler.begin();
		SurgicalModelRenderContext.Snapshot snapshot = plan.snapshot(poseStack, cubeCount, presentCubes,
			Map.of(), Map.of(), cameraPosition);
		SurgicalProfiler.end("captureGeometry", started);
		return snapshot;
	}

	private static SurgicalCapturedRenderPlan buildPlan(SurgicalCapturedRenderPlan.CapturedInput captured) {
		long started = SurgicalProfiler.begin();
		try {
			return SurgicalCapturedRenderPlan.build(captured);
		} finally {
			SurgicalProfiler.end("build(plan)", started);
		}
	}

	/** Publishes completed worker results on the client thread and frees pending-capacity promptly. */
	static void collectCompletedPlans() {
		Iterator<Map.Entry<RenderPlanKey, PendingRenderPlan>> iterator =
			PENDING_RENDER_PLANS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<RenderPlanKey, PendingRenderPlan> entry = iterator.next();
			PendingRenderPlan pending = entry.getValue();
			if (pending.generation != resourceGeneration) {
				pending.future.cancel(false);
				iterator.remove();
				continue;
			}
			if (!pending.future.isDone())
				continue;
			iterator.remove();
			try {
				SurgicalCapturedRenderPlan completed = pending.future.join();
				if (pending.generation == resourceGeneration)
					RENDER_PLANS.put(entry.getKey(), completed);
			} catch (RuntimeException ignored) {
				// A later visible-frame request may retry this immutable plan.
			}
		}
	}

	@Nullable
	private static SurgicalCapturedRenderPlan completedPlan(RenderPlanKey key) {
		SurgicalCapturedRenderPlan completed = RENDER_PLANS.get(key);
		if (completed != null)
			return completed;
		PendingRenderPlan pending = PENDING_RENDER_PLANS.get(key);
		if (pending == null)
			return null;
		if (pending.generation != resourceGeneration) {
			pending.future.cancel(false);
			PENDING_RENDER_PLANS.remove(key);
			return null;
		}
		if (!pending.future.isDone())
			return null;
		PENDING_RENDER_PLANS.remove(key);
		try {
			completed = pending.future.join();
		} catch (RuntimeException ignored) {
			return null;
		}
		if (pending.generation != resourceGeneration)
			return null;
		RENDER_PLANS.put(key, completed);
		return completed;
	}

	/** Captures unoffset source geometry without submitting the temporary model to the visible buffer. */
	public static SurgicalModelRenderContext.Snapshot captureGeometry(LivingEntity preview, int cubeCount,
		BitSet presentCubes, PoseStack poseStack, int packedLight, float yaw, float partialTick,
		@Nullable Vec3 cameraPosition, boolean renderSourceGeometry) {
		long started = SurgicalProfiler.begin();
		preparePreview(preview, yaw);
		SurgicalModelRenderContext.Snapshot snapshot = plan(preview, yaw, partialTick)
			.snapshot(poseStack, cubeCount, presentCubes, Map.of(), Map.of(), cameraPosition);
		SurgicalProfiler.end("captureGeometry", started);
		return snapshot;
	}

	private static void preparePreview(LivingEntity preview, float yaw) {
		preview.setYRot(yaw);
		preview.yRotO = yaw;
		preview.yBodyRot = yaw;
		preview.yBodyRotO = yaw;
		preview.yHeadRot = yaw;
		preview.yHeadRotO = yaw;
		preview.tickCount = 0;
	}

	@SuppressWarnings("unchecked")
	private static EntityRenderer<LivingEntity> renderer(LivingEntity preview) {
		EntityRenderer<LivingEntity> renderer = (EntityRenderer<LivingEntity>) Minecraft.getInstance()
			.getEntityRenderDispatcher().getRenderer(preview);
		return renderer;
	}

	public static void clear() {
		PREVIEWS.clear();
		PREVIEW_PROFILES.clear();
		RENDER_PLANS.clear();
		resourceGeneration++;
		PENDING_RENDER_PLANS.values().forEach(pending -> pending.future.cancel(false));
		PENDING_RENDER_PLANS.clear();
		FALLBACK_RENDER_PLANS.clear();
		SurgicalCapturedRenderPlan.clearResources();
	}

	/**
	 * Deliberately does not hold its profile: the profile is this entry's key in a weak map, so a
	 * reference from the value back to it would pin every entry forever.
	 */
	private record CachedPreview(ClientLevel level, LivingEntity entity) {}

	private record RenderPlanKey(MimicProfile profile, EntityRenderer<LivingEntity> renderer, int yawBits) {}

	private record CachedRenderPlan(EntityRenderer<LivingEntity> renderer, float yaw,
		SurgicalCapturedRenderPlan plan) {}

	private record PendingRenderPlan(int generation, CompletableFuture<SurgicalCapturedRenderPlan> future) {}

	private record AlphaBufferSource(MultiBufferSource delegate, float alpha) implements MultiBufferSource {
		@Override
		public VertexConsumer getBuffer(RenderType renderType) {
			RenderType translucent = SurgicalCapturedRenderPlan.translucentPreviewType(renderType);
			return new AlphaVertexConsumer(delegate.getBuffer(translucent), alpha);
		}
	}

	private record AlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			delegate.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int sourceAlpha) {
			int multipliedAlpha = Math.max(0, Math.min(255, Math.round(sourceAlpha * alpha)));
			delegate.setColor(red, green, blue, multipliedAlpha);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			delegate.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			delegate.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			delegate.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			delegate.setNormal(x, y, z);
			return this;
		}
	}
}
