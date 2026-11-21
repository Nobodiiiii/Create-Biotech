package com.nobodiiiii.createbiotech.entity;

import java.util.EnumSet;

import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** A crush requires a ground launch, clearance above the prey and descending contact. */
public final class DevourAttackGoal extends Goal {
	private static final double MAX_JUMP_HEIGHT = 4;
	private final Mob hunter;
	private LivingEntity prey;
	private int flightTicks;
	private long lastContact;
	private long retryAt;
	private boolean running;
	private double ceiling;
	private double previousBottom;
	private float crushDamage;
	private boolean clearedPrey;

	DevourAttackGoal(Mob hunter) {
		this.hunter = hunter;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	static int size(Mob hunter) {
		if (hunter instanceof Slime slime) return slime.getSize();
		var traits = ((SlimeBionicEntity) hunter).getBionicTraits();
		return traits.has(BionicTrait.DEVOUR) ? (int) traits.value(BionicTrait.DEVOUR) : 0;
	}

	static boolean isPrey(Mob hunter, LivingEntity target, int size) {
		if (size <= 0 || protectedPrey(target) || target == hunter
			|| !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target) || hunter.isAlliedTo(target)) return false;
		double edge = EntityType.SLIME.getWidth() * size * hunter.getScale();
		var box = target.getBoundingBox();
		return box.getXsize() * box.getYsize() * box.getZsize() <= edge * edge * edge + 1e-6;
	}

	public static boolean protectedPrey(Entity entity) {
		return entity.getType() == EntityType.SLIME || entity instanceof DevourerEntity
			|| entity instanceof SlimeBionicEntity || entity instanceof PlayerMimicEntity
			|| SlimeMimicHandler.isSlimeMimic(entity) || SlimeMimicHandler.isHauntedMimic(entity);
	}

	/** Only a normal, in-range contact attack can be replaced. The vanilla pursuit goals keep running until then. */
	boolean replaceContact(LivingEntity target, float damage) {
		if (running) return true;
		if (!(hunter.level() instanceof ServerLevel level) || !isPrey(hunter, target, size(hunter))
			|| !hunter.canAttack(target) || !hunter.hasLineOfSight(target)
			|| target.getBoundingBox().maxY - hunter.getY() + 0.25 > MAX_JUMP_HEIGHT
			|| target.getHealth() >= estimatedDamage(level, target, hunter.damageSources().mobAttack(hunter), damage)) return false;
		prey = target;
		lastContact = level.getGameTime();
		crushDamage = 2 * damage;
		return true;
	}

	@Override public boolean canUse() {
		return hunter.level().getGameTime() >= retryAt && hunter.level().getGameTime() - lastContact <= 10
			&& validPrey() && hunter.isWithinMeleeAttackRange(prey) && hunter.hasLineOfSight(prey);
	}

	private boolean validPrey() {
		return prey != null && prey.isAlive() && hunter.isAlive() && !hunter.isPassenger() && !hunter.hasControllingPassenger()
			&& hunter.canAttack(prey) && isPrey(hunter, prey, size(hunter));
	}

	@Override public boolean canContinueToUse() { return running && validPrey(); }
	@Override public boolean requiresUpdateEveryTick() { return true; }

	@Override public void start() {
		running = true;
		flightTicks = 0;
		hunter.getNavigation().stop();
		if (hunter instanceof SlimeBionicEntity bionic) bionic.setDevourGroundAttack(true);
	}

	@Override public void stop() {
		running = false;
		flightTicks = 0;
		prey = null;
		if (hunter instanceof SlimeBionicEntity bionic) bionic.setDevourGroundAttack(false);
	}

	@Override public void tick() {
		if (!(hunter.level() instanceof ServerLevel level) || !running) return;
		if (flightTicks > 0) {
			descend(level);
			return;
		}
		if (level.getGameTime() - lastContact > 40 || !hunter.isWithinMeleeAttackRange(prey) || !hunter.hasLineOfSight(prey)) {
			running = false;
			return;
		}
		if (!hunter.onGround()) return;
		double rise = prey.getBoundingBox().maxY - hunter.getY() + 0.25;
		if (rise > MAX_JUMP_HEIGHT) {
			running = false;
			return;
		}
		previousBottom = hunter.getY();
		ceiling = previousBottom + MAX_JUMP_HEIGHT;
		flightTicks = 1;
		clearedPrey = false;
		Vec3 toward = prey.position().subtract(hunter.position()).multiply(0.15, 0, 0.15);
		hunter.setDeltaMovement(toward.add(0, Math.sqrt(2 * 0.08 * Math.max(0.8, rise)) + 0.12, 0));
		hunter.hasImpulse = true;
	}

	private void descend(ServerLevel level) {
		var movement = hunter.getDeltaMovement();
		var box = prey.getBoundingBox();
		double top = box.maxY;
		double y = hunter.getY();
		boolean over = hunter.getBoundingBox().maxX > box.minX && hunter.getBoundingBox().minX < box.maxX
			&& hunter.getBoundingBox().maxZ > box.minZ && hunter.getBoundingBox().minZ < box.maxZ;
		if (y >= top && over) clearedPrey = true;
		if (clearedPrey && movement.y <= 0 && previousBottom >= top - 0.15 && y <= top + 0.08
			&& over && hunter.hasLineOfSight(prey)) {
			hit(level, crushDamage);
			running = false;
			return;
		}
		if (++flightTicks > 45 || flightTicks > 3 && hunter.onGround()) {
			running = false;
			retryAt = level.getGameTime() + 10;
			return;
		}
		previousBottom = y;
		Vec3 offset = prey.position().subtract(hunter.position());
		hunter.setDeltaMovement(Mth.clamp(offset.x * 0.3, -0.32, 0.32),
			Math.min(movement.y, ceiling - y), Mth.clamp(offset.z * 0.3, -0.32, 0.32));
		hunter.resetFallDistance();
	}

	private void hit(ServerLevel level, float damage) {
		var source = hunter.damageSources().mobAttack(hunter);
		if (!prey.hurt(source, damage)) return;
		hunter.playSound(SoundEvents.SLIME_ATTACK);
		EnchantmentHelper.doPostAttackEffects(level, prey, source);
		if (prey.isAlive() && hunter instanceof SlimeBionicEntity bionic) bionic.afterDevourHit(prey);
	}

	static float estimatedDamage(ServerLevel level, LivingEntity target, DamageSource source, float damage) {
		if (target.isInvulnerableTo(source) || target.isDamageSourceBlocked(source)) return 0;
		if (target instanceof Player player)
			damage = Math.max(0, source.type().scaling().getScalingFunction().scaleDamage(source, player, damage, level.getDifficulty()));
		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) damage = CombatRules.getDamageAfterAbsorb(target, damage, source, target.getArmorValue(),
			(float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		var resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
		if (resistance != null && !source.is(DamageTypeTags.BYPASSES_EFFECTS) && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) damage *= Math.max(0, 1 - (resistance.getAmplifier() + 1) * 0.2f);
		if (!source.is(DamageTypeTags.BYPASSES_EFFECTS) && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS))
			damage = CombatRules.getDamageAfterMagicAbsorb(damage, EnchantmentHelper.getDamageProtection(level, target, source));
		return Math.max(0, damage - target.getAbsorptionAmount());
	}
}
