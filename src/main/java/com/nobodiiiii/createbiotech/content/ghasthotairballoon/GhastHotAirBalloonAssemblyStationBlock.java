package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import dev.ryanhcode.sable.companion.SubLevelAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GhastHotAirBalloonAssemblyStationBlock extends BaseEntityBlock implements IWrenchable {
	public static final MapCodec<GhastHotAirBalloonAssemblyStationBlock> CODEC =
		simpleCodec(GhastHotAirBalloonAssemblyStationBlock::new);

	public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
	public GhastHotAirBalloonAssemblyStationBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends GhastHotAirBalloonAssemblyStationBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HORIZONTAL_FACING);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getClockWise());
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(HORIZONTAL_FACING, rot.rotate(state.getValue(HORIZONTAL_FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return rotate(state, mirror.getRotation(state.getValue(HORIZONTAL_FACING)));
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (context.getClickedFace().getAxis() != Direction.Axis.Y)
			return InteractionResult.PASS;
		BlockState rotated = state.setValue(HORIZONTAL_FACING,
			state.getValue(HORIZONTAL_FACING).getClockWise(context.getClickedFace().getAxis()));
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!rotated.canSurvive(level, pos))
			return InteractionResult.PASS;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		level.setBlock(pos, rotated, Block.UPDATE_ALL);
		IWrenchable.playRotateSound(level, pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GhastHotAirBalloonAssemblyStationBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
		BlockEntityType<T> type) {
		return createTickerHelper(type, CBBlockEntityTypes.GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION.get(),
			GhastHotAirBalloonAssemblyStationBlockEntity::tick);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, level, pos, oldState, isMoving);
		updatePoweredState(level, pos);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide)
			return;
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof GhastHotAirBalloonAssemblyStationBlockEntity station)
			station.setAdvancementOwner(placer);
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
		BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, neighborBlock, fromPos, isMoving);
		updatePoweredState(level, pos);
	}

	private static void updatePoweredState(Level level, BlockPos pos) {
		if (level.isClientSide)
			return;
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof GhastHotAirBalloonAssemblyStationBlockEntity station)
			station.onNeighborSignalChanged(level.hasNeighborSignal(pos));
	}

	public static boolean isSeatOccupied(Level world, BlockPos stationPos) {
		return !findSeatsAtStation(world, stationPos).isEmpty();
	}

	public static boolean canBePickedUp(Entity passenger, boolean allowContraptionPickup) {
		if (!(passenger instanceof Ghast ghast))
			return false;
		if (!ghast.isAlive())
			return false;
		if (SubLevelCompat.getTrackingOrVehicleSubLevel(ghast) != null)
			return false;
		if (ghast.isPassenger())
			return false;
		if (ghast.isVehicle()) {
			if (!allowContraptionPickup)
				return false;
			int contraptionCount = 0;
			for (Entity p : ghast.getPassengers()) {
				if (p instanceof GhastHotAirBalloonEntity gc && gc.isAlive()) {
					contraptionCount++;
				} else {
					return false;
				}
			}
			return contraptionCount == 1;
		}
		return true;
	}

	public static void sitDown(Level world, BlockPos stationPos, BlockState state, Ghast ghast) {
		if (world.isClientSide)
			return;
		if (!SubLevelCompat.isValidSpacePosition(world, stationPos))
			return;
		if (!(world.getBlockEntity(stationPos) instanceof GhastHotAirBalloonAssemblyStationBlockEntity station))
			return;

		List<GhastHotAirBalloonEntity> contraptions = new ArrayList<>();
		for (Entity p : ghast.getPassengers()) {
			if (p instanceof GhastHotAirBalloonEntity gc && gc.isAlive())
				contraptions.add(gc);
		}
		if (contraptions.size() > 1)
			return;

		SubLevelAccess stationSubLevel = SubLevelCompat.getContaining(world, stationPos);
		UUID stationSubLevelId = stationSubLevel == null ? null : stationSubLevel.getUniqueId();
		float localYaw = getFacingYaw(state);
		float worldYaw = SubLevelCompat.localYawToWorld(stationSubLevel, localYaw);
		Vec3 worldPosition = getGhastDockingWorldPosition(world, stationPos);

		GhastHotAirBalloonSeatEntity seat =
			new GhastHotAirBalloonSeatEntity(world, stationPos, stationSubLevelId);
		if (!world.addFreshEntity(seat))
			return;
		if (!ghast.startRiding(seat, true)) {
			seat.discard();
			return;
		}

		moveGhast(ghast, worldPosition, worldYaw);
		ghast.setNoAi(true);
		CapturedEntityBoxHelper.markAiDisabledByMod(ghast);
		ghast.setPersistenceRequired();
		ghast.setDeltaMovement(0, 0, 0);

		if (contraptions.isEmpty())
			return;
		for (GhastHotAirBalloonEntity gc : contraptions)
			station.dockContraption(gc, localYaw);
	}

	static float getFacingYaw(BlockState state) {
		return state.getValue(HORIZONTAL_FACING)
			.getCounterClockWise()
			.toYRot();
	}

	public static List<Ghast> findGhastsToSeat(Level world, BlockPos stationPos) {
		SubLevelAccess subLevel = SubLevelCompat.getContaining(world, stationPos);
		AABB localBounds = new AABB(stationPos.above());
		AABB worldBounds = SubLevelCompat.toWorldBounds(world, stationPos, localBounds);
		return world.getEntitiesOfClass(Ghast.class, worldBounds, ghast -> {
			if (SubLevelCompat.getTrackingOrVehicleSubLevel(ghast) != null)
				return false;
			return SubLevelCompat.toLocalBounds(subLevel, ghast.getBoundingBox()).intersects(localBounds);
		});
	}

	public static List<GhastHotAirBalloonSeatEntity> findSeatsAtStation(Level world, BlockPos stationPos) {
		SubLevelAccess subLevel = SubLevelCompat.getContaining(world, stationPos);
		Vec3 localPosition = getGhastDockingLocalPosition(stationPos);
		Vec3 center = SubLevelCompat.toWorld(subLevel, localPosition);
		List<GhastHotAirBalloonSeatEntity> seats =
			new ArrayList<>(findSeatsAround(world, stationPos, center));
		if (subLevel == null)
			return seats;

		// Block entities can tick before the bound seat receives this tick's position update.
		// Query the previous projected point as a second small box rather than one potentially
		// enormous swept AABB when a sublevel moves or teleports quickly.
		Vec3 previousCenter = SubLevelCompat.toPreviousWorld(subLevel, localPosition);
		if (previousCenter.distanceToSqr(center) < 1.0E-12)
			return seats;
		for (GhastHotAirBalloonSeatEntity seat : findSeatsAround(world, stationPos, previousCenter)) {
			if (!seats.contains(seat))
				seats.add(seat);
		}
		return seats;
	}

	private static List<GhastHotAirBalloonSeatEntity> findSeatsAround(Level world, BlockPos stationPos,
		Vec3 center) {
		AABB bounds = new AABB(center.x - 0.75, center.y - 0.75, center.z - 0.75,
			center.x + 0.75, center.y + 0.75, center.z + 0.75);
		return world.getEntitiesOfClass(GhastHotAirBalloonSeatEntity.class, bounds,
			seat -> seat.belongsTo(world, stationPos));
	}

	public static Vec3 getGhastDockingLocalPosition(BlockPos stationPos) {
		return new Vec3(stationPos.getX() + 0.5,
			stationPos.getY() + 1 + GhastHotAirBalloonSeatEntity.GHAST_PASSENGER_Y_OFFSET,
			stationPos.getZ() + 0.5);
	}

	public static Vec3 getGhastDockingWorldPosition(Level level, BlockPos stationPos) {
		return SubLevelCompat.toWorld(level, getGhastDockingLocalPosition(stationPos));
	}

	/** Sable reports metres/blocks per second; entity movement is measured per tick. */
	public static Vec3 getDockingVelocityPerTick(Level level, BlockPos stationPos) {
		SubLevelAccess subLevel = SubLevelCompat.getContaining(level, stationPos);
		return SubLevelCompat.getWorldVelocity(level, subLevel, getGhastDockingLocalPosition(stationPos))
			.scale(1 / 20d);
	}

	private static void moveGhast(Ghast ghast, Vec3 position, float yaw) {
		ghast.moveTo(position.x, position.y, position.z, yaw, 0);
		ghast.xo = position.x;
		ghast.yo = position.y;
		ghast.zo = position.z;
		ghast.xOld = position.x;
		ghast.yOld = position.y;
		ghast.zOld = position.z;
		ghast.setYRot(yaw);
		ghast.setYBodyRot(yaw);
		ghast.setYHeadRot(yaw);
		ghast.yRotO = yaw;
		ghast.yBodyRotO = yaw;
		ghast.yHeadRotO = yaw;
		ghast.setXRot(0);
		ghast.xRotO = 0;
	}
}
