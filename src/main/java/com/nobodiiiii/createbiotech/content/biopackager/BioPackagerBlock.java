package com.nobodiiiii.createbiotech.content.biopackager;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.logistics.packager.PackagerBlock;

import net.minecraft.world.level.block.entity.BlockEntityType;

public class BioPackagerBlock extends PackagerBlock {

	public BioPackagerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends BioPackagerBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.BIO_PACKAGER.get();
	}
}
