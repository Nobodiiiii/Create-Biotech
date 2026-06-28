package com.nobodiiiii.createbiotech.registry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModItems;
import com.nobodiiiii.createbiotech.content.buttercat.item.ConfigurableButterFoodItem;
import com.nobodiiiii.createbiotech.content.buttercat.item.ConfigurableButterSequencedAssemblyItem;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxItem;
import com.nobodiiiii.createbiotech.content.experience.ExperienceClusterBlockItem;
import com.nobodiiiii.createbiotech.content.experience.ExperienceConstants;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberItem;
import com.nobodiiiii.createbiotech.content.experience.HiddenExperienceItem;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultItem;
import com.nobodiiiii.createbiotech.content.cardboardbox.LargeCardboardBoxItem;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltConnectorItem;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltConnectorItem;
import com.nobodiiiii.createbiotech.content.processing.basin.CapturedSmallSlimeItem;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltConnectorItem;
import com.nobodiiiii.createbiotech.content.smartglue.SmartSuperGlueItem;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableItem;
import com.nobodiiiii.createbiotech.content.squidprinter.EnchantmentBookCopyItem;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerItem;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackageItem;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterItem;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointItem;
import com.nobodiiiii.createbiotech.content.wirelessterminal.WirelessTerminalItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.tterrag.registrate.util.entry.ItemEntry;

public class CBItems {

	public static final DeferredRegister<Item> ITEMS =
		DeferredRegister.create(ForgeRegistries.ITEMS, CreateBiotech.MOD_ID);

	public static final RegistryObject<Item> EVOKER_ENCHANTING_CHAMBER = ITEMS.register("evoker_enchanting_chamber",
		() -> new EvokerEnchantingChamberItem(CBBlocks.EVOKER_ENCHANTING_CHAMBER.get(), new Item.Properties()));

	public static final RegistryObject<Item> EXPERIENCE_PUMP = ITEMS.register("experience_pump",
		() -> new BlockItem(CBBlocks.EXPERIENCE_PUMP.get(), new Item.Properties()));

	public static final RegistryObject<Item> BUDDING_EXPERIENCE = ITEMS.register("budding_experience",
		() -> new BlockItem(CBBlocks.BUDDING_EXPERIENCE.get(), new Item.Properties()));

	public static final RegistryObject<Item> SMALL_EXPERIENCE_BUD = ITEMS.register("small_experience_bud",
		() -> new ExperienceClusterBlockItem(CBBlocks.SMALL_EXPERIENCE_BUD.get(),
			ExperienceConstants::smallBudXpValue, new Item.Properties()));

	public static final RegistryObject<Item> MEDIUM_EXPERIENCE_BUD = ITEMS.register("medium_experience_bud",
		() -> new ExperienceClusterBlockItem(CBBlocks.MEDIUM_EXPERIENCE_BUD.get(),
			ExperienceConstants::mediumBudXpValue, new Item.Properties()));

	public static final RegistryObject<Item> LARGE_EXPERIENCE_BUD = ITEMS.register("large_experience_bud",
		() -> new ExperienceClusterBlockItem(CBBlocks.LARGE_EXPERIENCE_BUD.get(),
			ExperienceConstants::largeBudXpValue, new Item.Properties()));

	public static final RegistryObject<Item> EXPERIENCE_CLUSTER = ITEMS.register("experience_cluster",
		() -> new ExperienceClusterBlockItem(CBBlocks.EXPERIENCE_CLUSTER.get(),
			ExperienceConstants::clusterXpValue, new Item.Properties()));

	public static final RegistryObject<Item> EXPERIENCE = ITEMS.register("experience",
		() -> new HiddenExperienceItem(new Item.Properties()));

	public static final RegistryObject<Item> SQUID_PRINTER = ITEMS.register("squid_printer",
		() -> new SquidPrinterItem(CBBlocks.SQUID_PRINTER.get(), new Item.Properties()));

	public static final RegistryObject<Item> PETRI_DISH = ITEMS.register("petri_dish",
		() -> new BlockItem(CBBlocks.PETRI_DISH.get(), new Item.Properties()));

	public static final RegistryObject<EnchantmentBookCopyItem> ENCHANTMENT_BOOK_COPY =
		ITEMS.register("enchantment_book_copy", () -> new EnchantmentBookCopyItem(new Item.Properties()));

	public static final RegistryObject<Item> SLIME_BELT_CONNECTOR = ITEMS.register("slime_belt_connector",
		() -> new SlimeBeltConnectorItem(new Item.Properties()));

	public static final RegistryObject<Item> MAGMA_BELT_CONNECTOR = ITEMS.register("magma_belt_connector",
		() -> new MagmaBeltConnectorItem(new Item.Properties()));

	public static final RegistryObject<Item> POWER_BELT_CONNECTOR = ITEMS.register("power_belt_connector",
		() -> new PowerBeltConnectorItem(new Item.Properties()));

	public static final RegistryObject<Item> SMART_SUPER_GLUE = ITEMS.register("smart_super_glue",
		() -> new SmartSuperGlueItem(new Item.Properties().stacksTo(1).durability(99)));

	public static final RegistryObject<Item> WIRELESS_TERMINAL = ITEMS.register("wireless_terminal",
		() -> new WirelessTerminalItem(new Item.Properties().stacksTo(1)));

	public static final RegistryObject<Item> HALF_SHAFT = ITEMS.register("half_shaft",
		() -> new Item(new Item.Properties()));

	public static final RegistryObject<Item> CAPTURED_SMALL_SLIME = ITEMS.register("captured_small_slime",
		() -> new CapturedSmallSlimeItem(new Item.Properties().stacksTo(4)));

	public static final RegistryObject<Item> UNIVERSAL_JOINT = ITEMS.register("universal_joint",
		() -> new UniversalJointItem(new Item.Properties()));

	public static final RegistryObject<Item> SLIME_CLUTCH = ITEMS.register("slime_clutch",
		() -> new BlockItem(CBBlocks.SLIME_CLUTCH.get(), new Item.Properties()));

	public static final RegistryObject<Item> BONE_RATCHET = ITEMS.register("bone_ratchet",
		() -> new BlockItem(CBBlocks.BONE_RATCHET.get(), new Item.Properties()));

	public static final RegistryObject<Item> CARDBOARD_BOX = ITEMS.register("cardboard_box",
		() -> new CardboardBoxItem(new Item.Properties().stacksTo(16)));

	public static final RegistryObject<Item> LARGE_CARDBOARD_BOX = ITEMS.register("large_cardboard_box",
		() -> new LargeCardboardBoxItem(new Item.Properties().stacksTo(16)));

	public static final RegistryObject<Item> SCHRODINGERS_CAT = ITEMS.register("schrodingers_cat",
		() -> new BlockItem(CBBlocks.SCHRODINGERS_CAT.get(), new Item.Properties()));

	public static final RegistryObject<Item> SPIDER_ASSEMBLY_TABLE = ITEMS.register("spider_assembly_table",
		() -> new SpiderAssemblyTableItem(CBBlocks.SPIDER_ASSEMBLY_TABLE.get(), new Item.Properties()));

	public static final RegistryObject<Item> FIXED_CARROT_FISHING_ROD = ITEMS.register("fixed_carrot_fishing_rod",
		() -> new BlockItem(CBBlocks.FIXED_CARROT_FISHING_ROD.get(), new Item.Properties()));

	public static final RegistryObject<Item> GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION =
		ITEMS.register("ghast_hot_air_balloon_assembly_station",
			() -> new BlockItem(CBBlocks.GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION.get(), new Item.Properties()));

	public static final RegistryObject<Item> GHAST_HELM = ITEMS.register("ghast_helm",
		() -> new BlockItem(CBBlocks.GHAST_HELM.get(), new Item.Properties()));

	public static final RegistryObject<Item> CREEPER_BLAST_CHAMBER = ITEMS.register("creeper_blast_chamber",
		() -> new BlockItem(CBBlocks.CREEPER_BLAST_CHAMBER.get(), new Item.Properties()));

	public static final RegistryObject<Item> BIO_PACKAGER = ITEMS.register("bio_packager",
		() -> new BlockItem(CBBlocks.BIO_PACKAGER.get(), new Item.Properties()));

	public static final RegistryObject<Item> SHULKER_PACKAGER = ITEMS.register("shulker_packager",
		() -> new ShulkerPackagerItem(CBBlocks.SHULKER_PACKAGER.get(), new Item.Properties()));

	public static final RegistryObject<Item> SHULKER_PACKAGE = ITEMS.register("shulker_package",
		() -> new ShulkerPackageItem(new Item.Properties().stacksTo(1)));

	public static final RegistryObject<Item> SHULKER_TELEPORTER = ITEMS.register("shulker_teleporter",
		() -> new BlockItem(CBBlocks.SHULKER_TELEPORTER.get(), new Item.Properties()));

	public static final RegistryObject<Item> INCOMPLETE_CREEPER_BLAST_CHAMBER =
		ITEMS.register("incomplete_creeper_blast_chamber", () -> new Item(new Item.Properties()));

	public static final RegistryObject<Item> BIONIC_MECHANISM = ITEMS.register("bionic_mechanism",
		() -> new Item(new Item.Properties()));

	public static final RegistryObject<Item> ASURINE_ALLOY = ITEMS.register("asurine_alloy",
		() -> new Item(new Item.Properties()));

	public static final RegistryObject<Item> CARBON_POWDER = ITEMS.register("carbon_powder",
		() -> new Item(new Item.Properties()));

	public static final RegistryObject<Item> GRAPHITE = ITEMS.register("graphite",
		() -> new Item(new Item.Properties()));

	public static final RegistryObject<Item> ZINC_SHEET = ITEMS.register("zinc_sheet",
		() -> new Item(new Item.Properties()));

	public static final RegistryObject<Item> INCOMPLETE_BIONIC_MECHANISM =
		ITEMS.register("incomplete_bionic_mechanism", () -> new Item(new Item.Properties()));

	public static final RegistryObject<Item> ASURINE_CASING = ITEMS.register("asurine_casing",
		() -> new BlockItem(CBBlocks.ASURINE_CASING.get(), new Item.Properties()));

	public static final RegistryObject<Item> BIOTECH_CASING = ITEMS.register("biotech_casing",
		() -> new BlockItem(CBBlocks.BIOTECH_CASING.get(), new Item.Properties()));

	public static final RegistryObject<Item> EXPLOSION_PROOF_CASING = ITEMS.register("explosion_proof_casing",
		() -> new BlockItem(CBBlocks.EXPLOSION_PROOF_CASING.get(), new Item.Properties()));

	public static final RegistryObject<Item> EXPLOSION_PROOF_ITEM_VAULT = ITEMS.register("explosion_proof_item_vault",
		() -> new ExplosionProofItemVaultItem(CBBlocks.EXPLOSION_PROOF_ITEM_VAULT.get(), new Item.Properties()));

	public static final RegistryObject<Item> BLAST_PROOF_GLASS = ITEMS.register("blast_proof_glass",
		() -> new BlockItem(CBBlocks.BLAST_PROOF_GLASS.get(), new Item.Properties()));

	public static final RegistryObject<Item> BLAST_PROOF_FRAMED_GLASS = ITEMS.register("blast_proof_framed_glass",
		() -> new BlockItem(CBBlocks.BLAST_PROOF_FRAMED_GLASS.get(), new Item.Properties()));

	public static final Map<DyeColor, RegistryObject<Item>> BUFFER_PADS = registerBufferPads();
	public static final RegistryObject<Item> BUFFER_PAD = BUFFER_PADS.get(DyeColor.RED);

	// Butter Cat content is registered through the shared ButterCat registrate, and re-exported
	// here so the project's primary item registry remains the place to inspect mod items.
	public static final ItemEntry<ConfigurableButterFoodItem> BUTTER = ModItems.BUTTER;
	public static final ItemEntry<ConfigurableButterSequencedAssemblyItem> INCOMPLETE_SUPER_BUTTER =
		ModItems.INCOMPLETE_SUPER_BUTTER;
	public static final ItemEntry<ConfigurableButterFoodItem> SUPER_BUTTER = ModItems.SUPER_BUTTER;

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

	private static Map<DyeColor, RegistryObject<Item>> registerBufferPads() {
		EnumMap<DyeColor, RegistryObject<Item>> bufferPads = new EnumMap<>(DyeColor.class);
		for (DyeColor color : DyeColor.values()) {
			String id = CBBlocks.bufferPadId(color);
			bufferPads.put(color, ITEMS.register(id,
				() -> new BlockItem(CBBlocks.BUFFER_PADS.get(color).get(), new Item.Properties())));
		}
		return Collections.unmodifiableMap(bufferPads);
	}
}
