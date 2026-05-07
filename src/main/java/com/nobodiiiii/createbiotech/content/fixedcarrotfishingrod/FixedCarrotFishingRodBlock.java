package com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod;

import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FixedCarrotFishingRodBlock extends HorizontalDirectionalBlock {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	private static final VoxelShape NORTH_SHAPE = Shapes.joinUnoptimized(
		Shapes.or(
			Block.box(6, 6, 12, 10, 10, 16),
			Block.box(7, 7, 1, 9, 9, 12),
			Block.box(7.5, -1, 0.5, 8.5, 7, 1.5)),
		Block.box(5.5, -8, 0.25, 10.5, -1, 1.75),
		BooleanOp.OR);
	private static final VoxelShape SOUTH_SHAPE = Shapes.joinUnoptimized(
		Shapes.or(
			Block.box(6, 6, 0, 10, 10, 4),
			Block.box(7, 7, 4, 9, 9, 15),
			Block.box(7.5, -1, 14.5, 8.5, 7, 15.5)),
		Block.box(5.5, -8, 14.25, 10.5, -1, 15.75),
		BooleanOp.OR);
	private static final VoxelShape WEST_SHAPE = Shapes.joinUnoptimized(
		Shapes.or(
			Block.box(12, 6, 6, 16, 10, 10),
			Block.box(1, 7, 7, 12, 9, 9),
			Block.box(0.5, -1, 7.5, 1.5, 7, 8.5)),
		Block.box(0.25, -8, 5.5, 1.75, -1, 10.5),
		BooleanOp.OR);
	private static final VoxelShape EAST_SHAPE = Shapes.joinUnoptimized(
		Shapes.or(
			Block.box(0, 6, 6, 4, 10, 10),
			Block.box(4, 7, 7, 15, 9, 9),
			Block.box(14.5, -1, 7.5, 15.5, 7, 8.5)),
		Block.box(14.25, -8, 5.5, 15.75, -1, 10.5),
		BooleanOp.OR);

	public FixedCarrotFishingRodBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction clickedFace = context.getClickedFace();
		if (clickedFace.getAxis().isVertical())
			return null;

		BlockState state = defaultBlockState().setValue(FACING, clickedFace);
		return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos attachedPos = pos.relative(facing.getOpposite());
		return level.getBlockState(attachedPos).isFaceSturdy(level, attachedPos, facing);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
		LevelAccessor level, BlockPos currentPos, BlockPos neighbourPos) {
		return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, currentPos)
			? Blocks.AIR.defaultBlockState()
			: super.updateShape(state, direction, neighbourState, level, currentPos, neighbourPos);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
		case NORTH -> NORTH_SHAPE;
		case SOUTH -> SOUTH_SHAPE;
		case WEST -> WEST_SHAPE;
		case EAST -> EAST_SHAPE;
		default -> NORTH_SHAPE;
		};
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return rotate(state, mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos,
		net.minecraft.world.entity.player.Player player) {
		return new ItemStack(CBItems.FIXED_CARROT_FISHING_ROD.get());
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
		return false;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
}
