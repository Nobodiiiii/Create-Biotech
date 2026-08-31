package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.slimemimic.SlimeMimicFragmentSpawner;
import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** End-of-tick support cleanup and rate-limited falling-fragment spawning for surgical tables. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class SurgicalTableSupportManager {
	private static final int GEOMETRY_WAIT_TICKS = 8;
	private static final int MAX_FRAGMENT_SPAWNS_PER_TICK = 64;
	private static final double MAX_REPORT_DISTANCE = 128.0d;
	private static final Map<ServerLevel, RemovalBatch> REMOVALS = new WeakHashMap<>();
	private static final Map<ServerLevel, Set<SurgicalTableBlockEntity>> SUBJECT_HOLDERS = new WeakHashMap<>();
	private static final Map<UUID, PendingRelease> PENDING_RELEASES = new HashMap<>();
	private static final Map<ServerLevel, ArrayDeque<SpawnJob>> SPAWN_QUEUES = new WeakHashMap<>();

	private SurgicalTableSupportManager() {}

	public static void enqueue(Level level, BlockPos pos, SurgicalTableBlockEntity table) {
		if (!(level instanceof ServerLevel serverLevel) || pos == null || table == null)
			return;
		REMOVALS.computeIfAbsent(serverLevel, ignored -> new RemovalBatch())
			.removed.putIfAbsent(pos.immutable(), table);
	}

	static void track(SurgicalTableBlockEntity table) {
		if (!(table.getLevel() instanceof ServerLevel level))
			return;
		Set<SurgicalTableBlockEntity> holders = SUBJECT_HOLDERS.computeIfAbsent(level,
			ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));
		if (!table.isRemoved() && table.hasSubjects())
			holders.add(table);
		else
			holders.remove(table);
	}

	static void untrack(SurgicalTableBlockEntity table) {
		if (!(table.getLevel() instanceof ServerLevel level))
			return;
		Set<SurgicalTableBlockEntity> holders = SUBJECT_HOLDERS.get(level);
		if (holders != null)
			holders.remove(table);
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (!(event.getLevel() instanceof ServerLevel level))
			return;
		RemovalBatch removals = REMOVALS.remove(level);
		if (removals != null && !removals.removed.isEmpty()) {
			long started = SurgicalProfiler.begin();
			processRemovals(level, removals);
			SurgicalProfiler.end("supportCleanup", started);
		}
		expireGeometry(level);
		drainSpawnQueue(level);
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (!(event.getLevel() instanceof ServerLevel level))
			return;
		REMOVALS.remove(level);
		SUBJECT_HOLDERS.remove(level);
		SPAWN_QUEUES.remove(level);
		PENDING_RELEASES.entrySet().removeIf(entry -> entry.getValue().level == level);
	}

	private static void processRemovals(ServerLevel level, RemovalBatch batch) {
		Set<Long> removedTiles = new HashSet<>(batch.removed.size() * 2);
		batch.removed.keySet().forEach(pos -> removedTiles.add(pos.asLong()));
		List<SurgicalTablePlane.Plane> remainingPlanes = scanRemainingPlanes(level, batch.removed.keySet());
		Set<SurgicalTableBlockEntity> finalPlaneHolders = identitySet();
		for (SurgicalTablePlane.Plane plane : remainingPlanes)
			for (BlockPos tile : plane.tiles())
				if (level.getBlockEntity(tile) instanceof SurgicalTableBlockEntity table && table.hasSubjects())
					finalPlaneHolders.add(table);

		Set<SurgicalTableBlockEntity> affected = identitySet();
		affected.addAll(batch.removed.values());
		Set<SurgicalTableBlockEntity> tracked = SUBJECT_HOLDERS.get(level);
		if (tracked != null)
			for (SurgicalTableBlockEntity table : List.copyOf(tracked))
				if (!table.isRemoved() && table.hasSubjects() && table.projectionTouches(removedTiles))
					affected.add(table);

		Set<SurgicalTableBlockEntity> changed = identitySet();
		Map<ChunkPos, List<ReleasedCube>> releases = new LinkedHashMap<>();
		for (SurgicalTableBlockEntity table : affected) {
			if (!table.hasSubjects())
				continue;
			List<ReleasedCube> unsupported = table.releaseUnsupportedComponents();
			if (!unsupported.isEmpty()) {
				releases.computeIfAbsent(new ChunkPos(table.getBlockPos()), ignored -> new ArrayList<>())
					.addAll(unsupported);
				changed.add(table);
			}
		}

		// Request exact client render geometry before the following block-entity sync removes the cubes.
		releases.forEach((chunk, cubes) -> beginReleases(level, chunk.getWorldPosition(), cubes));

		Set<SurgicalTableBlockEntity> migrationSources = identitySet();
		migrationSources.addAll(batch.removed.values());
		Map<BlockPos, BlockPos> finalControllers = new HashMap<>();
		for (SurgicalTablePlane.Plane plane : remainingPlanes)
			for (BlockPos tile : plane.tiles())
				finalControllers.put(tile, plane.source());
		for (SurgicalTableBlockEntity holder : finalPlaneHolders) {
			BlockPos controller = finalControllers.get(holder.getBlockPos());
			if (controller != null && !controller.equals(holder.getBlockPos()))
				migrationSources.add(holder);
		}
		List<DetachedSubject> detached = new ArrayList<>();
		for (SurgicalTableBlockEntity source : migrationSources) {
			if (!source.hasSubjects())
				continue;
			BlockPos previousController = source.getBlockPos().immutable();
			for (SurgicalSubject subject : source.detachSubjectsForSupport())
				detached.add(new DetachedSubject(previousController, subject));
			changed.add(source);
		}

		for (DetachedSubject entry : detached) {
			SurgicalTablePlane.Plane target = bestTargetPlane(level, remainingPlanes, entry.subject,
				entry.previousController.getY());
			if (target == null || target.source() == null
				|| !(level.getBlockEntity(target.source()) instanceof SurgicalTableBlockEntity controller))
				continue;
			controller.adoptSubjectsForSupport(entry.previousController, List.of(entry.subject));
			changed.add(controller);
		}
		for (SurgicalTableBlockEntity table : changed)
			if (!table.isRemoved() && level.getBlockEntity(table.getBlockPos()) == table)
				table.syncAfterSupportChange();
	}

	private static List<SurgicalTablePlane.Plane> scanRemainingPlanes(ServerLevel level,
		Set<BlockPos> removed) {
		Set<BlockPos> scanned = new HashSet<>();
		List<SurgicalTablePlane.Plane> planes = new ArrayList<>();
		for (BlockPos removedPos : removed) {
			List<BlockPos> seeds = new ArrayList<>(5);
			seeds.add(removedPos);
			for (Direction direction : Direction.Plane.HORIZONTAL)
				seeds.add(removedPos.relative(direction));
			for (BlockPos seed : seeds) {
				if (scanned.contains(seed) || !level.isLoaded(seed)
					|| !(level.getBlockState(seed).getBlock() instanceof SurgicalTableBlock))
					continue;
				SurgicalTablePlane.Plane plane = SurgicalTablePlane.scan(level, seed);
				if (!plane.valid())
					continue;
				scanned.addAll(plane.tiles());
				planes.add(plane);
			}
		}
		return List.copyOf(planes);
	}

	@Nullable
	private static SurgicalTablePlane.Plane bestTargetPlane(ServerLevel level,
		List<SurgicalTablePlane.Plane> candidates, SurgicalSubject subject, int tableY) {
		SurgicalTablePlane.Plane best = null;
		double bestArea = 0.0d;
		for (SurgicalTablePlane.Plane plane : candidates) {
			if (plane.workArea().y() != tableY)
				continue;
			double area = overlapArea(subject, plane.workArea());
			if (area > bestArea) {
				bestArea = area;
				best = plane;
			}
		}
		if (best != null)
			return best;
		BlockPos support = firstSupportingTile(level, subject, tableY);
		if (support == null)
			return null;
		SurgicalTablePlane.Plane plane = SurgicalTablePlane.scan(level, support);
		return plane.valid() ? plane : null;
	}

	private static double overlapArea(SurgicalSubject subject, SurgicalTablePlane.WorkArea area) {
		double total = 0.0d;
		for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints()) {
			int firstX = Math.max(area.minX(), (int) Math.floor(footprint.minX()));
			int lastX = Math.min(area.maxXExclusive() - 1, (int) Math.ceil(footprint.maxX()) - 1);
			int firstZ = Math.max(area.minZ(), (int) Math.floor(footprint.minZ()));
			int lastZ = Math.min(area.maxZExclusive() - 1, (int) Math.ceil(footprint.maxZ()) - 1);
			for (int x = firstX; x <= lastX; x++) {
				double overlapX = Math.min(footprint.maxX(), x + 1.0d) - Math.max(footprint.minX(), x);
				if (overlapX <= 1.0e-9d)
					continue;
				for (int z = firstZ; z <= lastZ; z++) {
					double overlapZ = Math.min(footprint.maxZ(), z + 1.0d) - Math.max(footprint.minZ(), z);
					if (overlapZ > 1.0e-9d && area.containsTile(x, z))
						total += overlapX * overlapZ;
				}
			}
		}
		return total;
	}

	@Nullable
	private static BlockPos firstSupportingTile(ServerLevel level, SurgicalSubject subject, int tableY) {
		for (SurgicalTableLayout.Footprint footprint : subject.occupiedFootprints()) {
			int firstX = (int) Math.floor(footprint.minX());
			int lastX = (int) Math.ceil(footprint.maxX()) - 1;
			int firstZ = (int) Math.floor(footprint.minZ());
			int lastZ = (int) Math.ceil(footprint.maxZ()) - 1;
			if ((long) lastX - firstX > SurgicalTablePlane.MAX_TILES
				|| (long) lastZ - firstZ > SurgicalTablePlane.MAX_TILES)
				continue;
			for (int x = firstX; x <= lastX; x++) {
				double overlapX = Math.min(footprint.maxX(), x + 1.0d) - Math.max(footprint.minX(), x);
				if (overlapX <= 1.0e-9d)
					continue;
				for (int z = firstZ; z <= lastZ; z++) {
					double overlapZ = Math.min(footprint.maxZ(), z + 1.0d) - Math.max(footprint.minZ(), z);
					if (overlapZ > 1.0e-9d) {
						BlockPos pos = new BlockPos(x, tableY, z);
						if (level.isLoaded(pos)
							&& level.getBlockState(pos).getBlock() instanceof SurgicalTableBlock)
							return pos;
					}
				}
			}
		}
		return null;
	}

	private static void beginReleases(ServerLevel level, BlockPos origin, List<ReleasedCube> released) {
		for (int start = 0; start < released.size(); start += SurgicalTableReleaseGeometryPacket.MAX_TRANSACTION_CUBES) {
			int end = Math.min(released.size(),
				start + SurgicalTableReleaseGeometryPacket.MAX_TRANSACTION_CUBES);
			beginRelease(level, origin, released.subList(start, end));
		}
	}

	private static void beginRelease(ServerLevel level, BlockPos origin, List<ReleasedCube> released) {
		Map<CubeKey, ReleasedCube> expected = new LinkedHashMap<>();
		for (ReleasedCube cube : released)
			expected.putIfAbsent(new CubeKey(cube.subjectKey, cube.cube), cube);
		if (expected.isEmpty())
			return;
		UUID transaction = UUID.randomUUID();
		AABB bounds = null;
		for (ReleasedCube cube : expected.values()) {
			SlimeMimicFragmentSpawner.Measure measure = SlimeMimicFragmentSpawner.measure(cube.fallbackCorners);
			if (measure != null)
				bounds = bounds == null ? measure.bounds() : bounds.minmax(measure.bounds());
		}
		if (bounds == null)
			bounds = new AABB(origin).inflate(1.0d);
		PendingRelease pending = new PendingRelease(level, origin, Map.copyOf(expected), bounds,
			level.getGameTime());
		PENDING_RELEASES.put(transaction, pending);

		Map<UUID, List<Integer>> bySubject = new LinkedHashMap<>();
		expected.values().forEach(cube -> bySubject.computeIfAbsent(cube.subjectKey,
			ignored -> new ArrayList<>()).add(cube.cube));
		List<SurgicalTableReleaseGeometryPacket.SubjectCubes> subjects = bySubject.entrySet().stream()
			.map(entry -> new SurgicalTableReleaseGeometryPacket.SubjectCubes(entry.getKey(), entry.getValue()))
			.toList();
		SurgicalTableReleaseGeometryPacket.ClientBoundRequest request =
			new SurgicalTableReleaseGeometryPacket.ClientBoundRequest(transaction, origin, subjects);
		Set<ChunkPos> chunks = new LinkedHashSet<>();
		for (ReleasedCube cube : expected.values()) {
			SlimeMimicFragmentSpawner.Measure measure = SlimeMimicFragmentSpawner.measure(cube.fallbackCorners);
			if (measure != null)
				chunks.add(new ChunkPos(BlockPos.containing(measure.center())));
		}
		if (chunks.isEmpty())
			chunks.add(new ChunkPos(origin));
		for (ChunkPos chunk : chunks)
			CBPackets.sendToTrackingChunk(request, level, chunk.getWorldPosition());
	}

	static void acceptGeometry(ServerPlayer player, UUID transaction,
		List<SurgicalTableReleaseGeometryPacket.CubeGeometry> reported) {
		if (player == null || transaction == null || reported == null)
			return;
		PendingRelease pending = PENDING_RELEASES.get(transaction);
		if (pending == null || pending.level != player.level()
			|| reported.size() != pending.expected.size()
			|| distanceToSqr(pending.bounds, player.position()) > MAX_REPORT_DISTANCE * MAX_REPORT_DISTANCE)
			return;
		Map<CubeKey, List<Vec3>> validated = new LinkedHashMap<>();
		AABB allowed = pending.bounds.inflate(64.0d);
		for (SurgicalTableReleaseGeometryPacket.CubeGeometry geometry : reported) {
			CubeKey key = new CubeKey(geometry.subjectKey(), geometry.cube());
			if (!pending.expected.containsKey(key) || validated.containsKey(key)
				|| !SlimeMimicFragmentSpawner.cornersInside(geometry.corners(), allowed)
				|| SlimeMimicFragmentSpawner.measure(geometry.corners()) == null)
				return;
			validated.put(key, geometry.corners());
		}
		if (validated.size() != pending.expected.size())
			return;
		PENDING_RELEASES.remove(transaction);
		for (Map.Entry<CubeKey, ReleasedCube> entry : pending.expected.entrySet())
			enqueueSpawn(pending.level, entry.getValue(), validated.get(entry.getKey()));
	}

	private static void expireGeometry(ServerLevel level) {
		long now = level.getGameTime();
		List<UUID> expired = new ArrayList<>();
		for (Map.Entry<UUID, PendingRelease> entry : PENDING_RELEASES.entrySet()) {
			PendingRelease pending = entry.getValue();
			if (pending.level != level || now - pending.createdTick < GEOMETRY_WAIT_TICKS)
				continue;
			pending.expected.values().forEach(cube -> enqueueSpawn(level, cube, cube.fallbackCorners));
			expired.add(entry.getKey());
		}
		expired.forEach(PENDING_RELEASES::remove);
	}

	private static void enqueueSpawn(ServerLevel level, ReleasedCube cube, List<Vec3> corners) {
		SPAWN_QUEUES.computeIfAbsent(level, ignored -> new ArrayDeque<>())
			.addLast(new SpawnJob(cube.profile, cube.cube, List.copyOf(corners)));
	}

	private static void drainSpawnQueue(ServerLevel level) {
		ArrayDeque<SpawnJob> queue = SPAWN_QUEUES.get(level);
		if (queue == null)
			return;
		for (int spawned = 0; spawned < MAX_FRAGMENT_SPAWNS_PER_TICK && !queue.isEmpty(); spawned++) {
			SpawnJob job = queue.removeFirst();
			SlimeMimicFragmentSpawner.spawn(level, job.profile, job.cube, job.corners);
		}
		if (queue.isEmpty())
			SPAWN_QUEUES.remove(level);
	}

	private static double distanceToSqr(AABB bounds, Vec3 point) {
		double x = Math.max(bounds.minX, Math.min(point.x, bounds.maxX));
		double y = Math.max(bounds.minY, Math.min(point.y, bounds.maxY));
		double z = Math.max(bounds.minZ, Math.min(point.z, bounds.maxZ));
		return point.distanceToSqr(x, y, z);
	}

	private static Set<SurgicalTableBlockEntity> identitySet() {
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}

	static record ReleasedCube(UUID subjectKey, MimicProfile profile, int cube,
		List<Vec3> fallbackCorners) {
		ReleasedCube {
			fallbackCorners = List.copyOf(fallbackCorners);
		}
	}

	private static final class RemovalBatch {
		private final Map<BlockPos, SurgicalTableBlockEntity> removed = new LinkedHashMap<>();
	}

	private record DetachedSubject(BlockPos previousController, SurgicalSubject subject) {}
	private record CubeKey(UUID subjectKey, int cube) {}
	private record PendingRelease(ServerLevel level, BlockPos origin,
		Map<CubeKey, ReleasedCube> expected, AABB bounds, long createdTick) {}
	private record SpawnJob(MimicProfile profile, int cube, List<Vec3> corners) {}
}
