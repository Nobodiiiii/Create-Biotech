package com.nobodiiiii.createbiotech.content.bufferpad;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.ghasthotairballoon.GhastHotAirBalloonEntity;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BufferPadCollisionHelper {

	private static final double IMPACT_SAMPLE_DISTANCE = 1.0E-4d;

	private static final CollisionTagSet ACTOR_TAGS = new CollisionTagSet(
		"EscapePushNormalX", "EscapePushNormalY", "EscapePushNormalZ",
		"TrainPositiveAxisX", "TrainPositiveAxisY", "TrainPositiveAxisZ");
	private static final CollisionTagSet ENTITY_TAGS = new CollisionTagSet(
		"CreateBiotechBufferPadEscapePushNormalX", "CreateBiotechBufferPadEscapePushNormalY",
		"CreateBiotechBufferPadEscapePushNormalZ", "CreateBiotechBufferPadTrainPositiveAxisX",
		"CreateBiotechBufferPadTrainPositiveAxisY", "CreateBiotechBufferPadTrainPositiveAxisZ");

	private BufferPadCollisionHelper() {}

	static void tickMovingPad(MovementContext context) {
		if (context.world == null || context.world.isClientSide)
			return;
		if (context.position == null || context.contraption == null || context.contraption.entity == null)
			return;
		if (!supportsBufferPadCollision(context.contraption.entity))
			return;

		Vec3 collisionNormal = getCollisionNormal(context);
		if (collisionNormal == null) {
			resolveCollision(context.contraption.entity, context.motion, context.data, ACTOR_TAGS, null, false, -1);
			return;
		}

		boolean collision = collidesWithWorld(context.position, context.world, collisionNormal);
		boolean previouslyColliding = collision
			&& collidesWithWorld(context.position.subtract(context.motion), context.world, collisionNormal);
		if (collision && !previouslyColliding)
			awardSoftLanding(context.contraption.entity);
		resolveCollision(context.contraption.entity, context.motion, context.data, ACTOR_TAGS,
			collision ? collisionNormal : null, previouslyColliding, -1);
	}

	public static void tickStaticWorldCollision(AbstractContraptionEntity entity) {
		if (entity == null || entity.level().isClientSide)
			return;
		if (!supportsBufferPadCollision(entity))
			return;

		WorldCollisionCandidate collision = findWorldCollision(entity,
			getStoredEscapePushNormal(entity.getPersistentData(), ENTITY_TAGS));
		if (collision == null) {
			resolveCollision(entity, Vec3.ZERO, entity.getPersistentData(), ENTITY_TAGS, null, false, -1);
			return;
		}

		resolveCollision(entity, collision.actorMotion(), entity.getPersistentData(), ENTITY_TAGS,
			collision.collisionNormal(), collision.previouslyColliding(), collision.positionCorrection());
	}

	private static boolean supportsBufferPadCollision(AbstractContraptionEntity entity) {
		return !(entity instanceof ControlledContraptionEntity) && !(entity instanceof GantryContraptionEntity);
	}

	private static void resolveCollision(AbstractContraptionEntity entity, Vec3 actorMotion, CompoundTag data,
		CollisionTagSet tags, @Nullable Vec3 collisionNormal, boolean previouslyColliding,
		double positionCorrection) {
		Vec3 previousEscapePushNormal = getStoredEscapePushNormal(data, tags);
		if (collisionNormal == null) {
			if (previousEscapePushNormal != null)
				clearEscapeInfluence(entity, previousEscapePushNormal);
			clearStoredEscapePushNormal(data, tags);
			return;
		}

		if (previousEscapePushNormal != null && !hasSameDirection(previousEscapePushNormal, collisionNormal)) {
			clearEscapeInfluence(entity, previousEscapePushNormal);
			clearStoredEscapePushNormal(data, tags);
		}

		CarriageContraptionEntity carriageEntity =
			entity instanceof CarriageContraptionEntity carriage && carriage.getCarriage() != null ? carriage : null;
		boolean escapeFromBlock =
			carriageEntity == null && shouldApplyEscapePush(actorMotion, collisionNormal, previouslyColliding);
		applyCollisionResponse(entity, actorMotion, data, tags, collisionNormal, escapeFromBlock, positionCorrection);
		if (carriageEntity != null) {
			clearStoredEscapePushNormal(data, tags);
			return;
		}
		if (escapeFromBlock) {
			storeEscapePushNormal(data, tags, collisionNormal);
			return;
		}

		clearStoredEscapePushNormal(data, tags);
	}

	private static boolean collidesWithWorld(Vec3 position, Level world, Vec3 collisionNormal) {
		BlockPos blockPos = BlockPos.containing(position.add(collisionNormal.scale(IMPACT_SAMPLE_DISTANCE)));
		BlockState worldState = world.getBlockState(blockPos);
		if (worldState.canBeReplaced())
			return false;
		return !worldState.getCollisionShape(world, blockPos)
			.isEmpty();
	}

	@Nullable
	private static Vec3 getCollisionNormal(MovementContext context) {
		if (!context.state.hasProperty(BufferPadBlock.FACING))
			return null;

		Direction collisionFace = context.state.getValue(BufferPadBlock.FACING)
			.getOpposite();
		Vec3 collisionNormal = context.rotation.apply(Vec3.atLowerCornerOf(collisionFace.getNormal()));
		if (collisionNormal.lengthSqr() < getMovementEpsilon())
			return null;
		return collisionNormal.normalize();
	}

	@Nullable
	private static Vec3 getCollisionNormal(BlockState state) {
		if (!(state.getBlock() instanceof BufferPadBlock))
			return null;
		if (!state.hasProperty(BufferPadBlock.FACING))
			return null;

		Vec3 collisionNormal = Vec3.atLowerCornerOf(state.getValue(BufferPadBlock.FACING)
			.getOpposite()
			.getNormal());
		if (collisionNormal.lengthSqr() < getMovementEpsilon())
			return null;
		return collisionNormal.normalize();
	}

	@Nullable
	private static WorldCollisionCandidate findWorldCollision(AbstractContraptionEntity entity,
		@Nullable Vec3 preferredNormal) {
		Contraption contraption = entity.getContraption();
		if (contraption == null)
			return null;

		List<BlockSample> samples = new ArrayList<>();

		for (Map.Entry<BlockPos, StructureBlockInfo> entry : contraption.getBlocks()
			.entrySet()) {
			BlockPos localPos = entry.getKey();
			StructureBlockInfo blockInfo = entry.getValue();
			if (contraption.isHiddenInPortal(localPos))
				continue;
			if (blockInfo.state().getBlock() instanceof BufferPadBlock)
				continue;
			var collisionShape = blockInfo.state()
				.getCollisionShape(contraption.getContraptionWorld(), localPos);
			if (collisionShape.isEmpty())
				continue;

			Vec3 localCenter = VecHelper.getCenterOf(localPos);
			Vec3 currentCenter = entity.toGlobalVector(localCenter, 1);
			Vec3 previousCenter = entity.toGlobalVector(localCenter, 0, true);
			samples.add(new BlockSample(BlockPos.containing(currentCenter), currentCenter, previousCenter,
				currentCenter.subtract(previousCenter), collisionShape.bounds()));
		}

		WorldCollisionCandidate bestCandidate = null;
		double bestPriority = Double.NEGATIVE_INFINITY;

		for (BlockSample sample : samples) {
			for (Direction direction : Iterate.directions) {
				BlockPos padPos = sample.currentCell()
					.relative(direction);
				Vec3 expectedNormal = Vec3.atLowerCornerOf(direction.getNormal());
				WorldCollisionCandidate candidate = tryCreateCandidate(entity, sample, padPos, expectedNormal);
				if (candidate != null) {
					double priority = getCollisionPriority(candidate, preferredNormal);
					if (priority > bestPriority) {
						bestPriority = priority;
						bestCandidate = candidate;
					}
				}
			}

			WorldCollisionCandidate tunneledCandidate = tryCreateCandidate(entity, sample, sample.currentCell(), null);
			if (tunneledCandidate == null)
				continue;
			double priority = getCollisionPriority(tunneledCandidate, preferredNormal) + 0.25d;
			if (priority > bestPriority) {
				bestPriority = priority;
				bestCandidate = tunneledCandidate;
			}
		}

		return bestCandidate;
	}

	@Nullable
	private static WorldCollisionCandidate tryCreateCandidate(AbstractContraptionEntity entity, BlockSample sample,
		BlockPos padPos, @Nullable Vec3 expectedNormal) {
		BlockState state = entity.level().getBlockState(padPos);
		Vec3 padNormal = getCollisionNormal(state);
		if (padNormal == null)
			return null;
		Vec3 collisionNormal = padNormal.scale(-1);
		if (expectedNormal != null && !hasSameDirection(collisionNormal, expectedNormal))
			return null;

		double collisionExtent = getCollisionExtent(sample.shapeBounds(), collisionNormal);
		if (collisionExtent <= getMovementEpsilon())
			return null;

		Vec3 padCenter = VecHelper.getCenterOf(padPos);
		double currentPenetration = sample.currentCenter()
			.subtract(padCenter)
			.dot(collisionNormal) + collisionExtent;
		double previousPenetration = sample.previousCenter()
			.subtract(padCenter)
			.dot(collisionNormal) + collisionExtent;
		boolean currentlyColliding = currentPenetration > getMovementEpsilon();
		boolean crossedIntoPad = previousPenetration <= getMovementEpsilon()
			&& currentPenetration > -getMovementEpsilon();
		if (!currentlyColliding && !crossedIntoPad)
			return null;

		boolean previouslyColliding = previousPenetration > getMovementEpsilon();
		return new WorldCollisionCandidate(collisionNormal, sample.motion(), previouslyColliding,
			Math.max(currentPenetration, 0));
	}

	private static double getCollisionPriority(WorldCollisionCandidate candidate, @Nullable Vec3 preferredNormal) {
		double priority = candidate.previouslyColliding() ? 1 : 0;
		double inwardMotion = candidate.actorMotion()
			.dot(candidate.collisionNormal());
		if (inwardMotion > getMovementEpsilon())
			priority += 2 + inwardMotion;
		else
			priority += inwardMotion;

		if (preferredNormal != null && hasSameDirection(preferredNormal, candidate.collisionNormal()))
			priority += 1000;
		return priority;
	}

	private static boolean shouldApplyEscapePush(Vec3 actorMotion, Vec3 collisionNormal, boolean previouslyColliding) {
		double inwardMotion = actorMotion.dot(collisionNormal);
		if (inwardMotion <= getMovementEpsilon())
			return true;
		return previouslyColliding;
	}

	private static void applyCollisionResponse(AbstractContraptionEntity entity, Vec3 actorMotion, CompoundTag data,
		CollisionTagSet tags, Vec3 collisionNormal, boolean escapeFromBlock, double positionCorrection) {
		clipEntityPosition(entity, collisionNormal, positionCorrection);
		clipEntityMotion(entity, collisionNormal, escapeFromBlock);

		if (entity instanceof CarriageContraptionEntity carriageEntity && carriageEntity.getCarriage() != null)
			clipTrainSpeed(data, tags, actorMotion, carriageEntity.getCarriage().train, collisionNormal, escapeFromBlock);

		if (entity instanceof OrientedContraptionEntity orientedEntity)
			clipCoupledCarts(orientedEntity, collisionNormal, escapeFromBlock, positionCorrection);

		for (Entity current = entity.getVehicle(); current != null; current = current.getVehicle()) {
			clipEntityPosition(current, collisionNormal, positionCorrection);
			clipEntityMotion(current, collisionNormal, escapeFromBlock);
		}
	}

	private static void clipCoupledCarts(OrientedContraptionEntity entity, Vec3 collisionNormal,
		boolean escapeFromBlock, double positionCorrection) {
		var coupledCarts = entity.getCoupledCartsIfPresent();
		if (coupledCarts == null)
			return;

		clipEntityPosition(coupledCarts.getFirst()
			.cart(), collisionNormal, positionCorrection);
		clipEntityMotion(coupledCarts.getFirst()
			.cart(), collisionNormal, escapeFromBlock);
		clipEntityPosition(coupledCarts.getSecond()
			.cart(), collisionNormal, positionCorrection);
		clipEntityMotion(coupledCarts.getSecond()
			.cart(), collisionNormal, escapeFromBlock);
	}

	private static void trimCoupledCarts(OrientedContraptionEntity entity, Vec3 collisionNormal) {
		var coupledCarts = entity.getCoupledCartsIfPresent();
		if (coupledCarts == null)
			return;

		trimEntityMotion(coupledCarts.getFirst()
			.cart(), collisionNormal);
		trimEntityMotion(coupledCarts.getSecond()
			.cart(), collisionNormal);
	}

	private static void clipTrainSpeed(CompoundTag data, CollisionTagSet tags, Vec3 actorMotion, Train train,
		Vec3 collisionNormal, boolean escapeFromBlock) {
		Vec3 adjustedMotion = adjustMotionAgainstCollision(actorMotion, collisionNormal, escapeFromBlock);
		if (adjustedMotion.equals(actorMotion))
			return;

		double referenceSpeed = getTrainReferenceSpeed(train);
		if (Math.abs(referenceSpeed) < getMovementEpsilon() && actorMotion.lengthSqr() < getMovementEpsilon())
			return;

		double adjustedSpeed = getAdjustedTrainSpeed(actorMotion, adjustedMotion, referenceSpeed, escapeFromBlock);
		train.speed = adjustedSpeed;
		if (train.speedBeforeStall != null)
			train.speedBeforeStall = adjustedSpeed;
		clipTrainTargetSpeed(data, tags, actorMotion, train, collisionNormal, referenceSpeed);
	}

	private static void clearEscapeInfluence(AbstractContraptionEntity entity, Vec3 collisionNormal) {
		trimEntityMotion(entity, collisionNormal);

		if (entity instanceof CarriageContraptionEntity carriageEntity && carriageEntity.getCarriage() != null)
			trimTrainSpeed(carriageEntity.getCarriage().train);

		if (entity instanceof OrientedContraptionEntity orientedEntity)
			trimCoupledCarts(orientedEntity, collisionNormal);

		for (Entity current = entity.getVehicle(); current != null; current = current.getVehicle())
			trimEntityMotion(current, collisionNormal);
	}

	private static void clipEntityPosition(Entity entity, Vec3 collisionNormal, double positionCorrection) {
		if (entity == null)
			return;

		double intoCollisionFace = positionCorrection;
		if (intoCollisionFace <= getMovementEpsilon()) {
			Vec3 displacement = entity.position()
				.subtract(entity.xo, entity.yo, entity.zo);
			intoCollisionFace = displacement.dot(collisionNormal);
		}
		if (intoCollisionFace <= getMovementEpsilon())
			return;

		Vec3 correctedPosition = entity.position()
			.subtract(collisionNormal.scale(intoCollisionFace));
		entity.setPos(correctedPosition.x, correctedPosition.y, correctedPosition.z);
		entity.hurtMarked = true;
	}

	private static void clipEntityMotion(Entity entity, Vec3 collisionNormal, boolean escapeFromBlock) {
		if (entity == null)
			return;

		Vec3 motion = entity.getDeltaMovement();
		Vec3 adjustedMotion = adjustMotionAgainstCollision(motion, collisionNormal, escapeFromBlock);
		if (adjustedMotion.equals(motion))
			return;

		if (entity instanceof AbstractContraptionEntity contraptionEntity)
			contraptionEntity.setContraptionMotion(adjustedMotion);
		else
			entity.setDeltaMovement(adjustedMotion);
		entity.hurtMarked = true;

		if (entity instanceof MinecartFurnace furnaceCart)
			clipFurnacePush(furnaceCart, collisionNormal, escapeFromBlock);
	}

	private static void trimEntityMotion(Entity entity, Vec3 collisionNormal) {
		if (entity == null)
			return;

		Vec3 motion = entity.getDeltaMovement();
		Vec3 trimmedMotion = removeEscapeVelocityContribution(motion, collisionNormal);
		if (trimmedMotion.equals(motion))
			return;

		if (entity instanceof AbstractContraptionEntity contraptionEntity)
			contraptionEntity.setContraptionMotion(trimmedMotion);
		else
			entity.setDeltaMovement(trimmedMotion);
		entity.hurtMarked = true;

		if (entity instanceof MinecartFurnace furnaceCart)
			trimFurnacePush(furnaceCart, collisionNormal);
	}

	private static void clipFurnacePush(MinecartFurnace furnaceCart, Vec3 collisionNormal, boolean escapeFromBlock) {
		Vec3 horizontalNormal = new Vec3(collisionNormal.x, 0, collisionNormal.z);
		if (horizontalNormal.lengthSqr() < getMovementEpsilon())
			return;

		horizontalNormal = horizontalNormal.normalize();

		CompoundTag nbt = furnaceCart.serializeNBT();
		Vec3 push = new Vec3(nbt.getDouble("PushX"), 0, nbt.getDouble("PushZ"));
		Vec3 adjustedPush = adjustMotionAgainstCollision(push, horizontalNormal, escapeFromBlock);
		if (adjustedPush.equals(push))
			return;

		nbt.putDouble("PushX", adjustedPush.x);
		nbt.putDouble("PushZ", adjustedPush.z);
		furnaceCart.deserializeNBT(nbt);
	}

	private static void trimFurnacePush(MinecartFurnace furnaceCart, Vec3 collisionNormal) {
		Vec3 horizontalNormal = new Vec3(collisionNormal.x, 0, collisionNormal.z);
		if (horizontalNormal.lengthSqr() < getMovementEpsilon())
			return;

		horizontalNormal = horizontalNormal.normalize();

		CompoundTag nbt = furnaceCart.serializeNBT();
		Vec3 push = new Vec3(nbt.getDouble("PushX"), 0, nbt.getDouble("PushZ"));
		Vec3 trimmedPush = removeEscapeVelocityContribution(push, horizontalNormal);
		if (trimmedPush.equals(push))
			return;

		nbt.putDouble("PushX", trimmedPush.x);
		nbt.putDouble("PushZ", trimmedPush.z);
		furnaceCart.deserializeNBT(nbt);
	}

	private static double getTrainReferenceSpeed(Train train) {
		double currentSpeed = train.speedBeforeStall != null ? train.speedBeforeStall : train.speed;
		if (Math.abs(currentSpeed) >= getMovementEpsilon())
			return currentSpeed;
		if (Math.abs(train.targetSpeed) >= getMovementEpsilon())
			return train.targetSpeed;
		return 0;
	}

	private static double getAdjustedTrainSpeed(Vec3 actorMotion, Vec3 adjustedMotion, double referenceSpeed,
		boolean escapeFromBlock) {
		if (actorMotion.lengthSqr() < getMovementEpsilon()) {
			if (!escapeFromBlock || Math.abs(referenceSpeed) < getMovementEpsilon())
				return referenceSpeed;
			return -Math.copySign(getEscapePushSpeed(), referenceSpeed);
		}

		Vec3 travelAxis = actorMotion.normalize();
		double alongTravel = adjustedMotion.dot(travelAxis);
		if (escapeFromBlock && alongTravel > -getEscapePushSpeed())
			alongTravel = -getEscapePushSpeed();

		double speedMagnitude = Math.abs(alongTravel);
		if (speedMagnitude < getMovementEpsilon())
			return 0;

		double speed = Math.copySign(speedMagnitude, referenceSpeed);
		if (alongTravel < 0)
			speed *= -1;
		return speed;
	}

	private static void trimTrainSpeed(Train train) {
		train.speed = trimEscapeSpeedMagnitude(train.speed);
		if (train.speedBeforeStall != null)
			train.speedBeforeStall = trimEscapeSpeedMagnitude(train.speedBeforeStall);
	}

	private static void clipTrainTargetSpeed(CompoundTag data, CollisionTagSet tags, Vec3 actorMotion, Train train,
		Vec3 collisionNormal, double referenceSpeed) {
		if (Math.abs(train.targetSpeed) < getMovementEpsilon())
			return;

		Vec3 positiveAxis = getTrainPositiveAxis(data, tags, actorMotion, referenceSpeed);
		if (positiveAxis == null)
			return;

		Vec3 targetMotion = positiveAxis.scale(train.targetSpeed);
		Vec3 adjustedTargetMotion = removeVelocityIntoCollisionFace(targetMotion, collisionNormal);
		double adjustedTargetSpeed = adjustedTargetMotion.dot(positiveAxis);
		train.targetSpeed = Math.abs(adjustedTargetSpeed) < getMovementEpsilon() ? 0 : adjustedTargetSpeed;
	}

	private static Vec3 adjustMotionAgainstCollision(Vec3 motion, Vec3 collisionNormal, boolean escapeFromBlock) {
		Vec3 adjustedMotion = removeVelocityIntoCollisionFace(motion, collisionNormal);
		if (!escapeFromBlock)
			return adjustedMotion;
		return ensureEscapeVelocity(adjustedMotion, collisionNormal);
	}

	private static Vec3 ensureEscapeVelocity(Vec3 motion, Vec3 collisionNormal) {
		double normalSpeed = motion.dot(collisionNormal);
		if (normalSpeed <= -getEscapePushSpeed())
			return motion;
		return motion.subtract(collisionNormal.scale(normalSpeed + getEscapePushSpeed()));
	}

	private static Vec3 removeEscapeVelocityContribution(Vec3 motion, Vec3 collisionNormal) {
		double normalSpeed = motion.dot(collisionNormal);
		if (normalSpeed >= -getMovementEpsilon())
			return motion;
		double cancelledSpeed = Math.min(-normalSpeed, getEscapePushSpeed());
		return motion.add(collisionNormal.scale(cancelledSpeed));
	}

	private static Vec3 removeVelocityIntoCollisionFace(Vec3 motion, Vec3 collisionNormal) {
		double intoCollisionFace = motion.dot(collisionNormal);
		if (intoCollisionFace <= getMovementEpsilon())
			return motion;
		return motion.subtract(collisionNormal.scale(intoCollisionFace));
	}

	private static double trimEscapeSpeedMagnitude(double speed) {
		double magnitude = Math.abs(speed);
		if (magnitude < getMovementEpsilon())
			return 0;
		double trimmedMagnitude = Math.max(0, magnitude - getEscapePushSpeed());
		if (trimmedMagnitude < getMovementEpsilon())
			return 0;
		return Math.copySign(trimmedMagnitude, speed);
	}

	private static void storeEscapePushNormal(CompoundTag data, CollisionTagSet tags, Vec3 collisionNormal) {
		data.putDouble(tags.escapePushNormalX(), collisionNormal.x);
		data.putDouble(tags.escapePushNormalY(), collisionNormal.y);
		data.putDouble(tags.escapePushNormalZ(), collisionNormal.z);
	}

	private static void clearStoredEscapePushNormal(CompoundTag data, CollisionTagSet tags) {
		data.remove(tags.escapePushNormalX());
		data.remove(tags.escapePushNormalY());
		data.remove(tags.escapePushNormalZ());
	}

	@Nullable
	private static Vec3 getStoredEscapePushNormal(CompoundTag data, CollisionTagSet tags) {
		if (!data.contains(tags.escapePushNormalX()) || !data.contains(tags.escapePushNormalY())
			|| !data.contains(tags.escapePushNormalZ()))
			return null;

		Vec3 collisionNormal = new Vec3(data.getDouble(tags.escapePushNormalX()), data.getDouble(tags.escapePushNormalY()),
			data.getDouble(tags.escapePushNormalZ()));
		if (collisionNormal.lengthSqr() < getMovementEpsilon())
			return null;
		return collisionNormal.normalize();
	}

	@Nullable
	private static Vec3 getTrainPositiveAxis(CompoundTag data, CollisionTagSet tags, Vec3 actorMotion,
		double referenceSpeed) {
		if (actorMotion.lengthSqr() >= getMovementEpsilon()
			&& Math.abs(referenceSpeed) >= getMovementEpsilon()) {
			Vec3 positiveAxis = actorMotion.normalize();
			if (referenceSpeed < 0)
				positiveAxis = positiveAxis.scale(-1);
			storeTrainPositiveAxis(data, tags, positiveAxis);
			return positiveAxis;
		}

		return getStoredTrainPositiveAxis(data, tags);
	}

	private static void storeTrainPositiveAxis(CompoundTag data, CollisionTagSet tags, Vec3 positiveAxis) {
		data.putDouble(tags.trainPositiveAxisX(), positiveAxis.x);
		data.putDouble(tags.trainPositiveAxisY(), positiveAxis.y);
		data.putDouble(tags.trainPositiveAxisZ(), positiveAxis.z);
	}

	@Nullable
	private static Vec3 getStoredTrainPositiveAxis(CompoundTag data, CollisionTagSet tags) {
		if (!data.contains(tags.trainPositiveAxisX()) || !data.contains(tags.trainPositiveAxisY())
			|| !data.contains(tags.trainPositiveAxisZ()))
			return null;

		Vec3 positiveAxis = new Vec3(data.getDouble(tags.trainPositiveAxisX()), data.getDouble(tags.trainPositiveAxisY()),
			data.getDouble(tags.trainPositiveAxisZ()));
		if (positiveAxis.lengthSqr() < getMovementEpsilon())
			return null;
		return positiveAxis.normalize();
	}

	private static boolean hasSameDirection(Vec3 first, Vec3 second) {
		return Direction.getNearest(first.x, first.y, first.z) == Direction.getNearest(second.x, second.y, second.z);
	}

	private static double getCollisionExtent(AABB shapeBounds, Vec3 collisionNormal) {
		return switch (Direction.getNearest(collisionNormal.x, collisionNormal.y, collisionNormal.z)) {
		case DOWN -> 0.5d - shapeBounds.minY;
		case UP -> shapeBounds.maxY - 0.5d;
		case NORTH -> 0.5d - shapeBounds.minZ;
		case SOUTH -> shapeBounds.maxZ - 0.5d;
		case WEST -> 0.5d - shapeBounds.minX;
		case EAST -> shapeBounds.maxX - 0.5d;
		};
	}

	private static double getMovementEpsilon() {
		return CBConfigs.COMMON.bufferPad.movementEpsilon.get();
	}

	private static double getEscapePushSpeed() {
		return CBConfigs.COMMON.bufferPad.escapePushSpeed.get();
	}

	private static void awardSoftLanding(AbstractContraptionEntity entity) {
		if (!(entity instanceof GhastHotAirBalloonEntity) || !(entity.level() instanceof ServerLevel serverLevel))
			return;

		boolean awarded = entity.getControllingPlayer()
			.map(playerId -> CBAdvancements.awardPlayer(serverLevel, playerId, CBAdvancements.SOFT_LANDING))
			.orElse(false);
		if (!awarded)
			CBAdvancements.awardNearby(serverLevel, entity.position(), 32, CBAdvancements.SOFT_LANDING);
	}

	private record CollisionTagSet(String escapePushNormalX, String escapePushNormalY, String escapePushNormalZ,
		String trainPositiveAxisX, String trainPositiveAxisY, String trainPositiveAxisZ) {
	}

	private record BlockSample(BlockPos currentCell, Vec3 currentCenter, Vec3 previousCenter, Vec3 motion,
		AABB shapeBounds) {
	}

	private record WorldCollisionCandidate(Vec3 collisionNormal, Vec3 actorMotion, boolean previouslyColliding,
		double positionCorrection) {
	}
}
