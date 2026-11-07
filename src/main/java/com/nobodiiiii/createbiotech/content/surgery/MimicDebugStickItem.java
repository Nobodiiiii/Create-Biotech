package com.nobodiiiii.createbiotech.content.surgery;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicHandler;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Command-only inspection tool. Intercepts both interaction routes before equipment or mob actions. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class MimicDebugStickItem extends Item {

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String MESSAGE_PREFIX = "item.create_biotech.mimic_debug_stick.";

	public MimicDebugStickItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
		InteractionHand hand) {
		return inspect(player, target);
	}

	private InteractionResult inspect(Player player, Entity target) {
		if (player.isSpectator())
			return InteractionResult.PASS;
		if (target instanceof PartEntity<?> part)
			target = part.getParent();
		if (player.level().isClientSide)
			return InteractionResult.SUCCESS;
		if (player.getCooldowns().isOnCooldown(this))
			return InteractionResult.CONSUME;
		// Also bounds repeated clicks and duplicate interaction packets to one report per second.
		player.getCooldowns().addCooldown(this, 20);
		if (!(target instanceof LivingEntity living) || !(living instanceof SlimeBionicEntity
			|| SlimeMimicHandler.isSlimeMimic(living) || SlimeMimicHandler.isHauntedMimic(living))) {
			player.displayClientMessage(Component.translatable(MESSAGE_PREFIX + "invalid_target")
				.withStyle(ChatFormatting.RED), true);
			return InteractionResult.CONSUME;
		}
		if (!(living.level() instanceof ServerLevel level))
			return InteractionResult.CONSUME;
		try {
			Path report = MimicDebugReport.write(level, living);
			Component location = Component.literal(report.toString()).withStyle(style -> style
				.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, report.toString()))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
					Component.translatable(MESSAGE_PREFIX + "copy_path"))));
			player.displayClientMessage(Component.translatable(MESSAGE_PREFIX + "saved", location), false);
			LOGGER.info("Exported mimic diagnostic report for {} to {}", living.getUUID(), report);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Could not export mimic diagnostic report for {}", living.getUUID(), exception);
			player.displayClientMessage(Component.translatable(MESSAGE_PREFIX + "failed")
				.withStyle(ChatFormatting.RED), false);
		}
		return InteractionResult.CONSUME;
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
		if (event.getItemStack().getItem() instanceof MimicDebugStickItem stick && !event.getEntity().isSpectator()) {
			event.setCancellationResult(stick.inspect(event.getEntity(), event.getTarget()));
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (event.getItemStack().getItem() instanceof MimicDebugStickItem stick && !event.getEntity().isSpectator()) {
			event.setCancellationResult(stick.inspect(event.getEntity(), event.getTarget()));
			event.setCanceled(true);
		}
	}
}
