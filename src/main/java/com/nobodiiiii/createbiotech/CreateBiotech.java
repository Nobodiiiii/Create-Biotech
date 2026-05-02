package com.nobodiiiii.createbiotech;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CreateBiotech.MOD_ID)
public class CreateBiotech {
	public static final String MOD_ID = "create_biotech";

	public CreateBiotech() {
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		CBBlocks.register(modEventBus);
		CBItems.register(modEventBus);
		CBBlockEntityTypes.register(modEventBus);
		modEventBus.addListener(CBItems::addToCreativeTabs);
	}
}
