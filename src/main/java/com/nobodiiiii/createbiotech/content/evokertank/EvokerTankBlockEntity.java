package com.nobodiiiii.createbiotech.content.evokertank;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class EvokerTankBlockEntity extends BlockEntity {

	public EvokerTankBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.EVOKER_TANK.get(), pos, state);
	}

	@Override
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition, worldPosition.offset(1, 2, 1));
	}
}
