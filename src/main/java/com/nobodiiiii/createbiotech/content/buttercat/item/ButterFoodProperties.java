package com.nobodiiiii.createbiotech.content.buttercat.item;

import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBMobEffects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;

public final class ButterFoodProperties {

	public enum Variant {
		BUTTER,
		SUPER_BUTTER,
		INCOMPLETE_SUPER_BUTTER
	}

	private ButterFoodProperties() {}

	public static FoodProperties create(Variant variant) {
		return create(variant, false);
	}

	public static FoodProperties createDefault(Variant variant) {
		return create(variant, true);
	}

	private static FoodProperties create(Variant variant, boolean useDefaults) {
		CBConfigs.ButterCat config = CBConfigs.SERVER.butterCat;
		FoodProperties.Builder builder = switch (variant) {
		case BUTTER -> food(value(config.butterNutrition, useDefaults), value(config.butterSaturation, useDefaults));
		case SUPER_BUTTER -> food(value(config.superButterNutrition, useDefaults),
			value(config.superButterSaturation, useDefaults))
			.withEffect(CBMobEffects.BUTTER_ROTATION, value(config.superButterRotationDuration, useDefaults),
				value(config.superButterRotationAmplifier, useDefaults))
			.withEffect(MobEffects.LEVITATION, value(config.superButterLevitationDuration, useDefaults),
				value(config.superButterLevitationAmplifier, useDefaults));
		case INCOMPLETE_SUPER_BUTTER -> food(value(config.incompleteSuperButterNutrition, useDefaults),
			value(config.incompleteSuperButterSaturation, useDefaults))
				.withEffect(CBMobEffects.BUTTER_ROTATION,
					value(config.incompleteSuperButterRotationDuration, useDefaults),
					value(config.incompleteSuperButterRotationAmplifier, useDefaults));
		};
		return builder.build();
	}

	private static <T> T value(ConfigValue<T> configValue, boolean useDefault) {
		return useDefault ? configValue.getDefault() : configValue.get();
	}

	private static Builder food(int nutrition, double saturation) {
		return new Builder(nutrition, saturation);
	}

	private static class Builder extends FoodProperties.Builder {
		private Builder(int nutrition, double saturation) {
			nutrition(nutrition);
			saturationModifier((float) saturation);
		}

		private Builder withEffect(Holder<MobEffect> effect, int duration, int amplifier) {
			if (duration > 0)
				effect(() -> new MobEffectInstance(effect, duration, amplifier), 1.0f);
			return this;
		}
	}
}
