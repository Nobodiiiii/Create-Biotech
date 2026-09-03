package com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod;

import com.mojang.serialization.MapCodec;
import com.nobodiiiii.createbiotech.foundation.block.CBWrenchHelper;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FixedCarrotFishingRodBlock extends HorizontalDirectionalBlock implements EntityBlock, IWrenchable {
	public static final MapCodec<FixedCarrotFishingRodBlock> CODEC = simpleCodec(FixedCarrotFishingRodBlock::new);

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
	protected MapCodec<? extends FixedCarrotFishingRodBlock> codec() {
		return CODEC;
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
	public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
		Player player) {
		return new ItemStack(CBItems.FIXED_CARROT_FISHING_ROD.get());
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
		Player player, InteractionHand hand, BlockHitResult hit) {
		if (CBWrenchHelper.isWrench(heldItem))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		InteractionResult result = interact(state, level, pos, player, heldItem);
		return result.consumesAction()
			? ItemInteractionResult.sidedSuccess(level.isClientSide)
			: ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hit) {
		return interact(state, level, pos, player, ItemStack.EMPTY);
	}

	private InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player,
		ItemStack heldItem) {
		if (!(level.getBlockEntity(pos) instanceof FixedCarrotFishingRodBlockEntity blockEntity))
			return InteractionResult.PASS;

		ItemStack baitItem = blockEntity.getBaitItem();
		if (level.isClientSide())
			return baitItem.isEmpty() && heldItem.isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS;

		if (baitItem.isEmpty() && !heldItem.isEmpty()) {
			ItemStack copy = heldItem.copy();
			copy.setCount(1);
			blockEntity.setBaitItem(copy);
			blockEntity.setAdvancementOwner(player);
			if (!player.isCreative())
				heldItem.shrink(1);
		} else if (!baitItem.isEmpty() && heldItem.isEmpty()) {
			giveOrDrop(player, baitItem.copy());
			blockEntity.setBaitItem(ItemStack.EMPTY);
		} else if (!baitItem.isEmpty() && !heldItem.isEmpty()) {
			ItemStack copy = heldItem.copy();
			copy.setCount(1);
			if (!player.isCreative())
				heldItem.shrink(1);
			blockEntity.setBaitItem(copy);
			blockEntity.setAdvancementOwner(player);
			giveOrDrop(player, baitItem.copy());
		} else {
			return InteractionResult.PASS;
		}

		BlockPos soundPos = BlockPos.containing(SubLevelCompat.toWorld(level, pos, Vec3.atCenterOf(pos)));
		level.playSound(null, soundPos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
		return InteractionResult.SUCCESS;
	}

	private static void giveOrDrop(Player player, ItemStack stack) {
		if (!player.addItem(stack))
			player.drop(stack, false);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (context.getClickedFace().getAxis() != Direction.Axis.Y)
			return InteractionResult.PASS;
		BlockState rotated = state.setValue(FACING,
			state.getValue(FACING).getClockWise(context.getClickedFace().getAxis()));
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!rotated.canSurvive(level, pos))
			return InteractionResult.PASS;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		level.setBlock(pos, rotated, Block.UPDATE_ALL);
		IWrenchable.playRotateSound(level, pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock())) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof FixedCarrotFishingRodBlockEntity rodEntity) {
				ItemStack stack = rodEntity.getBaitItem();
				if (!stack.isEmpty()) {
					popResource(level, pos, stack.copy());
					rodEntity.setBaitItem(ItemStack.EMPTY);
				}
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
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
