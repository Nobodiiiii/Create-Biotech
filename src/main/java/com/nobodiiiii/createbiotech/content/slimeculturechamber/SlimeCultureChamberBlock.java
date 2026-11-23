package com.nobodiiiii.createbiotech.content.slimeculturechamber;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nobodiiiii.createbiotech.foundation.block.CBMultiBlockLifecycle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SlimeCultureChamberBlock extends HorizontalDirectionalBlock implements CBMultiBlockLifecycle.Part {

	public static final MapCodec<SlimeCultureChamberBlock> CODEC = simpleCodec(SlimeCultureChamberBlock::new);
	static final int WIDTH = 3;
	static final int HEIGHT = 4;
	static final int PART_COUNT = WIDTH * WIDTH * HEIGHT;
	static final int ANCHOR_PART = 4;
	public static final IntegerProperty PART = IntegerProperty.create("part", 0, PART_COUNT - 1);

	private static final VoxelShape[] PART_SHAPES = makePartShapes();

	public SlimeCultureChamberBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(PART, ANCHOR_PART));
	}

	@Override
	protected MapCodec<SlimeCultureChamberBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		return context.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
			.apply(state).setValue(PART, ANCHOR_PART);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		// BlockItem applies the item state again after the whole chamber has been placed.
		if (state.getValue(PART) != ANCHOR_PART)
			level.setBlock(pos, state.setValue(PART, ANCHOR_PART), Block.UPDATE_CLIENTS);
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		if (mirror == Mirror.NONE)
			return state;
		int part = state.getValue(PART);
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
			.setValue(PART, part - 2 * localX(part));
	}

	@Override
	public BlockPos getMultiBlockAnchor(BlockPos pos, BlockState state) {
		BlockPos offset = partPos(BlockPos.ZERO, state.getValue(FACING), state.getValue(PART));
		return pos.subtract(offset);
	}

	static BlockPos partPos(BlockPos anchor, Direction facing, int part) {
		return anchor.relative(facing.getCounterClockWise(), localX(part))
			.relative(facing, localZ(part)).above(localY(part));
	}

	// Parts are numbered left to right, then back to front, then bottom to top in the export.
	private static int localX(int part) {
		return part % WIDTH - 1;
	}

	private static int localY(int part) {
		return part / (WIDTH * WIDTH);
	}

	private static int localZ(int part) {
		return part / WIDTH % WIDTH - 1;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return PART_SHAPES[state.getValue(PART)];
	}

	private static VoxelShape[] makePartShapes() {
		// Approximate the bevels with steps and keep collision within each occupied cell.
		VoxelShape outline = Shapes.or(
			box(-16, 0, -12, 32, 7, 28), box(-12, 0, -16, 28, 7, 32),
			box(-13, 6, -9, 29, 16, 25), box(-9, 6, -13, 25, 16, 29),
			box(-10.5, 16, -10.5, 26.5, 54, 26.5),
			box(-13, 54, -9, 29, 62, 25), box(-9, 54, -13, 25, 62, 29),
			box(-2, 62, -13, 18, 64, 29), box(-13, 62, -2, 29, 64, 18));
		VoxelShape[] shapes = new VoxelShape[PART_COUNT];
		for (int part = 0; part < PART_COUNT; part++) {
			VoxelShape local = outline.move(-localX(part), -localY(part), -localZ(part));
			shapes[part] = Shapes.join(local, Shapes.block(), BooleanOp.AND);
		}
		return shapes;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, level, pos, oldState, isMoving);
		BlockPos anchor = getMultiBlockAnchor(pos, state);
		BlockPos checkPos = CBMultiBlockLifecycle.isLoaded(level, anchor) && belongsTo(level, anchor, anchor)
			? anchor : pos;
		CBMultiBlockLifecycle.scheduleValidation(level, checkPos, this);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// Structure templates and contraptions restore parts individually. Validate after placement settles.
		BlockPos anchor = getMultiBlockAnchor(pos, state);
		Direction facing = state.getValue(FACING);
		for (int part = 0; part < PART_COUNT; part++) {
			BlockPos partPos = partPos(anchor, facing, part);
			if (CBMultiBlockLifecycle.isLoaded(level, partPos)
				&& level.getBlockState(partPos) != state.setValue(PART, part)) {
				CBMultiBlockLifecycle.removeSilently(level, pos);
				return;
			}
		}
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		super.onRemove(state, level, pos, newState, isMoving);
		if (newState.is(this) || isMoving || level.restoringBlockSnapshots)
			return;

		BlockPos anchor = getMultiBlockAnchor(pos, state);
		if (!pos.equals(anchor)) {
			if (belongsTo(level, anchor, anchor))
				CBMultiBlockLifecycle.removeSilently(level, anchor);
			return;
		}

		// Only the first part broken participates in vanilla loot. Once the anchor is gone,
		// removing the other parts cannot trigger another teardown.
		Direction facing = state.getValue(FACING);
		for (int part = 0; part < PART_COUNT; part++) {
			BlockPos partPos = partPos(anchor, facing, part);
			if (belongsTo(level, partPos, anchor))
				CBMultiBlockLifecycle.removeSilently(level, partPos);
		}
	}

	private boolean belongsTo(Level level, BlockPos pos, BlockPos anchor) {
		BlockState state = level.getBlockState(pos);
		return state.is(this) && getMultiBlockAnchor(pos, state).equals(anchor);
	}
}
