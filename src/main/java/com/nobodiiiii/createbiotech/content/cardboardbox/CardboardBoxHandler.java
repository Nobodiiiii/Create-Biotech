package com.nobodiiiii.createbiotech.content.cardboardbox;

import net.minecraft.core.registries.BuiltInRegistries;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;


@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class CardboardBoxHandler {

	private CardboardBoxHandler() {}

	@SubscribeEvent
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		Player player = event.getEntity();
		InteractionHand hand = event.getHand();
		ItemStack stack = player.getItemInHand(hand);

		if (!CapturedEntityBoxHelper.isEmptySmallBox(stack))
			return;
		if (player.isShiftKeyDown())
			return;
		LivingEntity livingTarget = CapturedEntityBoxHelper.resolveLivingTarget(event.getTarget());
		if (livingTarget == null)
			return;
		if (!isSmallMob(livingTarget))
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);

		if (player.level().isClientSide())
			return;

		if (!CapturedEntityBoxHelper.captureEntityFromPlayerStack(stack, player, livingTarget))
			return;
		livingTarget.discard();
		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
			CBAdvancements.award(serverPlayer, CBAdvancements.CARDBOARD_BOX);
	}

	private static boolean isSmallMob(LivingEntity target) {
		return isSmallMobType(target);
	}

	public static boolean isSmallMobType(LivingEntity target) {
		EntityType<?> type = target.getType();
		if (target instanceof Slime slime && slime.getSize() > 1) return false;
		if (target instanceof MagmaCube magmaCube && magmaCube.getSize() > 1) return false;
		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return id != null && CBConfigs.containsResourceLocation(
			CBConfigs.SERVER.cardboardBox.smallBoxEntityAllowlist.get(), id);
	}
}
