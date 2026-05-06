package com.nobodiiiii.createbiotech.content.powerbelt;

import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class PowerBeltSlicer {

	public static class Feedback {}

	private PowerBeltSlicer() {}

	public static InteractionResult useWrench(BlockState state, Level world, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit, Feedback feedback) {
		PowerBeltBlockEntity controllerBE = PowerBeltBlock.getControllerBE(world, pos);
		if (controllerBE == null)
			return InteractionResult.PASS;
		if (state.getValue(PowerBeltBlock.PART) == BeltPart.PULLEY && hit.getDirection()
			.getAxis() != Axis.Y)
			return InteractionResult.PASS;

		int beltLength = controllerBE.beltLength;
		if (beltLength == 2)
			return InteractionResult.FAIL;

		BlockPos beltVector = BlockPos.containing(PowerBeltBlock.getBeltVector(state));
		BeltPart part = state.getValue(PowerBeltBlock.PART);
		boolean creative = player != null && player.isCreative();

		if (hoveringEnd(state, hit)) {
			if (world.isClientSide)
				return InteractionResult.SUCCESS;

			resetChain(world, PowerBeltBlock.getBeltChain(world, controllerBE.getBlockPos()));
			BlockPos next = part == BeltPart.END ? pos.subtract(beltVector) : pos.offset(beltVector);
			BlockState nextState = world.getBlockState(next);
			if (!nextState.is(CBBlocks.POWER_BELT.get()))
				return InteractionResult.FAIL;

			KineticBlockEntity.switchToBlockState(world, next, nextState.setValue(PowerBeltBlock.PART, part));
			world.setBlock(pos, ProperWaterloggedBlock.withWater(world, Blocks.AIR.defaultBlockState(), pos),
				Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
			world.removeBlockEntity(pos);
			world.levelEvent(2001, pos, Block.getId(state));
			if (!creative && nextState.getValue(PowerBeltBlock.PART) == BeltPart.PULLEY && player != null)
				player.getInventory()
					.placeItemBackInInventory(AllBlocks.SHAFT.asStack());
			PowerBeltBlock.initBelt(world, next);
			return InteractionResult.SUCCESS;
		}

		PowerBeltBlockEntity segmentBE = PowerBeltBlock.getSegmentBE(world, pos);
		if (segmentBE == null)
			return InteractionResult.PASS;

		int hitSegment = segmentBE.index;
		Vec3 centerOf = VecHelper.getCenterOf(hit.getBlockPos());
		boolean towardPositive = hit.getLocation()
			.subtract(centerOf)
			.dot(Vec3.atLowerCornerOf(beltVector)) > 0;
		BlockPos next = !towardPositive ? pos.subtract(beltVector) : pos.offset(beltVector);

		if (hitSegment == 0 || hitSegment == 1 && !towardPositive)
			return InteractionResult.FAIL;
		if (hitSegment == beltLength - 1 || hitSegment == beltLength - 2 && towardPositive)
			return InteractionResult.FAIL;

		if (!creative) {
			int requiredShafts = 0;
			if (!segmentBE.hasPulley())
				requiredShafts++;
			BlockState other = world.getBlockState(next);
			if (other.is(CBBlocks.POWER_BELT.get()) && other.getValue(PowerBeltBlock.PART) == BeltPart.MIDDLE)
				requiredShafts++;
			if (!hasSplitMaterials(player, requiredShafts))
				return InteractionResult.FAIL;
		}
		if (world.isClientSide)
			return InteractionResult.SUCCESS;

		if (!creative)
			consumeSplitMaterials(player, segmentBE.hasPulley() ? 0 : 1,
				world.getBlockState(next)
					.is(CBBlocks.POWER_BELT.get())
					&& world.getBlockState(next)
						.getValue(PowerBeltBlock.PART) == BeltPart.MIDDLE ? 1 : 0);

		resetChain(world, PowerBeltBlock.getBeltChain(world, controllerBE.getBlockPos()));
		KineticBlockEntity.switchToBlockState(world, pos,
			state.setValue(PowerBeltBlock.PART, towardPositive ? BeltPart.END : BeltPart.START));
		KineticBlockEntity.switchToBlockState(world, next, world.getBlockState(next)
			.setValue(PowerBeltBlock.PART, towardPositive ? BeltPart.START : BeltPart.END));
		world.playSound(null, pos, SoundEvents.WOOL_HIT, player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, .5f,
			2.3f);
		PowerBeltBlock.initBelt(world, pos);
		PowerBeltBlock.initBelt(world, next);
		return InteractionResult.SUCCESS;
	}

	public static InteractionResult useConnector(BlockState state, Level world, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit, Feedback feedback) {
		PowerBeltBlockEntity controllerBE = PowerBeltBlock.getControllerBE(world, pos);
		if (controllerBE == null)
			return InteractionResult.PASS;

		int beltLength = controllerBE.beltLength;
		if (beltLength == PowerBeltConnectorItem.maxLength())
			return InteractionResult.FAIL;

		BlockPos beltVector = BlockPos.containing(PowerBeltBlock.getBeltVector(state));
		BeltPart part = state.getValue(PowerBeltBlock.PART);
		Direction facing = state.getValue(PowerBeltBlock.HORIZONTAL_FACING);
		boolean creative = player != null && player.isCreative();

		if (!hoveringEnd(state, hit))
			return InteractionResult.PASS;

		BlockPos next = part == BeltPart.START ? pos.subtract(beltVector) : pos.offset(beltVector);
		PowerBeltBlockEntity mergedController = null;
		BlockState nextState = world.getBlockState(next);

		if (!nextState.canBeReplaced()) {
			if (!nextState.is(CBBlocks.POWER_BELT.get()))
				return InteractionResult.FAIL;
			if (!beltStatesCompatible(state, nextState))
				return InteractionResult.FAIL;

			mergedController = PowerBeltBlock.getControllerBE(world, next);
			if (mergedController == null || mergedController.getController()
				.equals(controllerBE.getController()))
				return InteractionResult.FAIL;
			if (mergedController.beltLength + beltLength > PowerBeltConnectorItem.maxLength())
				return InteractionResult.FAIL;
		}

		if (world.isClientSide)
			return InteractionResult.SUCCESS;

		boolean flipMergedBelt = mergedController != null && facing != nextState.getValue(PowerBeltBlock.HORIZONTAL_FACING);
		if (flipMergedBelt)
			for (BlockPos blockPos : PowerBeltBlock.getBeltChain(world, mergedController.getBlockPos())) {
				BlockState mergedState = world.getBlockState(blockPos);
				if (mergedState.is(CBBlocks.POWER_BELT.get()))
					world.setBlock(blockPos, flipBelt(mergedState), Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
			}

		resetChain(world, PowerBeltBlock.getBeltChain(world, controllerBE.getBlockPos()));
		if (mergedController != null)
			resetChain(world, PowerBeltBlock.getBeltChain(world, mergedController.getBlockPos()));
		KineticBlockEntity.switchToBlockState(world, pos, state.setValue(PowerBeltBlock.PART, BeltPart.MIDDLE));

		if (mergedController == null) {
			world.setBlock(next, ProperWaterloggedBlock.withWater(world, state, next),
				Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
			world.playSound(null, pos, SoundEvents.WOOL_PLACE, player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS,
				.5f, 1f);
		} else {
			world.playSound(null, pos, SoundEvents.WOOL_HIT, player == null ? SoundSource.BLOCKS : SoundSource.PLAYERS, .5f,
				1.3f);
			KineticBlockEntity.switchToBlockState(world, next, state.setValue(PowerBeltBlock.PART, BeltPart.MIDDLE));
			if (!creative && player != null) {
				player.getInventory()
					.placeItemBackInInventory(AllBlocks.SHAFT.asStack(2));
				player.getInventory()
					.placeItemBackInInventory(new ItemStack(CBItems.POWER_BELT_CONNECTOR.get()));
			}
		}

		PowerBeltBlock.initBelt(world, pos);
		PowerBeltBlock.initBelt(world, next);
		return InteractionResult.SUCCESS;
	}

	private static void resetChain(Level world, Iterable<BlockPos> beltChain) {
		for (BlockPos chainPos : beltChain) {
			PowerBeltBlockEntity belt = PowerBeltBlock.getSegmentBE(world, chainPos);
			if (belt != null)
				belt.resetChainState();
		}
	}

	private static boolean hasSplitMaterials(Player player, int requiredShafts) {
		if (player == null)
			return false;
		int connectors = 0;
		int shafts = 0;
		for (int i = 0; i < player.getInventory()
			.getContainerSize(); i++) {
			ItemStack stack = player.getInventory()
				.getItem(i);
			if (stack.isEmpty())
				continue;
			if (CBItems.isPowerBeltConnector(stack))
				connectors += stack.getCount();
			if (AllBlocks.SHAFT.isIn(stack))
				shafts += stack.getCount();
		}
		return connectors >= 1 && shafts >= requiredShafts;
	}

	private static void consumeSplitMaterials(Player player, int firstSegmentShafts, int secondSegmentShafts) {
		consumeInventoryItem(player, true, 1);
		consumeInventoryItem(player, false, firstSegmentShafts + secondSegmentShafts);
	}

	private static void consumeInventoryItem(Player player, boolean connector, int amount) {
		int remaining = amount;
		for (int i = 0; i < player.getInventory()
			.getContainerSize() && remaining > 0; i++) {
			ItemStack stack = player.getInventory()
				.getItem(i);
			if (stack.isEmpty())
				continue;
			boolean matches = connector ? CBItems.isPowerBeltConnector(stack) : AllBlocks.SHAFT.isIn(stack);
			if (!matches)
				continue;
			int taken = Math.min(stack.getCount(), remaining);
			stack.shrink(taken);
			remaining -= taken;
		}
	}

	static boolean beltStatesCompatible(BlockState state, BlockState nextState) {
		Direction facing1 = state.getValue(PowerBeltBlock.HORIZONTAL_FACING);
		BeltSlope slope1 = state.getValue(PowerBeltBlock.SLOPE);
		Direction facing2 = nextState.getValue(PowerBeltBlock.HORIZONTAL_FACING);
		BeltSlope slope2 = nextState.getValue(PowerBeltBlock.SLOPE);
		return slope1 == BeltSlope.HORIZONTAL && slope2 == BeltSlope.HORIZONTAL && facing2.getAxis() == facing1.getAxis();
	}

	static BlockState flipBelt(BlockState state) {
		Direction facing = state.getValue(PowerBeltBlock.HORIZONTAL_FACING);
		BeltPart part = state.getValue(PowerBeltBlock.PART);

		if (part == BeltPart.END)
			state = state.setValue(PowerBeltBlock.PART, BeltPart.START);
		else if (part == BeltPart.START)
			state = state.setValue(PowerBeltBlock.PART, BeltPart.END);

		return state.setValue(PowerBeltBlock.HORIZONTAL_FACING, facing.getOpposite());
	}

	static boolean hoveringEnd(BlockState state, BlockHitResult hit) {
		BeltPart part = state.getValue(PowerBeltBlock.PART);
		if (part == BeltPart.MIDDLE || part == BeltPart.PULLEY)
			return false;

		Vec3 beltVector = PowerBeltBlock.getBeltVector(state);
		Vec3 centerOf = VecHelper.getCenterOf(hit.getBlockPos());
		Vec3 subtract = hit.getLocation()
			.subtract(centerOf);
		return subtract.dot(beltVector) > 0 == (part == BeltPart.END);
	}
}
