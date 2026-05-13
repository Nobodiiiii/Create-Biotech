package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import java.util.Collection;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;

import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GhastHotAirBalloonEntity extends OrientedContraptionEntity {

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
	private static final double STATIC_TURN_SPEED_THRESHOLD = 0.01d;
	private static final float STATIC_TURN_YAW_THRESHOLD = 0.5f;
	private static final float STATIC_TURN_VISUAL_YAW_STEP = 4.0f;
	private static final float TURN_STOP_EPSILON = 0.05f;
	private static final float TURN_DIRECTION_EPSILON = 0.25f;
	private static final double MOTION_EPSILON = 1.0E-4d;
	private static final int INPUT_TIMEOUT_TICKS = 8;

	private float deltaRotation;
	private int inputTimeout;
	private boolean inputForward;
	private boolean inputBackward;
	private boolean inputLeft;
	private boolean inputRight;
	private boolean inputUp;
	private boolean inputDown;

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
		Ghast ghast = getVehicle() instanceof Ghast g && g.isAlive() ? g : null;
		if (!level().isClientSide && ghast != null) {
			ghast.setNoAi(true);
			CapturedEntityBoxHelper.markAiDisabledByMod(ghast);
			tickInputTimeout();
			applyControlledMovement(ghast);
		}

		super.tick();

		if (ghast != null)
			syncVisualYawWhileTurningInPlace(ghast, ghast.getDeltaMovement());
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
		inputTimeout = 0;
	}

	@Override
	protected boolean updateOrientation(boolean rotationLock, boolean wasStalled, Entity riding, boolean isOnCoupling) {
		if (!(riding instanceof Ghast ghast) || !ghast.isAlive())
			return super.updateOrientation(rotationLock, wasStalled, riding, isOnCoupling);

		prevYaw = yaw;
		prevPitch = pitch;
		pitch = 0;

		targetYaw = AngleHelper.wrapAngle180(ghast.getYRot());
		float approach = AngleHelper.getShortestAngleDiff(yaw, targetYaw);
		approach = Mth.clamp(approach, -MAX_CONTRAPTION_YAW_STEP, MAX_CONTRAPTION_YAW_STEP);
		yaw = AngleHelper.wrapAngle180(yaw + approach);

		if (Math.abs(AngleHelper.getShortestAngleDiff(yaw, targetYaw)) < 0.5f)
			yaw = targetYaw;

		return Math.abs(approach) > 0.01f;
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
		Vec3 movement = ghast.getDeltaMovement().multiply(HORIZONTAL_DRAG, VERTICAL_DRAG, HORIZONTAL_DRAG);

		float desiredTurnSpeed = 0;
		if (inputLeft != inputRight)
			desiredTurnSpeed = inputRight ? MAX_TURN_SPEED : -MAX_TURN_SPEED;
		deltaRotation = updateTurnSpeed(deltaRotation, desiredTurnSpeed);

		float yaw = ghast.getYRot() + deltaRotation;
		applyYaw(ghast, yaw);

		double thrust = 0;
		if (inputForward)
			thrust += FORWARD_ACCELERATION;
		if (inputBackward)
			thrust -= BACKWARD_ACCELERATION;
		if (thrust != 0)
			movement = movement.add(Vec3.directionFromRotation(0, yaw).scale(thrust));

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

	private void syncVisualYawWhileTurningInPlace(Ghast ghast, Vec3 movement) {
		if (movement.horizontalDistanceSqr() > STATIC_TURN_SPEED_THRESHOLD * STATIC_TURN_SPEED_THRESHOLD)
			return;
		float yaw = ghast.getYRot();
		boolean rotating = Math.abs(deltaRotation) >= STATIC_TURN_YAW_THRESHOLD
			|| inputLeft || inputRight
			|| Math.abs(AngleHelper.getShortestAngleDiff(ghast.yBodyRot, yaw)) >= STATIC_TURN_YAW_THRESHOLD;
		if (!rotating)
			return;

		if (level().isClientSide) {
			float bodyYaw = approachAngle(ghast.yBodyRot, yaw, STATIC_TURN_VISUAL_YAW_STEP);
			float headYaw = approachAngle(ghast.yHeadRot, yaw, STATIC_TURN_VISUAL_YAW_STEP);
			ghast.yBodyRotO = ghast.yBodyRot;
			ghast.yHeadRotO = ghast.yHeadRot;
			ghast.setYBodyRot(bodyYaw);
			ghast.setYHeadRot(headYaw);
			return;
		}

		ghast.setYBodyRot(yaw);
		ghast.setYHeadRot(yaw);
		ghast.yBodyRotO = yaw;
		ghast.yHeadRotO = yaw;
	}

	private static float approachAngle(float current, float target, float maxStep) {
		float diff = AngleHelper.getShortestAngleDiff(current, target);
		diff = Mth.clamp(diff, -maxStep, maxStep);
		return AngleHelper.wrapAngle180(current + diff);
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
