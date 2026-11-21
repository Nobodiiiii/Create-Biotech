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
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalProfiler;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalSourcePlanCache implementation; public entry points remain on SurgicalSourceModelRenderer. */
final class SurgicalSourcePlanCache {
	private SurgicalSourcePlanCache() {}

	static final Map<RenderPlanKey, SurgicalCapturedRenderPlan> RENDER_PLANS =
		new LinkedHashMap<>(64, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<RenderPlanKey, SurgicalCapturedRenderPlan> eldest) {
				return size() > SurgicalSourceModelRenderer.MAX_RENDER_PLANS;
			}
		};
	static final Map<RenderPlanKey, PendingRenderPlan> PENDING_RENDER_PLANS = new HashMap<>();
	static final Map<LivingEntity, CachedRenderPlan> FALLBACK_RENDER_PLANS = new WeakHashMap<>();

	/**
	 * Captured plans are a fixed pose, not a frame of a running animation, so callers' frame partial
	 * ticks are deliberately not forwarded here - see {@link #captureInputInCanonicalPose}.
	 */
	static SurgicalCapturedRenderPlan plan(LivingEntity preview, float yaw) {
		EntityRenderer<LivingEntity> renderer = SurgicalSourceModelRenderer.renderer(preview);
		MimicProfile profile = SurgicalSourceModelRenderer.PREVIEW_PROFILES.get(preview);
		if (profile != null) {
			RenderPlanKey key = new RenderPlanKey(profile, renderer, Float.floatToIntBits(yaw));
			SurgicalCapturedRenderPlan plan = completedPlan(key);
			if (plan == null) {
				// Deliberately leave any in-flight build alone. cancel(false) cannot stop a worker that
				// has already started, so cancelling only discarded a result that was often milliseconds
				// from completion while the thread kept running - paying for the same plan twice and
				// stalling this frame either way. collectCompletedPlans() harvests the duplicate on a
				// later frame and overwrites this entry with an equal, immutable plan.
				long started = SurgicalProfiler.begin();
				plan = SurgicalSourceModelRenderer.captureInCanonicalPose(renderer, preview, yaw).forProfile(profile);
				SurgicalProfiler.end("capture(plan)", started);
				RENDER_PLANS.put(key, plan);
			}
			return plan;
		}

		CachedRenderPlan cached = FALLBACK_RENDER_PLANS.get(preview);
		if (cached == null || cached.renderer != renderer
			|| Float.floatToIntBits(cached.yaw) != Float.floatToIntBits(yaw)) {
			SurgicalCapturedRenderPlan plan = SurgicalSourceModelRenderer.captureInCanonicalPose(renderer, preview, yaw);
			cached = new CachedRenderPlan(renderer, yaw, plan);
			FALLBACK_RENDER_PLANS.put(preview, cached);
		}
		return cached.plan;
	}

	static SurgicalSourceModelRenderer.RenderPlanState renderPlanState(LivingEntity preview, float yaw) {
		EntityRenderer<LivingEntity> renderer = SurgicalSourceModelRenderer.renderer(preview);
		MimicProfile profile = SurgicalSourceModelRenderer.PREVIEW_PROFILES.get(preview);
		if (profile == null)
			return SurgicalSourceModelRenderer.RenderPlanState.MISSING;
		RenderPlanKey key = new RenderPlanKey(profile, renderer, Float.floatToIntBits(yaw));
		// get(), not containsKey(): RENDER_PLANS is an access-ordered LRU and only get() marks an entry
		// as recently used. Probing with containsKey() left a plan looking stale to the eviction policy
		// however often it was asked about, so the very plan a visible subject checks every frame could
		// still be evicted - and plan() would then rebuild it synchronously on the render thread.
		if (RENDER_PLANS.get(key) != null)
			return SurgicalSourceModelRenderer.RenderPlanState.READY;
		return PENDING_RENDER_PLANS.containsKey(key) ? SurgicalSourceModelRenderer.RenderPlanState.PENDING : SurgicalSourceModelRenderer.RenderPlanState.MISSING;
	}

	/**
	 * Captures the renderer on the client thread, then recovers cuboids and texture metadata on the
	 * bounded surgical worker pool. A null result means the immutable plan is still being built and
	 * the caller should retry on a later frame.
	 */
	@Nullable
	static SurgicalModelRenderContext.Snapshot captureGeometryDeferred(LivingEntity preview, int cubeCount,
		BitSet presentCubes, PoseStack poseStack, float yaw, float partialTick,
		@Nullable Vec3 cameraPosition) {
		return geometryDeferred(preview, cubeCount, presentCubes, Map.of(), Map.of(), poseStack, yaw, cameraPosition);
	}

	/** Geometry only: no vertex replay, no synchronous fallback while a plan is pending. */
	@Nullable
	static SurgicalModelRenderContext.Snapshot geometryDeferred(LivingEntity preview, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> offsets, Map<Integer, SurgicalCubeRotation> rotations,
		PoseStack poseStack, float yaw, @Nullable Vec3 cameraPosition) {
		SurgicalSourceModelRenderer.preparePreview(preview, yaw);
		EntityRenderer<LivingEntity> renderer = SurgicalSourceModelRenderer.renderer(preview);
		MimicProfile profile = SurgicalSourceModelRenderer.PREVIEW_PROFILES.get(preview);
		if (profile == null) {
			if (!SurgicalPreparationBudget.begin(true)) return null;
			try { return plan(preview, yaw).snapshot(poseStack, cubeCount, presentCubes, offsets, rotations, cameraPosition); }
			finally { SurgicalPreparationBudget.end(); }
		}

		RenderPlanKey key = new RenderPlanKey(profile, renderer, Float.floatToIntBits(yaw));
		SurgicalCapturedRenderPlan plan = completedPlan(key);
		if (plan == null) {
			PendingRenderPlan pending = PENDING_RENDER_PLANS.get(key);
			if (pending == null) {
				if (PENDING_RENDER_PLANS.size() >= SurgicalSourceModelRenderer.MAX_PENDING_RENDER_PLANS
					|| !SurgicalPreparationBudget.begin(true))
					return null;
				long started = SurgicalProfiler.begin();
				SurgicalCapturedRenderPlan.CapturedInput captured;
				try { captured = SurgicalSourceModelRenderer.captureInputInCanonicalPose(renderer, preview, yaw); }
				finally {
					SurgicalPreparationBudget.end();
					SurgicalProfiler.end("capture(plan-input)", started);
				}
				int generation = SurgicalSourceModelRenderer.resourceGeneration;
				pending = new PendingRenderPlan(generation,
					SurgicalClientExecutors.submit(() -> buildPlan(captured).forProfile(profile)));
				PENDING_RENDER_PLANS.put(key, pending);
				// Publish only from the next frame. Besides keeping the handoff deterministic, this
				// prevents a full worker queue from causing several same-frame recaptures of one profile.
				return null;
			}
			plan = completedPlan(key);
			if (plan == null)
				return null;
		}

		if (!SurgicalPreparationBudget.begin(false)) return null;
		long started = SurgicalProfiler.begin();
		try { return plan.snapshot(poseStack, cubeCount, presentCubes, offsets, rotations, cameraPosition); }
		finally {
			SurgicalPreparationBudget.end();
			SurgicalProfiler.end("captureGeometry", started);
		}
	}

	static SurgicalCapturedRenderPlan buildPlan(SurgicalCapturedRenderPlan.CapturedInput captured) {
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
			if (pending.generation != SurgicalSourceModelRenderer.resourceGeneration) {
				pending.future.cancel(false);
				iterator.remove();
				continue;
			}
			if (!pending.future.isDone())
				continue;
			iterator.remove();
			try {
				SurgicalCapturedRenderPlan completed = pending.future.join();
				if (pending.generation == SurgicalSourceModelRenderer.resourceGeneration)
					RENDER_PLANS.put(entry.getKey(), completed);
			} catch (RuntimeException ignored) {
				// A later visible-frame request may retry this immutable plan.
			}
		}
	}

	@Nullable
	static SurgicalCapturedRenderPlan completedPlan(RenderPlanKey key) {
		SurgicalCapturedRenderPlan completed = RENDER_PLANS.get(key);
		if (completed != null)
			return completed;
		PendingRenderPlan pending = PENDING_RENDER_PLANS.get(key);
		if (pending == null)
			return null;
		if (pending.generation != SurgicalSourceModelRenderer.resourceGeneration) {
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
		if (pending.generation != SurgicalSourceModelRenderer.resourceGeneration)
			return null;
		RENDER_PLANS.put(key, completed);
		return completed;
	}

	record RenderPlanKey(MimicProfile profile, EntityRenderer<LivingEntity> renderer, int yawBits) {}

	record CachedRenderPlan(EntityRenderer<LivingEntity> renderer, float yaw,
		SurgicalCapturedRenderPlan plan) {}

	record PendingRenderPlan(int generation, CompletableFuture<SurgicalCapturedRenderPlan> future) {}
}
