package com.nobodiiiii.createbiotech.entity;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import com.google.common.collect.HashMultimap;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.entity.ai.BionicIntelligence;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.network.CBPackets;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** BionicMeleeController algorithms over the canonical SlimeBionicEntity state. */
final class BionicMeleeController {
	private BionicMeleeController() {}


	/** A body with no attacking arms, including wing-only bodies, fights by slime-like contact. */
	static boolean usesContactAttack(SlimeBionicEntity operationOwner) {
		SurgicalAssembly assembly = operationOwner.getAssembly();
		SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
		return geometry == null || geometry.arms().stream().allMatch(operationOwner::isBionicWing);
	}

	static boolean withinCombatEnvelope(SlimeBionicEntity operationOwner, AABB targetBounds) {
		if (BionicMeleeController.usesContactAttack(operationOwner))
			return operationOwner.bionicAttackBoundingBox().intersects(targetBounds);
		SurgicalAssembly assembly = operationOwner.getAssembly();
		SurgicalAssembly.AttackGeometry geometry = assembly == null ? null : assembly.attackGeometry();
		for (SurgicalAssembly.ArmAttackGeometry arm : geometry.arms())
			if (!operationOwner.isBionicWing(arm) && SlimeBionicCombat.withinStartEnvelope(targetBounds, operationOwner.position(), BionicMeleeController.combatBodyYaw(operationOwner), arm))
				return true;
		return false;
	}

	static boolean isValidMeleeTarget(SlimeBionicEntity operationOwner, @Nullable LivingEntity target) {
		return target != null && operationOwner.isAlive() && !operationOwner.isNoAi() && target.isAlive()
			&& !operationOwner.isVehicle() && !operationOwner.specialAttacks.busy()
			&& target.level() == operationOwner.level() && operationOwner.canAttack(target)
			&& !operationOwner.isAlliedTo(target) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
	}

	static float combatBodyYaw(SlimeBionicEntity operationOwner) {
		return operationOwner.combatFacingControlled ? operationOwner.combatFacingYaw : operationOwner.getYRot();
	}

	/** Movement and visual body smoothing follow the server's combat facing, never the reverse. */
	static boolean applyCombatFacing(SlimeBionicEntity operationOwner) {
		if (operationOwner.isBlockAttached()) return false;
		if (!operationOwner.combatFacingControlled || operationOwner.isNoAi() || !operationOwner.isAlive())
			return false;
		operationOwner.setYRot(operationOwner.combatFacingYaw);
		operationOwner.yBodyRot = operationOwner.combatFacingYaw;
		return true;
	}

	static @Nullable
	SlimeBionicEntity.ArmSelection selectArm(SlimeBionicEntity operationOwner, LivingEntity target, SurgicalAssembly.AttackGeometry geometry,
		long[] rightRecovery, long[] leftRecovery, @Nullable SlimeBionicEntity.ArmUse lastArm) {
		SlimeBionicEntity.ArmCandidate best = null;
		int cadenceArms = 0;
		int fixedArmIndex = -1;
		if (operationOwner.getIntelligence() == BionicIntelligence.SIMPLE) {
			double highestDps = -1;
			double baseAttackDamage = operationOwner.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
			var weaponArm = operationOwner.equipment.mainHandArm();
			ItemStack weapon = operationOwner.getMainHandItem();
			double armedAttackDamage = SlimeBionicEntity.isAttackWeapon(weapon)
				? SurgicalCombatCalibration.attackDamageWithWeapon(baseAttackDamage, weapon) : baseAttackDamage;
			var arms = geometry.arms();
			for (int index = 0; index < arms.size(); index++) {
				var arm = arms.get(index);
				if (operationOwner.isBionicWing(arm)) continue;
				var stats = SurgicalCombatCalibration.stats(arm, operationOwner.level());
				double attackDamage = arm == weaponArm ? armedAttackDamage : baseAttackDamage;
				double dps = SurgicalCombatCalibration.damagePerHit(attackDamage, stats, BionicMeleeController.armDamageOffset(operationOwner, arm))
					* 20.0d / SurgicalCombatCalibration.armRecovery(stats, operationOwner.level());
				if (dps > highestDps) { highestDps = dps; fixedArmIndex = index; }
			}
		}
		long now = operationOwner.level().getGameTime();
		float bodyYaw = BionicMeleeController.combatBodyYaw(operationOwner);
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			List<SurgicalAssembly.ArmAttackGeometry> arms = left ? geometry.left() : geometry.right();
			long[] recovery = left ? leftRecovery : rightRecovery;
			for (int slot = 0; slot < arms.size(); slot++) {
				// A simple mind waits for its favourite, even when another arm could hit now.
				int armIndex = left ? geometry.right().size() + slot : slot;
				if (fixedArmIndex >= 0 && armIndex != fixedArmIndex)
					continue;
				if (slot >= recovery.length || recovery[slot] > now)
					continue;
				SurgicalAssembly.ArmAttackGeometry arm = arms.get(slot);
				if (operationOwner.isBionicWing(arm)) continue;
				if (!SlimeBionicCombat.withinStartEnvelope(target.getBoundingBox(), operationOwner.position(), bodyYaw, arm)
					|| !BionicMeleeController.hasAttackLineOfSight(operationOwner, target, arm, bodyYaw))
					continue;
				// Count coordination before posture filtering, preserving the existing attack cadence.
				if (SlimeBionicCombat.contributesToCadence(target.getBoundingBox(), operationOwner.position(), bodyYaw, arm))
					cadenceArms++;
				if (!SlimeBionicCombat.canStart(target.getBoundingBox(), operationOwner.position(), bodyYaw, arm))
					continue;
				Vec3 origin = SlimeBionicCombat.worldOrigin(operationOwner.position(), bodyYaw, arm);
				Vec3 aim = SlimeBionicCombat.constrainAim(
					SlimeBionicCombat.aimAt(target.getBoundingBox(), origin, bodyYaw), bodyYaw, arm);
				float relativeYaw = Mth.wrapDegrees(SlimeBionicCombat.yaw(aim) - bodyYaw);
				boolean weapon = BionicMeleeController.armHasWeapon(operationOwner, left, slot);
				Vec3 localTarget = target.getBoundingBox().getCenter().subtract(operationOwner.position())
					.yRot(bodyYaw * Mth.DEG_TO_RAD);
				boolean crossesBody = left ? localTarget.x < -0.05d : localTarget.x > 0.05d;
				double score = Math.abs(relativeYaw)
					+ Math.abs(SlimeBionicCombat.pitch(aim)) * 0.35d
					+ SlimeBionicCombat.posturePreferencePenalty(aim, bodyYaw, arm)
					+ (crossesBody ? 30.0d : 0.0d)
					+ (lastArm != null && lastArm.left() == left && lastArm.slot() == slot ? 18.0d : 0.0d)
					- (weapon ? 12.0d : 0.0d) + slot * 1.0e-3d;
				SlimeBionicEntity.ArmCandidate candidate = new SlimeBionicEntity.ArmCandidate(left, slot, weapon, arm, aim, score);
				if (best == null || candidate.score() < best.score())
					best = candidate;
			}
		}
		return best == null ? null : new SlimeBionicEntity.ArmSelection(best, Math.max(1, cadenceArms));
	}

	static int startAttackAction(SlimeBionicEntity operationOwner, SlimeBionicEntity.ArmCandidate selected, int interval) {
		operationOwner.attackActionInterval = interval;
		operationOwner.attackActionLeft = selected.left();
		operationOwner.attackActionArmSlot = selected.slot();
		operationOwner.attackActionWeapon = selected.weapon();
		operationOwner.attackActionHasElbow = selected.arm().hasElbow();
		operationOwner.attackActionDuration = SlimeBionicCombat.duration(interval);
		operationOwner.attackActionTick = operationOwner.attackActionDuration;
		operationOwner.attackBodyYaw = BionicMeleeController.combatBodyYaw(operationOwner);
		operationOwner.attackActionSequence++;
		BionicMeleeController.setAttackAim(operationOwner, selected.aim());
		CBPackets.sendToTrackingEntity(SlimeBionicAttackActionPacket.start(operationOwner), operationOwner);
		return operationOwner.attackActionDuration;
	}

	static void setAttackAim(SlimeBionicEntity operationOwner, Vec3 aim) {
		operationOwner.attackAimYaw = SlimeBionicCombat.yaw(aim);
		operationOwner.attackAimPitch = SlimeBionicCombat.pitch(aim);
	}

	static void synchronizeAttackAim(SlimeBionicEntity operationOwner, Vec3 aim, float bodyYaw, int remainingTicks) {
		BionicMeleeController.setAttackAim(operationOwner, aim);
		operationOwner.attackBodyYaw = bodyYaw;
		operationOwner.attackActionTick = remainingTicks;
		CBPackets.sendToTrackingEntity(SlimeBionicAttackActionPacket.aim(operationOwner), operationOwner);
	}

	/** Shared across server and clients; see {@link #authoritativeBodyBounds()}. */
	static @Nullable
	SurgicalAssembly.HitboxGeometry authoritativeHitboxGeometry(SlimeBionicEntity operationOwner) {
		SurgicalAssembly assembly = operationOwner.getAssembly();
		return assembly == null ? null : assembly.hitboxGeometry();
	}

	/** Render-only envelope: this client's own measurement when it has one. */
	static @Nullable
	SurgicalAssembly.HitboxGeometry renderHitboxGeometry(SlimeBionicEntity operationOwner) {
		SurgicalAssembly assembly = operationOwner.getAssembly();
		return operationOwner.level().isClientSide && operationOwner.clientBoundsAssembly == assembly && operationOwner.clientHitboxGeometry != null
			? operationOwner.clientHitboxGeometry : BionicMeleeController.authoritativeHitboxGeometry(operationOwner);
	}

	static boolean armHasWeapon(SlimeBionicEntity operationOwner, boolean left, int slot) {
		SurgicalAssembly assembly = operationOwner.getAssembly();
		var mainHandArm = operationOwner.equipment.mainHandArm();
		return SlimeBionicEntity.isAttackWeapon(operationOwner.getMainHandItem()) && assembly != null && assembly.attackGeometry() != null
			&& mainHandArm != null && mainHandArm == assembly.attackGeometry().arm(left, slot);
	}

	/** Applies one logical attack hit without touching its independent presentation clock. */
	static boolean performAttackDamage(SlimeBionicEntity operationOwner, Entity target, double damageMultiplier) {
		boolean replaceMainHand = !BionicMeleeController.attackUsesMainHand(operationOwner);
		HashMultimap<Holder<Attribute>, AttributeModifier> mainHandModifiers = replaceMainHand
			? BionicMeleeController.combatModifiers(operationOwner.getMainHandItem()) : null;
		HashMultimap<Holder<Attribute>, AttributeModifier> selectedModifiers = replaceMainHand
			? BionicMeleeController.combatModifiers(BionicMeleeController.selectedAttackWeapon(operationOwner)) : null;
		if (replaceMainHand) {
			operationOwner.getAttributes().removeAttributeModifiers(mainHandModifiers);
			operationOwner.getAttributes().addTransientAttributeModifiers(selectedModifiers);
		}
		var attackDamage = operationOwner.getAttribute(Attributes.ATTACK_DAMAGE);
		try {
			if (attackDamage == null || Math.abs(damageMultiplier - 1.0d) < 1.0e-8d)
				return operationOwner.doSelectedAttack(target);
			attackDamage.removeModifier(SlimeBionicEntity.ANATOMICAL_ATTACK_MODIFIER_ID);
			attackDamage.addTransientModifier(new AttributeModifier(SlimeBionicEntity.ANATOMICAL_ATTACK_MODIFIER_ID,
				damageMultiplier - 1.0d, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			try {
				return operationOwner.doSelectedAttack(target);
			} finally {
				attackDamage.removeModifier(SlimeBionicEntity.ANATOMICAL_ATTACK_MODIFIER_ID);
			}
		} finally {
			if (replaceMainHand) {
				operationOwner.getAttributes().removeAttributeModifiers(selectedModifiers);
				operationOwner.getAttributes().addTransientAttributeModifiers(mainHandModifiers);
			}
		}
	}

	/** The correction follows the entire installed shoulder/elbow chain, including mixed donors. */
	static double armDamageOffset(SlimeBionicEntity operationOwner, @Nullable SurgicalAssembly.ArmAttackGeometry arm) {
		SurgicalAssembly assembly = operationOwner.getAssembly();
		return assembly == null || arm == null || arm.sourceId() < 0 ? 0.0d
			: operationOwner.getBionicTraits().armValue(BionicTrait.DAMAGE_OFFSET,
				assembly.rotatingGroup(arm.sourceId(), arm.cubeId()));
	}

	static boolean attackUsesMainHand(SlimeBionicEntity operationOwner) {
		return operationOwner.attackActionWeapon && BionicMeleeController.armHasWeapon(operationOwner, operationOwner.attackActionLeft, operationOwner.attackActionArmSlot);
	}

	static ItemStack selectedAttackWeapon(SlimeBionicEntity operationOwner) {
		return BionicMeleeController.attackUsesMainHand(operationOwner) ? operationOwner.getMainHandItem() : ItemStack.EMPTY;
	}

	static HashMultimap<Holder<Attribute>, AttributeModifier> combatModifiers(ItemStack stack) {
		HashMultimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create();
		if (stack == null || stack.isEmpty())
			return modifiers;
		stack.forEachModifier(EquipmentSlot.MAINHAND, modifiers::put);
		return modifiers;
	}

	static boolean hasAttackLineOfSight(SlimeBionicEntity operationOwner, LivingEntity target, SurgicalAssembly.ArmAttackGeometry arm,
		float bodyYaw) {
		Vec3 origin = SlimeBionicCombat.worldOrigin(operationOwner.position(), bodyYaw, arm);
		Vec3 targetPoint = SlimeBionicCombat.aimPoint(target.getBoundingBox(), origin);
		if (origin.distanceToSqr(targetPoint) < 1.0e-8d)
			return true;
		return operationOwner.level().clip(new ClipContext(origin, targetPoint, ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, operationOwner)).getType() == HitResult.Type.MISS;
	}
}
