package com.nobodiiiii.createbiotech.content.powerbelt;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltPart;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class PowerBeltBlockEntity extends GeneratingKineticBlockEntity {

	public static final float MIN_SURFACE_SPEED = 1.0E-4f;

	private static final int SURFACE_SPEED_DETECTION_INTERVAL = 10;
	private static final float GENERATED_RPM_THRESHOLD = 4f;
	private static final float SURFACE_SPEED_TO_RPM = 480f;
	private static final float REFERENCE_BELT_RPM = 64f;
	private static final float SU_PER_REFERENCE_SPEED = 128f;
	private static final float CAPACITY_PER_RPM = SU_PER_REFERENCE_SPEED / REFERENCE_BELT_RPM;

	public int beltLength;
	public int index;
	protected BlockPos controller;

	private long lastMovementGameTime = Long.MIN_VALUE;
	private long nextDetectionGameTime = Long.MIN_VALUE;
	private float collectedSurfaceSpeed;
	private float collectedDetectionSurfaceSpeed;
	private int collectedDetectionTicks;
	private float generatedSpeed;
	private float generatedCapacity;

	public PowerBeltBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public PowerBeltBlockEntity(BlockPos pos, BlockState state) {
		this(CBBlockEntityTypes.POWER_BELT.get(), pos, state);
	}

	@Override
	public void tick() {
		if (beltLength == 0)
			PowerBeltBlock.initBelt(level, worldPosition);

		super.tick();

		if (level == null || level.isClientSide)
			return;
		if (!getBlockState().is(CBBlocks.POWER_BELT.get()))
			return;
		if (!isController())
			return;

		sampleSurfaceMovementBefore(level.getGameTime());
	}

	public void addSurfaceMovement(float signedSurfaceSpeed) {
		PowerBeltBlockEntity controllerBE = isController() ? this : getControllerBE();
		if (controllerBE == null)
			return;
		controllerBE.collectSurfaceMovement(signedSurfaceSpeed);
	}

	private void collectSurfaceMovement(float signedSurfaceSpeed) {
		if (level == null || level.isClientSide)
			return;
		if (Math.abs(signedSurfaceSpeed) < MIN_SURFACE_SPEED)
			return;

		long gameTime = level.getGameTime();
		sampleSurfaceMovementBefore(gameTime);
		if (gameTime != lastMovementGameTime) {
			lastMovementGameTime = gameTime;
			collectedSurfaceSpeed = 0;
		}

		collectedSurfaceSpeed += signedSurfaceSpeed;
	}

	private void sampleSurfaceMovementBefore(long gameTime) {
		if (nextDetectionGameTime == Long.MIN_VALUE)
			nextDetectionGameTime = gameTime;

		while (nextDetectionGameTime < gameTime) {
			float surfaceSpeed = nextDetectionGameTime == lastMovementGameTime ? collectedSurfaceSpeed : 0;
			collectedDetectionSurfaceSpeed += surfaceSpeed;
			collectedDetectionTicks++;

			if (nextDetectionGameTime == lastMovementGameTime)
				collectedSurfaceSpeed = 0;

			nextDetectionGameTime++;

			if (collectedDetectionTicks >= SURFACE_SPEED_DETECTION_INTERVAL)
				applyDetectedSurfaceMovement();
		}
	}

	private void applyDetectedSurfaceMovement() {
		float averageSurfaceSpeed = collectedDetectionSurfaceSpeed / collectedDetectionTicks;
		float speed = surfaceSpeedToGeneratedRpm(averageSurfaceSpeed);
		if (isBelowRpmThreshold(speed))
			speed = 0;

		collectedDetectionSurfaceSpeed = 0;
		collectedDetectionTicks = 0;

		if (!shouldApplyDetectedSpeed(speed))
			return;

		float capacity = speed == 0 ? 0 : CAPACITY_PER_RPM;
		setGeneratedOutput(speed, capacity);
	}

	private static boolean isBelowRpmThreshold(float speed) {
		float absoluteSpeed = Math.abs(speed);
		return absoluteSpeed < GENERATED_RPM_THRESHOLD && !Mth.equal(absoluteSpeed, GENERATED_RPM_THRESHOLD);
	}

	private boolean shouldApplyDetectedSpeed(float speed) {
		if (Mth.equal(generatedSpeed, speed))
			return false;
		if (generatedSpeed == 0 || speed == 0)
			return true;

		float difference = Math.abs(speed - generatedSpeed);
		return difference > GENERATED_RPM_THRESHOLD || Mth.equal(difference, GENERATED_RPM_THRESHOLD);
	}

	private float surfaceSpeedToGeneratedRpm(float signedSurfaceSpeed) {
		Direction facing = getBlockState().getValue(PowerBeltBlock.HORIZONTAL_FACING);
		return -signedSurfaceSpeed * SURFACE_SPEED_TO_RPM / getDirectionFactor(facing);
	}

	private static float getDirectionFactor(Direction facing) {
		int factor = facing.getAxisDirection()
			.getStep();
		if (facing.getAxis() == Direction.Axis.X)
			factor *= -1;
		return factor;
	}

	private void setGeneratedOutput(float speed, float capacity) {
		if (Mth.equal(generatedSpeed, speed) && Mth.equal(generatedCapacity, capacity))
			return;

		generatedSpeed = speed;
		generatedCapacity = capacity;
		updateGeneratedRotation();
	}

	@Override
	public float getGeneratedSpeed() {
		if (!isController() || !getBlockState().is(CBBlocks.POWER_BELT.get()))
			return 0;
		return generatedSpeed;
	}

	@Override
	public float calculateAddedStressCapacity() {
		lastCapacityProvided = !isController() || generatedSpeed == 0 ? 0 : generatedCapacity;
		return lastCapacityProvided;
	}

	@Override
	public float calculateStressApplied() {
		lastStressApplied = 0;
		return 0;
	}

	@Override
	public void clearKineticInformation() {
		super.clearKineticInformation();
		beltLength = 0;
		index = 0;
		controller = null;
		lastMovementGameTime = Long.MIN_VALUE;
		nextDetectionGameTime = Long.MIN_VALUE;
		collectedSurfaceSpeed = 0;
		collectedDetectionSurfaceSpeed = 0;
		collectedDetectionTicks = 0;
		generatedSpeed = 0;
		generatedCapacity = 0;
	}

	public boolean hasPulley() {
		return getBlockState().is(CBBlocks.POWER_BELT.get())
			&& getBlockState().getValue(PowerBeltBlock.PART) != BeltPart.MIDDLE;
	}

	public PowerBeltBlockEntity getControllerBE() {
		if (controller == null || level == null || !level.isLoaded(controller))
			return null;
		BlockEntity be = level.getBlockEntity(controller);
		return be instanceof PowerBeltBlockEntity powerBelt ? powerBelt : null;
	}

	public void setController(BlockPos controller) {
		this.controller = controller;
	}

	public BlockPos getController() {
		return controller == null ? worldPosition : controller;
	}

	public boolean isController() {
		return controller != null && worldPosition.equals(controller);
	}

	public Direction getBeltFacing() {
		return getBlockState().getValue(PowerBeltBlock.HORIZONTAL_FACING);
	}

	public float getBeltMovementSpeed() {
		return getSpeed() / SURFACE_SPEED_TO_RPM;
	}

	@Override
	public AABB createRenderBoundingBox() {
		return isController() ? super.createRenderBoundingBox().inflate(beltLength + 1) : super.createRenderBoundingBox();
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		if (controller != null)
			compound.put("Controller", NbtUtils.writeBlockPos(controller));
		compound.putBoolean("IsController", isController());
		compound.putInt("Length", beltLength);
		compound.putInt("Index", index);
		super.write(compound, clientPacket);
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);

		if (compound.getBoolean("IsController"))
			controller = worldPosition;

		if (!wasMoved) {
			if (!isController() && compound.contains("Controller"))
				controller = NbtUtils.readBlockPos(compound.getCompound("Controller"));
			index = compound.getInt("Index");
			beltLength = compound.getInt("Length");
		}
	}

	void resetChainState() {
		detachKinetics();
		beltLength = 0;
		index = 0;
		controller = null;
		lastMovementGameTime = Long.MIN_VALUE;
		nextDetectionGameTime = Long.MIN_VALUE;
		collectedSurfaceSpeed = 0;
		collectedDetectionSurfaceSpeed = 0;
		collectedDetectionTicks = 0;
		generatedSpeed = 0;
		generatedCapacity = 0;
	}

	@Override
	protected boolean canPropagateDiagonally(IRotate block, BlockState state) {
		return false;
	}

	@Override
	public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
		boolean connectedViaAxes, boolean connectedViaCogs) {
		if (target instanceof PowerBeltBlockEntity belt && !connectedViaAxes)
			return getController().equals(belt.getController()) ? 1 : 0;
		return 0;
	}

	@Override
	protected boolean isNoisy() {
		return false;
	}
}
