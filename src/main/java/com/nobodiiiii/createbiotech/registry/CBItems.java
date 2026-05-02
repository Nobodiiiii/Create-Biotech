package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltConnectorItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CBItems {

	public static final DeferredRegister<Item> ITEMS =
		DeferredRegister.create(ForgeRegistries.ITEMS, CreateBiotech.MOD_ID);

	public static final RegistryObject<Item> SLIME_BELT_CONNECTOR = ITEMS.register("slime_belt_connector",
		() -> new SlimeBeltConnectorItem(new Item.Properties()));

	private CBItems() {}

	public static void register(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
	}

	public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS)
			event.accept(SLIME_BELT_CONNECTOR.get());
	}

	public static boolean isSlimeBeltConnector(ItemStack stack) {
		return stack.is(SLIME_BELT_CONNECTOR.get());
	}
}
