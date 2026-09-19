package com.nobodiiiii.createbiotech.entity.trait;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** Detects stable donor facts, applies per-trait data rules, and aggregates source weights. */
public final class BionicBodyTraitRegistry {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<MimicProfile.BiologicalKey, BionicBodyTraits> DETECTED =
		new ConcurrentHashMap<>();
	private static final AtomicLong GENERATION = new AtomicLong();
	private static volatile DataOverrides DATA = DataOverrides.EMPTY;
	private static boolean registered;

	private BionicBodyTraitRegistry() {}

	public static void register() {
		if (registered)
			return;
		registered = true;
		NeoForge.EVENT_BUS.addListener(BionicBodyTraitRegistry::onTagsUpdated);
	}

	private static void onTagsUpdated(TagsUpdatedEvent event) {
		invalidateCache();
	}

	static void replaceData(DataOverrides data) {
		DATA = data;
		invalidateCache();
	}

	private static void invalidateCache() {
		DETECTED.clear();
		GENERATION.incrementAndGet();
	}

	public static long generation() {
		return GENERATION.get();
	}

	public static BionicBodyTraits get(MimicProfile profile, Level level) {
		if (profile == null || level == null)
			return BionicBodyTraits.EMPTY;
		return DETECTED.computeIfAbsent(profile.biologicalKey(), ignored -> {
			try {
				LivingEntity donor = profile.createBiologicalEntity(level);
				return donor == null ? BionicBodyTraits.EMPTY : detect(donor);
			} catch (RuntimeException exception) {
				LOGGER.warn("Could not inspect stable bionic donor traits for {}",
					profile.entityTypeId(), exception);
				return BionicBodyTraits.EMPTY;
			}
		});
	}

	public static BionicBodyTraits resolve(@Nullable SurgicalAssembly assembly, Level level) {
		if (assembly == null || level == null || assembly.sources().isEmpty())
			return BionicBodyTraits.EMPTY;

		int totalWeight = 0;
		EnumMap<BionicBodyTrait, Double> weightedCoverage = new EnumMap<>(BionicBodyTrait.class);
		double weightedArmor = 0.0d;
		Set<ResourceLocation> commonImmunities = null;
		for (SurgicalAssembly.Source source : assembly.sources()) {
			int weight = source.presentCubes().cardinality();
			if (weight <= 0)
				continue;
			BionicBodyTraits donor = get(source.profile(), level);
			totalWeight += weight;
			weightedArmor += donor.naturalArmor() * weight;
			for (BionicBodyTrait trait : BionicBodyTrait.values())
				weightedCoverage.merge(trait, donor.coverage(trait) * weight, Double::sum);
			if (commonImmunities == null)
				commonImmunities = new HashSet<>(donor.immuneEffects());
			else
				commonImmunities.retainAll(donor.immuneEffects());
		}
		if (totalWeight <= 0)
			return BionicBodyTraits.EMPTY;
		for (Map.Entry<BionicBodyTrait, Double> entry : weightedCoverage.entrySet())
			entry.setValue(entry.getValue() / totalWeight);
		return new BionicBodyTraits(weightedCoverage,
			commonImmunities == null ? Set.of() : commonImmunities, weightedArmor / totalWeight);
	}

	@SuppressWarnings("deprecation")
	public static BionicBodyTraits detect(LivingEntity donor) {
		if (donor == null)
			return BionicBodyTraits.EMPTY;
		EntityType<?> type = donor.getType();
		DataOverrides data = DATA;
		EnumMap<BionicBodyTrait, Double> traits = new EnumMap<>(BionicBodyTrait.class);
		if (data.trait(BionicBodyTrait.FIRE_IMMUNE).automaticDetection())
			put(traits, BionicBodyTrait.FIRE_IMMUNE, type.fireImmune() || donor.fireImmune());
		if (data.trait(BionicBodyTrait.WATER_SENSITIVE).automaticDetection())
			put(traits, BionicBodyTrait.WATER_SENSITIVE, donor.isSensitiveToWater());
		if (data.trait(BionicBodyTrait.FREEZE_IMMUNE).automaticDetection())
			put(traits, BionicBodyTrait.FREEZE_IMMUNE,
				type.is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES));
		if (data.trait(BionicBodyTrait.FREEZE_VULNERABLE).automaticDetection())
			put(traits, BionicBodyTrait.FREEZE_VULNERABLE,
				type.is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES));
		if (data.trait(BionicBodyTrait.INVERTED_HEALING).automaticDetection())
			put(traits, BionicBodyTrait.INVERTED_HEALING, donor.isInvertedHealAndHarm());

		Set<ResourceLocation> immuneEffects = new HashSet<>();
		if (data.immuneEffects().automaticDetection())
			BuiltInRegistries.MOB_EFFECT.holders().forEach(effect -> {
				try {
					if (!donor.canBeAffected(new MobEffectInstance(effect, 20)))
						immuneEffects.add(effect.key().location());
				} catch (RuntimeException exception) {
					LOGGER.debug("Could not probe {} effect immunity for {}", effect.key().location(),
						BuiltInRegistries.ENTITY_TYPE.getKey(type), exception);
				}
			});

		double naturalArmor = 0.0d;
		if (data.naturalArmor().automaticDetection()) {
			AttributeInstance armor = donor.getAttribute(Attributes.ARMOR);
			naturalArmor = armor == null ? 0.0d : armor.getBaseValue();
		}

		for (BionicBodyTrait trait : BionicBodyTrait.values())
			applyTrait(data.trait(trait), type, traits, trait);
		applyEffects(data.immuneEffects(), type, immuneEffects);
		naturalArmor = applyArmor(data.naturalArmor(), type, naturalArmor);
		return new BionicBodyTraits(traits, immuneEffects, naturalArmor);
	}

	private static void applyTrait(TraitOverrides overrides, EntityType<?> type,
		EnumMap<BionicBodyTrait, Double> traits, BionicBodyTrait trait) {
		for (TaggedBooleanOverride tagged : overrides.tags())
			if (type.is(tagged.tag()))
				setTrait(traits, trait, tagged.value());
		Boolean exact = overrides.entityTypes().get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
		if (exact != null)
			setTrait(traits, trait, exact);
	}

	private static void applyEffects(EffectOverrides overrides, EntityType<?> type,
		Set<ResourceLocation> immuneEffects) {
		for (TaggedEffectOverride tagged : overrides.tags())
			if (type.is(tagged.tag()))
				applyEffects(tagged.values(), immuneEffects);
		Map<ResourceLocation, Boolean> exact =
			overrides.entityTypes().get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
		if (exact != null)
			applyEffects(exact, immuneEffects);
	}

	private static void applyEffects(Map<ResourceLocation, Boolean> values,
		Set<ResourceLocation> immuneEffects) {
		values.forEach((effect, enabled) -> {
			if (enabled)
				immuneEffects.add(effect);
			else
				immuneEffects.remove(effect);
		});
	}

	private static double applyArmor(ArmorOverrides overrides, EntityType<?> type,
		double naturalArmor) {
		for (TaggedArmorOverride tagged : overrides.tags())
			if (type.is(tagged.tag()))
				naturalArmor = tagged.value();
		Double exact = overrides.entityTypes().get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
		return exact == null ? naturalArmor : exact;
	}

	private static void put(EnumMap<BionicBodyTrait, Double> traits, BionicBodyTrait trait,
		boolean present) {
		if (present)
			traits.put(trait, 1.0d);
	}

	private static void setTrait(EnumMap<BionicBodyTrait, Double> traits, BionicBodyTrait trait,
		boolean enabled) {
		if (enabled)
			traits.put(trait, 1.0d);
		else
			traits.remove(trait);
	}

	record TraitOverrides(boolean automaticDetection,
		Map<ResourceLocation, Boolean> entityTypes, List<TaggedBooleanOverride> tags) {
		private static final TraitOverrides AUTO = new TraitOverrides(true, Map.of(), List.of());

		TraitOverrides {
			entityTypes = Map.copyOf(entityTypes);
			tags = List.copyOf(tags);
		}
	}

	record TaggedBooleanOverride(TagKey<EntityType<?>> tag, boolean value) {}

	record EffectOverrides(boolean automaticDetection,
		Map<ResourceLocation, Map<ResourceLocation, Boolean>> entityTypes,
		List<TaggedEffectOverride> tags) {
		private static final EffectOverrides AUTO = new EffectOverrides(true, Map.of(), List.of());

		EffectOverrides {
			Map<ResourceLocation, Map<ResourceLocation, Boolean>> immutable = new HashMap<>();
			entityTypes.forEach((entityType, effects) ->
				immutable.put(entityType, Map.copyOf(effects)));
			entityTypes = Map.copyOf(immutable);
			tags = List.copyOf(tags);
		}
	}

	record TaggedEffectOverride(TagKey<EntityType<?>> tag,
		Map<ResourceLocation, Boolean> values) {
		TaggedEffectOverride {
			values = Map.copyOf(values);
		}
	}

	record ArmorOverrides(boolean automaticDetection,
		Map<ResourceLocation, Double> entityTypes, List<TaggedArmorOverride> tags) {
		private static final ArmorOverrides AUTO = new ArmorOverrides(true, Map.of(), List.of());

		ArmorOverrides {
			entityTypes = Map.copyOf(entityTypes);
			tags = List.copyOf(tags);
		}
	}

	record TaggedArmorOverride(TagKey<EntityType<?>> tag, double value) {}

	record DataOverrides(Map<BionicBodyTrait, TraitOverrides> traits,
		EffectOverrides immuneEffects, ArmorOverrides naturalArmor) {
		private static final DataOverrides EMPTY =
			new DataOverrides(Map.of(), EffectOverrides.AUTO, ArmorOverrides.AUTO);

		DataOverrides {
			traits = Map.copyOf(traits);
		}

		TraitOverrides trait(BionicBodyTrait trait) {
			return traits.getOrDefault(trait, TraitOverrides.AUTO);
		}
	}
}
