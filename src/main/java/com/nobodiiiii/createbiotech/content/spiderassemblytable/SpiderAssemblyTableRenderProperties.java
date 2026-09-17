package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.simibubi.create.AllBlocks;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

/** Uses the applied casing for the table's mining and destruction particles. */
public final class SpiderAssemblyTableRenderProperties implements IClientBlockExtensions {

	@Override
	public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
		if (!(level instanceof ClientLevel clientLevel))
			return true;

		BlockState particleState = getParticleState(state, level, pos);
		VoxelShape shape = state.getShape(level, pos);
		if (shape.isEmpty() || particleState.isAir())
			return true;

		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
			double width = Math.min(1, maxX - minX);
			double height = Math.min(1, maxY - minY);
			double depth = Math.min(1, maxZ - minZ);
			int xParts = Math.max(2, Mth.ceil(width / .25));
			int yParts = Math.max(2, Mth.ceil(height / .25));
			int zParts = Math.max(2, Mth.ceil(depth / .25));

			for (int xIndex = 0; xIndex < xParts; xIndex++)
				for (int yIndex = 0; yIndex < yParts; yIndex++)
					for (int zIndex = 0; zIndex < zParts; zIndex++) {
						double xRatio = (xIndex + .5) / xParts;
						double yRatio = (yIndex + .5) / yParts;
						double zRatio = (zIndex + .5) / zParts;
						double x = pos.getX() + minX + xRatio * width;
						double y = pos.getY() + minY + yRatio * height;
						double z = pos.getZ() + minZ + zRatio * depth;
						manager.add(new TerrainParticle(clientLevel, x, y, z,
							xRatio - .5, yRatio - .5, zRatio - .5, particleState, pos));
					}
		});
		return true;
	}

	@Override
	public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
		if (!(level instanceof ClientLevel clientLevel) || !(target instanceof BlockHitResult blockHit))
			return true;

		BlockPos pos = blockHit.getBlockPos();
		VoxelShape shape = state.getShape(level, pos);
		if (shape.isEmpty())
			return true;

		AABB bounds = shape.bounds();
		double x = pos.getX() + randomInside(clientLevel, bounds.minX, bounds.maxX);
		double y = pos.getY() + randomInside(clientLevel, bounds.minY, bounds.maxY);
		double z = pos.getZ() + randomInside(clientLevel, bounds.minZ, bounds.maxZ);
		Direction side = blockHit.getDirection();
		if (side == Direction.DOWN)
			y = pos.getY() + bounds.minY - .1;
		else if (side == Direction.UP)
			y = pos.getY() + bounds.maxY + .1;
		else if (side == Direction.NORTH)
			z = pos.getZ() + bounds.minZ - .1;
		else if (side == Direction.SOUTH)
			z = pos.getZ() + bounds.maxZ + .1;
		else if (side == Direction.WEST)
			x = pos.getX() + bounds.minX - .1;
		else if (side == Direction.EAST)
			x = pos.getX() + bounds.maxX + .1;

		BlockState particleState = getParticleState(state, level, pos);
		manager.add(new TerrainParticle(clientLevel, x, y, z, 0, 0, 0, particleState, pos)
			.setPower(.2f)
			.scale(.6f));
		return true;
	}

	private static double randomInside(ClientLevel level, double min, double max) {
		double inset = Math.min(.1, (max - min) / 2);
		return min + inset + level.random.nextDouble() * Math.max(0, max - min - inset * 2);
	}

	private static BlockState getParticleState(BlockState tableState, Level level, BlockPos pos) {
		if (!tableState.getValue(SpiderAssemblyTableBlock.CASING))
			return tableState;

		Block casing = AllBlocks.ANDESITE_CASING.get();
		if (level.getBlockEntity(pos) instanceof SpiderAssemblyTableBlockEntity table) {
			Block storedCasing = table.getCasing();
			if (storedCasing != null)
				casing = storedCasing;
		}
		return casing.defaultBlockState();
	}
}
