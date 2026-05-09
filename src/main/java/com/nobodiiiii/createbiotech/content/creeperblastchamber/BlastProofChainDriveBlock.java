package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class BlastProofChainDriveBlock extends ChainDriveBlock {

	public static final DirectionProperty VISUAL_SIDE =
		DirectionProperty.create("visual_side", Direction.Plane.HORIZONTAL);

	public BlastProofChainDriveBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(VISUAL_SIDE, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(VISUAL_SIDE));
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return super.rotate(state, rotation)
			.setValue(VISUAL_SIDE, rotation.rotate(state.getValue(VISUAL_SIDE)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return super.mirror(state, mirror)
			.setValue(VISUAL_SIDE, mirror.mirror(state.getValue(VISUAL_SIDE)));
	}

	@Override
	public BlockState updateAfterWrenched(BlockState newState, UseOnContext context) {
		return CBBlocks.EXPLOSION_PROOF_CASING.get().defaultBlockState();
	}
}
