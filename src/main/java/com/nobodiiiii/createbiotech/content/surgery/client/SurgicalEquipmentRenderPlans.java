package com.nobodiiiii.createbiotech.content.surgery.client;

import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalSourceModelRenderer.SourceRidingSeat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Internal SurgicalEquipmentRenderPlans implementation; public entry points remain on SurgicalSourceModelRenderer. */
final class SurgicalEquipmentRenderPlans {
	private SurgicalEquipmentRenderPlans() {}

	static final Map<MimicProfile, SourceRidingSeat> RIDING_SEATS = new WeakHashMap<>();
	static final Map<SaddlePlanKey, SurgicalCapturedRenderPlan> SADDLE_RENDER_PLANS =
		new LinkedHashMap<>(64, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<SaddlePlanKey, SurgicalCapturedRenderPlan> eldest) {
				return size() > SurgicalSourceModelRenderer.MAX_RENDER_PLANS;
			}
		};

	record TraitPlanKey(SurgicalSourcePlanCache.RenderPlanKey source, int state) {}
	static final Map<TraitPlanKey, SurgicalCapturedRenderPlan> TRAIT_RENDER_PLANS =
		new LinkedHashMap<>(64, 0.75f, true) {
			@Override protected boolean removeEldestEntry(Map.Entry<TraitPlanKey, SurgicalCapturedRenderPlan> entry) {
				return size() > SurgicalSourceModelRenderer.MAX_RENDER_PLANS;
			}
		};

	/** Sample native appearance on separate previews, preserving each body's surgical reference ids. */
	static SurgicalCapturedRenderPlan traitPlan(LivingEntity preview, SlimeBionicEntity owner,
		int source, float partialTick, SurgicalCapturedRenderPlan base) {
		MimicProfile profile = SurgicalSourceModelRenderer.PREVIEW_PROFILES.get(preview);
		if (profile == null) return base;
		if (preview instanceof net.minecraft.world.entity.animal.armadillo.Armadillo
			|| preview instanceof net.minecraft.world.entity.monster.Shulker
			|| preview instanceof net.minecraft.world.entity.monster.Evoker
			|| preview instanceof net.minecraft.world.entity.monster.Witch
			|| preview instanceof net.minecraft.world.entity.monster.warden.Warden)
			return SurgicalNativeAnimations.plan(owner, source, partialTick, base);
		int state;
		if (preview instanceof net.minecraft.world.entity.animal.Bee) state = owner.hasBionicPollen() ? 2 : 1;
		else return base;
		var renderer = SurgicalSourceModelRenderer.renderer(preview);
		TraitPlanKey key = new TraitPlanKey(new SurgicalSourcePlanCache.RenderPlanKey(profile, renderer, 0), state);
		SurgicalCapturedRenderPlan cached = TRAIT_RENDER_PLANS.get(key);
		if (cached != null) return cached;
		LivingEntity animated = profile.createPreviewEntity(preview.level());
		if (animated == null) return base;
		if (animated instanceof net.minecraft.world.entity.animal.Bee bee) {
			CompoundTag data = bee.saveWithoutId(new CompoundTag());
			data.putBoolean("HasNectar", state == 2);
			bee.load(data);
		}
		SurgicalSourceModelRenderer.preparePreview(animated, 0);
		cached = base.withTraitAppearance(SurgicalSourceModelRenderer.captureInCanonicalPose(renderer, animated, 0));
		TRAIT_RENDER_PLANS.put(key, cached);
		return cached;
	}

	/** Accessory envelopes use exactly the same native capture and rebase as live rendering. */
	static java.util.List<Vec3> equipmentCorners(MimicProfile profile, int carrier, boolean chest) {
		LivingEntity preview = SurgicalSourceModelRenderer.preview(profile);
		SourceRidingSeat seat = nativeRidingSeat(profile);
		if (preview == null || seat == null) return java.util.List.of();
		var base = SurgicalSourcePlanCache.plan(preview, 0);
		var naked = chestPlan(preview, 0, false, false, saddlePlan(preview, 0, false, base));
		var equipped = chest ? chestPlan(preview, 0, false, true, base)
			: chestPlan(preview, 0, true, false, saddlePlan(preview, 0, true, base));
		return naked.equipmentCorners(equipped, carrier, seat.first());
	}

	static final Map<EquipmentPlanKey, SurgicalCapturedRenderPlan> CHEST_RENDER_PLANS =
		new LinkedHashMap<>(64, 0.75f, true) {
			@Override protected boolean removeEldestEntry(Map.Entry<EquipmentPlanKey, SurgicalCapturedRenderPlan> entry) {
				return size() > SurgicalSourceModelRenderer.MAX_RENDER_PLANS;
			}
		};
	record EquipmentPlanKey(SurgicalSourcePlanCache.RenderPlanKey source, boolean saddled, boolean chested) {}

	/** Capture the donor's native boxes on the same retained geometry used for live saddles. */
	static SurgicalCapturedRenderPlan chestPlan(LivingEntity preview, float yaw,
		@Nullable Boolean saddled, boolean chested, SurgicalCapturedRenderPlan base) {
		if (!(preview instanceof net.minecraft.world.entity.animal.horse.AbstractChestedHorse horse)) return base;
		boolean saddle = saddled == null ? horse.isSaddled() : saddled;
		MimicProfile profile = SurgicalSourceModelRenderer.PREVIEW_PROFILES.get(preview);
		if (profile == null) return base;
		var renderer = SurgicalSourceModelRenderer.renderer(preview);
		var key = new EquipmentPlanKey(new SurgicalSourcePlanCache.RenderPlanKey(profile, renderer, Float.floatToIntBits(yaw)), saddle, chested);
		return CHEST_RENDER_PLANS.computeIfAbsent(key, ignored -> {
			var equipped = profile.createPreviewEntity(preview.level());
			if (!(equipped instanceof net.minecraft.world.entity.animal.horse.AbstractChestedHorse mount)) return base;
			mount.setChest(chested);
			mount.setFlag(4, saddle);
			SurgicalSourceModelRenderer.preparePreview(mount, yaw);
			return base.withAppearance(SurgicalSourceModelRenderer.captureInCanonicalPose(renderer, mount, yaw));
		});
	}

	static SurgicalCapturedRenderPlan saddlePlan(LivingEntity preview, float yaw,
		boolean saddled, SurgicalCapturedRenderPlan base) {
		if (!(preview instanceof Saddleable saddleable) || saddleable.isSaddled() == saddled)
			return base;
		MimicProfile profile = SurgicalSourceModelRenderer.PREVIEW_PROFILES.get(preview);
		if (profile == null)
			return base;
		EntityRenderer<LivingEntity> renderer = SurgicalSourceModelRenderer.renderer(preview);
		SaddlePlanKey key = new SaddlePlanKey(new SurgicalSourcePlanCache.RenderPlanKey(profile, renderer,
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
		SurgicalSourceModelRenderer.preparePreview(equipped, yaw);
		SurgicalCapturedRenderPlan appearance = SurgicalSourceModelRenderer.captureInCanonicalPose(renderer, equipped, yaw);
		cached = base.withAppearance(appearance);
		SADDLE_RENDER_PLANS.put(key, cached);
		return cached;
	}

	/** Native front/back passenger anchors, before projection onto a retained carrier cube. */
	@Nullable
	static SourceRidingSeat nativeRidingSeat(MimicProfile profile) {
		SourceRidingSeat cached = RIDING_SEATS.get(profile);
		if (cached != null)
			return cached;
		LivingEntity preview = SurgicalSourceModelRenderer.preview(profile);
		if (!(preview instanceof Saddleable))
			return null;
		SurgicalSourceModelRenderer.preparePreview(preview, 0.0f);
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


	/** Bake against a retained body cube, independently of optional saddle-layer cube ids. */
	@Nullable
	static SourceRidingSeat ridingSeat(MimicProfile profile, int carrier) {
		SourceRidingSeat nativeSeat = nativeRidingSeat(profile);
		if (nativeSeat == null) return null;
		LivingEntity preview = SurgicalSourceModelRenderer.preview(profile);
		SurgicalCapturedRenderPlan base = SurgicalSourcePlanCache.plan(preview, 0.0f);
		if (base.singleCubeGeometry(carrier) == null) return null;
		Vec3 first = base.equipmentSeat(carrier, nativeSeat.first());
		return new SourceRidingSeat(first, first.add(nativeSeat.second().subtract(nativeSeat.first())));
	}

	record SaddlePlanKey(SurgicalSourcePlanCache.RenderPlanKey source, boolean saddled) {}
}
