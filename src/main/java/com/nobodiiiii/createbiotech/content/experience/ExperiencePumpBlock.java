package com.nobodiiiii.createbiotech.content.experience;

import com.nobodiiiii.createbiotech.content.experience.pipe.ExperiencePipeBlock;
import com.nobodiiiii.createbiotech.content.experience.pipe.ExperiencePropagator;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

public class ExperiencePumpBlock extends DirectionalKineticBlock
	implements SimpleWaterloggedBlock, ICogWheel, IBE<ExperiencePumpBlockEntity> {

	public ExperiencePumpBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
		return originalState.setValue(FACING, originalState.getValue(FACING)
			.getOpposite());
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING)
			.getAxis();
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return AllShapes.PUMP.get(state.getValue(FACING));
	}

	@Override
	public void neighborChanged(BlockState state, Level world, BlockPos pos, Block otherBlock, BlockPos neighborPos,
		boolean isMoving) {
		DebugPackets.sendNeighborsUpdatePacket(world, pos);
		Direction direction =
			ExperiencePropagator.validateNeighbourChange(state, world, pos, otherBlock, neighborPos, isMoving);
		if (direction == null || !isOpenAt(state, direction))
			return;
		world.scheduleTick(pos, this, 1, TickPriority.HIGH);
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false)
			: Fluids.EMPTY.defaultFluidState();
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState, LevelAccessor level,
		BlockPos pos, BlockPos neighbourPos) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState toPlace = super.getStateForPlacement(context);
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();

		boolean isShiftKeyDown = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
		toPlace = ProperWaterloggedBlock.withWater(level, toPlace, pos);

		Direction nearestLookingDirection = context.getNearestLookingDirection();
		Direction targetDirection = isShiftKeyDown ? nearestLookingDirection : nearestLookingDirection.getOpposite();
		Direction bestConnectedDirection = null;
		double bestDistance = Double.MAX_VALUE;

		for (Direction direction : Iterate.directions) {
			BlockPos adjacentPos = pos.relative(direction);
			BlockState adjacentState = level.getBlockState(adjacentPos);
			if (!ExperiencePipeBlock.canConnectTo(level, adjacentPos, adjacentState, direction))
				continue;
			double distance = Vec3.atLowerCornerOf(direction.getNormal())
				.distanceTo(Vec3.atLowerCornerOf(targetDirection.getNormal()));
			if (distance > bestDistance)
				continue;
			bestDistance = distance;
			bestConnectedDirection = direction;
		}

		if (bestConnectedDirection != null && bestConnectedDirection.getAxis() != targetDirection.getAxis()
			&& !isShiftKeyDown)
			return toPlace.setValue(FACING, bestConnectedDirection);

		return toPlace;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide)
			return;
		withBlockEntityDo(level, pos, be -> be.setAdvancementOwner(placer));
	}

	public static boolean isPump(BlockState state) {
		return state.getBlock() instanceof ExperiencePumpBlock;
	}

	@Override
	public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, world, pos, oldState, isMoving);
		if (world.isClientSide)
			return;
		if (state != oldState)
			world.scheduleTick(pos, this, 1, TickPriority.HIGH);

		if (isPump(state) && isPump(oldState)
			&& state.getValue(FACING) == oldState.getValue(FACING).getOpposite()) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity instanceof ExperiencePumpBlockEntity pump)
				pump.pressureUpdate = true;
		}
	}

	public static boolean isOpenAt(BlockState state, Direction direction) {
		return direction.getAxis() == state.getValue(FACING).getAxis();
	}

	@Override
	public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
		ExperiencePropagator.propagateChangedPipe(world, pos, state);
	}

	@Override
	public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
		boolean blockTypeChanged = !state.is(newState.getBlock());
		if (blockTypeChanged && !world.isClientSide)
			ExperiencePropagator.propagateChangedPipe(world, pos, state);
		super.onRemove(state, world, pos, newState, isMoving);
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter reader, BlockPos pos, PathComputationType type) {
		return false;
	}

	@Override
	public Class<ExperiencePumpBlockEntity> getBlockEntityClass() {
		return ExperiencePumpBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ExperiencePumpBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.EXPERIENCE_PUMP.get();
	}
}
