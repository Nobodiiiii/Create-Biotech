package com.nobodiiiii.createbiotech.registry;

import net.neoforged.neoforge.common.ModConfigSpec;
import static com.nobodiiiii.createbiotech.registry.CBConfigs.*;

/** Feature configuration definitions; CBConfigs retains its public section types. */
public final class CBMachineConfigSections {
	private CBMachineConfigSections() {}


	public static class ExperiencePump {
		public final ModConfigSpec.BooleanValue allowPlayerXpDrain;
		public final ModConfigSpec.BooleanValue allowOrbDrain;
		public final ModConfigSpec.BooleanValue allowItemXpDrain;
		public final ModConfigSpec.IntValue maxTransferPerTick;
		public final ModConfigSpec.DoubleValue nozzleAttractionRangeAt256Rpm;
		public final ModConfigSpec.BooleanValue emitOrbsFromOpenPipes;

		ExperiencePump(ModConfigSpec.Builder builder) {
			builder.push("experiencePump");
			allowPlayerXpDrain = builder
				.comment("Allow an open experience pump input to drain experience from players.")
				.define("allowPlayerXpDrain", true);
			allowOrbDrain = builder
				.comment("Allow an open experience pump input to drain and attract experience orbs.")
				.define("allowOrbDrain", true);
			allowItemXpDrain = builder
				.comment("Allow experience pumps to consume supported experience items from inventories.")
				.define("allowItemXpDrain", true);
			maxTransferPerTick = builder
				.comment("Maximum experience points transferred by one pump per tick. 0 keeps the RPM-derived rate unlimited.")
				.defineInRange("maxTransferPerTick", 0, 0, Integer.MAX_VALUE);
			nozzleAttractionRangeAt256Rpm = builder
				.comment("Experience-orb attraction radius, in blocks, for an input nozzle at 256 RPM.")
				.defineInRange("nozzleAttractionRangeAt256Rpm", 16.5d, 0.0d, 128.0d);
			emitOrbsFromOpenPipes = builder
				.comment("Convert experience fluid leaving open pipe ends back into experience orbs.")
				.define("emitOrbsFromOpenPipes", true);
			builder.pop();
		}
	}

	public static class BioPackager {
		public final ModConfigSpec.BooleanValue enableContraptionLethalCapture;
		public final ModConfigSpec.IntValue cycleTicks;

		BioPackager(ModConfigSpec.Builder builder) {
			builder.push("bioPackager");
			enableContraptionLethalCapture = builder
				.comment("Allow moving bio-packagers to replace a contraption's lethal mob hit with capture.")
				.define("enableContraptionLethalCapture", true);
			cycleTicks = builder
				.comment("Duration of one bio-packager tray movement. Values below 6 are invalid for its animation curve.")
				.defineInRange("cycleTicks", 20, 6, 1200);
			builder.pop();
		}
	}

	public static class PetriDish {
		public final ModConfigSpec.IntValue scanInterval;
		public final ModConfigSpec.IntValue scanRadius;
		public final ModConfigSpec.IntValue fluidPerHealth;
		public final ModConfigSpec.IntValue tankCapacity;
		public final ModConfigSpec.BooleanValue requireNearbyMatchingEntity;

		PetriDish(ModConfigSpec.Builder builder) {
			builder.push("petriDish");
			scanInterval = builder.defineInRange("scanInterval", 20, 1, Integer.MAX_VALUE);
			scanRadius = builder.defineInRange("scanRadius", 2, 0, 64);
			fluidPerHealth = builder.defineInRange("fluidPerHealth", 250, 1, Integer.MAX_VALUE);
			tankCapacity = builder.defineInRange("tankCapacity", 51200, 1, Integer.MAX_VALUE);
			requireNearbyMatchingEntity = builder.define("requireNearbyMatchingEntity", true);
			builder.pop();
		}
	}

	public static class SpiderAssemblyTable {
		public final ModConfigSpec.IntValue fluidCapacityPerLeg;
		public final ModConfigSpec.DoubleValue deployerBaseDuration;
		public final ModConfigSpec.IntValue sawFallbackDuration;
		public final ModConfigSpec.DoubleValue sawSpeedDivisor;

		SpiderAssemblyTable(ModConfigSpec.Builder builder) {
			builder.push("spiderAssemblyTable");
			fluidCapacityPerLeg = builder.defineInRange("fluidCapacityPerLeg", 1000, 1, Integer.MAX_VALUE);
			deployerBaseDuration = builder.defineInRange("deployerBaseDuration", 2000.0d, 1.0d, Double.MAX_VALUE);
			sawFallbackDuration = builder.defineInRange("sawFallbackDuration", 50, 1, Integer.MAX_VALUE);
			sawSpeedDivisor = builder.defineInRange("sawSpeedDivisor", 24.0d, 0.0001d, Double.MAX_VALUE);
			builder.pop();
		}
	}

	public static class SurgicalTable {
		public final ModConfigSpec.BooleanValue consumeInteractionItems;

		SurgicalTable(ModConfigSpec.Builder builder) {
			builder.push("surgicalTable");
			consumeInteractionItems = builder
				.comment("Whether successful surgical-table interactions consume held materials or damage tools. "
					+ "Anatomical joints are always consumed; cardboard boxes still change between their empty "
					+ "and filled states.")
				.define("consumeInteractionItems", false);
			builder.pop();
		}
	}

	public static class SquidPrinter {
		public final ModConfigSpec.IntValue tankCapacity;
		public final ModConfigSpec.IntValue finishingTicks;

		SquidPrinter(ModConfigSpec.Builder builder) {
			builder.push("squidPrinter");
			tankCapacity = builder.defineInRange("tankCapacity", 1000, 1, Integer.MAX_VALUE);
			finishingTicks = builder.defineInRange("finishingTicks", 5, 0, Integer.MAX_VALUE);
			builder.pop();
		}
	}

	public static class EvokerEnchantingChamber {
		public final ModConfigSpec.IntValue castDurationTicksPerLevel;
		public final ModConfigSpec.IntValue fluidPerLevel;
		public final ModConfigSpec.IntValue cacheCapacity;

		EvokerEnchantingChamber(ModConfigSpec.Builder builder) {
			builder.push("evokerEnchantingChamber");
			castDurationTicksPerLevel = builder.defineInRange("castDurationTicksPerLevel", 40, 1, Integer.MAX_VALUE);
			fluidPerLevel = builder.defineInRange("fluidPerLevel", 1000, 1, Integer.MAX_VALUE);
			cacheCapacity = builder.defineInRange("cacheCapacity", 4000, 1, Integer.MAX_VALUE);
			builder.pop();
		}
	}
}
