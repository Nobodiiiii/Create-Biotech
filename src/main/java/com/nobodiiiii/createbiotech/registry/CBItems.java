package com.nobodiiiii.createbiotech.registry;

import net.minecraft.core.registries.Registries;

import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.buttercat.item.ButterCatBlockItem;
import com.nobodiiiii.createbiotech.content.buttercat.item.ButterFoodProperties;
import com.nobodiiiii.createbiotech.content.buttercat.item.ConfigurableButterFoodItem;
import com.nobodiiiii.createbiotech.content.buttercat.item.ConfigurableButterSequencedAssemblyItem;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxItem;
import com.nobodiiiii.createbiotech.content.dingdongchicken.DingDongChickenItem;
import com.nobodiiiii.createbiotech.content.experience.ExperienceClusterBlockItem;
import com.nobodiiiii.createbiotech.content.experience.ExperienceConstants;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberItem;
import com.nobodiiiii.createbiotech.content.experience.HiddenExperienceItem;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultItem;
import com.nobodiiiii.createbiotech.content.cardboardbox.LargeCardboardBoxItem;
import com.nobodiiiii.createbiotech.content.giantfrog.GiantFrogItem;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltConnectorItem;
import com.nobodiiiii.createbiotech.content.magmacubeburner.MagmaCubeBurnerItem;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltConnectorItem;
import com.nobodiiiii.createbiotech.content.processing.basin.CapturedSmallSlimeItem;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltConnectorItem;
import com.nobodiiiii.createbiotech.content.smartglue.SmartSuperGlueItem;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalJointItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.squidprinter.EnchantmentBookCopyItem;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerItem;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackageItem;
import com.nobodiiiii.createbiotech.content.slimearmor.SlimeArmorItem;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonItem;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterItem;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointItem;
import com.nobodiiiii.createbiotech.content.wirelessterminal.WirelessTerminalItem;
import com.nobodiiiii.createbiotech.content.automaticfishreleasemachine.AutomaticFishReleaseMachineItem;
import com.simibubi.create.content.logistics.tableCloth.TableClothBlockItem;
import com.nobodiiiii.createbiotech.foundation.item.BlockCenteredRenderedLivingEntityItem;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortItem;
import com.nobodiiiii.createbiotech.content.allay.item.allaycourier.AllayCourierItem;
import com.nobodiiiii.createbiotech.content.allay.item.allaycourier.IncompleteAllayCourierItem;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.neoforged.neoforge.registries.DeferredHolder;

public class CBItems {

	public static final DeferredRegister<Item> ITEMS =
		DeferredRegister.create(BuiltInRegistries.ITEM, CreateBiotech.MOD_ID);

	public static final DeferredHolder<Item, Item> AUTOMATIC_FISH_RELEASE_MACHINE =
		ITEMS.register("automatic_fish_release_machine",
			() -> new AutomaticFishReleaseMachineItem(CBBlocks.AUTOMATIC_FISH_RELEASE_MACHINE.get(),
				new Item.Properties()));

	public static final DeferredHolder<Item, Item> EVOKER_ENCHANTING_CHAMBER = ITEMS.register("evoker_enchanting_chamber",
		() -> new EvokerEnchantingChamberItem(CBBlocks.EVOKER_ENCHANTING_CHAMBER.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> EXPERIENCE_PUMP = ITEMS.register("experience_pump",
		() -> new BlockItem(CBBlocks.EXPERIENCE_PUMP.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> BUDDING_EXPERIENCE = ITEMS.register("budding_experience",
		() -> new BlockItem(CBBlocks.BUDDING_EXPERIENCE.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> SMALL_EXPERIENCE_BUD = ITEMS.register("small_experience_bud",
		() -> new ExperienceClusterBlockItem(CBBlocks.SMALL_EXPERIENCE_BUD.get(),
			ExperienceConstants::smallBudXpValue, new Item.Properties()));

	public static final DeferredHolder<Item, Item> MEDIUM_EXPERIENCE_BUD = ITEMS.register("medium_experience_bud",
		() -> new ExperienceClusterBlockItem(CBBlocks.MEDIUM_EXPERIENCE_BUD.get(),
			ExperienceConstants::mediumBudXpValue, new Item.Properties()));

	public static final DeferredHolder<Item, Item> LARGE_EXPERIENCE_BUD = ITEMS.register("large_experience_bud",
		() -> new ExperienceClusterBlockItem(CBBlocks.LARGE_EXPERIENCE_BUD.get(),
			ExperienceConstants::largeBudXpValue, new Item.Properties()));

	public static final DeferredHolder<Item, Item> EXPERIENCE_CLUSTER = ITEMS.register("experience_cluster",
		() -> new ExperienceClusterBlockItem(CBBlocks.EXPERIENCE_CLUSTER.get(),
			ExperienceConstants::clusterXpValue, new Item.Properties()));

	public static final DeferredHolder<Item, Item> EXPERIENCE = ITEMS.register("experience",
		() -> new HiddenExperienceItem(new Item.Properties()));

	public static final DeferredHolder<Item, Item> SQUID_PRINTER = ITEMS.register("squid_printer",
		() -> new SquidPrinterItem(CBBlocks.SQUID_PRINTER.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> PETRI_DISH = ITEMS.register("petri_dish",
		() -> new BlockItem(CBBlocks.PETRI_DISH.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> SURGICAL_TABLE = ITEMS.register("surgical_table",
		() -> new BlockItem(CBBlocks.SURGICAL_TABLE.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> PROJECTION_SURGICAL_TABLE =
		ITEMS.register("projection_surgical_table",
			() -> new BlockItem(CBBlocks.PROJECTION_SURGICAL_TABLE.get(), new Item.Properties()));

	public static final DeferredHolder<Item, SurgicalKitItem> SURGICAL_KIT = ITEMS.register("surgical_kit",
		() -> new SurgicalKitItem(new Item.Properties().durability(SurgicalKitItem.MAX_DURABILITY)));

	public static final DeferredHolder<Item, SurgicalJointItem> NECK_JOINT = ITEMS.register("neck_joint",
		() -> new SurgicalJointItem(SurgicalLimbType.NECK, new Item.Properties()));

	public static final DeferredHolder<Item, SurgicalJointItem> SHOULDER_JOINT = ITEMS.register("shoulder_joint",
		() -> new SurgicalJointItem(SurgicalLimbType.SHOULDER, new Item.Properties()));

	public static final DeferredHolder<Item, SurgicalJointItem> ELBOW_JOINT = ITEMS.register("elbow_joint",
		() -> new SurgicalJointItem(SurgicalLimbType.ELBOW, new Item.Properties()));

	public static final DeferredHolder<Item, SurgicalJointItem> HIP_JOINT = ITEMS.register("hip_joint",
		() -> new SurgicalJointItem(SurgicalLimbType.HIP, new Item.Properties()));

	public static final DeferredHolder<Item, SurgicalJointItem> KNEE_JOINT = ITEMS.register("knee_joint",
		() -> new SurgicalJointItem(SurgicalLimbType.KNEE, new Item.Properties()));

	public static final DeferredHolder<Item, EnchantmentBookCopyItem> ENCHANTMENT_BOOK_COPY =
		ITEMS.register("enchantment_book_copy", () -> new EnchantmentBookCopyItem(new Item.Properties()));

	public static final DeferredHolder<Item, Item> SLIME_BELT_CONNECTOR = ITEMS.register("slime_belt_connector",
		() -> new SlimeBeltConnectorItem(new Item.Properties()));

	public static final DeferredHolder<Item, Item> MAGMA_BELT_CONNECTOR = ITEMS.register("magma_belt_connector",
		() -> new MagmaBeltConnectorItem(new Item.Properties()));

	public static final DeferredHolder<Item, Item> EMPTY_MAGMA_CUBE_BURNER =
		ITEMS.register("empty_magma_cube_burner",
			() -> MagmaCubeBurnerItem.empty(CBBlocks.MAGMA_CUBE_BURNER.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> MAGMA_CUBE_BURNER = ITEMS.register("magma_cube_burner",
		() -> MagmaCubeBurnerItem.withMagmaCube(CBBlocks.MAGMA_CUBE_BURNER.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> POWER_BELT_CONNECTOR = ITEMS.register("power_belt_connector",
		() -> new PowerBeltConnectorItem(new Item.Properties()));

	public static final DeferredHolder<Item, Item> DING_DONG_CHICKEN_SPAWN_EGG =
		ITEMS.register("ding_dong_chicken_spawn_egg",
			() -> new DeferredSpawnEggItem(CBEntityTypes.DING_DONG_CHICKEN, 0xA1A1A1, 0xD8A52A,
				new Item.Properties()));

	public static final DeferredHolder<Item, Item> DING_DONG_CHICKEN = ITEMS.register("ding_dong_chicken",
		() -> new DingDongChickenItem(new Item.Properties().stacksTo(4)));

	/** Internal JEI representation of a vanilla chicken; intentionally absent from creative tabs. */
	public static final DeferredHolder<Item, Item> CHICKEN = ITEMS.register("chicken",
		() -> new BlockCenteredRenderedLivingEntityItem<>(new Item.Properties(), EntityType.CHICKEN, 1.5f));

	public static final DeferredHolder<Item, Item> SMART_SUPER_GLUE = ITEMS.register("smart_super_glue",
		() -> new SmartSuperGlueItem(new Item.Properties().stacksTo(1).durability(99)));

	public static final DeferredHolder<Item, Item> WIRELESS_TERMINAL = ITEMS.register("wireless_terminal",
		() -> new WirelessTerminalItem(new Item.Properties().stacksTo(1)));

	public static final DeferredHolder<Item, SonicDogCannonItem> SONIC_DOG_CANNON = ITEMS.register("sonic_dog_cannon",
		() -> new SonicDogCannonItem(new Item.Properties().durability(SonicDogCannonItem.MAX_DURABILITY)));

	public static final DeferredHolder<Item, Item> HALF_SHAFT = ITEMS.register("half_shaft",
		() -> new BlockItem(CBBlocks.HALF_SHAFT.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> CAPTURED_SMALL_SLIME = ITEMS.register("captured_small_slime",
		() -> new CapturedSmallSlimeItem(new Item.Properties().stacksTo(4)));

	// Slime armour: a slime counterpart to Create's cardboard armour, obtained by filling each
	// cardboard piece with 250mb of liquid living slime. Renders as a small slime when crouching.
	public static final DeferredHolder<Item, Item> SLIME_HELMET = ITEMS.register("slime_helmet",
		() -> new SlimeArmorItem(ArmorItem.Type.HELMET, new Item.Properties()));

	public static final DeferredHolder<Item, Item> SLIME_CHESTPLATE = ITEMS.register("slime_chestplate",
		() -> new SlimeArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Properties()));

	public static final DeferredHolder<Item, Item> SLIME_LEGGINGS = ITEMS.register("slime_leggings",
		() -> new SlimeArmorItem(ArmorItem.Type.LEGGINGS, new Item.Properties()));

	public static final DeferredHolder<Item, Item> SLIME_BOOTS = ITEMS.register("slime_boots",
		() -> new SlimeArmorItem(ArmorItem.Type.BOOTS, new Item.Properties()));

	public static final DeferredHolder<Item, Item> UNIVERSAL_JOINT = ITEMS.register("universal_joint",
		() -> new UniversalJointItem(new Item.Properties()));

	public static final DeferredHolder<Item, Item> SLIME_CLUTCH = ITEMS.register("slime_clutch",
		() -> new BlockItem(CBBlocks.SLIME_CLUTCH.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> BONE_RATCHET = ITEMS.register("bone_ratchet",
		() -> new BlockItem(CBBlocks.BONE_RATCHET.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> CARDBOARD_BOX = ITEMS.register("cardboard_box",
		() -> new CardboardBoxItem(new Item.Properties().stacksTo(16)));

	public static final DeferredHolder<Item, Item> LARGE_CARDBOARD_BOX = ITEMS.register("large_cardboard_box",
		() -> new LargeCardboardBoxItem(new Item.Properties().stacksTo(16)));

	public static final DeferredHolder<Item, Item> SCHRODINGERS_CAT = ITEMS.register("schrodingers_cat",
		() -> new BlockItem(CBBlocks.SCHRODINGERS_CAT.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> SPIDER_ASSEMBLY_TABLE = ITEMS.register("spider_assembly_table",
		() -> new SpiderAssemblyTableItem(CBBlocks.SPIDER_ASSEMBLY_TABLE.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> FIXED_CARROT_FISHING_ROD = ITEMS.register("fixed_carrot_fishing_rod",
		() -> new BlockItem(CBBlocks.FIXED_CARROT_FISHING_ROD.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION =
		ITEMS.register("ghast_hot_air_balloon_assembly_station",
			() -> new BlockItem(CBBlocks.GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> GHAST_HELM = ITEMS.register("ghast_helm",
		() -> new BlockItem(CBBlocks.GHAST_HELM.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> CREEPER_BLAST_CHAMBER = ITEMS.register("creeper_blast_chamber",
		() -> new BlockItem(CBBlocks.CREEPER_BLAST_CHAMBER.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> BIO_PACKAGER = ITEMS.register("bio_packager",
		() -> new BlockItem(CBBlocks.BIO_PACKAGER.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> SHULKER_PACKAGER = ITEMS.register("shulker_packager",
		() -> new ShulkerPackagerItem(CBBlocks.SHULKER_PACKAGER.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> SHULKER_PACKAGE = ITEMS.register("shulker_package",
		() -> new ShulkerPackageItem(new Item.Properties().stacksTo(1)));

	public static final DeferredHolder<Item, Item> SHULKER_TELEPORTER = ITEMS.register("shulker_teleporter",
		() -> new BlockItem(CBBlocks.SHULKER_TELEPORTER.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> GIANT_FROG = ITEMS.register("giant_frog",
		() -> new GiantFrogItem(CBBlocks.GIANT_FROG.get(), new Item.Properties().stacksTo(1)));

	public static final DeferredHolder<Item, Item> FROG_STOMACH_WALL = ITEMS.register("frog_stomach_wall",
		() -> new BlockItem(CBBlocks.FROG_STOMACH_WALL.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> FROG_STOMACH_MUCOSA = ITEMS.register("frog_stomach_mucosa",
		() -> new BlockItem(CBBlocks.FROG_STOMACH_MUCOSA.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> FROG_STOMACH_FUNGUS = ITEMS.register("frog_stomach_fungus",
		() -> new BlockItem(CBBlocks.FROG_STOMACH_FUNGUS.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> FROG_STOMACH_SECRETION =
		ITEMS.register("frog_stomach_secretion",
			() -> new BlockItem(CBBlocks.FROG_STOMACH_SECRETION.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> FROG_DIGESTIVE_TRACT = ITEMS.register("frog_digestive_tract",
		() -> new BlockItem(CBBlocks.FROG_DIGESTIVE_TRACT.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> FROG_DIGESTIVE_TRACT_WALL =
		ITEMS.register("frog_digestive_tract_wall",
			() -> new BlockItem(CBBlocks.FROG_DIGESTIVE_TRACT_WALL.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> ALLAY_PORT = ITEMS.register("allay_port",
		() -> new AllayPortItem(CBBlocks.ALLAY_PORT.get(), new Item.Properties()));

	public static final DeferredHolder<Item, AllayCourierItem> ALLAY_COURIER = ITEMS.register("allay_courier",
		() -> new AllayCourierItem(new Item.Properties()));
	public static final DeferredHolder<Item, IncompleteAllayCourierItem> INCOMPLETE_ALLAY_COURIER =
		ITEMS.register("incomplete_allay_courier", () -> new IncompleteAllayCourierItem(new Item.Properties()));

	public static final DeferredHolder<Item, Item> INCOMPLETE_CREEPER_BLAST_CHAMBER =
		ITEMS.register("incomplete_creeper_blast_chamber", () -> new Item(new Item.Properties()));

	public static final DeferredHolder<Item, Item> BIONIC_MECHANISM = ITEMS.register("bionic_mechanism",
		() -> new Item(new Item.Properties()));

	public static final DeferredHolder<Item, Item> ASURINE_ALLOY = ITEMS.register("asurine_alloy",
		() -> new Item(new Item.Properties()));

	public static final DeferredHolder<Item, Item> CARBON_POWDER = ITEMS.register("carbon_powder",
		() -> new Item(new Item.Properties()));

	public static final DeferredHolder<Item, Item> GRAPHITE = ITEMS.register("graphite",
		() -> new Item(new Item.Properties()));

	public static final DeferredHolder<Item, Item> ZINC_SHEET = ITEMS.register("zinc_sheet",
		() -> new Item(new Item.Properties()));

	public static final DeferredHolder<Item, Item> INCOMPLETE_BIONIC_MECHANISM =
		ITEMS.register("incomplete_bionic_mechanism", () -> new Item(new Item.Properties()));

	public static final DeferredHolder<Item, Item> ASURINE_CASING = ITEMS.register("asurine_casing",
		() -> new BlockItem(CBBlocks.ASURINE_CASING.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> BIOTECH_CASING = ITEMS.register("biotech_casing",
		() -> new BlockItem(CBBlocks.BIOTECH_CASING.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> ASURINE_TABLE_CLOTH = ITEMS.register("asurine_table_cloth",
		() -> new TableClothBlockItem(CBBlocks.ASURINE_TABLE_CLOTH.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> EXPLOSION_PROOF_CASING = ITEMS.register("explosion_proof_casing",
		() -> new BlockItem(CBBlocks.EXPLOSION_PROOF_CASING.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> EXPLOSION_PROOF_ITEM_VAULT = ITEMS.register("explosion_proof_item_vault",
		() -> new ExplosionProofItemVaultItem(CBBlocks.EXPLOSION_PROOF_ITEM_VAULT.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> BLAST_PROOF_GLASS = ITEMS.register("blast_proof_glass",
		() -> new BlockItem(CBBlocks.BLAST_PROOF_GLASS.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> BLAST_PROOF_FRAMED_GLASS = ITEMS.register("blast_proof_framed_glass",
		() -> new BlockItem(CBBlocks.BLAST_PROOF_FRAMED_GLASS.get(), new Item.Properties()));

	public static final Map<DyeColor, DeferredHolder<Item, Item>> BUFFER_PADS = registerBufferPads();
	public static final DeferredHolder<Item, Item> BUFFER_PAD = BUFFER_PADS.get(DyeColor.RED);

	public static final DeferredHolder<Item, Item> CUTE_CAT_ON_SHAFT = ITEMS.register("cute_cat_on_shaft",
		() -> new ButterCatBlockItem(CBBlocks.CUTE_CAT_ON_SHAFT.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> BUTTER_CAT_ENGINE = ITEMS.register("butter_cat_engine",
		() -> new ButterCatBlockItem(CBBlocks.BUTTER_CAT_ENGINE.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> BUTTER_BLOCK = ITEMS.register("butter_block",
		() -> new BlockItem(CBBlocks.BUTTER_BLOCK.get(), new Item.Properties()));

	public static final DeferredHolder<Item, Item> SUPER_BUTTER_BLOCK = ITEMS.register("super_butter_block",
		() -> new BlockItem(CBBlocks.SUPER_BUTTER_BLOCK.get(), new Item.Properties().rarity(Rarity.EPIC)));

	public static final DeferredHolder<Item, ConfigurableButterFoodItem> BUTTER = ITEMS.register("butter",
		() -> new ConfigurableButterFoodItem(
			new Item.Properties().food(ButterFoodProperties.createDefault(ButterFoodProperties.Variant.BUTTER)),
			ButterFoodProperties.Variant.BUTTER));

	public static final DeferredHolder<Item, ConfigurableButterSequencedAssemblyItem> INCOMPLETE_SUPER_BUTTER =
		ITEMS.register("incomplete_super_butter",
			() -> new ConfigurableButterSequencedAssemblyItem(
				new Item.Properties().food(
					ButterFoodProperties.createDefault(ButterFoodProperties.Variant.INCOMPLETE_SUPER_BUTTER)),
				ButterFoodProperties.Variant.INCOMPLETE_SUPER_BUTTER));

	public static final DeferredHolder<Item, ConfigurableButterFoodItem> SUPER_BUTTER = ITEMS.register("super_butter",
		() -> new ConfigurableButterFoodItem(
			new Item.Properties()
				.food(ButterFoodProperties.createDefault(ButterFoodProperties.Variant.SUPER_BUTTER))
				.rarity(Rarity.EPIC),
			ButterFoodProperties.Variant.SUPER_BUTTER));

	private CBItems() {}

	public static void register(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
	}

	public static boolean isSlimeBeltConnector(ItemStack stack) {
		return stack.is(SLIME_BELT_CONNECTOR.get());
	}

	public static boolean isMagmaBeltConnector(ItemStack stack) {
		return stack.is(MAGMA_BELT_CONNECTOR.get());
	}

	public static boolean isPowerBeltConnector(ItemStack stack) {
		return stack.is(POWER_BELT_CONNECTOR.get());
	}

	public static boolean isCustomBeltConnector(ItemStack stack) {
		return isSlimeBeltConnector(stack) || isMagmaBeltConnector(stack) || isPowerBeltConnector(stack);
	}

	public static int getButterLevel(Item item) {
		if (item == INCOMPLETE_SUPER_BUTTER.get())
			return 3;
		if (item == SUPER_BUTTER.get())
			return 5;
		return 1;
	}

	private static Map<DyeColor, DeferredHolder<Item, Item>> registerBufferPads() {
		EnumMap<DyeColor, DeferredHolder<Item, Item>> bufferPads = new EnumMap<>(DyeColor.class);
		for (DyeColor color : DyeColor.values()) {
			String id = CBBlocks.bufferPadId(color);
			bufferPads.put(color, ITEMS.register(id,
				() -> new BlockItem(CBBlocks.BUFFER_PADS.get(color).get(), new Item.Properties())));
		}
		return Collections.unmodifiableMap(bufferPads);
	}
}
