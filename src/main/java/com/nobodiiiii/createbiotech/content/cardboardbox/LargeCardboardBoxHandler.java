package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LargeCardboardBoxHandler {

	private LargeCardboardBoxHandler() {}

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
	}

	private static Player getCapturingPlayer(DamageSource source) {
		Entity sourceEntity = source.getEntity();
		return sourceEntity instanceof Player player ? player : null;
	}
}
