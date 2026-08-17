package com.nobodiiiii.createbiotech.content.sonicdogcannon;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBMobEffects;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SonicDogCannonStunHandler {

	private SonicDogCannonStunHandler() {}

	@SubscribeEvent
	public static void onPlayerAttack(AttackEntityEvent event) {
		if (isStunned(event.getEntity()))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
		LivingEntity entity = event.getEntity();
		if (isStunned(entity))
			entity.setDeltaMovement(entity.getDeltaMovement().x(), 0.0d, entity.getDeltaMovement().z());
	}

	@SubscribeEvent
	public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
		if (isStunned(event.getEntity()))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onUseItem(LivingEntityUseItemEvent event) {
		if (isStunned(event.getEntity()))
			event.setDuration(0);
	}

	@SubscribeEvent
	public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
		Entity entity = event.getEntity();
		if (entity instanceof LivingEntity living && isStunned(living))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onBreakBlock(BlockEvent.BreakEvent event) {
		if (isStunned(event.getPlayer()))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (isStunned(event.getEntity()))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
		if (isStunned(event.getEntity()))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (isStunned(event.getEntity()))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		if (isStunned(event.getEntity()))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
		if (isStunned(event.getEntity()))
			event.setCanceled(true);
	}

	private static boolean isStunned(LivingEntity entity) {
		return entity.hasEffect(CBMobEffects.STUN.get());
	}
}
