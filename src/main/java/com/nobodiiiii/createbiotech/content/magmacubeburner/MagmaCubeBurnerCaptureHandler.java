package com.nobodiiiii.createbiotech.content.magmacubeburner;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagmaCubeBurnerCaptureHandler {

	private MagmaCubeBurnerCaptureHandler() {}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void captureMagmaCube(PlayerInteractEvent.EntityInteract event) {
		Player player = event.getEntity();
		ItemStack heldItem = player.getItemInHand(event.getHand());
		if (player.isSpectator() || !heldItem.is(CBItems.EMPTY_MAGMA_CUBE_BURNER.get())
			|| !(event.getTarget() instanceof MagmaCube magmaCube))
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		spawnCaptureEffects(event.getLevel(), magmaCube.position());
		if (player.level().isClientSide)
			return;

		giveFilledBurner(player, heldItem, event.getHand());
		magmaCube.discard();
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void captureFromSpawner(PlayerInteractEvent.RightClickBlock event) {
		Player player = event.getEntity();
		ItemStack heldItem = player.getItemInHand(event.getHand());
		if (player.isSpectator() || !heldItem.is(CBItems.EMPTY_MAGMA_CUBE_BURNER.get()))
			return;

		BlockPos pos = event.getPos();
		Level level = event.getLevel();
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof SpawnerBlockEntity spawnerBlockEntity)
			|| !(spawnerBlockEntity.getSpawner().getOrCreateDisplayEntity(level, level.getRandom(), pos)
				instanceof MagmaCube))
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		spawnCaptureEffects(level, VecHelper.getCenterOf(pos));
		if (!level.isClientSide)
			giveFilledBurner(player, heldItem, event.getHand());
	}

	private static void giveFilledBurner(Player player, ItemStack heldItem, InteractionHand hand) {
		ItemStack filled = CBItems.MAGMA_CUBE_BURNER.get().getDefaultInstance();
		if (!player.getAbilities().instabuild)
			heldItem.shrink(1);
		if (heldItem.isEmpty()) {
			player.setItemInHand(hand, filled);
			return;
		}
		player.getInventory().placeItemBackInInventory(filled);
	}

	private static void spawnCaptureEffects(Level level, Vec3 position) {
		if (level.isClientSide) {
			for (int i = 0; i < 40; i++) {
				Vec3 motion = VecHelper.offsetRandomly(Vec3.ZERO, level.random, .125f);
				level.addParticle(ParticleTypes.FLAME, position.x, position.y, position.z,
					motion.x, motion.y, motion.z);
				level.addParticle(ParticleTypes.SMOKE, position.x + motion.x * 4, position.y,
					position.z + motion.z * 4, 0, -.125, 0);
			}
			return;
		}

		BlockPos soundPos = BlockPos.containing(position);
		level.playSound(null, soundPos, SoundEvents.MAGMA_CUBE_HURT, SoundSource.HOSTILE, .25f, .75f);
		level.playSound(null, soundPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, .5f, .75f);
	}
}
