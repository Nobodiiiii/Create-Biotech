package com.nobodiiiii.createbiotech.content.bouncing;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBMobEffects;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class BouncingFallHandler {
	private static final Map<LivingEntity, Double> PENDING_BOUNCES =
		Collections.synchronizedMap(new WeakHashMap<>());

	private BouncingFallHandler() {}

	@SubscribeEvent
	public static void onLivingFall(LivingFallEvent event) {
		LivingEntity entity = event.getEntity();
		if (!entity.hasEffect(CBMobEffects.BOUNCING) || entity.isSuppressingBounce())
			return;

		// SlimeBlock prevents fall damage unless the entity is suppressing its bounce.
		event.setDamageMultiplier(0.0F);

		double fallingSpeed = entity.getDeltaMovement().y;
		if (fallingSpeed < 0.0D)
			PENDING_BOUNCES.put(entity, fallingSpeed);
	}

	@SubscribeEvent
	public static void onLivingTick(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof LivingEntity entity) || PENDING_BOUNCES.isEmpty())
			return;

		Double fallingSpeed = PENDING_BOUNCES.remove(entity);
		if (fallingSpeed == null
			|| !entity.isAlive()
			|| entity.isSuppressingBounce()
			|| !entity.hasEffect(CBMobEffects.BOUNCING))
			return;

		// Block#updateEntityAfterFallOn has now run. Apply the same full-strength vertical
		// reversal that SlimeBlock uses for living entities, unless the landing block has
		// already supplied an upward bounce of its own.
		Vec3 movement = entity.getDeltaMovement();
		if (movement.y > 0.0D)
			return;

		entity.setDeltaMovement(movement.x, -fallingSpeed, movement.z);
		entity.hasImpulse = true;
	}
}
