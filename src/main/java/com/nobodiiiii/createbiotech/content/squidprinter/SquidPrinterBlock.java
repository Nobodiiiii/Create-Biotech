package com.nobodiiiii.createbiotech.content.squidprinter;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SquidPrinterBlock extends Block implements IWrenchable, IBE<SquidPrinterBlockEntity> {

	public SquidPrinterBlock(Properties properties) {
		super(properties);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return AllShapes.SPOUT;
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter reader, BlockPos pos, PathComputationType type) {
		return false;
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState blockState, Level worldIn, BlockPos pos) {
		return getBlockEntityOptional(worldIn, pos)
			.map(SquidPrinterBlockEntity::getComparatorOutput)
			.orElse(0);
	}

	@Override
	public Class<SquidPrinterBlockEntity> getBlockEntityClass() {
		return SquidPrinterBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SquidPrinterBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.SQUID_PRINTER.get();
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		IBE.onRemove(state, level, pos, newState);
	}
}
