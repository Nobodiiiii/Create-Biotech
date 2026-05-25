package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LargeCardboardBoxHandler {

	private LargeCardboardBoxHandler() {}

	@SubscribeEvent
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		Player player = event.getEntity();
		if (!player.isCreative())
			return;

		InteractionHand hand = event.getHand();
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(CBItems.LARGE_CARDBOARD_BOX.get()))
			return;
		if (player.isShiftKeyDown())
			return;
		if (CapturedEntityBoxItem.hasCapturedEntity(stack))
			return;

		Entity target = event.getTarget();
		if (!(target instanceof Mob mobTarget))
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);

		if (player.level().isClientSide())
			return;
		if (!CapturedEntityBoxHelper.captureEntity(stack, mobTarget))
			return;

		mobTarget.discard();
	}

	@SubscribeEvent
	public static void onLivingDamage(LivingDamageEvent event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide())
			return;
		if (!(target instanceof Mob))
			return;
		if (target.getHealth() > event.getAmount())
			return;

		Player player = getCapturingPlayer(event.getSource());
		if (player == null)
			return;

		ItemStack offhandStack = player.getOffhandItem();
		if (!offhandStack.is(CBItems.LARGE_CARDBOARD_BOX.get()))
			return;
		if (CapturedEntityBoxItem.hasCapturedEntity(offhandStack))
			return;
		if (!CapturedEntityBoxHelper.captureEntity(offhandStack, target))
			return;

		event.setCanceled(true);
		target.discard();
		CBAdvancements.awardPlayer((net.minecraft.server.level.ServerLevel) player.level(), player.getUUID(),
			CBAdvancements.LARGE_CARDBOARD_BOX);
	}

	private static Player getCapturingPlayer(DamageSource source) {
		Entity sourceEntity = source.getEntity();
		return sourceEntity instanceof Player player ? player : null;
	}
}
