package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.sync.ContraptionSeatMappingPacket;

import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

public class GhastHotAirBalloonEntity extends OrientedContraptionEntity {

	private static final String PERSISTED_SEAT_INDEX_TAG = "CreateBiotechGhastBalloonSeatIndex";
	private static final String PERSISTED_VEHICLE_TAG = "CreateBiotechGhastBalloonVehicle";
	private static final EntityDataAccessor<Float> SYNCED_YAW =
		SynchedEntityData.defineId(GhastHotAirBalloonEntity.class, EntityDataSerializers.FLOAT);

	private static final float TURN_STOP_EPSILON = 0.05f;
	private static final float TURN_DIRECTION_EPSILON = 0.25f;
	private static final double MOTION_EPSILON = 1.0E-4d;
	private static final double MAGNET_ARRIVAL_DEADZONE_SQR = 0.01d;

	private float deltaRotation;
	private int inputTimeout;
	private boolean inputForward;
	private boolean inputBackward;
	private boolean inputLeft;
	private boolean inputRight;
	private boolean inputUp;
	private boolean inputDown;

	private BlockPos magnetTargetPos;
	private int magnetExpireTicks;

	public GhastHotAirBalloonEntity(EntityType<?> type, Level level) {
		super(type, level);
	}

	public static GhastHotAirBalloonEntity create(Level level, GhastHotAirBalloonContraption contraption,
												  Direction initialOrientation) {
		GhastHotAirBalloonEntity entity =
			new GhastHotAirBalloonEntity(CBEntityTypes.GHAST_HOT_AIR_BALLOON.get(), level);
		entity.setContraption(contraption);
		entity.setInitialOrientation(initialOrientation);
		entity.startAtInitialYaw();
		return entity;
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		entityData.define(SYNCED_YAW, 0f);
	}

	@Override
	public void startAtYaw(float yaw) {
		super.startAtYaw(yaw);
		setControlledYaw(yaw);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
	}

	@Override
	protected void readAdditional(CompoundTag compound, boolean spawnPacket) {
		super.readAdditional(compound, spawnPacket);
		setControlledYaw(yaw);
	}

	@Override
	protected void writeAdditional(CompoundTag compound, boolean spawnPacket) {
		super.writeAdditional(compound, spawnPacket);
	}

	@Override
	public double getMyRidingOffset() {
		Entity vehicle = getVehicle();
		if (vehicle == null)
			return 0;
		return -getCordOffset() - GhastHotAirBalloonSeatEntity.GHAST_PASSENGER_Y_OFFSET
			- vehicle.getPassengersRidingOffset();
	}

	private double getCordOffset() {
		if (getContraption() instanceof GhastHotAirBalloonContraption gc)
			return gc.getInitialOffset();
		return 0;
	}

	@Override
	public void tick() {
		if (!level().isClientSide)
			restorePassengerSeatMappings();

		Ghast ghast = getVehicle() instanceof Ghast g && g.isAlive() ? g : null;
		if (!level().isClientSide && ghast != null) {
			ghast.setNoAi(true);
			CapturedEntityBoxHelper.markAiDisabledByMod(ghast);
			tickInputTimeout();
			tickMagnetExpiry();
			applyControlledMovement(ghast);
		}

		super.tick();

		if (!level().isClientSide) {
			restorePassengerSeatMappings();
			cachePassengerSeatMappings();
		}

		if (ghast != null)
			syncGhastYawToBalloon(ghast);
	}

	@Override
	public Vec3 getPassengerPosition(Entity passenger, float partialTicks) {
		if (getContraption() == null)
			return null;

		if (passenger instanceof OrientedContraptionEntity)
			return super.getPassengerPosition(passenger, partialTicks);

		BlockPos seat = resolvePassengerSeat(passenger);
		if (seat == null)
			return super.getPassengerPosition(passenger, partialTicks);

		return getPassengerPositionForSeat(passenger, partialTicks, seat);
	}

	public void setMagnetTarget(BlockPos pos) {
		magnetTargetPos = pos;
		magnetExpireTicks = getMagnetTimeoutTicks();
	}

	public void clearMagnetTarget() {
		magnetTargetPos = null;
		magnetExpireTicks = 0;
	}

	private void tickMagnetExpiry() {
		if (magnetTargetPos == null)
			return;
		if (magnetExpireTicks > 0)
			magnetExpireTicks--;
		if (magnetExpireTicks <= 0 || !isMagnetTargetValid()) {
			magnetTargetPos = null;
			magnetExpireTicks = 0;
		}
	}

	private boolean isMagnetTargetValid() {
		if (magnetTargetPos == null || level() == null)
			return false;
		if (!level().isLoaded(magnetTargetPos))
			return false;
		BlockEntity be = level().getBlockEntity(magnetTargetPos);
		if (!(be instanceof GhastHotAirBalloonAssemblyStationBlockEntity station))
			return false;
		return station.isReadyToAccept();
	}

	private void restorePassengerSeatMappings() {
		if (getContraption() == null || getPassengers().isEmpty())
			return;

		Map<UUID, Integer> seatMapping = getContraption().getSeatMapping();
		boolean changed = false;

		for (Entity passenger : getPassengers()) {
			if (passenger instanceof OrientedContraptionEntity)
				continue;
			if (seatMapping.containsKey(passenger.getUUID()))
				continue;

			CompoundTag data = passenger.getPersistentData();
			if (!data.contains(PERSISTED_SEAT_INDEX_TAG, Tag.TAG_INT) || !data.hasUUID(PERSISTED_VEHICLE_TAG))
				continue;
			if (!getUUID().equals(data.getUUID(PERSISTED_VEHICLE_TAG)))
				continue;

			int seatIndex = data.getInt(PERSISTED_SEAT_INDEX_TAG);
			if (seatIndex < 0 || seatIndex >= getContraption().getSeats().size())
				continue;
			if (!isSeatAvailable(seatMapping, passenger.getUUID(), seatIndex))
				continue;

			seatMapping.put(passenger.getUUID(), seatIndex);
			changed = true;
		}

		if (changed) {
			AllPackets.getChannel().send(PacketDistributor.TRACKING_ENTITY.with(() -> this),
				new ContraptionSeatMappingPacket(getId(), seatMapping));
		}
	}

	private BlockPos resolvePassengerSeat(Entity passenger) {
		if (getContraption() == null)
			return null;

		BlockPos mappedSeat = getContraption().getSeatOf(passenger.getUUID());
		if (mappedSeat != null)
			return mappedSeat;

		Integer persistedSeatIndex = getPersistedSeatIndex(passenger);
		if (persistedSeatIndex == null)
			return null;
		if (persistedSeatIndex < 0 || persistedSeatIndex >= getContraption().getSeats().size())
			return null;

		return getContraption().getSeats().get(persistedSeatIndex);
	}

	private Integer getPersistedSeatIndex(Entity passenger) {
		CompoundTag data = passenger.getPersistentData();
		if (!data.contains(PERSISTED_SEAT_INDEX_TAG, Tag.TAG_INT) || !data.hasUUID(PERSISTED_VEHICLE_TAG))
			return null;
		if (!getUUID().equals(data.getUUID(PERSISTED_VEHICLE_TAG)))
			return null;
		return data.getInt(PERSISTED_SEAT_INDEX_TAG);
	}

	private Vec3 getPassengerPositionForSeat(Entity passenger, float partialTicks, BlockPos seat) {
		AABB bb = passenger.getBoundingBox();
		double ySize = bb.getYsize();
		return toGlobalVector(Vec3.atLowerCornerOf(seat)
			.add(.5, passenger.getMyRidingOffset() + ySize - .15f, .5), partialTicks)
			.add(VecHelper.getCenterOf(BlockPos.ZERO))
			.subtract(0.5, ySize, 0.5);
	}

	private void cachePassengerSeatMappings() {
		if (getContraption() == null || getPassengers().isEmpty())
			return;

		Map<UUID, Integer> seatMapping = getContraption().getSeatMapping();
		for (Entity passenger : getPassengers()) {
			Integer seatIndex = seatMapping.get(passenger.getUUID());
			if (seatIndex == null)
				continue;

			CompoundTag data = passenger.getPersistentData();
			data.putInt(PERSISTED_SEAT_INDEX_TAG, seatIndex);
			data.putUUID(PERSISTED_VEHICLE_TAG, getUUID());
		}
	}

	private static boolean isSeatAvailable(Map<UUID, Integer> seatMapping, UUID passengerId, int seatIndex) {
		for (Map.Entry<UUID, Integer> entry : seatMapping.entrySet()) {
			if (entry.getValue() == seatIndex && !entry.getKey().equals(passengerId))
				return false;
		}
		return true;
	}

	@Override
	public boolean startControlling(BlockPos controlsLocalPos, Player player) {
		if (player == null || player.isSpectator())
			return false;
		if (!(getVehicle() instanceof Ghast ghast) || !ghast.isAlive())
			return false;
		if (!toGlobalVector(VecHelper.getCenterOf(controlsLocalPos), 1).closerThan(player.position(), 8))
			return false;
		clearInputs();
		deltaRotation = 0;
		setControlledYaw(yaw);
		inputTimeout = 0;
		return true;
	}

	@Override
	public boolean control(BlockPos controlsLocalPos, Collection<Integer> heldControls, Player player) {
		if (level().isClientSide)
			return true;
		if (player == null || player.isSpectator())
			return false;
		if (!(getVehicle() instanceof Ghast ghast) || !ghast.isAlive())
			return false;
		if (!toGlobalVector(VecHelper.getCenterOf(controlsLocalPos), 1).closerThan(player.position(), 16))
			return false;

		ghast.setNoAi(true);
		CapturedEntityBoxHelper.markAiDisabledByMod(ghast);

		inputForward = heldControls.contains(0);
		inputBackward = heldControls.contains(1);
		inputLeft = heldControls.contains(2);
		inputRight = heldControls.contains(3);
		inputUp = heldControls.contains(4);
		inputDown = heldControls.contains(5);
		inputTimeout = getInputTimeoutTicks();
		return true;
	}

	@Override
	public void stopControlling(BlockPos controlsLocalPos) {
		super.stopControlling(controlsLocalPos);
		clearInputs();
		deltaRotation = 0;
		setControlledYaw(yaw);
		inputTimeout = 0;
	}

	@Override
	protected boolean updateOrientation(boolean rotationLock, boolean wasStalled, Entity riding, boolean isOnCoupling) {
		if (!(riding instanceof Ghast ghast) || !ghast.isAlive())
			return super.updateOrientation(rotationLock, wasStalled, riding, isOnCoupling);

		prevYaw = yaw;
		prevPitch = pitch;
		pitch = 0;
		float desiredYaw = getControlledYaw();
		targetYaw = desiredYaw;
		yaw = desiredYaw;
		return Math.abs(AngleHelper.getShortestAngleDiff(prevYaw, yaw)) > 0.01f;
	}

	@Override
	public float getViewYRot(float partialTicks) {
		return -(partialTicks == 1.0F ? yaw : AngleHelper.angleLerp(partialTicks, prevYaw, yaw));
	}

	@Override
	public float getViewXRot(float partialTicks) {
		return pitch;
	}

	private void tickInputTimeout() {
		if (inputTimeout > 0) {
			inputTimeout--;
			return;
		}
		clearInputs();
	}

	private void clearInputs() {
		inputForward = false;
		inputBackward = false;
		inputLeft = false;
		inputRight = false;
		inputUp = false;
		inputDown = false;
	}

	private void applyControlledMovement(Ghast ghast) {
		if (magnetTargetPos != null) {
			applyMagnetMovement(ghast);
			return;
		}

		Vec3 movement = ghast.getDeltaMovement().multiply(getHorizontalDrag(), getVerticalDrag(), getHorizontalDrag());

		float desiredTurnSpeed = 0;
		if (inputLeft != inputRight)
			desiredTurnSpeed = inputRight ? getMaxTurnSpeed() : -getMaxTurnSpeed();
		deltaRotation = updateTurnSpeed(deltaRotation, desiredTurnSpeed);

		float currentYaw = getControlledYaw();
		if (deltaRotation != 0)
			currentYaw += deltaRotation;
		if (deltaRotation != 0 || movement.horizontalDistanceSqr() > MOTION_EPSILON)
			setControlledYaw(currentYaw);
		applyYaw(ghast, currentYaw);

		double thrust = 0;
		if (inputForward)
			thrust += getForwardAcceleration();
		if (inputBackward)
			thrust -= getBackwardAcceleration();
		if (thrust != 0)
			movement = movement.add(Vec3.directionFromRotation(0, currentYaw).scale(thrust));

		double verticalThrust = 0;
		if (inputUp)
			verticalThrust += getVerticalAcceleration();
		if (inputDown)
			verticalThrust -= getVerticalAcceleration();
		if (verticalThrust != 0)
			movement = movement.add(0, verticalThrust, 0);

		movement = clampMovement(movement);
		ghast.setDeltaMovement(movement);
		ghast.move(MoverType.SELF, movement);
		ghast.hasImpulse = true;
		ghast.hurtMarked = true;
	}

	private void applyMagnetMovement(Ghast ghast) {
		double targetY = magnetTargetPos.getY() + 1 + GhastHotAirBalloonSeatEntity.GHAST_PASSENGER_Y_OFFSET;
		Vec3 target = new Vec3(magnetTargetPos.getX() + 0.5, targetY, magnetTargetPos.getZ() + 0.5);
		Vec3 delta = target.subtract(ghast.position());

		if (delta.lengthSqr() < MAGNET_ARRIVAL_DEADZONE_SQR) {
			ghast.setDeltaMovement(Vec3.ZERO);
			deltaRotation = 0;
			ghast.hasImpulse = true;
			ghast.hurtMarked = true;
			return;
		}

		double horizDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		double maxHorizontalSpeed = getMaxHorizontalSpeed();
		double maxVerticalSpeed = getMaxVerticalSpeed();
		double magnetBrakeDistance = getMagnetBrakeDistance();
		double horizSpeed = Math.min(maxHorizontalSpeed, horizDist / magnetBrakeDistance * maxHorizontalSpeed);
		double vertSpeed = Mth.clamp(delta.y / magnetBrakeDistance * maxVerticalSpeed,
			-maxVerticalSpeed, maxVerticalSpeed);

		double mx = horizDist > 1.0E-6 ? delta.x / horizDist * horizSpeed : 0;
		double mz = horizDist > 1.0E-6 ? delta.z / horizDist * horizSpeed : 0;
		Vec3 movement = clampMovement(new Vec3(mx, vertSpeed, mz));

		deltaRotation = 0;
		if (horizDist > 1.0E-6) {
			float targetYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
			setControlledYaw(targetYaw);
			applyYaw(ghast, targetYaw);
		}

		ghast.setDeltaMovement(movement);
		ghast.move(MoverType.SELF, movement);
		ghast.hasImpulse = true;
		ghast.hurtMarked = true;
	}

	private static float updateTurnSpeed(float currentTurnSpeed, float desiredTurnSpeed) {
		if (Math.abs(currentTurnSpeed) < TURN_DIRECTION_EPSILON)
			currentTurnSpeed = 0;

		if (desiredTurnSpeed == 0) {
			float nextTurnSpeed = Mth.approach(currentTurnSpeed, 0, getTurnBrake());
			return Math.abs(nextTurnSpeed) < TURN_STOP_EPSILON ? 0 : nextTurnSpeed;
		}

		if (currentTurnSpeed != 0 && Math.signum(currentTurnSpeed) != Math.signum(desiredTurnSpeed)) {
			float brakedTurnSpeed = Mth.approach(currentTurnSpeed, 0, getTurnDirectionChangeBrake());
			if (Math.abs(brakedTurnSpeed) >= TURN_DIRECTION_EPSILON)
				return brakedTurnSpeed;
			currentTurnSpeed = 0;
		}

		return Mth.approach(currentTurnSpeed, desiredTurnSpeed, getTurnAcceleration());
	}

	private void setControlledYaw(float yaw) {
		if (!level().isClientSide)
			entityData.set(SYNCED_YAW, yaw);
	}

	private float getControlledYaw() {
		return entityData.get(SYNCED_YAW);
	}

	private void syncGhastYawToBalloon(Ghast ghast) {
		float previousYaw = prevYaw;
		float currentYaw = yaw;
		ghast.yRotO = previousYaw;
		ghast.setYRot(currentYaw);
		ghast.yBodyRotO = previousYaw;
		ghast.setYBodyRot(currentYaw);
		ghast.yHeadRotO = previousYaw;
		ghast.setYHeadRot(currentYaw);
	}

	private static void applyYaw(Ghast ghast, float yaw) {
		ghast.setYRot(yaw);
		ghast.setYBodyRot(yaw);
		ghast.setYHeadRot(yaw);
		ghast.yRotO = yaw;
		ghast.yBodyRotO = yaw;
		ghast.yHeadRotO = yaw;
	}

	private static Vec3 clampMovement(Vec3 movement) {
		double horizontalSpeed = movement.horizontalDistance();
		double maxHorizontalSpeed = getMaxHorizontalSpeed();
		if (horizontalSpeed > maxHorizontalSpeed) {
			double scale = maxHorizontalSpeed / horizontalSpeed;
			movement = new Vec3(movement.x * scale, movement.y, movement.z * scale);
		}

		double maxVerticalSpeed = getMaxVerticalSpeed();
		double verticalSpeed = Mth.clamp(movement.y, -maxVerticalSpeed, maxVerticalSpeed);
		movement = new Vec3(movement.x, verticalSpeed, movement.z);

		double x = Math.abs(movement.x) < MOTION_EPSILON ? 0 : movement.x;
		double y = Math.abs(movement.y) < MOTION_EPSILON ? 0 : movement.y;
		double z = Math.abs(movement.z) < MOTION_EPSILON ? 0 : movement.z;
		return new Vec3(x, y, z);
	}

	private static double getForwardAcceleration() {
		return CBConfigs.SERVER.ghastHotAirBalloon.forwardAcceleration.get();
	}

	private static double getBackwardAcceleration() {
		return CBConfigs.SERVER.ghastHotAirBalloon.backwardAcceleration.get();
	}

	private static double getVerticalAcceleration() {
		return CBConfigs.SERVER.ghastHotAirBalloon.verticalAcceleration.get();
	}

	private static double getHorizontalDrag() {
		return CBConfigs.SERVER.ghastHotAirBalloon.horizontalDrag.get();
	}

	private static double getVerticalDrag() {
		return CBConfigs.SERVER.ghastHotAirBalloon.verticalDrag.get();
	}

	private static double getMaxHorizontalSpeed() {
		return CBConfigs.SERVER.ghastHotAirBalloon.maxHorizontalSpeed.get();
	}

	private static double getMaxVerticalSpeed() {
		return CBConfigs.SERVER.ghastHotAirBalloon.maxVerticalSpeed.get();
	}

	private static float getTurnAcceleration() {
		return CBConfigs.SERVER.ghastHotAirBalloon.turnAcceleration.get().floatValue();
	}

	private static float getTurnBrake() {
		return CBConfigs.SERVER.ghastHotAirBalloon.turnBrake.get().floatValue();
	}

	private static float getTurnDirectionChangeBrake() {
		return CBConfigs.SERVER.ghastHotAirBalloon.turnDirectionChangeBrake.get().floatValue();
	}

	private static float getMaxTurnSpeed() {
		return CBConfigs.SERVER.ghastHotAirBalloon.maxTurnSpeed.get().floatValue();
	}

	private static int getInputTimeoutTicks() {
		return CBConfigs.SERVER.ghastHotAirBalloon.inputTimeoutTicks.get();
	}

	private static int getMagnetTimeoutTicks() {
		return CBConfigs.SERVER.ghastHotAirBalloon.magnetTimeoutTicks.get();
	}

	private static double getMagnetBrakeDistance() {
		return CBConfigs.SERVER.ghastHotAirBalloon.magnetBrakeDistance.get();
	}

	public static EntityType.Builder<?> build(EntityType.Builder<?> builder) {
		@SuppressWarnings("unchecked")
		EntityType.Builder<GhastHotAirBalloonEntity> entityBuilder = (EntityType.Builder<GhastHotAirBalloonEntity>) builder;
		return entityBuilder.sized(5f, 1f).fireImmune();
	}
}
