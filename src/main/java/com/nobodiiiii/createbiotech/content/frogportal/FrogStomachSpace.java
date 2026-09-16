package com.nobodiiiii.createbiotech.content.frogportal;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pure geometry + builder for the private rooms in the Frog Stomach dimension. Each space index maps
 * to a fixed, non-overlapping room laid out on a grid so coordinates stay bounded. A room is a hollow
 * rectangular prism walled with the indestructible {@link CBBlocks#FROG_STOMACH_WALL} and lined
 * with generated living terrain. Its north wall contains a high, pre-activated mouth portal and its
 * south wall contains a low, slimeball-activated tail portal.
 */
public final class FrogStomachSpace {

	/** Empty blocks left between adjacent rooms. */
	private static final int GAP = 16;
	/** Rooms per grid row before wrapping to the next row of the layout. */
	private static final int ROW = 4096;
	/** Y of the room's floor shell. */
	private static final int BASE_Y = 0;
	private static final int PORTAL_SECTION_SIZE = 16;
	private static final int PORTAL_SIZE = 4;
	private static final int PORTAL_SECTION_PADDING = (PORTAL_SECTION_SIZE - PORTAL_SIZE) / 2;
	private static final int PORTAL_CLEARANCE_DEPTH = 5;
	private static final int MIN_INITIAL_SLIMES = 3;
	private static final int INITIAL_SLIME_VARIATION = 3;
	private static final double MOUTH_ENTRY_SPEED = 0.5d;

	private FrogStomachSpace() {}

	public enum PortalType {
		MOUTH,
		TAIL
	}

	/** Horizontal edge length of a room in blocks, from server config (default 48 = 3x3 chunks). */
	public static int width() {
		return Math.max(PORTAL_SECTION_SIZE, CBConfigs.SERVER.frogStomach.width.get());
	}

	/** Vertical height of a room in blocks, from server config (default 32). */
	public static int height() {
		return Math.max(PORTAL_SECTION_SIZE, CBConfigs.SERVER.frogStomach.height.get());
	}

	/** Lowest-corner (min x/y/z) block position of the room for {@code index}. */
	public static BlockPos origin(long index) {
		int spacing = width() + GAP;
		int col = (int) Math.floorMod(index, (long) ROW);
		int row = (int) Math.floorDiv(index, (long) ROW);
		return new BlockPos(col * spacing, BASE_Y, row * spacing);
	}

	/** Bottom-left block of the 4x4 tail exit, centred in the lower 16x16 wall section. */
	public static BlockPos tailPortalPos(long index) {
		BlockPos o = origin(index);
		int width = width();
		return new BlockPos(portalSectionX(o, width), o.getY() + PORTAL_SECTION_PADDING,
			o.getZ() + width - 2);
	}

	/** Bottom-left block of the 4x4 mouth entrance, centred in the upper 16x16 wall section. */
	public static BlockPos mouthPortalPos(long index) {
		BlockPos o = origin(index);
		int width = width();
		return new BlockPos(portalSectionX(o, width),
			o.getY() + height() - PORTAL_SECTION_SIZE + PORTAL_SECTION_PADDING,
			o.getZ() + 1);
	}

	/** Exact centre of the mouth portal, used by everything entering through the Giant Frog's mouth. */
	public static Vec3 mouthPortalCenter(long index) {
		BlockPos portal = mouthPortalPos(index);
		return new Vec3(portal.getX() + PORTAL_SIZE / 2.0d,
			portal.getY() + PORTAL_SIZE / 2.0d, portal.getZ() + 0.5d);
	}

	private static int portalSectionX(BlockPos origin, int width) {
		return origin.getX() + (width - PORTAL_SECTION_SIZE) / 2 + PORTAL_SECTION_PADDING;
	}

	/** The southward impulse applied after an entity or item arrives through the mouth. */
	public static Vec3 mouthEntryVelocity() {
		return Vec3.atLowerCornerOf(Direction.SOUTH.getNormal())
			.scale(MOUTH_ENTRY_SPEED);
	}

	public static long spaceIndexFromDigestiveTractPos(BlockPos pos) {
		long index = spaceIndexAt(pos);
		return index >= 0 && portalTypeFromDigestiveTractPos(index, pos) != null ? index : -1L;
	}

	/** Returns the room containing {@code pos}, or {@code -1} when it lies in a grid gap. */
	static long spaceIndexAt(BlockPos pos) {
		int spacing = width() + GAP;
		long col = Math.floorDiv(pos.getX(), spacing);
		long row = Math.floorDiv(pos.getZ(), spacing);
		if (col < 0 || col >= ROW || row < 0)
			return -1L;
		int localX = Math.floorMod(pos.getX(), spacing);
		int localZ = Math.floorMod(pos.getZ(), spacing);
		return localX < width() && localZ < width() ? row * ROW + col : -1L;
	}

	@Nullable
	public static PortalType portalTypeFromDigestiveTractPos(long index, BlockPos pos) {
		for (PortalType type : PortalType.values())
			if (isPortalPos(index, type, pos) || isWallPos(index, type, pos))
				return type;
		return null;
	}

	/** Checks the shell, both wall rings, and the permanently active mouth portal. */
	public static boolean isBuilt(ServerLevel level, long index) {
		if (!level.getBlockState(origin(index)).is(CBBlocks.FROG_STOMACH_WALL.get()))
			return false;
		for (PortalType type : PortalType.values())
			for (BlockPos wallPos : wallPositions(index, type))
				if (!level.getBlockState(wallPos).is(CBBlocks.FROG_DIGESTIVE_TRACT_WALL.get()))
					return false;
		for (BlockPos wallPos : wallPositions(index, PortalType.MOUTH))
			if (!level.getBlockState(wallPos).getValue(FrogDigestiveTractWallBlock.HAS_SLIME))
				return false;
		for (BlockPos portalPos : portalPositions(index, PortalType.MOUTH))
			if (!level.getBlockState(portalPos).is(CBBlocks.FROG_DIGESTIVE_TRACT.get()))
				return false;
		return true;
	}

	/**
	 * Build (or repair) the room for {@code index}: force-loads the covered chunks, places the six
	 * indestructible wall faces, optionally generates the mucosa ecology for a newly allocated room,
	 * and places a pre-activated high mouth portal plus an inactive low tail portal. Repairing an
	 * existing room deliberately leaves player changes to its interior untouched.
	 */
	public static void buildRoom(ServerLevel level, long index, boolean generateEcology) {
		int width = width();
		int height = height();
		BlockPos o = origin(index);
		int minX = o.getX(), minY = o.getY(), minZ = o.getZ();
		int maxX = minX + width - 1, maxY = minY + height - 1, maxZ = minZ + width - 1;

		for (int cx = minX >> 4; cx <= (maxX >> 4); cx++)
			for (int cz = minZ >> 4; cz <= (maxZ >> 4); cz++)
				level.getChunk(cx, cz);

		BlockState wall = CBBlocks.FROG_STOMACH_WALL.get().defaultBlockState();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = minX; x <= maxX; x++)
			for (int y = minY; y <= maxY; y++)
				for (int z = minZ; z <= maxZ; z++) {
					boolean shell = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
					if (shell) {
						p.set(x, y, z);
						level.setBlock(p, wall, Block.UPDATE_CLIENTS);
					}
				}

		if (generateEcology)
			FrogStomachEcology.generate(level, index, o, width, height);

		// Keep the full 6x6 framed area clear for five blocks in front, while preserving vines.
		// The mouth field is installed again below after generated terrain is removed from its opening.
		clearPortalAccess(level, index, PortalType.TAIL);
		clearPortalAccess(level, index, PortalType.MOUTH);

		placeWallRing(level, index, PortalType.TAIL, false);
		placeWallRing(level, index, PortalType.MOUTH, true);
		activatePortal(level, index, PortalType.MOUTH, false);

		// Remove the old, single floor portal when an existing room is migrated to the walled exit.
		BlockPos legacyPortal = o.offset(2, 1, 2);
		if (level.getBlockState(legacyPortal).is(CBBlocks.FROG_DIGESTIVE_TRACT.get()))
			level.setBlock(legacyPortal, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);

		if (generateEcology)
			spawnInitialSlimes(level, o, width, height);
	}

	private static void spawnInitialSlimes(ServerLevel level, BlockPos origin, int width, int height) {
		RandomSource random = level.getRandom();
		int targetCount = MIN_INITIAL_SLIMES + random.nextInt(INITIAL_SLIME_VARIATION);
		List<BlockPos> spawnPositions = findSlimeSpawnPositions(level, origin, width, height, true);
		if (spawnPositions.size() < targetCount)
			spawnPositions.addAll(findSlimeSpawnPositions(level, origin, width, height, false));

		int spawned = 0;
		while (spawned < targetCount && !spawnPositions.isEmpty()) {
			BlockPos spawnPos = spawnPositions.remove(random.nextInt(spawnPositions.size()));
			if (EntityType.SLIME.spawn(level, spawnPos, MobSpawnType.STRUCTURE) != null)
				spawned++;
		}
	}

	private static List<BlockPos> findSlimeSpawnPositions(ServerLevel level, BlockPos origin, int width,
		int height, boolean ecologySurfaceOnly) {
		List<BlockPos> positions = new ArrayList<>();
		BlockPos.MutableBlockPos spawnPos = new BlockPos.MutableBlockPos();
		int maxSpawnY = Math.min(origin.getY() + height - 2,
			origin.getY() + FrogStomachEcology.MAX_FLOOR_SURFACE_OFFSET
				+ FrogStomachEcology.MAX_SECRETION_GROWTH_DEPTH + 1);
		for (int x = origin.getX() + 1; x < origin.getX() + width - 1; x++)
			for (int z = origin.getZ() + 1; z < origin.getZ() + width - 1; z++)
				for (int y = origin.getY() + 1; y <= maxSpawnY; y++) {
					spawnPos.set(x, y, z);
					BlockState support = level.getBlockState(spawnPos.below());
					boolean validSupport = ecologySurfaceOnly
						? support.is(CBBlocks.FROG_STOMACH_SECRETION.get())
						: support.is(CBBlocks.FROG_STOMACH_WALL.get());
					if (validSupport && level.getBlockState(spawnPos).isAir()
						&& level.getBlockState(spawnPos.above()).isAir())
						positions.add(spawnPos.immutable());
				}
		return positions;
	}

	private static void clearPortalAccess(ServerLevel level, long index, PortalType type) {
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos portal = portalPos(index, type);
		for (BlockPos portalBlock : portalPositions(index, type))
			level.setBlock(portalBlock, air, Block.UPDATE_CLIENTS);

		Direction front = type == PortalType.MOUTH ? Direction.SOUTH : Direction.NORTH;
		for (int depth = 1; depth <= PORTAL_CLEARANCE_DEPTH; depth++)
			for (int x = -1; x <= PORTAL_SIZE; x++)
				for (int y = -1; y <= PORTAL_SIZE; y++) {
					BlockPos accessPos = portal.offset(x, y, 0).relative(front, depth);
					if (!isVine(level.getBlockState(accessPos)))
						level.setBlock(accessPos, air, Block.UPDATE_CLIENTS);
				}
	}

	/** Prevents generated folds and fungi from intruding into either portal's framed approach. */
	static boolean isPortalApproachProtected(long index, BlockPos pos) {
		for (PortalType type : PortalType.values()) {
			BlockPos portal = portalPos(index, type);
			int offsetX = pos.getX() - portal.getX();
			int offsetY = pos.getY() - portal.getY();
			if (offsetX < -1 || offsetX > PORTAL_SIZE || offsetY < -1 || offsetY > PORTAL_SIZE)
				continue;

			Direction front = type == PortalType.MOUTH ? Direction.SOUTH : Direction.NORTH;
			int depth = front == Direction.SOUTH
				? pos.getZ() - portal.getZ()
				: portal.getZ() - pos.getZ();
			if (depth >= 1 && depth <= PORTAL_CLEARANCE_DEPTH)
				return true;
		}
		return false;
	}

	private static boolean isVine(BlockState state) {
		return state.is(Blocks.VINE)
			|| state.is(Blocks.CAVE_VINES)
			|| state.is(Blocks.CAVE_VINES_PLANT)
			|| state.is(Blocks.WEEPING_VINES)
			|| state.is(Blocks.WEEPING_VINES_PLANT)
			|| state.is(Blocks.TWISTING_VINES)
			|| state.is(Blocks.TWISTING_VINES_PLANT);
	}

	/**
	 * Activates the generated room exit containing {@code filledWallPos}, if every digestive-tract
	 * wall block has received a slimeball.
	 */
	public static boolean tryActivatePortal(ServerLevel level, BlockPos filledWallPos) {
		if (!level.dimension().equals(FrogStomachDimensions.FROG_STOMACH))
			return false;
		long index = spaceIndexFromDigestiveTractPos(filledWallPos);
		PortalType type = index >= 0 ? portalTypeFromDigestiveTractPos(index, filledWallPos) : null;
		if (type == null || !isWallPos(index, type, filledWallPos))
			return false;
		for (BlockPos wallPos : wallPositions(index, type)) {
			BlockState state = level.getBlockState(wallPos);
			if (!state.is(CBBlocks.FROG_DIGESTIVE_TRACT_WALL.get())
				|| !state.getValue(FrogDigestiveTractWallBlock.HAS_SLIME))
				return false;
		}

		activatePortal(level, index, type, true);
		return true;
	}

	private static void placeWallRing(ServerLevel level, long index, PortalType type, boolean activated) {
		Direction facing = type == PortalType.MOUTH ? Direction.SOUTH : Direction.NORTH;
		BlockState wall = CBBlocks.FROG_DIGESTIVE_TRACT_WALL.get()
			.defaultBlockState()
			.setValue(FrogDigestiveTractWallBlock.HAS_SLIME, activated)
			.setValue(FrogDigestiveTractWallBlock.FACING, facing);
		for (BlockPos wallPos : wallPositions(index, type))
			level.setBlock(wallPos, wall, Block.UPDATE_CLIENTS);
	}

	private static void activatePortal(ServerLevel level, long index, PortalType type, boolean playEffect) {
		BlockState portal = CBBlocks.FROG_DIGESTIVE_TRACT.get()
			.defaultBlockState()
			.setValue(FrogDigestiveTractBehaviour.AXIS, Direction.Axis.X);
		for (BlockPos portalPos : portalPositions(index, type)) {
			level.setBlock(portalPos, portal, Block.UPDATE_CLIENTS);
			if (level.getBlockEntity(portalPos) instanceof FrogDigestiveTractBlockEntity digestiveTract)
				digestiveTract.setBinding(index, type);
		}
		if (playEffect)
			level.globalLevelEvent(1038,
				portalPos(index, type).offset(PORTAL_SIZE / 2, PORTAL_SIZE / 2, 0), 0);
	}

	private static BlockPos[] wallPositions(long index, PortalType type) {
		BlockPos portal = portalPos(index, type);
		BlockPos[] positions = new BlockPos[PORTAL_SIZE * 4];
		int next = 0;
		for (int x = 0; x < PORTAL_SIZE; x++) {
			positions[next++] = portal.offset(x, -1, 0);
			positions[next++] = portal.offset(x, PORTAL_SIZE, 0);
		}
		for (int y = 0; y < PORTAL_SIZE; y++) {
			positions[next++] = portal.offset(-1, y, 0);
			positions[next++] = portal.offset(PORTAL_SIZE, y, 0);
		}
		return positions;
	}

	private static BlockPos[] portalPositions(long index, PortalType type) {
		BlockPos portal = portalPos(index, type);
		BlockPos[] positions = new BlockPos[PORTAL_SIZE * PORTAL_SIZE];
		int next = 0;
		for (int x = 0; x < PORTAL_SIZE; x++)
			for (int y = 0; y < PORTAL_SIZE; y++)
				positions[next++] = portal.offset(x, y, 0);
		return positions;
	}

	private static boolean isPortalPos(long index, PortalType type, BlockPos pos) {
		BlockPos portal = portalPos(index, type);
		return pos.getZ() == portal.getZ()
			&& pos.getX() >= portal.getX() && pos.getX() < portal.getX() + PORTAL_SIZE
			&& pos.getY() >= portal.getY() && pos.getY() < portal.getY() + PORTAL_SIZE;
	}

	private static boolean isWallPos(long index, PortalType type, BlockPos pos) {
		BlockPos portal = portalPos(index, type);
		if (pos.getZ() != portal.getZ())
			return false;
		int dx = pos.getX() - portal.getX();
		int dy = pos.getY() - portal.getY();
		boolean horizontalEdge = dx >= 0 && dx < PORTAL_SIZE && (dy == -1 || dy == PORTAL_SIZE);
		boolean verticalEdge = dy >= 0 && dy < PORTAL_SIZE && (dx == -1 || dx == PORTAL_SIZE);
		return horizontalEdge || verticalEdge;
	}

	private static BlockPos portalPos(long index, PortalType type) {
		return type == PortalType.MOUTH ? mouthPortalPos(index) : tailPortalPos(index);
	}
}
