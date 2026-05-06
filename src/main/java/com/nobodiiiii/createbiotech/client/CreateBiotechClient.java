package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.content.evokertank.EvokerTankRenderer;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.client.render.SlimeBeltFunnelModel;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltHelper;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltRenderer;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltSpriteShifts;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltRenderer;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltSpriteShifts;
import com.nobodiiiii.createbiotech.content.universaljoint.UniversalJointRenderer;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.Create;
import com.simibubi.create.CreateClient;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CreateBiotechClient {

	@SubscribeEvent
	public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(CBBlockEntityTypes.EVOKER_TANK.get(), EvokerTankRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SLIME_BELT.get(), SlimeBeltRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.MAGMA_BELT.get(), MagmaBeltRenderer::new);
		event.registerBlockEntityRenderer(CBBlockEntityTypes.UNIVERSAL_JOINT.get(), UniversalJointRenderer::new);
	}

	@SubscribeEvent
	public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
		SlimeBeltSpriteShifts.init();
		MagmaBeltSpriteShifts.init();
		event.registerReloadListener(SlimeBeltHelper.LISTENER);
		event.registerReloadListener(MagmaBeltHelper.LISTENER);
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			CreateClient.MODEL_SWAPPER.getCustomBlockModels()
				.register(Create.asResource("andesite_belt_funnel"), SlimeBeltFunnelModel::new);
			CreateClient.MODEL_SWAPPER.getCustomBlockModels()
				.register(Create.asResource("brass_belt_funnel"), SlimeBeltFunnelModel::new);
		});
	}
}
