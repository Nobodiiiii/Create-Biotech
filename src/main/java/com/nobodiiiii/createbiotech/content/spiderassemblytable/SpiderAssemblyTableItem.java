package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class SpiderAssemblyTableItem extends BlockItem {

	public SpiderAssemblyTableItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		BlockPos placedOnPos = context.getClickedPos()
			.relative(context.getClickedFace().getOpposite());
		Level level = context.getLevel();
		BlockState placedOnState = level.getBlockState(placedOnPos);

		if (context.getClickedFace() == Direction.UP
			&& (AllBlocks.DEPOT.has(placedOnState) || AllBlocks.WEIGHTED_EJECTOR.has(placedOnState))) {
			BlockPos mainPos = placedOnPos.above(2);
			return placeAtMainPos(context, mainPos);
		}

		BlockPos tailPos = context.getClickedPos();
		return placeAnchoredAtTail(context, tailPos);
	}

	private InteractionResult placeAnchoredAtTail(BlockPlaceContext context, BlockPos tailPos) {
		SpiderAssemblyTableBlock block = (SpiderAssemblyTableBlock) getBlock();
		Direction facing = block.getPlacementFacing(context);

		for (int i = 0; i < 2; i++) {
			BlockPos mainPos = tailPos.relative(facing);
			BlockPlaceContext shifted = BlockPlaceContext.at(context, mainPos, context.getClickedFace());
			Direction recalculated = block.getPlacementFacing(shifted);
			if (recalculated == facing)
				return placeAtMainPos(context, mainPos, facing);
			facing = recalculated;
		}

		return placeAtMainPos(context, tailPos.relative(facing), facing);
	}

	private InteractionResult placeAtMainPos(BlockPlaceContext context, BlockPos mainPos) {
		SpiderAssemblyTableBlock block = (SpiderAssemblyTableBlock) getBlock();
		BlockPlaceContext shifted = BlockPlaceContext.at(context, mainPos, context.getClickedFace());
		return placeAtMainPos(context, mainPos, block.getPlacementFacing(shifted));
	}

	private InteractionResult placeAtMainPos(BlockPlaceContext context, BlockPos mainPos, Direction facing) {
		BlockPlaceContext shifted = BlockPlaceContext.at(context, mainPos, context.getClickedFace());
		SpiderAssemblyTableBlock.setForcedPlacementFacing(facing);
		try {
			return super.place(shifted);
		} finally {
			SpiderAssemblyTableBlock.clearForcedPlacementFacing();
		}
	}
}
