package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.ToDoubleFunction;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalProfiler;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.phys.Vec3;

public final class SurgicalSourceModelRenderer {
	static final int MAX_RENDER_PLANS = 512;
	static final int MAX_PENDING_RENDER_PLANS = 64;
	private static final int CAPTURE_ENTITY_ID = 0;
	/**
	 * One preview per distinct appearance. A preview entity is a pure function of its profile - it is
	 * never added to the level, carries no owner state, and {@link MimicProfile#createPreviewEntity}
	 * fully determines it - so keying this per owner meant every part cut from one creature built and
	 * held its own identical entity. Profiles are interned, so equal appearances land on one entry.
	 */
	private static final Map<MimicProfile, CachedPreview> PREVIEWS = new WeakHashMap<>();
	static final Map<LivingEntity, MimicProfile> PREVIEW_PROFILES = new WeakHashMap<>();
	private static final Map<MimicProfile, DonorGeometry> DONOR_GEOMETRIES = new WeakHashMap<>();

	/** Frozen native geometry and accessory metadata; safe to read on surgical workers. */
	public record DonorGeometry(Map<Integer, SurgicalModelRenderContext.CubeGeometry> cubes,
		Map<Integer, SourceRidingSeat> seats, Map<Integer, java.util.List<Vec3>> saddles,
		Map<Integer, java.util.List<Vec3>> chests) {
		public DonorGeometry {
			cubes = Map.copyOf(cubes);
			seats = Map.copyOf(seats);
			saddles = Map.copyOf(saddles);
			chests = Map.copyOf(chests);
		}
	}

	@Nullable
	public static DonorGeometry donorGeometryDeferred(MimicProfile profile) {
		DonorGeometry cached = DONOR_GEOMETRIES.get(profile);
		if (cached != null) return cached;
		LivingEntity entity = previewDeferred(profile);
		if (entity == null) return null;
		var snapshot = SurgicalSourcePlanCache.geometryDeferred(entity, 0, new BitSet(), Map.of(), Map.of(), new PoseStack(), 0, null);
		if (snapshot == null) return null;
		if (!SurgicalPreparationBudget.begin(entity instanceof Saddleable)) return null;
		try { return cacheDonorGeometry(profile, entity, snapshot); }
		finally { SurgicalPreparationBudget.end(); }
	}

	/** Existing synchronous consumers share the same metadata, including negative accessory results. */
	@Nullable
	public static DonorGeometry donorGeometry(MimicProfile profile) {
		DonorGeometry cached = DONOR_GEOMETRIES.get(profile);
		if (cached != null) return cached;
		LivingEntity entity = preview(profile);
		if (entity == null) return null;
		return cacheDonorGeometry(profile, entity,
			SurgicalSourcePlanCache.plan(entity, 0).snapshot(new PoseStack(), 0, new BitSet(), Map.of(), Map.of(), null));
	}

	private static DonorGeometry cacheDonorGeometry(MimicProfile profile, LivingEntity entity,
		SurgicalModelRenderContext.Snapshot snapshot) {
		Map<Integer, SurgicalModelRenderContext.CubeGeometry> cubes = new HashMap<>();
		Map<Integer, SourceRidingSeat> seats = new HashMap<>();
		Map<Integer, java.util.List<Vec3>> saddles = new HashMap<>(), chests = new HashMap<>();
		for (var cube : snapshot.cubes()) {
			cubes.put(cube.cubeId(), cube);
			if (!(entity instanceof Saddleable)) continue;
			SourceRidingSeat seat = SurgicalEquipmentRenderPlans.ridingSeat(profile, cube.cubeId());
			if (seat == null) continue;
			seats.put(cube.cubeId(), seat);
			saddles.put(cube.cubeId(), java.util.List.copyOf(SurgicalEquipmentRenderPlans.equipmentCorners(profile, cube.cubeId(), false)));
			chests.put(cube.cubeId(), java.util.List.copyOf(SurgicalEquipmentRenderPlans.equipmentCorners(profile, cube.cubeId(), true)));
		}
		DonorGeometry result = new DonorGeometry(cubes, seats, saddles, chests);
		DONOR_GEOMETRIES.put(profile, result);
		return result;
	}

	static int resourceGeneration;

	private SurgicalSourceModelRenderer() {}

	public static int resourceGeneration() {
		return resourceGeneration;
	}

	enum RenderPlanState {
		READY,
		PENDING,
		MISSING
	}

	/** Budgeted creation for resumable preparation; live entity rendering keeps its synchronous API. */
	@Nullable
	public static LivingEntity previewDeferred(MimicProfile profile) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) return null;
		CachedPreview cached = PREVIEWS.get(profile);
		if (cached != null && cached.level == level) return cached.entity;
		if (!SurgicalPreparationBudget.begin(true)) return null;
		try { return preview(profile); }
		finally { SurgicalPreparationBudget.end(); }
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
		return render(preview, cubeCount, presentCubes, cubeOffsets, cubeRotations, poseStack, buffer,
			packedLight, yaw, partialTick, collectGeometry, cameraPosition, renderSourceGeometry, alpha,
			null);
	}

	public static SurgicalModelRenderContext.Snapshot render(LivingEntity preview, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> cubeOffsets,
		Map<Integer, SurgicalCubeRotation> cubeRotations, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float yaw, float partialTick, boolean collectGeometry, @Nullable Vec3 cameraPosition,
		boolean renderSourceGeometry, float alpha, @Nullable Boolean hurtOverlay) {
		return render(preview, cubeCount, presentCubes, cubeOffsets, cubeRotations, poseStack, buffer,
			packedLight, yaw, partialTick, collectGeometry, cameraPosition, renderSourceGeometry, alpha,
			hurtOverlay, null);
	}

	/** Live equipment changes appearance only; the saved surgical topology remains the reference. */
	public static SurgicalModelRenderContext.Snapshot render(LivingEntity preview, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> cubeOffsets,
		Map<Integer, SurgicalCubeRotation> cubeRotations, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float yaw, float partialTick, boolean collectGeometry, @Nullable Vec3 cameraPosition,
		boolean renderSourceGeometry, float alpha, @Nullable Boolean hurtOverlay, @Nullable Boolean saddled) {
		return render(preview, cubeCount, presentCubes, cubeOffsets, cubeRotations, poseStack, buffer,
			packedLight, yaw, partialTick, collectGeometry, cameraPosition, renderSourceGeometry, alpha,
			hurtOverlay, saddled, null);
	}

	public static SurgicalModelRenderContext.Snapshot render(LivingEntity preview, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> cubeOffsets,
		Map<Integer, SurgicalCubeRotation> cubeRotations, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
		float yaw, float partialTick, boolean collectGeometry, @Nullable Vec3 cameraPosition,
		boolean renderSourceGeometry, float alpha, @Nullable Boolean hurtOverlay, @Nullable Boolean saddled,
		@Nullable Boolean chested) {
		preparePreview(preview, yaw);
		SurgicalCapturedRenderPlan plan = SurgicalSourcePlanCache.plan(preview, yaw);
		if (saddled != null)
			plan = SurgicalEquipmentRenderPlans.saddlePlan(preview, yaw, saddled, plan);
		if (chested != null)
			plan = SurgicalEquipmentRenderPlans.chestPlan(preview, yaw, saddled, chested, plan);
		MultiBufferSource renderBuffer = alphaBuffer(buffer, alpha);
		return plan.render(poseStack, renderBuffer, packedLight, cubeCount, presentCubes, cubeOffsets, cubeRotations,
			collectGeometry, cameraPosition, renderSourceGeometry, hurtOverlay);
	}

	/** A per-body mask; shared donor previews and cached plans retain their original appearance. */
	public static BitSet visibleCubes(LivingEntity preview, BitSet presentCubes, boolean sheared) {
		if (!sheared)
			return presentCubes;
		preparePreview(preview, 0);
		return SurgicalSourcePlanCache.plan(preview, 0).withoutWool(presentCubes);
	}

	/** Render the naked donor, carrier equipment, and head tack bound to each retained head cube. */
	public static SurgicalModelRenderContext.Snapshot renderEquipped(LivingEntity preview, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> cubeOffsets, Map<Integer, SurgicalCubeRotation> cubeRotations,
		PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick,
		boolean collectGeometry, @Nullable Vec3 cameraPosition, boolean renderSourceGeometry,
		@Nullable Boolean hurtOverlay, java.util.Set<Integer> saddles, java.util.Set<Integer> chests) {
		return renderEquipped(preview, cubeCount, presentCubes, cubeOffsets, cubeRotations, poseStack, buffer,
			packedLight, partialTick, collectGeometry, cameraPosition, renderSourceGeometry, hurtOverlay, saddles, chests, null, -1);
	}

	public static SurgicalModelRenderContext.Snapshot renderEquipped(LivingEntity preview, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> cubeOffsets, Map<Integer, SurgicalCubeRotation> cubeRotations,
		PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick,
		boolean collectGeometry, @Nullable Vec3 cameraPosition, boolean renderSourceGeometry,
		@Nullable Boolean hurtOverlay, java.util.Set<Integer> saddles, java.util.Set<Integer> chests,
		@Nullable SlimeBionicEntity owner, int source) {
		if (owner != null) buffer = selfDestructBuffer(buffer, owner.getSelfDestructSwelling(partialTick));
		preparePreview(preview, 0);
		SurgicalCapturedRenderPlan base = SurgicalSourcePlanCache.plan(preview, 0);
		if (owner != null) base = SurgicalEquipmentRenderPlans.traitPlan(preview, owner, source, partialTick, base);
		SurgicalCapturedRenderPlan naked = SurgicalEquipmentRenderPlans.chestPlan(preview, 0, false, false, SurgicalEquipmentRenderPlans.saddlePlan(preview, 0, false, base));
		var snapshot = naked.render(poseStack, buffer, packedLight, cubeCount, presentCubes, cubeOffsets,
			cubeRotations, collectGeometry, cameraPosition, renderSourceGeometry, hurtOverlay);
		if (owner != null && !owner.getMainHandItem().isEmpty()) {
			var hand = owner.getBionicEquipment().mainHandCarrier();
			if (hand != null && hand.source() == source && presentCubes.get(hand.cube()))
				naked.renderMainHandItem(owner, hand.cube(), poseStack, buffer, packedLight,
					cubeOffsets.get(hand.cube()), cubeRotations.get(hand.cube()));
		}
		// Any installed saddle enables each donor's own bridle, including head-only donors.
		boolean bridled = owner == null ? !saddles.isEmpty() : owner.isSaddled();
		if (!bridled && saddles.isEmpty() && chests.isEmpty()) return snapshot;
		SurgicalCapturedRenderPlan withSaddle = !bridled && saddles.isEmpty() ? naked
			: SurgicalEquipmentRenderPlans.chestPlan(preview, 0, true, false, SurgicalEquipmentRenderPlans.saddlePlan(preview, 0, true, base));
		if (bridled)
			naked.renderHeadEquipment(withSaddle, cubeCount, presentCubes, cubeOffsets, cubeRotations,
				poseStack, buffer, packedLight, hurtOverlay);
		MimicProfile profile = PREVIEW_PROFILES.get(preview);
		SourceRidingSeat seat = profile == null ? null : SurgicalEquipmentRenderPlans.nativeRidingSeat(profile);
		if (seat == null) return snapshot;
		SurgicalCapturedRenderPlan withChest = chests.isEmpty() ? naked : SurgicalEquipmentRenderPlans.chestPlan(preview, 0, false, true, base);
		for (int carrier : saddles) if (presentCubes.get(carrier))
			naked.renderEquipment(withSaddle, carrier, seat.first(), poseStack, buffer, packedLight,
				cubeOffsets.get(carrier), cubeRotations.get(carrier), hurtOverlay);
		for (int carrier : chests) if (presentCubes.get(carrier))
			naked.renderEquipment(withChest, carrier, seat.first(), poseStack, buffer, packedLight,
				cubeOffsets.get(carrier), cubeRotations.get(carrier), hurtOverlay);
		return snapshot;
	}

	/** Accessory envelopes use exactly the same native capture and rebase as live rendering. */
	public static java.util.List<Vec3> equipmentCorners(MimicProfile profile, int carrier, boolean chest) {
		return SurgicalEquipmentRenderPlans.equipmentCorners(profile, carrier, chest);
	}

	/** Bake against a retained body cube, independently of optional saddle-layer cube ids. */
	@Nullable
	public record SourceRidingSeat(Vec3 first, Vec3 second) {}

	public static SourceRidingSeat ridingSeat(MimicProfile profile, int carrier) {
		return SurgicalEquipmentRenderPlans.ridingSeat(profile, carrier);
	}

	/** Renders exactly one captured cuboid without duplicating model-wide non-cuboid extras. */
	public static SurgicalModelRenderContext.Snapshot renderSingleCube(MimicProfile profile,
		int cube, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		LivingEntity preview = preview(profile);
		if (preview == null)
			return new SurgicalModelRenderContext.Snapshot(0, java.util.List.of());
		preparePreview(preview, 0.0f);
		return SurgicalSourcePlanCache.plan(preview, 0.0f).renderSingleCube(poseStack, buffer, packedLight, cube);
	}

	/** Returns the neutral-pose source geometry used to align one released cuboid to its entity. */
	@Nullable
	public static SurgicalModelRenderContext.CubeGeometry singleCubeGeometry(
		MimicProfile profile, int cube) {
		LivingEntity preview = preview(profile);
		if (preview == null)
			return null;
		preparePreview(preview, 0.0f);
		return SurgicalSourcePlanCache.plan(preview, 0.0f).singleCubeGeometry(cube);
	}

	/**
	 * Every source capture happens in this fixed animation state, so a captured creature keeps one pose
	 * however often its plan is rebuilt - after LRU eviction, a level change, a resource reload or taking
	 * the part off the table and putting it back.
	 * <p>
	 * Renderers commonly derive a per-creature animation phase from state a preview does not keep stable:
	 * the phantom's wing flap reads {@code getId() * 3}, the jeb_ sheep's wool cycle reads {@code getId()},
	 * and mods seed idle motion from the entity's own random. A preview's real id comes from the global
	 * entity counter and differs every time the preview is rebuilt, so it is swapped for a fixed id only
	 * for the duration of the capture. It cannot stay swapped: {@link net.minecraft.world.entity.Entity}
	 * equality and hashing are by id, and the weak maps here are keyed by preview. 0 is never handed to a
	 * real entity (the counter pre-increments), so the swap cannot alias a live entity either.
	 * <p>
	 * Only animation inputs are pinned; appearance NBT such as wool colour or variants is left untouched.
	 */
	static SurgicalCapturedRenderPlan.CapturedInput captureInputInCanonicalPose(
		EntityRenderer<LivingEntity> renderer, LivingEntity preview, float yaw) {
		return captureInputAtAge(renderer, preview, yaw, SurgicalCapturePoseOverrides::captureAgeInTicks);
	}

	private static SurgicalCapturedRenderPlan.CapturedInput captureInputAtAge(
		EntityRenderer<LivingEntity> renderer, LivingEntity preview, float yaw,
		ToDoubleFunction<LivingEntity> animationAge) {
		int id = preview.getId();
		int tickCount = preview.tickCount;
		preview.setId(CAPTURE_ENTITY_ID);
		try {
			preview.getRandom().setSeed(captureRandomSeed(preview));
			// Resolve the canonical age only after pinning the id: phantom flap offsets depend on it.
			float ageInTicks = (float) animationAge.applyAsDouble(preview);
			// Renderers see the age as tickCount + partialTick, so a fractional age is split across both.
			int wholeTicks = Mth.floor(ageInTicks);
			preview.tickCount = wholeTicks;
			return SurgicalCapturedRenderPlan.captureInput(renderer, preview, yaw, ageInTicks - wholeTicks, true);
		} finally {
			preview.setId(id);
			preview.tickCount = tickCount;
		}
	}

	static SurgicalCapturedRenderPlan captureInCanonicalPose(EntityRenderer<LivingEntity> renderer,
		LivingEntity preview, float yaw) {
		return SurgicalCapturedRenderPlan.build(captureInputInCanonicalPose(renderer, preview, yaw));
	}

	public static float captureAnimationAge(LivingEntity preview) {
		return SurgicalCapturePoseOverrides.captureAgeInTicks(preview);
	}

	private static long captureRandomSeed(LivingEntity preview) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(preview.getType()).toString().hashCode();
	}

	static RenderPlanState renderPlanState(LivingEntity preview, float yaw) {
		return SurgicalSourcePlanCache.renderPlanState(preview, yaw);
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
		return SurgicalSourcePlanCache.captureGeometryDeferred(preview, cubeCount, presentCubes, poseStack, yaw, partialTick, cameraPosition);
	}

	/** Geometry only: no vertex replay, no synchronous fallback while a plan is pending. */
	@Nullable
	public static SurgicalModelRenderContext.Snapshot geometryDeferred(LivingEntity preview, int cubeCount,
		BitSet presentCubes, Map<Integer, Vec3> offsets, Map<Integer, SurgicalCubeRotation> rotations,
		PoseStack poseStack, float yaw, @Nullable Vec3 cameraPosition) {
		return SurgicalSourcePlanCache.geometryDeferred(preview, cubeCount, presentCubes, offsets, rotations, poseStack, yaw, cameraPosition);
	}

	/** Publishes completed worker results on the client thread and frees pending-capacity promptly. */
	static void collectCompletedPlans() {
		SurgicalSourcePlanCache.collectCompletedPlans();
	}

	/** Captures unoffset source geometry without submitting the temporary model to the visible buffer. */
	public static SurgicalModelRenderContext.Snapshot captureGeometry(LivingEntity preview, int cubeCount,
		BitSet presentCubes, PoseStack poseStack, int packedLight, float yaw, float partialTick,
		@Nullable Vec3 cameraPosition, boolean renderSourceGeometry) {
		long started = SurgicalProfiler.begin();
		preparePreview(preview, yaw);
		SurgicalModelRenderContext.Snapshot snapshot = SurgicalSourcePlanCache.plan(preview, yaw)
			.snapshot(poseStack, cubeCount, presentCubes, Map.of(), Map.of(), cameraPosition);
		SurgicalProfiler.end("captureGeometry", started);
		return snapshot;
	}

	static void preparePreview(LivingEntity preview, float yaw) {
		preview.setYRot(yaw);
		preview.yRotO = yaw;
		preview.yBodyRot = yaw;
		preview.yBodyRotO = yaw;
		preview.yHeadRot = yaw;
		preview.yHeadRotO = yaw;
	}

	@SuppressWarnings("unchecked")
	static EntityRenderer<LivingEntity> renderer(LivingEntity preview) {
		EntityRenderer<LivingEntity> renderer = (EntityRenderer<LivingEntity>) Minecraft.getInstance()
			.getEntityRenderDispatcher().getRenderer(preview);
		return renderer;
	}

	public static void clear() {
		PREVIEWS.clear();
		PREVIEW_PROFILES.clear();
		SurgicalSourcePlanCache.RENDER_PLANS.clear();
		resourceGeneration++;
		SurgicalSourcePlanCache.PENDING_RENDER_PLANS.values().forEach(pending -> pending.future().cancel(false));
		SurgicalSourcePlanCache.PENDING_RENDER_PLANS.clear();
		SurgicalSourcePlanCache.FALLBACK_RENDER_PLANS.clear();
		SurgicalEquipmentRenderPlans.SADDLE_RENDER_PLANS.clear();
		SurgicalEquipmentRenderPlans.CHEST_RENDER_PLANS.clear();
		SurgicalEquipmentRenderPlans.TRAIT_RENDER_PLANS.clear();
		SurgicalNativeAnimations.clear();
		SurgicalEquipmentRenderPlans.RIDING_SEATS.clear();
		DONOR_GEOMETRIES.clear();
		SurgicalCapturedRenderPlan.clearResources();
	}

	/**
	 * Deliberately does not hold its profile: the profile is this entry's key in a weak map, so a
	 * reference from the value back to it would pin every entry forever.
	 */
	private record CachedPreview(ClientLevel level, LivingEntity entity) {}

	/** CreeperRenderer's white flash, applied at draw time across every retained donor and its tack. */
	static MultiBufferSource selfDestructBuffer(MultiBufferSource buffer, float swelling) {
		if ((int) (swelling * 10.0f) % 2 == 0) return buffer;
		int whiteU = net.minecraft.client.renderer.texture.OverlayTexture.u(
			net.minecraft.util.Mth.clamp(swelling, 0.5f, 1.0f));
		return renderType -> new WhiteOverlayVertexConsumer(buffer.getBuffer(renderType), whiteU);
	}

	private record WhiteOverlayVertexConsumer(VertexConsumer delegate, int whiteU) implements VertexConsumer {
		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			delegate.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			delegate.setColor(red, green, blue, alpha);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			delegate.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			// Keep the body's red hurt/death row, as LivingEntityRenderer.getOverlayCoords does.
			delegate.setUv1(Math.max(u, whiteU), v);
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

	static MultiBufferSource alphaBuffer(MultiBufferSource buffer, float alpha) {
		float clamped = Math.max(0.0f, Math.min(1.0f, alpha));
		return clamped < 1.0f ? new AlphaBufferSource(buffer, clamped) : buffer;
	}

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
