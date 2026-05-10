package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BlastProofChainDriveBlock extends ChainDriveBlock {

	public BlastProofChainDriveBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockState updateAfterWrenched(BlockState newState, UseOnContext context) {
		return CBBlocks.EXPLOSION_PROOF_CASING.get().defaultBlockState();
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return CBBlockEntityTypes.BLAST_PROOF_CHAIN_DRIVE.get();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	public Class<KineticBlockEntity> getBlockEntityClass() {
		return (Class) BlastProofChainDriveBlockEntity.class;
	}
}
