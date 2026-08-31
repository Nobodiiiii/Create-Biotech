package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Resolves one horizontal connected surgical-table work surface, independent of block orientation. */
public final class SurgicalTablePlane {
	public static final int MAX_TILES = 1024;
	private static final Direction[] HORIZONTAL = {
		Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
	};

	private SurgicalTablePlane() {}

	public static Plane scan(Level level, BlockPos start) {
		return scan(level, start, null);
	}

	/** Scans a surface as though one table position had already been removed. */
	static Plane scanExcluding(Level level, BlockPos start, BlockPos excluded) {
		return scan(level, start, excluded);
	}

	private static Plane scan(Level level, BlockPos start, @Nullable BlockPos excluded) {
		if (!matches(level, start, start.getY(), excluded))
			return Plane.EMPTY;

		ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
		LinkedHashSet<BlockPos> tiles = new LinkedHashSet<>();
		frontier.add(start.immutable());
		boolean complete = true;
		while (!frontier.isEmpty()) {
			BlockPos current = frontier.removeFirst();
			if (tiles.contains(current))
				continue;
			if (tiles.size() >= MAX_TILES) {
				complete = false;
				break;
			}
			if (!matches(level, current, start.getY(), excluded))
				continue;

			tiles.add(current);
			for (Direction direction : HORIZONTAL) {
				BlockPos next = current.relative(direction);
				if (!tiles.contains(next) && level.isLoaded(next))
					frontier.addLast(next.immutable());
			}
		}
		List<BlockPos> frozenTiles = List.copyOf(tiles);
		WorkArea workArea = complete ? WorkArea.of(frozenTiles, start.getY()) : WorkArea.EMPTY;
		BlockPos source = complete ? frozenTiles.stream()
			.min(Comparator.comparingInt((BlockPos pos) -> pos.getZ()).thenComparingInt(pos -> pos.getX()))
			.orElse(null) : null;
		return new Plane(frozenTiles, source, complete, workArea);
	}

	/** Keeps a newly joined surface within the same bounded scan used by normal interactions. */
	public static boolean canExtendAt(Level level, BlockPos destination) {
		Set<BlockPos> scanned = new HashSet<>();
		for (Direction direction : HORIZONTAL) {
			BlockPos neighbor = destination.relative(direction);
			if (!level.isLoaded(neighbor) || scanned.contains(neighbor)
				|| !matches(level, neighbor, destination.getY(), null))
				continue;
			Plane plane = scan(level, neighbor);
			if (!plane.complete())
				return false;
			scanned.addAll(plane.tiles());
			if (scanned.size() >= MAX_TILES)
				return false;
		}
		return true;
	}

	/** Returns all persisted footprints, or null if legacy data makes collision checks uncertain. */
	@Nullable
	public static List<SurgicalTableLayout.Footprint> occupiedFootprints(Level level, Plane plane,
		int excludedSubjectId) {
		return occupiedFootprints(level, plane, excludedSubjectId < 0 ? Set.of() : Set.of(excludedSubjectId));
	}

	/** Returns persisted footprints outside the supplied logical editing group. */
	@Nullable
	public static List<SurgicalTableLayout.Footprint> occupiedFootprints(Level level, Plane plane,
		Set<Integer> excludedSubjectIds) {
		if (!plane.valid() || plane.source() == null)
			return null;
		SurgicalTableBlockEntity controller = SurgicalTableBlockEntity.controller(level, plane);
		if (controller == null)
			return null;
		List<SurgicalTableLayout.Footprint> footprints = new ArrayList<>();
		for (SurgicalSubject subject : controller.getSubjects()) {
			if (excludedSubjectIds.contains(subject.id()))
				continue;
			if (subject.occupiedFootprints().isEmpty())
				return null;
			footprints.addAll(subject.occupiedFootprints());
		}
		return List.copyOf(footprints);
	}

	private static boolean matches(Level level, BlockPos pos, int y, @Nullable BlockPos excluded) {
		return pos.getY() == y && !pos.equals(excluded)
			&& level.getBlockState(pos).getBlock() instanceof SurgicalTableBlock;
	}

	public record Plane(List<BlockPos> tiles, @Nullable BlockPos source, boolean complete, WorkArea workArea) {
		private static final Plane EMPTY = new Plane(List.of(), null, true, WorkArea.EMPTY);

		public Plane {
			tiles = List.copyOf(tiles);
			workArea = workArea == null ? WorkArea.EMPTY : workArea;
		}

		public boolean valid() {
			return complete && source != null;
		}
	}

	/** Exact union of the connected table tiles, including gaps in its bounding box. */
	public static final class WorkArea {
		private static final WorkArea EMPTY = new WorkArea(List.of(), Set.of(), 0, 0, 0, 0, 0);

		private final List<BlockPos> tiles;
		private final Set<Long> tileKeys;
		private final int minX;
		private final int minZ;
		private final int maxXExclusive;
		private final int maxZExclusive;
		private final int y;

		private WorkArea(List<BlockPos> tiles, Set<Long> tileKeys, int minX, int minZ,
			int maxXExclusive, int maxZExclusive, int y) {
			this.tiles = List.copyOf(tiles);
			this.tileKeys = Set.copyOf(tileKeys);
			this.minX = minX;
			this.minZ = minZ;
			this.maxXExclusive = maxXExclusive;
			this.maxZExclusive = maxZExclusive;
			this.y = y;
		}

		private static WorkArea of(List<BlockPos> tiles, int y) {
			if (tiles.isEmpty())
				return EMPTY;
			Set<Long> keys = new HashSet<>(tiles.size() * 2);
			for (BlockPos tile : tiles)
				keys.add(tile.asLong());
			return new WorkArea(tiles, keys,
				tiles.stream().mapToInt(BlockPos::getX).min().orElse(0),
				tiles.stream().mapToInt(BlockPos::getZ).min().orElse(0),
				tiles.stream().mapToInt(BlockPos::getX).max().orElse(-1) + 1,
				tiles.stream().mapToInt(BlockPos::getZ).max().orElse(-1) + 1, y);
		}

		public List<BlockPos> tiles() {
			return tiles;
		}

		public int minX() {
			return minX;
		}

		public int minZ() {
			return minZ;
		}

		public int maxXExclusive() {
			return maxXExclusive;
		}

		public int maxZExclusive() {
			return maxZExclusive;
		}

		public int y() {
			return y;
		}

		public boolean isEmpty() {
			return tiles.isEmpty();
		}

		public int tileArea() {
			return tiles.size();
		}

		public boolean containsTile(BlockPos pos) {
			return pos.getY() == y && tileKeys.contains(pos.asLong());
		}

		public boolean containsTile(int x, int z) {
			return tileKeys.contains(BlockPos.asLong(x, y, z));
		}

		/** True only when every tile touched by the horizontal bounds actually exists. */
		public boolean contains(double boundMinX, double boundMinZ, double boundMaxX, double boundMaxZ,
			double epsilon) {
			if (isEmpty() || !Double.isFinite(boundMinX) || !Double.isFinite(boundMinZ)
				|| !Double.isFinite(boundMaxX) || !Double.isFinite(boundMaxZ)
				|| boundMaxX < boundMinX || boundMaxZ < boundMinZ
				|| boundMinX < minX - epsilon || boundMinZ < minZ - epsilon
				|| boundMaxX > maxXExclusive + epsilon || boundMaxZ > maxZExclusive + epsilon)
				return false;

			double sampleMinX = boundMinX + epsilon;
			double sampleMaxX = boundMaxX - epsilon;
			double sampleMinZ = boundMinZ + epsilon;
			double sampleMaxZ = boundMaxZ - epsilon;
			if (sampleMinX > sampleMaxX)
				sampleMinX = sampleMaxX = (boundMinX + boundMaxX) * 0.5d;
			if (sampleMinZ > sampleMaxZ)
				sampleMinZ = sampleMaxZ = (boundMinZ + boundMaxZ) * 0.5d;
			int firstX = (int) Math.floor(sampleMinX);
			int lastX = (int) Math.floor(sampleMaxX);
			int firstZ = (int) Math.floor(sampleMinZ);
			int lastZ = (int) Math.floor(sampleMaxZ);
			for (int x = firstX; x <= lastX; x++)
				for (int z = firstZ; z <= lastZ; z++)
					if (!tileKeys.contains(BlockPos.asLong(x, y, z)))
						return false;
			return true;
		}
	}
}
