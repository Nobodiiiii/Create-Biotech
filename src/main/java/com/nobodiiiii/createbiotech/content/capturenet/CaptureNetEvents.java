package com.nobodiiiii.createbiotech.content.capturenet;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxHandler;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class CaptureNetEvents {
	private CaptureNetEvents() {}

	@SubscribeEvent
	public static void onAttack(AttackEntityEvent event) {
		Player player = event.getEntity();
		ItemStack stack = player.getMainHandItem();
		if (stack.getItem() instanceof CaptureNetItem && (player.getCooldowns().isOnCooldown(stack.getItem())
			|| CaptureNetItem.isEscapeCoolingDown(stack, player.level())))
			event.setCanceled(true);
	}

	/** Direct hits and sweep victims share the player's melee damage source. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingDamage(LivingDamageEvent.Pre event) {
		if (!(event.getEntity() instanceof Mob target) || target.level().isClientSide()
			|| !target.isAlive() || !Float.isFinite(target.getHealth())
			|| !target.getType().canSerialize() || target.getType().is(CaptureNetItem.CAPTURE_BLACKLIST)
			|| target.isPassenger() || target.isVehicle() || target.isLeashed())
			return;
		if (!event.getSource().is(DamageTypes.PLAYER_ATTACK)
			|| !(event.getSource().getEntity() instanceof ServerPlayer player)
			|| event.getSource().getDirectEntity() != player || player.isSpectator() || !player.mayBuild()
			|| !(player.getMainHandItem().getItem() instanceof CaptureNetItem))
			return;
		// Pre runs before absorption hearts are deducted from the remaining damage.
		if (!(event.getNewDamage() - target.getAbsorptionAmount() >= target.getHealth())
			|| !CBConfigs.SERVER.cardboardBox.lethalCaptureEnabled.get())
			return;

		ItemStack box = CardboardBoxHandler.isSmallMobType(target)
			? findEmptyBox(player, false) : ItemStack.EMPTY;
		if (box.isEmpty()) {
			var config = CBConfigs.SERVER.cardboardBox;
			if (CBConfigs.isEntityTypeAllowed(target.getType(), config.largeBoxEntityListMode.get(),
				config.largeBoxEntityAllowlist.get(), config.largeBoxEntityDenylist.get()))
				box = findEmptyBox(player, true);
		}
		if (box.isEmpty())
			return;
		boolean large = CapturedEntityBoxHelper.isEmptyLargeBox(box);
		if (!CapturedEntityBoxHelper.captureEntityFromPlayerStack(box, player, target))
			return;

		event.setNewDamage(0);
		target.discard();
		CBAdvancements.award(player, large ? CBAdvancements.LARGE_CARDBOARD_BOX : CBAdvancements.CARDBOARD_BOX);
	}

	private static ItemStack findEmptyBox(Player player, boolean large) {
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (large ? CapturedEntityBoxHelper.isEmptyLargeBox(stack) : CapturedEntityBoxHelper.isEmptySmallBox(stack))
				return stack;
		}
		return ItemStack.EMPTY;
	}

	@SubscribeEvent
	public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
		if (handle(event, event.getTarget())) {
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
		}
	}

	@SubscribeEvent
	public static void interact(PlayerInteractEvent.EntityInteract event) {
		if (handle(event, event.getTarget())) {
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
		}
	}

	private static boolean handle(PlayerInteractEvent event, net.minecraft.world.entity.Entity target) {
		var stack = event.getItemStack();
		if (!(stack.getItem() instanceof CaptureNetItem net) || event.getEntity().isSpectator()) return false;
		net.interact(stack, event.getEntity(), event.getHand(), target);
		return true;
	}
}
