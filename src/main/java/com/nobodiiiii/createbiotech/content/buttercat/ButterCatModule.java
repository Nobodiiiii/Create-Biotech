package com.nobodiiiii.createbiotech.content.buttercat;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModArmInteractions;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModBlockEnetities;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModBlocks;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModConfigs;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModEffects;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModFluids;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModItems;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModPartialModels;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModPonder;
import com.nobodiiiii.createbiotech.content.buttercat.register.ModPotions;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.createmod.catnip.lang.FontHelper;
import net.createmod.ponder.foundation.PonderIndex;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.RegisterEvent;

public final class ButterCatModule {
	public static final String MODID = CreateBiotech.MOD_ID;
	public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID);

	private static boolean initialized;
	private static boolean clientInitialized;

	static {
		REGISTRATE.setTooltipModifierFactory(item -> new ItemDescription.Modifier(item,
			FontHelper.Palette.STANDARD_CREATE).andThen(TooltipModifier.mapNull(KineticStats.create(item))));
	}

	private ButterCatModule() {}

	public static void init(IEventBus modEventBus) {
		if (initialized)
			return;
		initialized = true;

		REGISTRATE.registerEventListeners(modEventBus);
		ModBlocks.register();
		ModBlockEnetities.register();
		ModItems.register();
		ModFluids.register();
		ModEffects.register(modEventBus);
		ModPotions.register(modEventBus);
		ModPartialModels.init();
		modEventBus.addListener(ButterCatModule::onRegister);
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModConfigs.COMMON_SPEC);
	}

	public static void clientInit() {
		if (clientInitialized)
			return;
		clientInitialized = true;

		PonderIndex.addPlugin(new ModPonder());
		ItemBlockRenderTypes.setRenderLayer(ModBlocks.BUTTER_CAT_ENGINE.get(), RenderType.cutoutMipped());
		ItemBlockRenderTypes.setRenderLayer(ModFluids.CREAM.getSource(), RenderType.translucent());
		ItemBlockRenderTypes.setRenderLayer(ModFluids.CREAM.get(), RenderType.translucent());
		ModFluids.CREAM.getBlock()
			.ifPresent(block -> ItemBlockRenderTypes.setRenderLayer(block, RenderType.translucent()));
	}

	private static void onRegister(RegisterEvent event) {
		ModArmInteractions.register();
	}

	public static ResourceLocation rl(String path) {
		return CreateBiotech.asResource(path);
	}

	public static boolean isLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}
}
