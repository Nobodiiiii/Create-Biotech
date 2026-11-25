package com.nobodiiiii.createbiotech.content.biopackager;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.logistics.packager.PackagerBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;

public class BioPackagerBlock extends PackagerBlock {

	public BioPackagerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED);
	}

	@Override
	public BlockEntityType<? extends BioPackagerBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.BIO_PACKAGER.get();
	}
}
