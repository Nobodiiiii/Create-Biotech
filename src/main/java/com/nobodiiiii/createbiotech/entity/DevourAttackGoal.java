package com.nobodiiiii.createbiotech.entity;

import java.util.EnumSet;

import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/** A crush requires a ground launch, clearance above the prey and descending contact. */
public final class DevourAttackGoal extends Goal {
	private static final double MAX_JUMP_HEIGHT = 4;
	private static final double LAUNCH_REACH = 3.0;
	private static final double MIN_CLEARANCE = 0.25;
	private static final double MAX_HORIZONTAL_SPEED = 0.8;
	private static final int ASCENT_TICKS = 8;
	private static final float AIR_VERTICAL_DRAG = 0.98F;
	private final Mob hunter;
	final DevouredPlayer swallowedPlayer;
	private LivingEntity prey;
	private int flightTicks;
	private long reservedUntil;
	private long groundDeadline;
	private long retryAt;
	private boolean running;
	private double takeoffY;
	private double apexY;
	private double previousBottom;
	private double airFallVelocity;
	private float crushDamage;
	private boolean clearedPrey;
	private boolean diving;

	DevourAttackGoal(Mob hunter) {
		this.hunter = hunter;
		swallowedPlayer = new DevouredPlayer(hunter);
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
		if (target instanceof Player && (!CBConfigs.SERVER.devourer.canHuntPlayers(size) || DevouredPlayer.isInside(hunter, target)))
			return false;
		double edge = EntityType.SLIME.getWidth() * size * hunter.getScale();
		var box = target.getBoundingBox();
		return box.getXsize() * box.getYsize() * box.getZsize() <= edge * edge * edge + 1e-6;
	}

	public static boolean protectedPrey(Entity entity) {
		return entity.getType() == EntityType.SLIME || entity instanceof DevourerEntity
			|| entity instanceof SlimeBionicEntity || entity instanceof PlayerMimicEntity
			|| SlimeMimicHandler.isSlimeMimic(entity) || SlimeMimicHandler.isHauntedMimic(entity);
	}

	/** A contact or arm swing can supply its actual damage before the next goal-selector pass. */
	boolean replaceContact(LivingEntity target, float damage) {
		if (running) return isCrushing();
		if (!(hunter.level() instanceof ServerLevel level) || !available(level)
			|| !canLaunchAt(level, target, damage)) return false;
		if (prey == null) reservedUntil = level.getGameTime() + 4;
		if (prey != null && prey != target) return false;
		prey = target;
		crushDamage = 2 * damage;
		return true;
	}

	private boolean available(ServerLevel level) {
		long now = level.getGameTime();
		if (prey != null && now > reservedUntil) fail(level);
		return now >= retryAt;
	}

	private boolean validPrey(LivingEntity target) {
		return target != null && target.isAlive() && hunter.isAlive() && target.level() == hunter.level()
			&& !hunter.isPassenger() && !hunter.hasControllingPassenger()
			&& !swallowedPlayer.hasCaptive() && isPrey(hunter, target, size(hunter)) && hunter.canAttack(target)
			&& (!(target instanceof ServerPlayer player) || swallowedPlayer.canSwallow(player)) && hasCapacity(target);
	}

	private boolean hasCapacity(LivingEntity target) {
		return target instanceof ServerPlayer ? DevourGrowth.hasGrowthCapacity(hunter) : DevourGrowth.canDevour(hunter);
	}

	private boolean canLaunchAt(ServerLevel level, LivingEntity target, float damage) {
		return damage > 0 && inLaunchRange(hunter.getBoundingBox(), target.getBoundingBox())
			&& validPrey(target) && canCrush(level, target, damage) && hunter.hasLineOfSight(target);
	}

	private boolean canCrush(ServerLevel level, LivingEntity target, float damage) {
		// The impact holds players alive. Ordinary prey still need to be weakened before a crush.
		return target instanceof ServerPlayer || target.getHealth() <= damage
			&& target.getHealth() <= estimatedDamage(level, target, hunter.damageSources().mobAttack(hunter), damage);
	}

	private static boolean inLaunchRange(AABB hunter, AABB target) {
		double dx = Math.max(0, Math.max(target.minX - hunter.maxX, hunter.minX - target.maxX));
		double dz = Math.max(0, Math.max(target.minZ - hunter.maxZ, hunter.minZ - target.maxZ));
		return dx * dx + dz * dz <= LAUNCH_REACH * LAUNCH_REACH && target.maxY + MIN_CLEARANCE <= hunter.minY + MAX_JUMP_HEIGHT
			&& hunter.minY - target.maxY <= MAX_JUMP_HEIGHT;
	}

	boolean pressing(Entity other) {
		return flightTicks > 0 && other == prey && isCrushing();
	}

	boolean isCrushing() {
		return running && hasCapacity(prey);
	}

	@Override public boolean canUse() {
		if (!(hunter.level() instanceof ServerLevel level) || !available(level)) return false;
		// The selected prey can trigger a ground leap before either mob has physically touched the other.
		if (prey != null) {
			if (canLaunchAt(level, prey, crushDamage * 0.5f)) return true;
			prey = null;
		}
		var target = hunter.getTarget();
		if (target == null || !inLaunchRange(hunter.getBoundingBox(), target.getBoundingBox()) || size(hunter) <= 0) return false;
		float damage = hunter instanceof SlimeBionicEntity bionic ? bionic.devourAttackDamage()
			: ((DevourerEntity) hunter).getAttackDamage();
		return replaceContact(target, damage);
	}

	@Override public boolean canContinueToUse() { return running && validPrey(prey); }
	@Override public boolean requiresUpdateEveryTick() { return true; }

	@Override public void start() {
		running = true;
		flightTicks = 0;
		clearedPrey = false;
		diving = false;
		groundDeadline = hunter.level().getGameTime() + 20;
		hunter.getNavigation().stop();
		if (hunter instanceof SlimeBionicEntity bionic) bionic.setDevourGroundAttack(true);
	}

	@Override public void stop() {
		if (running) retryAt = hunter.level().getGameTime() + 10;
		running = false;
		flightTicks = 0;
		prey = null;
		if (hunter instanceof SlimeBionicEntity bionic) bionic.setDevourGroundAttack(false);
	}

	private void fail(ServerLevel level) {
		running = false;
		prey = null;
		retryAt = level.getGameTime() + 10;
	}

	@Override public void tick() {
		if (!(hunter.level() instanceof ServerLevel level) || !running) return;
		if (!validPrey(prey)) {
			fail(level);
			return;
		}
		var body = hunter.getBoundingBox();
		var target = prey.getBoundingBox();
		if (flightTicks == 0) {
			if (level.getGameTime() > groundDeadline || !inLaunchRange(body, target) || !hunter.hasLineOfSight(prey)) {
				fail(level);
				return;
			}
			if (!hunter.onGround()) return;
			boolean waitingForJump = hunter instanceof DevourerEntity devourer
				? devourer.waitForWalkingJump() : ((SlimeBionicEntity) hunter).waitForWalkingJump();
			if (waitingForJump) {
				// The landing deadline pauses while the grounded hunter waits for its next hop.
				groundDeadline++;
				return;
			}
			double desired = Mth.clamp(target.maxY - body.minY + 2.0, 3.0, MAX_JUMP_HEIGHT);
			double room = Shapes.collide(Direction.Axis.Y, body, level.getBlockCollisions(hunter, body.expandTowards(0, desired, 0)), desired);
			if (room < Math.max(0.8, target.maxY - body.minY + MIN_CLEARANCE)
				|| !canCrush(level, prey, crushDamage * 0.5f)) {
				fail(level);
				return;
			}
			takeoffY = previousBottom = body.minY;
			apexY = takeoffY + room;
			if (hunter instanceof DevourerEntity devourer) devourer.resetWalkingJumpDelay();
			else ((SlimeBionicEntity) hunter).resetDevourJumpDelay();
			hunter.setOnGround(false);
		} else {
			boolean over = body.maxX > target.minX && body.minX < target.maxX && body.maxZ > target.minZ && body.minZ < target.maxZ;
			if (over && body.minY >= target.maxY) clearedPrey = true;
			// Press down through the prey. A moving or airborne target can cross the impact height between ticks.
			double impactY = target.minY + Math.min(0.2, target.getYsize() * 0.25);
			if (diving && clearedPrey && over && body.minY <= previousBottom && body.minY <= target.maxY + 1e-5
				&& (hunter.onGround() && body.maxY >= target.minY || previousBottom >= impactY && body.minY <= impactY)
				&& hunter.hasLineOfSight(prey)) {
				running = false;
				hit(level, crushDamage);
				return;
			}
			if (hunter.onGround() || flightTicks >= 40) {
				fail(level);
				return;
			}
			boolean hitCeiling = hunter.verticalCollision && !hunter.verticalCollisionBelow;
			if (!diving && (flightTicks >= ASCENT_TICKS || hitCeiling)) {
				diving = true;
				airFallVelocity = hitCeiling ? 0.0 : Math.max(0.0, body.minY - previousBottom);
			}
		}

		// Each ascent step aims at a point on the arc. Gravity takes over at the apex.
		double vertical;
		if (diving) {
			// Preserve the air fall when swimming or levitation would otherwise stall the press.
			airFallVelocity = Math.min(0.0, (airFallVelocity - LivingEntity.DEFAULT_BASE_GRAVITY) * AIR_VERTICAL_DRAG);
			vertical = Math.min(hunter.getDeltaMovement().y, airFallVelocity);
		} else {
			double remaining = 1.0 - (flightTicks + 1.0) / ASCENT_TICKS;
			vertical = takeoffY + (apexY - takeoffY) * (1.0 - remaining * remaining) - body.minY;
		}
		Vec3 aim = target.getCenter().subtract(body.getCenter()).multiply(1, 0, 1);
		double steeringTicks = diving ? 2.0 : Math.clamp(ASCENT_TICKS - flightTicks, 1, 4);
		Vec3 horizontal = aim.scale(1.0 / Math.max(steeringTicks, aim.length() / MAX_HORIZONTAL_SPEED));
		previousBottom = body.minY;
		flightTicks++;
		hunter.setDeltaMovement(horizontal.x, vertical, horizontal.z);
		hunter.hasImpulse = true;
		hunter.resetFallDistance();
	}

	/** The drops event confirms a real death, after totems and death cancellation, for this hit only. */
	static final class CrushDamage extends DamageSource {
		private final LivingEntity target;
		private boolean killedTarget;

		CrushDamage(Mob hunter, LivingEntity target) {
			super(hunter.damageSources().mobAttack(hunter).typeHolder(), hunter);
			this.target = target;
		}

		void confirmDeath(LivingEntity victim) {
			if (victim == target) killedTarget = true;
		}
	}

	private void hit(ServerLevel level, float damage) {
		LivingEntity target = prey;
		if (target instanceof ServerPlayer player) {
			if (swallowedPlayer.swallow(player)) hunter.playSound(SoundEvents.SLIME_ATTACK);
			else fail(level);
			return;
		}
		var source = new CrushDamage(hunter, target);
		if (!target.hurt(source, damage)) return;
		hunter.playSound(SoundEvents.SLIME_ATTACK);
		EnchantmentHelper.doPostAttackEffects(level, target, source);
		if (target.isAlive()) {
			if (hunter instanceof SlimeBionicEntity bionic) bionic.afterDevourHit(target);
		} else if (source.killedTarget && !target.isRemoved()) {
			// As in Frog's ShootTongue, finish removal after hurt has delivered the ordinary death and loot.
			target.remove(Entity.RemovalReason.KILLED);
		}
	}

	private static float estimatedDamage(ServerLevel level, LivingEntity target, DamageSource source, float damage) {
		if (target.isInvulnerableTo(source) || target.isDamageSourceBlocked(source)) return 0;
		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) damage = CombatRules.getDamageAfterAbsorb(target, damage, source, target.getArmorValue(),
			(float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		var resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
		if (resistance != null && !source.is(DamageTypeTags.BYPASSES_EFFECTS) && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) damage *= Math.max(0, 1 - (resistance.getAmplifier() + 1) * 0.2f);
		if (!source.is(DamageTypeTags.BYPASSES_EFFECTS) && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS))
			damage = CombatRules.getDamageAfterMagicAbsorb(damage, EnchantmentHelper.getDamageProtection(level, target, source));
		return Math.max(0, damage - target.getAbsorptionAmount());
	}
}
