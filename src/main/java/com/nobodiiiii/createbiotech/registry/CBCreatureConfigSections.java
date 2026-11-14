package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStatParameters;
import java.util.List;
import java.util.EnumMap;
import com.nobodiiiii.createbiotech.foundation.feature.CBFeature;
import net.neoforged.neoforge.common.ModConfigSpec;
import static com.nobodiiiii.createbiotech.registry.CBConfigs.*;

/** Feature configuration definitions; CBConfigs retains its public section types. */
public final class CBCreatureConfigSections {
	private CBCreatureConfigSections() {}


	public static class BionicStatsConfig {
		public final java.util.Map<String, ModConfigSpec.DoubleValue> values = new java.util.LinkedHashMap<>();
		BionicStatsConfig(ModConfigSpec.Builder builder) {
			builder.comment("Anatomical base-stat calibration. Formula overrides belong in data/create_biotech/bionic_stats/. Invalid anchor combinations retain the previous valid calibration.").push("bionicStats");
			for (var setting : BionicStatParameters.SETTINGS)
				values.put(setting.key(), builder.comment(setting.comment()).defineInRange(setting.key(), setting.value(), setting.min(), setting.max()));
			builder.pop();
		}
	}

	public static class Features {
		private final EnumMap<CBFeature, ModConfigSpec.BooleanValue> enabled = new EnumMap<>(CBFeature.class);

		Features(ModConfigSpec.Builder builder) {
			builder.comment("Master content-family switches. Disabled families keep their registry entries and existing "
				+ "world blocks, but their output recipes will not load and players cannot place new blocks. "
				+ "Run /reload or restart the server after changing these values.")
				.push("features");
			for (CBFeature feature : CBFeature.values())
				enabled.put(feature, builder.define(feature.serializedName(), true));
			builder.pop();
		}

		public boolean isEnabled(CBFeature feature) {
			ModConfigSpec.BooleanValue value = enabled.get(feature);
			// WorldStem recipes are decoded before the per-world SERVER config is loaded.
			// The actual values are applied by CBFeatureRecipeFilter once the server starts.
			return SERVER_SPEC.isLoaded() ? value.get() : value.getDefault();
		}
	}

	public static class FrogStomach {
		public final ModConfigSpec.IntValue width;
		public final ModConfigSpec.IntValue height;

		FrogStomach(ModConfigSpec.Builder builder) {
			builder.push("frogStomach");
			width = builder.defineInRange("width", 48, 16, 256);
			height = builder.defineInRange("height", 32, 16, 256);
			builder.pop();
		}
	}

	public static class Experience {
		public final ModConfigSpec.IntValue xpPerNugget;
		public final ModConfigSpec.IntValue clusterXpValue;
		public final ModConfigSpec.IntValue largeBudXpValue;
		public final ModConfigSpec.IntValue mediumBudXpValue;
		public final ModConfigSpec.IntValue smallBudXpValue;
		public final ModConfigSpec.IntValue buddingGrowthChance;
		public final ModConfigSpec.IntValue clusterMaxOrbsPerPinch;
		public final ModConfigSpec.IntValue clusterMinXpPerSplitOrb;

		Experience(ModConfigSpec.Builder builder) {
			builder.push("experience");
			xpPerNugget = builder.defineInRange("xpPerNugget", 3, 1, Integer.MAX_VALUE);
			clusterXpValue = builder.defineInRange("clusterXpValue", 128, 1, Integer.MAX_VALUE);
			largeBudXpValue = builder.defineInRange("largeBudXpValue", 64, 1, Integer.MAX_VALUE);
			mediumBudXpValue = builder.defineInRange("mediumBudXpValue", 32, 1, Integer.MAX_VALUE);
			smallBudXpValue = builder.defineInRange("smallBudXpValue", 16, 1, Integer.MAX_VALUE);
			buddingGrowthChance = builder.defineInRange("buddingGrowthChance", 20, 1, Integer.MAX_VALUE);
			clusterMaxOrbsPerPinch = builder.defineInRange("clusterMaxOrbsPerPinch", 5, 1, 64);
			clusterMinXpPerSplitOrb = builder.defineInRange("clusterMinXpPerSplitOrb", 37, 1, Integer.MAX_VALUE);
			builder.pop();
		}
	}

	public static class CreeperBlastChamber {
		public final ModConfigSpec.IntValue minSize;
		public final ModConfigSpec.IntValue maxSize;
		public final ModConfigSpec.IntValue overloadThresholdRpm;
		public final ModConfigSpec.IntValue overloadPointsCap;
		public final ModConfigSpec.IntValue overloadDecayPointsPerSecond;
		public final ModConfigSpec.IntValue overloadTntEquivalentPerCreeper;
		public final ModConfigSpec.IntValue chargedCreeperEquivalentMultiplier;
		public final ModConfigSpec.DoubleValue tntExplosionPower;
		public final ModConfigSpec.IntValue readyOutputTimeout;
		public final ModConfigSpec.BooleanValue enableOverloadExplosions;

		CreeperBlastChamber(ModConfigSpec.Builder builder) {
			builder.push("creeperBlastChamber");
			minSize = builder.defineInRange("minSize", 3, 1, 16);
			maxSize = builder.defineInRange("maxSize", 5, 1, 32);
			overloadThresholdRpm = builder.defineInRange("overloadThresholdRpm", 128, 1, Integer.MAX_VALUE);
			overloadPointsCap = builder.defineInRange("overloadPointsCap", 128 * 64, 1, Integer.MAX_VALUE);
			overloadDecayPointsPerSecond = builder.defineInRange("overloadDecayPointsPerSecond", 128, 0, Integer.MAX_VALUE);
			overloadTntEquivalentPerCreeper = builder.defineInRange("overloadTntEquivalentPerCreeper", 2, 0, Integer.MAX_VALUE);
			chargedCreeperEquivalentMultiplier = builder.defineInRange("chargedCreeperEquivalentMultiplier", 2, 1, Integer.MAX_VALUE);
			tntExplosionPower = builder.defineInRange("tntExplosionPower", 4.0d, 0.0d, Double.MAX_VALUE);
			readyOutputTimeout = builder.defineInRange("readyOutputTimeout", 20 * 5, 1, Integer.MAX_VALUE);
			enableOverloadExplosions = builder.define("enableOverloadExplosions", true);
			builder.pop();
		}
	}

	public static class ClientCreeperBlastChamber {
		public final ModConfigSpec.BooleanValue enableExplosionParticles;

		ClientCreeperBlastChamber(ModConfigSpec.Builder builder) {
			builder.push("creeperBlastChamber");
			enableExplosionParticles = builder.define("enableExplosionParticles", true);
			builder.pop();
		}
	}

	public static class SlimeMimic {
		public final ModConfigSpec.IntValue hauntCycleTicks;
		public final ModConfigSpec.BooleanValue rewriteVillagerTrades;
		public final ModConfigSpec.IntValue villagerTradeMinSlimeBalls;
		public final ModConfigSpec.IntValue villagerTradeMaxSlimeBalls;
		public final ModConfigSpec.BooleanValue allowSpawnInjection;
		public final ModConfigSpec.EnumValue<EntityListMode> entityListMode;
		public final ModConfigSpec.ConfigValue<List<? extends String>> entityAllowlist;
		public final ModConfigSpec.ConfigValue<List<? extends String>> entityDenylist;

		SlimeMimic(ModConfigSpec.Builder builder) {
			builder.push("slimeMimic");
			hauntCycleTicks = builder.defineInRange("hauntCycleTicks", 100, 1, Integer.MAX_VALUE);
			rewriteVillagerTrades = builder.define("rewriteVillagerTrades", true);
			villagerTradeMinSlimeBalls = builder.defineInRange("villagerTradeMinSlimeBalls", 1, 1, 64);
			villagerTradeMaxSlimeBalls = builder.defineInRange("villagerTradeMaxSlimeBalls", 3, 1, 64);
			allowSpawnInjection = builder.define("allowSpawnInjection", true);
			entityListMode = builder.defineEnum("entityListMode", EntityListMode.ALLOW_ALL);
			entityAllowlist = defineResourceLocationList(builder, "entityAllowlist", List.of());
			entityDenylist = defineResourceLocationList(builder, "entityDenylist", List.of());
			builder.pop();
		}
	}

	public static class GhastHotAirBalloon {
		public static final int MAGNET_KEEP_ALIVE_PERIOD_TICKS = 4;
		/** Allows two consecutive keep-alive packets to be delayed or lost before expiry. */
		public static final int MIN_MAGNET_TIMEOUT_TICKS = MAGNET_KEEP_ALIVE_PERIOD_TICKS * 3;

		public final ModConfigSpec.DoubleValue forwardAcceleration;
		public final ModConfigSpec.DoubleValue backwardAcceleration;
		public final ModConfigSpec.DoubleValue verticalAcceleration;
		public final ModConfigSpec.DoubleValue horizontalDrag;
		public final ModConfigSpec.DoubleValue verticalDrag;
		public final ModConfigSpec.DoubleValue maxHorizontalSpeed;
		public final ModConfigSpec.DoubleValue maxVerticalSpeed;
		public final ModConfigSpec.DoubleValue turnAcceleration;
		public final ModConfigSpec.DoubleValue turnBrake;
		public final ModConfigSpec.DoubleValue turnDirectionChangeBrake;
		public final ModConfigSpec.DoubleValue maxTurnSpeed;
		public final ModConfigSpec.IntValue inputTimeoutTicks;
		public final ModConfigSpec.IntValue magnetTimeoutTicks;
		public final ModConfigSpec.DoubleValue magnetBrakeDistance;
		public final ModConfigSpec.DoubleValue magnetMaxDistance;
		public final ModConfigSpec.DoubleValue assemblyStationSpeed;
		public final ModConfigSpec.IntValue attractPeriodTicks;
		public final ModConfigSpec.DoubleValue maxVelocityForAttractSqr;

		GhastHotAirBalloon(ModConfigSpec.Builder builder) {
			builder.push("ghastHotAirBalloon");
			forwardAcceleration = builder.defineInRange("forwardAcceleration", 0.04d, 0.0d, Double.MAX_VALUE);
			backwardAcceleration = builder.defineInRange("backwardAcceleration", 0.02d, 0.0d, Double.MAX_VALUE);
			verticalAcceleration = builder.defineInRange("verticalAcceleration", 0.03d, 0.0d, Double.MAX_VALUE);
			horizontalDrag = builder.defineInRange("horizontalDrag", 0.9d, 0.0d, 1.0d);
			verticalDrag = builder.defineInRange("verticalDrag", 0.85d, 0.0d, 1.0d);
			maxHorizontalSpeed = builder.defineInRange("maxHorizontalSpeed", 0.35d, 0.0d, Double.MAX_VALUE);
			maxVerticalSpeed = builder.defineInRange("maxVerticalSpeed", 0.25d, 0.0d, Double.MAX_VALUE);
			turnAcceleration = builder.defineInRange("turnAcceleration", 1.0d, 0.0d, Double.MAX_VALUE);
			turnBrake = builder.defineInRange("turnBrake", 4.0d, 0.0d, Double.MAX_VALUE);
			turnDirectionChangeBrake = builder.defineInRange("turnDirectionChangeBrake", 6.0d, 0.0d, Double.MAX_VALUE);
			maxTurnSpeed = builder.defineInRange("maxTurnSpeed", 6.0d, 0.0d, Double.MAX_VALUE);
			inputTimeoutTicks = builder.defineInRange("inputTimeoutTicks", 8, 1, Integer.MAX_VALUE);
			magnetTimeoutTicks = builder.defineInRange("magnetTimeoutTicks", 12,
				MIN_MAGNET_TIMEOUT_TICKS, Integer.MAX_VALUE);
			magnetBrakeDistance = builder.defineInRange("magnetBrakeDistance", 2.5d, 0.0001d, Double.MAX_VALUE);
			magnetMaxDistance = builder.defineInRange("magnetMaxDistance", 32.0d, 0.0d, Double.MAX_VALUE);
			assemblyStationSpeed = builder.defineInRange("assemblyStationSpeed", 0.5d, 0.0001d, Double.MAX_VALUE);
			attractPeriodTicks = builder.defineInRange("attractPeriodTicks", 20, 1, Integer.MAX_VALUE);
			maxVelocityForAttractSqr = builder.defineInRange("maxVelocityForAttractSqr", 1.0E-3d, 0.0d, Double.MAX_VALUE);
			builder.pop();
		}
	}

	public static class ButterCat {
		public final ModConfigSpec.IntValue maxButterCount;
		public final ModConfigSpec.IntValue butterDecayTicks;
		public final ModConfigSpec.IntValue butterForMaxRpm;
		public final ModConfigSpec.DoubleValue rpmPerButter;
		public final ModConfigSpec.DoubleValue maxGeneratedRpm;
		public final ModConfigSpec.DoubleValue stressCapacityPerRpm;
		public final ModConfigSpec.DoubleValue maxStressCapacity;
		public final ModConfigSpec.DoubleValue rotationAngularSpeed;
		public final ModConfigSpec.IntValue butterNutrition;
		public final ModConfigSpec.DoubleValue butterSaturation;
		public final ModConfigSpec.IntValue superButterNutrition;
		public final ModConfigSpec.DoubleValue superButterSaturation;
		public final ModConfigSpec.IntValue superButterRotationDuration;
		public final ModConfigSpec.IntValue superButterRotationAmplifier;
		public final ModConfigSpec.IntValue superButterLevitationDuration;
		public final ModConfigSpec.IntValue superButterLevitationAmplifier;
		public final ModConfigSpec.IntValue incompleteSuperButterNutrition;
		public final ModConfigSpec.DoubleValue incompleteSuperButterSaturation;
		public final ModConfigSpec.IntValue incompleteSuperButterRotationDuration;
		public final ModConfigSpec.IntValue incompleteSuperButterRotationAmplifier;

		ButterCat(ModConfigSpec.Builder builder) {
			builder.push("butterCat");
			maxButterCount = builder.defineInRange("maxButterCount", 16, 1, 8192);
			butterDecayTicks = builder.defineInRange("butterDecayTicks", 20 * 16, 1, Integer.MAX_VALUE);
			butterForMaxRpm = builder.defineInRange("butterForMaxRpm", 16, 1, Integer.MAX_VALUE);
			rpmPerButter = builder.defineInRange("rpmPerButter", 16.0d, 0.0d, Double.MAX_VALUE);
			maxGeneratedRpm = builder.defineInRange("maxGeneratedRpm", 256.0d, 0.0d, Double.MAX_VALUE);
			stressCapacityPerRpm = builder.defineInRange("stressCapacityPerRpm", 64.0d, 0.0d, Double.MAX_VALUE);
			maxStressCapacity = builder.defineInRange("maxStressCapacity", 16384.0d, 0.0d, Double.MAX_VALUE);
			rotationAngularSpeed = builder.defineInRange("rotationAngularSpeed", Math.PI / 2.0d, 0.0d, Double.MAX_VALUE);
			butterNutrition = builder.defineInRange("butterNutrition", 1, 0, 20);
			butterSaturation = builder.defineInRange("butterSaturation", 0.5d, 0.0d, 20.0d);
			superButterNutrition = builder.defineInRange("superButterNutrition", 18, 0, 20);
			superButterSaturation = builder.defineInRange("superButterSaturation", 0.5d, 0.0d, 20.0d);
			superButterRotationDuration = builder.defineInRange("superButterRotationDuration", 90, 0, Integer.MAX_VALUE);
			superButterRotationAmplifier = builder.defineInRange("superButterRotationAmplifier", 1, 0, 255);
			superButterLevitationDuration = builder.defineInRange("superButterLevitationDuration", 90, 0, Integer.MAX_VALUE);
			superButterLevitationAmplifier = builder.defineInRange("superButterLevitationAmplifier", 0, 0, 255);
			incompleteSuperButterNutrition = builder.defineInRange("incompleteSuperButterNutrition", 2, 0, 20);
			incompleteSuperButterSaturation = builder.defineInRange("incompleteSuperButterSaturation", 0.5d, 0.0d, 20.0d);
			incompleteSuperButterRotationDuration =
				builder.defineInRange("incompleteSuperButterRotationDuration", 30, 0, Integer.MAX_VALUE);
			incompleteSuperButterRotationAmplifier =
				builder.defineInRange("incompleteSuperButterRotationAmplifier", 2, 0, 255);
			builder.pop();
		}
	}

	public static class SchrodingersCat {
		public final ModConfigSpec.IntValue defaultInterval;
		public final ModConfigSpec.IntValue maxInterval;
		public final ModConfigSpec.DoubleValue highSignalChance;

		SchrodingersCat(ModConfigSpec.Builder builder) {
			builder.push("schrodingersCat");
			defaultInterval = builder.defineInRange("defaultInterval", 20, 1, Integer.MAX_VALUE);
			maxInterval = builder.defineInRange("maxInterval", 60 * 20 * 60, 1, Integer.MAX_VALUE);
			highSignalChance = builder.defineInRange("highSignalChance", 0.5d, 0.0d, 1.0d);
			builder.pop();
		}
	}
}
