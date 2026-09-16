package com.nobodiiiii.createbiotech.content.frogportal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.registry.CBBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.level.material.Fluids;

/**
 * Deterministic ecology generation for a newly allocated Frog Stomach room. The hard stomach-wall
 * shell stays as the room boundary while a softer mucosa layer forms rolling ground, an irregular
 * ceiling and walls, water, tissue folds, and hanging glow-berry vines inside it.
 */
final class FrogStomachEcology {

	static final int MAX_FLOOR_SURFACE_OFFSET = 6;
	static final int MAX_SECRETION_GROWTH_DEPTH = 3;

	private static final long ROOM_SEED_SALT = 0x6A09E667F3BCC909L;
	private static final int MIN_LINING_THICKNESS = 1;
	private static final int MAX_LINING_THICKNESS = 3;
	private static final int MIN_POOL_ROOM_SIZE = 12;
	private static final int MIN_SECRETION_ROOM_SIZE = 18;
	private static final int POOL_WATER_LEVEL_OFFSET = 4;
	private static final double POOL_DEEPENING_DISTANCE = 0.65d;
	private static final int MIN_SECRETION_GROWTHS = 4;
	private static final int SECRETION_GROWTH_VARIATION = 5;
	private static final int MAX_SECRETION_RADIUS = 6;
	private static final int EMBEDDED_CONTENT_CHANCE = 40;
	private static final int MIN_UNGROWN_FUNGI = 10;
	private static final int UNGROWN_FUNGUS_VARIATION = 7;
	private static final int FUNGUS_PLACEMENT_ATTEMPTS = 32;
	private static final float VINE_FROGLIGHT_CHANCE = 0.25f;
	private static final BlockState[] FROGLIGHTS = {
		Blocks.OCHRE_FROGLIGHT.defaultBlockState(),
		Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(),
		Blocks.VERDANT_FROGLIGHT.defaultBlockState()
	};
	private static final Direction[] SECRETION_NORMALS = {
		Direction.UP,
		Direction.DOWN,
		Direction.SOUTH,
		Direction.NORTH,
		Direction.EAST,
		Direction.WEST
	};
	private static final Direction[] FOLD_WALLS = {
		Direction.NORTH,
		Direction.SOUTH,
		Direction.WEST,
		Direction.EAST
	};
	private static final Direction[] FUNGUS_SURFACES = Direction.values();

	private FrogStomachEcology() {}

	static void generate(ServerLevel level, long index, BlockPos origin, int width, int height) {
		long seed = level.getSeed()
			^ Long.rotateLeft(index * 0x9E3779B97F4A7C15L, 17)
			^ ROOM_SEED_SALT;
		RandomSource random = RandomSource.create(seed);
		SimplexNoise terrainNoise = new SimplexNoise(random);
		SimplexNoise terrainDetailNoise = new SimplexNoise(random);
		SimplexNoise liningNoise = new SimplexNoise(random);
		SimplexNoise poolShoreNoise = new SimplexNoise(random);
		SimplexNoise poolDepthNoise = new SimplexNoise(random);

		generateCeilingAndWallLining(level, origin, width, height, liningNoise);
		Pool pool = generateFloorAndPool(level, origin, width, height, random, terrainNoise, terrainDetailNoise,
			poolShoreNoise, poolDepthNoise);
		List<BlockPos> foldVineAnchors = generateWallFolds(level, index, origin, width, height, random);
		if (pool != null)
			generatePoolWaterfallFold(level, index, origin, width, height, pool, random);
		generateSecretionGrowths(level, origin, width, height, random);
		int foldVineCount = Math.min(12, foldVineAnchors.size());
		for (int i = 0; i < foldVineCount; i++)
			generateGlowBerryVine(level, foldVineAnchors.remove(random.nextInt(foldVineAnchors.size())),
				origin.getY(), random);
		generateCeilingVines(level, origin, width, height, random);
		generateStomachFungi(level, index, origin, width, height, random);
	}

	private static void generateCeilingAndWallLining(ServerLevel level, BlockPos origin, int width,
		int height, SimplexNoise noise) {
		BlockState mucosa = CBBlocks.FROG_STOMACH_MUCOSA.get().defaultBlockState();
		int minX = origin.getX();
		int minY = origin.getY();
		int minZ = origin.getZ();
		int maxX = minX + width - 1;
		int maxY = minY + height - 1;
		int maxZ = minZ + width - 1;
		int ceilingThickness = maximumLiningThickness(height);
		int wallThickness = maximumLiningThickness(width);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = minX + 1; x < maxX; x++)
			for (int z = minZ + 1; z < maxZ; z++) {
				int thickness = liningThickness(noise, x - minX, z - minZ, 0, ceilingThickness);
				for (int depth = 1; depth <= thickness; depth++)
					setMucosa(level, pos.set(x, maxY - depth, z), mucosa);
			}

		for (int x = minX + 1; x < maxX; x++)
			for (int y = minY + 1; y < maxY; y++) {
				int northThickness = liningThickness(noise, x - minX, y - minY, 1, wallThickness);
				int southThickness = liningThickness(noise, x - minX, y - minY, 2, wallThickness);
				for (int depth = 1; depth <= northThickness; depth++)
					setMucosa(level, pos.set(x, y, minZ + depth), mucosa);
				for (int depth = 1; depth <= southThickness; depth++)
					setMucosa(level, pos.set(x, y, maxZ - depth), mucosa);
			}

		for (int z = minZ + 1; z < maxZ; z++)
			for (int y = minY + 1; y < maxY; y++) {
				int westThickness = liningThickness(noise, z - minZ, y - minY, 3, wallThickness);
				int eastThickness = liningThickness(noise, z - minZ, y - minY, 4, wallThickness);
				for (int depth = 1; depth <= westThickness; depth++)
					setMucosa(level, pos.set(minX + depth, y, z), mucosa);
				for (int depth = 1; depth <= eastThickness; depth++)
					setMucosa(level, pos.set(maxX - depth, y, z), mucosa);
			}
	}

	private static int liningThickness(SimplexNoise noise, int firstCoordinate, int secondCoordinate,
		int faceSalt, int maximumThickness) {
		double value = noise.getValue(
			firstCoordinate / 7.0d + faceSalt * 31.75d,
			secondCoordinate / 7.0d - faceSalt * 19.25d);
		int range = maximumThickness - MIN_LINING_THICKNESS + 1;
		return MIN_LINING_THICKNESS + Mth.clamp((int) Math.floor((value + 1.0d) * range / 2.0d),
			0, range - 1);
	}

	private static int maximumLiningThickness(int size) {
		return Math.max(MIN_LINING_THICKNESS,
			Math.min(MAX_LINING_THICKNESS, (size - 3) / 2));
	}

	private static Pool generateFloorAndPool(ServerLevel level, BlockPos origin, int width, int height,
		RandomSource random, SimplexNoise terrainNoise, SimplexNoise detailNoise, SimplexNoise poolShoreNoise,
		SimplexNoise poolDepthNoise) {
		BlockState mucosa = CBBlocks.FROG_STOMACH_MUCOSA.get().defaultBlockState();
		BlockState water = Blocks.WATER.defaultBlockState();
		int minX = origin.getX();
		int minY = origin.getY();
		int minZ = origin.getZ();
		int maxX = minX + width - 1;
		int maxZ = minZ + width - 1;
		int maximumSurfaceY = minY + Math.max(1, height - maximumLiningThickness(height) - 3);
		Pool pool = createPool(random, origin, width, poolShoreNoise, poolDepthNoise);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = minX + 1; x < maxX; x++)
			for (int z = minZ + 1; z < maxZ; z++) {
				int surfaceY = Math.min(maximumSurfaceY,
					terrainSurfaceY(minY, x - minX, z - minZ, terrainNoise, detailNoise));
				int waterDepth = pool == null ? 0 : pool.depthAt(x, z);
				if (waterDepth > 0)
					surfaceY = pool.waterY() - waterDepth;
				else if (pool != null && pool.isBank(x, z))
					surfaceY = Math.max(surfaceY, pool.waterY());

				for (int y = minY + 1; y <= surfaceY; y++)
					setMucosa(level, pos.set(x, y, z), mucosa);

				if (waterDepth > 0)
					for (int y = surfaceY + 1; y <= pool.waterY(); y++)
						level.setBlock(pos.set(x, y, z), water, Block.UPDATE_CLIENTS);
			}
		return pool;
	}

	private static int terrainSurfaceY(int floorY, int localX, int localZ, SimplexNoise terrainNoise,
		SimplexNoise detailNoise) {
		double rolling = terrainNoise.getValue(localX / 18.0d, localZ / 18.0d);
		double detail = detailNoise.getValue(localX / 7.0d, localZ / 7.0d);
		double combined = rolling * 0.72d + detail * 0.28d;
		int relief = Mth.clamp((int) Math.floor((combined + 1.0d) * 2.0d), 0, 3);
		return floorY + 3 + relief;
	}

	private static Pool createPool(RandomSource random, BlockPos origin, int size, SimplexNoise shoreNoise,
		SimplexNoise depthNoise) {
		if (size < MIN_POOL_ROOM_SIZE)
			return null;
		int interiorWidth = size - 2;
		int radiusLimit = Math.max(3, Math.min(8, interiorWidth / 4));
		int radiusX = Math.max(3, radiusLimit - random.nextInt(3));
		int radiusZ = Math.max(3, radiusLimit - random.nextInt(3));
		int maximumMargin = Math.max(1, (interiorWidth - 1) / 2);
		int wallMargin = Math.min(radiusLimit + 4, maximumMargin);
		int centerX = randomCoordinate(random,
			origin.getX() + 1 + wallMargin, origin.getX() + size - 2 - wallMargin);
		int centerZ = randomCoordinate(random,
			origin.getZ() + 1 + wallMargin, origin.getZ() + size - 2 - wallMargin);

		List<PoolLobe> lobes = new ArrayList<>();
		lobes.add(new PoolLobe(centerX, centerZ, radiusX, radiusZ));
		int lobeCount = 4 + random.nextInt(4);
		for (int i = 1; i < lobeCount; i++) {
			int lobeRadiusX = Math.max(2, radiusX - random.nextInt(4));
			int lobeRadiusZ = Math.max(2, radiusZ - random.nextInt(4));
			double angle = random.nextDouble() * Math.PI * 2.0d;
			double displacement = Math.min(radiusX, radiusZ) * (0.25d + random.nextDouble() * 0.95d);
			int candidateX = centerX + Mth.floor(Math.cos(angle) * displacement);
			int candidateZ = centerZ + Mth.floor(Math.sin(angle) * displacement);
			int lobeCenterX = clampPoolLobeCenter(candidateX, origin.getX(), size, lobeRadiusX, centerX);
			int lobeCenterZ = clampPoolLobeCenter(candidateZ, origin.getZ(), size, lobeRadiusZ, centerZ);
			lobes.add(new PoolLobe(lobeCenterX, lobeCenterZ, lobeRadiusX, lobeRadiusZ));
		}
		return new Pool(List.copyOf(lobes), shoreNoise, depthNoise, origin.getY(),
			origin.getY() + POOL_WATER_LEVEL_OFFSET);
	}

	private static int randomCoordinate(RandomSource random, int minimum, int maximum) {
		if (maximum <= minimum)
			return minimum;
		return minimum + random.nextInt(maximum - minimum + 1);
	}

	private static int clampPoolLobeCenter(int candidate, int roomMinimum, int size, int radius,
		int fallback) {
		int minimum = roomMinimum + 1 + radius + 3;
		int maximum = roomMinimum + size - 2 - radius - 3;
		return minimum <= maximum ? Mth.clamp(candidate, minimum, maximum) : fallback;
	}

	private static void generateSecretionGrowths(ServerLevel level, BlockPos origin, int width, int height,
		RandomSource random) {
		if (width < MIN_SECRETION_ROOM_SIZE)
			return;
		int maximumRadius = Math.max(2, Math.min(MAX_SECRETION_RADIUS, (width - 6) / 4));
		generateGuaranteedFloorSecretion(level, origin, width, height, maximumRadius, random);
		int targetCount = MIN_SECRETION_GROWTHS + random.nextInt(SECRETION_GROWTH_VARIATION);
		int attempts = targetCount * 6;
		int generated = 0;
		while (generated < targetCount && attempts-- > 0) {
			Direction normal = SECRETION_NORMALS[random.nextInt(SECRETION_NORMALS.length)];
			int radiusFirst = Math.max(2, maximumRadius - random.nextInt(3));
			int radiusSecond = Math.max(2, maximumRadius - random.nextInt(3));
			int margin = maximumRadius + 3;
			SurfaceCoordinates center = randomSurfaceCoordinates(random, origin, width, height, normal, margin);
			if (center == null)
				continue;
			int depth = 2 + random.nextInt(2);
			if (placeSecretionGrowth(level, origin, width, height, normal, center, radiusFirst, radiusSecond,
				depth, random))
				generated++;
		}
	}

	private static void generateGuaranteedFloorSecretion(ServerLevel level, BlockPos origin, int width,
		int height, int maximumRadius, RandomSource random) {
		int margin = maximumRadius + 3;
		for (int attempt = 0; attempt < 64; attempt++) {
			SurfaceCoordinates center = randomSurfaceCoordinates(random, origin, width, height,
				Direction.UP, margin);
			if (center != null && placeSecretionGrowth(level, origin, width, height, Direction.UP, center,
				maximumRadius, Math.max(2, maximumRadius - random.nextInt(2)),
				MAX_SECRETION_GROWTH_DEPTH, random))
				return;
		}

		int minX = origin.getX() + 3;
		int maxX = origin.getX() + width - 4;
		int minZ = origin.getZ() + 3;
		int maxZ = origin.getZ() + width - 4;
		for (int x = minX; x <= maxX; x++)
			for (int z = minZ; z <= maxZ; z++)
				if (placeSecretionGrowth(level, origin, width, height, Direction.UP,
					new SurfaceCoordinates(x, z), 2, 2, 2, random))
					return;
	}

	private static SurfaceCoordinates randomSurfaceCoordinates(RandomSource random, BlockPos origin, int width,
		int height, Direction normal, int margin) {
		int firstMinimum;
		int firstMaximum;
		if (normal.getAxis() == Direction.Axis.X) {
			firstMinimum = origin.getZ() + margin;
			firstMaximum = origin.getZ() + width - 1 - margin;
		} else {
			firstMinimum = origin.getX() + margin;
			firstMaximum = origin.getX() + width - 1 - margin;
		}

		int secondMinimum;
		int secondMaximum;
		if (normal.getAxis() == Direction.Axis.Y) {
			secondMinimum = origin.getZ() + margin;
			secondMaximum = origin.getZ() + width - 1 - margin;
		} else {
			secondMinimum = origin.getY() + margin;
			secondMaximum = origin.getY() + height - 1 - margin;
		}
		if (firstMinimum > firstMaximum || secondMinimum > secondMaximum)
			return null;
		return new SurfaceCoordinates(
			randomCoordinate(random, firstMinimum, firstMaximum),
			randomCoordinate(random, secondMinimum, secondMaximum));
	}

	private static boolean placeSecretionGrowth(ServerLevel level, BlockPos origin, int width, int height,
		Direction normal, SurfaceCoordinates center, int radiusFirst, int radiusSecond, int maximumDepth,
		RandomSource random) {
		if (findLiningSurface(level, origin, width, height, normal, center.first(), center.second()) == null)
			return false;
		BlockState secretion = CBBlocks.FROG_STOMACH_SECRETION.get().defaultBlockState();
		List<BlockPos> placed = new ArrayList<>();
		double firstPhase = random.nextDouble() * Math.PI * 2.0d;
		double secondPhase = random.nextDouble() * Math.PI * 2.0d;
		for (int firstOffset = -radiusFirst; firstOffset <= radiusFirst; firstOffset++)
			for (int secondOffset = -radiusSecond; secondOffset <= radiusSecond; secondOffset++) {
				double dx = firstOffset / (double) radiusFirst;
				double dz = secondOffset / (double) radiusSecond;
				double angle = Math.atan2(dz, dx);
				double outline = 1.0d
					+ 0.16d * Math.sin(angle * 3.0d + firstPhase)
					+ 0.10d * Math.sin(angle * 5.0d + secondPhase);
				double distance = Math.sqrt(dx * dx + dz * dz) / outline;
				if (distance > 1.0d)
					continue;
				BlockPos surface = findLiningSurface(level, origin, width, height, normal,
					center.first() + firstOffset, center.second() + secondOffset);
				if (surface == null)
					continue;
				int columnDepth = secretionColumnDepth(distance, maximumDepth);
				for (int depth = 1; depth <= columnDepth; depth++) {
					BlockPos target = surface.relative(normal, depth);
					if (!level.getBlockState(target).isAir())
						break;
					level.setBlock(target, secretion, Block.UPDATE_CLIENTS);
					placed.add(target);
				}
			}
		if (placed.isEmpty())
			return false;
		embedSecretionContents(level, placed, random);
		return true;
	}

	private static int secretionColumnDepth(double distance, int maximumDepth) {
		if (maximumDepth <= 2)
			return distance <= 0.42d ? 2 : 1;
		if (distance <= 0.30d)
			return 3;
		return distance <= 0.66d ? 2 : 1;
	}

	private static BlockPos findLiningSurface(ServerLevel level, BlockPos origin, int width, int height,
		Direction normal, int first, int second) {
		int minX = origin.getX();
		int minY = origin.getY();
		int minZ = origin.getZ();
		int maxX = minX + width - 1;
		int maxY = minY + height - 1;
		int maxZ = minZ + width - 1;
		BlockPos surface = switch (normal) {
			case UP -> new BlockPos(first, minY + 1, second);
			case DOWN -> new BlockPos(first, maxY - 1, second);
			case SOUTH -> new BlockPos(first, second, minZ + 1);
			case NORTH -> new BlockPos(first, second, maxZ - 1);
			case EAST -> new BlockPos(minX + 1, second, first);
			case WEST -> new BlockPos(maxX - 1, second, first);
		};
		if (!isLivingLining(level.getBlockState(surface)))
			return null;
		while (isLivingLining(level.getBlockState(surface.relative(normal))))
			surface = surface.relative(normal);
		return level.getBlockState(surface.relative(normal)).isAir() ? surface : null;
	}

	private static void embedSecretionContents(ServerLevel level, List<BlockPos> secretionPositions,
		RandomSource random) {
		List<BlockPos> enclosed = new ArrayList<>();
		for (BlockPos pos : secretionPositions)
			if (level.getBlockState(pos).is(CBBlocks.FROG_STOMACH_SECRETION.get())
				&& isEnclosedSecretion(level, pos))
				enclosed.add(pos);
		if (enclosed.isEmpty())
			return;

		for (BlockPos pos : enclosed) {
			int roll = random.nextInt(EMBEDDED_CONTENT_CHANCE);
			if (roll == 0)
				level.setBlock(pos, FROGLIGHTS[random.nextInt(FROGLIGHTS.length)], Block.UPDATE_CLIENTS);
			else if (roll == 1)
				level.setBlock(pos, Blocks.SLIME_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	private static boolean isEnclosedSecretion(ServerLevel level, BlockPos pos) {
		return secretionNeighbourCount(level, pos) == Direction.values().length;
	}

	private static int secretionNeighbourCount(ServerLevel level, BlockPos pos) {
		int count = 0;
		for (Direction direction : Direction.values()) {
			BlockState neighbour = level.getBlockState(pos.relative(direction));
			if (neighbour.is(CBBlocks.FROG_STOMACH_SECRETION.get())
				|| isLivingLining(neighbour)
				|| neighbour.is(CBBlocks.FROG_STOMACH_WALL.get()))
				count++;
		}
		return count;
	}

	private static List<BlockPos> generateWallFolds(ServerLevel level, long index, BlockPos origin, int width,
		int height, RandomSource random) {
		BlockState fold = CBBlocks.FROG_STOMACH_FOLD.get().defaultBlockState();
		List<BlockPos> vineAnchors = new ArrayList<>();
		for (Direction wall : FOLD_WALLS) {
			int[][] depths = FrogStomachFoldGeometry.wallDepths(width, height, random.nextLong());
			for (int along = 1; along < width - 1; along++)
				for (int y = 1; y < height - 1; y++)
					for (int depth = 1; depth <= depths[along][y]; depth++) {
						BlockPos pos = switch (wall) {
							case NORTH -> origin.offset(along, y, depth);
							case SOUTH -> origin.offset(along, y, width - 1 - depth);
							case WEST -> origin.offset(depth, y, along);
							case EAST -> origin.offset(width - 1 - depth, y, along);
							default -> throw new IllegalArgumentException("Fold wall must be horizontal");
						};
						if (!placeFold(level, index, pos, fold))
							continue;
						if (depth == depths[along][y] && level.getBlockState(pos.below()).isAir())
							vineAnchors.add(pos);
					}
		}
		return vineAnchors;
	}

	private static void generatePoolWaterfallFold(ServerLevel level, long index, BlockPos origin, int width,
		int height, Pool pool, RandomSource random) {
		PoolEdge edge = findNearestPoolEdge(pool, origin, width, random);
		if (edge == null)
			return;

		int heightVariation = Math.max(1, height / 10);
		int desiredTopY = origin.getY() + height / 2
			+ random.nextInt(heightVariation * 2 + 1) - heightVariation;
		int minimumTopY = pool.waterY() + 4;
		int maximumTopY = origin.getY() + height - 1 - maximumLiningThickness(height) - 3;
		int topY = maximumTopY >= minimumTopY
			? Mth.clamp(desiredTopY, minimumTopY, maximumTopY)
			: Math.max(pool.waterY() + 2, maximumTopY);
		BlockState fold = CBBlocks.FROG_STOMACH_FOLD.get().defaultBlockState();
		int halfWidth = 2 + random.nextInt(2);
		double outlinePhase = random.nextDouble() * Math.PI * 2.0d;
		List<BlockPos> foldBlocks = new ArrayList<>();
		for (int offset = -halfWidth; offset <= halfWidth; offset++) {
			int edgeInset = offset == 0 ? 0 : Mth.clamp(
				Mth.floor(Math.abs(offset) / (double) halfWidth * 1.4d
					+ (Math.sin(offset * 1.73d + outlinePhase) + 1.0d) * 0.65d),
				0, Math.max(0, edge.wallDistance() - 1));
			int localDepth = Math.max(1, edge.wallDistance() - edgeInset);
			for (int inwardStep = 1; inwardStep <= localDepth; inwardStep++) {
				int x = waterfallFoldX(origin, width, edge, offset, inwardStep);
				int z = waterfallFoldZ(origin, width, edge, offset, inwardStep);
				if (x <= origin.getX() || x >= origin.getX() + width - 1
					|| z <= origin.getZ() || z >= origin.getZ() + width - 1)
					continue;
				// A floor-rooted ridge with sloped shoulders replaces the thin suspended shelf.
				int shoulderDrop = Math.abs(offset) * 2;
				int ridgeTop = topY - shoulderDrop;
				for (int y = origin.getY() + 1; y <= ridgeTop; y++)
					foldBlocks.add(new BlockPos(x, y, z));
			}
		}

		BlockPos sourcePos = new BlockPos(edge.x(), topY, edge.z());
		foldBlocks.add(sourcePos.below());
		for (Direction blockedSide : new Direction[] {
			edge.inward().getOpposite(),
			edge.inward().getClockWise(),
			edge.inward().getCounterClockWise()
		})
			foldBlocks.add(sourcePos.relative(blockedSide));
		if (FrogStomachSpace.isPortalApproachProtected(index, sourcePos)
			|| foldBlocks.stream().anyMatch(posToPlace ->
				FrogStomachSpace.isPortalApproachProtected(index, posToPlace)))
			return;
		for (BlockPos foldBlock : foldBlocks)
			placeFold(level, index, foldBlock, fold);
		level.setBlock(sourcePos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
		level.scheduleTick(sourcePos, Fluids.WATER, 1);
	}

	private static PoolEdge findNearestPoolEdge(Pool pool, BlockPos origin, int size, RandomSource random) {
		int minX = origin.getX();
		int maxX = minX + size - 1;
		int minZ = origin.getZ();
		int maxZ = minZ + size - 1;
		List<PoolEdge> candidates = new ArrayList<>();
		for (int x = minX + 1; x < maxX; x++)
			for (int z = minZ + 1; z < maxZ; z++) {
				if (!pool.contains(x, z))
					continue;
				if (!pool.contains(x - 1, z) && pool.contains(x + 1, z))
					candidates.add(new PoolEdge(x, z, Direction.EAST, x - minX));
				if (!pool.contains(x + 1, z) && pool.contains(x - 1, z))
					candidates.add(new PoolEdge(x, z, Direction.WEST, maxX - x));
				if (!pool.contains(x, z - 1) && pool.contains(x, z + 1))
					candidates.add(new PoolEdge(x, z, Direction.SOUTH, z - minZ));
				if (!pool.contains(x, z + 1) && pool.contains(x, z - 1))
					candidates.add(new PoolEdge(x, z, Direction.NORTH, maxZ - z));
			}
		if (candidates.isEmpty())
			return null;

		int nearestDistance = Integer.MAX_VALUE;
		for (PoolEdge candidate : candidates)
			nearestDistance = Math.min(nearestDistance, candidate.wallDistance());
		List<PoolEdge> nearestEdges = new ArrayList<>();
		for (PoolEdge candidate : candidates)
			if (candidate.wallDistance() == nearestDistance)
				nearestEdges.add(candidate);
		return nearestEdges.get(random.nextInt(nearestEdges.size()));
	}

	private static int waterfallFoldX(BlockPos origin, int size, PoolEdge edge, int offset,
		int inwardStep) {
		int minX = origin.getX();
		int maxX = minX + size - 1;
		return switch (edge.inward()) {
			case EAST -> minX + inwardStep;
			case WEST -> maxX - inwardStep;
			case NORTH, SOUTH -> edge.x() + offset;
			default -> throw new IllegalArgumentException("Waterfall platform direction must be horizontal");
		};
	}

	private static int waterfallFoldZ(BlockPos origin, int size, PoolEdge edge, int offset,
		int inwardStep) {
		int minZ = origin.getZ();
		int maxZ = minZ + size - 1;
		return switch (edge.inward()) {
			case SOUTH -> minZ + inwardStep;
			case NORTH -> maxZ - inwardStep;
			case EAST, WEST -> edge.z() + offset;
			default -> throw new IllegalArgumentException("Waterfall platform direction must be horizontal");
		};
	}

	private static void generateCeilingVines(ServerLevel level, BlockPos origin, int width, int height,
		RandomSource random) {
		int margin = Math.min(MAX_LINING_THICKNESS + 2, Math.max(1, (width - 3) / 2));
		int minX = origin.getX() + margin;
		int maxX = origin.getX() + width - 1 - margin;
		int minZ = origin.getZ() + margin;
		int maxZ = origin.getZ() + width - 1 - margin;
		if (minX > maxX || minZ > maxZ)
			return;

		int targetCount = Math.max(2, width / 4) + random.nextInt(Math.max(1, width / 8));
		int attempts = targetCount * 5;
		int generated = 0;
		Set<Long> usedColumns = new HashSet<>();
		while (generated < targetCount && attempts-- > 0) {
			int x = randomCoordinate(random, minX, maxX);
			int z = randomCoordinate(random, minZ, maxZ);
			long columnKey = BlockPos.asLong(x, 0, z);
			if (!usedColumns.add(columnKey))
				continue;
			BlockPos anchor = findCeilingAnchor(level, origin, height, x, z);
			if (anchor != null && generateGlowBerryVine(level, anchor, origin.getY(), random))
				generated++;
		}
	}

	private static BlockPos findCeilingAnchor(ServerLevel level, BlockPos origin, int height, int x, int z) {
		BlockPos anchor = null;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(
			x, origin.getY() + height - 2, z);
		while (cursor.getY() > origin.getY() + 2
			&& isLivingLining(level.getBlockState(cursor))) {
			anchor = cursor.immutable();
			cursor.move(Direction.DOWN);
		}
		return anchor != null && level.getBlockState(anchor.below()).isAir() ? anchor : null;
	}

	private static void generateStomachFungi(ServerLevel level, long index, BlockPos origin, int width,
		int height, RandomSource random) {
		int firstSurface = random.nextInt(FUNGUS_SURFACES.length);
		for (int offset = 0; offset < FUNGUS_SURFACES.length; offset++) {
			Direction growthDirection = FUNGUS_SURFACES[(firstSurface + offset) % FUNGUS_SURFACES.length];
			if (placeMatureFungus(level, index, origin, width, height, growthDirection, random))
				break;
		}

		BlockState fungus = CBBlocks.FROG_STOMACH_FUNGUS.get().defaultBlockState();
		int targetCount = MIN_UNGROWN_FUNGI + random.nextInt(UNGROWN_FUNGUS_VARIATION);
		int attempts = targetCount * FUNGUS_PLACEMENT_ATTEMPTS;
		int placed = 0;
		while (placed < targetCount && attempts-- > 0) {
			Direction growthDirection = FUNGUS_SURFACES[random.nextInt(FUNGUS_SURFACES.length)];
			BlockPos fungusPos = findFungusPosition(level, index, origin, width, height, growthDirection, random);
			if (fungusPos == null)
				continue;
			BlockState placedState = fungus.setValue(FrogStomachFungusBlock.FACING, growthDirection);
			if (!placedState.canSurvive(level, fungusPos))
				continue;
			level.setBlock(fungusPos, placedState, Block.UPDATE_CLIENTS);
			placed++;
		}
	}

	private static boolean placeMatureFungus(ServerLevel level, long index, BlockPos origin, int width,
		int height, Direction growthDirection, RandomSource random) {
		for (int attempt = 0; attempt < FUNGUS_PLACEMENT_ATTEMPTS * 3; attempt++) {
			BlockPos fungusPos = findFungusPosition(level, index, origin, width, height, growthDirection, random);
			if (fungusPos != null
				&& FrogStomachFungusBlock.grow(level, random, fungusPos, growthDirection))
				return true;
		}
		return false;
	}

	@Nullable
	private static BlockPos findFungusPosition(ServerLevel level, long index, BlockPos origin, int width,
		int height, Direction growthDirection, RandomSource random) {
		SurfaceCoordinates coordinates = randomSurfaceCoordinates(random, origin, width, height,
			growthDirection, MAX_LINING_THICKNESS + 2);
		if (coordinates == null)
			return null;
		BlockPos surface = findLiningSurface(level, origin, width, height, growthDirection,
			coordinates.first(), coordinates.second());
		if (surface == null)
			return null;
		BlockPos fungusPos = surface.relative(growthDirection);
		return level.getBlockState(fungusPos).isAir()
			&& !FrogStomachSpace.isPortalApproachProtected(index, fungusPos)
			? fungusPos
			: null;
	}

	private static boolean generateGlowBerryVine(ServerLevel level, BlockPos anchor, int floorY,
		RandomSource random) {
		if (!isLivingLining(level.getBlockState(anchor)))
			return false;
		int requestedLength = 3 + random.nextInt(6);
		boolean attachFroglight = random.nextFloat() < VINE_FROGLIGHT_CHANCE;
		int requestedSpace = requestedLength + (attachFroglight ? 1 : 0);
		int availableLength = 0;
		BlockPos.MutableBlockPos cursor = anchor.mutable().move(Direction.DOWN);
		while (availableLength < requestedSpace && cursor.getY() > floorY + 2
			&& level.getBlockState(cursor).isAir()) {
			availableLength++;
			cursor.move(Direction.DOWN);
		}
		if (availableLength == 0)
			return false;
		if (attachFroglight && availableLength < 2)
			attachFroglight = false;
		int vineLength = Math.min(requestedLength, availableLength - (attachFroglight ? 1 : 0));
		if (vineLength == 0)
			return false;

		for (int depth = 1; depth <= vineLength; depth++) {
			boolean head = depth == vineLength;
			BlockState vine = (head ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT)
				.defaultBlockState()
				.setValue(CaveVines.BERRIES, head || random.nextFloat() < 0.35f);
			level.setBlock(anchor.below(depth), vine, Block.UPDATE_CLIENTS);
		}
		if (attachFroglight)
			level.setBlock(anchor.below(vineLength + 1), FROGLIGHTS[random.nextInt(FROGLIGHTS.length)],
				Block.UPDATE_CLIENTS);
		return true;
	}

	private static boolean isLivingLining(BlockState state) {
		return state.is(CBBlocks.FROG_STOMACH_MUCOSA.get()) || state.is(CBBlocks.FROG_STOMACH_FOLD.get());
	}

	private static boolean placeFold(ServerLevel level, long index, BlockPos pos, BlockState fold) {
		if (FrogStomachSpace.isPortalApproachProtected(index, pos))
			return false;
		BlockState current = level.getBlockState(pos);
		if (!current.isAir() && !isLivingLining(current))
			return false;
		level.setBlock(pos, fold, Block.UPDATE_CLIENTS);
		return true;
	}

	private static void setMucosa(ServerLevel level, BlockPos pos, BlockState mucosa) {
		level.setBlock(pos, mucosa, Block.UPDATE_CLIENTS);
	}

	private record Pool(List<PoolLobe> lobes, SimplexNoise shoreNoise, SimplexNoise depthNoise,
		int floorY, int waterY) {
		boolean contains(int x, int z) {
			return shapeValue(x, z) >= 0.0d;
		}

		boolean isBank(int x, int z) {
			return !contains(x, z)
				&& (contains(x - 1, z) || contains(x + 1, z)
					|| contains(x, z - 1) || contains(x, z + 1));
		}

		int depthAt(int x, int z) {
			double shapeValue = shapeValue(x, z);
			if (shapeValue < 0.0d)
				return 0;

			int maximumDepth = waterY - floorY - 1;
			double inwardProgress = Mth.clamp(shapeValue / POOL_DEEPENING_DISTANCE, 0.0d, 1.0d);
			double broadVariation = depthNoise.getValue(x / 4.5d + 41.0d, z / 4.5d - 23.0d) * 0.55d;
			double detailVariation = depthNoise.getValue(x * 0.71d - 13.0d, z * 0.71d + 37.0d) * 0.20d;
			double variation = (broadVariation + detailVariation) * (0.35d + inwardProgress * 0.65d);
			int additionalDepth = Mth.clamp(
				Mth.floor(inwardProgress * (maximumDepth - 1) + variation + 0.35d),
				0, maximumDepth - 1);
			return 1 + additionalDepth;
		}

		private double shapeValue(int x, int z) {
			double nearestLobe = Double.POSITIVE_INFINITY;
			for (PoolLobe lobe : lobes) {
				double dx = (x - lobe.centerX()) / (double) lobe.radiusX();
				double dz = (z - lobe.centerZ()) / (double) lobe.radiusZ();
				nearestLobe = Math.min(nearestLobe, Math.sqrt(dx * dx + dz * dz));
			}
			double broadRoughness = shoreNoise.getValue(x / 4.5d, z / 4.5d) * 0.24d;
			double mediumRoughness = shoreNoise.getValue(x / 2.2d + 11.0d, z / 2.2d - 19.0d) * 0.12d;
			double fineRoughness = shoreNoise.getValue(x * 0.83d + 17.0d, z * 0.83d - 29.0d) * 0.05d;
			return 1.0d + broadRoughness + mediumRoughness + fineRoughness - nearestLobe;
		}
	}

	private record PoolLobe(int centerX, int centerZ, int radiusX, int radiusZ) {}

	private record PoolEdge(int x, int z, Direction inward, int wallDistance) {}

	private record SurfaceCoordinates(int first, int second) {}
}
