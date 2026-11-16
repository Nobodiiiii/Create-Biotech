package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Publishes complete press snapshots before Flywheel's parallel frame callbacks. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class ChamberPressRenderHandler {
	private ChamberPressRenderHandler() {}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		// All pressure heads have ticked, regardless of their order in the block-entity list.
		ChamberClientEffects.preparePressRenderStates(Minecraft.getInstance().level);
	}

	@SubscribeEvent
	public static void onRenderFrame(RenderFrameEvent.Pre event) {
		// Packets, newly loaded chambers and removals can invalidate a snapshot between ticks.
		ChamberClientEffects.prepareDirtyPressRenderStates(Minecraft.getInstance().level);
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel().isClientSide())
			ChamberClientEffects.clearPressRenderStates(event.getLevel());
	}

	@SubscribeEvent
	public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		ChamberClientEffects.clearPressRenderStates(null);
	}
}
