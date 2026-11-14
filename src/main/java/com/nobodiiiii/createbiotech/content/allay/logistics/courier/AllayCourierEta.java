package com.nobodiiiii.createbiotech.content.allay.logistics.courier;

import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortBlockEntity;
import com.nobodiiiii.createbiotech.content.allay.entity.courier.AllayCourierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** AllayCourierEta algorithms over the canonical AllayCourierTask state. */
final class AllayCourierEta {
	private AllayCourierEta() {}


	/**
	 * Estimates the sooner of physical arrival, the near-target relocation, and the hard arrival
	 * deadline. The speed constants match the steady movement of the vanilla Allay flight control
	 * used by this task.
	 */
	static int estimateRemainingTicks(AllayCourierTask operationOwner, MinecraftServer server) {
		AllayCourierTask.ResolvedTarget target = operationOwner.resolveTarget(server);
		if (target == null) {
			return -1;
		}
		if (operationOwner.forceArrivalPending) {
			return 0;
		}

		int forceRemaining = Math.max(0, AllayCourierTask.forceArrivalTicks() - operationOwner.deliveryElapsedTicks);
		Vec3 previewPosition = AllayCourierEta.previewNearTargetPosition(operationOwner, target.allayPort(), target.player());
		int afterRelocation = AllayCourierEta.estimateTravelTicksFrom(operationOwner, previewPosition, target.allayPort(), target.player(), false);
		int untilRelocation = Math.max(0, AllayCourierTask.teleportAfterTicks() - operationOwner.deliveryElapsedTicks);

		if (!operationOwner.teleportedNearTarget && !target.level().dimension().equals(operationOwner.currentDimension)) {
			return Math.min(forceRemaining, untilRelocation + afterRelocation);
		}

		int physicalEstimate;
		if (AllayCourierPortRoute.isGuidedPortArrival(operationOwner) && target.allayPort() != null) {
			physicalEstimate = AllayCourierEta.estimateGuidedPortArrivalTicks(operationOwner, target.allayPort());
		} else if (AllayCourierPortRoute.isGuidedPortDeparture(operationOwner) && operationOwner.portDepartureOrigin != null) {
			AllayPortBlockEntity departurePort = operationOwner.resolveDepartureAllayPort(server.getLevel(operationOwner.currentDimension));
			Vec3 departureTarget = AllayCourierPortRoute.portDepartureTarget(operationOwner, departurePort);
			physicalEstimate = AllayCourierEta.estimateGuidedPortDepartureTicks(operationOwner, departurePort)
				+ AllayCourierEta.estimateTravelTicksFrom(operationOwner, departureTarget, target.allayPort(), target.player(), false);
		} else {
			physicalEstimate = AllayCourierEta.estimateTravelTicksFrom(operationOwner, operationOwner.position, target.allayPort(), target.player(),
				operationOwner.phase == AllayCourierEntity.Phase.LANDING);
		}

		if (!operationOwner.teleportedNearTarget) {
			physicalEstimate = Math.min(physicalEstimate, untilRelocation + afterRelocation);
		}
		return Math.min(forceRemaining, physicalEstimate);
	}

	static int estimateTravelTicksFrom(AllayCourierTask operationOwner, Vec3 from, @Nullable AllayPortBlockEntity allayPort,
		@Nullable ServerPlayer player, boolean landingOnly) {
		Vec3 target = allayPort != null ? AllayCourierPortRoute.portHighApproach(operationOwner, allayPort) : AllayCourierPortRoute.landingTarget(operationOwner, null, player);
		double completionDistance = allayPort != null ? AllayCourierTask.PORT_CAPTURE_RADIUS : AllayCourierTask.PLAYER_COMPLETION_DISTANCE;
		double distance = from.distanceTo(target);
		double remainingDistance = Math.max(0, distance - completionDistance);

		int travelTicks = 0;
		if (remainingDistance > 0) {
			if (landingOnly || distance <= AllayCourierTask.PRECISE_APPROACH_DISTANCE) {
				travelTicks = Mth.ceil(remainingDistance / AllayCourierTask.ESTIMATED_APPROACH_BLOCKS_PER_TICK);
			} else {
				double cruiseDistance = distance - AllayCourierTask.PRECISE_APPROACH_DISTANCE;
				double approachDistance = AllayCourierTask.PRECISE_APPROACH_DISTANCE - completionDistance;
				travelTicks = Mth.ceil(cruiseDistance / AllayCourierTask.ESTIMATED_CRUISE_BLOCKS_PER_TICK)
					+ Mth.ceil(Math.max(0, approachDistance) / AllayCourierTask.ESTIMATED_APPROACH_BLOCKS_PER_TICK);
			}
			travelTicks += AllayCourierTask.ESTIMATED_ACCELERATION_TICKS;
		}

		return travelTicks + (allayPort != null ? AllayCourierTask.ESTIMATED_GUIDED_PORT_ARRIVAL_TICKS : 0);
	}

	static int estimateGuidedPortArrivalTicks(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		return operationOwner.estimateGuidedPortArrivalTicks(allayPort);
	}

	static int estimateGuidedPortDepartureTicks(AllayCourierTask operationOwner, @Nullable AllayPortBlockEntity departurePort) {
		return operationOwner.estimateGuidedPortDepartureTicks(departurePort);
	}

	static int estimateLinearTicks(AllayCourierTask operationOwner, Vec3 from, Vec3 target, double completionDistance, double speed) {
		double distance = Math.max(0, from.distanceTo(target) - completionDistance);
		return distance <= 0 ? 0 : Mth.ceil(distance / speed) + AllayCourierTask.ESTIMATED_ACCELERATION_TICKS;
	}

	static Vec3 previewNearTargetPosition(AllayCourierTask operationOwner, @Nullable AllayPortBlockEntity allayPort,
		@Nullable ServerPlayer player) {
		Vec3 waypoint = operationOwner.nearTargetWaypoint(allayPort, player);
		return AllayCourierTargetRecovery.computeNearTargetSpawn(operationOwner, allayPort, player, waypoint);
	}

	static Vec3 normalizedDirection(Vec3 direction) {
		return direction.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : direction.normalize();
	}

	static void projectLegacyDepartureFrame(AllayCourierTask operationOwner, ServerLevel level, BlockPos spaceAnchor) {
		if (operationOwner.portDepartureOrigin == null) {
			return;
		}
		Vec3 worldOrigin = SubLevelCompat.toWorld(level, spaceAnchor, operationOwner.portDepartureOrigin);
		Vec3 worldOutward = AllayCourierEta.normalizedDirection(SubLevelCompat.localNormalToWorld(
			level, spaceAnchor, operationOwner.launchDirection));
		Vec3 worldUp = AllayCourierEta.normalizedDirection(SubLevelCompat.localNormalToWorld(
			level, spaceAnchor, new Vec3(0, 1, 0)));
		operationOwner.portDepartureOrigin = worldOrigin;
		operationOwner.launchDirection = worldOutward;
		operationOwner.portDepartureClearTarget = worldOrigin
			.add(worldOutward.scale(AllayCourierTask.PORT_DEPARTURE_CLEAR_OFFSET))
			.add(worldUp.scale(AllayCourierTask.PORT_DEPARTURE_CLEAR_HEIGHT));
	}
}
