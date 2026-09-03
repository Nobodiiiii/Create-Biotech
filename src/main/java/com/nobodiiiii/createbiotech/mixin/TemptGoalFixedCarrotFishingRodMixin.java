package com.nobodiiiii.createbiotech.mixin;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodTarget;
import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodTargeting;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Lets a fixed rod act as a stationary player for existing vanilla-style temptation goals.
 * The goal keeps its original priority, bait predicate, speed, cooldown and subclass gates.
 */
@Mixin(TemptGoal.class)
public abstract class TemptGoalFixedCarrotFishingRodMixin {

	@Shadow
	@Final
	private TargetingConditions targetingConditions;

	@Shadow
	@Final
	protected PathfinderMob mob;

	@Shadow
	@Final
	private double speedModifier;

	@Shadow
	@Final
	private Predicate<ItemStack> items;

	@Shadow
	@Nullable
	protected Player player;

	@Shadow
	private int calmDown;

	@Shadow
	private double px;

	@Shadow
	private double py;

	@Shadow
	private double pz;

	@Shadow
	private double pRotX;

	@Shadow
	private double pRotY;

	@Shadow
	private boolean isRunning;

	@Unique
	@Nullable
	private FixedCarrotFishingRodTarget createBiotech$fixedRodTarget;

	@Unique
	private boolean createBiotech$wasCoolingDown;

	@Unique
	private int createBiotech$fixedRodSearchCooldown;

	@Inject(method = "canUse", at = @At("HEAD"))
	private void createBiotech$rememberCooldown(CallbackInfoReturnable<Boolean> cir) {
		createBiotech$wasCoolingDown = calmDown > 0;
	}

	@Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
	private void createBiotech$useFixedRodWhenNoPlayer(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue()) {
			createBiotech$fixedRodTarget = null;
			createBiotech$fixedRodSearchCooldown = 0;
			return;
		}
		if (createBiotech$wasCoolingDown)
			return;
		if (createBiotech$fixedRodSearchCooldown > 0) {
			createBiotech$fixedRodSearchCooldown--;
			return;
		}

		createBiotech$fixedRodTarget = FixedCarrotFishingRodTargeting.findNearest(mob, items);
		if (createBiotech$fixedRodTarget != null) {
			cir.setReturnValue(true);
		} else {
			createBiotech$fixedRodSearchCooldown =
				FixedCarrotFishingRodTargeting.getSearchCooldownTicks();
		}
	}

	@Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
	private void createBiotech$continueUsingFixedRod(CallbackInfoReturnable<Boolean> cir) {
		if (createBiotech$fixedRodTarget == null)
			return;

		Player nearestPlayer = mob.level()
			.getNearestPlayer(targetingConditions, mob);
		if (nearestPlayer != null) {
			player = nearestPlayer;
			px = nearestPlayer.getX();
			py = nearestPlayer.getY();
			pz = nearestPlayer.getZ();
			pRotX = nearestPlayer.getXRot();
			pRotY = nearestPlayer.getYRot();
			createBiotech$fixedRodTarget = null;
			cir.setReturnValue(true);
			return;
		}

		if (FixedCarrotFishingRodTargeting.getValidBaitPosition(mob,
			createBiotech$fixedRodTarget) != null) {
			cir.setReturnValue(true);
			return;
		}

		createBiotech$fixedRodTarget = FixedCarrotFishingRodTargeting.findNearest(mob, items);
		cir.setReturnValue(createBiotech$fixedRodTarget != null);
	}

	@Inject(method = "start", at = @At("HEAD"), cancellable = true)
	private void createBiotech$startUsingFixedRod(CallbackInfo ci) {
		if (createBiotech$fixedRodTarget == null)
			return;

		player = null;
		isRunning = true;
		ci.cancel();
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void createBiotech$tickFixedRod(CallbackInfo ci) {
		FixedCarrotFishingRodTarget target = createBiotech$fixedRodTarget;
		if (target == null)
			return;

		Vec3 baitPosition = FixedCarrotFishingRodTargeting.getValidBaitPosition(mob, target);
		if (baitPosition != null) {
			FixedCarrotFishingRodTargeting.awardIfAnimalReachedPowerBelt(mob, target);
			mob.getLookControl()
				.setLookAt(baitPosition.x, baitPosition.y, baitPosition.z,
					(float) (mob.getMaxHeadYRot() + 20), (float) mob.getMaxHeadXRot());
			if (mob.distanceToSqr(baitPosition) < 6.25)
				mob.getNavigation().stop();
			else
				mob.getNavigation().moveTo(baitPosition.x, baitPosition.y, baitPosition.z, speedModifier);
		}

		ci.cancel();
	}

	@Inject(method = "stop", at = @At("TAIL"))
	private void createBiotech$clearFixedRod(CallbackInfo ci) {
		createBiotech$fixedRodTarget = null;
	}
}
