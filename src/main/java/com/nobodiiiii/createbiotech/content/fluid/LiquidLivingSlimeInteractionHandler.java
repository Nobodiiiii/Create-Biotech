package com.nobodiiiii.createbiotech.content.fluid;

import java.util.HashMap;
import java.util.Map;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LiquidLivingSlimeInteractionHandler {

	private static final int SOURCE_HITS_TO_BREAK = 4;
	private static final String WAS_TOUCHING_LIQUID_LIVING_SLIME_KEY =
		CreateBiotech.MOD_ID + ".was_touching_liquid_living_slime";
	private static final String PREVIOUS_VERTICAL_SPEED_KEY =
		CreateBiotech.MOD_ID + ".previous_liquid_living_slime_vertical_speed";
	private static final double LANDING_VERTICAL_SPEED_THRESHOLD = -0.16D;
	private static final Map<SourceKey, Integer> SOURCE_HIT_COUNTS = new HashMap<>();

	private LiquidLivingSlimeInteractionHandler() {}

	@SubscribeEvent
	public static void onLivingTick(LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();
		boolean wasTouchingLiquidLivingSlime =
			entity.getPersistentData().getBoolean(WAS_TOUCHING_LIQUID_LIVING_SLIME_KEY);
		double previousVerticalSpeed = entity.getPersistentData().getDouble(PREVIOUS_VERTICAL_SPEED_KEY);
		boolean touchingLiquidLivingSlime =
			entity.getFluidTypeHeight(CBFluids.LIQUID_LIVING_SLIME_TYPE.get()) > 0.0D;

		if (!entity.level().isClientSide && touchingLiquidLivingSlime && !wasTouchingLiquidLivingSlime
			&& previousVerticalSpeed < LANDING_VERTICAL_SPEED_THRESHOLD) {
			playLandingSound(entity, previousVerticalSpeed);
		}

		entity.getPersistentData().putBoolean(WAS_TOUCHING_LIQUID_LIVING_SLIME_KEY, touchingLiquidLivingSlime);
		entity.getPersistentData().putDouble(PREVIOUS_VERTICAL_SPEED_KEY, entity.getDeltaMovement().y);
	}

	@SubscribeEvent
	public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.ABORT)
			return;

		Level level = event.getLevel();
		BlockPos pos = event.getPos();
		BlockState state = level.getBlockState(pos);
		FluidState fluidState = state.getFluidState();
		if (!isLiquidLivingSlime(fluidState))
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);

		if (level.isClientSide || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START)
			return;

		handleServerHit((ServerLevel) level, pos, fluidState, event.getEntity());
	}

	private static void handleServerHit(ServerLevel level, BlockPos pos, FluidState fluidState, Player player) {
		playHitSounds(level, pos);

		if (!fluidState.isSource()) {
			clearFluid(level, pos);
			return;
		}

		SourceKey key = new SourceKey(level.dimension(), pos.immutable());
		int hits = SOURCE_HIT_COUNTS.getOrDefault(key, 0) + 1;
		if (hits < SOURCE_HITS_TO_BREAK) {
			SOURCE_HIT_COUNTS.put(key, hits);
			level.destroyBlockProgress(progressIdFor(pos), pos, hitToProgressStage(hits));
			return;
		}

		SOURCE_HIT_COUNTS.remove(key);
		level.destroyBlockProgress(progressIdFor(pos), pos, -1);
		clearFluid(level, pos);
		Block.popResource(level, pos, new ItemStack(Items.SLIME_BALL));
	}

	private static void clearFluid(ServerLevel level, BlockPos pos) {
		SourceKey key = new SourceKey(level.dimension(), pos.immutable());
		SOURCE_HIT_COUNTS.remove(key);
		level.destroyBlockProgress(progressIdFor(pos), pos, -1);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	private static void playHitSounds(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.SLIME_HURT_SMALL, SoundSource.BLOCKS, 0.8F, 0.9F);
		level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.BLOCKS, 0.6F, 0.95F);
	}

	private static void playLandingSound(LivingEntity entity, double previousVerticalSpeed) {
		Vec3 position = entity.position();
		float volume = (float) Mth.clamp(-previousVerticalSpeed * 0.75D, 0.35D, 1.0D);
		float pitch = 1.0F + (entity.level().random.nextFloat() - entity.level().random.nextFloat()) * 0.1F;
		entity.level().playSound(null, position.x, position.y, position.z, SoundEvents.SLIME_BLOCK_FALL,
			entity.getSoundSource(), volume, pitch);
	}

	private static boolean isLiquidLivingSlime(FluidState fluidState) {
		return !fluidState.isEmpty() && fluidState.getFluidType() == CBFluids.LIQUID_LIVING_SLIME_TYPE.get();
	}

	private static int hitToProgressStage(int hits) {
		return switch (hits) {
		case 1 -> 2;
		case 2 -> 5;
		default -> 8;
		};
	}

	private static int progressIdFor(BlockPos pos) {
		return pos.hashCode();
	}

	private record SourceKey(ResourceKey<Level> dimension, BlockPos pos) {}
}
