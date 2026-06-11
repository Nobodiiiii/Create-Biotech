package com.nobodiiiii.createbiotech.content.experience;

import com.nobodiiiii.createbiotech.registry.CBConfigs;

public final class ExperienceConstants {
	private ExperienceConstants() {
	}

	public static int xpPerNugget() {
		return CBConfigs.COMMON.experience.xpPerNugget.get();
	}

	public static int tankCapacityPerBlock() {
		return CBConfigs.COMMON.experience.tankCapacityPerBlock.get();
	}

	public static int chamberCacheCapacity() {
		return CBConfigs.COMMON.evokerEnchantingChamber.cacheCapacity.get();
	}

	public static int chamberXpPerLevel() {
		return CBConfigs.COMMON.evokerEnchantingChamber.xpPerLevel.get();
	}

	public static float pumpXpPerRpmPerSecond() {
		return (float) (CBConfigs.COMMON.experience.pumpXpPerRpmPerSecond.get()
			* CBConfigs.COMMON.experience.pumpEfficiency.get());
	}

	public static float speedNormalizationRpm() {
		return CBConfigs.COMMON.experience.speedNormalizationRpm.get().floatValue();
	}

	public static int clusterNuggetValue() {
		return CBConfigs.COMMON.experience.clusterNuggetValue.get();
	}

	public static int largeBudNuggetValue() {
		return CBConfigs.COMMON.experience.largeBudNuggetValue.get();
	}

	public static int mediumBudNuggetValue() {
		return CBConfigs.COMMON.experience.mediumBudNuggetValue.get();
	}

	public static int smallBudNuggetValue() {
		return CBConfigs.COMMON.experience.smallBudNuggetValue.get();
	}

	public static int buddingMatureXp() {
		return clusterNuggetValue() * xpPerNugget();
	}

	public static int buddingGrowthChance() {
		return CBConfigs.COMMON.experience.buddingGrowthChance.get();
	}

	public static int tankMaxWidth() {
		return CBConfigs.COMMON.experience.tankMaxWidth.get();
	}

	public static int tankMaxHeight() {
		return CBConfigs.COMMON.experience.tankMaxHeight.get();
	}

	public static int pumpRange() {
		return CBConfigs.COMMON.experience.pumpRange.get();
	}

	public static int clusterMaxOrbsPerPinch() {
		return CBConfigs.COMMON.experience.clusterMaxOrbsPerPinch.get();
	}

	public static int clusterMinXpPerSplitOrb() {
		return CBConfigs.COMMON.experience.clusterMinXpPerSplitOrb.get();
	}
}
