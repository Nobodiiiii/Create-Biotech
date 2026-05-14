package com.nobodiiiii.createbiotech.content.aircushion;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class AirCushionMovementBehaviour implements MovementBehaviour {

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

		boolean collision = collidesWithWorld(context);
		context.stall = collision;

		if (collision && entity != null) {
			for (Entity e = entity; e != null; e = e.getVehicle()) {
				e.setDeltaMovement(Vec3.ZERO);
			}
		}
	}

	private static boolean collidesWithWorld(MovementContext context) {
		BlockPos blockPos = BlockPos.containing(context.position);
		BlockState worldState = context.world.getBlockState(blockPos);
		if (worldState.canBeReplaced())
			return false;
		return !worldState.getCollisionShape(context.world, blockPos)
			.isEmpty();
	}
}
