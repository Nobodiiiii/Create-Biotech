package com.nobodiiiii.createbiotech.content.bouncing;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBMobEffects;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class BouncingFallHandler {
	private static final double BOUNCE_MULTIPLIER = 1.0D;
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
	}

	@SubscribeEvent
	public static void onLivingTick(EntityTickEvent.Pre event) {
		if (!(event.getEntity() instanceof LivingEntity entity))
			return;

		double fallingSpeed = entity.getDeltaMovement().y;
		if (entity.isAlive()
			&& entity.hasEffect(CBMobEffects.BOUNCING)
			&& !entity.isSuppressingBounce()
			&& fallingSpeed <= -LivingEntity.MIN_MOVEMENT_DISTANCE) {
			PENDING_BOUNCES.put(entity, fallingSpeed);
		} else {
			PENDING_BOUNCES.remove(entity);
		}
	}

	@SubscribeEvent
	public static void onLivingTick(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof LivingEntity entity) || PENDING_BOUNCES.isEmpty())
			return;

		Double fallingSpeed = PENDING_BOUNCES.remove(entity);
		if (fallingSpeed == null
			|| !entity.isAlive()
			|| !entity.onGround()
			|| entity.isSuppressingBounce()
			|| !entity.hasEffect(CBMobEffects.BOUNCING))
			return;

		// Block#updateEntityAfterFallOn has now run. Recreate SlimeBlock's bounce and the
		// remainder of LivingEntity#travel, which normally runs after the block callback.
		Vec3 movement = entity.getDeltaMovement();
		if (movement.y > 0.0D)
			return;

		double bounceSpeed = getPostTravelBounceSpeed(entity, fallingSpeed);
		entity.setDeltaMovement(movement.x, bounceSpeed, movement.z);
		entity.hasImpulse = true;
	}

	private static double getPostTravelBounceSpeed(LivingEntity entity, double fallingSpeed) {
		// SlimeBlock fully reverses the collision speed for living entities.
		double bounceSpeed = -fallingSpeed * BOUNCE_MULTIPLIER;

		// This is the vertical part of vanilla LivingEntity#travel that executes after
		// SlimeBlock#updateEntityAfterFallOn during an ordinary airborne landing.
		MobEffectInstance levitation = entity.getEffect(MobEffects.LEVITATION);
		if (levitation != null) {
			bounceSpeed += (0.05D * (levitation.getAmplifier() + 1) - bounceSpeed) * 0.2D;
		} else {
			double gravity = entity.getGravity();
			if (entity.hasEffect(MobEffects.SLOW_FALLING))
				gravity = Math.min(gravity, 0.01D);
			bounceSpeed -= gravity;
		}

		if (!entity.shouldDiscardFriction())
			bounceSpeed *= entity instanceof FlyingAnimal ? 0.91F : 0.98F;

		// LivingEntity#aiStep removes motion below this exact vanilla threshold at the
		// beginning of the next tick. Applying it here is behaviorally equivalent and
		// prevents the last imperceptible rebound from being sent to the client.
		return Math.abs(bounceSpeed) < LivingEntity.MIN_MOVEMENT_DISTANCE ? 0.0D : bounceSpeed;
	}
}
