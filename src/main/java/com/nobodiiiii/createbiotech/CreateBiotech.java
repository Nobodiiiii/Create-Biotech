package com.nobodiiiii.createbiotech;

import net.minecraft.core.registries.Registries;
import com.nobodiiiii.createbiotech.foundation.render.material.MaterialRenderingModule;

import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodGoalHandler;
import com.nobodiiiii.createbiotech.content.shulkerpackager.ShulkerPackagerArmInteractions;
import com.nobodiiiii.createbiotech.content.bufferpad.BufferPadMovementBehaviour;
import com.nobodiiiii.createbiotech.content.experience.ExperienceOpenPipeEffectHandler;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultCompat;
import com.nobodiiiii.createbiotech.content.frogportal.FrogStomachSecretionSpreading;
import com.nobodiiiii.createbiotech.content.frogportal.FrogStomachSlimeSpawning;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastBalloonRopeShearsInteraction;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHelmMovingInteraction;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHelmMovementBehaviour;
import com.nobodiiiii.createbiotech.data.CBDataGenerators;
import com.nobodiiiii.createbiotech.foundation.block.CBMultiBlockLifecycle;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBArmInteractionPointTypes;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBCapabilities;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBContraptionTypes;
import com.nobodiiiii.createbiotech.registry.CBCreativeModeTabs;
import com.nobodiiiii.createbiotech.registry.CBDataComponents;
import com.nobodiiiii.createbiotech.registry.CBDisplaySources;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBFluids;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBIngredients;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;
import com.nobodiiiii.createbiotech.registry.CBMobEffects;
import com.nobodiiiii.createbiotech.registry.CBParticleTypes;
import com.nobodiiiii.createbiotech.registry.CBPoiTypes;
import com.nobodiiiii.createbiotech.registry.CBPotions;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;
import com.nobodiiiii.createbiotech.registry.CBRecipeConditions;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.api.stress.BlockStressValues;
import com.yision.allay.block.allayport.AllayPortTargetRegistry;
import com.yision.allay.logistics.courier.AllayCourierTaskManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(CreateBiotech.MOD_ID)
public class CreateBiotech {
	public static final String MOD_ID = "create_biotech";

	public CreateBiotech(IEventBus modEventBus, ModContainer modContainer) {
		MaterialRenderingModule.register(modEventBus);
		CBConfigs.register(modContainer);
		CBBlocks.register(modEventBus);
		CBDataComponents.register(modEventBus);
		CBItems.register(modEventBus);
		CBIngredients.register(modEventBus);
		CBFluids.register(modEventBus);
		CBPoiTypes.register(modEventBus);
		CBCreativeModeTabs.register(modEventBus);
		CBBlockEntityTypes.register(modEventBus);
		modEventBus.addListener(CBCapabilities::register);
		CBEntityTypes.register(modEventBus);
		CBMenuTypes.register(modEventBus);
		CBParticleTypes.register(modEventBus);
		CBRecipeConditions.register(modEventBus);
		CBRecipeTypes.register(modEventBus);
		CBMobEffects.register(modEventBus);
		CBPotions.register(modEventBus);
		modEventBus.addListener(CBDataGenerators::gatherData);
		modEventBus.addListener(CreateBiotech::onCommonSetup);
		modEventBus.addListener(CreateBiotech::onRegister);
		CBPackets.register();
		registerAllayEvents();
		FixedCarrotFishingRodGoalHandler.register();
		FrogStomachSlimeSpawning.register();
		FrogStomachSecretionSpreading.register();
	}

	private static void registerAllayEvents() {
		NeoForge.EVENT_BUS.addListener(AllayCourierTaskManager::onServerTick);
		NeoForge.EVENT_BUS.addListener(AllayPortTargetRegistry::onServerTick);
		NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> {
			AllayPortTargetRegistry.clear();
			AllayCourierTaskManager.onServerStarting(event.getServer());
		});
	}

	private static void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			CBMultiBlockLifecycle.registerMovementChecks();
			CBDisplaySources.register();
			ExperienceOpenPipeEffectHandler.register();
			ExplosionProofItemVaultCompat.register();
			BlockStressValues.IMPACTS.register(CBBlocks.EXPERIENCE_PUMP.get(), () -> 4.0d);
			BlockStressValues.CAPACITIES.register(CBBlocks.AUTOMATIC_FISH_RELEASE_MACHINE.get(),
				() -> BlockStressValues.getCapacity(AllBlocks.LARGE_WATER_WHEEL.get()));
			BlockStressValues.RPM.register(CBBlocks.AUTOMATIC_FISH_RELEASE_MACHINE.get(),
				new BlockStressValues.GeneratedRpm(4, false));
			CBBlocks.registerButterCatStressValues();
			CBFluids.registerCreamDispenseBehavior();
			BoilerHeater.REGISTRY.register(CBBlocks.MAGMA_CUBE_BURNER.get(), BoilerHeater.BLAZE_BURNER);
			MovementBehaviour.REGISTRY.register(CBBlocks.GHAST_HELM.get(), new GhastHelmMovementBehaviour());
			BufferPadMovementBehaviour bufferPadMovementBehaviour = new BufferPadMovementBehaviour();
			for (DyeColor color : DyeColor.values())
				MovementBehaviour.REGISTRY.register(CBBlocks.BUFFER_PADS.get(color).get(), bufferPadMovementBehaviour);
			MovingInteractionBehaviour.REGISTRY.register(CBBlocks.GHAST_HELM.get(), new GhastHelmMovingInteraction());
			GhastBalloonRopeShearsInteraction ghastBalloonRopeShears = new GhastBalloonRopeShearsInteraction();
			MovingInteractionBehaviour.REGISTRY.register(AllBlocks.ROPE.get(), ghastBalloonRopeShears);
			MovingInteractionBehaviour.REGISTRY.register(AllBlocks.PULLEY_MAGNET.get(), ghastBalloonRopeShears);
		});
	}

	private static void onRegister(RegisterEvent event) {
		ShulkerPackagerArmInteractions.register();
		CBArmInteractionPointTypes.register();
		CBContraptionTypes.init();
	}

	public static ResourceLocation asResource(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
