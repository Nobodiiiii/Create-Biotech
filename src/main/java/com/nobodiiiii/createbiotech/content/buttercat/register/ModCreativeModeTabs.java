package com.nobodiiiii.createbiotech.content.buttercat.register;

import com.nobodiiiii.createbiotech.registry.CBCreativeModeTabs;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeModeTabs {
	public static final RegistryObject<CreativeModeTab> CBC_TAB = CBCreativeModeTabs.MAIN;

	private ModCreativeModeTabs() {}

	public static void register(IEventBus eventBus) {}
}
