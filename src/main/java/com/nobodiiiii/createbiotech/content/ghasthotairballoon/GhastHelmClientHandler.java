package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.network.CBPackets;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsInputPacket;
import com.simibubi.create.content.trains.CameraDistanceModifier;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.createmod.catnip.platform.CatnipServices;

import dev.ryanhcode.sable.companion.SubLevelAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = CreateBiotech.MOD_ID, value = Dist.CLIENT)
public class GhastHelmClientHandler {

	private static final double DETECT_RADIUS = 16d;
	private static final double ENGAGED_RADIUS = 24d;
	private static final int SCAN_PERIOD_TICKS = 10;
	private static final int KEEP_ALIVE_PERIOD_TICKS =
		CBConfigs.GhastHotAirBalloon.MAGNET_KEEP_ALIVE_PERIOD_TICKS;
	private static final int MAX_SCANNED_CHUNKS_PER_SPACE = 4096;

	private static boolean previousSprintDown;
	private static boolean controllingGhastBalloon;

	private static StationTarget nearestStation;
	private static StationTarget engagedStation;
	private static int scanCooldown;
	private static int keepAliveCooldown;

	private GhastHelmClientHandler() {}

	public static void startControlling(GhastHotAirBalloonEntity entity) {
		controllingGhastBalloon = entity != null;
		previousSprintDown = false;
		nearestStation = null;
		engagedStation = null;
		scanCooldown = 0;
		keepAliveCooldown = 0;
		if (controllingGhastBalloon)
			CameraDistanceModifier.zoomOut();
	}

	public static boolean shouldShowMagnetPrompt() {
		return controllingGhastBalloon && nearestStation != null;
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if (!controllingGhastBalloon)
			return;

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			reset();
			return;
		}

		AbstractContraptionEntity contraption = ControlsHandler.getContraption();
		BlockPos controlsPos = ControlsHandler.getControlsPos();
		if (!(contraption instanceof GhastHotAirBalloonEntity) || contraption.isRemoved() || controlsPos == null) {
			disengageIfEngaged(contraption);
			reset();
			return;
		}

		boolean sprintDown = minecraft.options.keySprint.isDown();
		if (sprintDown && !previousSprintDown) {
			disengageIfEngaged(contraption);
			CatnipServices.NETWORK.sendToServer(
				new ControlsInputPacket(ControlsHandler.currentlyPressed, false, contraption.getId(), controlsPos, true));
			ControlsHandler.stopControlling();
			reset();
			return;
		}
		previousSprintDown = sprintDown;

		// Keep the train-style zoom active even if mounting or dismounting a seat resets it.
		CameraDistanceModifier.zoomOut();
		tickMagnetSnap(minecraft, contraption);
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel().isClientSide())
			reset();
	}

	private static void tickMagnetSnap(Minecraft minecraft, AbstractContraptionEntity contraption) {
		if (!(contraption instanceof GhastHotAirBalloonEntity balloon)) {
			disengageIfEngaged(contraption);
			nearestStation = null;
			return;
		}
		if (!(balloon.getVehicle() instanceof Ghast ghast) || !ghast.isAlive()) {
			disengageIfEngaged(contraption);
			nearestStation = null;
			return;
		}

		if (--scanCooldown <= 0) {
			double radius = engagedStation != null ? ENGAGED_RADIUS : DETECT_RADIUS;
			nearestStation = findClosestValidStation(minecraft.level, ghast.position(), radius);
			scanCooldown = SCAN_PERIOD_TICKS;
		}

		boolean altDown = minecraft.screen == null && Screen.hasAltDown();
		if (altDown && nearestStation != null) {
			boolean targetChanged = engagedStation == null || !engagedStation.equals(nearestStation);
			if (targetChanged || --keepAliveCooldown <= 0) {
				CBPackets.sendToServer(new GhastBalloonMagnetTargetPacket(balloon.getId(), nearestStation.pos(),
					nearestStation.subLevelId()));
				engagedStation = nearestStation;
				keepAliveCooldown = KEEP_ALIVE_PERIOD_TICKS;
			}
		} else if (engagedStation != null) {
			CBPackets.sendToServer(new GhastBalloonMagnetTargetPacket(balloon.getId(), null));
			engagedStation = null;
			keepAliveCooldown = 0;
		}
	}

	private static void disengageIfEngaged(AbstractContraptionEntity contraption) {
		if (engagedStation == null)
			return;
		if (contraption instanceof GhastHotAirBalloonEntity)
			CBPackets.sendToServer(new GhastBalloonMagnetTargetPacket(contraption.getId(), null));
		engagedStation = null;
		keepAliveCooldown = 0;
	}

	private static StationTarget findClosestValidStation(ClientLevel level, Vec3 center, double radius) {
		double r2 = radius * radius;
		AABB worldBounds = new AABB(center.x - radius, center.y - radius, center.z - radius,
			center.x + radius, center.y + radius, center.z + radius);
		ClosestStation closest = new ClosestStation();
		scanSpace(level, center, r2, null, worldBounds, closest);

		for (SubLevelAccess subLevel : SubLevelCompat.getAllIntersecting(level, worldBounds)) {
			AABB localSearchBounds = SubLevelCompat.toLocalBounds(subLevel, worldBounds);
			AABB localSubLevelBounds = SubLevelCompat.toLocalBounds(subLevel, subLevel.boundingBox().toMojang());
			AABB scanBounds = intersect(localSearchBounds, localSubLevelBounds);
			if (scanBounds != null)
				scanSpace(level, center, r2, subLevel, scanBounds, closest);
		}
		return closest.target;
	}

	private static void scanSpace(ClientLevel level, Vec3 center, double radiusSqr,
		@Nullable SubLevelAccess subLevel, AABB localBounds, ClosestStation closest) {
		if (!hasFiniteBounds(localBounds))
			return;
		int minCX = SectionPos.blockToSectionCoord((int) Math.floor(localBounds.minX));
		int maxCX = SectionPos.blockToSectionCoord((int) Math.ceil(localBounds.maxX));
		int minCZ = SectionPos.blockToSectionCoord((int) Math.floor(localBounds.minZ));
		int maxCZ = SectionPos.blockToSectionCoord((int) Math.ceil(localBounds.maxZ));
		long chunkWidth = (long) maxCX - minCX + 1;
		long chunkLength = (long) maxCZ - minCZ + 1;
		if (chunkWidth <= 0 || chunkLength <= 0
			|| chunkWidth > MAX_SCANNED_CHUNKS_PER_SPACE
			|| chunkLength > MAX_SCANNED_CHUNKS_PER_SPACE
			|| chunkWidth * chunkLength > MAX_SCANNED_CHUNKS_PER_SPACE)
			return;
		UUID subLevelId = subLevel == null ? null : subLevel.getUniqueId();

		for (int cx = minCX; cx <= maxCX; cx++) {
			for (int cz = minCZ; cz <= maxCZ; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
				// Sable may return its shared empty client chunk for an unloaded plot coordinate.
				// Reject it by position as well as null so this scan never treats the placeholder
				// as a real loaded chunk and never requests a chunk load.
				if (chunk == null || chunk.getPos().x != cx || chunk.getPos().z != cz)
					continue;
				for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
					if (!(entry.getValue() instanceof GhastHotAirBalloonAssemblyStationBlockEntity station))
						continue;
					if (!station.isReadyToAccept())
						continue;
					BlockPos pos = entry.getKey();
					if (!SubLevelCompat.matchesSpace(level, pos, subLevelId))
						continue;
					Vec3 stationCenter = SubLevelCompat.toWorld(subLevel,
						GhastHotAirBalloonAssemblyStationBlock.getGhastDockingLocalPosition(pos));
					double dsq = stationCenter.distanceToSqr(center);
					if (dsq > radiusSqr)
						continue;
					if (dsq < closest.distanceSqr) {
						closest.distanceSqr = dsq;
						closest.target = new StationTarget(pos.immutable(), subLevelId);
					}
				}
			}
		}
	}

	@Nullable
	private static AABB intersect(AABB first, AABB second) {
		double minX = Math.max(first.minX, second.minX);
		double minY = Math.max(first.minY, second.minY);
		double minZ = Math.max(first.minZ, second.minZ);
		double maxX = Math.min(first.maxX, second.maxX);
		double maxY = Math.min(first.maxY, second.maxY);
		double maxZ = Math.min(first.maxZ, second.maxZ);
		return maxX < minX || maxY < minY || maxZ < minZ ? null
			: new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static boolean hasFiniteBounds(AABB bounds) {
		return Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
			&& Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ);
	}

	private static void reset() {
		if (controllingGhastBalloon) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof CarriageContraptionEntity))
				CameraDistanceModifier.reset();
		}
		controllingGhastBalloon = false;
		previousSprintDown = false;
		nearestStation = null;
		engagedStation = null;
		scanCooldown = 0;
		keepAliveCooldown = 0;
	}

	private record StationTarget(BlockPos pos, @Nullable UUID subLevelId) {}

	private static class ClosestStation {
		private StationTarget target;
		private double distanceSqr = Double.MAX_VALUE;
	}
}
