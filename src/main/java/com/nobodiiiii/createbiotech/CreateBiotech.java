package com.nobodiiiii.createbiotech;

import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodGoalHandler;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultCompat;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBCreativeModeTabs;
import com.nobodiiiii.createbiotech.registry.CBFluids;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(CreateBiotech.MOD_ID)
public class CreateBiotech {
	public static final String MOD_ID = "create_biotech";

	public CreateBiotech() {
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		CBBlocks.register(modEventBus);
		CBItems.register(modEventBus);
		CBFluids.register(modEventBus);
		CBCreativeModeTabs.register(modEventBus);
		CBBlockEntityTypes.register(modEventBus);
		CBRecipeTypes.register(modEventBus);
		modEventBus.addListener(CreateBiotech::onCommonSetup);
		CBPackets.register();
		FixedCarrotFishingRodGoalHandler.register();
	}

	private static void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(ExplosionProofItemVaultCompat::register);
	}

	public static ResourceLocation asResource(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
