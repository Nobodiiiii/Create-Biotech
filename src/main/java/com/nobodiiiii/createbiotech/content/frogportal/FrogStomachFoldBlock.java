package com.nobodiiiii.createbiotech.content.frogportal;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Cushioned, axis-aligned tissue forming the ridges of the stomach lining. */
public class FrogStomachFoldBlock extends RotatedPillarBlock {

	public static final MapCodec<FrogStomachFoldBlock> CODEC = simpleCodec(FrogStomachFoldBlock::new);

	public FrogStomachFoldBlock(Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<? extends FrogStomachFoldBlock> codec() {
		return CODEC;
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
		entity.causeFallDamage(fallDistance, 0.0f, level.damageSources().fall());
	}
}
