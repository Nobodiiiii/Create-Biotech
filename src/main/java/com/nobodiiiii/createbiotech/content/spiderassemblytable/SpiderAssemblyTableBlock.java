package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.item.ItemHelper;

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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

public class SpiderAssemblyTableBlock extends HorizontalKineticBlock
	implements IBE<SpiderAssemblyTableBlockEntity> {

	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16);
	private static final ThreadLocal<Boolean> REMOVING_MAIN_FROM_TAIL = ThreadLocal.withInitial(() -> false);
	private static final ThreadLocal<Boolean> REMOVING_TAIL_FROM_MAIN = ThreadLocal.withInitial(() -> false);
	private static final ThreadLocal<Direction> FORCED_PLACEMENT_FACING = new ThreadLocal<>();

	public SpiderAssemblyTableBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = getPlacementFacing(context);
		BlockPos tailPos = context.getClickedPos().relative(facing.getOpposite());
		if (!context.getLevel().getBlockState(tailPos).canBeReplaced(context))
			return null;
		return defaultBlockState().setValue(FACING, facing);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == state.getValue(FACING).getOpposite();
	}

	@Override
	public Direction.Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING).getAxis();
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide)
			return;
		BlockPos tailPos = getTailPos(pos, state);
		BlockState tailState = CBBlocks.SPIDER_ASSEMBLY_TABLE_COG.get()
			.defaultBlockState()
			.setValue(SpiderAssemblyTableCogBlock.FACING, state.getValue(FACING));
		level.setBlock(tailPos, tailState, Block.UPDATE_ALL);
		withBlockEntityDo(level, pos, be -> be.setAdvancementOwner(placer));
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!player.isShiftKeyDown() && player.mayBuild() && player.getItemInHand(hand).is(CBItems.SPIDER_ASSEMBLY_TABLE.get()))
			return InteractionResult.PASS;
		return openMenu(level, pos, player);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
		BlockPos pos, BlockPos neighborPos) {
		if (direction == state.getValue(FACING).getOpposite()
			&& !SpiderAssemblyTableCogBlock.isValidCogFor(level, neighborPos, state))
			return Blocks.AIR.defaultBlockState();
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.hasBlockEntity() || state.getBlock() == newState.getBlock())
			return;

		if (!isRemovingMainFromTail()) {
			removeTail(level, pos, state);
			withBlockEntityDo(level, pos, be -> ItemHelper.dropContents(level, pos, be.getInventory()));
		}
		IBE.onRemove(state, level, pos, newState);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
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
	public Class<SpiderAssemblyTableBlockEntity> getBlockEntityClass() {
		return SpiderAssemblyTableBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SpiderAssemblyTableBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.SPIDER_ASSEMBLY_TABLE.get();
	}

	public static InteractionResult openMenu(Level level, BlockPos pos, Player player) {
		if (level.isClientSide)
			return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer serverPlayer))
			return InteractionResult.PASS;
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof SpiderAssemblyTableBlockEntity be))
			return InteractionResult.PASS;
		NetworkHooks.openScreen(serverPlayer, be, be::sendToMenu);
		return InteractionResult.SUCCESS;
	}

	static void removeMainFromTail(Level level, BlockPos mainPos, boolean dropMainBlock, boolean playBreakEffect) {
		BlockState mainState = level.getBlockState(mainPos);
		if (!(mainState.getBlock() instanceof SpiderAssemblyTableBlock))
			return;

		if (!level.isClientSide && dropMainBlock) {
			Block.popResource(level, mainPos, new ItemStack(CBItems.SPIDER_ASSEMBLY_TABLE.get()));
			BlockEntity blockEntity = level.getBlockEntity(mainPos);
			if (blockEntity instanceof SpiderAssemblyTableBlockEntity be)
				ItemHelper.dropContents(level, mainPos, be.getInventory());
		}

		REMOVING_MAIN_FROM_TAIL.set(true);
		level.setBlock(mainPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
		REMOVING_MAIN_FROM_TAIL.set(false);

		if (playBreakEffect)
			level.levelEvent(null, 2001, mainPos, Block.getId(mainState));
	}

	static boolean isRemovingMainFromTail() {
		return REMOVING_MAIN_FROM_TAIL.get();
	}

	static boolean isRemovingTailFromMain() {
		return REMOVING_TAIL_FROM_MAIN.get();
	}

	static BlockPos getTailPos(BlockPos pos, BlockState state) {
		return pos.relative(state.getValue(FACING).getOpposite());
	}

	Direction getPlacementFacing(BlockPlaceContext context) {
		Direction forcedFacing = FORCED_PLACEMENT_FACING.get();
		if (forcedFacing != null)
			return forcedFacing;
		Direction preferred = getPreferredHorizontalFacing(context);
		if (preferred != null && (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()))
			return preferred.getOpposite();
		return context.getHorizontalDirection().getOpposite();
	}

	static void setForcedPlacementFacing(Direction facing) {
		FORCED_PLACEMENT_FACING.set(facing);
	}

	static void clearForcedPlacementFacing() {
		FORCED_PLACEMENT_FACING.remove();
	}

	private static void removeTail(Level level, BlockPos pos, BlockState state) {
		BlockPos tailPos = getTailPos(pos, state);
		BlockState tailState = level.getBlockState(tailPos);
		if (!SpiderAssemblyTableCogBlock.isValidCogFor(level, tailPos, state))
			return;
		REMOVING_TAIL_FROM_MAIN.set(true);
		level.setBlock(tailPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
		REMOVING_TAIL_FROM_MAIN.set(false);
	}
}
