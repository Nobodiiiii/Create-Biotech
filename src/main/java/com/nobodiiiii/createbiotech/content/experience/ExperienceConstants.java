package com.nobodiiiii.createbiotech.content.experience;

import com.nobodiiiii.createbiotech.registry.CBConfigs;

public final class ExperienceConstants {
	private ExperienceConstants() {
	}

	public static int xpPerNugget() {
		return CBConfigs.SERVER.experience.xpPerNugget.get();
	}

	public static int chamberCacheCapacity() {
		return CBConfigs.SERVER.evokerEnchantingChamber.cacheCapacity.get();
	}

	public static int chamberFluidPerLevel() {
		return CBConfigs.SERVER.evokerEnchantingChamber.fluidPerLevel.get();
	}

	public static int clusterXpValue() {
		return CBConfigs.SERVER.experience.clusterXpValue.get();
	}

	public static int largeBudXpValue() {
		return CBConfigs.SERVER.experience.largeBudXpValue.get();
	}

	public static int mediumBudXpValue() {
		return CBConfigs.SERVER.experience.mediumBudXpValue.get();
	}

	public static int smallBudXpValue() {
		return CBConfigs.SERVER.experience.smallBudXpValue.get();
	}

	public static int buddingMatureXp() {
		return clusterXpValue();
	}

	public static int buddingGrowthChance() {
		return CBConfigs.SERVER.experience.buddingGrowthChance.get();
	}

	public static int clusterMaxOrbsPerPinch() {
		return CBConfigs.SERVER.experience.clusterMaxOrbsPerPinch.get();
	}

	public static int clusterMinXpPerSplitOrb() {
		return CBConfigs.SERVER.experience.clusterMinXpPerSplitOrb.get();
	}
}
