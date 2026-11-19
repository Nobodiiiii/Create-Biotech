package com.nobodiiiii.createbiotech.entity;

import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.entity.ai.BionicWallClimbing;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.network.CBPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Internal BionicGoalSet implementation; public entry points remain on SlimeBionicEntity. */
final class BionicGoalSet {
	private BionicGoalSet() {}

	static final class BionicFloatGoal extends FloatGoal {
		private final SlimeBionicEntity bionic;

		BionicFloatGoal(SlimeBionicEntity bionic) {
			super(bionic);
			this.bionic = bionic;
		}

		@Override public boolean canUse() {
			// Strider buoyancy handles lava without FloatGoal's repeated jump requests.
			if (bionic.isInLava() && bionic.getBionicTraits().has(BionicTrait.LAVA_WALK))
				return false;
			// Sinking bodies use vanilla water gravity instead of jumping to the surface.
			// Aquatic and flying controllers retain their own vertical movement.
			if (bionic.isInWater() && !bionic.isInLava()
				&& bionic.getBionicTraits().has(BionicTrait.WATER_SINKING))
				return false;
			// Swimming already steers vertically; keep fluid escape for other bodies.
			return (!bionic.organSwimEnabled || bionic.isInLava()) && super.canUse();
		}
	}

	/** Skeleton-style shade seeking, also usable by assembled bodies with non-ground navigation. */
	static final class BionicFleeSunGoal extends FleeSunGoal {
		private final SlimeBionicEntity bionic;

		BionicFleeSunGoal(SlimeBionicEntity bionic) {
			super(bionic, 1.0d);
			this.bionic = bionic;
		}

		private boolean needsShade() {
			return bionic.shouldAvoidSun() && bionic.level().isDay()
				&& bionic.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
				&& !bionic.isOrderedToSit() && !bionic.isVehicle() && !bionic.isPassenger()
				&& bionic.getTarget() == null;
		}

		@Override public boolean canUse() {
			// Seek cover before ignition; combat, sitting and rider control retain priority.
			return needsShade() && bionic.level().canSeeSky(bionic.blockPosition()) && setWantedPos();
		}

		@Override public boolean canContinueToUse() {
			return needsShade() && super.canContinueToUse();
		}

		@Override @Nullable protected Vec3 getHidePos() {
			for (int attempt = 0; attempt < 10; attempt++) {
				BlockPos pos = bionic.blockPosition().offset(bionic.getRandom().nextInt(20) - 10,
					bionic.getRandom().nextInt(6) - 3, bionic.getRandom().nextInt(20) - 10);
				Vec3 target = Vec3.atBottomCenterOf(pos);
				// PathfinderMob has no Monster light preference; check shade and room for this body.
				if (!bionic.level().canSeeSky(pos) && bionic.getNavigation().isStableDestination(pos)
					&& bionic.level().noCollision(bionic, bionic.getBoundingBox().move(target.subtract(bionic.position()))))
					return target;
			}
			return null;
		}
	}

	static final class BionicSitGoal extends Goal {
		private final SlimeBionicEntity bionic;

		BionicSitGoal(SlimeBionicEntity bionic) {
			this.bionic = bionic;
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override public boolean canUse() {
			return bionic.orderedToSit && bionic.getBionicTraits().has(BionicTrait.TAMEABLE)
				&& bionic.onGround() && !bionic.isInWaterOrBubble();
		}
		@Override public boolean canContinueToUse() { return canUse(); }
		@Override public void start() { bionic.getNavigation().stop(); }
		@Override public void stop() { bionic.getNavigation().stop(); }
	}

	static final class BionicLandStrollGoal extends WaterAvoidingRandomStrollGoal {
		private final SlimeBionicEntity bionic;
		BionicLandStrollGoal(SlimeBionicEntity bionic) {
			super(bionic, 1.0d);
			this.bionic = bionic;
		}
		@Override public boolean canUse() {
			return !bionic.isSwimming() && !BionicLocomotionPolicy.prefersLowFlight(bionic) && super.canUse();
		}
		@Override public boolean canContinueToUse() {
			return !bionic.isSwimming() && !BionicLocomotionPolicy.prefersLowFlight(bionic) && super.canContinueToUse();
		}
	}

	static final class BionicSwimStrollGoal extends RandomStrollGoal {
		private static final int[][] DISTANCES = {{1, 1}, {3, 3}, {5, 5}, {6, 5}, {7, 7}, {10, 7}};
		private final SlimeBionicEntity bionic;
		BionicSwimStrollGoal(SlimeBionicEntity bionic) {
			// Unlike land wandering, water activity must not stop after noActionTime >= 100.
			super(bionic, SlimeBionicEntity.SWIMMING_IDLE_SPEED, 20, false);
			this.bionic = bionic;
		}
		@Override public boolean canUse() {
			return bionic.isSwimming() && super.canUse();
		}
		@Override public boolean canContinueToUse() {
			return bionic.isSwimming() && super.canContinueToUse();
		}
		@Nullable
		@Override protected Vec3 getPosition() {
			// AxolotlAi uses RandomStroll.swim: choose nearby water, then extend that
			// direction in tiers and retain the last water point before reaching shore.
			Vec3 target = null;
			for (int[] distance : DISTANCES) {
				Vec3 candidate = target == null
					? BehaviorUtils.getRandomSwimmablePos(bionic, distance[0], distance[1])
					: bionic.position().add(target.subtract(bionic.position()).normalize()
						.multiply(distance[0], distance[1], distance[0]));
				if (candidate == null || !bionic.level().getFluidState(BlockPos.containing(candidate))
					.is(FluidTags.WATER))
					break;
				target = candidate;
			}
			return target;
		}
	}

	static final class BionicFollowOwnerGoal extends Goal {
		private final SlimeBionicEntity bionic;
		@Nullable private Player owner;
		private int recalculate;

		BionicFollowOwnerGoal(SlimeBionicEntity bionic) {
			this.bionic = bionic;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override public boolean canUse() {
			if (bionic.orderedToSit || bionic.ownerId == null || !bionic.getBionicTraits().has(BionicTrait.TAMEABLE))
				return false;
			owner = bionic.getCompanionPlayer();
			return owner != null && !owner.isSpectator() && bionic.distanceToSqr(owner) > 100.0d;
		}
		@Override public boolean canContinueToUse() {
			return owner != null && owner.isAlive() && !bionic.orderedToSit
				&& (bionic.ownerId != null && bionic.getBionicTraits().has(BionicTrait.TAMEABLE))
				&& bionic.distanceToSqr(owner) > 4.0d && (!bionic.getNavigation().isDone()
					|| BionicWallClimbing.isInProgress(bionic.getNavigation()));
		}
		@Override public void stop() { owner = null; bionic.getNavigation().stop(); }
		@Override public void tick() {
			if (owner == null)
				return;
			bionic.getLookControl().setLookAt(owner, 10.0f, bionic.getMaxHeadXRot());
			if (--recalculate <= 0) {
				recalculate = adjustedTickDelay(10);
				if (bionic.distanceToSqr(owner) >= 144 && !bionic.isLeashed() && !bionic.isPassenger()) {
					for (int attempt = 0; attempt < 10; attempt++) {
						BlockPos pos = owner.blockPosition().offset(bionic.getRandom().nextInt(7) - 3,
							bionic.getRandom().nextInt(3) - 1, bionic.getRandom().nextInt(7) - 3);
						if (Math.abs(pos.getX() - owner.getBlockX()) < 2 && Math.abs(pos.getZ() - owner.getBlockZ()) < 2) continue;
						if (net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(bionic, pos) == PathType.WALKABLE
							&& !(bionic.level().getBlockState(pos.below()).getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)
							&& bionic.level().noCollision(bionic, bionic.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(bionic.position())))) {
							bionic.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, bionic.getYRot(), bionic.getXRot());
							bionic.getNavigation().stop(); break;
						}
					}
				} else bionic.getNavigation().moveTo(owner, bionic.isSwimming() ? SlimeBionicEntity.SWIMMING_PURSUIT_SPEED : 1.2d);
			}
		}
	}

	/** Vanilla pursuit with independent, directional melee actions and persistent recovery deadlines. */
	static class BionicAttackGoal extends MeleeAttackGoal {
		private final SlimeBionicEntity bionic;
		private final double pursuitSpeed;
		private final long[] rightArmRecovery = new long[SurgicalLimbType.SHOULDER.maxPerBody()];
		private final long[] leftArmRecovery = new long[SurgicalLimbType.SHOULDER.maxPerBody()];
		private int raiseArmTicks;
		private long nextAttackTick;
		private int currentAttackInterval = SurgicalCombatCalibration.ZOMBIE_ATTACK_INTERVAL;
		@Nullable
		private LivingEntity pendingAttackTarget;
		private long pendingAttackStartedAt;
		private int pendingAttackDuration;
		private int pendingContactStartTick;
		private boolean pendingImpactApplied;
		private double pendingDamageMultiplier = 1.0d;
		@Nullable
		private SurgicalAssembly.ArmAttackGeometry pendingArmGeometry;
		@Nullable
		private Vec3 pendingAim;
		private float pendingBodyYaw;
		private boolean pendingAimSynchronized;
		@Nullable
		private SlimeBionicEntity.ArmUse lastArm;

		BionicAttackGoal(SlimeBionicEntity bionic, double speedModifier, boolean followingTargetEvenIfNotSeen) {
			super(bionic, speedModifier, followingTargetEvenIfNotSeen);
			this.bionic = bionic;
			pursuitSpeed = speedModifier;
		}

		@Override
		public boolean canUse() {
			return validTarget(bionic.getTarget()) && (bionic.vibrationSense.lastHeardPosition(bionic.getTarget()) != null
				|| super.canUse() || bionic.canUseWallClimbing());
		}

		@Override
		public void start() {
			super.start();
			// MeleeAttackGoal can have no cached path (or one from a previous attempt)
			// when climbing is what allowed this goal to start.
			if (bionic.canUseWallClimbing() && bionic.getTarget() != null)
				bionic.getNavigation().moveTo(bionic.getTarget(), pursuitSpeed);
			raiseArmTicks = 0;
			clearPendingAttack();
			// Deadlines deliberately survive Goal restarts and target changes.
			bionic.combatFacingControlled = false;
		}

		@Override
		public void stop() {
			super.stop();
			clearPendingAttack();
			bionic.combatFacingControlled = false;
			bionic.setAggressive(false);
		}

		private boolean validTarget(@Nullable LivingEntity target) {
			return BionicMeleeController.isValidMeleeTarget(bionic, target);
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = bionic.getTarget();
			return validTarget(target) && bionic.isWithinRestriction(target.blockPosition())
				&& (pendingAttackTarget == target || bionic.isWithinMeleeAttackRange(target)
					|| bionic.vibrationSense.lastHeardPosition(target) != null
					// SpiderAttackGoal keeps pursuing a valid target after its ground path ends.
					|| bionic.canUseWallClimbing() || super.canContinueToUse());
		}

		@Override
		protected void checkAndPerformAttack(LivingEntity target) {
			// Vanilla canPerformAttack() requires eye-to-eye sensing before our complete-AABB arm
			// checks run. Use only its timing/range portions; per-arm block visibility is checked from
			// the shoulder to the nearest target-box point in beginAttack() and again on contact.
			if (BionicMeleeController.usesContactAttack(bionic) || pendingAttackTarget != null || !validTarget(target) || !isTimeToAttack()
				|| !bionic.isWithinMeleeAttackRange(target))
				return;
			SlimeBionicEntity.AttackStart attack = bionic.beginAttack(target, rightArmRecovery, leftArmRecovery, lastArm);
			if (attack == null)
				return;
			long now = bionic.level().getGameTime();
			nextAttackTick = now + attack.globalAttackInterval();
			currentAttackInterval = attack.globalAttackInterval();
			bionic.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			pendingAttackDuration = attack.actionDuration();
			pendingContactStartTick = attack.contactStartTick();
			pendingArmGeometry = attack.arm();
			pendingAim = attack.aim();
			pendingBodyYaw = BionicMeleeController.combatBodyYaw(bionic);
			pendingDamageMultiplier = attack.damageMultiplier();
			pendingAttackTarget = target;
			pendingAttackStartedAt = now;
			pendingImpactApplied = false;
			pendingAimSynchronized = false;
			long[] recovery = attack.left() ? leftArmRecovery : rightArmRecovery;
			recovery[attack.slot()] = now + attack.armRecovery();
			lastArm = new SlimeBionicEntity.ArmUse(attack.left(), attack.slot());
		}

		private void advancePendingAttack() {
			LivingEntity target = pendingAttackTarget;
			if (!validTarget(target) || target != bionic.getTarget()
				|| pendingArmGeometry == null || pendingAim == null) {
				clearPendingAttack();
				return;
			}
			long elapsed = bionic.level().getGameTime() - pendingAttackStartedAt;
			if (elapsed < 0 || elapsed >= pendingAttackDuration) {
				clearPendingAttack();
				return;
			}
			int pendingAttackElapsed = (int) elapsed;
			int activeStart = pendingContactStartTick;
			// The final preparation tick commits both the aim and shoulder orientation. Neither
			// navigation nor model/body smoothing can sweep the strike around after this point.
			if (pendingAttackElapsed < activeStart) {
				turnBodyToward(target);
				pendingBodyYaw = BionicMeleeController.combatBodyYaw(bionic);
				trackAim(target);
				BionicMeleeController.synchronizeAttackAim(bionic, pendingAim, pendingBodyYaw, pendingAttackDuration - pendingAttackElapsed);
			}
			if (!pendingAimSynchronized && pendingAttackElapsed >= activeStart) {
				pendingAimSynchronized = true;
				BionicMeleeController.synchronizeAttackAim(bionic, pendingAim, pendingBodyYaw, pendingAttackDuration - pendingAttackElapsed);
			}
			if (!pendingImpactApplied && SlimeBionicCombat.isContactTick(pendingAttackElapsed,
				pendingContactStartTick)
				&& SlimeBionicCombat.intersects(target.getBoundingBox(), bionic.position(), pendingBodyYaw,
					pendingAim, pendingArmGeometry)
				&& BionicMeleeController.hasAttackLineOfSight(bionic, target, pendingArmGeometry, pendingBodyYaw)) {
				// Contact consumes the swing even when a shield, hurt immunity or an event blocks damage.
				pendingImpactApplied = true;
				BionicMeleeController.performAttackDamage(bionic, target, pendingDamageMultiplier);
			}
		}

		private void turnBodyToward(LivingEntity target) {
			float current = BionicMeleeController.combatBodyYaw(bionic);
			Vec3 direction = target.getBoundingBox().getCenter().subtract(bionic.position());
			float desired = direction.horizontalDistanceSqr() < 1.0e-8d ? current : SlimeBionicCombat.yaw(direction);
			bionic.combatFacingYaw = Mth.approachDegrees(current, desired,
				SlimeBionicCombat.BODY_TURN_DEGREES);
			bionic.combatFacingControlled = true;
			BionicMeleeController.applyCombatFacing(bionic);
		}

		private void trackAim(LivingEntity target) {
			Vec3 origin = SlimeBionicCombat.worldOrigin(bionic.position(), pendingBodyYaw, pendingArmGeometry);
			Vec3 desired = SlimeBionicCombat.constrainAim(
				SlimeBionicCombat.aimAt(target.getBoundingBox(), origin, pendingBodyYaw), pendingBodyYaw, pendingArmGeometry);
			pendingAim = SlimeBionicCombat.constrainAim(SlimeBionicCombat.approachAim(
				pendingAim, desired, SlimeBionicCombat.AIM_TRACKING_DEGREES), pendingBodyYaw, pendingArmGeometry);
		}

		private void clearPendingAttack() {
			boolean wasPending = pendingAttackTarget != null;
			pendingAttackTarget = null;
			pendingAttackStartedAt = 0L;
			pendingAttackDuration = 0;
			pendingContactStartTick = 0;
			pendingImpactApplied = false;
			pendingArmGeometry = null;
			pendingAim = null;
			pendingAimSynchronized = false;
			pendingDamageMultiplier = 1.0d;
			bionic.attackActionTick = 0;
			if (wasPending)
				CBPackets.sendToTrackingEntity(SlimeBionicAttackActionPacket.aim(bionic), bionic);
		}

		@Override
		protected boolean isTimeToAttack() {
			return bionic.level().getGameTime() >= nextAttackTick;
		}

		@Override
		protected int getTicksUntilNextAttack() {
			return (int) Math.max(0L, nextAttackTick - bionic.level().getGameTime());
		}

		@Override
		protected int getAttackInterval() {
			return currentAttackInterval;
		}

		@Override
		public void tick() {
			LivingEntity target = bionic.getTarget();
			if (!validTarget(target)) {
				clearPendingAttack();
				bionic.combatFacingControlled = false;
				bionic.getNavigation().stop();
				return;
			}
			if (bionic.getBionicTraits().has(BionicTrait.VIBRATION_SENSE) && !bionic.hasLineOfSight(target)) {
				clearPendingAttack();
				bionic.combatFacingControlled = false;
				Vec3 heard = bionic.vibrationSense.lastHeardPosition(target);
				if (heard != null) {
					bionic.getLookControl().setLookAt(heard.x, heard.y, heard.z);
					bionic.getNavigation().moveTo(heard.x, heard.y, heard.z,
						bionic.isSwimming() ? SlimeBionicEntity.SWIMMING_PURSUIT_SPEED : 1.0d);
				} else bionic.getNavigation().stop();
				return;
			}
			boolean contactAttack = BionicMeleeController.usesContactAttack(bionic);
			if (pendingAttackTarget != null && (pendingAttackTarget != target || contactAttack))
				clearPendingAttack();
			if (contactAttack) {
				bionic.combatFacingControlled = false;
			} else if (pendingAttackTarget != null) {
				advancePendingAttack();
			} else if (BionicMeleeController.withinCombatEnvelope(bionic, target.getBoundingBox().inflate(0.5d))) {
				turnBodyToward(target);
			} else {
				bionic.combatFacingControlled = false;
			}
			super.tick();
			if (bionic.isSwimming())
				bionic.getNavigation().setSpeedModifier(SlimeBionicEntity.SWIMMING_PURSUIT_SPEED);
			if (pendingAttackTarget != null && bionic.shouldPauseAttackMovement())
				bionic.getNavigation().stop();
			raiseArmTicks++;
			bionic.setAggressive(contactAttack || pendingAttackTarget != null
				|| raiseArmTicks >= 5 && getTicksUntilNextAttack() < getAttackInterval() / 2);
		}
	}
}
