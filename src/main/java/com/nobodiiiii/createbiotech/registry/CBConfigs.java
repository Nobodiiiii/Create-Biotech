package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStats;
import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStatsPacket;
import com.nobodiiiii.createbiotech.content.capturenet.CaptureNetConfig;

import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.stream.Stream;

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
	static final int SHULKER_PACKAGER_DEFAULT_RANGE = 8;
	static final int SHULKER_PACKAGER_CONFIG_VERSION = 1;
	private static final String DING_DONG_CHICKEN_ID = "create_biotech:ding_dong_chicken";
	static final int CARDBOARD_BOX_CONFIG_VERSION = 1;
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
	static final List<String> CARDBOARD_BOX_DEFAULT_SMALL_ENTITY_ALLOWLIST = Stream.concat(
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
		configureBionicStats(event.getConfig());
		migrateShulkerPackagerConfig(event.getConfig());
		migrateCardboardBoxConfig(event.getConfig());
		migrateExperienceConfig(event.getConfig());
	}

	@SubscribeEvent
	public static void onConfigReload(ModConfigEvent.Reloading event) {
		configureBionicStats(event.getConfig());
		migrateShulkerPackagerConfig(event.getConfig());
		migrateCardboardBoxConfig(event.getConfig());
		migrateExperienceConfig(event.getConfig());
	}

	private static void configureBionicStats(ModConfig config) {
		if (config.getSpec() != SERVER_SPEC) return;
		var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
		java.util.Map<String, Double> values = new java.util.HashMap<>();
		SERVER.bionicStats.values.forEach((key, value) -> values.put(key, value.get()));
		Runnable apply = () -> {
			BionicStats.configure(values);
			if (server != null) {
				var packet = new BionicStatsPacket(
					BionicStats.exportServer().toString());
				server.getPlayerList().getPlayers().forEach(player -> com.nobodiiiii.createbiotech.network.CBPackets.sendToPlayer(packet, player));
			}
		};
		if (server != null && !server.isSameThread()) server.execute(apply); else apply.run();
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

	private static void migrateExperienceConfig(ModConfig config) {
		if (config.getSpec() != SERVER_SPEC || SERVER.experience.configVersion.get() >= 1)
			return;
		Experience experience = SERVER.experience;
		// Upgrade old defaults once, retaining deliberate server overrides.
		if (experience.smallBudXpValue.get() == 16)
			experience.smallBudXpValue.set(24);
		if (experience.mediumBudXpValue.get() == 32)
			experience.mediumBudXpValue.set(48);
		if (experience.largeBudXpValue.get() == 64)
			experience.largeBudXpValue.set(96);
		if (experience.clusterXpValue.get() == 128)
			experience.clusterXpValue.set(192);
		experience.configVersion.set(1);
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
		public final ModConfigSpec.BooleanValue showBionicTraitDonorsInJei;
		public final ModConfigSpec.BooleanValue showBionicAttackRange;
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
			showBionicTraitDonorsInJei = builder
				.comment("Show donor creatures on bionic trait JEI pages and enable species trait lookups from previews, spawn eggs and filled boxes. Synchronizes donor data while JEI is available; disable to stop requesting it from the server.")
				.define("showBionicTraitDonorsInJei", true);
			showBionicAttackRange = builder
				.comment("Show the melee attack range preview while bionic creatures swing their arms.")
				.translation("create_biotech.configuration.showBionicAttackRange")
				.define("showBionicAttackRange", false);
			creeperBlastChamber = new ClientCreeperBlastChamber(builder);
			universalJoint = new ClientUniversalJoint(builder);
			beltParticles = new BeltParticles(builder);
		}

		public boolean shouldRenderCapturedEntitiesOnBoxes() {
			// Render-frame events can arrive during startup before client configs load.
			return CLIENT_SPEC.isLoaded() && renderCapturedEntitiesOnBoxes.get();
		}

		public boolean shouldShowBionicAttackRange() {
			return CLIENT_SPEC.isLoaded() && showBionicAttackRange.get();
		}
	}

	public static class Server {
		public final CaptureNetConfig captureNet;
		public final PoweredCaptureNet poweredCaptureNet;
		public final Experience experience;
		public final CreeperBlastChamber creeperBlastChamber;
		public final PowerBelt powerBelt;
		public final PetriDish petriDish;
		public final SpiderAssemblyTable spiderAssemblyTable;
		public final SurgicalTable surgicalTable;
		public final CardboardBox cardboardBox;
		public final SlimeMimic slimeMimic;
		public final Devourer devourer;
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
		public final BionicStatsConfig bionicStats;

		Server(ModConfigSpec.Builder builder) {
			captureNet = new CaptureNetConfig(builder);
			poweredCaptureNet = new PoweredCaptureNet(builder);
			experience = new Experience(builder);
			creeperBlastChamber = new CreeperBlastChamber(builder);
			powerBelt = new PowerBelt(builder);
			petriDish = new PetriDish(builder);
			spiderAssemblyTable = new SpiderAssemblyTable(builder);
			surgicalTable = new SurgicalTable(builder);
			cardboardBox = new CardboardBox(builder);
			slimeMimic = new SlimeMimic(builder);
			devourer = new Devourer(builder);
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
			bionicStats = new BionicStatsConfig(builder);
		}
	}

	public static class PoweredCaptureNet {
		public final ModConfigSpec.BooleanValue consumeDurability;
		public final ModConfigSpec.IntValue captureRange;
		public final ModConfigSpec.DoubleValue captureMargin;

		PoweredCaptureNet(ModConfigSpec.Builder builder) {
			builder.push("poweredCaptureNet");
			consumeDurability = builder.comment("Spend one point of net durability after a successful automatic capture.")
				.translation("create_biotech.configuration.poweredCaptureNet.consumeDurability")
				.define("consumeDurability", false);
			captureRange = builder.comment("Maximum distance from the block center to the creature's hitbox center, in blocks.",
					"Tracking starts one block beyond this range. Set to zero to use Create's Mechanical Arm range.")
				.translation("create_biotech.configuration.poweredCaptureNet.captureRange")
				.defineInRange("captureRange", 0, 0, 32);
			captureMargin = builder.comment("Extra space along each axis of a creature's hitbox when testing net contact.")
				.translation("create_biotech.configuration.poweredCaptureNet.captureMargin")
				.defineInRange("captureMargin", 0.5, 0.0, 2.0);
			builder.pop();
		}
	}

	public static class BionicStatsConfig extends CBCreatureConfigSections.BionicStatsConfig {
		BionicStatsConfig(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class Features extends CBCreatureConfigSections.Features {
		Features(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class ExperiencePump extends CBMachineConfigSections.ExperiencePump {
		ExperiencePump(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class BioPackager extends CBMachineConfigSections.BioPackager {
		BioPackager(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class ShulkerTeleporter extends CBLogisticsConfigSections.ShulkerTeleporter {
		ShulkerTeleporter(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class TeleportationFluid extends CBLogisticsConfigSections.TeleportationFluid {
		TeleportationFluid(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class AllayCourier extends CBLogisticsConfigSections.AllayCourier {
		AllayCourier(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class FrogStomach extends CBCreatureConfigSections.FrogStomach {
		FrogStomach(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class Experience extends CBCreatureConfigSections.Experience {
		Experience(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class CreeperBlastChamber extends CBCreatureConfigSections.CreeperBlastChamber {
		CreeperBlastChamber(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class ClientCreeperBlastChamber extends CBCreatureConfigSections.ClientCreeperBlastChamber {
		ClientCreeperBlastChamber(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class PowerBelt extends CBKineticConfigSections.PowerBelt {
		PowerBelt(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class PetriDish extends CBMachineConfigSections.PetriDish {
		PetriDish(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class SpiderAssemblyTable extends CBMachineConfigSections.SpiderAssemblyTable {
		SpiderAssemblyTable(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class SurgicalTable extends CBMachineConfigSections.SurgicalTable {
		SurgicalTable(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class CardboardBox extends CBLogisticsConfigSections.CardboardBox {
		CardboardBox(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class SlimeMimic extends CBCreatureConfigSections.SlimeMimic {
		SlimeMimic(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class Devourer extends CBCreatureConfigSections.Devourer {
		Devourer(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class GhastHotAirBalloon extends CBCreatureConfigSections.GhastHotAirBalloon {
		GhastHotAirBalloon(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class ButterCat extends CBCreatureConfigSections.ButterCat {
		ButterCat(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class Automation extends CBKineticConfigSections.Automation {
		Automation(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class UniversalJoint extends CBKineticConfigSections.UniversalJoint {
		UniversalJoint(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class ClientUniversalJoint extends CBKineticConfigSections.ClientUniversalJoint {
		ClientUniversalJoint(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class SquidPrinter extends CBMachineConfigSections.SquidPrinter {
		SquidPrinter(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class EvokerEnchantingChamber extends CBMachineConfigSections.EvokerEnchantingChamber {
		EvokerEnchantingChamber(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class SchrodingersCat extends CBCreatureConfigSections.SchrodingersCat {
		SchrodingersCat(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class BoneRatchet extends CBKineticConfigSections.BoneRatchet {
		BoneRatchet(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class SlimeClutch extends CBKineticConfigSections.SlimeClutch {
		SlimeClutch(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class LiquidLivingSlime extends CBKineticConfigSections.LiquidLivingSlime {
		LiquidLivingSlime(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class FixedCarrotFishingRod extends CBKineticConfigSections.FixedCarrotFishingRod {
		FixedCarrotFishingRod(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class BeltParticles extends CBKineticConfigSections.BeltParticles {
		BeltParticles(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class BufferPad extends CBLogisticsConfigSections.BufferPad {
		BufferPad(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	public static class ShulkerPackager extends CBLogisticsConfigSections.ShulkerPackager {
		ShulkerPackager(ModConfigSpec.Builder builder) {
			super(builder);
		}
	}

	static ModConfigSpec.ConfigValue<List<? extends String>> defineResourceLocationList(
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
