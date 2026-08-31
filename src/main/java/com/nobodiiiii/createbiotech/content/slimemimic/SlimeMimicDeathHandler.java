package com.nobodiiiii.createbiotech.content.slimemimic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Turns the renderer's dying cuboids into server-authoritative falling fragment entities. */
@EventBusSubscriber(modid = CreateBiotech.MOD_ID)
public final class SlimeMimicDeathHandler {
	private static final int GEOMETRY_WAIT_TICKS = 8;
	private static final double MAX_REPORT_DISTANCE_SQR = 128.0d * 128.0d;
	private static final Map<UUID, PendingDeath> PENDING = new HashMap<>();

	private SlimeMimicDeathHandler() {}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();
		if (event.isCanceled() || !SlimeMimicHandler.isSlimeMimic(entity)
			|| !(entity.level() instanceof ServerLevel level))
			return;
		List<MimicProfile> profiles = profiles(entity);
		if (profiles.isEmpty())
			return;
		PENDING.putIfAbsent(entity.getUUID(), new PendingDeath(level, entity.getBoundingBox(), profiles,
			fallbackCube(entity), level.getGameTime()));
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (!(event.getLevel() instanceof ServerLevel level) || PENDING.isEmpty())
			return;
		long now = level.getGameTime();
		List<UUID> expired = new ArrayList<>();
		for (Map.Entry<UUID, PendingDeath> entry : PENDING.entrySet()) {
			PendingDeath pending = entry.getValue();
			if (pending.level != level || now - pending.createdTick < GEOMETRY_WAIT_TICKS)
				continue;
			spawn(pending, List.of(pending.fallback));
			expired.add(entry.getKey());
		}
		expired.forEach(PENDING::remove);
	}

	static void acceptGeometry(ServerPlayer player, int entityId, List<SlimeMimicCubeGeometry> reported) {
		if (player == null || reported == null || reported.isEmpty()
			|| reported.size() > SurgicalAssembly.MAX_CUBES)
			return;
		Entity found = player.level().getEntity(entityId);
		if (!(found instanceof LivingEntity dying) || !dying.isDeadOrDying()
			|| !SlimeMimicHandler.isSlimeMimic(dying)
			|| player.distanceToSqr(dying) > MAX_REPORT_DISTANCE_SQR)
			return;
		PendingDeath pending = PENDING.get(dying.getUUID());
		if (pending == null || pending.level != player.level())
			return;
		List<SlimeMimicCubeGeometry> validated = validate(pending, reported);
		if (validated.isEmpty())
			return;
		PENDING.remove(dying.getUUID());
		spawn(pending, validated);
	}

	private static List<SlimeMimicCubeGeometry> validate(PendingDeath pending,
		List<SlimeMimicCubeGeometry> reported) {
		Set<Long> identities = new HashSet<>();
		List<SlimeMimicCubeGeometry> validated = new ArrayList<>(reported.size());
		double totalVolume = 0.0d;
		double authoritativeVolume = Math.max(SlimeMimicFragmentSpawner.SMALL_SLIME_VOLUME,
			pending.bounds.getXsize() * pending.bounds.getYsize() * pending.bounds.getZsize());
		double positionAllowance = Math.max(4.0d, Math.max(pending.bounds.getXsize(),
			Math.max(pending.bounds.getYsize(), pending.bounds.getZsize())) * 2.0d);
		AABB allowed = pending.bounds.inflate(positionAllowance);
		for (SlimeMimicCubeGeometry geometry : reported) {
			if (geometry.source() < 0 || geometry.source() >= pending.profiles.size()
				|| geometry.cube() < 0 || geometry.cube() >= SurgicalAssembly.MAX_CUBES)
				return List.of();
			long identity = (long) geometry.source() << 32 | geometry.cube() & 0xffffffffL;
			if (!identities.add(identity)
				|| !SlimeMimicFragmentSpawner.cornersInside(geometry.corners(), allowed))
				return List.of();
			SlimeMimicFragmentSpawner.Measure measure =
				SlimeMimicFragmentSpawner.measure(geometry.corners());
			if (measure == null)
				return List.of();
			totalVolume += measure.volume();
			if (totalVolume > authoritativeVolume * 8.0d + 1.0d)
				return List.of();
			validated.add(geometry);
		}
		return List.copyOf(validated);
	}

	private static void spawn(PendingDeath pending, List<SlimeMimicCubeGeometry> cubes) {
		for (SlimeMimicCubeGeometry geometry : cubes) {
			if (geometry.source() < 0 || geometry.source() >= pending.profiles.size())
				continue;
			SlimeMimicFragmentSpawner.spawn(pending.level, pending.profiles.get(geometry.source()),
				geometry.cube(), geometry.corners());
		}
	}

	private static List<MimicProfile> profiles(LivingEntity entity) {
		if (entity instanceof SlimeBionicEntity bionic) {
			SurgicalAssembly assembly = bionic.getAssembly();
			if (assembly != null && !assembly.sources().isEmpty())
				return assembly.sources().stream().map(SurgicalAssembly.Source::profile).toList();
		}
		MimicProfile profile = MimicProfile.capture(entity);
		return profile == null ? List.of() : List.of(profile);
	}

	private static SlimeMimicCubeGeometry fallbackCube(LivingEntity entity) {
		int source = 0;
		int cube = 0;
		if (entity instanceof SlimeBionicEntity bionic && bionic.getAssembly() != null) {
			List<SurgicalAssembly.Source> sources = bionic.getAssembly().sources();
			for (int index = 0; index < sources.size(); index++) {
				int first = sources.get(index).presentCubes().nextSetBit(0);
				if (first >= 0) {
					source = index;
					cube = first;
					break;
				}
			}
		}
		AABB box = entity.getBoundingBox();
		return new SlimeMimicCubeGeometry(source, cube, List.of(
			new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
			new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
			new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.minY, box.maxZ),
			new Vec3(box.minX, box.maxY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ)));
	}

	private record PendingDeath(ServerLevel level, AABB bounds,
		List<MimicProfile> profiles, SlimeMimicCubeGeometry fallback, long createdTick) {
		private PendingDeath {
			profiles = List.copyOf(profiles);
		}
	}

}
