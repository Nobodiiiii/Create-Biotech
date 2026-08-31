package com.nobodiiiii.createbiotech.content.surgery.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.simibubi.create.content.equipment.toolbox.RadialToolboxMenu;

import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/** Ensures a held kit wins if Create opens its nearby-toolbox wheel for the same bound key. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class SurgicalKitClientHandler {
	private SurgicalKitClientHandler() {}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onKeyInput(InputEvent.Key event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (event.getAction() != InputConstants.PRESS || !SurgicalKitKeyMappings.matches(event))
			return;
		if (minecraft.screen != null && !(minecraft.screen instanceof RadialToolboxMenu))
			return;
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator())
			return;
		InteractionHand hand = SurgicalKitItem.isKit(player.getMainHandItem())
			? InteractionHand.MAIN_HAND
			: SurgicalKitItem.isKit(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
		if (hand != null)
			ScreenOpener.open(new SurgicalKitRadialScreen(hand));
	}
}
