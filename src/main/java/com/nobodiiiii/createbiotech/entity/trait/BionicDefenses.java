package com.nobodiiiii.createbiotech.entity.trait;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** Damage and teleport hooks; all side effects execute on the logical server. */
public final class BionicDefenses {
	private BionicDefenses() {}

	public static boolean avoids(SlimeBionicEntity mob, DamageSource source) {
		var traits = mob.getBionicTraits();
		var direct = source.getDirectEntity();
		if (traits.has(BionicTrait.WITHER_SHIELD) && mob.getHealth() <= mob.getMaxHealth() * 0.5f
			&& (direct instanceof AbstractArrow || direct != null && (direct.getType() == EntityType.WIND_CHARGE
				|| direct.getType() == EntityType.BREEZE_WIND_CHARGE))) return true;
		return false;
	}

	public static boolean projectileTeleport(SlimeBionicEntity mob) {
		for (int attempt = 0; attempt < 64; attempt++) if (randomTeleport(mob, 32)) return true;
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
		if (traits.has(BionicTrait.ENDER_TELEPORT)
			&& !(source.getEntity() instanceof LivingEntity) && mob.getRandom().nextFloat() < 0.9f)
			randomTeleport(mob, 32);
	}

	public static void tick(SlimeBionicEntity mob) {
		LivingEntity target = mob.getTarget();
		if (!mob.getBionicTraits().has(BionicTrait.ENDER_TELEPORT) || target == null || !target.isAlive()
			|| mob.isPassenger() || mob.isOrderedToSit() || mob.isNoAi()) { mob.teleportPursuitTicks = 0; return; }
		if (mob.distanceToSqr(target) > 256 && mob.teleportPursuitTicks++ >= 30) {
			Vec3 direction = new Vec3(mob.getX() - target.getX(), mob.getY(0.5) - target.getEyeY(), mob.getZ() - target.getZ()).normalize();
			if (teleport(mob, mob.getX() + (mob.getRandom().nextDouble() - 0.5) * 8 - direction.x * 16,
				mob.getY() + mob.getRandom().nextInt(16) - 8 - direction.y * 16,
				mob.getZ() + (mob.getRandom().nextDouble() - 0.5) * 8 - direction.z * 16)) mob.teleportPursuitTicks = 0;
		}
	}

	private static boolean randomTeleport(SlimeBionicEntity mob, int radius) {
		return teleport(mob, mob.getX() + (mob.getRandom().nextDouble() - 0.5) * radius * 2,
			mob.getY() + mob.getRandom().nextInt(radius * 2) - radius,
			mob.getZ() + (mob.getRandom().nextDouble() - 0.5) * radius * 2);
	}

	private static boolean teleport(SlimeBionicEntity mob, double x, double y, double z) {
		if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive()) return false;
		boolean attaching = mob.getBlockAttachment().locksMovement();
		if (!attaching) {
			BlockPos.MutableBlockPos support = new BlockPos.MutableBlockPos(x, y, z);
			while (support.getY() > mob.level().getMinBuildHeight() && !mob.level().getBlockState(support).blocksMotion()) support.move(Direction.DOWN);
			var state = mob.level().getBlockState(support);
			if (!state.blocksMotion() || state.getFluidState().is(FluidTags.WATER)) return false;
		}
		var event = EventHooks.onEnderTeleport(mob, x, y, z);
		if (event.isCanceled()) return false;
		x = event.getTargetX(); y = event.getTargetY(); z = event.getTargetZ();
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
			|| !mob.level().isInWorldBounds(BlockPos.containing(x, y, z))
			|| !mob.level().getWorldBorder().isWithinBounds(mob.getBoundingBox().move(x - mob.getX(), y - mob.getY(), z - mob.getZ()))) return false;
		Vec3 previous = mob.position();
		AABB previousBounds = mob.getBoundingBox();
		// Send particles at both endpoints instead of relying on client entity-position packet timing.
		if (attaching ? !mob.getBlockAttachment().teleportTo(x, y, z) : !mob.randomTeleport(x, y, z, false)) return false;
		mob.resetFallDistance();
		teleportParticles(level, previousBounds);
		teleportParticles(level, mob.getBoundingBox());
		mob.level().gameEvent(GameEvent.TELEPORT, previous, GameEvent.Context.of(mob));
		mob.level().playSound(null, previous.x, previous.y, previous.z, SoundEvents.ENDERMAN_TELEPORT, mob.getSoundSource(), 1, 1);
		mob.playSound(SoundEvents.ENDERMAN_TELEPORT, 1, 1);
		return true;
	}

	private static void teleportParticles(ServerLevel level, AABB bounds) {
		Vec3 center = bounds.getCenter();
		level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 32,
			bounds.getXsize() * 0.25, bounds.getYsize() * 0.25, bounds.getZsize() * 0.25, 0.2);
	}
}
