package com.nobodiiiii.createbiotech.entity;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

/** Slime's ground controller for a wild animal that now uses the bionic body/trait runtime.
 * Its ordinary movement never issues a pathfinding or melee attack command. */
final class WildDevourerSlime extends Goal {
	private final SlimeBionicEntity body;
	private int turnDelay, pursuitTicks;
	private float direction;
	private boolean active, wasOnGround;
	float squish, oldSquish;
	private float targetSquish;

	WildDevourerSlime(SlimeBionicEntity body) {
		this.body = body;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	boolean active() { return active; }
	@Override public boolean canUse() {
		return body.wildSlimeSize() > 0 && !body.isPassenger() && !body.hasControllingPassenger()
			&& !body.isOrderedToSit() && !body.isBlockAttached() && !body.isSwimming()
			&& !BionicLocomotionPolicy.hasFlightLocomotion(body);
	}
	@Override public boolean requiresUpdateEveryTick() { return true; }
	@Override public void start() { active = true; direction = body.getYRot(); body.getNavigation().stop(); }
	@Override public void stop() { active = false; body.setSpeed(0); }

	@Override public void tick() {
		var target = body.getTarget();
		boolean fluid = body.isInWater() || body.isInLava();
		var random = body.getRandom();
		boolean pursuing = target != null && body.canAttack(target);
		if (pursuing) {
			if (pursuitTicks++ < reducedTickDelay(300)) {
				body.lookAt(target, 10, 10);
				direction = body.getYRot();
			} else pursuitTicks = 0;
		} else {
			pursuitTicks = 0;
			if ((body.onGround() || fluid || body.hasEffect(MobEffects.LEVITATION)) && --turnDelay <= 0) {
				turnDelay = 40 + random.nextInt(60);
				direction = random.nextInt(360);
			}
		}
		body.setYRot(Mth.approachDegrees(body.getYRot(), direction, 90));
		body.yBodyRot = body.yHeadRot = body.getYRot();
		if (fluid && random.nextFloat() < 0.8f) body.getJumpControl().jump();
		body.setSpeed((float) ((fluid ? 1.2 : 1.0) * body.getAttributeValue(Attributes.MOVEMENT_SPEED)));
		if (!body.onGround()) return;
		if (body.waitForWalkingJump()) {
			body.setXxa(0);
			body.setSpeed(0);
			return;
		}
		int size = body.wildSlimeSize();
		body.resetWalkingJumpDelay(pursuing && size > 1);
		body.getJumpControl().jump();
		body.playSound(size == 1 ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP, 0.4f * size,
			((random.nextFloat() - random.nextFloat()) * 0.2f + 1) * (size == 1 ? 1.4f : 0.8f));
	}

	void animate(int size) {
		squish += (targetSquish - squish) * 0.5f;
		oldSquish = squish;
		if (body.onGround() && !wasOnGround) {
			var random = body.getRandom();
			float width = 0.52f * size * body.getScale();
			for (int i = 0; i < width * 32; i++) {
				float angle = random.nextFloat() * Mth.TWO_PI;
				float radius = (0.5f + 0.5f * random.nextFloat()) * width;
				body.level().addParticle(ParticleTypes.ITEM_SLIME, body.getX() + Mth.sin(angle) * radius,
					body.getY(), body.getZ() + Mth.cos(angle) * radius, 0, 0, 0);
			}
			body.playSound(size == 1 ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.SLIME_SQUISH,
				0.4f * size, ((random.nextFloat() - random.nextFloat()) * 0.2f + 1) / 0.8f);
			targetSquish = -0.5f;
		} else if (!body.onGround() && wasOnGround) targetSquish = 1;
		wasOnGround = body.onGround();
		targetSquish *= 0.6f;
	}
}
