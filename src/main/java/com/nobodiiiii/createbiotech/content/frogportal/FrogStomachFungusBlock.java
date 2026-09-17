package com.nobodiiiii.createbiotech.content.frogportal;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nobodiiiii.createbiotech.registry.CBBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A crimson-fungus-like stomach plant that grows away from any stomach surface. */
public class FrogStomachFungusBlock extends Block implements BonemealableBlock {

	public static final MapCodec<FrogStomachFungusBlock> CODEC = simpleCodec(FrogStomachFungusBlock::new);
	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	private static final float BONEMEAL_SUCCESS_CHANCE = 0.4f;

	private static final VoxelShape UP_SHAPE = box(4, 0, 4, 12, 9, 12);
	private static final VoxelShape DOWN_SHAPE = box(4, 7, 4, 12, 16, 12);
	private static final VoxelShape NORTH_SHAPE = box(4, 4, 7, 12, 12, 16);
	private static final VoxelShape SOUTH_SHAPE = box(4, 4, 0, 12, 12, 9);
	private static final VoxelShape EAST_SHAPE = box(0, 4, 4, 9, 12, 12);
	private static final VoxelShape WEST_SHAPE = box(7, 4, 4, 16, 12, 12);

	public FrogStomachFungusBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
	}

	@Override
	protected MapCodec<? extends FrogStomachFungusBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case DOWN -> DOWN_SHAPE;
			case NORTH -> NORTH_SHAPE;
			case SOUTH -> SOUTH_SHAPE;
			case EAST -> EAST_SHAPE;
			case WEST -> WEST_SHAPE;
			default -> UP_SHAPE;
		};
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
		return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction growthDirection = state.getValue(FACING);
		BlockPos supportPos = pos.relative(growthDirection.getOpposite());
		return isStomachSurface(level, supportPos, growthDirection);
	}

	private static boolean isStomachSurface(LevelReader level, BlockPos pos, Direction face) {
		BlockState support = level.getBlockState(pos);
		return (support.is(CBBlocks.FROG_STOMACH_WALL.get())
			|| support.is(CBBlocks.FROG_STOMACH_MUCOSA.get())
			|| support.is(CBBlocks.FROG_STOMACH_FOLD.get()))
			&& support.isFaceSturdy(level, pos, face);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
		LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos))
			return Blocks.AIR.defaultBlockState();
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
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
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
		return state.canSurvive(level, pos);
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
		return random.nextFloat() < BONEMEAL_SUCCESS_CHANCE;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
		grow(level, random, pos, state.getValue(FACING));
	}

	/** Grows a broad umbrella-shaped stomach fungus away from its supporting surface. */
	public static boolean grow(ServerLevel level, RandomSource random, BlockPos origin,
		Direction growthDirection) {
		if (!isStomachSurface(level, origin.relative(growthDirection.getOpposite()), growthDirection))
			return false;

		Map<BlockPos, BlockState> structure = createGrowthStructure(origin, growthDirection, random);
		long spaceIndex = level.dimension().equals(FrogStomachDimensions.FROG_STOMACH)
			? FrogStomachSpace.spaceIndexAt(origin)
			: -1L;
		for (BlockPos target : structure.keySet()) {
			BlockState current = level.getBlockState(target);
			boolean replaceableOrigin = target.equals(origin)
				&& current.is(CBBlocks.FROG_STOMACH_FUNGUS.get());
			if ((!current.isAir() && !replaceableOrigin)
				|| (spaceIndex >= 0 && FrogStomachSpace.isPortalApproachProtected(spaceIndex, target)))
				return false;
		}

		for (Map.Entry<BlockPos, BlockState> entry : structure.entrySet())
			level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
		return true;
	}

	private static Map<BlockPos, BlockState> createGrowthStructure(BlockPos origin, Direction growthDirection,
		RandomSource random) {
		Map<BlockPos, BlockState> structure = new LinkedHashMap<>();
		Direction firstAxis = firstPerpendicularAxis(growthDirection);
		Direction secondAxis = secondPerpendicularAxis(growthDirection);
		FrogStomachFungusGeometry.Structure geometry = FrogStomachFungusGeometry.create(random.nextLong());
		BlockState stem = CBBlocks.FROG_STOMACH_FUNGUS_STEM.get().defaultBlockState()
			.setValue(BlockStateProperties.AXIS, growthDirection.getAxis());
		BlockState gills = CBBlocks.FROG_STOMACH_FUNGUS_GILLS.get().defaultBlockState();
		BlockState cap = CBBlocks.FROG_STOMACH_FUNGUS_CAP.get().defaultBlockState();
		BlockState light = CBBlocks.FROG_STOMACH_FUNGUS_LIGHT.get().defaultBlockState();
		for (FrogStomachFungusGeometry.Cell cell : geometry.cells()) {
			BlockState state = switch (cell.part()) {
				case STEM -> stem;
				case GILLS -> gills;
				case CAP -> cap;
				case LIGHT -> light;
			};
			structure.put(localPos(origin, growthDirection, firstAxis, secondAxis,
				cell.first(), cell.forward(), cell.second()), state);
		}
		return structure;
	}

	private static BlockPos localPos(BlockPos origin, Direction growthDirection, Direction firstAxis,
		Direction secondAxis, int first, int forward, int second) {
		return origin.relative(firstAxis, first)
			.relative(growthDirection, forward)
			.relative(secondAxis, second);
	}

	private static Direction firstPerpendicularAxis(Direction growthDirection) {
		return growthDirection.getAxis() == Direction.Axis.X ? Direction.UP : Direction.EAST;
	}

	private static Direction secondPerpendicularAxis(Direction growthDirection) {
		return growthDirection.getAxis() == Direction.Axis.Z ? Direction.UP : Direction.SOUTH;
	}
}
