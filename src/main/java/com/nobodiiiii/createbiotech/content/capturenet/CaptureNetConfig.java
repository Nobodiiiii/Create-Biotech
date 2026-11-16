package com.nobodiiiii.createbiotech.content.capturenet;

import java.util.EnumMap;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CaptureNetConfig {
	public final EnumMap<CaptureNetTier, ModConfigSpec.DoubleValue> healthLimits =
		new EnumMap<>(CaptureNetTier.class);
	public final ModConfigSpec.IntValue fastestEscapeSeconds;
	public final ModConfigSpec.IntValue slowestEscapeSeconds;
	public final ModConfigSpec.DoubleValue escapeDurabilityFraction;
	public final ModConfigSpec.IntValue escapeCooldownSeconds;

	public CaptureNetConfig(ModConfigSpec.Builder builder) {
		builder.comment("Reusable capture nets. Health is measured in hit points (2 HP = 1 heart).")
			.push("captureNet");
		for (CaptureNetTier tier : CaptureNetTier.values())
			healthLimits.put(tier, builder.comment("Maximum current health that this net can capture.")
				.defineInRange(tier.id() + "MaxHealth", tier.defaultHealthLimit(), 0.5, 1024.0));
		fastestEscapeSeconds = builder.comment("Escape time at the net's health limit, in seconds.")
			.defineInRange("fastestEscapeSeconds", 10, 1, 3600);
		slowestEscapeSeconds = builder.comment("Escape time as captured health approaches zero, in seconds. "
			+ "The larger of the two escape-time settings is always used as the slow end.")
			.defineInRange("slowestEscapeSeconds", 60, 1, 3600);
		escapeDurabilityFraction = builder.comment("Fraction of maximum durability lost after a successful escape, rounded up. Unbreaking applies normally.")
			.defineInRange("escapeDurabilityFraction", 0.1, 0.0, 1.0);
		escapeCooldownSeconds = builder.comment("Cooldown after a successful escape, in seconds.")
			.defineInRange("escapeCooldownSeconds", 10, 0, 3600);
		builder.pop();
	}
}
