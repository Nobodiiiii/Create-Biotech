package com.nobodiiiii.createbiotech.content.slimemimic;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class GreenprintInteractionHandler {

	private GreenprintInteractionHandler() {}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		InteractionResult result = record(event, event.getTarget());
		if (result != InteractionResult.PASS) {
			event.setCancellationResult(result);
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
		InteractionResult result = record(event, event.getTarget());
		if (result != InteractionResult.PASS) {
			event.setCancellationResult(result);
			event.setCanceled(true);
		}
	}

	private static InteractionResult record(PlayerInteractEvent event, Entity target) {
		if (!(event.getItemStack().getItem() instanceof GreenprintItem item))
			return InteractionResult.PASS;
		if (event.getEntity().isShiftKeyDown())
			return item.clearBinding(event.getItemStack(), event.getEntity());
		if (target instanceof PartEntity<?> part)
			target = part.getParent();
		// Intercept before trading, mounting or entity-specific interaction consumes the click.
		return target instanceof LivingEntity living
			? item.interactLivingEntity(event.getItemStack(), event.getEntity(), living, event.getHand())
			: InteractionResult.PASS;
	}
}
