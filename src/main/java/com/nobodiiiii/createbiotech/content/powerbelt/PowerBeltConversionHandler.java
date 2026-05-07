package com.nobodiiiii.createbiotech.content.powerbelt;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateBiotech.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PowerBeltConversionHandler {

	private PowerBeltConversionHandler() {}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		Player player = event.getEntity();
		if (player.isShiftKeyDown() || !player.mayBuild())
			return;

		Level level = event.getLevel();
		BlockPos pos = event.getPos();
		BlockState state = level.getBlockState(pos);
		if (!AllBlocks.BELT.has(state))
			return;

		ItemStack heldItem = player.getItemInHand(event.getHand());
		if (!AllItems.ANDESITE_ALLOY.isIn(heldItem))
			return;
		if (state.getValue(BeltBlock.SLOPE) != BeltSlope.HORIZONTAL)
			return;

		event.setCanceled(true);
		if (level.isClientSide) {
			event.setCancellationResult(InteractionResult.SUCCESS);
			return;
		}

		boolean converted = convert(level, pos, player, heldItem);
		event.setCancellationResult(converted ? InteractionResult.SUCCESS : InteractionResult.FAIL);
	}

	private static boolean convert(Level level, BlockPos clickedPos, Player player, ItemStack heldItem) {
		BlockPos controllerPos = findController(level, clickedPos);
		if (controllerPos == null)
			return false;

		List<BlockPos> beltChain = BeltBlock.getBeltChain(level, controllerPos);
		if (beltChain.size() < 2)
			return false;
		for (BlockPos beltPos : beltChain)
			if (!level.isLoaded(beltPos) || !isHorizontalBelt(level.getBlockState(beltPos)))
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
			BlockState newState = CBBlocks.POWER_BELT.get()
				.defaultBlockState()
				.setValue(PowerBeltBlock.SLOPE, oldState.getValue(BeltBlock.SLOPE))
				.setValue(PowerBeltBlock.PART, oldState.getValue(BeltBlock.PART))
				.setValue(PowerBeltBlock.HORIZONTAL_FACING, oldState.getValue(BeltBlock.HORIZONTAL_FACING))
				.setValue(PowerBeltBlock.CASING, false);

			newState = ProperWaterloggedBlock.withWater(level, newState, beltPos);
			level.setBlock(beltPos, newState, Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
			level.levelEvent(2001, beltPos, Block.getId(newState));
		}

		PowerBeltBlock.initBelt(level, controllerPos);
		level.playSound(null, clickedPos, SoundEvents.WOOL_PLACE,
			player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, 0.5F, 1F);
		if (!player.isCreative())
			heldItem.shrink(1);
		return true;
	}

	private static BlockPos findController(Level level, BlockPos pos) {
		BlockPos currentPos = pos;
		int limit = 1000;
		while (limit-- > 0) {
			if (!level.isLoaded(currentPos))
				return null;
			BlockState currentState = level.getBlockState(currentPos);
			if (!isHorizontalBelt(currentState))
				return null;

			BlockPos nextSegmentPosition = BeltBlock.nextSegmentPosition(currentState, currentPos, false);
			if (nextSegmentPosition == null)
				return currentPos;
			currentPos = nextSegmentPosition;
		}
		return null;
	}

	private static boolean isHorizontalBelt(BlockState state) {
		return AllBlocks.BELT.has(state) && state.getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL;
	}
}
