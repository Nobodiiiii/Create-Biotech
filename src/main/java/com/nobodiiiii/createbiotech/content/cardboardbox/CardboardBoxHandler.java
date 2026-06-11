package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CardboardBoxHandler {

	private CardboardBoxHandler() {}

	@SubscribeEvent
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		Player player = event.getEntity();
		InteractionHand hand = event.getHand();
		ItemStack stack = player.getItemInHand(hand);

		if (!stack.is(CBItems.CARDBOARD_BOX.get()))
			return;
		if (player.isShiftKeyDown())
			return;
		if (CardboardBoxItem.hasCapturedEntity(stack))
			return;

		Entity target = event.getTarget();
		if (!(target instanceof LivingEntity livingTarget))
			return;
		if (!isSmallMob(livingTarget))
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);

		if (player.level().isClientSide())
			return;

		if (!CapturedEntityBoxHelper.captureEntity(stack, livingTarget))
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
		ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
		return id != null && CBConfigs.containsResourceLocation(
			CBConfigs.SERVER.cardboardBox.smallBoxEntityAllowlist.get(), id);
	}
}
