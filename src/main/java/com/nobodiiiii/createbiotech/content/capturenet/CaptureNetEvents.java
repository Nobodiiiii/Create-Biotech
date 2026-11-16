package com.nobodiiiii.createbiotech.content.capturenet;

import java.util.Map;
import java.util.WeakHashMap;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxHandler;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class CaptureNetEvents {
	private static final Map<Player, PendingCapture> PENDING_CAPTURES = new WeakHashMap<>();

	private CaptureNetEvents() {}

	static void queueCapture(Player player, InteractionHand hand, ItemStack stack, Mob target, long startedAt) {
		PENDING_CAPTURES.put(player, new PendingCapture(hand, stack, target, startedAt));
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) return;
		PendingCapture pending = PENDING_CAPTURES.get(player);
		if (pending == null) return;
		long elapsed = player.server.overworld().getGameTime() - pending.startedAt();
		if (!player.isAlive() || player.isSpectator() || player.getItemInHand(pending.hand()) != pending.stack()
			|| pending.target().level() != player.level() || !pending.target().isAlive()
			|| elapsed < 0 || elapsed >= CaptureNetItem.ANIMATION_TICKS) {
			PENDING_CAPTURES.remove(player);
			return;
		}
		if (elapsed < CaptureNetItem.CAPTURE_CONTACT_TICK) return;
		PENDING_CAPTURES.remove(player);
		if (pending.stack().getItem() instanceof CaptureNetItem net)
			net.completeCapture(pending.stack(), player, pending.target());
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			PENDING_CAPTURES.remove(player);
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		PENDING_CAPTURES.clear();
	}

	private record PendingCapture(InteractionHand hand, ItemStack stack, Mob target, long startedAt) {}

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
			|| !target.getType().canSerialize() || target.getType().is(CaptureNetItem.CAPTURE_BLACKLIST))
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
		InteractionResult result = handle(event, event.getTarget());
		if (result != InteractionResult.PASS) {
			event.setCanceled(true);
			event.setCancellationResult(result);
		}
	}

	@SubscribeEvent
	public static void interact(PlayerInteractEvent.EntityInteract event) {
		InteractionResult result = handle(event, event.getTarget());
		if (result != InteractionResult.PASS) {
			event.setCanceled(true);
			event.setCancellationResult(result);
		}
	}

	private static InteractionResult handle(PlayerInteractEvent event, net.minecraft.world.entity.Entity target) {
		var stack = event.getItemStack();
		if (!(stack.getItem() instanceof CaptureNetItem net) || event.getEntity().isSpectator())
			return InteractionResult.PASS;
		net.interact(stack, event.getEntity(), event.getHand(), target);
		return InteractionResult.sidedSuccess(event.getLevel().isClientSide());
	}
}
