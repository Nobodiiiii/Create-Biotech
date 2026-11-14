package com.nobodiiiii.createbiotech.registry;

import net.neoforged.neoforge.common.ModConfigSpec;
import static com.nobodiiiii.createbiotech.registry.CBConfigs.*;

/** Feature configuration definitions; CBConfigs retains its public section types. */
public final class CBKineticConfigSections {
	private CBKineticConfigSections() {}


	public static class PowerBelt {
		public final ModConfigSpec.DoubleValue surfaceMetersPerSecondToRpm;
		public final ModConfigSpec.DoubleValue maxGeneratedRpm;
		public final ModConfigSpec.DoubleValue stressCapacityPerRpm;
		public final ModConfigSpec.DoubleValue maxStressCapacityPerSegment;
		public final ModConfigSpec.IntValue surfaceSpeedDetectionInterval;
		public final ModConfigSpec.DoubleValue maxPlayerSurfaceSpeed;

		PowerBelt(ModConfigSpec.Builder builder) {
			builder.push("powerBelt");
			surfaceMetersPerSecondToRpm = builder.defineInRange("surfaceMetersPerSecondToRpm", 24.0d, 0.0d, Double.MAX_VALUE);
			maxGeneratedRpm = builder.defineInRange("maxGeneratedRpm", 256.0d, 0.0d, Double.MAX_VALUE);
			stressCapacityPerRpm = builder.defineInRange("stressCapacityPerRpm", 4.0d, 0.0d, Double.MAX_VALUE);
			maxStressCapacityPerSegment = builder.defineInRange("maxStressCapacityPerSegment", 1024.0d, 0.0d, Double.MAX_VALUE);
			surfaceSpeedDetectionInterval = builder.defineInRange("surfaceSpeedDetectionInterval", 10, 1, 20 * 60);
			maxPlayerSurfaceSpeed = builder.defineInRange("maxPlayerSurfaceSpeed", 1.0d, 0.0d, Double.MAX_VALUE);
			builder.pop();
		}
	}

	public static class Automation {
		Automation(ModConfigSpec.Builder builder) {
			builder.push("automation");
			builder.pop();
		}
	}

	public static class UniversalJoint {
		private static final double DEFAULT_STRAIN_START_DISTANCE = 4.675d;
		private static final int LEGACY_DEFAULT_MAX_CONNECTION_RANGE = 2;

		@Deprecated(forRemoval = true)
		public final ModConfigSpec.IntValue maxConnectionRange;
		public final ModConfigSpec.IntValue itemCooldownTicks;
		public final ModConfigSpec.DoubleValue strainStartDistance;
		public final ModConfigSpec.DoubleValue disconnectDistance;
		public final ModConfigSpec.DoubleValue peakTension;
		public final ModConfigSpec.DoubleValue separationDamping;
		public final ModConfigSpec.DoubleValue endpointAirDrag;
		public final ModConfigSpec.DoubleValue maxImpulse;
		public final ModConfigSpec.DoubleValue shaftSlowdownMultiplier;

		UniversalJoint(ModConfigSpec.Builder builder) {
			builder.push("universalJoint");
			maxConnectionRange = builder
				.comment("Deprecated compatibility value. Custom values migrate to strainStartDistance "
					+ "while the new setting remains at its default.")
				.defineInRange("maxConnectionRange", LEGACY_DEFAULT_MAX_CONNECTION_RANGE, 0, 64);
			itemCooldownTicks = builder.defineInRange("itemCooldownTicks", 5, 0, Integer.MAX_VALUE);
			strainStartDistance = builder
				.comment("World-space placement/repair radius and distance where stretching begins, in blocks.")
				.defineInRange("strainStartDistance", DEFAULT_STRAIN_START_DISTANCE, 0.0d, 128.0d);
			disconnectDistance = builder
				.comment("World-space distance that breaks an overstretched joint, in blocks.")
				.defineInRange("disconnectDistance", 5.5d, 0.01d, 128.0d);
			peakTension = builder
				.comment("Maximum spring force reached at the disconnect distance.")
				.defineInRange("peakTension", 8192.0d, 0.0d, 1000000.0d);
			separationDamping = builder
				.comment("Radial damping force per block/second of relative endpoint speed.")
				.defineInRange("separationDamping", 320.0d, 0.0d, 100000.0d);
			endpointAirDrag = builder
				.comment("Directionless Sable air-drag coefficient contributed by each joint endpoint block. "
					+ "Sable scales it by local air pressure and physics timestep.")
				.defineInRange("endpointAirDrag", 0.75d, 0.0d, 16.0d);
			maxImpulse = builder
				.comment("Maximum elastic impulse applied to either structure in one physics step.")
				.defineInRange("maxImpulse", 1024.0d, 0.01d, 1000000.0d);
			shaftSlowdownMultiplier = builder
				.comment("Per-axis player movement multiplier while intersecting the slime shaft. "
					+ "Use 1.0 to disable the slowdown; lower values apply stronger slowdown.")
				.defineInRange("shaftSlowdownMultiplier", 0.4d, 0.01d, 1.0d);
			builder.pop();
		}

		public double effectiveStrainStartDistance() {
			return selectStrainStartDistance(strainStartDistance.get(),
				maxConnectionRange.get());
		}

		static double selectStrainStartDistance(double configured, int legacy) {
			if (Double.compare(configured, DEFAULT_STRAIN_START_DISTANCE) == 0
				&& legacy != LEGACY_DEFAULT_MAX_CONNECTION_RANGE)
				return Math.max(0.0d, legacy);
			return Math.max(0.0d, configured);
		}

		public double effectiveDisconnectRange() {
			return Math.max(0.01d, Math.max(disconnectDistance.get(),
				effectiveStrainStartDistance() + 0.01d));
		}
	}

	public static class ClientUniversalJoint {
		public final ModConfigSpec.IntValue previewRange;

		ClientUniversalJoint(ModConfigSpec.Builder builder) {
			builder.push("universalJoint");
			previewRange = builder.defineInRange("previewRange", 16, 0, 256);
			builder.pop();
		}
	}

	public static class BoneRatchet {
		public final ModConfigSpec.DoubleValue fallbackJamStressImpact;
		public final ModConfigSpec.DoubleValue creativeMotorMargin;

		BoneRatchet(ModConfigSpec.Builder builder) {
			builder.push("boneRatchet");
			fallbackJamStressImpact = builder.defineInRange("fallbackJamStressImpact", 20000.0d, 0.0d, Double.MAX_VALUE);
			creativeMotorMargin = builder.defineInRange("creativeMotorMargin", 1024.0d, 0.0d, Double.MAX_VALUE);
			builder.pop();
		}
	}

	public static class SlimeClutch {
		public final ModConfigSpec.IntValue recheckPeriod;
		public final ModConfigSpec.IntValue maxWalk;
		public final ModConfigSpec.BooleanValue enableSoftOverloadCheck;

		SlimeClutch(ModConfigSpec.Builder builder) {
			builder.push("slimeClutch");
			recheckPeriod = builder.defineInRange("recheckPeriod", 20, 1, Integer.MAX_VALUE);
			maxWalk = builder.defineInRange("maxWalk", 1024, 1, Integer.MAX_VALUE);
			enableSoftOverloadCheck = builder.define("enableSoftOverloadCheck", true);
			builder.pop();
		}
	}

	public static class LiquidLivingSlime {
		public final ModConfigSpec.IntValue sourceHitsToBreak;
		public final ModConfigSpec.BooleanValue dropSlimeBallWhenSourceBreaks;

		LiquidLivingSlime(ModConfigSpec.Builder builder) {
			builder.push("liquidLivingSlime");
			sourceHitsToBreak = builder.defineInRange("sourceHitsToBreak", 4, 1, 64);
			dropSlimeBallWhenSourceBreaks = builder.define("dropSlimeBallWhenSourceBreaks", true);
			builder.pop();
		}
	}

	public static class FixedCarrotFishingRod {
		public final ModConfigSpec.DoubleValue searchRange;
		public final ModConfigSpec.DoubleValue speedModifier;
		public final ModConfigSpec.DoubleValue stopDistance;
		public final ModConfigSpec.IntValue searchCooldown;
		public final ModConfigSpec.IntValue stopCooldown;

		FixedCarrotFishingRod(ModConfigSpec.Builder builder) {
			builder.push("fixedCarrotFishingRod");
			searchRange = builder.defineInRange("searchRange", 10.0d, 0.0d, 128.0d);
			speedModifier = builder.defineInRange("speedModifier", 1.2d, 0.0d, Double.MAX_VALUE);
			stopDistance = builder.defineInRange("stopDistance", 2.5d, 0.0d, 64.0d);
			searchCooldown = builder.defineInRange("searchCooldown", 20, 0, Integer.MAX_VALUE);
			stopCooldown = builder.defineInRange("stopCooldown", 100, 0, Integer.MAX_VALUE);
			builder.pop();
		}
	}

	public static class BeltParticles {
		public final ModConfigSpec.DoubleValue slimeBeltBaseChance;
		public final ModConfigSpec.DoubleValue slimeBeltLengthChance;
		public final ModConfigSpec.DoubleValue slimeBeltSpeedChance;
		public final ModConfigSpec.DoubleValue slimeBeltMaxChance;
		public final ModConfigSpec.DoubleValue magmaBeltBaseChance;
		public final ModConfigSpec.DoubleValue magmaBeltLengthChance;
		public final ModConfigSpec.DoubleValue magmaBeltMaxChance;

		BeltParticles(ModConfigSpec.Builder builder) {
			builder.push("beltParticles");
			slimeBeltBaseChance = builder.defineInRange("slimeBeltBaseChance", 0.035d, 0.0d, 1.0d);
			slimeBeltLengthChance = builder.defineInRange("slimeBeltLengthChance", 0.008d, 0.0d, 1.0d);
			slimeBeltSpeedChance = builder.defineInRange("slimeBeltSpeedChance", 0.12d, 0.0d, 1.0d);
			slimeBeltMaxChance = builder.defineInRange("slimeBeltMaxChance", 0.18d, 0.0d, 1.0d);
			magmaBeltBaseChance = builder.defineInRange("magmaBeltBaseChance", 0.025d, 0.0d, 1.0d);
			magmaBeltLengthChance = builder.defineInRange("magmaBeltLengthChance", 0.006d, 0.0d, 1.0d);
			magmaBeltMaxChance = builder.defineInRange("magmaBeltMaxChance", 0.16d, 0.0d, 1.0d);
			builder.pop();
		}
	}
}
