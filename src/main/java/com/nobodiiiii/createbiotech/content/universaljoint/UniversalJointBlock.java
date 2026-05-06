package com.nobodiiiii.createbiotech.content.universaljoint;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class UniversalJointBlock extends KineticBlock implements IBE<UniversalJointBlockEntity>, ProperWaterloggedBlock {

	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	private static final VoxelShape DOWN_SHAPE = Block.box(6, 0, 6, 10, 4, 10);
	private static final VoxelShape UP_SHAPE = Block.box(6, 12, 6, 10, 16, 10);
	private static final VoxelShape NORTH_SHAPE = Block.box(6, 6, 0, 10, 10, 4);
	private static final VoxelShape SOUTH_SHAPE = Block.box(6, 6, 12, 10, 10, 16);
	private static final VoxelShape WEST_SHAPE = Block.box(0, 6, 6, 4, 10, 10);
	private static final VoxelShape EAST_SHAPE = Block.box(12, 6, 6, 16, 10, 10);

	public UniversalJointBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
			.setValue(FACING, Direction.NORTH)
			.setValue(WATERLOGGED, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withWater(defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite()), context);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == state.getValue(FACING).getAxis();
	}

	@Override
	public Direction.Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING).getAxis();
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos neighbourPos = pos.relative(facing);
		BlockState neighbourState = level.getBlockState(neighbourPos);
		return !neighbourState.isAir() && !neighbourState.canBeReplaced();
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
		LevelAccessor level, BlockPos currentPos, BlockPos neighbourPos) {
		updateWater(level, state, currentPos);
		if (direction == state.getValue(FACING) && !state.canSurvive(level, currentPos))
			return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		return state;
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!level.isClientSide && state.getBlock() != newState.getBlock() && !isMoving) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof UniversalJointBlockEntity joint)
				removePairedEndpoint(level, pos, joint);
		}

		super.onRemove(state, level, pos, newState, isMoving);
	}

	private void removePairedEndpoint(Level level, BlockPos pos, UniversalJointBlockEntity joint) {
		BlockPos linkedPos = joint.getLinkedPos();
		if (linkedPos == null || !level.isLoaded(linkedPos))
			return;

		BlockEntity linkedBlockEntity = level.getBlockEntity(linkedPos);
		if (!(linkedBlockEntity instanceof UniversalJointBlockEntity linkedJoint))
			return;
		if (!pos.equals(linkedJoint.getLinkedPos()))
			return;

		linkedJoint.clearLink();
		if (level.getBlockState(linkedPos).is(this))
			level.destroyBlock(linkedPos, false);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShapeForFacing(state.getValue(FACING));
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	private static VoxelShape getShapeForFacing(Direction facing) {
		return switch (facing) {
		case DOWN -> DOWN_SHAPE;
		case UP -> UP_SHAPE;
		case NORTH -> NORTH_SHAPE;
		case SOUTH -> SOUTH_SHAPE;
		case WEST -> WEST_SHAPE;
		case EAST -> EAST_SHAPE;
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
		return new ItemStack(CBItems.UNIVERSAL_JOINT.get());
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
		return false;
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return fluidState(state);
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(FACING, WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public Class<UniversalJointBlockEntity> getBlockEntityClass() {
		return UniversalJointBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends UniversalJointBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.UNIVERSAL_JOINT.get();
	}
}
