package com.nobodiiiii.createbiotech.content.experience;

import com.nobodiiiii.createbiotech.registry.CBConfigs;

public final class ExperienceConstants {
	private ExperienceConstants() {
	}

	public static int xpPerNugget() {
		return CBConfigs.SERVER.experience.xpPerNugget.get();
	}

	public static int tankCapacityPerBlock() {
		return CBConfigs.SERVER.experience.tankCapacityPerBlock.get();
	}

	public static int chamberCacheCapacity() {
		return CBConfigs.SERVER.evokerEnchantingChamber.cacheCapacity.get();
	}

	public static int chamberXpPerLevel() {
		return CBConfigs.SERVER.evokerEnchantingChamber.xpPerLevel.get();
	}

	public static float pumpXpPerRpmPerSecond() {
		return (float) (CBConfigs.SERVER.experience.pumpXpPerRpmPerSecond.get()
			* CBConfigs.SERVER.experience.pumpEfficiency.get());
	}

	public static float speedNormalizationRpm() {
		return CBConfigs.SERVER.experience.speedNormalizationRpm.get().floatValue();
	}

	public static int clusterNuggetValue() {
		return CBConfigs.SERVER.experience.clusterNuggetValue.get();
	}

	public static int largeBudNuggetValue() {
		return CBConfigs.SERVER.experience.largeBudNuggetValue.get();
	}

	public static int mediumBudNuggetValue() {
		return CBConfigs.SERVER.experience.mediumBudNuggetValue.get();
	}

	public static int smallBudNuggetValue() {
		return CBConfigs.SERVER.experience.smallBudNuggetValue.get();
	}

	public static int buddingMatureXp() {
		return clusterNuggetValue() * xpPerNugget();
	}

	public static int buddingGrowthChance() {
		return CBConfigs.SERVER.experience.buddingGrowthChance.get();
	}

	public static int tankMaxWidth() {
		return CBConfigs.SERVER.experience.tankMaxWidth.get();
	}

	public static int tankMaxHeight() {
		return CBConfigs.SERVER.experience.tankMaxHeight.get();
	}

	public static int pumpRange() {
		return CBConfigs.SERVER.experience.pumpRange.get();
	}

	public static int clusterMaxOrbsPerPinch() {
		return CBConfigs.SERVER.experience.clusterMaxOrbsPerPinch.get();
	}

	public static int clusterMinXpPerSplitOrb() {
		return CBConfigs.SERVER.experience.clusterMinXpPerSplitOrb.get();
	}
}
