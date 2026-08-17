package com.nobodiiiii.createbiotech.content.magmacubeburner;

import javax.annotation.ParametersAreNonnullByDefault;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.fluid.FluidHelper;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class MagmaCubeBurnerBlock extends BaseEntityBlock implements IWrenchable {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	private static final VoxelShape MODEL_SHAPE = Shapes.or(
		Block.box(1, 0, 1, 15, 2, 15),
		Block.box(2, 2, 2, 14, 6, 3),
		Block.box(2, 2, 2, 3, 6, 14),
		Block.box(13, 2, 2, 14, 6, 14),
		Block.box(2, 2, 13, 14, 6, 14),
		Block.box(1, 2, 1, 3, 13, 3),
		Block.box(13, 2, 1, 15, 13, 3),
		Block.box(13, 2, 13, 15, 13, 15),
		Block.box(1, 2, 13, 3, 13, 15),
		Block.box(3, 6, 13, 13, 8, 15),
		Block.box(3, 6, 1, 13, 8, 3),
		Block.box(1, 6, 3, 3, 8, 13),
		Block.box(13, 6, 3, 15, 8, 13),
		Block.box(5, 14, 11, 11, 15, 12),
		Block.box(5, 13, 13, 11, 15, 15),
		Block.box(4, 14, 5, 5, 15, 11),
		Block.box(1, 13, 5, 3, 15, 11),
		Block.box(5, 14, 4, 11, 15, 5),
		Block.box(11, 13, 5, 12, 14, 11),
		Block.box(5, 13, 1, 11, 15, 3),
		Block.box(13, 13, 5, 15, 15, 11),
		Block.box(1, 13, 11, 5, 16, 15),
		Block.box(1, 13, 1, 5, 16, 5),
		Block.box(11, 13, 1, 15, 16, 5),
		Block.box(11, 13, 11, 15, 16, 15),
		Block.box(0, 0, 0, 5, 2, 1),
		Block.box(0, 0, 1, 1, 2, 5),
		Block.box(0, 0, 11, 1, 2, 15),
		Block.box(0, 0, 15, 5, 2, 16),
		Block.box(11, 0, 15, 16, 2, 16),
		Block.box(15, 0, 11, 16, 2, 15),
		Block.box(11, 0, 0, 16, 2, 1),
		Block.box(15, 0, 1, 16, 2, 5));

	public MagmaCubeBurnerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
			.setValue(FACING, Direction.NORTH)
			.setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.NONE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, BlazeBurnerBlock.HEAT_LEVEL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		ItemStack stack = context.getItemInHand();
		HeatLevel initialHeat = stack.getItem() instanceof MagmaCubeBurnerItem burnerItem
			&& !burnerItem.hasCapturedMagmaCube() ? HeatLevel.NONE : HeatLevel.SMOULDERING;
		return defaultBlockState()
			.setValue(BlazeBurnerBlock.HEAT_LEVEL, initialHeat)
			.setValue(FACING, context.getHorizontalDirection().getOpposite());
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
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide && level.getBlockEntity(pos.above()) instanceof BasinBlockEntity basin)
			basin.notifyChangeOfContents();
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hitResult) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof MagmaCubeBurnerBlockEntity burner
			&& FluidHelper.tryEmptyItemIntoBE(level, player, hand, player.getItemInHand(hand), burner))
			return InteractionResult.SUCCESS;
		return InteractionResult.PASS;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		if (state.getValue(BlazeBurnerBlock.HEAT_LEVEL) == HeatLevel.NONE)
			return null;
		return new MagmaCubeBurnerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
		BlockEntityType<T> type) {
		return createTickerHelper(type, CBBlockEntityTypes.MAGMA_CUBE_BURNER.get(),
			MagmaCubeBurnerBlockEntity::tick);
	}

	@Override
	public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos,
		Player player) {
		return (state.getValue(BlazeBurnerBlock.HEAT_LEVEL) == HeatLevel.NONE
			? CBItems.EMPTY_MAGMA_CUBE_BURNER : CBItems.MAGMA_CUBE_BURNER).get().getDefaultInstance();
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return MODEL_SHAPE;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return MODEL_SHAPE;
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return switch (state.getValue(BlazeBurnerBlock.HEAT_LEVEL)) {
		case NONE, SMOULDERING -> 0;
		case FADING, KINDLED -> 2;
		case SEETHING -> 3;
		};
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos,
		PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!isBurning(state.getValue(BlazeBurnerBlock.HEAT_LEVEL)) || random.nextInt(10) != 0)
			return;
		level.playLocalSound(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
			SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, .5f + random.nextFloat(),
			random.nextFloat() * .7f + .6f, false);
	}

	public static int getLight(BlockState state) {
		return BlazeBurnerBlock.getLight(state);
	}

	public static boolean isBurning(HeatLevel heatLevel) {
		return heatLevel == HeatLevel.FADING || heatLevel == HeatLevel.KINDLED;
	}
}
