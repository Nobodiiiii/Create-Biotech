package com.nobodiiiii.createbiotech.content.evokerenchantingchamber;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class EvokerEnchantingChamberBlock extends BaseEntityBlock {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

	public EvokerEnchantingChamberBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
			.setValue(FACING, Direction.NORTH)
			.setValue(HALF, DoubleBlockHalf.LOWER));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		if (!hasSpaceForUpperHalf(level, pos))
			return null;
		if (!level.getBlockState(pos.above()).canBeReplaced(context))
			return null;
		return defaultBlockState()
			.setValue(FACING, context.getHorizontalDirection().getOpposite())
			.setValue(HALF, DoubleBlockHalf.LOWER);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
		LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
		DoubleBlockHalf half = state.getValue(HALF);
		Direction counterpartDirection = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;

		if (direction == counterpartDirection) {
			return neighborState.is(this) && neighborState.getValue(HALF) != half
				? state
				: Blocks.AIR.defaultBlockState();
		}

		if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(level, currentPos))
			return Blocks.AIR.defaultBlockState();

		return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockState belowState = level.getBlockState(pos.below());
			return belowState.is(this) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER;
		}

		BlockPos belowPos = pos.below();
		return level.getBlockState(belowPos).isFaceSturdy(level, belowPos, Direction.UP);
	}

	@Override
	public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockPos lowerPos = pos.below();
			BlockState lowerState = level.getBlockState(lowerPos);
			if (lowerState.is(this) && lowerState.getValue(HALF) == DoubleBlockHalf.LOWER) {
				if (player.isCreative()) {
					level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
					level.levelEvent(player, 2001, lowerPos, Block.getId(lowerState));
				} else {
					BlockEntity lowerBlockEntity = level.getBlockEntity(lowerPos);
					dropResources(lowerState, (ServerLevel) level, lowerPos, lowerBlockEntity, player,
						player.getMainHandItem());
					level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
				}
			}
		}

		super.playerWillDestroy(level, pos, state, player);
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
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	@Override
	public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0f;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return CBBlockEntityTypes.EVOKER_ENCHANTING_CHAMBER.get().create(pos, state);
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		EvokerEnchantingChamberBlockEntity blockEntity = getChamberBlockEntity(level, pos, state);
		if (blockEntity == null)
			return InteractionResult.PASS;

		ItemStack heldStack = player.getItemInHand(hand);
		if (level.isClientSide)
			return blockEntity.canInteract(heldStack) ? InteractionResult.SUCCESS : InteractionResult.PASS;

		if (blockEntity.tryInsertFromPlayer(player, hand, heldStack))
			return InteractionResult.CONSUME;
		if (blockEntity.tryExtractToPlayer(player)) {
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2f, 1f);
			return InteractionResult.CONSUME;
		}
		return InteractionResult.PASS;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
		BlockEntityType<T> type) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER
			? createTickerHelper(type, CBBlockEntityTypes.EVOKER_ENCHANTING_CHAMBER.get(),
				EvokerEnchantingChamberBlockEntity::tick)
			: null;
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock()) && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof EvokerEnchantingChamberBlockEntity chamber)
				chamber.dropContentsAndFluid();
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF);
	}

	private static EvokerEnchantingChamberBlockEntity getChamberBlockEntity(Level level, BlockPos pos,
		BlockState state) {
		BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
		BlockEntity blockEntity = level.getBlockEntity(basePos);
		return blockEntity instanceof EvokerEnchantingChamberBlockEntity chamber ? chamber : null;
	}

	public static boolean hasSpaceForUpperHalf(Level level, BlockPos lowerPos) {
		if (lowerPos.getY() >= level.getMaxBuildHeight() - 1)
			return false;
		return level.getBlockState(lowerPos.above()).canBeReplaced();
	}
}
