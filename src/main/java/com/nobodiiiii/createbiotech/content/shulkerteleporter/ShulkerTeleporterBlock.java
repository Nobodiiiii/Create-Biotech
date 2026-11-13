package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.foundation.block.CBMultiBlockLifecycle;
import com.nobodiiiii.createbiotech.foundation.block.CBWrenchHelper;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

public class ShulkerTeleporterBlock extends KineticBlock
	implements IBE<ShulkerTeleporterBlockEntity>, ICogWheel, CBMultiBlockLifecycle.Part, IProxyHoveringInformation {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);

	public static final int BOTTOM = 0;
	public static final int MIDDLE = 1;
	public static final int TOP = 2;

	private static final VoxelShape FULL_OUTLINE = Block.box(0, 0, 0, 16, 16, 16);
	private static final VoxelShape OUTLINE = Block.box(1, 0, 1, 15, 16, 15);
	private static final VoxelShape BOTTOM_COLLISION = Block.box(1, -1, 1, 15, 0, 15);
	private static final VoxelShape TOP_COLLISION = Block.box(1, 0, 1, 15, 16, 15);
	private static final VoxelShape EMPTY = Block.box(0, 0, 0, 0, 0, 0);

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
		// Placed on both sides like vanilla doors do, so the client is never left
		// holding a lone base block until the server's updates arrive.
		BlockState partState = defaultBlockState().setValue(FACING, state.getValue(FACING));
		level.setBlock(pos.above(), partState.setValue(PART, MIDDLE), Block.UPDATE_ALL);
		level.setBlock(pos.above(2), partState.setValue(PART, TOP), Block.UPDATE_ALL);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
		Player player, InteractionHand hand, BlockHitResult hit) {
		if (CBWrenchHelper.isWrench(stack))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		InteractionResult result = interact(state, level, pos, player);
		return result.consumesAction()
			? ItemInteractionResult.sidedSuccess(level.isClientSide)
			: ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hit) {
		return interact(state, level, pos, player);
	}

	private InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player) {
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
		serverPlayer.openMenu(teleporter, teleporter::sendToMenu);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos clickedPos = context.getClickedPos();
		BlockPos bottomPos = getBottomPos(clickedPos, state);
		if (!isCompleteStructure(level, bottomPos))
			return InteractionResult.PASS;
		if (context.getClickedFace().getAxis() != Direction.Axis.Y)
			return InteractionResult.PASS;

		Direction rotatedFacing = level.getBlockState(bottomPos).getValue(FACING)
			.getClockWise(context.getClickedFace().getAxis());
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		for (int part = BOTTOM; part <= TOP; part++) {
			BlockPos partPos = bottomPos.above(part);
			BlockState partState = level.getBlockState(partPos)
				.setValue(FACING, rotatedFacing);
			level.setBlock(partPos, partState, Block.UPDATE_ALL);
		}
		IWrenchable.playRotateSound(level, clickedPos);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos clickedPos = context.getClickedPos();
		BlockPos bottomPos = getBottomPos(clickedPos, state);
		if (!isCompleteStructure(level, bottomPos))
			return InteractionResult.PASS;
		if (!(level instanceof ServerLevel serverLevel))
			return InteractionResult.SUCCESS;

		Player player = context.getPlayer();
		BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, clickedPos, state, player);
		NeoForge.EVENT_BUS.post(event);
		if (event.isCanceled())
			return InteractionResult.SUCCESS;

		BlockState bottomState = level.getBlockState(bottomPos);
		if (player != null && !player.isCreative()) {
			Block.getDrops(bottomState, serverLevel, bottomPos, level.getBlockEntity(bottomPos), player,
				context.getItemInHand()).forEach(player.getInventory()::placeItemBackInInventory);
		}
		bottomState.spawnAfterBreak(serverLevel, bottomPos, ItemStack.EMPTY, true);
		// The drops were handed to the player above, so the base goes without any of
		// its own; the remaining parts follow on the scheduled structure check.
		level.destroyBlock(bottomPos, false);
		IWrenchable.playRemoveSound(level, bottomPos);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, level, pos, oldState, isMoving);
		// Catches parts that arrive without going through setPlacedBy, such as a
		// /setblock of a single segment, which would otherwise linger as a ghost block.
		scheduleStructureCheck(level, pos, state);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
		BlockPos pos, BlockPos neighborPos) {
		// Deferred to the scheduled tick so the check never runs on the client and a
		// burst of neighbour updates collapses into a single validation.
		scheduleStructureCheck(level, pos, state);
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (isValidStructure(level, pos, state))
			return;
		removeStructure(level, pos, state);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		// Survival breaks let the scheduled teardown destroy the base so its loot table
		// decides what drops. Creative has to take the base out itself, without drops -
		// without this, breaking a segment in creative handed out a free teleporter.
		if (!level.isClientSide && state.getValue(PART) != BOTTOM && player.isCreative()) {
			BlockPos bottomPos = getBottomPos(pos, state);
			if (isPartAt(level, bottomPos, BOTTOM))
				CBMultiBlockLifecycle.removeAnchorInCreative(level, bottomPos, player);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (state.getBlock() == newState.getBlock())
			return;

		if (state.getValue(PART) == TOP && level.getBlockEntity(pos) instanceof ShulkerTeleporterBlockEntity teleporter)
			teleporter.unregisterAddress();

		scheduleStructureCheck(level, pos, state);

		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(PART) == TOP ? OUTLINE : FULL_OUTLINE;
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
	public BlockPos getMultiBlockAnchor(BlockPos pos, BlockState state) {
		return getBottomPos(pos, state);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
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
	public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
		Player player) {
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

	@Override
	public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
		return getTopPos(pos, state);
	}

	public static BlockPos getBottomPos(BlockPos pos, BlockState state) {
		return pos.below(state.getValue(PART));
	}

	public static BlockPos getTopPos(BlockPos pos, BlockState state) {
		return getBottomPos(pos, state).above(TOP);
	}

	private static boolean canPlaceAt(Level level, BlockPos pos, BlockPlaceContext context) {
		if (pos.getY() + TOP >= level.getMaxBuildHeight())
			return false;
		for (int part = BOTTOM + 1; part <= TOP; part++) {
			BlockPos partPos = pos.above(part);
			if (!level.getWorldBorder().isWithinBounds(partPos))
				return false;
			if (!level.getBlockState(partPos).canBeReplaced(context))
				return false;
		}
		return true;
	}

	private static boolean isValidStructure(LevelReader level, BlockPos pos, BlockState state) {
		BlockPos bottom = getBottomPos(pos, state);
		for (int part = BOTTOM; part <= TOP; part++) {
			BlockPos partPos = bottom.above(part);
			// An unloaded position is not evidence of a broken teleporter, and reading
			// it would force its chunk in from disk on a block-update path.
			if (!CBMultiBlockLifecycle.isLoaded(level, partPos))
				continue;
			if (!isPartAt(level, partPos, part))
				return false;
		}
		return true;
	}

	private static boolean isCompleteStructure(LevelReader level, BlockPos bottomPos) {
		for (int part = BOTTOM; part <= TOP; part++) {
			if (!isPartAt(level, bottomPos.above(part), part))
				return false;
		}
		return true;
	}

	/** Whether {@code pos} holds the given segment of a teleporter. */
	private static boolean isPartAt(LevelReader level, BlockPos pos, int part) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof ShulkerTeleporterBlock && state.getValue(PART) == part;
	}

	/**
	 * Asks the segments of this teleporter to re-check themselves next tick. While the
	 * base stands its own check covers all three, so it speaks for them; once it is
	 * gone the orphans have to clean themselves up individually.
	 */
	private void scheduleStructureCheck(LevelAccessor level, BlockPos pos, BlockState state) {
		if (level.isClientSide())
			return;

		BlockPos bottom = getBottomPos(pos, state);
		if (CBMultiBlockLifecycle.isLoaded(level, bottom) && isPartAt(level, bottom, BOTTOM)) {
			CBMultiBlockLifecycle.scheduleValidation(level, bottom, this);
			return;
		}

		for (int part = BOTTOM; part <= TOP; part++) {
			BlockPos partPos = bottom.above(part);
			if (CBMultiBlockLifecycle.isLoaded(level, partPos) && isPartAt(level, partPos, part))
				CBMultiBlockLifecycle.scheduleValidation(level, partPos, this);
		}
	}

	/**
	 * Tears down every segment of the teleporter based at this position. Only the base
	 * may drop anything, and it does so through {@code destroyBlock} so its loot table
	 * - not this method - decides what the player gets. Runs from a scheduled tick, so
	 * a second segment reaching the same conclusion finds nothing left to do.
	 */
	private static void removeStructure(Level level, BlockPos pos, BlockState state) {
		BlockPos bottom = getBottomPos(pos, state);
		boolean basePresent = isPartAt(level, bottom, BOTTOM);

		for (int part = BOTTOM + 1; part <= TOP; part++) {
			BlockPos partPos = bottom.above(part);
			if (isPartAt(level, partPos, part))
				CBMultiBlockLifecycle.removeSilently(level, partPos);
		}

		if (basePresent)
			level.destroyBlock(bottom, true);
	}
}
