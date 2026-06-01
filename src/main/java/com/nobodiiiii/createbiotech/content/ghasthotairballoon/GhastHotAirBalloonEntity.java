package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

public class GhastHotAirBalloonEntity extends OrientedContraptionEntity {

	private static final String PERSISTED_SEAT_INDEX_TAG = "CreateBiotechGhastBalloonSeatIndex";
	private static final String PERSISTED_VEHICLE_TAG = "CreateBiotechGhastBalloonVehicle";
	private static final String STEERING_YAW_TAG = "CreateBiotechGhastBalloonSteeringYaw";

	private static final double FORWARD_ACCELERATION = 0.04d;
	private static final double BACKWARD_ACCELERATION = 0.02d;
	private static final double VERTICAL_ACCELERATION = 0.03d;
	private static final double HORIZONTAL_DRAG = 0.9d;
	private static final double VERTICAL_DRAG = 0.85d;
	private static final double MAX_HORIZONTAL_SPEED = 0.35d;
	private static final double MAX_VERTICAL_SPEED = 0.25d;
	private static final float TURN_ACCELERATION = 1.0f;
	private static final float TURN_BRAKE = 4.0f;
	private static final float TURN_DIRECTION_CHANGE_BRAKE = 6.0f;
	private static final float MAX_TURN_SPEED = 6.0f;
	private static final float MAX_CONTRAPTION_YAW_STEP = MAX_TURN_SPEED;
	private static final float TURN_STOP_EPSILON = 0.05f;
	private static final float TURN_DIRECTION_EPSILON = 0.25f;
	private static final double MOTION_EPSILON = 1.0E-4d;
	private static final int INPUT_TIMEOUT_TICKS = 8;
	private static final int MAGNET_TIMEOUT_TICKS = 12;
	private static final double MAGNET_ARRIVAL_DEADZONE_SQR = 0.01d;
	private static final double MAGNET_BRAKE_DISTANCE = 2.5d;

	private float deltaRotation;
	private float steeringYaw;
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
	public void startAtYaw(float yaw) {
		super.startAtYaw(yaw);
		steeringYaw = AngleHelper.wrapAngle180(yaw);
	}

	@Override
	protected void readAdditional(CompoundTag compound, boolean spawnPacket) {
		super.readAdditional(compound, spawnPacket);
		steeringYaw = compound.contains(STEERING_YAW_TAG, Tag.TAG_FLOAT)
			? AngleHelper.wrapAngle180(compound.getFloat(STEERING_YAW_TAG))
			: AngleHelper.wrapAngle180(yaw);
	}

	@Override
	protected void writeAdditional(CompoundTag compound, boolean spawnPacket) {
		super.writeAdditional(compound, spawnPacket);
		compound.putFloat(STEERING_YAW_TAG, steeringYaw);
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

		if (!level().isClientSide)
			cachePassengerSeatMappings();

		if (ghast != null)
			syncGhastYawToBalloon(ghast);
		if (!inputLeft && !inputRight && deltaRotation == 0)
			steeringYaw = AngleHelper.wrapAngle180(yaw);
	}

	public void setMagnetTarget(BlockPos pos) {
		magnetTargetPos = pos;
		magnetExpireTicks = MAGNET_TIMEOUT_TICKS;
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
		steeringYaw = AngleHelper.wrapAngle180(yaw);
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
		inputTimeout = INPUT_TIMEOUT_TICKS;
		return true;
	}

	@Override
	public void stopControlling(BlockPos controlsLocalPos) {
		super.stopControlling(controlsLocalPos);
		clearInputs();
		deltaRotation = 0;
		steeringYaw = AngleHelper.wrapAngle180(yaw);
		inputTimeout = 0;
	}

	@Override
	protected boolean updateOrientation(boolean rotationLock, boolean wasStalled, Entity riding, boolean isOnCoupling) {
		if (!(riding instanceof Ghast ghast) || !ghast.isAlive())
			return super.updateOrientation(rotationLock, wasStalled, riding, isOnCoupling);

		prevYaw = yaw;
		prevPitch = pitch;
		pitch = 0;

		Vec3 locationDiff = riding.position()
			.subtract(riding.xo, riding.yo, riding.zo);
		float movementApproach = updateYawFromMovement(locationDiff, riding.getDeltaMovement());
		if (!Float.isNaN(movementApproach))
			return Math.abs(movementApproach) > 0.01f;

		targetYaw = AngleHelper.wrapAngle180(ghast.getYRot());
		yaw = targetYaw;
		return Math.abs(AngleHelper.getShortestAngleDiff(prevYaw, yaw)) > 0.01f;
	}

	private float updateYawFromMovement(Vec3 locationDiff, Vec3 deltaMovement) {
		Vec3 horizontalLocationDiff = new Vec3(locationDiff.x, 0, locationDiff.z);
		Vec3 horizontalMotion = horizontalLocationDiff.lengthSqr() > MOTION_EPSILON
			? horizontalLocationDiff
			: new Vec3(deltaMovement.x, 0, deltaMovement.z);
		if (horizontalMotion.lengthSqr() <= MOTION_EPSILON)
			return Float.NaN;

		targetYaw = yawFromVector(horizontalMotion.normalize());
		if (targetYaw < 0)
			targetYaw += 360;
		if (yaw < 0)
			yaw += 360;

		float approach;
		if (horizontalLocationDiff.lengthSqr() > MOTION_EPSILON) {
			float yawHint = AngleHelper.getShortestAngleDiff(yaw, yawFromVector(horizontalLocationDiff));
			approach = AngleHelper.getShortestAngleDiff(yaw, targetYaw, yawHint);
		} else {
			approach = AngleHelper.getShortestAngleDiff(yaw, targetYaw);
		}

		approach = Mth.clamp(approach, -MAX_CONTRAPTION_YAW_STEP, MAX_CONTRAPTION_YAW_STEP);
		yaw = AngleHelper.wrapAngle180(yaw + approach);
		targetYaw = AngleHelper.wrapAngle180(targetYaw);

		if (Math.abs(AngleHelper.getShortestAngleDiff(yaw, targetYaw)) < 0.5f)
			yaw = targetYaw;

		return approach;
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

		Vec3 movement = ghast.getDeltaMovement().multiply(HORIZONTAL_DRAG, VERTICAL_DRAG, HORIZONTAL_DRAG);

		float desiredTurnSpeed = 0;
		if (inputLeft != inputRight)
			desiredTurnSpeed = inputRight ? MAX_TURN_SPEED : -MAX_TURN_SPEED;
		deltaRotation = updateTurnSpeed(deltaRotation, desiredTurnSpeed);

		steeringYaw = AngleHelper.wrapAngle180(steeringYaw + deltaRotation);
		applyYaw(ghast, steeringYaw);

		double thrust = 0;
		if (inputForward)
			thrust += FORWARD_ACCELERATION;
		if (inputBackward)
			thrust -= BACKWARD_ACCELERATION;
		if (thrust != 0)
			movement = movement.add(Vec3.directionFromRotation(0, steeringYaw).scale(thrust));

		double verticalThrust = 0;
		if (inputUp)
			verticalThrust += VERTICAL_ACCELERATION;
		if (inputDown)
			verticalThrust -= VERTICAL_ACCELERATION;
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
		double horizSpeed = Math.min(MAX_HORIZONTAL_SPEED, horizDist / MAGNET_BRAKE_DISTANCE * MAX_HORIZONTAL_SPEED);
		double vertSpeed = Mth.clamp(delta.y / MAGNET_BRAKE_DISTANCE * MAX_VERTICAL_SPEED,
			-MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);

		double mx = horizDist > 1.0E-6 ? delta.x / horizDist * horizSpeed : 0;
		double mz = horizDist > 1.0E-6 ? delta.z / horizDist * horizSpeed : 0;
		Vec3 movement = clampMovement(new Vec3(mx, vertSpeed, mz));

		deltaRotation = 0;
		if (horizDist > 1.0E-6) {
			float targetYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
			steeringYaw = AngleHelper.wrapAngle180(targetYaw);
			applyYaw(ghast, steeringYaw);
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
			float nextTurnSpeed = Mth.approach(currentTurnSpeed, 0, TURN_BRAKE);
			return Math.abs(nextTurnSpeed) < TURN_STOP_EPSILON ? 0 : nextTurnSpeed;
		}

		if (currentTurnSpeed != 0 && Math.signum(currentTurnSpeed) != Math.signum(desiredTurnSpeed)) {
			float brakedTurnSpeed = Mth.approach(currentTurnSpeed, 0, TURN_DIRECTION_CHANGE_BRAKE);
			if (Math.abs(brakedTurnSpeed) >= TURN_DIRECTION_EPSILON)
				return brakedTurnSpeed;
			currentTurnSpeed = 0;
		}

		return Mth.approach(currentTurnSpeed, desiredTurnSpeed, TURN_ACCELERATION);
	}

	private void syncGhastYawToBalloon(Ghast ghast) {
		float previousYaw = AngleHelper.wrapAngle180(prevYaw);
		float currentYaw = AngleHelper.wrapAngle180(yaw);
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
		if (horizontalSpeed > MAX_HORIZONTAL_SPEED) {
			double scale = MAX_HORIZONTAL_SPEED / horizontalSpeed;
			movement = new Vec3(movement.x * scale, movement.y, movement.z * scale);
		}

		double verticalSpeed = Mth.clamp(movement.y, -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);
		movement = new Vec3(movement.x, verticalSpeed, movement.z);

		double x = Math.abs(movement.x) < MOTION_EPSILON ? 0 : movement.x;
		double y = Math.abs(movement.y) < MOTION_EPSILON ? 0 : movement.y;
		double z = Math.abs(movement.z) < MOTION_EPSILON ? 0 : movement.z;
		return new Vec3(x, y, z);
	}

	public static EntityType.Builder<?> build(EntityType.Builder<?> builder) {
		@SuppressWarnings("unchecked")
		EntityType.Builder<GhastHotAirBalloonEntity> entityBuilder = (EntityType.Builder<GhastHotAirBalloonEntity>) builder;
		return entityBuilder.sized(5f, 1f).fireImmune();
	}
}
