package com.nobodiiiii.createbiotech.content.honeycombgauge;

import java.util.List;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Owns the client-side highlight of the honeycomb face selected by this cluster. */
public class HoneycombGaugeClusterBlockEntity extends BlockEntity {
	private List<BlockPos> honeycombs = List.of();
	private Direction scannedFacing;
	private long lastScan = Long.MIN_VALUE;

	public HoneycombGaugeClusterBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.HONEYCOMB_GAUGE_CLUSTER.get(), pos, state);
	}

	public List<BlockPos> visibleHoneycombs() {
		if (level == null)
			return List.of();
		Direction facing = getBlockState().getValue(HoneycombGaugeClusterBlock.FACING);
		long tick = level.getGameTime();
		if (lastScan == Long.MIN_VALUE || scannedFacing != facing || tick - lastScan >= 10) {
			honeycombs = HoneycombGaugeClusterScanner.connectedHoneycombs(level, worldPosition, facing);
			scannedFacing = facing;
			lastScan = tick;
		}
		return honeycombs;
	}
}
