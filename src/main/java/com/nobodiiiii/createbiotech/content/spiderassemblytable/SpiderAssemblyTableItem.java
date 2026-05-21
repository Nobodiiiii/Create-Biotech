package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import java.util.function.Consumer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public class SpiderAssemblyTableItem extends BlockItem {

	public SpiderAssemblyTableItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void initializeClient(Consumer<IClientItemExtensions> consumer) {
		consumer.accept(SimpleCustomRenderer.create(this, new SpiderAssemblyTableItemRenderer()));
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		BlockPos placedOnPos = context.getClickedPos()
			.relative(context.getClickedFace().getOpposite());
		Level level = context.getLevel();
		BlockState placedOnState = level.getBlockState(placedOnPos);

		if (context.getClickedFace() == Direction.UP && operatesOn(level, placedOnPos, placedOnState)) {
			BlockPos mainPos = placedOnPos.above(2);
			if (!level.getBlockState(mainPos).canBeReplaced())
				return InteractionResult.FAIL;
			return placeAtMainPos(context, mainPos);
		}

		BlockPos tailPos = context.getClickedPos();
		return placeAnchoredAtTail(context, tailPos);
	}

	protected boolean operatesOn(LevelReader world, BlockPos pos, BlockState placedOnState) {
		if (AllBlocks.BELT.has(placedOnState))
			return placedOnState.getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL;
		return BasinBlock.isBasin(world, pos) || AllBlocks.DEPOT.has(placedOnState)
			|| AllBlocks.WEIGHTED_EJECTOR.has(placedOnState);
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
