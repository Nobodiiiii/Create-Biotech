package com.nobodiiiii.createbiotech;

import com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod.FixedCarrotFishingRodGoalHandler;
import com.nobodiiiii.createbiotech.content.buttercat.ButterCatModule;
import com.nobodiiiii.createbiotech.content.bufferpad.BufferPadMovementBehaviour;
import com.nobodiiiii.createbiotech.content.explosionproofitemvault.ExplosionProofItemVaultCompat;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastBalloonRopeShearsInteraction;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHelmMovingInteraction;
import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHelmMovementBehaviour;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBContraptionTypes;
import com.nobodiiiii.createbiotech.registry.CBCreativeModeTabs;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBFluids;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;
import com.nobodiiiii.createbiotech.registry.CBParticleTypes;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;

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
		CBEntityTypes.register(modEventBus);
		CBMenuTypes.register(modEventBus);
		CBParticleTypes.register(modEventBus);
		CBRecipeTypes.register(modEventBus);
		ButterCatModule.init(modEventBus);
		modEventBus.addListener(CreateBiotech::onCommonSetup);
		modEventBus.addListener(CreateBiotech::onRegister);
		CBPackets.register();
		FixedCarrotFishingRodGoalHandler.register();
	}

	private static void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			ExplosionProofItemVaultCompat.register();
			EncasingRegistry.addVariant(CBBlocks.EXPERIENCE_PIPE.get(), CBBlocks.ENCASED_EXPERIENCE_PIPE.get());
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
		CBContraptionTypes.init();
	}

	public static ResourceLocation asResource(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
