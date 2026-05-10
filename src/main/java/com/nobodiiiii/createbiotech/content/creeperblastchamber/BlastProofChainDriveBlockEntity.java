package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class BlastProofChainDriveBlockEntity extends KineticBlockEntity {

	public BlastProofChainDriveBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.BLAST_PROOF_CHAIN_DRIVE.get(), pos, state);
	}
}
