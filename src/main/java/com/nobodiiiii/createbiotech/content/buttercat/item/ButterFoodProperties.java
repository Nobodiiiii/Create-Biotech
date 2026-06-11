package com.nobodiiiii.createbiotech.content.buttercat.item;

import com.nobodiiiii.createbiotech.content.buttercat.register.ModEffects;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;

public final class ButterFoodProperties {

	public enum Variant {
		BUTTER,
		HONEY_BUTTER,
		SUPER_BUTTER,
		INCOMPLETE_SUPER_BUTTER
	}

	private ButterFoodProperties() {}

	public static FoodProperties create(Variant variant) {
		CBConfigs.ButterCat config = CBConfigs.SERVER.butterCat;
		FoodProperties.Builder builder = switch (variant) {
		case BUTTER -> food(config.butterNutrition.get(), config.butterSaturation.get());
		case HONEY_BUTTER -> food(config.honeyButterNutrition.get(), config.honeyButterSaturation.get())
			.withEffect(ModEffects.BUTTER_ROTATION_EFFECT.get(), config.honeyButterRotationDuration.get(),
				config.honeyButterRotationAmplifier.get());
		case SUPER_BUTTER -> food(config.superButterNutrition.get(), config.superButterSaturation.get())
			.withEffect(ModEffects.BUTTER_ROTATION_EFFECT.get(), config.superButterRotationDuration.get(),
				config.superButterRotationAmplifier.get())
			.withEffect(MobEffects.LEVITATION, config.superButterLevitationDuration.get(),
				config.superButterLevitationAmplifier.get());
		case INCOMPLETE_SUPER_BUTTER -> food(config.incompleteSuperButterNutrition.get(),
			config.incompleteSuperButterSaturation.get())
				.withEffect(ModEffects.BUTTER_ROTATION_EFFECT.get(), config.incompleteSuperButterRotationDuration.get(),
					config.incompleteSuperButterRotationAmplifier.get());
		};
		return builder.build();
	}

	private static Builder food(int nutrition, double saturation) {
		return new Builder(nutrition, saturation);
	}

	private static class Builder extends FoodProperties.Builder {
		private Builder(int nutrition, double saturation) {
			nutrition(nutrition);
			saturationMod((float) saturation);
		}

		private Builder withEffect(MobEffect effect, int duration, int amplifier) {
			if (duration > 0)
				effect(() -> new MobEffectInstance(effect, duration, amplifier), 1.0f);
			return this;
		}
	}
}
