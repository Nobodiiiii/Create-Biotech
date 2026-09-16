package com.nobodiiiii.createbiotech.content.powerbelt;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.client.PowerBeltClientReporter;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltTransform;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltChain;
import com.nobodiiiii.createbiotech.foundation.block.CBBeltPlacementBlock;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.contraption.transformable.TransformableBlock;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltShapes;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.block.render.MultiPosDestructionHandler;
import com.simibubi.create.foundation.block.render.ReducedDestroyEffects;

import dev.ryanhcode.sable.companion.SubLevelAccess;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.Property;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PowerBeltBlock extends HorizontalKineticBlock
	implements IBE<PowerBeltBlockEntity>, ProperWaterloggedBlock, TransformableBlock, CBBeltPlacementBlock {

	public static final Property<BeltSlope> SLOPE = BeltBlock.SLOPE;
	public static final Property<BeltPart> PART = BeltBlock.PART;
	public static final BooleanProperty CASING = BeltBlock.CASING;
	private static final PathType POWER_BELT_PATH_TYPE = PathType.RAIL;

	public PowerBeltBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(SLOPE, BeltSlope.HORIZONTAL)
			.setValue(PART, BeltPart.PULLEY)
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
	public ItemStack createBiotech$connectorStack() {
		return new ItemStack(CBItems.POWER_BELT_CONNECTOR.get());
	}

	@Override
	public void createBiotech$createChain(Level level, BlockPos start, BlockPos end) {
		PowerBeltConnectorItem.createBelts(level, start, end);
	}

	@Override
	protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
		return super.areStatesKineticallyEquivalent(oldState, newState)
			&& oldState.getValue(PART) == newState.getValue(PART);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction preferredSide = getPreferredHorizontalFacing(context);
		Direction facing = preferredSide == null ? context.getHorizontalDirection()
			.getOpposite() : preferredSide.getClockWise();
		return withWater(defaultBlockState().setValue(HORIZONTAL_FACING, facing), context);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		if (face.getAxis() != getRotationAxis(state))
			return false;
		return getBlockEntityOptional(world, pos).map(PowerBeltBlockEntity::hasPulley)
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
	public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader world, BlockPos pos, Player player) {
		return new ItemStack(CBItems.POWER_BELT_CONNECTOR.get());
	}

	@SuppressWarnings("deprecation")
	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		List<ItemStack> drops = super.getDrops(state, builder);
		BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (blockEntity instanceof PowerBeltBlockEntity belt && belt.hasPulley())
			drops.addAll(AllBlocks.SHAFT.getDefaultState()
				.getDrops(builder));
		return drops;
	}

	@Override
	public void updateEntityAfterFallOn(BlockGetter world, Entity entity) {
		super.updateEntityAfterFallOn(world, entity);
		if (!(world instanceof Level level))
			return;
		BlockPos beltPos = SubLevelCompat.runIncludingEntitySpace(level, entity.position(), entity,
			(subLevel, candidatePos) -> {
				if (isPowerBelt(level.getBlockState(candidatePos)))
					return candidatePos;
				return isPowerBelt(level.getBlockState(candidatePos.below())) ? candidatePos.below() : null;
			});
		if (beltPos == null)
			return;
		if (!SubLevelCompat.canEntityInteractWith(level, beltPos, entity))
			return;

		entityInside(world.getBlockState(beltPos), level, beltPos, entity);
	}

	@Override
	public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (entity instanceof Player)
			return;
		captureSurfaceMovement(state, level, pos, entity);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (entity instanceof Player)
			captureSurfaceMovement(state, level, pos, entity);
		super.stepOn(level, pos, state, entity);
	}

	private static void captureSurfaceMovement(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (entity instanceof ItemEntity || entity instanceof ExperienceOrb)
			return;
		if (!isPowerBelt(state) || state.getValue(SLOPE) != BeltSlope.HORIZONTAL)
			return;
		if (!SubLevelCompat.canEntityInteractWith(level, pos, entity))
			return;

		SubLevelAccess beltSubLevel = SubLevelCompat.getContaining(level, pos);
		Vec3 localEntityPosition = SubLevelCompat.toLocal(beltSubLevel, entity.position());
		if (!isEntityOnBeltSurface(level, pos, entity, localEntityPosition))
			return;

		Vec3 beltAxis = Vec3.atLowerCornerOf(state.getValue(HORIZONTAL_FACING)
			.getNormal());
		Vec3 previousLocalPosition = SubLevelCompat.toPreviousLocal(beltSubLevel,
			new Vec3(entity.xo, entity.yo, entity.zo));
		Vec3 tickMovement = localEntityPosition.subtract(previousLocalPosition);
		Vec3 motion = entity.getDeltaMovement();
		double movedSurfaceSpeed = tickMovement.x * beltAxis.x + tickMovement.z * beltAxis.z;
		double motionSurfaceSpeed = beltSubLevel == null ? motion.x * beltAxis.x + motion.z * beltAxis.z : 0;
		double surfaceSpeed =
			Math.abs(movedSurfaceSpeed) >= PowerBeltBlockEntity.MIN_SURFACE_SPEED ? movedSurfaceSpeed : motionSurfaceSpeed;
		if (Math.abs(surfaceSpeed) < PowerBeltBlockEntity.MIN_SURFACE_SPEED)
			return;

		if (level.isClientSide && entity instanceof LivingEntity livingEntity)
			PowerBeltWalkAnimation.recordSurfaceMovement(livingEntity, (float) Math.abs(surfaceSpeed));

		if (Math.abs(movedSurfaceSpeed) >= PowerBeltBlockEntity.MIN_SURFACE_SPEED) {
			Vec3 correction = SubLevelCompat.localNormalToWorld(beltSubLevel, beltAxis.scale(movedSurfaceSpeed));
			entity.setPos(entity.position()
				.subtract(correction));
		}
		entity.hurtMarked = true;

		if (level.isClientSide) {
			if (entity instanceof Player player) {
				float reportedSpeed = (float) surfaceSpeed;
				PowerBeltClientReporter.reportSurfaceMovement(player, pos, reportedSpeed);
			}
			return;
		}
		if (entity instanceof LivingEntity)
			CBPackets.sendToTrackingEntity(new PowerBeltEntityAnimationPacket(entity.getId(), (float) Math.abs(surfaceSpeed)),
				entity);
		if (entity instanceof Player)
			return;

		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof PowerBeltBlockEntity powerBelt)
			powerBelt.addSurfaceMovement((float) surfaceSpeed);
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

	private InteractionResult interact(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
		ItemStack heldItem, BlockHitResult hit) {
		if (player.isShiftKeyDown() || !player.mayBuild())
			return InteractionResult.PASS;

		boolean isWrench = AllItems.WRENCH.isIn(heldItem);
		boolean isConnector = CBItems.isPowerBeltConnector(heldItem);
		if (isConnector)
			return PowerBeltSlicer.useConnector(state, world, pos, player, hand, hit, new PowerBeltSlicer.Feedback());
		if (isWrench)
			return PowerBeltSlicer.useWrench(state, world, pos, player, hand, hit, new PowerBeltSlicer.Feedback());

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

		if (state.getValue(PART) != BeltPart.PULLEY)
			return InteractionResult.PASS;
		if (world.isClientSide)
			return InteractionResult.SUCCESS;
		KineticBlockEntity.switchToBlockState(world, pos, state.setValue(PART, BeltPart.MIDDLE));
		if (player != null && !player.isCreative())
			player.getInventory()
				.placeItemBackInInventory(AllBlocks.SHAFT.asStack());
		return InteractionResult.SUCCESS;
	}

	static boolean isEntityOnBeltSurface(Level level, BlockPos pos, Entity entity) {
		if (!SubLevelCompat.canEntityInteractWith(level, pos, entity))
			return false;
		return isEntityOnBeltSurface(level, pos, entity,
			SubLevelCompat.toLocal(level, pos, entity.position()));
	}

	private static boolean isEntityOnBeltSurface(Level level, BlockPos pos, Entity entity, Vec3 localEntityPosition) {
		if (localEntityPosition.y - .25f < pos.getY())
			return false;
		return localEntityPosition.x >= pos.getX() && localEntityPosition.x < pos.getX() + 1
			&& localEntityPosition.z >= pos.getZ() && localEntityPosition.z < pos.getZ() + 1;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(SLOPE, PART, CASING, WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public PathType getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, @Nullable Mob entity) {
		return POWER_BELT_PATH_TYPE;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return BeltShapes.getShape(state);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		if (state.getBlock() != this)
			return Shapes.empty();
		return BeltShapes.getCollisionShape(state);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return state.getValue(CASING) ? RenderShape.MODEL : RenderShape.ENTITYBLOCK_ANIMATED;
	}

	public static void initBelt(Level world, BlockPos pos) {
		if (world == null || world.isClientSide)
			return;

		BlockState state = world.getBlockState(pos);
		if (!isPowerBelt(state))
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
			if (!(blockEntity instanceof PowerBeltBlockEntity be) || !isPowerBelt(currentState)) {
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
			if (!sameSpace(world, pos, currentPos))
				continue;
			BlockState currentState = world.getBlockState(currentPos);
			if (!isPowerBelt(currentState))
				continue;

			boolean hasPulley = false;
			BlockEntity blockEntity = world.getBlockEntity(currentPos);
			if (blockEntity instanceof PowerBeltBlockEntity belt)
				hasPulley = belt.hasPulley();

			world.removeBlockEntity(currentPos);
			BlockState shaftState = AllBlocks.SHAFT.getDefaultState()
				.setValue(BlockStateProperties.AXIS, getRotationAxis(currentState));
			world.setBlock(currentPos,
				ProperWaterloggedBlock.withWater(world, hasPulley ? shaftState : Blocks.AIR.defaultBlockState(), currentPos), Block.UPDATE_ALL);
			world.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, currentPos, Block.getId(currentState));
		}
	}

	@Override
	public BlockState updateShape(BlockState state, Direction side, BlockState neighbourState, LevelAccessor world,
		BlockPos pos, BlockPos neighbourPos) {
		updateWater(world, state, pos);
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

	@Override
	public Class<PowerBeltBlockEntity> getBlockEntityClass() {
		return PowerBeltBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PowerBeltBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.POWER_BELT.get();
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return fluidState(state);
	}

	public static List<BlockPos> getBeltChain(LevelAccessor world, BlockPos controllerPos) {
		return CBBeltChain.getBeltChain(world, controllerPos, 1000);
	}

	public static BlockPos nextSegmentPosition(BlockState state, BlockPos pos, boolean forward) {
		return CBBeltChain.nextSegmentPosition(state, pos, forward);
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
		BlockState rotated = super.rotate(state, rot);
		if (state.getValue(SLOPE) != BeltSlope.VERTICAL)
			return rotated;
		if (state.getValue(HORIZONTAL_FACING)
			.getAxisDirection() != rotated.getValue(HORIZONTAL_FACING)
				.getAxisDirection()) {
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

	@Nullable
	public static PowerBeltBlockEntity getSegmentBE(BlockGetter world, BlockPos pos) {
		if (world instanceof Level level && !level.isLoaded(pos))
			return null;
		BlockEntity blockEntity = world.getBlockEntity(pos);
		return blockEntity instanceof PowerBeltBlockEntity powerBelt ? powerBelt : null;
	}

	@Nullable
	public static PowerBeltBlockEntity getControllerBE(BlockGetter world, BlockPos pos) {
		PowerBeltBlockEntity segment = getSegmentBE(world, pos);
		if (segment == null)
			return null;
		if (world instanceof Level level && !SubLevelCompat.sameSpace(level, pos, segment.getController()))
			return null;
		return getSegmentBE(world, segment.getController());
	}

	private static boolean sameSpace(LevelAccessor world, BlockPos first, BlockPos second) {
		return !(world instanceof Level level) || SubLevelCompat.sameSpace(level, first, second);
	}

	public static Vec3 getBeltVector(BlockState state) {
		BeltSlope slope = state.getValue(SLOPE);
		int verticality = slope == BeltSlope.DOWNWARD ? -1 : slope == BeltSlope.UPWARD ? 1 : 0;
		Vec3 horizontalMovement = Vec3.atLowerCornerOf(state.getValue(HORIZONTAL_FACING)
			.getNormal());
		if (slope == BeltSlope.VERTICAL)
			return new Vec3(0, state.getValue(HORIZONTAL_FACING)
				.getAxisDirection()
				.getStep(), 0);
		return new Vec3(0, verticality, 0).add(horizontalMovement);
	}

	public static boolean isPowerBelt(BlockState state) {
		return state.is(CBBlocks.POWER_BELT.get());
	}

	public static class RenderProperties extends ReducedDestroyEffects implements MultiPosDestructionHandler {
		@Override
		public Set<BlockPos> getExtraPositions(ClientLevel level, BlockPos pos, BlockState blockState, int progress) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof PowerBeltBlockEntity belt)
				return new HashSet<>(PowerBeltBlock.getBeltChain(level, belt.getController()));
			return null;
		}
	}
}
