package com.nobodiiiii.createbiotech.content.processing.basin;

import com.nobodiiiii.createbiotech.CreateBiotech;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BasinEntityProcessingHandler {
	private static final int CHECK_INTERVAL = 10;

	private BasinEntityProcessingHandler() {}

	@SubscribeEvent
	public static void onLivingTick(LivingEvent.LivingTickEvent event) {
		if (event.getEntity() instanceof Slime slime && slime.tickCount % CHECK_INTERVAL == 0)
			BasinEntityProcessing.tickCapturedSmallSlime(slime);
	}

	@SubscribeEvent
	public static void onLivingDrops(LivingDropsEvent event) {
		if (event.getEntity() instanceof Slime slime)
			BasinEntityProcessing.releaseCapturedSmallSlime(slime);
	}

	@SubscribeEvent
	public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
		if (event.getLevel()
			.isClientSide())
			return;
		if (!(event.getEntity() instanceof Slime slime) || !BasinEntityProcessing.isCapturedSmallSlime(slime))
			return;

		Entity.RemovalReason reason = slime.getRemovalReason();
		if (reason == null || !reason.shouldDestroy())
			return;

		BasinEntityProcessing.onCapturedSmallSlimeRemoved(slime);
	}
}
