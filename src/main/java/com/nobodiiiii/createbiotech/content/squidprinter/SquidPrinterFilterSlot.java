package com.nobodiiiii.createbiotech.content.squidprinter;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SquidPrinterFilterSlot extends ValueBoxTransform.Sided {

	@Override
	protected Vec3 getSouthLocation() {
		return VecHelper.voxelSpace(8, 8, 14.5);
	}

	@Override
	protected boolean isSideActive(BlockState state, Direction direction) {
		return direction.getAxis() != Direction.Axis.Y;
	}
}
