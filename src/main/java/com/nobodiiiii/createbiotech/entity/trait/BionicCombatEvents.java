package com.nobodiiiii.createbiotech.entity.trait;

import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeCompanionHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Native projectiles retain their vanilla behavior while respecting the assembled owner's allies. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class BionicCombatEvents {
	private static final String MARK = "BionicAttack";
	private BionicCombatEvents() {}
	public static void mark(Entity attack, SlimeBionicEntity owner) {
		CompoundTag tag = new CompoundTag(); tag.putUUID("Source", owner.getUUID());
		if (owner.getBionicOwnerId() != null) tag.putUUID("Owner", owner.getBionicOwnerId());
		if (owner.getTeam() != null) tag.putString("Team", owner.getTeam().getName());
		attack.getPersistentData().put(MARK, tag);
	}
	@Nullable private static Entity owner(@Nullable Entity attack) {
		return attack instanceof Projectile projectile ? projectile.getOwner()
			: attack instanceof AreaEffectCloud cloud ? cloud.getOwner()
			: attack instanceof EvokerFangs fangs ? fangs.getOwner() : attack;
	}
	static boolean protectedFrom(@Nullable Entity attack, Entity victim) {
		if (attack == null) return false;
		Entity owner = owner(attack);
		if (owner instanceof SlimeBionicEntity bionic) return victim == bionic || bionic.isAlliedTo(victim);
		// A deflected fireball follows its new owner's allegiance.
		if (attack instanceof Projectile && owner != null) return false;
		CompoundTag tag = attack.getPersistentData().getCompound(MARK);
		if (!tag.hasUUID("Source")) return false;
		if (tag.getUUID("Source").equals(victim.getUUID())) return true;
		if (tag.hasUUID("Owner")) {
			var id = tag.getUUID("Owner");
			if (id.equals(victim.getUUID()) || victim instanceof SlimeBionicEntity bionic && id.equals(bionic.getBionicOwnerId())
				|| victim instanceof TamableAnimal animal && id.equals(animal.getOwnerUUID()) || id.equals(SlimeCompanionHandler.ownerOf(victim))) return true;
		}
		return victim.getTeam() != null && victim.getTeam().getName().equals(tag.getString("Team"));
	}
	@SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide) return;
		Entity attack = event.getEntity();
		if (!(owner(attack) instanceof SlimeBionicEntity bionic) || attack == bionic) return;
		mark(attack, bionic);
		if (attack instanceof AreaEffectCloud cloud && !(cloud instanceof BionicAreaEffectCloud)) {
			BionicAreaEffectCloud replacement = com.nobodiiiii.createbiotech.registry.CBEntityTypes.BIONIC_EFFECT_CLOUD.get().create(event.getLevel());
			if (replacement != null) {
				replacement.load(cloud.saveWithoutId(new CompoundTag()));
				replacement.setOwner(bionic);
				event.setCanceled(true);
				event.getLevel().addFreshEntity(replacement);
			}
		}
	}
	@SubscribeEvent public static void impact(ProjectileImpactEvent event) {
		if (event.getRayTraceResult() instanceof EntityHitResult hit && protectedFrom(event.getProjectile(), hit.getEntity())) {
			event.setCanceled(true);
			return;
		}
		if (!(event.getProjectile() instanceof ThrownPotion potion) || potion.level().isClientSide
			|| !(owner(potion) instanceof SlimeBionicEntity) && !potion.getPersistentData().contains(MARK)
			|| !potion.getItem().is(net.minecraft.world.item.Items.SPLASH_POTION)) return;
		var contents = potion.getItem().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
			net.minecraft.world.item.alchemy.PotionContents.EMPTY);
		if (!contents.hasEffects()) return;
		Entity directHit = event.getRayTraceResult() instanceof EntityHitResult hit ? hit.getEntity() : null;
		for (LivingEntity victim : potion.level().getEntitiesOfClass(LivingEntity.class, potion.getBoundingBox().inflate(4, 2, 4))) {
			if (!victim.isAffectedByPotions() || protectedFrom(potion, victim)) continue;
			double distance = potion.distanceToSqr(victim);
			if (distance >= 16) continue;
			double strength = victim == directHit ? 1 : 1 - Math.sqrt(distance) / 4;
			for (var effect : contents.getAllEffects()) {
				if (effect.getEffect().value().isInstantenous())
					effect.getEffect().value().applyInstantenousEffect(potion, potion.getOwner(), victim, effect.getAmplifier(), strength);
				else {
					var applied = new net.minecraft.world.effect.MobEffectInstance(effect.getEffect(),
						effect.mapDuration(duration -> (int) (strength * duration + 0.5)), effect.getAmplifier(), effect.isAmbient(), effect.isVisible());
					if (!applied.endsWithin(20)) victim.addEffect(applied, potion);
				}
			}
		}
		boolean instant = false;
		for (var effect : contents.getAllEffects()) instant |= effect.getEffect().value().isInstantenous();
		potion.level().levelEvent(instant ? 2007 : 2002, potion.blockPosition(), contents.getColor());
		potion.gameEvent(net.minecraft.world.level.gameevent.GameEvent.PROJECTILE_LAND);
		potion.discard();
		event.setCanceled(true);
	}
	@SubscribeEvent public static void damage(LivingIncomingDamageEvent event) {
		if (protectedFrom(event.getSource().getDirectEntity(), event.getEntity()) || protectedFrom(event.getSource().getEntity(), event.getEntity())) event.setCanceled(true);
	}
	@SubscribeEvent public static void effect(MobEffectEvent.Applicable event) {
		if (protectedFrom(event.getEffectSource(), event.getEntity())) event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
	}
	@SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
		Entity attack = event.getExplosion().getDirectSourceEntity();
		event.getAffectedEntities().removeIf(victim -> protectedFrom(attack, victim));
		if (owner(attack) instanceof SlimeBionicEntity bionic && !EventHooks.canEntityGrief(event.getLevel(), bionic)) event.getAffectedBlocks().clear();
	}
	@SubscribeEvent public static void bottle(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
		if (!event.getItemStack().is(net.minecraft.world.item.Items.GLASS_BOTTLE)) return;
		var player = event.getEntity();
		var clouds = event.getLevel().getEntitiesOfClass(AreaEffectCloud.class, player.getBoundingBox().inflate(2),
			cloud -> (cloud instanceof BionicAreaEffectCloud || cloud.getPersistentData().contains(MARK)) && cloud.getParticle() == net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH
				&& cloud.getRadius() >= 0.5f);
		if (clouds.isEmpty()) return;
		if (!event.getLevel().isClientSide) {
			var cloud = clouds.getFirst(); cloud.setRadius(cloud.getRadius() - 0.5f);
			player.setItemInHand(event.getHand(), net.minecraft.world.item.ItemUtils.createFilledResult(event.getItemStack(), player,
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DRAGON_BREATH)));
			player.playSound(net.minecraft.sounds.SoundEvents.BOTTLE_FILL_DRAGONBREATH);
		}
		event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(event.getLevel().isClientSide));
		event.setCanceled(true);
	}
}
