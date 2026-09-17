package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.nobodiiiii.createbiotech.foundation.block.CBMultiBlockLifecycle;
import com.nobodiiiii.createbiotech.foundation.block.CBWrenchHelper;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.foundation.render.material.CastedMaterialsApi;
import com.nobodiiiii.createbiotech.foundation.render.material.CastedMaterialsApi.MaterialSetResult;
import com.nobodiiiii.createbiotech.foundation.render.material.MaterialRenderingModule;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SpiderAssemblyTableBlock extends HorizontalKineticBlock
	implements IBE<SpiderAssemblyTableBlockEntity>, CBMultiBlockLifecycle.Part {

	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty CASING = BooleanProperty.create("casing");
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16);
	private static final ThreadLocal<Direction> FORCED_PLACEMENT_FACING = new ThreadLocal<>();

	public SpiderAssemblyTableBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
			.setValue(FACING, Direction.NORTH)
			.setValue(CASING, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = getPlacementFacing(context);
		BlockPos tailPos = context.getClickedPos().relative(facing.getOpposite());
		if (!context.getLevel().getWorldBorder().isWithinBounds(tailPos))
			return null;
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
		// Placed on both sides like vanilla doors do, so the client is never left
		// holding a table with no cog until the server's updates arrive.
		BlockState tailState = CBBlocks.SPIDER_ASSEMBLY_TABLE_COG.get()
			.defaultBlockState()
			.setValue(SpiderAssemblyTableCogBlock.FACING, state.getValue(FACING));
		level.setBlock(getTailPos(pos, state), tailState, Block.UPDATE_ALL);

		if (level.isClientSide)
			return;
		withBlockEntityDo(level, pos, be -> be.setAdvancementOwner(placer));
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
		Player player, InteractionHand hand, BlockHitResult hit) {
		if (CBWrenchHelper.isWrench(stack))
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
		if (!player.isShiftKeyDown() && player.mayBuild() && stack.is(MaterialRenderingModule.CASING_ITEMS)) {
			if (level.isClientSide)
				return ItemInteractionResult.SUCCESS;
			if (level.getBlockEntity(pos) instanceof SpiderAssemblyTableBlockEntity be) {
				MaterialSetResult installed = SpiderAssemblyCasing.install(be, stack);
				if (installed == MaterialSetResult.CHANGED && stack.getItem() instanceof BlockItem blockItem) {
					SoundType soundType = blockItem.getBlock().defaultBlockState().getSoundType(level, pos, player);
					level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS,
						(soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f);
				}
				if (installed == MaterialSetResult.CHANGED || installed == MaterialSetResult.UNCHANGED)
					return ItemInteractionResult.SUCCESS;
			}
		}
		if (!player.isShiftKeyDown() && player.mayBuild() && stack.is(CBItems.SPIDER_ASSEMBLY_TABLE.get()))
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
		InteractionResult result = openMenu(level, pos, player);
		return result.consumesAction()
			? ItemInteractionResult.sidedSuccess(level.isClientSide)
			: ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hit) {
		return openMenu(level, pos, player);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		InteractionResult removed = removeCasing(context);
		return removed.consumesAction() ? removed : super.onWrenched(state, context);
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		InteractionResult removed = removeCasing(context);
		return removed.consumesAction() ? removed : super.onSneakWrenched(state, context);
	}

	private InteractionResult removeCasing(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!(level.getBlockEntity(pos) instanceof SpiderAssemblyTableBlockEntity be))
			return InteractionResult.PASS;
		var material = CastedMaterialsApi.getMaterial(be);
		if (material.isEmpty())
			return InteractionResult.PASS;
		if (level.isClientSide)
			return InteractionResult.SUCCESS;

		SpiderAssemblyCasing.remove(be);
		level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos,
			Block.getId(BuiltInRegistries.BLOCK.get(material.orElseThrow()).defaultBlockState()));
		IWrenchable.playRemoveSound(level, pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(CASING));
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, level, pos, oldState, isMoving);
		// Catches tables that arrive without going through setPlacedBy, such as a
		// /setblock, which would otherwise sit there cogless.
		scheduleStructureCheck(level, pos, getTailPos(pos, state));
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
		BlockPos pos, BlockPos neighborPos) {
		// Deferred to the scheduled tick so the check never runs on the client and does
		// not read across the chunk border the cog may sit behind.
		scheduleStructureCheck(level, pos, getTailPos(pos, state));
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos tailPos = getTailPos(pos, state);
		if (isValidStructure(level, pos, tailPos))
			return;
		removeStructure(level, pos, tailPos);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.hasBlockEntity() || state.getBlock() == newState.getBlock())
			return;

		// Contents drop no matter who removed the table, matching vanilla containers.
		// Gating this on the drop-the-block decision used to void the inventory when a
		// creative player broke the cog.
		withBlockEntityDo(level, pos, be -> ItemHelper.dropContents(level, pos, be.getInventory()));
		scheduleStructureCheck(level, pos, getTailPos(pos, state));
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
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
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
		serverPlayer.openMenu(be, be::sendToMenu);
		return InteractionResult.SUCCESS;
	}

	/**
	 * Whether the table at {@code mainPos} and the cog at {@code tailPos} still form an
	 * intact machine. A position whose chunk is not loaded counts as intact: it is not
	 * evidence of a broken table, and reading it would force the chunk in from disk.
	 */
	static boolean isValidStructure(LevelReader level, BlockPos mainPos, BlockPos tailPos) {
		if (!CBMultiBlockLifecycle.isLoaded(level, mainPos) || !CBMultiBlockLifecycle.isLoaded(level, tailPos))
			return true;

		BlockState mainState = level.getBlockState(mainPos);
		if (!(mainState.getBlock() instanceof SpiderAssemblyTableBlock))
			return false;
		if (!getTailPos(mainPos, mainState).equals(tailPos))
			return false;
		return SpiderAssemblyTableCogBlock.isValidCogFor(level, tailPos, mainState);
	}

	/** Asks both halves to re-check themselves next tick. */
	static void scheduleStructureCheck(LevelAccessor level, BlockPos mainPos, BlockPos tailPos) {
		if (level.isClientSide())
			return;
		CBMultiBlockLifecycle.scheduleValidation(level, mainPos, CBBlocks.SPIDER_ASSEMBLY_TABLE.get());
		CBMultiBlockLifecycle.scheduleValidation(level, tailPos, CBBlocks.SPIDER_ASSEMBLY_TABLE_COG.get());
	}

	/**
	 * Tears down both halves. Only the table may drop anything, and it does so through
	 * {@code destroyBlock} so its loot table - not this method - decides what the
	 * player gets. Runs from a scheduled tick, so the other half reaching the same
	 * conclusion this tick finds nothing left to do.
	 */
	static void removeStructure(Level level, BlockPos mainPos, BlockPos tailPos) {
		BlockState tailState = level.getBlockState(tailPos);
		if (tailState.getBlock() instanceof SpiderAssemblyTableCogBlock
			&& SpiderAssemblyTableCogBlock.getMainPos(tailPos, tailState).equals(mainPos))
			CBMultiBlockLifecycle.removeSilently(level, tailPos);

		BlockState mainState = level.getBlockState(mainPos);
		if (mainState.getBlock() instanceof SpiderAssemblyTableBlock
			&& getTailPos(mainPos, mainState).equals(tailPos))
			level.destroyBlock(mainPos, true);
	}

	static BlockPos getTailPos(BlockPos pos, BlockState state) {
		return pos.relative(state.getValue(FACING).getOpposite());
	}

	@Override
	public BlockPos getMultiBlockAnchor(BlockPos pos, BlockState state) {
		return pos;
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
}
