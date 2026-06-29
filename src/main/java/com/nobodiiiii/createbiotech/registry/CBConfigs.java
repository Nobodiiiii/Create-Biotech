package com.nobodiiiii.createbiotech.registry;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;

public class CBConfigs {
	public static final Client CLIENT;
	public static final ForgeConfigSpec CLIENT_SPEC;
	public static final Common COMMON;
	public static final ForgeConfigSpec COMMON_SPEC;
	public static final Server SERVER;
	public static final ForgeConfigSpec SERVER_SPEC;

	static {
		Pair<Client, ForgeConfigSpec> clientSpecPair =
			new ForgeConfigSpec.Builder().configure(Client::new);
		CLIENT = clientSpecPair.getLeft();
		CLIENT_SPEC = clientSpecPair.getRight();

		Pair<Common, ForgeConfigSpec> commonSpecPair =
			new ForgeConfigSpec.Builder().configure(Common::new);
		COMMON = commonSpecPair.getLeft();
		COMMON_SPEC = commonSpecPair.getRight();

		Pair<Server, ForgeConfigSpec> serverSpecPair =
			new ForgeConfigSpec.Builder().configure(Server::new);
		SERVER = serverSpecPair.getLeft();
		SERVER_SPEC = serverSpecPair.getRight();
	}

	private CBConfigs() {}

	public static void register() {
		ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
	}

	public enum EntityListMode {
		ALLOW_ALL,
		ALLOWLIST,
		DENYLIST
	}

	public static class Common {
		Common(ForgeConfigSpec.Builder builder) {
		}
	}

	public static class Client {
		public final ForgeConfigSpec.BooleanValue enableShulkerTeleporterCameraOffset;
		public final ForgeConfigSpec.BooleanValue enableShulkerTeleporterPlayerClipping;
		public final ClientCreeperBlastChamber creeperBlastChamber;
		public final ClientUniversalJoint universalJoint;
		public final BeltParticles beltParticles;

		Client(ForgeConfigSpec.Builder builder) {
			enableShulkerTeleporterCameraOffset = builder.define("enableShulkerTeleporterCameraOffset", true);
			enableShulkerTeleporterPlayerClipping = builder.define("enableShulkerTeleporterPlayerClipping", true);
			creeperBlastChamber = new ClientCreeperBlastChamber(builder);
			universalJoint = new ClientUniversalJoint(builder);
			beltParticles = new BeltParticles(builder);
		}
	}

	public static class Server {
		public final Experience experience;
		public final CreeperBlastChamber creeperBlastChamber;
		public final PowerBelt powerBelt;
		public final PetriDish petriDish;
		public final SpiderAssemblyTable spiderAssemblyTable;
		public final CardboardBox cardboardBox;
		public final SlimeMimic slimeMimic;
		public final GhastHotAirBalloon ghastHotAirBalloon;
		public final ButterCat butterCat;
		public final Automation automation;
		public final Wireless wireless;
		public final UniversalJoint universalJoint;
		public final SquidPrinter squidPrinter;
		public final EvokerEnchantingChamber evokerEnchantingChamber;
		public final SchrodingersCat schrodingersCat;
		public final BoneRatchet boneRatchet;
		public final BasinEntityProcessing basinEntityProcessing;
		public final SlimeClutch slimeClutch;
		public final LiquidLivingSlime liquidLivingSlime;
		public final FixedCarrotFishingRod fixedCarrotFishingRod;
		public final BufferPad bufferPad;
		public final ShulkerPackager shulkerPackager;

		Server(ForgeConfigSpec.Builder builder) {
			experience = new Experience(builder);
			creeperBlastChamber = new CreeperBlastChamber(builder);
			powerBelt = new PowerBelt(builder);
			petriDish = new PetriDish(builder);
			spiderAssemblyTable = new SpiderAssemblyTable(builder);
			cardboardBox = new CardboardBox(builder);
			slimeMimic = new SlimeMimic(builder);
			ghastHotAirBalloon = new GhastHotAirBalloon(builder);
			butterCat = new ButterCat(builder);
			automation = new Automation(builder);
			wireless = new Wireless(builder);
			universalJoint = new UniversalJoint(builder);
			squidPrinter = new SquidPrinter(builder);
			evokerEnchantingChamber = new EvokerEnchantingChamber(builder);
			schrodingersCat = new SchrodingersCat(builder);
			boneRatchet = new BoneRatchet(builder);
			basinEntityProcessing = new BasinEntityProcessing(builder);
			slimeClutch = new SlimeClutch(builder);
			liquidLivingSlime = new LiquidLivingSlime(builder);
			fixedCarrotFishingRod = new FixedCarrotFishingRod(builder);
			bufferPad = new BufferPad(builder);
			shulkerPackager = new ShulkerPackager(builder);
		}
	}

	public static class Experience {
		public final ForgeConfigSpec.IntValue xpPerNugget;
		public final ForgeConfigSpec.IntValue clusterXpValue;
		public final ForgeConfigSpec.IntValue largeBudXpValue;
		public final ForgeConfigSpec.IntValue mediumBudXpValue;
		public final ForgeConfigSpec.IntValue smallBudXpValue;
		public final ForgeConfigSpec.IntValue buddingGrowthChance;
		public final ForgeConfigSpec.IntValue clusterMaxOrbsPerPinch;
		public final ForgeConfigSpec.IntValue clusterMinXpPerSplitOrb;

		Experience(ForgeConfigSpec.Builder builder) {
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
		public final ForgeConfigSpec.IntValue minSize;
		public final ForgeConfigSpec.IntValue maxSize;
		public final ForgeConfigSpec.IntValue overloadThresholdRpm;
		public final ForgeConfigSpec.IntValue overloadPointsCap;
		public final ForgeConfigSpec.IntValue overloadDecayPointsPerSecond;
		public final ForgeConfigSpec.IntValue overloadTntEquivalentPerCreeper;
		public final ForgeConfigSpec.IntValue chargedCreeperEquivalentMultiplier;
		public final ForgeConfigSpec.DoubleValue tntExplosionPower;
		public final ForgeConfigSpec.IntValue readyOutputTimeout;
		public final ForgeConfigSpec.BooleanValue enableOverloadExplosions;

		CreeperBlastChamber(ForgeConfigSpec.Builder builder) {
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
		public final ForgeConfigSpec.BooleanValue enableExplosionParticles;

		ClientCreeperBlastChamber(ForgeConfigSpec.Builder builder) {
			builder.push("creeperBlastChamber");
			enableExplosionParticles = builder.define("enableExplosionParticles", true);
			builder.pop();
		}
	}

	public static class PowerBelt {
		public final ForgeConfigSpec.DoubleValue surfaceMetersPerSecondToRpm;
		public final ForgeConfigSpec.DoubleValue maxGeneratedRpm;
		public final ForgeConfigSpec.DoubleValue stressCapacityPerRpm;
		public final ForgeConfigSpec.DoubleValue maxStressCapacityPerSegment;
		public final ForgeConfigSpec.IntValue surfaceSpeedDetectionInterval;
		public final ForgeConfigSpec.DoubleValue maxPlayerSurfaceSpeed;

		PowerBelt(ForgeConfigSpec.Builder builder) {
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

	public static class PetriDish {
		public final ForgeConfigSpec.IntValue scanInterval;
		public final ForgeConfigSpec.IntValue scanRadius;
		public final ForgeConfigSpec.IntValue fluidPerHealth;
		public final ForgeConfigSpec.IntValue tankCapacity;
		public final ForgeConfigSpec.BooleanValue requireNearbyMatchingEntity;

		PetriDish(ForgeConfigSpec.Builder builder) {
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
		public final ForgeConfigSpec.IntValue fluidCapacityPerLeg;
		public final ForgeConfigSpec.DoubleValue deployerBaseDuration;
		public final ForgeConfigSpec.IntValue sawFallbackDuration;
		public final ForgeConfigSpec.DoubleValue sawSpeedDivisor;

		SpiderAssemblyTable(ForgeConfigSpec.Builder builder) {
			builder.push("spiderAssemblyTable");
			fluidCapacityPerLeg = builder.defineInRange("fluidCapacityPerLeg", 1000, 1, Integer.MAX_VALUE);
			deployerBaseDuration = builder.defineInRange("deployerBaseDuration", 2000.0d, 1.0d, Double.MAX_VALUE);
			sawFallbackDuration = builder.defineInRange("sawFallbackDuration", 50, 1, Integer.MAX_VALUE);
			sawSpeedDivisor = builder.defineInRange("sawSpeedDivisor", 24.0d, 0.0001d, Double.MAX_VALUE);
			builder.pop();
		}
	}

	public static class CardboardBox {
		public final ForgeConfigSpec.ConfigValue<List<? extends String>> smallBoxEntityAllowlist;
		public final ForgeConfigSpec.BooleanValue largeBoxCreativeOnly;
		public final ForgeConfigSpec.BooleanValue lethalCaptureEnabled;
		public final ForgeConfigSpec.EnumValue<EntityListMode> largeBoxEntityListMode;
		public final ForgeConfigSpec.ConfigValue<List<? extends String>> largeBoxEntityAllowlist;
		public final ForgeConfigSpec.ConfigValue<List<? extends String>> largeBoxEntityDenylist;

		CardboardBox(ForgeConfigSpec.Builder builder) {
			builder.push("cardboardBox");
			smallBoxEntityAllowlist = defineResourceLocationList(builder, "smallBoxEntityAllowlist", List.of(
				"minecraft:slime",
				"minecraft:cat",
				"minecraft:bat",
				"minecraft:chicken",
				"minecraft:rabbit",
				"minecraft:silverfish",
				"minecraft:endermite",
				"minecraft:bee",
				"minecraft:parrot",
				"minecraft:allay",
				"minecraft:frog",
				"minecraft:ocelot",
				"minecraft:vex",
				"minecraft:magma_cube"));
			largeBoxCreativeOnly = builder.define("largeBoxCreativeOnly", true);
			lethalCaptureEnabled = builder.define("lethalCaptureEnabled", true);
			largeBoxEntityListMode = builder.defineEnum("largeBoxEntityListMode", EntityListMode.ALLOW_ALL);
			largeBoxEntityAllowlist = defineResourceLocationList(builder, "largeBoxEntityAllowlist", List.of());
			largeBoxEntityDenylist = defineResourceLocationList(builder, "largeBoxEntityDenylist", List.of());
			builder.pop();
		}
	}

	public static class SlimeMimic {
		public final ForgeConfigSpec.IntValue hauntCycleTicks;
		public final ForgeConfigSpec.BooleanValue replaceDropsWithSlime;
		public final ForgeConfigSpec.BooleanValue rewriteVillagerTrades;
		public final ForgeConfigSpec.IntValue villagerTradeMinSlimeBalls;
		public final ForgeConfigSpec.IntValue villagerTradeMaxSlimeBalls;
		public final ForgeConfigSpec.BooleanValue allowSpawnInjection;
		public final ForgeConfigSpec.EnumValue<EntityListMode> entityListMode;
		public final ForgeConfigSpec.ConfigValue<List<? extends String>> entityAllowlist;
		public final ForgeConfigSpec.ConfigValue<List<? extends String>> entityDenylist;

		SlimeMimic(ForgeConfigSpec.Builder builder) {
			builder.push("slimeMimic");
			hauntCycleTicks = builder.defineInRange("hauntCycleTicks", 100, 1, Integer.MAX_VALUE);
			replaceDropsWithSlime = builder.define("replaceDropsWithSlime", true);
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
		public final ForgeConfigSpec.DoubleValue forwardAcceleration;
		public final ForgeConfigSpec.DoubleValue backwardAcceleration;
		public final ForgeConfigSpec.DoubleValue verticalAcceleration;
		public final ForgeConfigSpec.DoubleValue horizontalDrag;
		public final ForgeConfigSpec.DoubleValue verticalDrag;
		public final ForgeConfigSpec.DoubleValue maxHorizontalSpeed;
		public final ForgeConfigSpec.DoubleValue maxVerticalSpeed;
		public final ForgeConfigSpec.DoubleValue turnAcceleration;
		public final ForgeConfigSpec.DoubleValue turnBrake;
		public final ForgeConfigSpec.DoubleValue turnDirectionChangeBrake;
		public final ForgeConfigSpec.DoubleValue maxTurnSpeed;
		public final ForgeConfigSpec.IntValue inputTimeoutTicks;
		public final ForgeConfigSpec.IntValue magnetTimeoutTicks;
		public final ForgeConfigSpec.DoubleValue magnetBrakeDistance;
		public final ForgeConfigSpec.DoubleValue magnetMaxDistance;
		public final ForgeConfigSpec.DoubleValue assemblyStationSpeed;
		public final ForgeConfigSpec.IntValue attractPeriodTicks;
		public final ForgeConfigSpec.DoubleValue maxVelocityForAttractSqr;

		GhastHotAirBalloon(ForgeConfigSpec.Builder builder) {
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
			magnetTimeoutTicks = builder.defineInRange("magnetTimeoutTicks", 12, 1, Integer.MAX_VALUE);
			magnetBrakeDistance = builder.defineInRange("magnetBrakeDistance", 2.5d, 0.0001d, Double.MAX_VALUE);
			magnetMaxDistance = builder.defineInRange("magnetMaxDistance", 32.0d, 0.0d, Double.MAX_VALUE);
			assemblyStationSpeed = builder.defineInRange("assemblyStationSpeed", 0.5d, 0.0001d, Double.MAX_VALUE);
			attractPeriodTicks = builder.defineInRange("attractPeriodTicks", 20, 1, Integer.MAX_VALUE);
			maxVelocityForAttractSqr = builder.defineInRange("maxVelocityForAttractSqr", 1.0E-3d, 0.0d, Double.MAX_VALUE);
			builder.pop();
		}
	}

	public static class ButterCat {
		public final ForgeConfigSpec.IntValue maxButterCount;
		public final ForgeConfigSpec.IntValue butterDecayTicks;
		public final ForgeConfigSpec.IntValue butterForMaxRpm;
		public final ForgeConfigSpec.DoubleValue rpmPerButter;
		public final ForgeConfigSpec.DoubleValue maxGeneratedRpm;
		public final ForgeConfigSpec.DoubleValue stressCapacityPerRpm;
		public final ForgeConfigSpec.DoubleValue maxStressCapacity;
		public final ForgeConfigSpec.DoubleValue rotationAngularSpeed;
		public final ForgeConfigSpec.IntValue butterNutrition;
		public final ForgeConfigSpec.DoubleValue butterSaturation;
		public final ForgeConfigSpec.IntValue superButterNutrition;
		public final ForgeConfigSpec.DoubleValue superButterSaturation;
		public final ForgeConfigSpec.IntValue superButterRotationDuration;
		public final ForgeConfigSpec.IntValue superButterRotationAmplifier;
		public final ForgeConfigSpec.IntValue superButterLevitationDuration;
		public final ForgeConfigSpec.IntValue superButterLevitationAmplifier;
		public final ForgeConfigSpec.IntValue incompleteSuperButterNutrition;
		public final ForgeConfigSpec.DoubleValue incompleteSuperButterSaturation;
		public final ForgeConfigSpec.IntValue incompleteSuperButterRotationDuration;
		public final ForgeConfigSpec.IntValue incompleteSuperButterRotationAmplifier;

		ButterCat(ForgeConfigSpec.Builder builder) {
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

	public static class Automation {
		Automation(ForgeConfigSpec.Builder builder) {
			builder.push("automation");
			builder.pop();
		}
	}

	public static class Wireless {
		public final ForgeConfigSpec.IntValue terminalAccessRange;
		public final ForgeConfigSpec.BooleanValue allowCrossDimensionTerminal;

		Wireless(ForgeConfigSpec.Builder builder) {
			builder.push("wireless");
			terminalAccessRange = builder.defineInRange("terminalAccessRange", 32, 0, Integer.MAX_VALUE);
			allowCrossDimensionTerminal = builder.define("allowCrossDimensionTerminal", false);
			builder.pop();
		}
	}

	public static class UniversalJoint {
		public final ForgeConfigSpec.IntValue maxConnectionRange;
		public final ForgeConfigSpec.IntValue itemCooldownTicks;

		UniversalJoint(ForgeConfigSpec.Builder builder) {
			builder.push("universalJoint");
			maxConnectionRange = builder.defineInRange("maxConnectionRange", 2, 0, 64);
			itemCooldownTicks = builder.defineInRange("itemCooldownTicks", 5, 0, Integer.MAX_VALUE);
			builder.pop();
		}
	}

	public static class ClientUniversalJoint {
		public final ForgeConfigSpec.IntValue previewRange;

		ClientUniversalJoint(ForgeConfigSpec.Builder builder) {
			builder.push("universalJoint");
			previewRange = builder.defineInRange("previewRange", 16, 0, 256);
			builder.pop();
		}
	}

	public static class SquidPrinter {
		public final ForgeConfigSpec.IntValue cycleTicks;
		public final ForgeConfigSpec.IntValue cycleWaterCost;
		public final ForgeConfigSpec.IntValue tankCapacity;
		public final ForgeConfigSpec.IntValue finishingTicks;

		SquidPrinter(ForgeConfigSpec.Builder builder) {
			builder.push("squidPrinter");
			cycleTicks = builder.defineInRange("cycleTicks", 20, 1, Integer.MAX_VALUE);
			cycleWaterCost = builder.defineInRange("cycleWaterCost", 50, 0, Integer.MAX_VALUE);
			tankCapacity = builder.defineInRange("tankCapacity", 1000, 1, Integer.MAX_VALUE);
			finishingTicks = builder.defineInRange("finishingTicks", 5, 0, Integer.MAX_VALUE);
			builder.pop();
		}
	}

	public static class EvokerEnchantingChamber {
		public final ForgeConfigSpec.IntValue castDurationTicksPerLevel;
		public final ForgeConfigSpec.IntValue xpPerLevel;
		public final ForgeConfigSpec.IntValue cacheCapacity;

		EvokerEnchantingChamber(ForgeConfigSpec.Builder builder) {
			builder.push("evokerEnchantingChamber");
			castDurationTicksPerLevel = builder.defineInRange("castDurationTicksPerLevel", 40, 1, Integer.MAX_VALUE);
			xpPerLevel = builder.defineInRange("xpPerLevel", 1000, 1, Integer.MAX_VALUE);
			cacheCapacity = builder.defineInRange("cacheCapacity", 3000, 1, Integer.MAX_VALUE);
			builder.pop();
		}
	}

	public static class SchrodingersCat {
		public final ForgeConfigSpec.IntValue defaultInterval;
		public final ForgeConfigSpec.IntValue maxInterval;
		public final ForgeConfigSpec.DoubleValue highSignalChance;

		SchrodingersCat(ForgeConfigSpec.Builder builder) {
			builder.push("schrodingersCat");
			defaultInterval = builder.defineInRange("defaultInterval", 20, 1, Integer.MAX_VALUE);
			maxInterval = builder.defineInRange("maxInterval", 60 * 20 * 60, 1, Integer.MAX_VALUE);
			highSignalChance = builder.defineInRange("highSignalChance", 0.5d, 0.0d, 1.0d);
			builder.pop();
		}
	}

	public static class BoneRatchet {
		public final ForgeConfigSpec.DoubleValue fallbackJamStressImpact;
		public final ForgeConfigSpec.DoubleValue creativeMotorMargin;

		BoneRatchet(ForgeConfigSpec.Builder builder) {
			builder.push("boneRatchet");
			fallbackJamStressImpact = builder.defineInRange("fallbackJamStressImpact", 20000.0d, 0.0d, Double.MAX_VALUE);
			creativeMotorMargin = builder.defineInRange("creativeMotorMargin", 1024.0d, 0.0d, Double.MAX_VALUE);
			builder.pop();
		}
	}

	public static class BasinEntityProcessing {
		public final ForgeConfigSpec.DoubleValue entityScanHeight;

		BasinEntityProcessing(ForgeConfigSpec.Builder builder) {
			builder.push("basinEntityProcessing");
			entityScanHeight = builder.defineInRange("entityScanHeight", 1.25d, 0.0d, 16.0d);
			builder.pop();
		}
	}

	public static class SlimeClutch {
		public final ForgeConfigSpec.IntValue recheckPeriod;
		public final ForgeConfigSpec.IntValue maxWalk;
		public final ForgeConfigSpec.BooleanValue enableSoftOverloadCheck;

		SlimeClutch(ForgeConfigSpec.Builder builder) {
			builder.push("slimeClutch");
			recheckPeriod = builder.defineInRange("recheckPeriod", 20, 1, Integer.MAX_VALUE);
			maxWalk = builder.defineInRange("maxWalk", 1024, 1, Integer.MAX_VALUE);
			enableSoftOverloadCheck = builder.define("enableSoftOverloadCheck", true);
			builder.pop();
		}
	}

	public static class LiquidLivingSlime {
		public final ForgeConfigSpec.IntValue sourceHitsToBreak;
		public final ForgeConfigSpec.BooleanValue dropSlimeBallWhenSourceBreaks;

		LiquidLivingSlime(ForgeConfigSpec.Builder builder) {
			builder.push("liquidLivingSlime");
			sourceHitsToBreak = builder.defineInRange("sourceHitsToBreak", 4, 1, 64);
			dropSlimeBallWhenSourceBreaks = builder.define("dropSlimeBallWhenSourceBreaks", true);
			builder.pop();
		}
	}

	public static class FixedCarrotFishingRod {
		public final ForgeConfigSpec.DoubleValue searchRange;
		public final ForgeConfigSpec.DoubleValue speedModifier;
		public final ForgeConfigSpec.DoubleValue stopDistance;
		public final ForgeConfigSpec.IntValue searchCooldown;
		public final ForgeConfigSpec.IntValue stopCooldown;

		FixedCarrotFishingRod(ForgeConfigSpec.Builder builder) {
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
		public final ForgeConfigSpec.DoubleValue slimeBeltBaseChance;
		public final ForgeConfigSpec.DoubleValue slimeBeltLengthChance;
		public final ForgeConfigSpec.DoubleValue slimeBeltSpeedChance;
		public final ForgeConfigSpec.DoubleValue slimeBeltMaxChance;
		public final ForgeConfigSpec.DoubleValue magmaBeltBaseChance;
		public final ForgeConfigSpec.DoubleValue magmaBeltLengthChance;
		public final ForgeConfigSpec.DoubleValue magmaBeltMaxChance;

		BeltParticles(ForgeConfigSpec.Builder builder) {
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

	public static class BufferPad {
		public final ForgeConfigSpec.DoubleValue escapePushSpeed;
		public final ForgeConfigSpec.DoubleValue movementEpsilon;

		BufferPad(ForgeConfigSpec.Builder builder) {
			builder.push("bufferPad");
			escapePushSpeed = builder.defineInRange("escapePushSpeed", 0.05d, 0.0d, Double.MAX_VALUE);
			movementEpsilon = builder.defineInRange("movementEpsilon", 1.0E-4d, 0.0d, 1.0d);
			builder.pop();
		}
	}

	public static class ShulkerPackager {
		public final ForgeConfigSpec.IntValue transferDelay;
		public final ForgeConfigSpec.IntValue connectionRange;

		ShulkerPackager(ForgeConfigSpec.Builder builder) {
			builder.push("shulkerPackager");
			transferDelay = builder.defineInRange("transferDelay", 8, 1, Integer.MAX_VALUE);
			connectionRange = builder.defineInRange("connectionRange", 5, 0, 64);
			builder.pop();
		}
	}

	private static ForgeConfigSpec.ConfigValue<List<? extends String>> defineResourceLocationList(
		ForgeConfigSpec.Builder builder, String path, List<? extends String> defaults) {
		return builder.defineListAllowEmpty(path, defaults, value -> value instanceof String string
			&& ResourceLocation.tryParse(string) != null);
	}

	public static boolean isEntityTypeAllowed(EntityType<?> type, EntityListMode mode,
		List<? extends String> allowlist, List<? extends String> denylist) {
		ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
		if (id == null)
			return mode == EntityListMode.ALLOW_ALL;
		return switch (mode) {
		case ALLOW_ALL -> true;
		case ALLOWLIST -> containsResourceLocation(allowlist, id);
		case DENYLIST -> !containsResourceLocation(denylist, id);
		};
	}

	public static boolean containsResourceLocation(List<? extends String> values, ResourceLocation id) {
		for (String value : values) {
			ResourceLocation parsed = ResourceLocation.tryParse(value);
			if (id.equals(parsed))
				return true;
		}
		return false;
	}
}
