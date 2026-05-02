package com.nobodiiiii.createbiotech.content.slimebelt;

import java.util.List;

import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltPart;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class SlimeBeltSlicer {

	public static class Feedback {}

	private SlimeBeltSlicer() {}

	public static InteractionResult useWrench(BlockState state, Level world, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit, Feedback feedback) {
		SlimeBeltBlockEntity controllerBE = SlimeBeltHelper.getControllerBE(world, pos);
		if (controllerBE == null)
			return InteractionResult.PASS;
		if (state.getValue(SlimeBeltBlock.PART) == BeltPart.PULLEY && hit.getDirection().getAxis() != Axis.Y)
			return InteractionResult.PASS;

		BlockPos beltVector = BlockPos.containing(SlimeBeltHelper.getBeltVector(state));
		BeltPart part = state.getValue(SlimeBeltBlock.PART);
		List<BlockPos> beltChain = SlimeBeltBlock.getBeltChain(world, controllerBE.getBlockPos());

		if (hoveringEnd(state, hit)) {
			if (controllerBE.beltLength <= 2)
				return InteractionResult.FAIL;
			if (world.isClientSide)
				return InteractionResult.SUCCESS;

			controllerBE.getInventory().ejectAll();
			resetChain(world, beltChain);

			BlockPos next = part == BeltPart.END ? pos.subtract(beltVector) : pos.offset(beltVector);
			BlockState nextState = world.getBlockState(next);
			if (!nextState.is(CBBlocks.SLIME_BELT.get()))
				return InteractionResult.FAIL;

			KineticBlockEntity.switchToBlockState(world, next, nextState.setValue(SlimeBeltBlock.PART, part));
			world.setBlock(pos, ProperWaterloggedBlock.withWater(world, Blocks.AIR.defaultBlockState(), pos),
				Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
			world.removeBlockEntity(pos);
			world.levelEvent(2001, pos, Block.getId(state));
			SlimeBeltBlock.initBelt(world, next);
			return InteractionResult.SUCCESS;
		}

		int hitSegment = SlimeBeltHelper.getSegmentBE(world, pos).index;
		Vec3 centerOf = VecHelper.getCenterOf(hit.getBlockPos());
		boolean towardPositive = hit.getLocation().subtract(centerOf).dot(Vec3.atLowerCornerOf(beltVector)) > 0;
		BlockPos next = !towardPositive ? pos.subtract(beltVector) : pos.offset(beltVector);

		if (hitSegment == 0 || hitSegment == 1 && !towardPositive)
			return InteractionResult.FAIL;
		if (hitSegment == controllerBE.beltLength - 1 || hitSegment == controllerBE.beltLength - 2 && towardPositive)
			return InteractionResult.FAIL;
		if (world.isClientSide)
			return InteractionResult.SUCCESS;

		controllerBE.getInventory().ejectAll();
		resetChain(world, beltChain);
		KineticBlockEntity.switchToBlockState(world, pos,
			state.setValue(SlimeBeltBlock.PART, towardPositive ? BeltPart.END : BeltPart.START));
		KineticBlockEntity.switchToBlockState(world, next, world.getBlockState(next)
			.setValue(SlimeBeltBlock.PART, towardPositive ? BeltPart.START : BeltPart.END));
		world.playSound(null, pos, SoundEvents.WOOL_HIT, SoundSource.PLAYERS, 0.5F, 2.3F);
		SlimeBeltBlock.initBelt(world, pos);
		SlimeBeltBlock.initBelt(world, next);
		return InteractionResult.SUCCESS;
	}

	public static InteractionResult useConnector(BlockState state, Level world, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit, Feedback feedback) {
		SlimeBeltBlockEntity controllerBE = SlimeBeltHelper.getControllerBE(world, pos);
		if (controllerBE == null || !hoveringEnd(state, hit))
			return InteractionResult.PASS;
		if (controllerBE.beltLength == SlimeBeltConnectorItem.maxLength())
			return InteractionResult.FAIL;

		BlockPos beltVector = BlockPos.containing(SlimeBeltHelper.getBeltVector(state));
		BeltPart part = state.getValue(SlimeBeltBlock.PART);
		BlockPos next = part == BeltPart.START ? pos.subtract(beltVector) : pos.offset(beltVector);
		if (!world.getBlockState(next).canBeReplaced())
			return InteractionResult.FAIL;
		if (world.isClientSide)
			return InteractionResult.SUCCESS;

		controllerBE.getInventory().ejectAll();
		resetChain(world, SlimeBeltBlock.getBeltChain(world, controllerBE.getBlockPos()));
		KineticBlockEntity.switchToBlockState(world, pos, state.setValue(SlimeBeltBlock.PART, BeltPart.MIDDLE));
		world.setBlock(next, ProperWaterloggedBlock.withWater(world, state, next),
			Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
		world.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.5F, 1F);
		SlimeBeltBlock.initBelt(world, next);
		return InteractionResult.SUCCESS;
	}

	private static void resetChain(Level world, List<BlockPos> beltChain) {
		for (BlockPos chainPos : beltChain) {
			SlimeBeltBlockEntity belt = SlimeBeltHelper.getSegmentBE(world, chainPos);
			if (belt == null)
				continue;
			belt.detachKinetics();
			belt.invalidateItemHandlers();
			belt.beltLength = 0;
		}
	}

	static boolean hoveringEnd(BlockState state, BlockHitResult hit) {
		BeltPart part = state.getValue(SlimeBeltBlock.PART);
		if (part == BeltPart.MIDDLE || part == BeltPart.PULLEY)
			return false;

		Vec3 beltVector = SlimeBeltHelper.getBeltVector(state);
		Vec3 centerOf = VecHelper.getCenterOf(hit.getBlockPos());
		Vec3 subtract = hit.getLocation().subtract(centerOf);
		return subtract.dot(beltVector) > 0 == (part == BeltPart.END);
	}
}
