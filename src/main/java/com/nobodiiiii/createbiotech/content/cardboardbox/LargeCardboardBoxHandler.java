package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.capturenet.CaptureNetItem;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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

	static boolean tryLethalCapture(LivingDamageEvent.Pre event, Mob target) {
		DamageSource source = event.getSource();
		if (!(source.getEntity() instanceof Player player))
			return false;
		// Capture nets select a suitable box from the player's entire inventory.
		if (source.is(DamageTypes.PLAYER_ATTACK)
			&& player.getMainHandItem().getItem() instanceof CaptureNetItem)
			return false;

		ItemStack offhandStack = player.getOffhandItem();
		if (!CapturedEntityBoxHelper.isEmptyLargeBox(offhandStack) || !canLargeBoxCapture(target))
			return false;
		if (!CapturedEntityBoxHelper.captureEntityFromPlayerStack(offhandStack, player, target))
			return false;

		CardboardBoxHandler.finishLethalCapture(event);
		CBAdvancements.awardPlayer((ServerLevel) player.level(), player.getUUID(), CBAdvancements.LARGE_CARDBOARD_BOX);
		return true;
	}

	public static boolean canLargeBoxCapture(Mob target) {
		CBConfigs.CardboardBox config = CBConfigs.SERVER.cardboardBox;
		return CBConfigs.isEntityTypeAllowed(target.getType(), config.largeBoxEntityListMode.get(),
			config.largeBoxEntityAllowlist.get(), config.largeBoxEntityDenylist.get());
	}
}
