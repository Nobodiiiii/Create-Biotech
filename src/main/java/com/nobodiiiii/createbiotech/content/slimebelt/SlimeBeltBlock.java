package com.nobodiiiii.createbiotech.content.slimebelt;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.content.slimebelt.transport.SlimeBeltInventory;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public class SlimeBeltBlock extends HorizontalKineticBlock implements IBE<SlimeBeltBlockEntity>, ProperWaterloggedBlock {

	public static final Property<BeltSlope> SLOPE = EnumProperty.create("slope", BeltSlope.class);
	public static final Property<BeltPart> PART = EnumProperty.create("part", BeltPart.class);

	public SlimeBeltBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(SLOPE, BeltSlope.HORIZONTAL)
			.setValue(PART, BeltPart.START)
			.setValue(WATERLOGGED, false));
	}

	@Override
	protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
		return super.areStatesKineticallyEquivalent(oldState, newState)
			&& oldState.getValue(PART) == newState.getValue(PART);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		if (face.getAxis() != getRotationAxis(state))
			return false;
		return getBlockEntityOptional(world, pos).map(SlimeBeltBlockEntity::hasPulley).orElse(false);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		if (state.getValue(SLOPE) == BeltSlope.SIDEWAYS)
			return Axis.Y;
		return state.getValue(HORIZONTAL_FACING).getClockWise().getAxis();
	}

	@Override
	public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter world, BlockPos pos, Player player) {
		return new ItemStack(CBItems.SLIME_BELT_CONNECTOR.get());
	}

	@SuppressWarnings("deprecation")
	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		List<ItemStack> drops = super.getDrops(state, builder);
		BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (blockEntity instanceof SlimeBeltBlockEntity belt && belt.hasPulley())
			drops.addAll(AllBlocks.SHAFT.getDefaultState().getDrops(builder));
		return drops;
	}

	@Override
	public void spawnAfterBreak(BlockState state, ServerLevel world, BlockPos pos, ItemStack stack, boolean b) {
		SlimeBeltBlockEntity controllerBE = SlimeBeltHelper.getControllerBE(world, pos);
		if (controllerBE != null)
			controllerBE.getInventory().ejectAll();
	}

	@Override
	public void updateEntityAfterFallOn(BlockGetter world, Entity entity) {
		super.updateEntityAfterFallOn(world, entity);
		BlockPos entityPosition = entity.blockPosition();
		BlockPos beltPos = null;

		if (world.getBlockState(entityPosition).is(CBBlocks.SLIME_BELT.get()))
			beltPos = entityPosition;
		else if (world.getBlockState(entityPosition.below()).is(CBBlocks.SLIME_BELT.get()))
			beltPos = entityPosition.below();

		if (beltPos == null || !(world instanceof Level level))
			return;
		entityInside(world.getBlockState(beltPos), level, beltPos, entity);
	}

	@Override
	public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
		if (!canTransportObjects(state))
			return;

		SlimeBeltBlockEntity belt = SlimeBeltHelper.getSegmentBE(world, pos);
		if (belt == null)
			return;

		ItemStack asItem = ItemHelper.fromItemEntity(entity);
		if (asItem.isEmpty())
			return;
		if (world.isClientSide || entity.getDeltaMovement().y > 0)
			return;

		SlimeBeltInventory beltInventory = belt.getInventory();
		SlimeBeltBlockEntity controller = belt.getControllerBE();
		if (beltInventory == null || controller == null)
			return;

		SlimeBeltHelper.Track insertTrack = getClosestCaptureTrack(entity, belt, beltInventory, controller);
		if (insertTrack == null)
			return;

		Vec3 targetLocation = getCaptureTarget(belt, beltInventory, controller, insertTrack);
		if (!PackageEntity.centerPackage(entity, targetLocation))
			return;

		ItemStack remainder = ItemHelper.limitCountToMaxStackSize(asItem, false);
		TransportedItemStack transported = new TransportedItemStack(asItem);
		beltInventory.prepareInsertedItemOnTrack(transported, belt.index, insertTrack);
		beltInventory.addItem(transported);
		controller.setChanged();
		controller.sendData();
		if (remainder.isEmpty())
			entity.discard();
		else if (entity instanceof ItemEntity itemEntity && remainder.getCount() != itemEntity.getItem().getCount())
			itemEntity.setItem(remainder);
	}

	public static boolean canTransportObjects(BlockState state) {
		return state.is(CBBlocks.SLIME_BELT.get());
	}

	private static SlimeBeltHelper.Track getClosestCaptureTrack(Entity entity, SlimeBeltBlockEntity belt, SlimeBeltInventory beltInventory,
		SlimeBeltBlockEntity controller) {
		SlimeBeltHelper.Track primary = getNearestTrack(entity.getBoundingBox()
			.getCenter(), belt, beltInventory, controller);
		SlimeBeltHelper.Track secondary = primary == SlimeBeltHelper.Track.FRONT ? SlimeBeltHelper.Track.BACK
			: SlimeBeltHelper.Track.FRONT;
		if (beltInventory.canInsertAtOnTrack(belt.index, primary))
			return primary;
		if (beltInventory.canInsertAtOnTrack(belt.index, secondary))
			return secondary;
		return null;
	}

	// Dropped items can touch either exposed belt surface, so choose the insertion point on the nearest track.
	private static Vec3 getCaptureTarget(SlimeBeltBlockEntity belt, SlimeBeltInventory beltInventory,
		SlimeBeltBlockEntity controller, SlimeBeltHelper.Track track) {
		float insertionPosition = beltInventory.getInsertionPositionForTrack(belt.index, track);
		return SlimeBeltHelper.getVectorForOffset(controller, insertionPosition);
	}

	private static SlimeBeltHelper.Track getNearestTrack(Vec3 referencePoint, SlimeBeltBlockEntity belt,
		SlimeBeltInventory beltInventory, SlimeBeltBlockEntity controller) {
		Vec3 frontTarget = getCaptureTarget(belt, beltInventory, controller, SlimeBeltHelper.Track.FRONT);
		Vec3 backTarget = getCaptureTarget(belt, beltInventory, controller, SlimeBeltHelper.Track.BACK);
		return referencePoint.distanceToSqr(backTarget) < referencePoint.distanceToSqr(frontTarget)
			? SlimeBeltHelper.Track.BACK : SlimeBeltHelper.Track.FRONT;
	}

	@Override
	public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (player.isShiftKeyDown() || !player.mayBuild())
			return InteractionResult.PASS;

		ItemStack heldItem = player.getItemInHand(hand);
		boolean isWrench = AllItems.WRENCH.isIn(heldItem);
		boolean isConnector = CBItems.isSlimeBeltConnector(heldItem);
		boolean isShaft = AllBlocks.SHAFT.isIn(heldItem);
		boolean isHand = heldItem.isEmpty() && hand == InteractionHand.MAIN_HAND;
		boolean hasWater = GenericItemEmptying.emptyItem(world, heldItem, true).getFirst().getFluid().isSame(Fluids.WATER);

		if (hasWater)
			return InteractionResult.PASS;
		if (isConnector)
			return SlimeBeltSlicer.useConnector(state, world, pos, player, hand, hit, new SlimeBeltSlicer.Feedback());
		if (isWrench)
			return SlimeBeltSlicer.useWrench(state, world, pos, player, hand, hit, new SlimeBeltSlicer.Feedback());

		SlimeBeltBlockEntity belt = SlimeBeltHelper.getSegmentBE(world, pos);
		if (belt == null)
			return InteractionResult.PASS;

		if (PackageItem.isPackage(heldItem)) {
			IItemHandler handler = belt.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
			if (handler == null)
				return InteractionResult.PASS;
			ItemStack remainder = handler.insertItem(0, heldItem.copy(), false);
			if (remainder.isEmpty()) {
				heldItem.shrink(1);
				return InteractionResult.SUCCESS;
			}
		}

		if (isHand) {
			SlimeBeltBlockEntity controllerBelt = belt.getControllerBE();
			if (controllerBelt == null)
				return InteractionResult.PASS;
			if (world.isClientSide)
				return InteractionResult.SUCCESS;

			SlimeBeltInventory beltInventory = controllerBelt.getInventory();
			SlimeBeltHelper.Track clickedTrack = getNearestTrack(hit.getLocation(), belt, beltInventory, controllerBelt);
			MutableBoolean success = new MutableBoolean(false);
			beltInventory.applyToEachWithin(belt.index + .5f, .55f, clickedTrack, transportedItemStack -> {
				player.getInventory().placeItemBackInInventory(transportedItemStack.stack);
				success.setTrue();
				return TransportedResult.removeItem();
			});

			if (success.isTrue())
				world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f,
					1f + world.random.nextFloat());
			return success.isTrue() ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}

		if (isShaft) {
			if (state.getValue(PART) != BeltPart.MIDDLE)
				return InteractionResult.PASS;
			if (world.isClientSide)
				return InteractionResult.SUCCESS;
			if (!player.isCreative())
				heldItem.shrink(1);
			KineticBlockEntity.switchToBlockState(world, pos, state.setValue(PART, BeltPart.PULLEY));
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (state.getValue(PART) != BeltPart.PULLEY)
			return InteractionResult.PASS;
		Level world = context.getLevel();
		Player player = context.getPlayer();
		BlockPos pos = context.getClickedPos();
		if (world.isClientSide)
			return InteractionResult.SUCCESS;
		KineticBlockEntity.switchToBlockState(world, pos, state.setValue(PART, BeltPart.MIDDLE));
		if (player != null && !player.isCreative())
			player.getInventory().placeItemBackInInventory(AllBlocks.SHAFT.asStack());
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(SLOPE, PART, WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockPathTypes getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, net.minecraft.world.entity.Mob entity) {
		return BlockPathTypes.RAIL;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return SlimeBeltShapes.getShape(state);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return state.getBlock() == this ? SlimeBeltShapes.getCollisionShape(state) : super.getCollisionShape(state, world, pos, context);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	public static void initBelt(Level world, BlockPos pos) {
		if (world.isClientSide)
			return;
		if (world instanceof ServerLevel serverLevel && serverLevel.getChunkSource().getGenerator() instanceof DebugLevelSource)
			return;

		BlockState state = world.getBlockState(pos);
		if (!state.is(CBBlocks.SLIME_BELT.get()))
			return;

		int limit = 1000;
		BlockPos currentPos = pos;
		while (limit-- > 0) {
			BlockState currentState = world.getBlockState(currentPos);
			if (!currentState.is(CBBlocks.SLIME_BELT.get())) {
				world.destroyBlock(pos, true);
				return;
			}
			BlockPos nextSegmentPosition = nextSegmentPosition(currentState, currentPos, false);
			if (nextSegmentPosition == null)
				break;
			if (!world.isLoaded(nextSegmentPosition))
				return;
			currentPos = nextSegmentPosition;
		}

		int index = 0;
		List<BlockPos> beltChain = getBeltChain(world, currentPos);
		if (beltChain.size() < 2) {
			world.destroyBlock(currentPos, true);
			return;
		}

		for (BlockPos beltPos : beltChain) {
			BlockEntity blockEntity = world.getBlockEntity(beltPos);
			BlockState currentState = world.getBlockState(beltPos);
			if (!(blockEntity instanceof SlimeBeltBlockEntity be) || !currentState.is(CBBlocks.SLIME_BELT.get())) {
				world.destroyBlock(currentPos, true);
				return;
			}

			be.setController(currentPos);
			be.beltLength = beltChain.size();
			be.index = index;
			be.attachKinetics();
			be.setChanged();
			be.sendData();
			index++;
		}
	}

	@Override
	public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
		super.onRemove(state, world, pos, newState, isMoving);
		if (world.isClientSide || state.getBlock() == newState.getBlock() || isMoving)
			return;

		for (boolean forward : new boolean[] {true, false}) {
			BlockPos currentPos = nextSegmentPosition(state, pos, forward);
			if (currentPos == null)
				continue;
			BlockState currentState = world.getBlockState(currentPos);
			if (!currentState.is(CBBlocks.SLIME_BELT.get()))
				continue;

			boolean hasPulley = false;
			BlockEntity blockEntity = world.getBlockEntity(currentPos);
			if (blockEntity instanceof SlimeBeltBlockEntity belt) {
				if (belt.isController())
					belt.getInventory().ejectAll();
				hasPulley = belt.hasPulley();
			}

			world.removeBlockEntity(currentPos);
			BlockState shaftState = AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, getRotationAxis(currentState));
			world.setBlock(currentPos, ProperWaterloggedBlock.withWater(world, hasPulley ? shaftState : Blocks.AIR.defaultBlockState(), currentPos), 3);
			world.levelEvent(2001, currentPos, Block.getId(currentState));
		}
	}

	@Override
	public BlockState updateShape(BlockState state, Direction side, BlockState neighbourState, LevelAccessor world,
		BlockPos pos, BlockPos neighbourPos) {
		updateWater(world, state, pos);
		return state;
	}

	public static List<BlockPos> getBeltChain(LevelAccessor world, BlockPos controllerPos) {
		List<BlockPos> positions = new LinkedList<>();
		BlockState blockState = world.getBlockState(controllerPos);
		if (!blockState.is(CBBlocks.SLIME_BELT.get()))
			return positions;

		int limit = 1000;
		BlockPos current = controllerPos;
		while (limit-- > 0 && current != null) {
			BlockState state = world.getBlockState(current);
			if (!state.is(CBBlocks.SLIME_BELT.get()))
				break;
			positions.add(current);
			current = nextSegmentPosition(state, current, true);
		}
		return positions;
	}

	public static BlockPos nextSegmentPosition(BlockState state, BlockPos pos, boolean forward) {
		Direction direction = state.getValue(HORIZONTAL_FACING);
		BeltSlope slope = state.getValue(SLOPE);
		BeltPart part = state.getValue(PART);
		int offset = forward ? 1 : -1;

		if (part == BeltPart.END && forward || part == BeltPart.START && !forward)
			return null;
		if (slope == BeltSlope.VERTICAL)
			return pos.above(direction.getAxisDirection() == AxisDirection.POSITIVE ? offset : -offset);
		pos = pos.relative(direction, offset);
		if (slope != BeltSlope.HORIZONTAL && slope != BeltSlope.SIDEWAYS)
			return pos.above(slope == BeltSlope.UPWARD ? offset : -offset);
		return pos;
	}

	@Override
	public Class<SlimeBeltBlockEntity> getBlockEntityClass() {
		return SlimeBeltBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SlimeBeltBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.SLIME_BELT.get();
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
		BlockState rotated = super.rotate(state, rot);
		if (state.getValue(SLOPE) != BeltSlope.VERTICAL)
			return rotated;
		if (state.getValue(HORIZONTAL_FACING).getAxisDirection() != rotated.getValue(HORIZONTAL_FACING).getAxisDirection()) {
			if (state.getValue(PART) == BeltPart.START)
				return rotated.setValue(PART, BeltPart.END);
			if (state.getValue(PART) == BeltPart.END)
				return rotated.setValue(PART, BeltPart.START);
		}
		return rotated;
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter reader, BlockPos pos, PathComputationType type) {
		return false;
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return fluidState(state);
	}
}
