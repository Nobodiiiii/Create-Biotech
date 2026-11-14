package com.nobodiiiii.createbiotech.content.allay.logistics.courier;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortBlockEntity;
import com.nobodiiiii.createbiotech.content.allay.entity.courier.AllayCourierEntity;
import com.nobodiiiii.createbiotech.content.allay.logistics.courier.hud.AllayCourierHudSync;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;

/** AllayCourierTargetRecovery algorithms over the canonical AllayCourierTask state. */
final class AllayCourierTargetRecovery {
	private AllayCourierTargetRecovery() {}


	static @Nullable AllayCourierTask.ResolvedTarget tickLostTarget(AllayCourierTask operationOwner, MinecraftServer server, ServerLevel currentLevel,
		Vec3 previousPosition, @Nullable AllayCourierEntity entity, boolean hasActiveEntity) {
		if (operationOwner.lostTargetTicks == 0) {
			AllayCourierTargetRecovery.stopGuidedArrivalAfterTargetLoss(operationOwner, entity, hasActiveEntity);
		}
		operationOwner.lostTargetTicks++;

		if (operationOwner.lostTargetTicks == 1 || operationOwner.lostTargetTicks % AllayCourierTask.LOST_TARGET_RESCAN_INTERVAL_TICKS == 0) {
			AllayCourierTask.ResolvedTarget replacement = AllayCourierTargetRecovery.scanForReplacementTarget(operationOwner, server, currentLevel);
			if (replacement != null) {
				return replacement;
			}
		}

		if (AllayCourierPortRoute.tickPortDeparture(operationOwner, currentLevel, entity, hasActiveEntity)) {
			return null;
		}
		AllayCourierTargetRecovery.tickTowardLastKnownTarget(operationOwner, server, currentLevel, previousPosition, entity, hasActiveEntity);
		return null;
	}

	static void stopGuidedArrivalAfterTargetLoss(AllayCourierTask operationOwner, @Nullable AllayCourierEntity entity, boolean hasActiveEntity) {
		if (!AllayCourierPortRoute.isGuidedPortArrival(operationOwner)) {
			return;
		}
		operationOwner.portMotion = AllayCourierTask.PortMotion.NONE;
		operationOwner.portMotionTicks = 0;
		if (hasActiveEntity) {
			entity.clearCourierDestination();
		}
	}

	static @Nullable AllayCourierTask.ResolvedTarget scanForReplacementTarget(AllayCourierTask operationOwner, MinecraftServer server, ServerLevel currentLevel) {
		if (!PackageItem.isPackage(operationOwner.box)
			|| operationOwner.mission == AllayCourierEntity.Mission.CARRIER_RETURN
			|| operationOwner.mission == AllayCourierEntity.Mission.CARRIER_RETURN_TO_PLAYER) {
			return null;
		}

		AllayCourierTarget replacement = AllayCourierDispatchService.resolvePackageTarget(
			currentLevel, operationOwner.box, operationOwner.position, operationOwner.sourceDimension, operationOwner.sourceAllayPortPos,
			operationOwner::isSafeReplacementAllayPort);
		if (replacement instanceof AllayCourierTarget.AllayPortTarget allayPortTarget) {
			operationOwner.targetDimension = allayPortTarget.dimension();
			operationOwner.targetAllayPortPos = allayPortTarget.pos().immutable();
			operationOwner.targetAllayPortSubLevelId = allayPortTarget.subLevelId();
			operationOwner.targetPlayerId = null;
			operationOwner.mission = AllayCourierEntity.Mission.PACKAGE_TO_ALLAY_PORT;
		} else if (replacement instanceof AllayCourierTarget.PlayerTarget playerTarget) {
			operationOwner.targetDimension = playerTarget.dimension();
			operationOwner.targetAllayPortPos = null;
			operationOwner.targetAllayPortSubLevelId = null;
			operationOwner.targetPlayerId = playerTarget.playerId();
			operationOwner.mission = AllayCourierEntity.Mission.PACKAGE_TO_PLAYER;
		} else {
			return null;
		}

		operationOwner.deliveryElapsedTicks = 0;
		operationOwner.teleportedNearTarget = false;
		operationOwner.forceArrivalPending = false;
		operationOwner.phaseTicks = 0;
		if (!AllayCourierPortRoute.isGuidedPortDeparture(operationOwner)) {
			operationOwner.phase = AllayCourierEntity.Phase.CRUISE;
		}
		return operationOwner.resolveTarget(server);
	}

	static boolean isSafeReplacementAllayPort(AllayCourierTask operationOwner, AllayCourierTarget.AllayPortTarget candidate) {
		return operationOwner.targetAllayPortPos == null
			|| !operationOwner.targetDimension.equals(candidate.dimension())
			|| !operationOwner.targetAllayPortPos.equals(candidate.pos())
			|| Objects.equals(operationOwner.targetAllayPortSubLevelId, candidate.subLevelId());
	}

	static void rememberResolvedTarget(AllayCourierTask operationOwner, AllayCourierTask.ResolvedTarget target) {
		operationOwner.lastKnownTargetDimension = target.level().dimension();
		operationOwner.lastKnownTargetPosition = AllayCourierPortRoute.landingTarget(operationOwner, target.allayPort(), target.player());
		if (target.player() != null) {
			operationOwner.targetDimension = target.player().serverLevel().dimension();
		}
		operationOwner.lostTargetTicks = 0;
	}

	static void tickTowardLastKnownTarget(AllayCourierTask operationOwner, MinecraftServer server, ServerLevel currentLevel,
		Vec3 previousPosition, @Nullable AllayCourierEntity entity, boolean hasActiveEntity) {
		if (operationOwner.lastKnownTargetDimension == null || operationOwner.lastKnownTargetPosition == null) {
			operationOwner.lastKnownTargetDimension = operationOwner.currentDimension;
			operationOwner.lastKnownTargetPosition = operationOwner.position;
		}

		if (operationOwner.lostTargetTicks > AllayCourierTask.forceArrivalTicks()) {
			if (AllayCourierTargetRecovery.recoverBeforePlacement(operationOwner, server, currentLevel)) {
				return;
			}
			AllayCourierTargetRecovery.placeAtLastKnownTarget(operationOwner, server, currentLevel);
			return;
		}

		if (!operationOwner.lastKnownTargetDimension.equals(operationOwner.currentDimension)) {
			if (operationOwner.lostTargetTicks >= AllayCourierTask.teleportAfterTicks()) {
				AllayCourierTargetRecovery.teleportNearLastKnownTarget(operationOwner, server, currentLevel);
			} else {
				AllayCourierPortRoute.tickCrossDimensionExit(operationOwner, entity, hasActiveEntity);
			}
			return;
		}

		Vec3 target = operationOwner.lastKnownTargetPosition;
		if (operationOwner.position.distanceTo(target) <= AllayCourierTask.LOST_TARGET_COMPLETION_DISTANCE
			|| AllayCourierPortRoute.segmentDistanceToPointSqr(previousPosition, operationOwner.position, target)
				<= AllayCourierTask.LOST_TARGET_COMPLETION_DISTANCE * AllayCourierTask.LOST_TARGET_COMPLETION_DISTANCE) {
			if (AllayCourierTargetRecovery.recoverBeforePlacement(operationOwner, server, currentLevel)) {
				return;
			}
			operationOwner.position = target;
			AllayCourierTargetRecovery.placeAtLastKnownTarget(operationOwner, server, currentLevel);
			return;
		}

		double distance = operationOwner.position.distanceTo(target);
		if (distance <= AllayCourierTask.PRECISE_APPROACH_DISTANCE) {
			operationOwner.phase = AllayCourierEntity.Phase.LANDING;
		} else if (operationOwner.phase == AllayCourierEntity.Phase.TAKEOFF && operationOwner.phaseTicks >= AllayCourierTask.TAKEOFF_PHASE_TICKS) {
			operationOwner.phase = AllayCourierEntity.Phase.CRUISE;
			operationOwner.phaseTicks = 0;
		}
		operationOwner.phaseTicks++;

		if (!hasActiveEntity) {
			return;
		}
		if (distance <= AllayCourierTask.PRECISE_APPROACH_DISTANCE) {
			entity.approachPreciselyAsVanillaAllay(target, AllayCourierTask.VANILLA_ALLAY_APPROACH_SPEED);
		} else {
			entity.flyDirectlyAsVanillaAllay(target, AllayCourierTask.VANILLA_ALLAY_CRUISE_SPEED);
		}
	}

	static boolean recoverBeforePlacement(AllayCourierTask operationOwner, MinecraftServer server, ServerLevel currentLevel) {
		AllayCourierTask.ResolvedTarget replacement = AllayCourierTargetRecovery.scanForReplacementTarget(operationOwner, server, currentLevel);
		if (replacement == null) {
			return false;
		}
		AllayCourierTargetRecovery.rememberResolvedTarget(operationOwner, replacement);
		return true;
	}

	static void teleportNearLastKnownTarget(AllayCourierTask operationOwner, MinecraftServer server, ServerLevel originLevel) {
		ServerLevel targetLevel = server.getLevel(operationOwner.lastKnownTargetDimension);
		if (targetLevel == null || operationOwner.lastKnownTargetPosition == null) {
			return;
		}

		Vec3 originPosition = operationOwner.position;
		targetLevel.getChunkAt(BlockPos.containing(operationOwner.lastKnownTargetPosition));
		Vec3 away = operationOwner.position.subtract(operationOwner.lastKnownTargetPosition).multiply(1, 0, 1);
		if (away.lengthSqr() < 1.0E-6) {
			away = operationOwner.launchDirection.scale(-1);
		}
		Vec3 preferredSpawn = operationOwner.lastKnownTargetPosition.add(away.normalize().scale(32.0)).add(0, 4.0, 0);
		operationOwner.position = AllayCourierTargetRecovery.findTickingPosTowardTarget(operationOwner, targetLevel, preferredSpawn, operationOwner.lastKnownTargetPosition);
		operationOwner.currentDimension = targetLevel.dimension();
		operationOwner.phase = AllayCourierEntity.Phase.CRUISE;
		operationOwner.phaseTicks = 0;
		operationOwner.portMotion = AllayCourierTask.PortMotion.NONE;
		operationOwner.clearPortDeparture();
		operationOwner.portMotionTicks = 0;
		operationOwner.teleportedNearTarget = true;
		operationOwner.relocatedThisTick = true;
		AllayCourierTargetRecovery.spawnTeleportParticles(originLevel, originPosition);
		AllayCourierTargetRecovery.spawnTeleportParticles(targetLevel, operationOwner.position);
	}

	static void placeAtLastKnownTarget(AllayCourierTask operationOwner, MinecraftServer server, ServerLevel fallbackLevel) {
		ServerLevel placementLevel = operationOwner.lastKnownTargetDimension != null
			? server.getLevel(operationOwner.lastKnownTargetDimension)
			: null;
		if (placementLevel == null) {
			placementLevel = fallbackLevel;
			operationOwner.lastKnownTargetDimension = fallbackLevel.dimension();
			operationOwner.lastKnownTargetPosition = operationOwner.position;
		}

		Vec3 placementPosition = operationOwner.lastKnownTargetPosition != null ? operationOwner.lastKnownTargetPosition : operationOwner.position;
		placementLevel.getChunkAt(BlockPos.containing(placementPosition));
		operationOwner.currentDimension = placementLevel.dimension();
		operationOwner.position = placementPosition;
		operationOwner.phase = AllayCourierEntity.Phase.WAITING;
		operationOwner.portMotion = AllayCourierTask.PortMotion.NONE;
		operationOwner.clearPortDeparture();
		operationOwner.portMotionTicks = 0;
		operationOwner.forceArrivalPending = false;
		operationOwner.placeCourierWhenRemoved = true;
		AllayCourierHudSync.onFailed(server, operationOwner);
		operationOwner.markRemoved();
	}

	static void teleportNearTarget(AllayCourierTask operationOwner, MinecraftServer server) {
		AllayCourierTask.ResolvedTarget target = operationOwner.resolveTarget(server);
		if (target == null) {
			return;
		}
		ServerLevel originLevel = server.getLevel(operationOwner.currentDimension);
		Vec3 originPosition = operationOwner.position;
		if (operationOwner.targetAllayPortPos != null) {
			target.level().getChunkAt(operationOwner.targetAllayPortPos);
		}

		Vec3 waypoint = operationOwner.nearTargetWaypoint(target.allayPort(), target.player());
		Vec3 preferredSpawn = AllayCourierTargetRecovery.computeNearTargetSpawn(operationOwner, target.allayPort(), target.player(), waypoint);
		operationOwner.position = AllayCourierTargetRecovery.findTickingPosTowardTarget(operationOwner, target.level(), preferredSpawn, waypoint);
		operationOwner.currentDimension = target.level().dimension();
		if (target.player() != null) {
			operationOwner.targetDimension = target.player().serverLevel().dimension();
		}
		operationOwner.phase = AllayCourierEntity.Phase.CRUISE;
		operationOwner.phaseTicks = 0;
		operationOwner.portMotion = AllayCourierTask.PortMotion.NONE;
		operationOwner.clearPortDeparture();
		operationOwner.portMotionTicks = 0;
		operationOwner.teleportedNearTarget = true;
		operationOwner.relocatedThisTick = true;
		AllayCourierTargetRecovery.spawnTeleportParticles(originLevel, originPosition);
		AllayCourierTargetRecovery.spawnTeleportParticles(target.level(), operationOwner.position);
	}

	static boolean teleportToForcedArrivalTarget(AllayCourierTask operationOwner, MinecraftServer server) {
		AllayCourierTask.ResolvedTarget target = operationOwner.resolveTarget(server);
		if (target == null) {
			return false;
		}
		ServerLevel originLevel = server.getLevel(operationOwner.currentDimension);
		Vec3 originPosition = operationOwner.position;
		if (operationOwner.targetAllayPortPos != null) {
			target.level().getChunkAt(operationOwner.targetAllayPortPos);
		}

		if (target.allayPort() != null) {
			operationOwner.position = AllayCourierPortRoute.portHighApproach(operationOwner, target.allayPort());
			operationOwner.portMotion = AllayCourierTask.PortMotion.ARRIVAL_ALIGN;
			operationOwner.portMotionTicks = 0;
			operationOwner.forceArrivalPending = false;
		} else {
			operationOwner.position = AllayCourierPortRoute.landingTarget(operationOwner, null, target.player());
			operationOwner.forceArrivalPending = true;
		}
		operationOwner.currentDimension = target.level().dimension();
		if (target.player() != null) {
			operationOwner.targetDimension = target.player().serverLevel().dimension();
		}
		operationOwner.phase = AllayCourierEntity.Phase.LANDING;
		operationOwner.phaseTicks = 0;
		operationOwner.teleportedNearTarget = true;
		operationOwner.relocatedThisTick = true;
		AllayCourierTargetRecovery.spawnTeleportParticles(originLevel, originPosition);
		AllayCourierTargetRecovery.spawnTeleportParticles(target.level(), operationOwner.position);
		return true;
	}

	static Vec3 computeNearTargetSpawn(AllayCourierTask operationOwner, @Nullable AllayPortBlockEntity allayPort,
		@Nullable ServerPlayer player, Vec3 waypoint) {
		if (allayPort != null) {
			Vec3 localSpawn = AllayCourierPortRoute.portHighApproachLocal(operationOwner, allayPort)
				.add(AllayCourierPortRoute.portOutwardLocal(operationOwner, allayPort).scale(48.0))
				.add(0, 8.0, 0);
			return AllayCourierPortRoute.projectPortPoint(operationOwner, allayPort, localSpawn);
		}

		Vec3 landingTarget = AllayCourierPortRoute.landingTarget(operationOwner, null, player);
		Vec3 away = new Vec3(operationOwner.position.x - landingTarget.x, 0, operationOwner.position.z - landingTarget.z);
		if (away.lengthSqr() < 1.0E-6) {
			away = operationOwner.launchDirection.scale(-1);
		}
		if (away.lengthSqr() < 1.0E-6) {
			away = new Vec3(0, 0, 1);
		}
		away = away.normalize();

		return waypoint.add(away.scale(32.0)).add(0, 4.0, 0);
	}

	static Vec3 findTickingPosTowardTarget(AllayCourierTask operationOwner, ServerLevel level, Vec3 preferredSpawn, Vec3 waypoint) {
		Vec3 path = waypoint.subtract(preferredSpawn);
		if (path.lengthSqr() < 1.0E-6) {
			return preferredSpawn;
		}

		Vec3 step = path.normalize().scale(8.0);
		Vec3 candidate = preferredSpawn;
		int iterations = Math.max(1, Mth.ceil(path.length() / 8.0));
		for (int i = 0; i <= iterations; i++) {
			if (level.isPositionEntityTicking(BlockPos.containing(candidate))) {
				return candidate;
			}
			candidate = candidate.add(step);
		}
		return preferredSpawn;
	}

	static void forceArrive(AllayCourierTask operationOwner, MinecraftServer server, ServerLevel fallbackLevel) {
		AllayCourierTask.ResolvedTarget target = operationOwner.resolveTarget(server);
		if (target == null) {
			operationOwner.doFail(server, fallbackLevel);
			return;
		}
		Vec3 originPosition = operationOwner.position;
		if (operationOwner.targetAllayPortPos != null) {
			target.level().getChunkAt(operationOwner.targetAllayPortPos);
		}
		operationOwner.position = target.allayPort() != null
			? AllayCourierPortRoute.portInside(operationOwner, target.allayPort())
			: AllayCourierPortRoute.landingTarget(operationOwner, null, target.player());
		operationOwner.currentDimension = target.level().dimension();
		AllayCourierTargetRecovery.spawnTeleportParticles(fallbackLevel, originPosition);
		AllayCourierTargetRecovery.spawnTeleportParticles(target.level(), operationOwner.position);
		operationOwner.doFinishDeliveryAt(server, target.level());
	}

	static void spawnTeleportParticles(@Nullable ServerLevel level, Vec3 effectPosition) {
		if (level == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			level.sendParticles(player, ParticleTypes.PORTAL, true,
				effectPosition.x, effectPosition.y + 0.3, effectPosition.z,
				32, 0.35, 0.4, 0.35, 0.1);
		}
	}
}
