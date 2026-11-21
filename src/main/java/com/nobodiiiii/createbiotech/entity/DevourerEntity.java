package com.nobodiiiii.createbiotech.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class DevourerEntity extends Slime {
	private DevourAttackGoal devour;
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

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (!tag.contains(SlimeMimicHandler.SLIME_MIMIC_TAG) && !SlimeMimicHandler.isHauntedMimic(this))
			SlimeMimicHandler.forceSetSlimeMimic(this, true);
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

	@Override
	public boolean isAlliedTo(Entity other) {
		return DevourAttackGoal.protectedPrey(other) || super.isAlliedTo(other);
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return super.canAttack(target) && DevourAttackGoal.isPrey(this, target, getSize());
	}

	@Override
	protected void dealDamage(LivingEntity target) {
		if (isEffectiveAi() && canAttack(target) && isWithinMeleeAttackRange(target) && hasLineOfSight(target)
			&& !devour.replaceContact(target, getAttackDamage()) && isDealsDamage())
			super.dealDamage(target);
	}

	// Slime handles players and iron golems itself. Extend the same contact path to other prey.
	@Override
	protected void doPush(Entity other) {
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
