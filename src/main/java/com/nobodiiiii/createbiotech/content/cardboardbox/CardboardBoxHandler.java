package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;

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

import java.util.Set;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CardboardBoxHandler {

	private static final Set<EntityType<?>> SMALL_MOBS = Set.of(
		EntityType.SLIME,
		EntityType.CAT,
		EntityType.BAT,
		EntityType.CHICKEN,
		EntityType.RABBIT,
		EntityType.SILVERFISH,
		EntityType.ENDERMITE,
		EntityType.BEE,
		EntityType.PARROT,
		EntityType.ALLAY,
		EntityType.FROG,
		EntityType.OCELOT,
		EntityType.VEX,
		EntityType.MAGMA_CUBE
	);

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
	}

	private static boolean isSmallMob(LivingEntity target) {
		EntityType<?> type = target.getType();
		if (target instanceof Slime slime && slime.getSize() > 1) return false;
		if (target instanceof MagmaCube magmaCube && magmaCube.getSize() > 1) return false;
		return SMALL_MOBS.contains(type);
	}
}
