package com.nobodiiiii.createbiotech.entity;

import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.shapes.CollisionContext;

/** BionicEnvironment algorithms over the canonical SlimeBionicEntity state. */
final class BionicEnvironment {
	private BionicEnvironment() {}


	/** Native armadillo roll states, triggered only by damage to the assembled body. */
	static void tickShellDefense(SlimeBionicEntity operationOwner) {
		var idle = net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.IDLE;
		var rolling = net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.ROLLING;
		var scared = net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.SCARED;
		var unrolling = net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.UNROLLING;
		boolean enabled = operationOwner.getBionicTraits().has(BionicTrait.SHELL_DEFENSE) && !operationOwner.isNoAi() && operationOwner.isAlive();
		boolean allowed = enabled && !operationOwner.isInLiquid() && !operationOwner.isLeashed() && !operationOwner.isPassenger() && !operationOwner.isVehicle();
		if (operationOwner.shellGuardTicks > 0) operationOwner.shellGuardTicks--;
		if (operationOwner.shellPeekTicks > 0) operationOwner.shellPeekTicks--;
		boolean dangerAround = operationOwner.shellGuardTicks > 75;
		if (dangerAround != operationOwner.shellDangerWasAround) operationOwner.shellPeekTicks = scared.animationDuration() + 100 + operationOwner.getRandom().nextInt(301);
		operationOwner.shellDangerWasAround = dangerAround;
		if (operationOwner.shellState == scared && operationOwner.shellPeekTicks == 0 && dangerAround) {
			operationOwner.level().broadcastEntityEvent(operationOwner, (byte) 64);
			operationOwner.shellPeekTicks = scared.animationDuration() + 100 + operationOwner.getRandom().nextInt(301);
		}
		var next = operationOwner.shellState;
		if (!allowed || operationOwner.shellGuardTicks == 0) next = idle;
		else if (operationOwner.shellState == idle) next = rolling;
		else if (operationOwner.shellState == rolling && operationOwner.shellStateTicks > rolling.animationDuration()) next = scared;
		else if (operationOwner.shellState == scared && operationOwner.shellGuardTicks < unrolling.animationDuration()) next = unrolling;
		else if (operationOwner.shellState == unrolling && operationOwner.shellGuardTicks > unrolling.animationDuration()) next = scared;
		if (next != operationOwner.shellState) {
			if (next == rolling) operationOwner.playSound(net.minecraft.sounds.SoundEvents.ARMADILLO_ROLL);
			if (next == unrolling) operationOwner.playSound(net.minecraft.sounds.SoundEvents.ARMADILLO_UNROLL_START);
			if (next == scared && operationOwner.shellState == rolling) operationOwner.playSound(net.minecraft.sounds.SoundEvents.ARMADILLO_LAND);
			if (next == idle && operationOwner.shellState != idle) operationOwner.playSound(net.minecraft.sounds.SoundEvents.ARMADILLO_UNROLL_FINISH);
			operationOwner.shellState = next; operationOwner.shellStateTicks = 0;
		} else operationOwner.shellStateTicks++;
		if (operationOwner.getEntityData().get(SlimeBionicEntity.SHELL_STATE_DATA) != operationOwner.shellState.ordinal()) {
			operationOwner.getEntityData().set(SlimeBionicEntity.SHELL_STATE_DATA, operationOwner.shellState.ordinal());
		}
		operationOwner.setShiftKeyDown(operationOwner.shellState != idle);
		if (operationOwner.isShiftKeyDown()) { operationOwner.getNavigation().stop(); operationOwner.setDeltaMovement(operationOwner.getDeltaMovement().multiply(0, 1, 0)); }
	}

	static void tickLavaWalking(SlimeBionicEntity operationOwner) {
		if (!operationOwner.getBionicTraits().has(BionicTrait.LAVA_WALK))
			return;
		// Vanilla Strider.floatStrider runs after super.tick on both sides. Use the
		// same half-block support shape as LiquidBlock, not the rendered fluid height.
		if (operationOwner.isInLava()) {
			if (CollisionContext.of(operationOwner).isAbove(LiquidBlock.STABLE_SHAPE, operationOwner.blockPosition(), true)
				&& !operationOwner.level().getFluidState(operationOwner.blockPosition().above()).is(FluidTags.LAVA)) {
				operationOwner.setOnGround(true);
			} else {
				operationOwner.setDeltaMovement(operationOwner.getDeltaMovement().scale(0.5d).add(0.0d, 0.05d, 0.0d));
			}
		}
		operationOwner.checkBionicInsideBlocks();
	}

	static void updateOwnerTarget(SlimeBionicEntity operationOwner) {
		if (operationOwner.level().isClientSide || operationOwner.ownerId == null || operationOwner.orderedToSit
			|| !operationOwner.getBionicTraits().has(BionicTrait.TAMEABLE))
			return;
		Player owner = operationOwner.getBionicOwner();
		if (owner == null)
			return;
		LivingEntity candidate = null;
		if (owner.getLastHurtByMobTimestamp() != operationOwner.lastOwnerHurtTimestamp) {
			operationOwner.lastOwnerHurtTimestamp = owner.getLastHurtByMobTimestamp();
			candidate = owner.getLastHurtByMob();
		}
		if (candidate == null && owner.getLastHurtMobTimestamp() != operationOwner.lastOwnerAttackTimestamp) {
			operationOwner.lastOwnerAttackTimestamp = owner.getLastHurtMobTimestamp();
			candidate = owner.getLastHurtMob();
		}
		if (candidate != null && candidate.isAlive() && candidate != operationOwner
			&& !operationOwner.isAlliedTo(candidate)) {
			if (operationOwner.canAttack(candidate))
				operationOwner.setTarget(candidate);
		}
	}

	static void updateDynamicGameEventListener(SlimeBionicEntity operationOwner, java.util.function.BiConsumer<net.minecraft.world.level.gameevent.DynamicGameEventListener<?>,
		net.minecraft.server.level.ServerLevel> consumer) {
		if (operationOwner.level() instanceof net.minecraft.server.level.ServerLevel server && operationOwner.vibrationSense != null)
			consumer.accept(operationOwner.vibrationSense.listener(), server);
	}



	static void tickHeadRespiration(SlimeBionicEntity operationOwner) {
		if (operationOwner.level().isClientSide || !operationOwner.isAlive())
			return;
		if (!operationOwner.getBionicTraits().has(BionicTrait.DRY_SUFFOCATION)) {
			operationOwner.dryAir = Integer.MIN_VALUE;
			return;
		}
		if (operationOwner.isInWaterOrBubble()) {
			operationOwner.dryAir = 300;
			return;
		}
		if (operationOwner.dryAir == Integer.MIN_VALUE) operationOwner.dryAir = 300;
		if (--operationOwner.dryAir == -20) { operationOwner.dryAir = 0; operationOwner.hurt(operationOwner.damageSources().drown(), 2.0f); }
	}

	static void tickBodyEnvironment(SlimeBionicEntity operationOwner) {
		if (operationOwner.level().isClientSide || !operationOwner.isAlive())
			return;
		BionicTraitSet traits = operationOwner.getBionicTraits();
		if (operationOwner.tickCount % 10 == 0 && traits.value(BionicTrait.PASSIVE_REGENERATION) > 0.0d
			&& operationOwner.getHealth() < operationOwner.getMaxHealth())
			operationOwner.heal((float) (traits.value(BionicTrait.PASSIVE_REGENERATION) * 0.5d));
		if (traits.has(BionicTrait.SUN_SENSITIVE) && operationOwner.isBionicSunBurnTick()) {
			boolean burns = true;
			ItemStack headwear = operationOwner.getItemBySlot(EquipmentSlot.HEAD);
			if (!headwear.isEmpty()) {
				if (headwear.isDamageableItem()) {
					Item item = headwear.getItem();
					headwear.setDamageValue(headwear.getDamageValue() + operationOwner.getRandom().nextInt(2));
					if (headwear.getDamageValue() >= headwear.getMaxDamage()) {
						operationOwner.onEquippedItemBroken(item, EquipmentSlot.HEAD);
						operationOwner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
					}
				}
				burns = false;
			}
			if (burns)
				operationOwner.igniteForSeconds(8.0f);
		}

		double heatSensitivity = traits.strength(BionicTrait.HEAT_SENSITIVE);
		if (heatSensitivity > 0.0d
			&& operationOwner.level().getBiome(operationOwner.blockPosition()).is(BiomeTags.SNOW_GOLEM_MELTS))
			operationOwner.hurt(operationOwner.damageSources().onFire(), (float) heatSensitivity);

		double moistureDependence = traits.strength(BionicTrait.MOISTURE_DEPENDENT);
		if (moistureDependence <= 0.0d) {
			operationOwner.moisture = -1;
			return;
		}
		if (operationOwner.isNoAi()) return;
		if (operationOwner.isInWaterRainOrBubble()) {
			operationOwner.moisture = SlimeBionicEntity.MAX_MOISTURE;
			return;
		}
		if (operationOwner.moisture < 0) operationOwner.moisture = SlimeBionicEntity.MAX_MOISTURE;
		operationOwner.moisture = Math.max(0, operationOwner.moisture - 1);
		if (operationOwner.moisture <= 0)
			operationOwner.hurt(operationOwner.damageSources().dryOut(), (float) moistureDependence);
	}
}
