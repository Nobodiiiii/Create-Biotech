package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.spiderassemblytable.SpiderAssemblyTableMenu;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CBMenuTypes {

	public static final DeferredRegister<MenuType<?>> MENU_TYPES =
		DeferredRegister.create(ForgeRegistries.MENU_TYPES, CreateBiotech.MOD_ID);

	public static final RegistryObject<MenuType<SpiderAssemblyTableMenu>> SPIDER_ASSEMBLY_TABLE =
		MENU_TYPES.register("spider_assembly_table", () -> IForgeMenuType.create(SpiderAssemblyTableMenu::new));

	private CBMenuTypes() {}

	public static void register(IEventBus modEventBus) {
		MENU_TYPES.register(modEventBus);
	}
}
