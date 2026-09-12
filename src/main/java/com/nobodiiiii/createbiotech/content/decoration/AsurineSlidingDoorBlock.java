package com.nobodiiiii.createbiotech.content.decoration;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * A Create-style folding door backed by this mod's own block entity type.
 * Create's sliding-door type only accepts Create's built-in door blocks.
 */
public class AsurineSlidingDoorBlock extends SlidingDoorBlock {

	public AsurineSlidingDoorBlock(Properties properties, BlockSetType type) {
		super(properties, type, true);
	}

	@Override
	public BlockEntityType<? extends SlidingDoorBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.ASURINE_DOOR.get();
	}
}
