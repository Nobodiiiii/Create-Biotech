package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxItem;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultItem;
import com.nobodiiiii.createbiotech.content.cardboardbox.LargeCardboardBoxItem;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltConnectorItem;
import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltConnectorItem;
import com.nobodiiiii.createbiotech.content.processing.basin.CapturedSmallSlimeItem;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltConnectorItem;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CBItems {

	public static final DeferredRegister<Item> ITEMS =
		DeferredRegister.create(ForgeRegistries.ITEMS, CreateBiotech.MOD_ID);

	public static final RegistryObject<Item> EVOKER_TANK = ITEMS.register("evoker_tank",
		() -> new BlockItem(CBBlocks.EVOKER_TANK.get(), new Item.Properties()));

	public static final RegistryObject<Item> SLIME_BELT_CONNECTOR = ITEMS.register("slime_belt_connector",
		() -> new SlimeBeltConnectorItem(new Item.Properties()));

	public static final RegistryObject<Item> MAGMA_BELT_CONNECTOR = ITEMS.register("magma_belt_connector",
		() -> new MagmaBeltConnectorItem(new Item.Properties()));

	public static final RegistryObject<Item> POWER_BELT_CONNECTOR = ITEMS.register("power_belt_connector",
		() -> new PowerBeltConnectorItem(new Item.Properties()));

	public static final RegistryObject<Item> HALF_SHAFT = ITEMS.register("half_shaft",
		() -> new Item(new Item.Properties()));

	public static final RegistryObject<Item> CAPTURED_SMALL_SLIME = ITEMS.register("captured_small_slime",
		() -> new CapturedSmallSlimeItem(new Item.Properties().stacksTo(4)));

	public static final RegistryObject<Item> UNIVERSAL_JOINT = ITEMS.register("universal_joint",
		() -> new UniversalJointItem(new Item.Properties()));

	public static final RegistryObject<Item> CARDBOARD_BOX = ITEMS.register("cardboard_box",
		() -> new CardboardBoxItem(new Item.Properties()));

	public static final RegistryObject<Item> LARGE_CARDBOARD_BOX = ITEMS.register("large_cardboard_box",
		() -> new LargeCardboardBoxItem(new Item.Properties().stacksTo(1)));

	public static final RegistryObject<Item> SCHRODINGERS_CAT = ITEMS.register("schrodingers_cat",
		() -> new BlockItem(CBBlocks.SCHRODINGERS_CAT.get(), new Item.Properties()));

	public static final RegistryObject<Item> FIXED_CARROT_FISHING_ROD = ITEMS.register("fixed_carrot_fishing_rod",
		() -> new BlockItem(CBBlocks.FIXED_CARROT_FISHING_ROD.get(), new Item.Properties()));

	public static final RegistryObject<Item> CREEPER_BLAST_CHAMBER = ITEMS.register("creeper_blast_chamber",
		() -> new BlockItem(CBBlocks.CREEPER_BLAST_CHAMBER.get(), new Item.Properties()));

	public static final RegistryObject<Item> EXPLOSION_PROOF_CASING = ITEMS.register("explosion_proof_casing",
		() -> new BlockItem(CBBlocks.EXPLOSION_PROOF_CASING.get(), new Item.Properties()));

	public static final RegistryObject<Item> EXPLOSION_PROOF_ITEM_VAULT = ITEMS.register("explosion_proof_item_vault",
		() -> new ExplosionProofItemVaultItem(CBBlocks.EXPLOSION_PROOF_ITEM_VAULT.get(), new Item.Properties()));

	public static final RegistryObject<Item> BLAST_PROOF_GLASS = ITEMS.register("blast_proof_glass",
		() -> new BlockItem(CBBlocks.BLAST_PROOF_GLASS.get(), new Item.Properties()));

	public static final RegistryObject<Item> BLAST_PROOF_FRAMED_GLASS = ITEMS.register("blast_proof_framed_glass",
		() -> new BlockItem(CBBlocks.BLAST_PROOF_FRAMED_GLASS.get(), new Item.Properties()));

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
}
