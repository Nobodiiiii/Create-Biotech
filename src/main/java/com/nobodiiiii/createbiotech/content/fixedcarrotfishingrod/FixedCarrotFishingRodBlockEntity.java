package com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class FixedCarrotFishingRodBlockEntity extends BlockEntity {

	public FixedCarrotFishingRodBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.FIXED_CARROT_FISHING_ROD.get(), pos, state);
	}

	@Override
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition).expandTowards(0, -1, 0);
	}
}
