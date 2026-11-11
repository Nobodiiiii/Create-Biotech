package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToDoubleFunction;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalProfiler;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class SurgicalSourceModelRenderer {
	private static final int MAX_RENDER_PLANS = 512;
	private static final int MAX_PENDING_RENDER_PLANS = 64;
	private static final int CAPTURE_ENTITY_ID = 0;
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
	private static final Map<MimicProfile, SourceRidingSeat> RIDING_SEATS = new WeakHashMap<>();
	private static final Map<SaddlePlanKey, SurgicalCapturedRenderPlan> SADDLE_RENDER_PLANS =
		new LinkedHashMap<>(64, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<SaddlePlanKey, SurgicalCapturedRenderPlan> eldest) {
				return size() > MAX_RENDER_PLANS;
			}
		};
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
		SurgicalCapturedRenderPlan plan = plan(preview, yaw);
		if (saddled != null)
			plan = saddlePlan(preview, yaw, saddled, plan);
		if (chested != null)
			plan = chestPlan(preview, yaw, saddled, chested, plan);
		float clampedAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
		MultiBufferSource renderBuffer = clampedAlpha < 1.0f
			? new AlphaBufferSource(buffer, clampedAlpha) : buffer;
		return plan.render(poseStack, renderBuffer, packedLight, cubeCount, presentCubes, cubeOffsets, cubeRotations,
			collectGeometry, cameraPosition, renderSourceGeometry, hurtOverlay);
	}

	/** A per-body mask; shared donor previews and cached plans retain their original appearance. */
	public static BitSet visibleCubes(LivingEntity preview, BitSet presentCubes, boolean sheared) {
		if (!sheared)
			return presentCubes;
		preparePreview(preview, 0);
		return plan(preview, 0).withoutWool(presentCubes);
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
		preparePreview(preview, 0);
		SurgicalCapturedRenderPlan base = plan(preview, 0);
		if (owner != null) base = traitPlan(preview, owner, source, base);
		SurgicalCapturedRenderPlan naked = chestPlan(preview, 0, false, false, saddlePlan(preview, 0, false, base));
		var snapshot = naked.render(poseStack, buffer, packedLight, cubeCount, presentCubes, cubeOffsets,
			cubeRotations, collectGeometry, cameraPosition, renderSourceGeometry, hurtOverlay);
		// Any installed saddle enables each donor's own bridle, including head-only donors.
		boolean bridled = owner == null ? !saddles.isEmpty() : owner.isSaddled();
		if (!bridled && saddles.isEmpty() && chests.isEmpty()) return snapshot;
		SurgicalCapturedRenderPlan withSaddle = !bridled && saddles.isEmpty() ? naked
			: chestPlan(preview, 0, true, false, saddlePlan(preview, 0, true, base));
		if (bridled)
			naked.renderHeadEquipment(withSaddle, cubeCount, presentCubes, cubeOffsets, cubeRotations,
				poseStack, buffer, packedLight, hurtOverlay);
		MimicProfile profile = PREVIEW_PROFILES.get(preview);
		SourceRidingSeat seat = profile == null ? null : nativeRidingSeat(profile);
		if (seat == null) return snapshot;
		SurgicalCapturedRenderPlan withChest = chests.isEmpty() ? naked : chestPlan(preview, 0, false, true, base);
		for (int carrier : saddles) if (presentCubes.get(carrier))
			naked.renderEquipment(withSaddle, carrier, seat.first(), poseStack, buffer, packedLight,
				cubeOffsets.get(carrier), cubeRotations.get(carrier), hurtOverlay);
		for (int carrier : chests) if (presentCubes.get(carrier))
			naked.renderEquipment(withChest, carrier, seat.first(), poseStack, buffer, packedLight,
				cubeOffsets.get(carrier), cubeRotations.get(carrier), hurtOverlay);
		return snapshot;
	}

	private record TraitPlanKey(RenderPlanKey source, int state, int ticks) {}
	private static final Map<TraitPlanKey, SurgicalCapturedRenderPlan> TRAIT_RENDER_PLANS =
		new LinkedHashMap<>(64, 0.75f, true) {
			@Override protected boolean removeEldestEntry(Map.Entry<TraitPlanKey, SurgicalCapturedRenderPlan> entry) {
				return size() > MAX_RENDER_PLANS;
			}
		};

	/** Sample native appearance on separate previews, preserving each body's surgical reference ids. */
	private static SurgicalCapturedRenderPlan traitPlan(LivingEntity preview, SlimeBionicEntity owner,
		int source, SurgicalCapturedRenderPlan base) {
		MimicProfile profile = PREVIEW_PROFILES.get(preview);
		if (profile == null) return base;
		boolean shell = owner.getBionicTraits().members(BionicTrait.SHELL_DEFENSE).stream().anyMatch(member -> member.source() == source);
		int state, ticks = 0;
		if (preview instanceof net.minecraft.world.entity.animal.Bee) state = owner.hasBionicPollen() ? 2 : 1;
		else if (shell && preview instanceof net.minecraft.world.entity.animal.armadillo.Armadillo) {
			var phase = owner.getShellState();
			if (phase == net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.IDLE) return base;
			state = 10 + phase.ordinal();
			ticks = phase == net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.SCARED
				? 0 : Math.min(owner.getShellStateTicks(), phase.animationDuration());
		} else if (shell && preview instanceof net.minecraft.world.entity.monster.Shulker) {
			var phase = owner.getShellState();
			float closure = switch (phase) {
			case IDLE -> 0;
			case SCARED -> 1;
			case ROLLING -> Math.min(1, owner.getShellStateTicks() / (float) Math.max(1, phase.animationDuration()));
			case UNROLLING -> 1 - Math.min(1, owner.getShellStateTicks() / (float) Math.max(1, phase.animationDuration()));
			};
			state = 20; ticks = Math.round(closure * 10);
		} else if (preview instanceof net.minecraft.world.entity.monster.warden.Warden && owner.getSpecialAction() == 1
			&& owner.getBionicTraits().members(BionicTrait.SONIC_BOOM).stream().anyMatch(member -> member.source() == source)) {
			state = 30; ticks = Math.min(60, owner.getSpecialActionTicks());
		} else return base;
		var renderer = renderer(preview);
		TraitPlanKey key = new TraitPlanKey(new RenderPlanKey(profile, renderer, 0), state, ticks);
		SurgicalCapturedRenderPlan cached = TRAIT_RENDER_PLANS.get(key);
		if (cached != null) return cached;
		LivingEntity animated = profile.createPreviewEntity(preview.level());
		if (animated == null) return base;
		if (animated instanceof net.minecraft.world.entity.animal.Bee bee) {
			CompoundTag data = bee.saveWithoutId(new CompoundTag());
			data.putBoolean("HasNectar", state == 2);
			bee.load(data);
		}
		if (state >= 10 && state < 20 && animated instanceof net.minecraft.world.entity.animal.armadillo.Armadillo armadillo) {
			var phase = net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.values()[state - 10];
			armadillo.switchToState(phase.shouldHideInShell(ticks)
				? net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.SCARED
				: net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.IDLE);
			armadillo.rollUpAnimationState.stop(); armadillo.rollOutAnimationState.stop(); armadillo.peekAnimationState.stop();
			if (phase == net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.ROLLING) armadillo.rollUpAnimationState.start(0);
			if (phase == net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.UNROLLING) armadillo.rollOutAnimationState.start(0);
		}
		if (state == 20) {
			float peek = 1 - ticks / 10.0f;
			var shulker = new net.minecraft.world.entity.monster.Shulker(EntityType.SHULKER, preview.level()) {
				@Override public float getClientPeekAmount(float partialTick) { return peek; }
			};
			shulker.load(animated.saveWithoutId(new CompoundTag()));
			animated = shulker;
		}
		if (state == 30 && animated instanceof net.minecraft.world.entity.monster.warden.Warden warden)
			warden.sonicBoomAnimationState.start(0);
		preparePreview(animated, 0);
		cached = base.withTraitAppearance(SurgicalCapturedRenderPlan.build(captureInputAtAge(renderer, animated, 0, ticks)));
		TRAIT_RENDER_PLANS.put(key, cached);
		return cached;
	}

	/** Accessory envelopes use exactly the same native capture and rebase as live rendering. */
	public static java.util.List<Vec3> equipmentCorners(MimicProfile profile, int carrier, boolean chest) {
		LivingEntity preview = preview(profile);
		SourceRidingSeat seat = nativeRidingSeat(profile);
		if (preview == null || seat == null) return java.util.List.of();
		var base = plan(preview, 0);
		var naked = chestPlan(preview, 0, false, false, saddlePlan(preview, 0, false, base));
		var equipped = chest ? chestPlan(preview, 0, false, true, base)
			: chestPlan(preview, 0, true, false, saddlePlan(preview, 0, true, base));
		return naked.equipmentCorners(equipped, carrier, seat.first());
	}

	private static final Map<EquipmentPlanKey, SurgicalCapturedRenderPlan> CHEST_RENDER_PLANS =
		new LinkedHashMap<>(64, 0.75f, true) {
			@Override protected boolean removeEldestEntry(Map.Entry<EquipmentPlanKey, SurgicalCapturedRenderPlan> entry) {
				return size() > MAX_RENDER_PLANS;
			}
		};
	private record EquipmentPlanKey(RenderPlanKey source, boolean saddled, boolean chested) {}

	/** Capture the donor's native boxes on the same retained geometry used for live saddles. */
	private static SurgicalCapturedRenderPlan chestPlan(LivingEntity preview, float yaw,
		@Nullable Boolean saddled, boolean chested, SurgicalCapturedRenderPlan base) {
		if (!(preview instanceof net.minecraft.world.entity.animal.horse.AbstractChestedHorse horse)) return base;
		boolean saddle = saddled == null ? horse.isSaddled() : saddled;
		MimicProfile profile = PREVIEW_PROFILES.get(preview);
		if (profile == null) return base;
		var renderer = renderer(preview);
		var key = new EquipmentPlanKey(new RenderPlanKey(profile, renderer, Float.floatToIntBits(yaw)), saddle, chested);
		return CHEST_RENDER_PLANS.computeIfAbsent(key, ignored -> {
			var equipped = profile.createPreviewEntity(preview.level());
			if (!(equipped instanceof net.minecraft.world.entity.animal.horse.AbstractChestedHorse mount)) return base;
			mount.setChest(chested);
			mount.setFlag(4, saddle);
			preparePreview(mount, yaw);
			return base.withAppearance(captureInCanonicalPose(renderer, mount, yaw));
		});
	}

	private static SurgicalCapturedRenderPlan saddlePlan(LivingEntity preview, float yaw,
		boolean saddled, SurgicalCapturedRenderPlan base) {
		if (!(preview instanceof Saddleable saddleable) || saddleable.isSaddled() == saddled)
			return base;
		MimicProfile profile = PREVIEW_PROFILES.get(preview);
		if (profile == null)
			return base;
		EntityRenderer<LivingEntity> renderer = renderer(preview);
		SaddlePlanKey key = new SaddlePlanKey(new RenderPlanKey(profile, renderer,
			Float.floatToIntBits(yaw)), saddled);
		SurgicalCapturedRenderPlan cached = SADDLE_RENDER_PLANS.get(key);
		if (cached != null)
			return cached;

		// The ordinary preview is shared with tables, fragments and other bodies. Capture equipment
		// on a separate entity so one saddled creature cannot change any of their cached appearances.
		LivingEntity equipped = profile.createPreviewEntity(preview.level());
		if (!(equipped instanceof Saddleable equippedSaddle))
			return base;
		if (saddled) {
			equippedSaddle.equipSaddle(new ItemStack(Items.SADDLE), null);
		} else {
			CompoundTag data = equipped.saveWithoutId(new CompoundTag());
			data.putBoolean("Saddle", false);
			data.remove("SaddleItem");
			equipped.load(data);
		}
		// AbstractHorse only synchronizes inventory -> saddle flags on the server. These unspawned
		// client previews need the same flag explicitly (also covers camels, donkeys and mules).
		if (equipped instanceof AbstractHorse horse)
			horse.setFlag(4, saddled);
		preparePreview(equipped, yaw);
		SurgicalCapturedRenderPlan appearance = captureInCanonicalPose(renderer, equipped, yaw);
		cached = base.withAppearance(appearance);
		SADDLE_RENDER_PLANS.put(key, cached);
		return cached;
	}

	/** Native front/back passenger anchors, before projection onto a retained carrier cube. */
	@Nullable
	private static SourceRidingSeat nativeRidingSeat(MimicProfile profile) {
		SourceRidingSeat cached = RIDING_SEATS.get(profile);
		if (cached != null)
			return cached;
		LivingEntity preview = preview(profile);
		if (!(preview instanceof Saddleable))
			return null;
		preparePreview(preview, 0.0f);
		LivingEntity mount = profile.createPreviewEntity(preview.level());
		var first = EntityType.ARMOR_STAND.create(preview.level());
		var second = EntityType.ARMOR_STAND.create(preview.level());
		if (mount == null || first == null || second == null)
			return null;
		// These marker passengers and their mount are never added to the world. Mounting two is
		// needed for the camel's native front/back offsets; their non-animal type matches players.
		try {
			first.startRiding(mount, true);
			if (mount instanceof Camel)
				second.startRiding(mount, true);
			Vec3 front = mount.getPassengerRidingPosition(first).subtract(mount.position());
			Vec3 rear = mount instanceof Camel
				? mount.getPassengerRidingPosition(second).subtract(mount.position()) : front;
			cached = new SourceRidingSeat(front, rear);
			RIDING_SEATS.put(profile, cached);
			return cached;
		} finally {
			mount.ejectPassengers();
		}
	}

	public record SourceRidingSeat(Vec3 first, Vec3 second) {}

	/** Bake against a retained body cube, independently of optional saddle-layer cube ids. */
	@Nullable
	public static SourceRidingSeat ridingSeat(MimicProfile profile, int carrier) {
		SourceRidingSeat nativeSeat = nativeRidingSeat(profile);
		if (nativeSeat == null) return null;
		LivingEntity preview = preview(profile);
		SurgicalCapturedRenderPlan base = plan(preview, 0.0f);
		if (base.singleCubeGeometry(carrier) == null) return null;
		Vec3 first = base.equipmentSeat(carrier, nativeSeat.first());
		return new SourceRidingSeat(first, first.add(nativeSeat.second().subtract(nativeSeat.first())));
	}

	/** Renders exactly one captured cuboid without duplicating model-wide non-cuboid extras. */
	public static SurgicalModelRenderContext.Snapshot renderSingleCube(MimicProfile profile,
		int cube, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		LivingEntity preview = preview(profile);
		if (preview == null)
			return new SurgicalModelRenderContext.Snapshot(0, java.util.List.of());
		preparePreview(preview, 0.0f);
		return plan(preview, 0.0f).renderSingleCube(poseStack, buffer, packedLight, cube);
	}

	/** Returns the neutral-pose source geometry used to align one released cuboid to its entity. */
	@Nullable
	public static SurgicalModelRenderContext.CubeGeometry singleCubeGeometry(
		MimicProfile profile, int cube) {
		LivingEntity preview = preview(profile);
		if (preview == null)
			return null;
		preparePreview(preview, 0.0f);
		return plan(preview, 0.0f).singleCubeGeometry(cube);
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
	private static SurgicalCapturedRenderPlan.CapturedInput captureInputInCanonicalPose(
		EntityRenderer<LivingEntity> renderer, LivingEntity preview, float yaw) {
		return captureInputAtAge(renderer, preview, yaw, SurgicalCapturePoseOverrides::captureAgeInTicks);
	}

	private static SurgicalCapturedRenderPlan.CapturedInput captureInputAtAge(
		EntityRenderer<LivingEntity> renderer, LivingEntity preview, float yaw, float ageInTicks) {
		return captureInputAtAge(renderer, preview, yaw, ignored -> ageInTicks);
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

	private static SurgicalCapturedRenderPlan captureInCanonicalPose(EntityRenderer<LivingEntity> renderer,
		LivingEntity preview, float yaw) {
		return SurgicalCapturedRenderPlan.build(captureInputInCanonicalPose(renderer, preview, yaw));
	}

	public static float captureAnimationAge(LivingEntity preview) {
		return SurgicalCapturePoseOverrides.captureAgeInTicks(preview);
	}

	private static long captureRandomSeed(LivingEntity preview) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(preview.getType()).toString().hashCode();
	}

	/**
	 * Captured plans are a fixed pose, not a frame of a running animation, so callers' frame partial
	 * ticks are deliberately not forwarded here - see {@link #captureInputInCanonicalPose}.
	 */
	private static SurgicalCapturedRenderPlan plan(LivingEntity preview, float yaw) {
		EntityRenderer<LivingEntity> renderer = renderer(preview);
		MimicProfile profile = PREVIEW_PROFILES.get(preview);
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
				plan = captureInCanonicalPose(renderer, preview, yaw);
				SurgicalProfiler.end("capture(plan)", started);
				RENDER_PLANS.put(key, plan);
			}
			return plan;
		}

		CachedRenderPlan cached = FALLBACK_RENDER_PLANS.get(preview);
		if (cached == null || cached.renderer != renderer
			|| Float.floatToIntBits(cached.yaw) != Float.floatToIntBits(yaw)) {
			SurgicalCapturedRenderPlan plan = captureInCanonicalPose(renderer, preview, yaw);
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
		// get(), not containsKey(): RENDER_PLANS is an access-ordered LRU and only get() marks an entry
		// as recently used. Probing with containsKey() left a plan looking stale to the eviction policy
		// however often it was asked about, so the very plan a visible subject checks every frame could
		// still be evicted - and plan() would then rebuild it synchronously on the render thread.
		if (RENDER_PLANS.get(key) != null)
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
					captureInputInCanonicalPose(renderer, preview, yaw);
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
		SurgicalModelRenderContext.Snapshot snapshot = plan(preview, yaw)
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
		SADDLE_RENDER_PLANS.clear();
		CHEST_RENDER_PLANS.clear();
		TRAIT_RENDER_PLANS.clear();
		RIDING_SEATS.clear();
		SurgicalCapturedRenderPlan.clearResources();
	}

	/**
	 * Deliberately does not hold its profile: the profile is this entry's key in a weak map, so a
	 * reference from the value back to it would pin every entry forever.
	 */
	private record CachedPreview(ClientLevel level, LivingEntity entity) {}

	private record RenderPlanKey(MimicProfile profile, EntityRenderer<LivingEntity> renderer, int yawBits) {}

	private record SaddlePlanKey(RenderPlanKey source, boolean saddled) {}

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
