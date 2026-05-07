package com.nobodiiiii.createbiotech.content.slimebelt.transport;

import static net.minecraft.core.Direction.AxisDirection.NEGATIVE;
import static net.minecraft.core.Direction.AxisDirection.POSITIVE;
import static net.minecraft.world.entity.MoverType.SELF;

import java.util.List;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlock;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlockEntity;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.content.equipment.armor.CardboardArmorHandler;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SlimeBeltMovementHandler {

	public static class TransportedEntityInfo {
		int ticksSinceLastCollision;
		BlockPos lastCollidedPos;
		BlockState lastCollidedState;

		public TransportedEntityInfo(BlockPos collision, BlockState belt) {
			refresh(collision, belt);
		}

		public void refresh(BlockPos collision, BlockState belt) {
			ticksSinceLastCollision = 0;
			lastCollidedPos = new BlockPos(collision).immutable();
			lastCollidedState = belt;
		}

		public TransportedEntityInfo tick() {
			ticksSinceLastCollision++;
			return this;
		}

		public int getTicksSinceLastCollision() {
			return ticksSinceLastCollision;
		}
	}

	public static boolean canBeTransported(Entity entity) {
		if (!entity.isAlive())
			return false;
		if (BasinEntityProcessing.isCapturedSmallSlime(entity))
			return false;
		if (entity instanceof Player player && player.isShiftKeyDown() && !CardboardArmorHandler.testForStealth(entity))
			return false;
		return true;
	}

	public static void transportEntity(SlimeBeltBlockEntity beltBE, Entity entityIn, TransportedEntityInfo info) {
		BlockPos pos = info.lastCollidedPos;
		Level world = beltBE.getLevel();
		BlockEntity be = world.getBlockEntity(pos);
		BlockEntity blockEntityBelowPassenger = world.getBlockEntity(entityIn.blockPosition());
		BlockState blockState = info.lastCollidedState;
		Direction movementFacing =
			Direction.fromAxisAndDirection(blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis(),
				beltBE.getSpeed() < 0 ? POSITIVE : NEGATIVE);

		boolean collidedWithBelt = be instanceof SlimeBeltBlockEntity;
		boolean betweenBelts = blockEntityBelowPassenger instanceof SlimeBeltBlockEntity && blockEntityBelowPassenger != be;

		if (!collidedWithBelt || betweenBelts)
			return;

		boolean notHorizontal = beltBE.getBlockState().getValue(SlimeBeltBlock.SLOPE) != BeltSlope.HORIZONTAL;
		if (Math.abs(beltBE.getSpeed()) < 1)
			return;

		if (entityIn.getY() - .25f < pos.getY())
			return;

		boolean isPlayer = entityIn instanceof Player;
		if (entityIn instanceof LivingEntity livingEntity && !isPlayer)
			livingEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 1, false, false));

		Direction beltFacing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);
		BeltSlope slope = blockState.getValue(SlimeBeltBlock.SLOPE);
		Axis axis = beltFacing.getAxis();
		float movementSpeed = beltBE.getBeltMovementSpeed();
		Direction movementDirection = Direction.get(axis == Axis.X ? NEGATIVE : POSITIVE, axis);

		Vec3i centeringDirection = Direction.get(POSITIVE, beltFacing.getClockWise().getAxis()).getNormal();
		Vec3 movement = Vec3.atLowerCornerOf(movementDirection.getNormal()).scale(movementSpeed);

		double diffCenter = axis == Axis.Z ? pos.getX() + .5f - entityIn.getX() : pos.getZ() + .5f - entityIn.getZ();
		if (Math.abs(diffCenter) > 48 / 64f)
			return;

		BeltPart part = blockState.getValue(SlimeBeltBlock.PART);
		float top = 13 / 16f;
		boolean onSlope = notHorizontal && (part == BeltPart.MIDDLE || part == BeltPart.PULLEY
			|| part == (slope == BeltSlope.UPWARD ? BeltPart.END : BeltPart.START) && entityIn.getY() - pos.getY() < top
			|| part == (slope == BeltSlope.UPWARD ? BeltPart.START : BeltPart.END) && entityIn.getY() - pos.getY() > top);

		boolean movingDown = onSlope && slope == (movementFacing == beltFacing ? BeltSlope.DOWNWARD : BeltSlope.UPWARD);
		boolean movingUp = onSlope && slope == (movementFacing == beltFacing ? BeltSlope.UPWARD : BeltSlope.DOWNWARD);

		if (beltFacing.getAxis() == Axis.Z) {
			boolean swap = movingDown;
			movingDown = movingUp;
			movingUp = swap;
		}

		if (movingUp)
			movement = movement.add(0, Math.abs(axis.choose(movement.x, movement.y, movement.z)), 0);
		if (movingDown)
			movement = movement.add(0, -Math.abs(axis.choose(movement.x, movement.y, movement.z)), 0);

		Vec3 centering = Vec3.atLowerCornerOf(centeringDirection)
			.scale(diffCenter * Math.min(Math.abs(movementSpeed), .1f) * 4);

		LivingEntity livingEntity = entityIn instanceof LivingEntity living ? living : null;
		if (livingEntity == null || livingEntity.zza == 0 && livingEntity.xxa == 0)
			movement = movement.add(centering);

		float step = entityIn.maxUpStep();
		if (!isPlayer)
			entityIn.setMaxUpStep(1);

		if (Math.abs(movementSpeed) < .5f) {
			Vec3 checkDistance = movement.normalize().scale(0.5);
			AABB bb = entityIn.getBoundingBox();
			AABB checkBB = new AABB(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
			checkBB = checkBB.move(checkDistance)
				.inflate(-Math.abs(checkDistance.x), -Math.abs(checkDistance.y), -Math.abs(checkDistance.z));
			List<Entity> list = world.getEntities(entityIn, checkBB);
			list.removeIf(other -> shouldIgnoreBlocking(entityIn, other));
			if (!list.isEmpty()) {
				entityIn.setDeltaMovement(0, 0, 0);
				info.ticksSinceLastCollision--;
				return;
			}
		}

		entityIn.fallDistance = 0;

		if (movingUp) {
			float minVelocity = .13f;
			float yMovement = (float) -Math.max(Math.abs(movement.y), minVelocity);
			entityIn.move(SELF, new Vec3(0, yMovement, 0));
			entityIn.move(SELF, movement.multiply(1, 0, 1));
		} else if (movingDown) {
			entityIn.move(SELF, movement.multiply(1, 0, 1));
			entityIn.move(SELF, movement.multiply(0, 1, 0));
		} else {
			entityIn.move(SELF, movement);
		}

		entityIn.setOnGround(true);

		if (!isPlayer)
			entityIn.setMaxUpStep(step);

		boolean movedPastEndingSlope = onSlope && (world.getBlockState(entityIn.blockPosition()).is(CBBlocks.SLIME_BELT.get())
			|| world.getBlockState(entityIn.blockPosition().below()).is(CBBlocks.SLIME_BELT.get()));

		if (movedPastEndingSlope && !movingDown && Math.abs(movementSpeed) > 0)
			entityIn.setPos(entityIn.getX(), entityIn.getY() + movement.y, entityIn.getZ());
		if (movedPastEndingSlope) {
			entityIn.setDeltaMovement(movement);
			entityIn.hurtMarked = true;
		}
	}

	public static boolean shouldIgnoreBlocking(Entity me, Entity other) {
		if (other instanceof HangingEntity)
			return true;
		if (other.getPistonPushReaction() == PushReaction.IGNORE)
			return true;
		return isRidingOrBeingRiddenBy(me, other);
	}

	public static boolean isRidingOrBeingRiddenBy(Entity me, Entity other) {
		for (Entity entity : me.getPassengers()) {
			if (entity.equals(other))
				return true;
			if (isRidingOrBeingRiddenBy(entity, other))
				return true;
		}
		return false;
	}

}
