package com.nobodiiiii.createbiotech.content.aircushion;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class AirCushionMovementBehaviour implements MovementBehaviour {

	// The cushion only protrudes half a block, so its visible impact face sits on the block center plane.
	private static final double IMPACT_SAMPLE_DISTANCE = 1.0E-4d;
	private static final double MOVEMENT_EPSILON = 1.0E-4d;

	@Override
	public void tick(MovementContext context) {
		if (context.world == null || context.world.isClientSide)
			return;
		if (context.position == null)
			return;

		AbstractContraptionEntity entity = context.contraption == null ? null : context.contraption.entity;
		if (entity instanceof ControlledContraptionEntity)
			return;
		if (entity instanceof GantryContraptionEntity)
			return;

		Vec3 collisionNormal = getCollisionNormal(context);
		boolean collision = collisionNormal != null && collidesWithWorld(context, collisionNormal);
		context.stall = false;

		if (collision && entity != null) {
			applyCollisionResponse(context, entity, collisionNormal);
		}
	}

	private static boolean collidesWithWorld(MovementContext context, Vec3 collisionNormal) {
		BlockPos blockPos = BlockPos.containing(context.position.add(collisionNormal.scale(IMPACT_SAMPLE_DISTANCE)));
		BlockState worldState = context.world.getBlockState(blockPos);
		if (worldState.canBeReplaced())
			return false;
		return !worldState.getCollisionShape(context.world, blockPos)
			.isEmpty();
	}

	private static Vec3 getCollisionNormal(MovementContext context) {
		if (!context.state.hasProperty(AirCushionBlock.FACING))
			return null;
		if (context.motion.lengthSqr() < MOVEMENT_EPSILON)
			return null;

		Direction collisionFace = context.state.getValue(AirCushionBlock.FACING)
			.getOpposite();
		Vec3 collisionNormal = context.rotation.apply(Vec3.atLowerCornerOf(collisionFace.getNormal()))
			.normalize();
		if (collisionNormal.lengthSqr() < MOVEMENT_EPSILON)
			return null;
		if (context.motion.dot(collisionNormal) <= MOVEMENT_EPSILON)
			return null;
		return collisionNormal;
	}

	private static void applyCollisionResponse(MovementContext context, AbstractContraptionEntity entity,
		Vec3 collisionNormal) {
		clipEntityPosition(entity, collisionNormal);
		clipEntityMotion(entity, collisionNormal);

		if (entity instanceof CarriageContraptionEntity carriageEntity && carriageEntity.getCarriage() != null)
			clipTrainSpeed(context.motion, carriageEntity.getCarriage().train, collisionNormal);

		if (entity instanceof OrientedContraptionEntity orientedEntity)
			clipCoupledCarts(orientedEntity, collisionNormal);

		for (Entity current = entity.getVehicle(); current != null; current = current.getVehicle()) {
			clipEntityPosition(current, collisionNormal);
			clipEntityMotion(current, collisionNormal);
		}
	}

	private static void clipCoupledCarts(OrientedContraptionEntity entity, Vec3 collisionNormal) {
		var coupledCarts = entity.getCoupledCartsIfPresent();
		if (coupledCarts == null)
			return;

		clipEntityPosition(coupledCarts.getFirst()
			.cart(), collisionNormal);
		clipEntityMotion(coupledCarts.getFirst()
			.cart(), collisionNormal);
		clipEntityPosition(coupledCarts.getSecond()
			.cart(), collisionNormal);
		clipEntityMotion(coupledCarts.getSecond()
			.cart(), collisionNormal);
	}

	private static void clipTrainSpeed(Vec3 actorMotion, Train train, Vec3 collisionNormal) {
		Vec3 clippedMotion = removeVelocityIntoCollisionFace(actorMotion, collisionNormal);
		if (clippedMotion.equals(actorMotion))
			return;

		double currentSpeed = train.speedBeforeStall != null ? train.speedBeforeStall : train.speed;
		if (Math.abs(currentSpeed) < MOVEMENT_EPSILON)
			return;

		double clippedSpeed = Math.copySign(clippedMotion.length(), currentSpeed);
		train.speed = clippedSpeed;
		if (train.speedBeforeStall != null)
			train.speedBeforeStall = clippedSpeed;
	}

	private static void clipEntityPosition(Entity entity, Vec3 collisionNormal) {
		if (entity == null)
			return;

		Vec3 displacement = entity.position()
			.subtract(entity.xo, entity.yo, entity.zo);
		double intoCollisionFace = displacement.dot(collisionNormal);
		if (intoCollisionFace <= MOVEMENT_EPSILON)
			return;

		Vec3 correctedPosition = entity.position()
			.subtract(collisionNormal.scale(intoCollisionFace));
		entity.setPos(correctedPosition.x, correctedPosition.y, correctedPosition.z);
		entity.hurtMarked = true;
	}

	private static void clipEntityMotion(Entity entity, Vec3 collisionNormal) {
		if (entity == null)
			return;

		Vec3 motion = entity.getDeltaMovement();
		Vec3 clippedMotion = removeVelocityIntoCollisionFace(motion, collisionNormal);
		if (clippedMotion.equals(motion))
			return;

		if (entity instanceof AbstractContraptionEntity contraptionEntity)
			contraptionEntity.setContraptionMotion(clippedMotion);
		else
			entity.setDeltaMovement(clippedMotion);
		entity.hurtMarked = true;

		if (entity instanceof MinecartFurnace furnaceCart)
			clipFurnacePush(furnaceCart, collisionNormal);
	}

	private static void clipFurnacePush(MinecartFurnace furnaceCart, Vec3 collisionNormal) {
		Vec3 horizontalNormal = new Vec3(collisionNormal.x, 0, collisionNormal.z);
		if (horizontalNormal.lengthSqr() < MOVEMENT_EPSILON)
			return;

		horizontalNormal = horizontalNormal.normalize();

		CompoundTag nbt = furnaceCart.serializeNBT();
		Vec3 push = new Vec3(nbt.getDouble("PushX"), 0, nbt.getDouble("PushZ"));
		Vec3 clippedPush = removeVelocityIntoCollisionFace(push, horizontalNormal);
		if (clippedPush.equals(push))
			return;

		nbt.putDouble("PushX", clippedPush.x);
		nbt.putDouble("PushZ", clippedPush.z);
		furnaceCart.deserializeNBT(nbt);
	}

	private static Vec3 removeVelocityIntoCollisionFace(Vec3 motion, Vec3 collisionNormal) {
		double intoCollisionFace = motion.dot(collisionNormal);
		if (intoCollisionFace <= MOVEMENT_EPSILON)
			return motion;
		return motion.subtract(collisionNormal.scale(intoCollisionFace));
	}
}
