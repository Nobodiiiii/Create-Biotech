package com.nobodiiiii.createbiotech.content.powerbelt;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.client.PowerBeltClientReporter;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltShapes;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public class PowerBeltBlock extends HorizontalKineticBlock implements IBE<PowerBeltBlockEntity>, ProperWaterloggedBlock {

	public static final Property<BeltSlope> SLOPE = BeltBlock.SLOPE;
	public static final Property<BeltPart> PART = BeltBlock.PART;
	public static final BooleanProperty CASING = BeltBlock.CASING;
	private static final BlockPathTypes POWER_BELT_PATH_TYPE =
		BlockPathTypes.create("CREATE_BIOTECH_POWER_BELT", 0.0F);

	public PowerBeltBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(SLOPE, BeltSlope.HORIZONTAL)
			.setValue(PART, BeltPart.PULLEY)
			.setValue(CASING, false)
			.setValue(WATERLOGGED, false));
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
	public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter world, BlockPos pos, Player player) {
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
		BlockPos entityPosition = entity.blockPosition();
		BlockPos beltPos = null;

		if (isPowerBelt(world.getBlockState(entityPosition)))
			beltPos = entityPosition;
		else if (isPowerBelt(world.getBlockState(entityPosition.below())))
			beltPos = entityPosition.below();
		if (beltPos == null || !(world instanceof Level level))
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
		if (!isPowerBelt(state) || state.getValue(SLOPE) != BeltSlope.HORIZONTAL)
			return;
		if (!isEntityOnBeltSurface(pos, entity))
			return;

		Vec3 beltAxis = Vec3.atLowerCornerOf(state.getValue(HORIZONTAL_FACING)
			.getNormal());
		Vec3 tickMovement = entity.position()
			.subtract(entity.xo, entity.yo, entity.zo);
		Vec3 motion = entity.getDeltaMovement();
		double movedSurfaceSpeed = tickMovement.x * beltAxis.x + tickMovement.z * beltAxis.z;
		double motionSurfaceSpeed = motion.x * beltAxis.x + motion.z * beltAxis.z;
		double surfaceSpeed =
			Math.abs(movedSurfaceSpeed) >= PowerBeltBlockEntity.MIN_SURFACE_SPEED ? movedSurfaceSpeed : motionSurfaceSpeed;
		if (Math.abs(surfaceSpeed) < PowerBeltBlockEntity.MIN_SURFACE_SPEED)
			return;

		if (level.isClientSide && entity instanceof LivingEntity livingEntity)
			PowerBeltWalkAnimation.recordSurfaceMovement(livingEntity, (float) Math.abs(surfaceSpeed));

		if (Math.abs(movedSurfaceSpeed) >= PowerBeltBlockEntity.MIN_SURFACE_SPEED) {
			Vec3 correction = beltAxis.scale(movedSurfaceSpeed);
			entity.setPos(entity.getX() - correction.x, entity.getY(), entity.getZ() - correction.z);
		}
		entity.hurtMarked = true;

		if (level.isClientSide) {
			if (entity instanceof Player player) {
				float reportedSpeed = (float) surfaceSpeed;
				DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
					() -> () -> PowerBeltClientReporter.reportSurfaceMovement(player, pos, reportedSpeed));
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
	public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (player.isShiftKeyDown() || !player.mayBuild())
			return InteractionResult.PASS;

		ItemStack heldItem = player.getItemInHand(hand);
		boolean isWrench = AllItems.WRENCH.isIn(heldItem);
		boolean isConnector = CBItems.isPowerBeltConnector(heldItem);
		boolean hasWater = GenericItemEmptying.emptyItem(world, heldItem, true)
			.getFirst()
			.getFluid()
			.isSame(Fluids.WATER);

		if (hasWater)
			return InteractionResult.PASS;
		if (isConnector)
			return PowerBeltSlicer.useConnector(state, world, pos, player, hand, hit, new PowerBeltSlicer.Feedback());
		if (isWrench)
			return PowerBeltSlicer.useWrench(state, world, pos, player, hand, hit, new PowerBeltSlicer.Feedback());

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
			player.getInventory()
				.placeItemBackInInventory(AllBlocks.SHAFT.asStack());
		return InteractionResult.SUCCESS;
	}

	static boolean isEntityOnBeltSurface(BlockPos pos, Entity entity) {
		return entity.getY() - .25f >= pos.getY();
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(SLOPE, PART, CASING, WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockPathTypes getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, @Nullable Mob entity) {
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
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	public static void initBelt(Level world, BlockPos pos) {
		if (world == null || world.isClientSide)
			return;

		BlockState state = world.getBlockState(pos);
		if (!isPowerBelt(state))
			return;

		int limit = 1000;
		BlockPos currentPos = pos;
		while (limit-- > 0) {
			BlockState currentState = world.getBlockState(currentPos);
			if (!isPowerBelt(currentState)) {
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
				ProperWaterloggedBlock.withWater(world, hasPulley ? shaftState : Blocks.AIR.defaultBlockState(), currentPos), 3);
			world.levelEvent(2001, currentPos, Block.getId(currentState));
		}
	}

	@Override
	public BlockState updateShape(BlockState state, Direction side, BlockState neighbourState, LevelAccessor world,
		BlockPos pos, BlockPos neighbourPos) {
		updateWater(world, state, pos);
		return state;
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
	public boolean isPathfindable(BlockState state, BlockGetter reader, BlockPos pos, PathComputationType type) {
		return false;
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return fluidState(state);
	}

	public static List<BlockPos> getBeltChain(LevelAccessor world, BlockPos controllerPos) {
		List<BlockPos> positions = new LinkedList<>();
		BlockState blockState = world.getBlockState(controllerPos);
		if (!isPowerBelt(blockState))
			return positions;

		int limit = 1000;
		BlockPos current = controllerPos;
		while (limit-- > 0 && current != null) {
			BlockState state = world.getBlockState(current);
			if (!isPowerBelt(state))
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
		return getSegmentBE(world, segment.getController());
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
}
