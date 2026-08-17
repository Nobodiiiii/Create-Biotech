package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBMobEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SonicDogCannonStunClientHandler {

	private SonicDogCannonStunClientHandler() {}

	@SubscribeEvent
	public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.isPaused())
			return;

		MobEffectInstance stun = player.getEffect(CBMobEffects.STUN.get());
		if (stun == null)
			return;

		float ticks = player.tickCount + (float) event.getPartialTick();
		float amplitude = (1 + stun.getAmplifier()) * 0.01f;
		event.setPitch((float) (event.getPitch() + amplitude * Math.cos(ticks * 3 + 2) * 25));
		event.setYaw((float) (event.getYaw() + amplitude * Math.cos(ticks * 5 + 1) * 25));
		event.setRoll((float) (event.getRoll() + amplitude * Math.cos(ticks * 4) * 25));
	}
}
