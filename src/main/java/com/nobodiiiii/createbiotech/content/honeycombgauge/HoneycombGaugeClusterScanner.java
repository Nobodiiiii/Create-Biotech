package com.nobodiiiii.createbiotech.content.honeycombgauge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/** A bounded, loaded-chunk-only snapshot of the connected honeycomb plane. */
public final class HoneycombGaugeClusterScanner {
	public static final int MAX_BLOCKS = 2048;
	private static final int MAX_OFFSET = 64;

	private HoneycombGaugeClusterScanner() {}

	public record Gauge(BlockPos pos, PanelSlot slot, ItemStack filter) {}

	public record Snapshot(List<BlockPos> honeycombs, List<Gauge> gauges, boolean limited) {}

	public static Snapshot scan(Level level, BlockPos origin, Direction facing) {
		Direction[] plane = planeDirections(facing);
		ArrayDeque<BlockPos> pending = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		List<BlockPos> honeycombs = new ArrayList<>();
		List<Gauge> gauges = new ArrayList<>();
		boolean limited = false;

		// Factory gauges are mounted above a horizontal honeycomb platform. The
		// controller can sit on top of it or replace one tile along its edge.
		BlockPos below = origin.below();
		if (isLoadedHoneycomb(level, below)) {
			plane = planeDirections(Direction.UP);
			pending.add(below);
		} else {
			boolean adjacentFloor = false;
			for (Direction direction : Direction.Plane.HORIZONTAL)
				adjacentFloor |= isLoadedHoneycomb(level, origin.relative(direction));
			if (adjacentFloor) {
				plane = planeDirections(Direction.UP);
				for (Direction direction : plane)
					pending.add(origin.relative(direction));
			} else {
				BlockPos support = origin.relative(facing.getOpposite());
				if (isLoadedHoneycomb(level, support))
					pending.add(support);
				else
					for (Direction direction : plane)
						pending.add(origin.relative(direction));
			}
		}

		while (!pending.isEmpty()) {
			BlockPos pos = pending.removeFirst();
			if (!visited.add(pos) || !withinBounds(origin, pos))
				continue;
			if (!hasLoadedChunk(level, pos) || !level.getBlockState(pos).is(Blocks.HONEYCOMB_BLOCK))
				continue;
			if (honeycombs.size() >= MAX_BLOCKS) {
				limited = true;
				break;
			}
			honeycombs.add(pos.immutable());
			BlockPos gaugePos = pos.above();
			if (hasLoadedChunk(level, gaugePos)) {
				BlockState gaugeState = level.getBlockState(gaugePos);
				if (gaugeState.getBlock() instanceof FactoryPanelBlock
					&& gaugeState.getValue(FactoryPanelBlock.FACE) == AttachFace.FLOOR
					&& level.getBlockEntity(gaugePos) instanceof FactoryPanelBlockEntity panelEntity) {
					for (PanelSlot slot : PanelSlot.values()) {
						FactoryPanelBehaviour panel = panelEntity.panels.get(slot);
						if (panel != null && panel.isActive())
							gauges.add(new Gauge(gaugePos.immutable(), slot, panel.getFilter().copyWithCount(1)));
					}
				}
			}
			for (Direction direction : plane)
				pending.add(pos.relative(direction));
		}

		return new Snapshot(List.copyOf(honeycombs), List.copyOf(gauges), limited);
	}

	private static boolean withinBounds(BlockPos origin, BlockPos pos) {
		return Math.abs(pos.getX() - origin.getX()) <= MAX_OFFSET
			&& Math.abs(pos.getY() - origin.getY()) <= MAX_OFFSET
			&& Math.abs(pos.getZ() - origin.getZ()) <= MAX_OFFSET;
	}

	private static boolean hasLoadedChunk(Level level, BlockPos pos) {
		return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
	}

	private static boolean isLoadedHoneycomb(Level level, BlockPos pos) {
		return hasLoadedChunk(level, pos) && level.getBlockState(pos).is(Blocks.HONEYCOMB_BLOCK);
	}

	public static Direction[] planeDirections(Direction facing) {
		return switch (facing.getAxis()) {
			case X -> new Direction[] {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH};
			case Y -> new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
			case Z -> new Direction[] {Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST};
		};
	}
}
