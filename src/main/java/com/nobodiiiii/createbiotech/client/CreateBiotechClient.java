package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltRenderer;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CreateBiotechClient {

	@SubscribeEvent
	public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(CBBlockEntityTypes.SLIME_BELT.get(), SlimeBeltRenderer::new);
	}

	@SubscribeEvent
	public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
		event.registerReloadListener(SlimeBeltHelper.LISTENER);
	}
}
