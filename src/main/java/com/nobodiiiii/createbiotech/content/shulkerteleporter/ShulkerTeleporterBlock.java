package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

public class ShulkerTeleporterBlock extends KineticBlock
	implements IBE<ShulkerTeleporterBlockEntity>, ICogWheel {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);

	public static final int BOTTOM = 0;
	public static final int MIDDLE = 1;
	public static final int TOP = 2;

	private static final VoxelShape OUTLINE = Block.box(1, 0, 1, 15, 16, 15);
	private static final VoxelShape BOTTOM_COLLISION = Block.box(0, 0, 0, 16, 1, 16);
	private static final VoxelShape TOP_COLLISION = Block.box(1, 0, 1, 15, 16, 15);
	private static final VoxelShape EMPTY = Block.box(0, 0, 0, 0, 0, 0);
	private static final ThreadLocal<Boolean> REMOVING_STRUCTURE = ThreadLocal.withInitial(() -> false);
	private static final ThreadLocal<Boolean> PLACING_STRUCTURE = ThreadLocal.withInitial(() -> false);

	public ShulkerTeleporterBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
			.setValue(FACING, Direction.NORTH)
			.setValue(PART, BOTTOM));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART);
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		if (!canPlaceAt(context.getLevel(), pos, context))
			return null;
		return defaultBlockState()
			.setValue(FACING, context.getHorizontalDirection().getOpposite())
			.setValue(PART, BOTTOM);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide)
			return;
		BlockState partState = defaultBlockState().setValue(FACING, state.getValue(FACING));
		PLACING_STRUCTURE.set(true);
		level.setBlock(pos.above(), partState.setValue(PART, MIDDLE), Block.UPDATE_ALL);
		level.setBlock(pos.above(2), partState.setValue(PART, TOP), Block.UPDATE_ALL);
		PLACING_STRUCTURE.set(false);
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (state.getValue(PART) == TOP)
			return InteractionResult.PASS;

		BlockPos top = getTopPos(pos, state);
		if (level.isClientSide)
			return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer serverPlayer))
			return InteractionResult.PASS;
		BlockEntity blockEntity = level.getBlockEntity(top);
		if (!(blockEntity instanceof ShulkerTeleporterBlockEntity teleporter))
			return InteractionResult.PASS;
		NetworkHooks.openScreen(serverPlayer, teleporter, teleporter::sendToMenu);
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
		BlockPos pos, BlockPos neighborPos) {
		if (!PLACING_STRUCTURE.get() && !isValidStructure(level, pos, state))
			return Blocks.AIR.defaultBlockState();
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return state.getValue(PART) == BOTTOM || PLACING_STRUCTURE.get() || isValidStructure(level, pos, state);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (state.getBlock() == newState.getBlock())
			return;

		if (!REMOVING_STRUCTURE.get())
			removeStructure(level, pos, state, state.getValue(PART) != BOTTOM);

		if (state.getValue(PART) == TOP && level.getBlockEntity(pos) instanceof ShulkerTeleporterBlockEntity teleporter)
			teleporter.unregisterAddress();

		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return OUTLINE;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(PART)) {
			case BOTTOM -> BOTTOM_COLLISION;
			case TOP -> TOP_COLLISION;
			default -> EMPTY;
		};
	}

	@Override
	public PushReaction getPistonPushReaction(BlockState state) {
		return PushReaction.BLOCK;
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
		return false;
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
	public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
		return new ItemStack(CBItems.SHULKER_TELEPORTER.get());
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return false;
	}

	@Override
	public Direction.Axis getRotationAxis(BlockState state) {
		return Direction.Axis.Y;
	}

	@Override
	public float getParticleTargetRadius() {
		return .85f;
	}

	@Override
	public float getParticleInitialRadius() {
		return .75f;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(PART) == TOP ? new ShulkerTeleporterBlockEntity(pos, state) : null;
	}

	@Override
	public Class<ShulkerTeleporterBlockEntity> getBlockEntityClass() {
		return ShulkerTeleporterBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ShulkerTeleporterBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.SHULKER_TELEPORTER.get();
	}

	public static BlockPos getBottomPos(BlockPos pos, BlockState state) {
		return pos.below(state.getValue(PART));
	}

	public static BlockPos getTopPos(BlockPos pos, BlockState state) {
		return getBottomPos(pos, state).above(TOP);
	}

	public static BlockState defaultItemRenderState() {
		return com.nobodiiiii.createbiotech.registry.CBBlocks.SHULKER_TELEPORTER.get()
			.defaultBlockState()
			.setValue(FACING, Direction.NORTH)
			.setValue(PART, TOP);
	}

	private static boolean canPlaceAt(Level level, BlockPos pos, BlockPlaceContext context) {
		return level.getBlockState(pos.above()).canBeReplaced(context)
			&& level.getBlockState(pos.above(2)).canBeReplaced(context);
	}

	private static boolean isValidStructure(LevelReader level, BlockPos pos, BlockState state) {
		int part = state.getValue(PART);
		BlockPos bottom = pos.below(part);
		for (int i = 0; i < 3; i++) {
			BlockState partState = level.getBlockState(bottom.above(i));
			if (!(partState.getBlock() instanceof ShulkerTeleporterBlock))
				return false;
			if (partState.getValue(PART) != i)
				return false;
		}
		return true;
	}

	private static void removeStructure(Level level, BlockPos pos, BlockState state, boolean dropItem) {
		BlockPos bottom = getBottomPos(pos, state);
		if (!level.isClientSide && dropItem)
			Block.popResource(level, bottom, new ItemStack(CBItems.SHULKER_TELEPORTER.get()));

		REMOVING_STRUCTURE.set(true);
		for (int i = 0; i < 3; i++) {
			BlockPos partPos = bottom.above(i);
			BlockState partState = level.getBlockState(partPos);
			if (partState.getBlock() instanceof ShulkerTeleporterBlock)
				level.setBlock(partPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
		}
		REMOVING_STRUCTURE.set(false);
	}
}
