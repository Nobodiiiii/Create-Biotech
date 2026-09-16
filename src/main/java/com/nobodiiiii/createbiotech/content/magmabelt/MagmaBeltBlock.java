package com.nobodiiiii.createbiotech.content.magmabelt;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.nobodiiiii.createbiotech.content.beltsurface.StandardItemBeltBlock;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltTransform;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltChain;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltPlacementBlock;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.api.contraption.transformable.TransformableBlock;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.nobodiiiii.createbiotech.content.magmabelt.MagmaBeltSlicer.Feedback;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.nobodiiiii.createbiotech.content.magmabelt.transport.MagmaBeltMovementHandler.TransportedEntityInfo;
import com.nobodiiiii.createbiotech.content.magmabelt.transport.MagmaBeltTunnelInteractionHandler;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.block.render.MultiPosDestructionHandler;
import com.simibubi.create.foundation.block.render.ReducedDestroyEffects;
import com.simibubi.create.foundation.item.ItemHelper;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.VecHelper;
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
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.neoforged.neoforge.items.IItemHandler;

public class MagmaBeltBlock extends HorizontalKineticBlock
	implements IBE<MagmaBeltBlockEntity>, SpecialBlockItemRequirement, TransformableBlock, ProperWaterloggedBlock,
	StandardItemBeltBlock, CBBeltPlacementBlock {

	public static final Property<BeltSlope> SLOPE = EnumProperty.create("slope", BeltSlope.class);
	public static final Property<BeltPart> PART = EnumProperty.create("part", BeltPart.class);
	public static final BooleanProperty CASING = BooleanProperty.create("casing");

	public MagmaBeltBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(SLOPE, BeltSlope.HORIZONTAL)
			.setValue(PART, BeltPart.START)
			.setValue(CASING, false)
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
		return state.getValue(CASING);
	}

	@Override
	public ItemStack createBiotech$connectorStack() {
		return new ItemStack(CBItems.MAGMA_BELT_CONNECTOR.get());
	}

	@Override
	public void createBiotech$createChain(Level level, BlockPos start, BlockPos end) {
		MagmaBeltConnectorItem.createBelts(level, start, end);
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
		return getBlockEntityOptional(world, pos).map(MagmaBeltBlockEntity::hasPulley)
			.orElse(false);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		if (state.getValue(SLOPE) == BeltSlope.SIDEWAYS)
			return Axis.Y;
		return state.getValue(HORIZONTAL_FACING)
			.getClockWise()
			.getAxis();
	}

	@Override
	public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader world, BlockPos pos,
									   Player player) {
		return new ItemStack(CBItems.MAGMA_BELT_CONNECTOR.get());
	}

	@SuppressWarnings("deprecation")
	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		List<ItemStack> drops = super.getDrops(state, builder);
		BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (blockEntity instanceof MagmaBeltBlockEntity && ((MagmaBeltBlockEntity) blockEntity).hasPulley())
			drops.addAll(AllBlocks.SHAFT.getDefaultState()
				.getDrops(builder));
		return drops;
	}

	@Override
	public void spawnAfterBreak(BlockState state, ServerLevel worldIn, BlockPos pos, ItemStack p_220062_4_, boolean b) {
		MagmaBeltBlockEntity controllerBE = MagmaBeltHelper.getControllerBE(worldIn, pos);
		if (controllerBE != null)
			controllerBE.getInventory()
				.ejectAll();
	}

	@Override
	public boolean isFlammable(BlockState state, BlockGetter world, BlockPos pos, Direction face) {
		return false;
	}

	@Override
	public void updateEntityAfterFallOn(BlockGetter worldIn, Entity entityIn) {
		super.updateEntityAfterFallOn(worldIn, entityIn);
		BlockPos entityPosition = entityIn.blockPosition();
		BlockPos beltPos = null;

		if (MagmaBeltBlock.isMagmaBelt(worldIn.getBlockState(entityPosition)))
			beltPos = entityPosition;
		else if (MagmaBeltBlock.isMagmaBelt(worldIn.getBlockState(entityPosition.below())))
			beltPos = entityPosition.below();
		if (beltPos == null)
			return;
		if (!(worldIn instanceof Level))
			return;

		entityInside(worldIn.getBlockState(beltPos), (Level) worldIn, beltPos, entityIn);
	}

	@Override
	public void entityInside(BlockState state, Level worldIn, BlockPos pos, Entity entityIn) {
		if (!canTransportObjects(state))
			return;
		if (entityIn instanceof Player player) {
			if (player.isShiftKeyDown() && !AllItems.CARDBOARD_BOOTS.isIn(player.getItemBySlot(EquipmentSlot.FEET)))
				return;
			if (player.getAbilities().flying)
				return;
		}

		if (DivingBootsItem.isWornBy(entityIn))
			return;

		MagmaBeltBlockEntity belt = MagmaBeltHelper.getSegmentBE(worldIn, pos);
		if (belt == null)
			return;
		ItemStack asItem = ItemHelper.fromItemEntity(entityIn);
		if (!asItem.isEmpty()) {
			if (worldIn.isClientSide)
				return;
			if (entityIn.getDeltaMovement().y > 0)
				return;
			Vec3 targetLocation = VecHelper.getCenterOf(pos)
				.add(0, 5 / 16f, 0);
			if (!PackageEntity.centerPackage(entityIn, targetLocation))
				return;
			if (MagmaBeltTunnelInteractionHandler.getTunnelOnPosition(worldIn, pos) != null)
				return;
			withBlockEntityDo(worldIn, pos, be -> {
				IItemHandler handler = be.getItemCapability(null);
				if (handler == null)
					return;
				ItemStack remainder = handler.insertItem(0, asItem, false);
				if (remainder.isEmpty())
					entityIn.discard();
				else if (entityIn instanceof ItemEntity itemEntity && remainder.getCount() != itemEntity.getItem().getCount())
					itemEntity.setItem(remainder);
			});
			return;
		}

		MagmaBeltBlockEntity controller = MagmaBeltHelper.getControllerBE(worldIn, pos);
		if (controller == null || controller.passengers == null)
			return;
		if (controller.passengers.containsKey(entityIn)) {
			TransportedEntityInfo info = controller.passengers.get(entityIn);
			if (info.getTicksSinceLastCollision() != 0 || pos.equals(entityIn.blockPosition()))
				info.refresh(pos, state);
		} else {
			controller.passengers.put(entityIn, new TransportedEntityInfo(pos, state));
			entityIn.setOnGround(true);
		}
	}

	public static boolean canTransportObjects(BlockState state) {
		if (!MagmaBeltBlock.isMagmaBelt(state))
			return false;
		BeltSlope slope = state.getValue(SLOPE);
		return slope != BeltSlope.VERTICAL && slope != BeltSlope.SIDEWAYS;
	}

	public static boolean isMagmaBelt(BlockState state) {
		return state.is(CBBlocks.MAGMA_BELT.get());
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level world, BlockPos pos,
		Player player, InteractionHand handIn, BlockHitResult hit) {
		InteractionResult result = interact(state, world, pos, player, handIn, heldItem, hit);
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
		InteractionHand handIn, ItemStack heldItem, BlockHitResult hit) {
		if (player.isShiftKeyDown() || !player.mayBuild())
			return InteractionResult.PASS;

		boolean isWrench = AllItems.WRENCH.isIn(heldItem);
		boolean isConnector = heldItem.is(CBItems.MAGMA_BELT_CONNECTOR.get());
		boolean isShaft = AllBlocks.SHAFT.isIn(heldItem);
		boolean isHand = heldItem.isEmpty() && handIn == InteractionHand.MAIN_HAND;

		if (isConnector)
			return MagmaBeltSlicer.useConnector(state, world, pos, player, handIn, hit, new Feedback());
		if (isWrench)
			return MagmaBeltSlicer.useWrench(state, world, pos, player, handIn, hit, new Feedback());

		MagmaBeltBlockEntity belt = MagmaBeltHelper.getSegmentBE(world, pos);
		if (belt == null)
			return InteractionResult.PASS;

		if (PackageItem.isPackage(heldItem)) {
			ItemStack toInsert = heldItem.copy();
			IItemHandler handler = belt.getItemCapability(null);
			if (handler == null)
				return InteractionResult.PASS;
			ItemStack remainder = handler.insertItem(0, toInsert, false);
			if (remainder.isEmpty()) {
				heldItem.shrink(1);
				return InteractionResult.SUCCESS;
			}
		}

		if (isHand) {
			MagmaBeltBlockEntity controllerBelt = belt.getControllerBE();
			if (controllerBelt == null)
				return InteractionResult.PASS;
			if (world.isClientSide)
				return InteractionResult.SUCCESS;
			MutableBoolean success = new MutableBoolean(false);
			controllerBelt.getInventory()
				.applyToEachWithin(belt.index + .5f, .55f, (transportedItemStack) -> {
					player.getInventory()
						.placeItemBackInInventory(transportedItemStack.stack);
					success.setTrue();
					return TransportedResult.removeItem();
				});
			if (success.isTrue())
				world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f,
					1f + world.random.nextFloat());
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

		if (AllBlocks.BRASS_CASING.isIn(heldItem)) {
			withBlockEntityDo(world, pos, be -> be.setCasingType(CasingType.BRASS));
			updateCoverProperty(world, pos, world.getBlockState(pos));

			SoundType soundType = AllBlocks.BRASS_CASING.getDefaultState()
				.getSoundType(world, pos, player);
			world.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS,
				(soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);

			return InteractionResult.SUCCESS;
		}

		if (AllBlocks.ANDESITE_CASING.isIn(heldItem)) {
			withBlockEntityDo(world, pos, be -> be.setCasingType(CasingType.ANDESITE));
			updateCoverProperty(world, pos, world.getBlockState(pos));

			SoundType soundType = AllBlocks.ANDESITE_CASING.getDefaultState()
				.getSoundType(world, pos, player);
			world.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS,
				(soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);

			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		Player player = context.getPlayer();
		BlockPos pos = context.getClickedPos();

		if (state.getValue(CASING)) {
			if (world.isClientSide)
				return InteractionResult.SUCCESS;
			withBlockEntityDo(world, pos, be -> be.setCasingType(CasingType.NONE));
			return InteractionResult.SUCCESS;
		}

		if (state.getValue(PART) == BeltPart.PULLEY) {
			if (world.isClientSide)
				return InteractionResult.SUCCESS;
			KineticBlockEntity.switchToBlockState(world, pos, state.setValue(PART, BeltPart.MIDDLE));
			if (player != null && !player.isCreative())
				player.getInventory()
					.placeItemBackInInventory(AllBlocks.SHAFT.asStack());
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(SLOPE, PART, CASING, WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public PathType getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, Mob entity) {
		return PathType.RAIL;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return MagmaBeltShapes.getShape(state);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		if (state.getBlock() != this)
			return Shapes.empty();

		VoxelShape shape = getShape(state, worldIn, pos, context);
		if (!(context instanceof EntityCollisionContext))
			return shape;

		return getBlockEntityOptional(worldIn, pos).map(be -> {
				Entity entity = ((EntityCollisionContext) context).getEntity();
				if (entity == null)
					return shape;

				MagmaBeltBlockEntity controller = be.getControllerBE();
				if (controller == null)
					return shape;
				if (controller.passengers == null || !controller.passengers.containsKey(entity))
					return MagmaBeltShapes.getCollisionShape(state);
				return shape;

			})
			.orElse(shape);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return state.getValue(CASING) ? RenderShape.MODEL : RenderShape.ENTITYBLOCK_ANIMATED;
	}

	public static void initBelt(Level world, BlockPos pos) {
		if (world.isClientSide)
			return;
		if (world instanceof ServerLevel && ((ServerLevel) world).getChunkSource()
			.getGenerator() instanceof DebugLevelSource)
			return;

		BlockState state = world.getBlockState(pos);
		if (!MagmaBeltBlock.isMagmaBelt(state))
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

		// Init belts
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

			if (blockEntity instanceof MagmaBeltBlockEntity be && MagmaBeltBlock.isMagmaBelt(currentState)) {
				be.setController(currentPos);
				be.beltLength = beltChain.size();
				be.index = index;
				// The segment's cached handler captures the old index; drop it and let the next tick rebuild.
				be.invalidateItemHandler();
				be.attachKinetics();
				be.setChanged();
				be.sendData();

				if (be.isController() && !canTransportObjects(currentState))
					be.getInventory()
						.ejectAll();
			} else {
				world.destroyBlock(currentPos, true);
				return;
			}
			index++;
		}

	}

	@Override
	public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
		super.onRemove(state, world, pos, newState, isMoving);

		if (world.isClientSide)
			return;
		if (state.getBlock() == newState.getBlock())
			return;
		if (isMoving)
			return;

		// Destroy chain
		for (boolean forward : Iterate.trueAndFalse) {
			BlockPos currentPos = nextSegmentPosition(state, pos, forward);
			if (currentPos == null)
				continue;
			if (!CBBeltChain.isLoadedInSameSpace(world, pos, currentPos))
				continue;
			BlockState currentState = world.getBlockState(currentPos);
			if (!MagmaBeltBlock.isMagmaBelt(currentState))
				continue;

			boolean hasPulley = false;
			BlockEntity blockEntity = world.getBlockEntity(currentPos);
			if (blockEntity instanceof MagmaBeltBlockEntity belt) {
				if (belt.isController())
					belt.getInventory()
						.ejectAll();

				hasPulley = belt.hasPulley();
			}

			world.removeBlockEntity(currentPos);
			BlockState shaftState = AllBlocks.SHAFT.getDefaultState()
				.setValue(BlockStateProperties.AXIS, getRotationAxis(currentState));
			world.setBlock(currentPos, ProperWaterloggedBlock.withWater(world,
				hasPulley ? shaftState : Blocks.AIR.defaultBlockState(), currentPos), Block.UPDATE_ALL);
			world.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, currentPos, Block.getId(currentState));
		}
	}

	@Override
	public BlockState updateShape(BlockState state, Direction side, BlockState p_196271_3_, LevelAccessor world,
								  BlockPos pos, BlockPos p_196271_6_) {
		updateWater(world, state, pos);
		if (side.getAxis()
			.isHorizontal())
			updateTunnelConnections(world, pos.above());
		if (side == Direction.UP)
			updateCoverProperty(world, pos, state);
		return state;
	}

	public void updateCoverProperty(LevelAccessor world, BlockPos pos, BlockState state) {
		if (world.isClientSide())
			return;
		if (state.getValue(CASING) && state.getValue(SLOPE) == BeltSlope.HORIZONTAL)
			withBlockEntityDo(world, pos, bbe -> bbe.setCovered(isBlockCoveringBelt(world, pos.above())));
	}

	public static boolean isBlockCoveringBelt(LevelAccessor world, BlockPos pos) {
		BlockState blockState = world.getBlockState(pos);
		VoxelShape collisionShape = blockState.getCollisionShape(world, pos);
		if (collisionShape.isEmpty())
			return false;
		AABB bounds = collisionShape.bounds();
		if (bounds.getXsize() < .5f || bounds.getZsize() < .5f)
			return false;
		if (bounds.minY > 0)
			return false;
		if (AllBlocks.CRUSHING_WHEEL_CONTROLLER.has(blockState))
			return false;
		if (FunnelBlock.isFunnel(blockState) && FunnelBlock.getFunnelFacing(blockState) != Direction.UP)
			return false;
		if (blockState.getBlock() instanceof BeltTunnelBlock)
			return false;
		return true;
	}

	private void updateTunnelConnections(LevelAccessor world, BlockPos pos) {
		Block tunnelBlock = world.getBlockState(pos)
			.getBlock();
		if (tunnelBlock instanceof BeltTunnelBlock)
			((BeltTunnelBlock) tunnelBlock).updateTunnel(world, pos);
	}

	public static List<BlockPos> getBeltChain(LevelAccessor world, BlockPos controllerPos) {
		return CBBeltChain.getBeltChain(world, controllerPos, 1000);
	}

	public static BlockPos nextSegmentPosition(BlockState state, BlockPos pos, boolean forward) {
		return CBBeltChain.nextSegmentPosition(state, pos, forward);
	}

	@Override
	public Class<MagmaBeltBlockEntity> getBlockEntityClass() {
		return MagmaBeltBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends MagmaBeltBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.MAGMA_BELT.get();
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
		BlockState rotate = super.rotate(state, rot);

		if (state.getValue(SLOPE) != BeltSlope.VERTICAL)
			return rotate;
		if (state.getValue(HORIZONTAL_FACING)
			.getAxisDirection() != rotate.getValue(HORIZONTAL_FACING)
			.getAxisDirection()) {
			if (state.getValue(PART) == BeltPart.START)
				return rotate.setValue(PART, BeltPart.END);
			if (state.getValue(PART) == BeltPart.END)
				return rotate.setValue(PART, BeltPart.START);
		}

		return rotate;
	}

	public BlockState transform(BlockState state, StructureTransform transform) {
		if (transform.mirror != null) {
			state = mirror(state, transform.mirror);
		}

		if (transform.rotationAxis == Direction.Axis.Y) {
			return rotate(state, transform.rotation);
		}
		return CBBeltTransform.transformInner(state, transform, SLOPE);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	public static class RenderProperties extends ReducedDestroyEffects implements MultiPosDestructionHandler {
		@Override
		public Set<BlockPos> getExtraPositions(ClientLevel level, BlockPos pos, BlockState blockState, int progress) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof MagmaBeltBlockEntity belt) {
				return new HashSet<>(MagmaBeltBlock.getBeltChain(level, belt.getController()));
			}
			return null;
		}
	}

}
