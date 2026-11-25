package com.nobodiiiii.createbiotech.content.surgery.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.client.CBKeyMappings;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitBlockEntity;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalKitItem;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalJointItem;
import com.simibubi.create.AllKeys;
import com.simibubi.create.content.equipment.toolbox.RadialToolboxMenu;
import com.simibubi.create.content.equipment.toolbox.ToolboxHandler;

import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public final class SurgicalKitClientHandler {
	private SurgicalKitClientHandler() {}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onKeyInput(InputEvent.Key event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (event.getAction() != InputConstants.PRESS
			|| !CBKeyMappings.matches(CBKeyMappings.SURGICAL_KIT, event))
			return;
		if (minecraft.screen != null && !(minecraft.screen instanceof RadialToolboxMenu))
			return;
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator())
			return;
		InteractionHand hand = hasWheel(player.getMainHandItem())
			? InteractionHand.MAIN_HAND
			: hasWheel(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
		if (hand != null) {
			ScreenOpener.open(new SurgicalKitRadialScreen(hand, player.getItemInHand(hand)));
			return;
		}
		if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
			&& player.level().getBlockEntity(hit.getBlockPos()) instanceof SurgicalKitBlockEntity kit
			&& kit.inRange(player)) {
			ScreenOpener.open(new SurgicalKitRadialScreen(kit, true));
			return;
		}
		SurgicalKitBlockEntity kit = SurgicalKitBlockEntity.nearest(player);
		if (kit == null)
			return;
		if (AllKeys.TOOLBELT.doesModifierAndCodeMatch(event.getKey())) {
			var toolboxes = ToolboxHandler.getNearest(player.level(), player, 1);
			if (!toolboxes.isEmpty() && ToolboxHandler.distance(player.position(), toolboxes.getFirst().getBlockPos())
				<= ToolboxHandler.distance(player.position(), kit.getBlockPos()))
				return;
		}
		ScreenOpener.open(new SurgicalKitRadialScreen(kit, true));
	}

	@SubscribeEvent
	public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || minecraft.screen != null
			|| player == null || player.isSpectator() || player.isSecondaryUseActive()
			|| !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
			|| !(player.level().getBlockEntity(hit.getBlockPos()) instanceof SurgicalKitBlockEntity kit)
			|| !kit.inRange(player))
			return;
		event.setSwingHand(false);
		event.setCanceled(true);
		ScreenOpener.open(new SurgicalKitRadialScreen(kit, false));
	}

	private static boolean hasWheel(ItemStack stack) {
		return SurgicalKitItem.isKit(stack) || SurgicalJointItem.canSelect(stack);
	}
}
