package com.nobodiiiii.createbiotech.entity;

import com.nobodiiiii.createbiotech.entity.trait.DevourAnatomy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class DevourerEntity extends Slime {
	private static final String CONTACT_DAMAGE_MIGRATED = "DevourContactDamageMigrated";
	static final EntityDataAccessor<Optional<UUID>> DEVOURED_PLAYER = SynchedEntityData.defineId(
		DevourerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	DevourAttackGoal devour;
	private static final Set<Slime> NATURAL_CANDIDATES =
		Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
		if (event.getSpawnType() == MobSpawnType.NATURAL && event.getEntity().getType() == EntityType.SLIME
			&& event.getEntity() instanceof Slime slime && slime.getRandom().nextInt(10) == 0)
			NATURAL_CANDIDATES.add(slime);
	}

	public DevourerEntity(EntityType<? extends Slime> type, Level level) {
		super(type, level);
		setSize(1, true);
		SlimeMimicHandler.forceSetSlimeMimic(this, true);
	}

	public static double maxHealthForSize(int size) { return 2.0d * size * size; }

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DEVOURED_PLAYER, Optional.empty());
	}

	@Override
	public void tick() {
		super.tick();
		if (devour != null) devour.swallowedPlayer.tick();
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction move) {
		if (!DevouredPlayer.positionInside(this, passenger, move)) super.positionRider(passenger, move);
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (devour != null) devour.swallowedPlayer.onDismount(passenger);
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		Vec3 safe = BionicRiding.safeDismount(this, passenger);
		return safe != null ? safe : super.getDismountLocationForPassenger(passenger);
	}

	@Override
	public void setSize(int size, boolean heal) {
		super.setSize(size, false);
		int actualSize = getSize();
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealthForSize(actualSize));
		if (heal) setHealth(getMaxHealth());
	}

	@Override
	protected float getAttackDamage() {
		return (float) DevourAnatomy.nativeContactDamage(this, super.getAttackDamage());
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean(CONTACT_DAMAGE_MIGRATED, true);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		restoreLegacyBaseAttributes(tag);
		if (!tag.contains("Health", Tag.TAG_ANY_NUMERIC)) setHealth(getMaxHealth());
		if (!tag.contains(SlimeMimicHandler.SLIME_MIMIC_TAG) && !SlimeMimicHandler.isHauntedMimic(this))
			SlimeMimicHandler.forceSetSlimeMimic(this, true);
	}

	private void restoreLegacyBaseAttributes(CompoundTag tag) {
		// LivingEntity restores saved attribute bases after Slime has applied its size.
		int size = getSize();
		var health = getAttribute(Attributes.MAX_HEALTH);
		double savedBase = health.getBaseValue();
		if (savedBase == size * size) {
			float fraction = getHealth() / getMaxHealth();
			health.setBaseValue(maxHealthForSize(size));
			setHealth(getMaxHealth() * fraction);
		} else if (savedBase == 4.0d * size * size) {
			health.setBaseValue(maxHealthForSize(size));
			if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
		}
		var attack = getAttribute(Attributes.ATTACK_DAMAGE);
		// Older devourers stored the contact bonus in their base attack attribute.
		if (!tag.getBoolean(CONTACT_DAMAGE_MIGRATED) && attack.getBaseValue() == 2.0 * size)
			attack.setBaseValue(size);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		targetSelector.removeAllGoals(goal -> true);
		targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
			target -> !(target instanceof Player) || Math.abs(target.getY() - getY()) <= 4));
		devour = new DevourAttackGoal(this);
		goalSelector.addGoal(0, devour);
	}

	int walkingJumpDelay() {
		return getMoveControl() instanceof SlimeMoveControl control ? control.jumpDelay : 0;
	}

	boolean waitForWalkingJump() {
		if (!(getMoveControl() instanceof SlimeMoveControl control) || control.jumpDelay <= 0) return false;
		// SlimeKeepOnJumpingGoal requests movement on full AI ticks. Floating requests it every tick.
		if (tickCount <= 1 || (tickCount + getId()) % 2 == 0 || isInWater() || isInLava())
			control.setWantedMovement(1.0);
		return true;
	}

	void resetWalkingJumpDelay() {
		if (getMoveControl() instanceof SlimeMoveControl control) {
			int delay = getJumpDelay();
			control.jumpDelay = control.isAggressive ? delay / 3 : delay;
		}
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return DevourAttackGoal.protectedPrey(other) || super.isAlliedTo(other);
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return super.canAttack(target) && DevourAttackGoal.isPrey(this, target, getSize());
	}

	@Override
	protected boolean isDealsDamage() {
		return super.isDealsDamage() || isEffectiveAi() && !DevourGrowth.canDevour(this);
	}

	@Override
	protected void dealDamage(LivingEntity target) {
		if (isEffectiveAi() && canAttack(target) && isWithinMeleeAttackRange(target) && hasLineOfSight(target)
			&& !devour.replaceContact(target, getAttackDamage()) && isDealsDamage())
			super.dealDamage(target);
	}

	@Override
	public void push(Entity other) {
		if (devour == null || !devour.pressing(other)) super.push(other);
	}

	// Slime handles players and iron golems itself. Extend the same contact path to other prey.
	@Override
	protected void doPush(Entity other) {
		if (devour != null && devour.pressing(other)) return;
		super.doPush(other);
		if (other instanceof LivingEntity target && !(target instanceof Player)
			&& !(target instanceof IronGolem)) dealDamage(target);
	}

	/** Share the successful slime spawn itself, including datapack/mod placement and biome changes. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onNaturalSlime(EntityJoinLevelEvent event) {
		if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)
			|| event.getEntity().getType() != EntityType.SLIME || !(event.getEntity() instanceof Slime slime)
			|| !NATURAL_CANDIDATES.remove(slime)) return;
		DevourerEntity devourer = CBEntityTypes.DEVOURER.get().create(level);
		if (devourer == null) return;
		devourer.moveTo(slime.getX(), slime.getY(), slime.getZ(), slime.getYRot(), slime.getXRot());
		devourer.setSize(slime.getSize(), true);
		if (level.addFreshEntity(devourer)) event.setCanceled(true);
	}
}
