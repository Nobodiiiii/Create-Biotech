package com.nobodiiiii.createbiotech.entity.trait;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/** Damage and teleport hooks; all side effects execute on the logical server. */
public final class BionicDefenses {
	private BionicDefenses() {}

	public static boolean avoids(SlimeBionicEntity mob, DamageSource source) {
		var traits = mob.getBionicTraits();
		var direct = source.getDirectEntity();
		if (traits.has(BionicTrait.WITHER_SHIELD) && mob.getHealth() <= mob.getMaxHealth() * 0.5f
			&& (direct instanceof AbstractArrow || direct != null && (direct.getType() == EntityType.WIND_CHARGE
				|| direct.getType() == EntityType.BREEZE_WIND_CHARGE))) return true;
		if (traits.hasVariant(BionicTrait.ENDER_TELEPORT, "enderman")
			&& (source.is(DamageTypeTags.IS_PROJECTILE) || direct instanceof ThrownPotion)) {
			for (int attempt = 0; attempt < 64; attempt++)
				if (randomTeleport(mob, 32)) return true;
		}
		return false;
	}

	public static float damage(SlimeBionicEntity mob, DamageSource source, float amount) {
		var traits = mob.getBionicTraits();
		if (traits.has(BionicTrait.ENCHANT_VULNERABILITY))
			for (var variant : traits.result(BionicTrait.ENCHANT_VULNERABILITY).subtraits().keySet())
				amount += enchantmentLevel(mob, source, variant.id()) * (float) variant.parameter("damage_per_level", 2.5);
		if (source.is(DamageTypeTags.WITCH_RESISTANT_TO))
			amount *= (float) (1 - traits.strength(BionicTrait.MAGIC_RESISTANCE)
				* traits.parameter(BionicTrait.MAGIC_RESISTANCE, "reduction_multiplier", 0.85));
		return amount;
	}

	private static int enchantmentLevel(SlimeBionicEntity mob, DamageSource source, ResourceLocation id) {
		// Vanilla already applied enchantments for a type carrying the actual sensitivity tag.
		if (mob.getType().is(TagKey.create(Registries.ENTITY_TYPE,
			ResourceLocation.withDefaultNamespace("sensitive_to_" + id.getPath())))) return 0;
		boolean melee = source.getDirectEntity() instanceof LivingEntity && (source.is(DamageTypeTags.IS_PLAYER_ATTACK)
			|| source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO));
		if (!melee && !(source.getDirectEntity() instanceof AbstractArrow && source.is(DamageTypeTags.IS_PROJECTILE))) return 0;
		ItemStack weapon = source.getWeaponItem();
		if (weapon == null || weapon.isEmpty()) return 0;
		var enchantment = mob.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(ResourceKey.create(Registries.ENCHANTMENT, id));
		return enchantment.map(holder -> weapon.getEnchantmentLevel(holder)).orElse(0);
	}

	public static void afterHurt(SlimeBionicEntity mob, DamageSource source) {
		var traits = mob.getBionicTraits();
		if (source.getDirectEntity() instanceof LivingEntity
			&& traits.hasVariant(BionicTrait.ENCHANT_VULNERABILITY, "bane_of_arthropods")) {
			int level = enchantmentLevel(mob, source, ResourceLocation.withDefaultNamespace("bane_of_arthropods"));
			if (level > 0) mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
				30 + mob.getRandom().nextInt(10 * (level - 1) + 1), 3), source.getEntity());
		}
		if (!mob.isAlive()) return;
		if (traits.hasVariant(BionicTrait.ENDER_TELEPORT, "enderman")
			&& !(source.getEntity() instanceof LivingEntity) && mob.getRandom().nextFloat() < 0.9f)
			randomTeleport(mob, 32);
		if (traits.hasVariant(BionicTrait.ENDER_TELEPORT, "shulker")
			&& mob.getHealth() < mob.getMaxHealth() * 0.5f && mob.getRandom().nextInt(4) == 0)
			for (int attempt = 0; attempt < 5; attempt++) if (randomTeleport(mob, 8)) break;
	}

	public static void tick(SlimeBionicEntity mob) {
		if (mob.tickCount % 30 != 0 || !mob.getBionicTraits().hasVariant(BionicTrait.ENDER_TELEPORT, "enderman")) return;
		LivingEntity target = mob.getTarget();
		if (target == null || !mob.canAttack(target) || mob.distanceToSqr(target) <= 256) return;
		Vec3 direction = target.position().subtract(mob.position()).normalize().scale(16);
		teleport(mob, mob.getX() + direction.x + (mob.getRandom().nextDouble() - 0.5) * 8,
			mob.getY() + direction.y + mob.getRandom().nextInt(16) - 8,
			mob.getZ() + direction.z + (mob.getRandom().nextDouble() - 0.5) * 8);
	}

	private static boolean randomTeleport(SlimeBionicEntity mob, int radius) {
		return teleport(mob, mob.getX() + (mob.getRandom().nextDouble() - 0.5) * radius * 2,
			mob.getY() + mob.getRandom().nextInt(radius * 2) - radius,
			mob.getZ() + (mob.getRandom().nextDouble() - 0.5) * radius * 2);
	}

	private static boolean teleport(SlimeBionicEntity mob, double x, double y, double z) {
		if (mob.level().isClientSide || !mob.isAlive() || mob.isOrderedToSit() || mob.isLeashed()
			|| mob.isPassenger() || mob.isVehicle()) return false;
		var event = NeoForge.EVENT_BUS.post(new EntityTeleportEvent.EnderEntity(mob, x, y, z));
		if (event.isCanceled()) return false;
		x = event.getTargetX(); y = event.getTargetY(); z = event.getTargetZ();
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
			|| !mob.level().isInWorldBounds(BlockPos.containing(x, y, z))
			|| !mob.level().getWorldBorder().isWithinBounds(mob.getBoundingBox().move(x - mob.getX(), y - mob.getY(), z - mob.getZ()))) return false;
		Vec3 previous = mob.position();
		if (!mob.randomTeleport(x, y, z, true)) return false;
		mob.resetFallDistance();
		mob.level().gameEvent(GameEvent.TELEPORT, previous, GameEvent.Context.of(mob));
		mob.level().playSound(null, previous.x, previous.y, previous.z, SoundEvents.ENDERMAN_TELEPORT, mob.getSoundSource(), 1, 1);
		mob.playSound(SoundEvents.ENDERMAN_TELEPORT, 1, 1);
		return true;
	}
}
