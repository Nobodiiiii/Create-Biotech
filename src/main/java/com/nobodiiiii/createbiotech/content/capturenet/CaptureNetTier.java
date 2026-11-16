package com.nobodiiiii.createbiotech.content.capturenet;

import java.util.Locale;

import com.nobodiiiii.createbiotech.registry.CBConfigs;

public enum CaptureNetTier {
	WOODEN(8), STONE(12), IRON(20), GOLDEN(16), DIAMOND(30), NETHERITE(40);

	private final double defaultHealthLimit;

	CaptureNetTier(double defaultHealthLimit) {
		this.defaultHealthLimit = defaultHealthLimit;
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
