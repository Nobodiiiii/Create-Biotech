package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltConnectorItem;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltConnectorItem;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
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

	public static final RegistryObject<Item> UNIVERSAL_JOINT = ITEMS.register("universal_joint",
		() -> new UniversalJointItem(new Item.Properties()));

	private CBItems() {}

	public static void register(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
	}

	public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
			event.accept(EVOKER_TANK.get());
			event.accept(SLIME_BELT_CONNECTOR.get());
			event.accept(MAGMA_BELT_CONNECTOR.get());
			event.accept(UNIVERSAL_JOINT.get());
		}
	}

	public static boolean isSlimeBeltConnector(ItemStack stack) {
		return stack.is(SLIME_BELT_CONNECTOR.get());
	}

	public static boolean isMagmaBeltConnector(ItemStack stack) {
		return stack.is(MAGMA_BELT_CONNECTOR.get());
	}

	public static boolean isCustomBeltConnector(ItemStack stack) {
		return isSlimeBeltConnector(stack) || isMagmaBeltConnector(stack);
	}
}
