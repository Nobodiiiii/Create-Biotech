package com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod;

import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FixedCarrotFishingRodBlock extends HorizontalDirectionalBlock implements EntityBlock {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	private static final VoxelShape NORTH_COLLISION = Shapes.or(
		Block.box(6, 10, 12, 10, 14, 16),
		Block.box(7, 11, 1, 9, 13, 12));
	private static final VoxelShape SOUTH_COLLISION = Shapes.or(
		Block.box(6, 10, 0, 10, 14, 4),
		Block.box(7, 11, 4, 9, 13, 15));
	private static final VoxelShape WEST_COLLISION = Shapes.or(
		Block.box(12, 10, 6, 16, 14, 10),
		Block.box(1, 11, 7, 12, 13, 9));
	private static final VoxelShape EAST_COLLISION = Shapes.or(
		Block.box(0, 10, 6, 4, 14, 10),
		Block.box(4, 11, 7, 15, 13, 9));

	private static final VoxelShape NORTH_SHAPE = Shapes.or(
		NORTH_COLLISION,
		Block.box(7.875, 3, 1.375, 8.125, 11, 1.625),
		Block.box(4, -4, 1, 12, 4, 2));
	private static final VoxelShape SOUTH_SHAPE = Shapes.or(
		SOUTH_COLLISION,
		Block.box(7.875, 3, 14.375, 8.125, 11, 14.625),
		Block.box(4, -4, 14, 12, 4, 15));
	private static final VoxelShape WEST_SHAPE = Shapes.or(
		WEST_COLLISION,
		Block.box(1.375, 3, 7.875, 1.625, 11, 8.125),
		Block.box(1, -4, 4, 2, 4, 12));
	private static final VoxelShape EAST_SHAPE = Shapes.or(
		EAST_COLLISION,
		Block.box(14.375, 3, 7.875, 14.625, 11, 8.125),
		Block.box(14, -4, 4, 15, 4, 12));

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
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
		case NORTH -> NORTH_COLLISION;
		case SOUTH -> SOUTH_COLLISION;
		case WEST -> WEST_COLLISION;
		case EAST -> EAST_COLLISION;
		default -> NORTH_COLLISION;
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
		Player player) {
		return new ItemStack(CBItems.FIXED_CARROT_FISHING_ROD.get());
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof FixedCarrotFishingRodBlockEntity blockEntity))
			return InteractionResult.PASS;

		ItemStack heldItem = player.getItemInHand(hand);
		ItemStack baitItem = blockEntity.getBaitItem();

		if (baitItem.isEmpty() && !heldItem.isEmpty()) {
			if (!level.isClientSide()) {
				ItemStack attachedItem = heldItem.copy();
				attachedItem.setCount(1);
				blockEntity.setBaitItem(attachedItem);
				if (!player.isCreative())
					heldItem.shrink(1);
			}
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
			return InteractionResult.sidedSuccess(level.isClientSide());
		}

		if (!baitItem.isEmpty() && !heldItem.isEmpty()) {
			if (!level.isClientSide()) {
				ItemStack oldBait = baitItem.copy();
				ItemStack newBait = heldItem.copy();
				newBait.setCount(1);
				blockEntity.setBaitItem(newBait);
				if (!player.isCreative())
					heldItem.shrink(1);
				player.addItem(oldBait);
			}
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
			return InteractionResult.sidedSuccess(level.isClientSide());
		}

		if (!baitItem.isEmpty() && heldItem.isEmpty()) {
			if (!level.isClientSide()) {
				ItemStack oldBait = baitItem.copy();
				blockEntity.setBaitItem(ItemStack.EMPTY);
				player.setItemInHand(hand, oldBait);
			}
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
			return InteractionResult.sidedSuccess(level.isClientSide());
		}

		return InteractionResult.PASS;
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
		return false;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FixedCarrotFishingRodBlockEntity(pos, state);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
}
