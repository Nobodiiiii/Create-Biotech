package com.nobodiiiii.createbiotech.content.fluid;

import java.util.Map;
import java.util.WeakHashMap;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBFluids;

import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class LiquidLivingSlimeInteractionHandler {

	private static final double LANDING_VERTICAL_SPEED_THRESHOLD = -0.16D;

	/**
	 * Descent speed of entities that were falling fast enough to land, measured on the last tick
	 * they were still outside the fluid. This is per-tick scratch with no reason to survive a save,
	 * so it deliberately does not live in persistent data: this handler sees every living entity in
	 * the level on every tick, and touching {@code getPersistentData()} there allocates a tag for
	 * every entity in the world and grows it on disk forever. Keys are weak so an entity removed
	 * mid-fall drops out on its own.
	 */
	private static final Map<LivingEntity, Double> DESCENDING = new WeakHashMap<>();

	private LiquidLivingSlimeInteractionHandler() {}

	@SubscribeEvent
	public static void onLivingTick(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide)
			return;

		// getFluidTypeHeight reads NeoForge's per-tick fluid cache, making it the cheapest available
		// discriminator. Entities that are neither in the fluid nor descending fast enough to ever
		// produce a landing - nearly all of them - leave here without touching the map.
		boolean touchingLiquidLivingSlime =
			entity.getFluidTypeHeight(CBFluids.LIQUID_LIVING_SLIME_TYPE.get()) > 0.0D;
		double verticalSpeed = entity.getDeltaMovement().y;

		if (touchingLiquidLivingSlime) {
			// An entry exists only if the previous tick found this entity outside the fluid and
			// descending past the threshold, which is exactly the original entry condition.
			Double descentSpeed = DESCENDING.isEmpty() ? null : DESCENDING.remove(entity);
			if (descentSpeed != null)
				playLandingSound(entity, descentSpeed);
			return;
		}

		if (verticalSpeed < LANDING_VERTICAL_SPEED_THRESHOLD)
			DESCENDING.put(entity, verticalSpeed);
		else if (!DESCENDING.isEmpty())
			DESCENDING.remove(entity);
	}

	private static void playLandingSound(LivingEntity entity, double previousVerticalSpeed) {
		Vec3 position = entity.position();
		float volume = (float) Mth.clamp(-previousVerticalSpeed * 0.75D, 0.35D, 1.0D);
		float pitch = 1.0F + (entity.level().random.nextFloat() - entity.level().random.nextFloat()) * 0.1F;
		entity.level().playSound(null, position.x, position.y, position.z, SoundEvents.SLIME_BLOCK_FALL,
			entity.getSoundSource(), volume, pitch);
	}

}
