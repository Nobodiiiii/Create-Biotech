package com.nobodiiiii.createbiotech.content.surgery;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class CreativeSurgicalKitInteractionHandler {

	private CreativeSurgicalKitInteractionHandler() {}

	@SubscribeEvent
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		Player player = event.getEntity();
		ItemStack stack = event.getItemStack();
		if (player.isSpectator() || !(stack.getItem() instanceof CreativeSurgicalKitItem kit)
			|| !(event.getTarget() instanceof LivingEntity target) || !target.isAlive())
			return;

		// Run before mobInteract: untamed llamas consume the click before the item callback.
		InteractionResult result = kit.interactLivingEntity(stack, player, target, event.getHand());
		if (!result.consumesAction())
			return;

		event.setCancellationResult(result);
		event.setCanceled(true);
	}
}
