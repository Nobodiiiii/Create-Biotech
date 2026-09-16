package com.nobodiiiii.createbiotech.registry;

import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.EnumMap;
import java.util.stream.Stream;

import com.nobodiiiii.createbiotech.foundation.feature.CBFeature;
import com.nobodiiiii.createbiotech.CreateBiotech;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class CBConfigs {
	private static final int SHULKER_PACKAGER_OLD_DEFAULT_RANGE = 5;
	private static final int SHULKER_PACKAGER_DEFAULT_RANGE = 8;
	private static final int SHULKER_PACKAGER_CONFIG_VERSION = 1;
	private static final String DING_DONG_CHICKEN_ID = "create_biotech:ding_dong_chicken";
	private static final int CARDBOARD_BOX_CONFIG_VERSION = 1;
	private static final List<String> CARDBOARD_BOX_OLD_DEFAULT_SMALL_ENTITY_ALLOWLIST = List.of(
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
		"minecraft:magma_cube");
	private static final List<String> CARDBOARD_BOX_DEFAULT_SMALL_ENTITY_ALLOWLIST = Stream.concat(
		Stream.of(DING_DONG_CHICKEN_ID), CARDBOARD_BOX_OLD_DEFAULT_SMALL_ENTITY_ALLOWLIST.stream()).toList();

	public static final Client CLIENT;
	public static final ModConfigSpec CLIENT_SPEC;
	public static final Common COMMON;
	public static final ModConfigSpec COMMON_SPEC;
	public static final Server SERVER;
	public static final ModConfigSpec SERVER_SPEC;

	static {
		Pair<Client, ModConfigSpec> clientSpecPair =
			new ModConfigSpec.Builder().configure(Client::new);
		CLIENT = clientSpecPair.getLeft();
		CLIENT_SPEC = clientSpecPair.getRight();

		Pair<Common, ModConfigSpec> commonSpecPair =
			new ModConfigSpec.Builder().configure(Common::new);
		COMMON = commonSpecPair.getLeft();
		COMMON_SPEC = commonSpecPair.getRight();

		Pair<Server, ModConfigSpec> serverSpecPair =
			new ModConfigSpec.Builder().configure(Server::new);
		SERVER = serverSpecPair.getLeft();
		SERVER_SPEC = serverSpecPair.getRight();
	}

	private CBConfigs() {}

	public static void register(ModContainer modContainer) {
		modContainer.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
		modContainer.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
		modContainer.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
	}

	@SubscribeEvent
	public static void onConfigLoad(ModConfigEvent.Loading event) {
		migrateShulkerPackagerConfig(event.getConfig());
		migrateCardboardBoxConfig(event.getConfig());
	}

	@SubscribeEvent
	public static void onConfigReload(ModConfigEvent.Reloading event) {
		migrateShulkerPackagerConfig(event.getConfig());
		migrateCardboardBoxConfig(event.getConfig());
	}

	private static void migrateShulkerPackagerConfig(ModConfig config) {
		if (config.getSpec() != SERVER_SPEC)
			return;

		ShulkerPackager shulkerPackager = SERVER.shulkerPackager;
		if (shulkerPackager.configVersion.get() >= SHULKER_PACKAGER_CONFIG_VERSION)
			return;

		// Only migrate servers that still have the old default. Preserve explicit custom values.
		if (shulkerPackager.connectionRange.get() == SHULKER_PACKAGER_OLD_DEFAULT_RANGE)
			shulkerPackager.connectionRange.set(SHULKER_PACKAGER_DEFAULT_RANGE);

		shulkerPackager.configVersion.set(SHULKER_PACKAGER_CONFIG_VERSION);
		SERVER_SPEC.save();
	}

	private static void migrateCardboardBoxConfig(ModConfig config) {
		if (config.getSpec() != SERVER_SPEC)
			return;

		CardboardBox cardboardBox = SERVER.cardboardBox;
		if (cardboardBox.configVersion.get() >= CARDBOARD_BOX_CONFIG_VERSION)
			return;

		// Only migrate servers that still have the old default. Preserve explicit custom values.
		if (cardboardBox.smallBoxEntityAllowlist.get().equals(CARDBOARD_BOX_OLD_DEFAULT_SMALL_ENTITY_ALLOWLIST))
			cardboardBox.smallBoxEntityAllowlist.set(CARDBOARD_BOX_DEFAULT_SMALL_ENTITY_ALLOWLIST);

		cardboardBox.configVersion.set(CARDBOARD_BOX_CONFIG_VERSION);
		SERVER_SPEC.save();
	}

	public enum EntityListMode {
		ALLOW_ALL,
		ALLOWLIST,
		DENYLIST
	}

	public static class Common {
		Common(ModConfigSpec.Builder builder) {
		}
	}

	public static class Client {
		public final ModConfigSpec.BooleanValue enableShulkerTeleporterCameraOffset;
		public final ModConfigSpec.BooleanValue enableShulkerTeleporterPlayerClipping;
		public final ModConfigSpec.BooleanValue renderExperienceAsFluid;
		public final ModConfigSpec.BooleanValue renderCapturedEntitiesOnBoxes;
		public final ClientCreeperBlastChamber creeperBlastChamber;
		public final ClientUniversalJoint universalJoint;
		public final BeltParticles beltParticles;

		Client(ModConfigSpec.Builder builder) {
			enableShulkerTeleporterCameraOffset = builder.define("enableShulkerTeleporterCameraOffset", true);
			enableShulkerTeleporterPlayerClipping = builder.define("enableShulkerTeleporterPlayerClipping", true);
			renderExperienceAsFluid = builder
				.comment("Render experience in Create fluid tanks as a conventional fluid instead of experience orbs.")
				.define("renderExperienceAsFluid", false);
			renderCapturedEntitiesOnBoxes = builder
				.comment("Render captured creatures on cardboard box fronts. Disable to use the packager-style face and skip entity icon caches.")
				.define("renderCapturedEntitiesOnBoxes", true);
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
		public final SurgicalTable surgicalTable;
		public final CardboardBox cardboardBox;
		public final SlimeMimic slimeMimic;
		public final GhastHotAirBalloon ghastHotAirBalloon;
		public final ButterCat butterCat;
		public final Automation automation;
		public final UniversalJoint universalJoint;
		public final SquidPrinter squidPrinter;
		public final EvokerEnchantingChamber evokerEnchantingChamber;
		public final SchrodingersCat schrodingersCat;
		public final BoneRatchet boneRatchet;
		public final SlimeClutch slimeClutch;
		public final LiquidLivingSlime liquidLivingSlime;
		public final FixedCarrotFishingRod fixedCarrotFishingRod;
		public final BufferPad bufferPad;
		public final ShulkerPackager shulkerPackager;
		public final FrogStomach frogStomach;
		public final ExperiencePump experiencePump;
		public final BioPackager bioPackager;
		public final ShulkerTeleporter shulkerTeleporter;
		public final TeleportationFluid teleportationFluid;
		public final AllayCourier allayCourier;
		public final Features features;

		Server(ModConfigSpec.Builder builder) {
			experience = new Experience(builder);
			creeperBlastChamber = new CreeperBlastChamber(builder);
			powerBelt = new PowerBelt(builder);
			petriDish = new PetriDish(builder);
			spiderAssemblyTable = new SpiderAssemblyTable(builder);
			surgicalTable = new SurgicalTable(builder);
			cardboardBox = new CardboardBox(builder);
			slimeMimic = new SlimeMimic(builder);
			ghastHotAirBalloon = new GhastHotAirBalloon(builder);
			butterCat = new ButterCat(builder);
			automation = new Automation(builder);
			universalJoint = new UniversalJoint(builder);
			squidPrinter = new SquidPrinter(builder);
			evokerEnchantingChamber = new EvokerEnchantingChamber(builder);
			schrodingersCat = new SchrodingersCat(builder);
			boneRatchet = new BoneRatchet(builder);
			slimeClutch = new SlimeClutch(builder);
			liquidLivingSlime = new LiquidLivingSlime(builder);
			fixedCarrotFishingRod = new FixedCarrotFishingRod(builder);
			bufferPad = new BufferPad(builder);
			shulkerPackager = new ShulkerPackager(builder);
			frogStomach = new FrogStomach(builder);
			experiencePump = new ExperiencePump(builder);
			bioPackager = new BioPackager(builder);
			shulkerTeleporter = new ShulkerTeleporter(builder);
			teleportationFluid = new TeleportationFluid(builder);
			allayCourier = new AllayCourier(builder);
			features = new Features(builder);
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

	public static class ShulkerTeleporter {
		public final ModConfigSpec.BooleanValue allowCrossDimension;
		public final ModConfigSpec.BooleanValue allowDestinationChunkLoading;
		public final ModConfigSpec.DoubleValue maxSameSpaceDistance;
		public final ModConfigSpec.BooleanValue allowPlayers;
		public final ModConfigSpec.BooleanValue allowMobs;
		public final ModConfigSpec.BooleanValue allowItems;
		public final ModConfigSpec.IntValue maxEntitiesPerTeleport;
		public final ModConfigSpec.IntValue arrivalCooldownTicks;

		ShulkerTeleporter(ModConfigSpec.Builder builder) {
			builder.push("shulkerTeleporter");
			allowCrossDimension = builder.define("allowCrossDimension", true);
			allowDestinationChunkLoading = builder
				.comment("Allow static-world destinations to load their chunk while resolving a teleport.")
				.define("allowDestinationChunkLoading", true);
			maxSameSpaceDistance = builder
				.comment("Maximum destination distance within the same dimension and sublevel. 0 means unlimited.")
				.defineInRange("maxSameSpaceDistance", 0.0d, 0.0d, 30000000.0d);
			allowPlayers = builder.define("allowPlayers", true);
			allowMobs = builder.define("allowMobs", true);
			allowItems = builder.define("allowItems", true);
			maxEntitiesPerTeleport = builder
				.comment("Maximum entities moved in one activation. 0 means unlimited.")
				.defineInRange("maxEntitiesPerTeleport", 0, 0, 1024);
			arrivalCooldownTicks = builder.defineInRange("arrivalCooldownTicks", 80, 0, Integer.MAX_VALUE);
			builder.pop();
		}
	}

	public static class TeleportationFluid {
		public final ModConfigSpec.BooleanValue enablePortalExtraction;
		public final ModConfigSpec.IntValue fluidPerPortalBlock;
		public final ModConfigSpec.BooleanValue destroyPortalBlockWhenDrained;

		TeleportationFluid(ModConfigSpec.Builder builder) {
			builder.push("teleportationFluid");
			enablePortalExtraction = builder
				.comment("Expose nether portal blocks as sources of teleportation fluid.")
				.define("enablePortalExtraction", true);
			fluidPerPortalBlock = builder
				.comment("Teleportation fluid available from each nether portal block, in millibuckets.")
				.defineInRange("fluidPerPortalBlock", 250, 1, Integer.MAX_VALUE);
			destroyPortalBlockWhenDrained = builder
				.comment("Destroy a portal block after all of its configured teleportation fluid is extracted.")
				.define("destroyPortalBlockWhenDrained", true);
			builder.pop();
		}
	}

	public static class AllayCourier {
		public final ModConfigSpec.BooleanValue allowCrossDimensionDelivery;
		public final ModConfigSpec.DoubleValue maxDeliveryDistance;
		public final ModConfigSpec.IntValue maxActiveTasks;
		public final ModConfigSpec.IntValue maxActiveTasksPerPlayer;
		public final ModConfigSpec.IntValue teleportAfterTicks;
		public final ModConfigSpec.IntValue forceArrivalTicks;
		public final ModConfigSpec.IntValue returnRetryTicks;
		public final ModConfigSpec.IntValue returnLaunchDelayTicks;

		AllayCourier(ModConfigSpec.Builder builder) {
			builder.push("allayCourier");
			allowCrossDimensionDelivery = builder.define("allowCrossDimensionDelivery", true);
			maxDeliveryDistance = builder
				.comment("Maximum same-space delivery distance, in blocks. 0 means unlimited.")
				.defineInRange("maxDeliveryDistance", 0.0d, 0.0d, 30000000.0d);
			maxActiveTasks = builder
				.comment("Maximum active courier tasks on the server. 0 means unlimited.")
				.defineInRange("maxActiveTasks", 0, 0, 1000000);
			maxActiveTasksPerPlayer = builder
				.comment("Maximum active tasks launched by one player. 0 means unlimited; port-launched tasks are not counted here.")
				.defineInRange("maxActiveTasksPerPlayer", 0, 0, 1000000);
			teleportAfterTicks = builder
				.comment("Ticks before a courier may relocate near a distant or cross-dimensional target.")
				.defineInRange("teleportAfterTicks", 300, 0, Integer.MAX_VALUE);
			forceArrivalTicks = builder
				.comment("Ticks before a courier forces delivery recovery. Effectively never less than teleportAfterTicks.")
				.defineInRange("forceArrivalTicks", 600, 0, Integer.MAX_VALUE);
			returnRetryTicks = builder
				.comment("Ticks a port keeps retrying a queued carrier return before applying its existing fallback.")
				.defineInRange("returnRetryTicks", 100, 1, Integer.MAX_VALUE);
			returnLaunchDelayTicks = builder
				.comment("Delay before a received carrier launches its return trip.")
				.defineInRange("returnLaunchDelayTicks", 40, 0, Integer.MAX_VALUE);
			builder.pop();
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

	public static class CardboardBox {
		public final ModConfigSpec.IntValue configVersion;
		public final ModConfigSpec.ConfigValue<List<? extends String>> smallBoxEntityAllowlist;
		public final ModConfigSpec.BooleanValue largeBoxCreativeOnly;
		public final ModConfigSpec.BooleanValue lethalCaptureEnabled;
		public final ModConfigSpec.EnumValue<EntityListMode> largeBoxEntityListMode;
		public final ModConfigSpec.ConfigValue<List<? extends String>> largeBoxEntityAllowlist;
		public final ModConfigSpec.ConfigValue<List<? extends String>> largeBoxEntityDenylist;

		CardboardBox(ModConfigSpec.Builder builder) {
			builder.push("cardboardBox");
			configVersion = builder
				.comment("Internal migration marker. Do not edit.")
				.defineInRange("configVersion", 0, 0, CARDBOARD_BOX_CONFIG_VERSION);
			smallBoxEntityAllowlist = defineResourceLocationList(builder, "smallBoxEntityAllowlist",
				CARDBOARD_BOX_DEFAULT_SMALL_ENTITY_ALLOWLIST);
			largeBoxCreativeOnly = builder.define("largeBoxCreativeOnly", true);
			lethalCaptureEnabled = builder.define("lethalCaptureEnabled", true);
			largeBoxEntityListMode = builder.defineEnum("largeBoxEntityListMode", EntityListMode.ALLOW_ALL);
			largeBoxEntityAllowlist = defineResourceLocationList(builder, "largeBoxEntityAllowlist", List.of());
			largeBoxEntityDenylist = defineResourceLocationList(builder, "largeBoxEntityDenylist", List.of());
			builder.pop();
		}
	}

	public static class SlimeMimic {
		public final ModConfigSpec.IntValue hauntCycleTicks;
		public final ModConfigSpec.BooleanValue replaceDropsWithSlime;
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

	public static class SquidPrinter {
		public final ModConfigSpec.IntValue cycleTicks;
		public final ModConfigSpec.IntValue cycleWaterCost;
		public final ModConfigSpec.IntValue tankCapacity;
		public final ModConfigSpec.IntValue finishingTicks;

		SquidPrinter(ModConfigSpec.Builder builder) {
			builder.push("squidPrinter");
			cycleTicks = builder.defineInRange("cycleTicks", 20, 1, Integer.MAX_VALUE);
			cycleWaterCost = builder.defineInRange("cycleWaterCost", 50, 0, Integer.MAX_VALUE);
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

	public static class BufferPad {
		public final ModConfigSpec.DoubleValue escapePushSpeed;
		public final ModConfigSpec.DoubleValue movementEpsilon;

		BufferPad(ModConfigSpec.Builder builder) {
			builder.push("bufferPad");
			escapePushSpeed = builder.defineInRange("escapePushSpeed", 0.05d, 0.0d, Double.MAX_VALUE);
			movementEpsilon = builder.defineInRange("movementEpsilon", 1.0E-4d, 0.0d, 1.0d);
			builder.pop();
		}
	}

	public static class ShulkerPackager {
		public final ModConfigSpec.IntValue transferDelay;
		public final ModConfigSpec.IntValue configVersion;
		public final ModConfigSpec.IntValue connectionRange;

		ShulkerPackager(ModConfigSpec.Builder builder) {
			builder.push("shulkerPackager");
			transferDelay = builder.defineInRange("transferDelay", 8, 1, Integer.MAX_VALUE);
			configVersion = builder
				.comment("Internal migration marker. Do not edit.")
				.defineInRange("configVersion", 0, 0, SHULKER_PACKAGER_CONFIG_VERSION);
			connectionRange = builder
				.comment("Half-size of the axis-aligned output cube. The default 8 creates a 17x17x17 cube.")
				.defineInRange("connectionRange", SHULKER_PACKAGER_DEFAULT_RANGE, 0, 64);
			builder.pop();
		}
	}

	private static ModConfigSpec.ConfigValue<List<? extends String>> defineResourceLocationList(
		ModConfigSpec.Builder builder, String path, List<? extends String> defaults) {
		return builder.defineListAllowEmpty(path, defaults, null, value -> value instanceof String string
			&& ResourceLocation.tryParse(string) != null);
	}

	public static boolean isEntityTypeAllowed(EntityType<?> type, EntityListMode mode,
		List<? extends String> allowlist, List<? extends String> denylist) {
		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
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
