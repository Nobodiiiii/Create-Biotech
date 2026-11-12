package com.nobodiiiii.createbiotech.entity.trait;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.entity.ai.BionicDispositionRegistry;
import com.nobodiiiii.createbiotech.entity.ai.BionicIntelligenceRegistry;

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
		return get(profile, level, BionicTraitRegistry.snapshot(level));
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
			: detect(donor.getType(), donor, BionicTraitRegistry.snapshot(donor.level()));
	}

	@SuppressWarnings("deprecation")
	private static Facts detect(EntityType<?> type, @Nullable LivingEntity donor,
		BionicTraitRegistry.Snapshot snapshot) {
		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		Set<BionicTrait> abilities = new LinkedHashSet<>();
		Map<BionicTrait, Double> numbers = new LinkedHashMap<>();
		for (BionicTrait trait : snapshot.definitions()) {
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
					var attribute = switch (trait.id().toString()) {
					case "create_biotech:attack_knockback" -> donor.getAttribute(Attributes.ATTACK_KNOCKBACK);
					case "create_biotech:natural_armor" -> donor.getAttribute(Attributes.ARMOR);
					case "create_biotech:knockback_resistance" -> donor.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
					default -> null;
					};
					automatic = attribute == null ? 0.0d : attribute.getBaseValue();
				}
				double value = data.numbers().resolve(type, id, automatic);
				value = Double.isFinite(value) ? Math.max(0.0d, value) : 0.0d;
				numbers.put(trait, BionicTrait.KNOCKBACK_RESISTANCE.equals(trait) ? Math.min(1.0d, value) : value);
			}
		}
		Map<BionicTrait, Set<ResourceLocation>> effectSets = new HashMap<>(), attackSets = new HashMap<>();
		for (BionicTrait trait : snapshot.definitions()) {
			if (trait.valueKind() != BionicTrait.ValueKind.EFFECT_SET && trait.valueKind() != BionicTrait.ValueKind.ATTACK_EFFECT_SET) continue;
			BionicTraitData definition = snapshot.get(trait);
			Set<ResourceLocation> supplied = new HashSet<>();
			if (BionicTrait.IMMUNE_EFFECTS.equals(trait) && definition.automaticDetection() && donor != null)
				BuiltInRegistries.MOB_EFFECT.holders().forEach(effect -> {
					try {
						if (!donor.canBeAffected(new MobEffectInstance(effect, 20))) supplied.add(effect.key().location());
					} catch (RuntimeException exception) {
						LogUtils.getLogger().debug("Could not probe {} immunity for {}", effect.key().location(), id, exception);
					}
				});
			definition.effects().forEach(type, id, overrides -> overrides.forEach((effect, enabled) -> {
				if (enabled) supplied.add(effect); else supplied.remove(effect);
			}));
			if (trait.valueKind() == BionicTrait.ValueKind.ATTACK_EFFECT_SET) {
				supplied.retainAll(definition.attackEffects().keySet());
				attackSets.put(trait, Set.copyOf(supplied));
			} else effectSets.put(trait, Set.copyOf(supplied));
		}
		Map<BionicTrait, Set<ResourceLocation>> variants = new LinkedHashMap<>();
		for (BionicTrait trait : snapshot.definitions()) {
			if (trait.valueKind() != BionicTrait.ValueKind.SUBTRAIT_SET) continue;
			BionicTraitData definition = snapshot.get(trait);
			Set<ResourceLocation> supplied = new HashSet<>();
			if (trait.isHeadClassification() && definition.automaticDetection() && donor != null)
				supplied.add(BionicTrait.DISPOSITION.equals(trait)
					? BionicHeadTraits.id(BionicDispositionRegistry.get(donor))
					: BionicHeadTraits.id(BionicIntelligenceRegistry.get(donor)));
			if (BionicTrait.ENCHANT_VULNERABILITY.equals(trait) && definition.automaticDetection()) {
				for (String enchantment : List.of("smite", "bane_of_arthropods", "impaling"))
					if (type.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
						ResourceLocation.withDefaultNamespace("sensitive_to_" + enchantment))))
						supplied.add(ResourceLocation.withDefaultNamespace(enchantment));
			}
			definition.effects().forEach(type, id, overrides -> overrides.forEach((variant, enabled) -> {
				if (enabled) supplied.add(variant); else supplied.remove(variant);
			}));
			supplied.retainAll(definition.subtraits().keySet());
			if (trait.isHeadClassification())
				supplied.retainAll(BionicHeadTraits.variants(trait).keySet());
			variants.put(trait, Set.copyOf(supplied));
		}
		if (donor != null && donor.isBaby() && (type == EntityType.HOGLIN || type == EntityType.ZOGLIN))
			abilities.remove(BionicTrait.LAUNCH_STRIKE);
		if (donor != null && donor.isBaby())
			abilities.removeAll(Set.of(BionicTrait.MILKABLE, BionicTrait.WOOL_SHEARING,
				BionicTrait.EGG_LAYING, BionicTrait.SCUTE_SHEDDING));
		return new Facts(abilities, numbers, effectSets, attackSets, BionicDeterrence.targets(type).stream()
			.map(BuiltInRegistries.ENTITY_TYPE::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet()),
			abilities.contains(BionicTrait.PACK_ALERT) ? BionicPackAlert.targets(donor) : Set.of(), variants);
	}

	@SuppressWarnings("deprecation")
	private static boolean automatic(BionicTrait trait, EntityType<?> type, @Nullable LivingEntity donor) {
		return switch (trait.id().toString()) {
		case "create_biotech:rideable" -> donor instanceof net.minecraft.world.entity.Saddleable;
		case "create_biotech:cargo_chest" -> donor instanceof net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
		case "create_biotech:fire_immune", "create_biotech:fire_resistance" -> type.fireImmune() || donor != null && donor.fireImmune();
		case "create_biotech:water_sensitive" -> donor != null && donor.isSensitiveToWater();
		case "create_biotech:freeze_immune", "create_biotech:freeze_resistance" -> type.is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES);
		case "create_biotech:freeze_vulnerable" -> type.is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES);
		case "create_biotech:inverted_healing" -> donor != null && donor.isInvertedHealAndHarm();
		case "create_biotech:water_sinking" -> type.is(EntityTypeTags.UNDEAD);
		case "create_biotech:hunting_instinct" -> BionicHunting.hasRelations(type);
		case "create_biotech:deterrence" -> !BionicDeterrence.targets(type).isEmpty();
		case "create_biotech:projectile_deflection" -> type.is(EntityTypeTags.DEFLECTS_PROJECTILES);
		case "create_biotech:water_breathing" -> donor != null && (type.is(EntityTypeTags.CAN_BREATHE_UNDER_WATER)
			&& !type.is(EntityTypeTags.UNDEAD) && type != EntityType.ARMOR_STAND
			&& !donor.canDrownInFluidType(NeoForgeMod.WATER_TYPE.value())
			|| donor.canDrownInFluidType(NeoForgeMod.WATER_TYPE.value()) && donor.getMaxAirSupply() > 300);
		case "create_biotech:tameable" -> donor instanceof TamableAnimal;
		case "create_biotech:livestock_tameable" -> donor instanceof AbstractHorse
			&& type != EntityType.SKELETON_HORSE && type != EntityType.ZOMBIE_HORSE;
		case "create_biotech:fall_damage_immune" -> (type == EntityType.CAT || type == EntityType.OCELOT)
			&& type.is(EntityTypeTags.FALL_DAMAGE_IMMUNE);
		case "create_biotech:powder_snow_walk" -> type.is(EntityTypeTags.POWDER_SNOW_WALKABLE_MOBS);
		// Squid swim without water navigation; drowned start with their land navigation.
		// Generic floating and underwater breathing alone do not establish swimming expertise.
		case "create_biotech:swim_specialist" -> donor instanceof WaterAnimal || donor instanceof Drowned
			|| donor instanceof Mob mob && (mob.getNavigation() instanceof WaterBoundPathNavigation
				|| mob.getNavigation() instanceof AmphibiousPathNavigation
				|| mob.getMoveControl() instanceof SmoothSwimmingMoveControl);
		default -> false;
		};
	}

	private record Cache(long generation, Map<MimicProfile.BiologicalKey, Facts> facts) {}

	public record Facts(Set<BionicTrait> abilities, Map<BionicTrait, Double> numbers,
		Map<BionicTrait, Set<ResourceLocation>> effectSets, Map<BionicTrait, Set<ResourceLocation>> attackSets, Set<ResourceLocation> deterrenceTargets,
		Set<ResourceLocation> packAlertTargets, Map<BionicTrait, Set<ResourceLocation>> variants) {
		public static final Facts EMPTY = new Facts(Set.of(), Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Map.of());
		public Facts {
			abilities = Set.copyOf(abilities);
			numbers = Map.copyOf(numbers);
			effectSets = Map.copyOf(effectSets);
			attackSets = Map.copyOf(attackSets);
			deterrenceTargets = Set.copyOf(deterrenceTargets);
			packAlertTargets = Set.copyOf(packAlertTargets);
			variants = Map.copyOf(variants);
		}
		public Set<ResourceLocation> effects(BionicTrait trait) { return effectSets.getOrDefault(trait, Set.of()); }
		public Set<ResourceLocation> attacks(BionicTrait trait) { return attackSets.getOrDefault(trait, Set.of()); }
		public Set<ResourceLocation> immuneEffects() { return effects(BionicTrait.IMMUNE_EFFECTS); }
		public Set<ResourceLocation> attackEffects() { return attacks(BionicTrait.EFFECT_ATTACK); }
		public Set<ResourceLocation> variants(BionicTrait trait) { return variants.getOrDefault(trait, Set.of()); }
		public boolean has(BionicTrait trait) { return abilities.contains(trait); }
		public double value(BionicTrait trait) { return numbers.getOrDefault(trait, 0.0d); }
		public boolean supplies(BionicTrait trait) {
			return switch (trait.valueKind()) {
			case ABILITY -> has(trait);
			case NUMBER -> value(trait) > 0;
			case SUBTRAIT_SET -> !variants(trait).isEmpty();
			case EFFECT_SET -> !effects(trait).isEmpty();
			case ATTACK_EFFECT_SET -> !attacks(trait).isEmpty();
			};
		}
	}
}
