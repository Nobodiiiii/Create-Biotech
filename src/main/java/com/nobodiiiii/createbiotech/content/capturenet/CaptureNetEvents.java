package com.nobodiiiii.createbiotech.content.capturenet;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class CaptureNetEvents {
	private CaptureNetEvents() {}

	@SubscribeEvent
	public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
		if (handle(event, event.getTarget())) {
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
		}
	}

	@SubscribeEvent
	public static void interact(PlayerInteractEvent.EntityInteract event) {
		if (handle(event, event.getTarget())) {
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
		}
	}

	private static boolean handle(PlayerInteractEvent event, net.minecraft.world.entity.Entity target) {
		var stack = event.getItemStack();
		if (!(stack.getItem() instanceof CaptureNetItem net) || event.getEntity().isSpectator()) return false;
		net.interact(stack, event.getEntity(), event.getHand(), target);
		return true;
	}
}
