package com.nobodiiiii.createbiotech.content.experience;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ExperienceClusterBlock extends Block implements ProperWaterloggedBlock {
	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	private final int xpNuggetValue;
	private final VoxelShape northShape;
	private final VoxelShape southShape;
	private final VoxelShape eastShape;
	private final VoxelShape westShape;
	private final VoxelShape upShape;
	private final VoxelShape downShape;

	public ExperienceClusterBlock(int height, int xzOffset, int xpNuggetValue, Properties properties) {
		super(properties);
		this.xpNuggetValue = xpNuggetValue;
		this.upShape = Block.box(xzOffset, 0.0, xzOffset, 16 - xzOffset, height, 16 - xzOffset);
		this.downShape = Block.box(xzOffset, 16 - height, xzOffset, 16 - xzOffset, 16, 16 - xzOffset);
		this.northShape = Block.box(xzOffset, xzOffset, 16 - height, 16 - xzOffset, 16 - xzOffset, 16);
		this.southShape = Block.box(xzOffset, xzOffset, 0, 16 - xzOffset, 16 - xzOffset, height);
		this.eastShape = Block.box(0, xzOffset, xzOffset, height, 16 - xzOffset, 16 - xzOffset);
		this.westShape = Block.box(16 - height, xzOffset, xzOffset, 16, 16 - xzOffset, 16 - xzOffset);
		registerDefaultState(stateDefinition.any()
			.setValue(FACING, Direction.UP)
			.setValue(WATERLOGGED, Boolean.FALSE));
	}

	public int getXpNuggetValue() {
		return xpNuggetValue;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, WATERLOGGED);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case NORTH -> northShape;
			case SOUTH -> southShape;
			case EAST -> eastShape;
			case WEST -> westShape;
			case DOWN -> downShape;
			default -> upShape;
		};
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState placement = defaultBlockState().setValue(FACING, context.getClickedFace());
		return withWater(placement, context);
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos attachedPos = pos.relative(facing.getOpposite());
		BlockState attachedState = level.getBlockState(attachedPos);
		return attachedState.isFaceSturdy(level, attachedPos, facing)
			|| attachedState.getBlock() instanceof BuddingExperienceBlock;
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
		BlockPos pos, BlockPos neighborPos) {
		updateWater(level, state, pos);
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos,
		boolean isMoving) {
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
		if (level.isClientSide)
			return;
		Direction facing = state.getValue(FACING);
		if (!neighborPos.equals(pos.relative(facing.getOpposite())))
			return;
		if (state.canSurvive(level, pos))
			return;
		level.destroyBlock(pos, true);
	}

	@Override
	public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!oldState.is(newState.getBlock()) && !(newState.getBlock() instanceof ExperienceClusterBlock)) {
			Direction facing = oldState.getValue(FACING);
			BlockPos buddingPos = pos.relative(facing.getOpposite());
			BlockState buddingState = level.getBlockState(buddingPos);
			if (buddingState.getBlock() instanceof BuddingExperienceBlock) {
				BlockEntity be = level.getBlockEntity(buddingPos);
				if (be instanceof BuddingExperienceBlockEntity budding)
					budding.resetFaceState(facing);
			}
		}
		super.onRemove(oldState, level, pos, newState, isMoving);
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return fluidState(state);
	}
}
