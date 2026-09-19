package com.nobodiiiii.createbiotech.entity.trait;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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

/** Detects stable donor facts and aggregates them by retained source-cube weight. */
public final class BionicBodyTraitRegistry {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final TagKey<EntityType<?>> SUN_SENSITIVE = tag("sun_sensitive");
	public static final TagKey<EntityType<?>> MOISTURE_DEPENDENT = tag("moisture_dependent");
	public static final TagKey<EntityType<?>> HEAT_SENSITIVE = tag("heat_sensitive");
	public static final TagKey<EntityType<?>> MATERIAL_FALL_DAMAGE_IMMUNE =
		tag("material_fall_damage_immune");
	public static final TagKey<EntityType<?>> WEB_ADAPTED = tag("web_adapted");

	private static final Map<MimicProfile.BiologicalKey, BionicBodyTraits> DETECTED =
		new ConcurrentHashMap<>();
	private static final AtomicLong GENERATION = new AtomicLong();
	private static boolean registered;

	private BionicBodyTraitRegistry() {}

	public static void register() {
		if (registered)
			return;
		registered = true;
		NeoForge.EVENT_BUS.addListener(BionicBodyTraitRegistry::onTagsUpdated);
	}

	private static void onTagsUpdated(TagsUpdatedEvent event) {
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
		EnumMap<BionicBodyTrait, Double> traits = new EnumMap<>(BionicBodyTrait.class);
		put(traits, BionicBodyTrait.FIRE_IMMUNE, type.fireImmune() || donor.fireImmune());
		put(traits, BionicBodyTrait.WATER_SENSITIVE, donor.isSensitiveToWater());
		put(traits, BionicBodyTrait.FREEZE_IMMUNE,
			type.is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES));
		put(traits, BionicBodyTrait.FREEZE_VULNERABLE,
			type.is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES));
		put(traits, BionicBodyTrait.SUN_SENSITIVE, type.is(SUN_SENSITIVE));
		put(traits, BionicBodyTrait.MOISTURE_DEPENDENT, type.is(MOISTURE_DEPENDENT));
		put(traits, BionicBodyTrait.HEAT_SENSITIVE, type.is(HEAT_SENSITIVE));
		put(traits, BionicBodyTrait.INVERTED_HEALING, donor.isInvertedHealAndHarm());
		put(traits, BionicBodyTrait.FALL_DAMAGE_IMMUNE,
			type.is(MATERIAL_FALL_DAMAGE_IMMUNE));
		put(traits, BionicBodyTrait.WEB_ADAPTED, type.is(WEB_ADAPTED));

		Set<ResourceLocation> immuneEffects = new HashSet<>();
		BuiltInRegistries.MOB_EFFECT.holders().forEach(effect -> {
			try {
				if (!donor.canBeAffected(new MobEffectInstance(effect, 20)))
					immuneEffects.add(effect.key().location());
			} catch (RuntimeException exception) {
				LOGGER.debug("Could not probe {} effect immunity for {}", effect.key().location(),
					BuiltInRegistries.ENTITY_TYPE.getKey(type), exception);
			}
		});
		AttributeInstance armor = donor.getAttribute(Attributes.ARMOR);
		double naturalArmor = armor == null ? 0.0d : armor.getBaseValue();
		return new BionicBodyTraits(traits, immuneEffects, naturalArmor);
	}

	private static void put(EnumMap<BionicBodyTrait, Double> traits, BionicBodyTrait trait,
		boolean present) {
		if (present)
			traits.put(trait, 1.0d);
	}

	private static TagKey<EntityType<?>> tag(String path) {
		return TagKey.create(Registries.ENTITY_TYPE, CreateBiotech.asResource(path));
	}
}
