package com.nobodiiiii.createbiotech.content.biocomputing;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.foundation.block.CBMultiBlockLifecycle;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ComputerBlock extends BaseEntityBlock implements IWrenchable, CBMultiBlockLifecycle.Part {
	public static final MapCodec<ComputerBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("creative", false).forGetter(ComputerBlock::isCreative),
		propertiesCodec()
	).apply(instance, ComputerBlock::new));
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

	private static final VoxelShaper LOWER_SHAPES = VoxelShaper.forHorizontal(Shapes.or(
		box(0, 0, 0, 16, 2, 16), box(2, 2, 2, 14, 3, 14),
		box(10, 3, 10, 13, 4, 11), box(10, 3, 5, 13, 4, 6),
		box(3, 3, 10, 6, 4, 11), box(3, 3, 6, 4, 4, 10),
		box(3, 3, 5, 6, 4, 6), box(12, 3, 6, 13, 4, 10)), Direction.NORTH);
	private static final VoxelShape UPPER_SHAPE = Shapes.or(box(0, 14, 0, 16, 16, 16),
		box(2, 13, 2, 14, 14, 14), box(3, 7, 3, 13, 13, 13));

	private final boolean creative;

	public ComputerBlock(boolean creative, Properties properties) {
		super(properties);
		this.creative = creative;
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
	}

	boolean isCreative() { return creative; }

	@Override
	protected MapCodec<ComputerBlock> codec() { return CODEC; }

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos upper = context.getClickedPos().above();
		Player player = context.getPlayer();
		if (level.isOutsideBuildHeight(upper) || !level.getWorldBorder().isWithinBounds(upper)
			|| !level.getBlockState(upper).canBeReplaced() || (player != null && !level.mayInteract(player, upper)))
			return null;
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (state.getValue(HALF) == DoubleBlockHalf.UPPER)
			return level.getBlockState(pos.below()) == state.setValue(HALF, DoubleBlockHalf.LOWER);
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
		super.onPlace(state, level, pos, previous, moving);
		CBMultiBlockLifecycle.scheduleValidation(level, pos, this);
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
		BlockPos pos, BlockPos neighbourPos) {
		if (direction.getAxis().isVertical())
			CBMultiBlockLifecycle.scheduleValidation(level, pos, this);
		return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos lowerPos = getMultiBlockAnchor(pos, state);
		if (!hasPair(level, lowerPos, state.setValue(HALF, DoubleBlockHalf.LOWER)) || !state.canSurvive(level, pos))
			level.destroyBlock(pos, isLower(state));
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		if (isLower(state))
			return super.getDrops(state, params);

		Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
		if (origin == null)
			return List.of();
		ServerLevel level = params.getLevel();
		BlockPos lowerPos = BlockPos.containing(origin).below();
		BlockState lower = state.setValue(HALF, DoubleBlockHalf.LOWER);
		if (!hasPair(level, lowerPos, lower))
			return List.of();
		return super.getDrops(lower, params.withOptionalParameter(LootContextParams.BLOCK_ENTITY,
			level.getBlockEntity(lowerPos)));
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		// Player mining rolls the normal loot after removal, so resolve an intact upper half first.
		if (!level.isClientSide && !isLower(state) && !player.isCreative()
			&& hasPair(level, pos.below(), state.setValue(HALF, DoubleBlockHalf.LOWER))
			&& state.canHarvestBlock(level, pos, player))
			Block.dropResources(state, level, pos, null, player, player.getMainHandItem());
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
		if (!level.isClientSide && !replacement.is(this) && !moving && !level.restoringBlockSnapshots) {
			boolean lower = isLower(state);
			BlockPos otherPos = lower ? pos.above() : pos.below();
			BlockState other = state.setValue(HALF, lower ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
			if (lower && level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
				ItemStack box = computer.takeVillagerBox();
				if (!box.isEmpty())
					Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), box);
			}
			if (level.getBlockState(otherPos) == other)
				CBMultiBlockLifecycle.removeSilently(level, otherPos);
		}
		super.onRemove(state, level, pos, replacement, moving);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
		Player player, InteractionHand hand, BlockHitResult hit) {
		boolean installVillager = !creative && CapturedEntityBoxItem.isBox(stack)
			&& CapturedEntityBoxHelper.containsEntityType(stack, EntityType.VILLAGER);
		boolean stockLink = AllBlocks.STOCK_LINK.isIn(stack);
		if (!installVillager && !stockLink)
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		BlockPos anchor = getMultiBlockAnchor(pos, state);
		if (player.isSpectator() || !level.mayInteract(player, anchor)
			|| !hasPair(level, anchor, state.setValue(HALF, DoubleBlockHalf.LOWER)))
			return ItemInteractionResult.FAIL;
		if (!level.isClientSide && level.getBlockEntity(anchor) instanceof ComputerBlockEntity computer) {
			if (computer.mayEdit(player)) {
				if (installVillager)
					computer.installVillager(player, stack);
				else
					computer.configureStockLink(player, stack);
			} else {
				computer.showInteractionMessage(player, "stock_protected");
			}
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		BlockPos lowerPos = getMultiBlockAnchor(pos, state);
		if (player.isSpectator() || !hasPair(level, lowerPos, state.setValue(HALF, DoubleBlockHalf.LOWER))
			|| !level.mayInteract(player, lowerPos))
			return InteractionResult.PASS;
		if (player instanceof ServerPlayer serverPlayer
			&& level.getBlockEntity(lowerPos) instanceof ComputerBlockEntity computer) {
			if (!computer.mayEdit(player)) {
				computer.showInteractionMessage(player, "stock_protected");
			} else if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()
				&& computer.hasVillagerBox()) {
				player.getInventory().placeItemBackInInventory(computer.ejectVillager());
			} else {
				ComputerProgramMenu.open(serverPlayer, computer);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (!context.getClickedFace().getAxis().isVertical())
			return InteractionResult.PASS;
		Level level = context.getLevel();
		BlockPos lowerPos = getMultiBlockAnchor(context.getClickedPos(), state);
		BlockState lower = state.setValue(HALF, DoubleBlockHalf.LOWER);
		if (!hasPair(level, lowerPos, lower))
			return InteractionResult.PASS;
		if (!level.isClientSide) {
			BlockState rotated = rotate(lower, Rotation.CLOCKWISE_90);
			level.setBlock(lowerPos, rotated, Block.UPDATE_ALL);
			level.setBlock(lowerPos.above(), rotated.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
			level.updateNeighborsAt(lowerPos, this);
			level.updateNeighborsAt(lowerPos.above(), this);
			IWrenchable.playRotateSound(level, context.getClickedPos());
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public BlockPos getMultiBlockAnchor(BlockPos pos, BlockState state) { return isLower(state) ? pos : pos.below(); }

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return rotate(state, mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public PushReaction getPistonPushReaction(BlockState state) { return PushReaction.BLOCK; }

	@Override
	protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return isLower(state) ? LOWER_SHAPES.get(state.getValue(FACING)) : UPPER_SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return isLower(state) ? new ComputerBlockEntity(pos, state) : null;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide || !isLower(state))
			return null;
		return createTickerHelper(type, CBBlockEntityTypes.COMPUTER.get(),
			(world, pos, blockState, computer) -> computer.serverTick());
	}

	private static boolean isLower(BlockState state) { return state.getValue(HALF) == DoubleBlockHalf.LOWER; }

	static boolean hasPair(Level level, BlockPos pos, BlockState lower) {
		return level.getBlockState(pos) == lower && level.getBlockState(pos.above()) == lower.setValue(HALF, DoubleBlockHalf.UPPER);
	}
}
