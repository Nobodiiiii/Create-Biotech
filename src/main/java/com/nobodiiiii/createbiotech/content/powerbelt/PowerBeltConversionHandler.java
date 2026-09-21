package com.nobodiiiii.createbiotech.content.powerbelt;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.itemapplication.RecipeBackedItemApplication;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.foundation.feature.CBFeature;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public class PowerBeltConversionHandler {
	private static final ResourceLocation RECIPE_ID =
		CreateBiotech.asResource("item_application/power_belt");

	private PowerBeltConversionHandler() {}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (!CBFeature.POWER_BELT.isEnabled())
			return;
		Player player = event.getEntity();
		Level level = event.getLevel();
		BlockPos pos = event.getPos();
		BlockState state = level.getBlockState(pos);
		if (!AllBlocks.BELT.has(state))
			return;

		ItemStack heldItem = player.getItemInHand(event.getHand());
		ItemStack processedItem = state.getCloneItemStack(event.getHitVec(), level, pos, player);
		var recipe = RecipeBackedItemApplication.find(level, RECIPE_ID, state, processedItem, heldItem);
		if (recipe.isEmpty())
			return;

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.FAIL);
		if (player.isShiftKeyDown() || !player.mayBuild())
			return;
		var resultBlock = RecipeBackedItemApplication.resultBlock(recipe.get());
		if (resultBlock.isEmpty() || !(resultBlock.get() instanceof PowerBeltBlock powerBeltBlock))
			return;
		if (state.getValue(BeltBlock.SLOPE) != BeltSlope.HORIZONTAL)
			return;

		if (level.isClientSide) {
			event.setCancellationResult(InteractionResult.SUCCESS);
			return;
		}

		boolean converted = convert(level, pos, player, event.getHand(), heldItem, recipe.get(), powerBeltBlock);
		event.setCancellationResult(converted ? InteractionResult.SUCCESS : InteractionResult.FAIL);
	}

	private static boolean convert(Level level, BlockPos clickedPos, Player player,
		InteractionHand hand, ItemStack heldItem, ManualApplicationRecipe recipe,
		PowerBeltBlock powerBeltBlock) {
		BlockPos controllerPos = findController(level, clickedPos);
		if (controllerPos == null)
			return false;

		List<BlockPos> beltChain = BeltBlock.getBeltChain(level, controllerPos);
		if (beltChain.size() < 2)
			return false;
		for (BlockPos beltPos : beltChain)
			if (!level.isLoaded(beltPos) || !SubLevelCompat.sameSpace(level, controllerPos, beltPos)
				|| !isHorizontalBelt(level.getBlockState(beltPos)))
				return false;

		BeltBlockEntity controllerBE = BeltHelper.getSegmentBE(level, controllerPos);
		if (controllerBE != null && controllerBE.isController() && controllerBE.getInventory() != null)
			controllerBE.getInventory()
				.ejectAll();

		for (BlockPos beltPos : beltChain) {
			BeltBlockEntity belt = BeltHelper.getSegmentBE(level, beltPos);
			if (belt == null)
				continue;
			belt.detachKinetics();
			belt.invalidateItemHandler();
			belt.beltLength = 0;
		}

		for (BlockPos beltPos : beltChain) {
			BlockState oldState = level.getBlockState(beltPos);
			BlockState newState = powerBeltBlock
				.defaultBlockState()
				.setValue(PowerBeltBlock.SLOPE, oldState.getValue(BeltBlock.SLOPE))
				.setValue(PowerBeltBlock.PART, oldState.getValue(BeltBlock.PART))
				.setValue(PowerBeltBlock.HORIZONTAL_FACING, oldState.getValue(BeltBlock.HORIZONTAL_FACING))
				.setValue(PowerBeltBlock.CASING, false);

			newState = ProperWaterloggedBlock.withWater(level, newState, beltPos);
			level.setBlock(beltPos, newState, Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
			level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, beltPos, Block.getId(newState));
		}

		PowerBeltBlock.initBelt(level, controllerPos);
		level.playSound(null, clickedPos, SoundEvents.WOOL_PLACE,
			player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, 0.5F, 1F);
		RecipeBackedItemApplication.consumeHeldItem(recipe, player, hand, heldItem);
		if (player instanceof ServerPlayer serverPlayer)
			CBAdvancements.award(serverPlayer, CBAdvancements.POWER_BELT);
		return true;
	}

	private static BlockPos findController(Level level, BlockPos pos) {
		BlockPos currentPos = pos;
		int limit = 1000;
		while (limit-- > 0) {
			if (!level.isLoaded(currentPos) || !SubLevelCompat.sameSpace(level, pos, currentPos))
				return null;
			BlockState currentState = level.getBlockState(currentPos);
			if (!isHorizontalBelt(currentState))
				return null;

			BlockPos nextSegmentPosition = BeltBlock.nextSegmentPosition(currentState, currentPos, false);
			if (nextSegmentPosition == null)
				return currentPos;
			if (!SubLevelCompat.sameSpace(level, pos, nextSegmentPosition))
				return null;
			currentPos = nextSegmentPosition;
		}
		return null;
	}

	private static boolean isHorizontalBelt(BlockState state) {
		return AllBlocks.BELT.has(state) && state.getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL;
	}
}
