package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class LargeCardboardBoxHandler {

	private LargeCardboardBoxHandler() {}

	@SubscribeEvent
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		Player player = event.getEntity();

		InteractionHand hand = event.getHand();
		ItemStack stack = player.getItemInHand(hand);
		if (!CapturedEntityBoxHelper.isEmptyLargeBox(stack))
			return;
		if (CBConfigs.SERVER.cardboardBox.largeBoxCreativeOnly.get() && !player.isCreative())
			return;
		if (player.isShiftKeyDown())
			return;
		LivingEntity livingTarget = CapturedEntityBoxHelper.resolveLivingTarget(event.getTarget());
		if (!(livingTarget instanceof Mob mobTarget))
			return;
		if (!canLargeBoxCapture(mobTarget))
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);

		if (player.level().isClientSide())
			return;
		if (!CapturedEntityBoxHelper.captureEntityFromPlayerStack(stack, player, mobTarget))
			return;

		mobTarget.discard();
	}

	@SubscribeEvent
	public static void onLivingDamage(LivingDamageEvent.Pre event) {
		if (!CBConfigs.SERVER.cardboardBox.lethalCaptureEnabled.get())
			return;

		LivingEntity target = event.getEntity();
		if (target.level().isClientSide())
			return;
		if (!(target instanceof Mob mobTarget))
			return;
		if (!canLargeBoxCapture(mobTarget))
			return;
		if (target.getHealth() > event.getNewDamage())
			return;

		Player player = getCapturingPlayer(event.getSource());
		if (player == null)
			return;

		ItemStack offhandStack = player.getOffhandItem();
		if (!CapturedEntityBoxHelper.isEmptyLargeBox(offhandStack))
			return;
		if (!CapturedEntityBoxHelper.captureEntityFromPlayerStack(offhandStack, player, target))
			return;

		event.setNewDamage(0);
		target.discard();
		CBAdvancements.awardPlayer((net.minecraft.server.level.ServerLevel) player.level(), player.getUUID(),
			CBAdvancements.LARGE_CARDBOARD_BOX);
	}

	private static Player getCapturingPlayer(DamageSource source) {
		Entity sourceEntity = source.getEntity();
		return sourceEntity instanceof Player player ? player : null;
	}

	private static boolean canLargeBoxCapture(Mob target) {
		CBConfigs.CardboardBox config = CBConfigs.SERVER.cardboardBox;
		return CBConfigs.isEntityTypeAllowed(target.getType(), config.largeBoxEntityListMode.get(),
			config.largeBoxEntityAllowlist.get(), config.largeBoxEntityDenylist.get());
	}
}
