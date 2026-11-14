package com.nobodiiiii.createbiotech.registry;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import static com.nobodiiiii.createbiotech.registry.CBConfigs.*;

/** Feature configuration definitions; CBConfigs retains its public section types. */
public final class CBLogisticsConfigSections {
	private CBLogisticsConfigSections() {}


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
}
