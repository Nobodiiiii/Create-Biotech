package com.nobodiiiii.createbiotech.entity.trait;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForgeMod;

/** Stable donor facts only. Assembly geometry and acquisition rules belong to the resolver. */
public final class BionicTraitDonors {
	private static final Map<Level, Cache> CACHES = new WeakHashMap<>();

	private BionicTraitDonors() {}

	public static Facts get(MimicProfile profile, Level level) {
		return get(profile, level, BionicTraitRegistry.snapshot());
	}

	static synchronized Facts get(MimicProfile profile, Level level, BionicTraitRegistry.Snapshot data) {
		if (!BionicTraits.ENABLED || profile == null || level == null)
			return Facts.EMPTY;
		Cache cache = CACHES.get(level);
		if (cache == null || cache.generation != data.generation()) {
			cache = new Cache(data.generation(), new HashMap<>());
			CACHES.put(level, cache);
		}
		return cache.facts.computeIfAbsent(profile.biologicalKey(), ignored -> {
			LivingEntity donor = null;
			try {
				donor = profile.createBiologicalEntity(level);
			} catch (RuntimeException exception) {
				LogUtils.getLogger().warn("Could not inspect bionic donor {}", profile.entityTypeId(), exception);
			}
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(profile.entityTypeId()).orElse(null);
			return type == null ? Facts.EMPTY : detect(type, donor, data);
		});
	}

	public static Facts detect(LivingEntity donor) {
		return !BionicTraits.ENABLED || donor == null ? Facts.EMPTY
			: detect(donor.getType(), donor, BionicTraitRegistry.snapshot());
	}

	@SuppressWarnings("deprecation")
	private static Facts detect(EntityType<?> type, @Nullable LivingEntity donor,
		BionicTraitRegistry.Snapshot snapshot) {
		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		Set<BionicTrait> abilities = EnumSet.noneOf(BionicTrait.class);
		Map<BionicTrait, Double> numbers = new EnumMap<>(BionicTrait.class);
		for (BionicTrait trait : BionicTrait.values()) {
			BionicTraitData data = snapshot.get(trait);
			if (trait.valueKind() == BionicTrait.ValueKind.ABILITY) {
				boolean automatic = false;
				try {
					automatic = data.automaticDetection() && automatic(trait, type, donor);
				} catch (RuntimeException exception) {
					LogUtils.getLogger().debug("Could not probe {} for {}", trait.id(), id, exception);
				}
				if (data.abilities().resolve(type, id, automatic))
					abilities.add(trait);
			} else if (trait.valueKind() == BionicTrait.ValueKind.NUMBER) {
				double automatic = 0.0d;
				if (data.automaticDetection() && donor != null) {
					var attribute = switch (trait) {
					case NATURAL_ARMOR -> donor.getAttribute(Attributes.ARMOR);
					case KNOCKBACK_RESISTANCE -> donor.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
					default -> null;
					};
					automatic = attribute == null ? 0.0d : attribute.getBaseValue();
				}
				double value = data.numbers().resolve(type, id, automatic);
				value = Double.isFinite(value) ? Math.max(0.0d, value) : 0.0d;
				numbers.put(trait, trait == BionicTrait.KNOCKBACK_RESISTANCE ? Math.min(1.0d, value) : value);
			}
		}
		Set<ResourceLocation> effects = new HashSet<>();
		BionicTraitData immunities = snapshot.get(BionicTrait.IMMUNE_EFFECTS);
		if (immunities.automaticDetection() && donor != null)
			BuiltInRegistries.MOB_EFFECT.holders().forEach(effect -> {
				try {
					if (!donor.canBeAffected(new MobEffectInstance(effect, 20)))
						effects.add(effect.key().location());
				} catch (RuntimeException exception) {
					LogUtils.getLogger().debug("Could not probe {} immunity for {}", effect.key().location(), id, exception);
				}
			});
		immunities.effects().forEach(type, id, overrides -> overrides.forEach((effect, enabled) -> {
			if (enabled) effects.add(effect);
			else effects.remove(effect);
		}));
		Set<ResourceLocation> attacks = new HashSet<>();
		BionicTraitData attackData = snapshot.get(BionicTrait.EFFECT_ATTACK);
		attackData.effects().forEach(type, id, overrides -> overrides.forEach((effect, enabled) -> {
			if (enabled && attackData.attackEffects().containsKey(effect)) attacks.add(effect);
			else attacks.remove(effect);
		}));
		return new Facts(abilities, numbers, effects, attacks, BionicDeterrence.targets(type).stream()
			.map(BuiltInRegistries.ENTITY_TYPE::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet()));
	}

	@SuppressWarnings("deprecation")
	private static boolean automatic(BionicTrait trait, EntityType<?> type, @Nullable LivingEntity donor) {
		return switch (trait) {
		case FIRE_IMMUNE, FIRE_RESISTANCE -> type.fireImmune() || donor != null && donor.fireImmune();
		case WATER_SENSITIVE -> donor != null && donor.isSensitiveToWater();
		case FREEZE_IMMUNE, FREEZE_RESISTANCE -> type.is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES);
		case FREEZE_VULNERABLE -> type.is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES);
		case INVERTED_HEALING -> donor != null && donor.isInvertedHealAndHarm();
		case DETERRENCE -> !BionicDeterrence.targets(type).isEmpty();
		case PROJECTILE_DEFLECTION -> type.is(EntityTypeTags.DEFLECTS_PROJECTILES);
		case WATER_BREATHING -> donor != null && (type.is(EntityTypeTags.CAN_BREATHE_UNDER_WATER)
			&& !type.is(EntityTypeTags.UNDEAD) && type != EntityType.ARMOR_STAND
			&& !donor.canDrownInFluidType(NeoForgeMod.WATER_TYPE.value())
			|| donor.canDrownInFluidType(NeoForgeMod.WATER_TYPE.value()) && donor.getMaxAirSupply() > 300);
		case TAMEABLE -> donor instanceof TamableAnimal || donor instanceof AbstractHorse
			&& type != EntityType.SKELETON_HORSE && type != EntityType.ZOMBIE_HORSE && type != EntityType.CAMEL;
		case AGILE_LANDING -> (type == EntityType.CAT || type == EntityType.OCELOT)
			&& type.is(EntityTypeTags.FALL_DAMAGE_IMMUNE);
		case POWDER_SNOW_WALK -> type.is(EntityTypeTags.POWDER_SNOW_WALKABLE_MOBS);
		// Squid swim without water navigation; drowned start with their land navigation.
		// Generic floating and underwater breathing alone do not establish swimming expertise.
		case SWIM_SPECIALIST -> donor instanceof WaterAnimal || donor instanceof Drowned
			|| donor instanceof Mob mob && (mob.getNavigation() instanceof WaterBoundPathNavigation
				|| mob.getNavigation() instanceof AmphibiousPathNavigation
				|| mob.getMoveControl() instanceof SmoothSwimmingMoveControl);
		default -> false;
		};
	}

	private record Cache(long generation, Map<MimicProfile.BiologicalKey, Facts> facts) {}

	public record Facts(Set<BionicTrait> abilities, Map<BionicTrait, Double> numbers,
		Set<ResourceLocation> immuneEffects, Set<ResourceLocation> attackEffects, Set<ResourceLocation> deterrenceTargets) {
		public static final Facts EMPTY = new Facts(Set.of(), Map.of(), Set.of(), Set.of(), Set.of());
		public Facts {
			abilities = Set.copyOf(abilities);
			numbers = Map.copyOf(numbers);
			immuneEffects = Set.copyOf(immuneEffects);
			attackEffects = Set.copyOf(attackEffects);
			deterrenceTargets = Set.copyOf(deterrenceTargets);
		}
		public boolean has(BionicTrait trait) { return abilities.contains(trait); }
		public double value(BionicTrait trait) { return numbers.getOrDefault(trait, 0.0d); }
	}
}
