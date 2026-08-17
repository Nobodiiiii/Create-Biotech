package com.nobodiiiii.createbiotech.client;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonItem;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SonicDogCannonClientHandler {

	private static final float MAX_FOV_REDUCTION = 0.15f;

	private SonicDogCannonClientHandler() {}

	@SubscribeEvent
	public static void onComputeFovModifier(ComputeFovModifierEvent event) {
		Player player = event.getPlayer();
		if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof SonicDogCannonItem))
			return;

		float charge = Math.min(player.getTicksUsingItem() / (float) SonicDogCannonItem.FULL_CHARGE_TICKS, 1.0f);
		charge *= charge;
		float zoomedFov = event.getNewFovModifier() * (1.0f - charge * MAX_FOV_REDUCTION);
		float effectScale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
		event.setNewFovModifier(Mth.lerp(effectScale, event.getNewFovModifier(), zoomedFov));
	}
}
