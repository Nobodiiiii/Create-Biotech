package com.nobodiiiii.createbiotech.content.capturenet;

import java.util.Locale;

import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.world.item.Tiers;

public enum CaptureNetTier {
	WOODEN(Tiers.WOOD, 8), STONE(Tiers.STONE, 12), IRON(Tiers.IRON, 20),
	GOLDEN(Tiers.GOLD, 16), DIAMOND(Tiers.DIAMOND, 30), NETHERITE(Tiers.NETHERITE, 40);

	private final Tiers toolTier;
	private final double defaultHealthLimit;

	CaptureNetTier(Tiers toolTier, double defaultHealthLimit) {
		this.toolTier = toolTier;
		this.defaultHealthLimit = defaultHealthLimit;
	}

	public Tiers toolTier() {
		return toolTier;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public double defaultHealthLimit() {
		return defaultHealthLimit;
	}

	public double healthLimit() {
		var value = CBConfigs.SERVER.captureNet.healthLimits.get(this);
		return CBConfigs.SERVER_SPEC.isLoaded() ? value.get() : value.getDefault();
	}
}
