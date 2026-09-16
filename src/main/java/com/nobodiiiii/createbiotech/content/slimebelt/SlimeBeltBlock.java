package com.nobodiiiii.createbiotech.content.slimebelt;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurfaceProviderBlock;
import com.nobodiiiii.createbiotech.content.beltsurface.StandardItemBeltBlock;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltTransform;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltChain;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltPlacementBlock;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltLoopGeometry.Track;
import com.nobodiiiii.createbiotech.content.slimebelt.transport.SlimeBeltInventory;
import com.nobodiiiii.createbiotech.content.slimebelt.transport.SlimeBeltMovementHandler.TransportedEntityInfo;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.contraption.transformable.TransformableBlock;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.block.render.MultiPosDestructionHandler;
import com.simibubi.create.foundation.block.render.ReducedDestroyEffects;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
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
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.IItemHandler;

public class SlimeBeltBlock extends HorizontalKineticBlock
	implements IBE<SlimeBeltBlockEntity>, ProperWaterloggedBlock, BeltSurfaceProviderBlock, TransformableBlock,
	StandardItemBeltBlock, CBBeltPlacementBlock {

	public static final Property<BeltSlope> SLOPE = EnumProperty.create("slope", BeltSlope.class);
	public static final Property<BeltPart> PART = EnumProperty.create("part", BeltPart.class);

	public SlimeBeltBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(SLOPE, BeltSlope.HORIZONTAL)
			.setValue(PART, BeltPart.START)
			.setValue(WATERLOGGED, false));
	}

	@Override
	public Property<BeltSlope> createBiotech$slopeProperty() {
		return SLOPE;
	}

	@Override
	public Property<BeltPart> createBiotech$partProperty() {
		return PART;
	}

	@Override
	public boolean createBiotech$canTransportItems(BlockState state) {
		return canTransportObjects(state);
	}

	@Override
	public boolean createBiotech$canSupportTunnel(BlockState state) {
		return true;
	}

	@Override
	public ItemStack createBiotech$connectorStack() {
		return new ItemStack(CBItems.SLIME_BELT_CONNECTOR.get());
	}

	@Override
	public void createBiotech$createChain(Level level, BlockPos start, BlockPos end) {
		SlimeBeltConnectorItem.createBelts(level, start, end);
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
	public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader world, BlockPos pos, Player player) {
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
		if (controllerBE == null)
			return;
		SlimeBeltInventory inventory = controllerBE.getInventory();
		if (inventory != null)
			inventory.ejectAll();
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
		if (entity instanceof Player player) {
			if (player.isShiftKeyDown() && !AllItems.CARDBOARD_BOOTS.isIn(player.getItemBySlot(EquipmentSlot.FEET)))
				return;
			if (player.getAbilities().flying)
				return;
		}

		if (DivingBootsItem.isWornBy(entity))
			return;

		SlimeBeltBlockEntity belt = SlimeBeltHelper.getSegmentBE(world, pos);
		if (belt == null)
			return;

		ItemStack asItem = ItemHelper.fromItemEntity(entity);
		if (!asItem.isEmpty()) {
			if (world.isClientSide || entity.getDeltaMovement().y > 0)
				return;

			SlimeBeltInventory beltInventory = belt.getInventory();
			SlimeBeltBlockEntity controller = belt.getControllerBE();
			if (beltInventory == null || controller == null)
				return;

			Track insertTrack = getClosestCaptureTrack(entity, belt, beltInventory, controller);
			if (insertTrack == null)
				return;

			Vec3 targetLocation = getCaptureTarget(belt, controller, insertTrack);
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
			return;
		}

		if (!canTransportEntities(state))
			return;

		SlimeBeltBlockEntity controller = SlimeBeltHelper.getControllerBE(world, pos);
		if (controller == null || controller.passengers == null)
			return;
		if (controller.passengers.containsKey(entity)) {
			TransportedEntityInfo info = controller.passengers.get(entity);
			if (info.getTicksSinceLastCollision() != 0 || pos.equals(entity.blockPosition()))
				info.refresh(pos, state);
		} else {
			controller.passengers.put(entity, new TransportedEntityInfo(pos, state));
			entity.setOnGround(true);
		}
	}

	public static boolean canTransportObjects(BlockState state) {
		return state.is(CBBlocks.SLIME_BELT.get());
	}

	public static boolean canTransportEntities(BlockState state) {
		if (!state.is(CBBlocks.SLIME_BELT.get()))
			return false;
		BeltSlope slope = state.getValue(SLOPE);
		return slope != BeltSlope.VERTICAL && slope != BeltSlope.SIDEWAYS;
	}

	private static Track getClosestCaptureTrack(Entity entity, SlimeBeltBlockEntity belt, SlimeBeltInventory beltInventory,
		SlimeBeltBlockEntity controller) {
		Track nearest = getNearestTrack(entity.getBoundingBox()
			.getCenter(), belt, controller);
		// The two surfaces are physically isolated. A blocked landing on the touched
		// surface must leave the entity in the world instead of rerouting it through
		// the opposite side of the belt.
		return beltInventory.canInsertAtOnTrack(belt.index, nearest) ? nearest : null;
	}

	@Override
	public boolean isFlammable(BlockState state, BlockGetter world, BlockPos pos, Direction face) {
		return false;
	}

	// Dropped items can touch either exposed belt surface, so choose the insertion point on the nearest track.
	private static Vec3 getCaptureTarget(SlimeBeltBlockEntity belt, SlimeBeltBlockEntity controller,
		Track track) {
		return SlimeBeltHelper.getTrackCenterVector(controller, belt.index, track);
	}

	private static Track getNearestTrack(Vec3 referencePoint, SlimeBeltBlockEntity belt,
		SlimeBeltBlockEntity controller) {
		Vec3 frontTarget = getCaptureTarget(belt, controller, Track.FRONT);
		Vec3 backTarget = getCaptureTarget(belt, controller, Track.BACK);
		return referencePoint.distanceToSqr(backTarget) < referencePoint.distanceToSqr(frontTarget)
			? Track.BACK : Track.FRONT;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level world, BlockPos pos,
		Player player, InteractionHand hand, BlockHitResult hit) {
		InteractionResult result = interact(state, world, pos, player, hand, heldItem, hit);
		return result.consumesAction()
			? ItemInteractionResult.sidedSuccess(world.isClientSide)
			: ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
		BlockHitResult hit) {
		return interact(state, world, pos, player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, hit);
	}

	private InteractionResult interact(BlockState state, Level world, BlockPos pos, Player player,
		InteractionHand hand, ItemStack heldItem, BlockHitResult hit) {
		if (player.isShiftKeyDown() || !player.mayBuild())
			return InteractionResult.PASS;

		boolean isWrench = AllItems.WRENCH.isIn(heldItem);
		boolean isConnector = CBItems.isSlimeBeltConnector(heldItem);
		boolean isShaft = AllBlocks.SHAFT.isIn(heldItem);
		boolean isHand = heldItem.isEmpty() && hand == InteractionHand.MAIN_HAND;
		if (isConnector)
			return SlimeBeltSlicer.useConnector(state, world, pos, player, hand, hit, new SlimeBeltSlicer.Feedback());
		if (isWrench)
			return SlimeBeltSlicer.useWrench(state, world, pos, player, hand, hit, new SlimeBeltSlicer.Feedback());

		SlimeBeltBlockEntity belt = SlimeBeltHelper.getSegmentBE(world, pos);
		if (belt == null)
			return InteractionResult.PASS;
		SlimeBeltBlockEntity controllerBelt = belt.getControllerBE();
		if (controllerBelt == null)
			return InteractionResult.PASS;
		Track clickedTrack = getNearestTrack(hit.getLocation(), belt, controllerBelt);

		if (PackageItem.isPackage(heldItem)) {
			if (clickedTrack != Track.FRONT)
				return InteractionResult.PASS;
			IItemHandler handler = belt.getItemCapability(Direction.UP);
			if (handler == null)
				return InteractionResult.PASS;
			ItemStack remainder = handler.insertItem(0, heldItem.copy(), false);
			if (remainder.isEmpty()) {
				heldItem.shrink(1);
				return InteractionResult.SUCCESS;
			}
		}

		if (isHand) {
			if (world.isClientSide)
				return InteractionResult.SUCCESS;

			SlimeBeltInventory beltInventory = controllerBelt.getInventory();
			MutableBoolean success = tryPickupItemFromTrack(player, belt, beltInventory, clickedTrack);

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

	private static MutableBoolean tryPickupItemFromTrack(Player player, SlimeBeltBlockEntity belt,
		SlimeBeltInventory beltInventory, Track track) {
		MutableBoolean success = new MutableBoolean(false);
		beltInventory.applyToEachWithin(belt.index + .5f, .55f, track, transportedItemStack -> {
			player.getInventory().placeItemBackInInventory(transportedItemStack.stack);
			success.setTrue();
			return TransportedResult.removeItem();
		});
		return success;
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
	public PathType getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, net.minecraft.world.entity.Mob entity) {
		return PathType.RAIL;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return SlimeBeltShapes.getShape(state);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		if (state.getBlock() != this)
			return Shapes.empty();

		VoxelShape shape = getShape(state, world, pos, context);
		if (!(context instanceof EntityCollisionContext entityContext))
			return shape;

		return getBlockEntityOptional(world, pos).map(be -> {
			Entity entity = entityContext.getEntity();
			if (entity == null)
				return shape;

			SlimeBeltBlockEntity controller = be.getControllerBE();
			if (controller == null)
				return shape;
			if (controller.passengers == null || !controller.passengers.containsKey(entity))
				return SlimeBeltShapes.getCollisionShape(state);
			return shape;
		}).orElse(shape);
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

		CBBeltChain.WalkResult backward =
			CBBeltChain.walk(world, pos, false, CBBeltChain.MAX_SEGMENTS);
		if (!backward.complete()) {
			if (backward.status() == CBBeltChain.WalkStatus.INVALID
				|| backward.status() == CBBeltChain.WalkStatus.TOO_LONG)
				world.destroyBlock(pos, true);
			return;
		}
		BlockPos currentPos = backward.lastPosition();
		if (currentPos == null)
			return;

		int index = 0;
		CBBeltChain.WalkResult forward =
			CBBeltChain.walk(world, currentPos, true, CBBeltChain.MAX_SEGMENTS);
		if (!forward.complete()) {
			if (forward.status() == CBBeltChain.WalkStatus.INVALID
				|| forward.status() == CBBeltChain.WalkStatus.TOO_LONG)
				world.destroyBlock(currentPos, true);
			return;
		}
		List<BlockPos> beltChain = forward.positions();
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
			be.invalidateItemHandlers();
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
			if (!CBBeltChain.isLoadedInSameSpace(world, pos, currentPos))
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
			world.setBlock(currentPos, ProperWaterloggedBlock.withWater(world, hasPulley ? shaftState : Blocks.AIR.defaultBlockState(), currentPos), Block.UPDATE_ALL);
			world.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, currentPos, Block.getId(currentState));
		}
	}

	@Override
	public BlockState updateShape(BlockState state, Direction side, BlockState neighbourState, LevelAccessor world,
		BlockPos pos, BlockPos neighbourPos) {
		updateWater(world, state, pos);
		if (side.getAxis().isHorizontal())
			updateTunnelConnections(world, pos.above());
		return state;
	}

	private static void updateTunnelConnections(LevelAccessor world, BlockPos pos) {
		Block block = world.getBlockState(pos).getBlock();
		if (block instanceof BeltTunnelBlock tunnel)
			tunnel.updateTunnel(world, pos);
	}

	public static List<BlockPos> getBeltChain(LevelAccessor world, BlockPos controllerPos) {
		return CBBeltChain.getBeltChain(world, controllerPos, 1000);
	}

	public static BlockPos nextSegmentPosition(BlockState state, BlockPos pos, boolean forward) {
		return CBBeltChain.nextSegmentPosition(state, pos, forward);
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
	public BlockState transform(BlockState state, StructureTransform transform) {
		if (transform.mirror != null)
			state = mirror(state, transform.mirror);
		if (transform.rotationAxis == Axis.Y)
			return rotate(state, transform.rotation);
		return CBBeltTransform.transformInner(state, transform, SLOPE);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return fluidState(state);
	}

	public static class RenderProperties extends ReducedDestroyEffects implements MultiPosDestructionHandler {
		@Override
		public Set<BlockPos> getExtraPositions(ClientLevel level, BlockPos pos, BlockState blockState, int progress) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof SlimeBeltBlockEntity belt)
				return new HashSet<>(SlimeBeltBlock.getBeltChain(level, belt.getController()));
			return null;
		}
	}
}
