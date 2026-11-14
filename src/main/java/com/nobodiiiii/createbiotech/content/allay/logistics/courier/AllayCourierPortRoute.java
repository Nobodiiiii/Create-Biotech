package com.nobodiiiii.createbiotech.content.allay.logistics.courier;

import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortBlock;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortBlockEntity;
import com.nobodiiiii.createbiotech.content.allay.entity.courier.AllayCourierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** AllayCourierPortRoute algorithms over the canonical AllayCourierTask state. */
final class AllayCourierPortRoute {
	private AllayCourierPortRoute() {}


	static boolean tickPortDeparture(AllayCourierTask operationOwner, ServerLevel currentLevel,
		@Nullable AllayCourierEntity entity, boolean hasActiveEntity) {
		if (!AllayCourierPortRoute.isGuidedPortDeparture(operationOwner) || operationOwner.portDepartureOrigin == null) {
			return false;
		}
		operationOwner.phase = AllayCourierEntity.Phase.TAKEOFF;
		AllayPortBlockEntity departurePort = operationOwner.resolveDepartureAllayPort(currentLevel);
		if (departurePort != null) {
			operationOwner.portDepartureOrigin = AllayCourierPortRoute.allayPortCenter(operationOwner, departurePort);
			operationOwner.launchDirection = AllayCourierPortRoute.portOutward(operationOwner, departurePort);
			operationOwner.portDepartureClearTarget = AllayCourierPortRoute.portDepartureTarget(operationOwner, departurePort);
		}
		Vec3 outward = operationOwner.launchDirection;
		Vec3 target;
		Vec3 targetVelocity = Vec3.ZERO;
		double speed;
		boolean lockToPath;

		if (operationOwner.portMotion == AllayCourierTask.PortMotion.DEPARTURE_PAUSE) {
			target = departurePort != null ? AllayCourierPortRoute.portInside(operationOwner, departurePort)
				: operationOwner.portDepartureOrigin.add(outward.scale(AllayCourierTask.PORT_INSIDE_OFFSET));
			if (departurePort != null) {
				targetVelocity = AllayCourierPortRoute.portVelocityPerTick(operationOwner, departurePort, AllayCourierPortRoute.portInsideLocal(operationOwner, departurePort));
			}
			speed = AllayCourierTask.PORT_ENTRY_SPEED;
			lockToPath = true;
			if (++operationOwner.portMotionTicks >= AllayCourierTask.PORT_TURNAROUND_PAUSE_TICKS) {
				AllayCourierPortRoute.advancePortMotion(operationOwner, AllayCourierTask.PortMotion.DEPARTURE_MOUTH);
				if (departurePort != null) {
					departurePort.flap(false);
				}
			}
		} else if (operationOwner.portMotion == AllayCourierTask.PortMotion.DEPARTURE_MOUTH) {
			target = departurePort != null ? AllayCourierPortRoute.portMouth(operationOwner, departurePort)
				: operationOwner.portDepartureOrigin.add(outward.scale(AllayCourierTask.PORT_MOUTH_OFFSET));
			if (departurePort != null) {
				targetVelocity = AllayCourierPortRoute.portVelocityPerTick(operationOwner, departurePort, AllayCourierPortRoute.portMouthLocal(operationOwner, departurePort));
			}
			speed = AllayCourierTask.PORT_DEPARTURE_SPEED;
			lockToPath = true;
			if (AllayCourierPortRoute.reachedPortStage(operationOwner, target)) {
				AllayCourierPortRoute.advancePortMotion(operationOwner, AllayCourierTask.PortMotion.DEPARTURE_CLEAR);
				target = AllayCourierPortRoute.portDepartureTarget(operationOwner, departurePort);
				if (departurePort != null) {
					targetVelocity = AllayCourierPortRoute.portVelocityPerTick(operationOwner, departurePort,
						AllayCourierPortRoute.portDepartureTargetLocal(operationOwner, departurePort));
				}
			}
		} else {
			target = AllayCourierPortRoute.portDepartureTarget(operationOwner, departurePort);
			if (departurePort != null) {
				targetVelocity = AllayCourierPortRoute.portVelocityPerTick(operationOwner, departurePort,
					AllayCourierPortRoute.portDepartureTargetLocal(operationOwner, departurePort));
			}
			speed = AllayCourierTask.PORT_DEPARTURE_SPEED;
			lockToPath = true;
			if (AllayCourierPortRoute.reachedPortStage(operationOwner, target)) {
				operationOwner.portMotion = AllayCourierTask.PortMotion.NONE;
				operationOwner.clearPortDeparture();
				operationOwner.portMotionTicks = 0;
				operationOwner.phaseTicks = 0;
				if (hasActiveEntity) {
					entity.clearCourierDestination();
				}
				return false;
			}
		}

		if (hasActiveEntity) {
			entity.guideAlongDockingPath(target, outward, speed,
				AllayCourierTask.PORT_DEPARTURE_ACCELERATION, lockToPath, targetVelocity);
		}
		return true;
	}

	static void tickCrossDimensionExit(AllayCourierTask operationOwner, @Nullable AllayCourierEntity entity, boolean hasActiveEntity) {
		operationOwner.phaseTicks++;
		if (operationOwner.phaseTicks >= AllayCourierTask.TAKEOFF_PHASE_TICKS) {
			operationOwner.phase = AllayCourierEntity.Phase.EXITING_DIMENSION;
		}
		if (hasActiveEntity) {
			Vec3 exitTarget = operationOwner.position.add(operationOwner.launchDirection.scale(24.0)).add(0, 6.0, 0);
			entity.flyDirectlyAsVanillaAllay(exitTarget, AllayCourierTask.VANILLA_ALLAY_CRUISE_SPEED);
		}
	}

	static void tickTowardTarget(AllayCourierTask operationOwner, MinecraftServer server, ServerLevel currentLevel, AllayCourierTask.ResolvedTarget target,
		Vec3 previousPosition, @Nullable AllayCourierEntity entity, boolean hasActiveEntity) {
		operationOwner.phaseTicks++;
		if (target.allayPort() != null) {
			AllayCourierPortRoute.tickTowardAllayPort(operationOwner, server, currentLevel, target.allayPort(),
				previousPosition, entity, hasActiveEntity);
			return;
		}

		Vec3 landingTarget = AllayCourierPortRoute.landingTarget(operationOwner, null, target.player());
		double distance = operationOwner.position.distanceTo(landingTarget);
		if (operationOwner.hasReachedPlayer(target.player(), landingTarget)) {
			operationOwner.doFinishDeliveryAt(server, currentLevel);
			return;
		}

		boolean preciseApproach = distance <= AllayCourierTask.PRECISE_APPROACH_DISTANCE;
		if (preciseApproach) {
			if (operationOwner.phase != AllayCourierEntity.Phase.LANDING) {
				operationOwner.phaseTicks = 0;
			}
			operationOwner.phase = AllayCourierEntity.Phase.LANDING;
		} else if (operationOwner.phase == AllayCourierEntity.Phase.TAKEOFF && operationOwner.phaseTicks >= AllayCourierTask.TAKEOFF_PHASE_TICKS) {
			operationOwner.phase = AllayCourierEntity.Phase.CRUISE;
			operationOwner.phaseTicks = 0;
		}

		if (!hasActiveEntity) {
			return;
		}
		if (preciseApproach) {
			entity.approachPreciselyAsVanillaAllay(landingTarget, AllayCourierTask.VANILLA_ALLAY_APPROACH_SPEED);
		} else {
			entity.flyDirectlyAsVanillaAllay(landingTarget, AllayCourierTask.VANILLA_ALLAY_CRUISE_SPEED);
		}
	}

	static void tickTowardAllayPort(AllayCourierTask operationOwner, MinecraftServer server, ServerLevel currentLevel,
		AllayPortBlockEntity allayPort, Vec3 previousPosition,
		@Nullable AllayCourierEntity entity, boolean hasActiveEntity) {
		if (AllayCourierPortRoute.isGuidedPortArrival(operationOwner)) {
			AllayCourierPortRoute.tickGuidedPortArrival(operationOwner, server, currentLevel, allayPort, entity, hasActiveEntity);
			return;
		}

		Vec3 highApproach = AllayCourierPortRoute.portHighApproach(operationOwner, allayPort);
		if (AllayCourierPortRoute.segmentDistanceToPointSqr(previousPosition, operationOwner.position, highApproach)
			<= AllayCourierTask.PORT_CAPTURE_RADIUS * AllayCourierTask.PORT_CAPTURE_RADIUS) {
			operationOwner.portMotion = AllayCourierTask.PortMotion.ARRIVAL_ALIGN;
			operationOwner.portMotionTicks = 0;
			operationOwner.teleportedNearTarget = true;
			operationOwner.phaseTicks = 0;
			if (hasActiveEntity) {
				entity.clearCourierDestination();
			}
			AllayCourierPortRoute.tickGuidedPortArrival(operationOwner, server, currentLevel, allayPort, entity, hasActiveEntity);
			return;
		}

		double distance = operationOwner.position.distanceTo(highApproach);
		if (distance <= AllayCourierTask.PRECISE_APPROACH_DISTANCE * 2.0) {
			if (operationOwner.phase != AllayCourierEntity.Phase.LANDING) {
				operationOwner.phaseTicks = 0;
			}
			operationOwner.phase = AllayCourierEntity.Phase.LANDING;
		} else if (operationOwner.phase == AllayCourierEntity.Phase.TAKEOFF && operationOwner.phaseTicks >= AllayCourierTask.TAKEOFF_PHASE_TICKS) {
			operationOwner.phase = AllayCourierEntity.Phase.CRUISE;
			operationOwner.phaseTicks = 0;
		}

		if (!hasActiveEntity) {
			return;
		}
		Vec3 cruiseTarget = AllayCourierPortRoute.safePortCruiseTarget(operationOwner, allayPort, highApproach);
		if (operationOwner.position.distanceTo(cruiseTarget) <= AllayCourierTask.PRECISE_APPROACH_DISTANCE) {
			entity.approachPreciselyAsVanillaAllay(cruiseTarget, AllayCourierTask.VANILLA_ALLAY_APPROACH_SPEED);
		} else {
			entity.flyDirectlyAsVanillaAllay(cruiseTarget, AllayCourierTask.VANILLA_ALLAY_CRUISE_SPEED);
		}
	}

	static void tickGuidedPortArrival(AllayCourierTask operationOwner, MinecraftServer server, ServerLevel currentLevel,
		AllayPortBlockEntity allayPort, @Nullable AllayCourierEntity entity, boolean hasActiveEntity) {
		operationOwner.phase = AllayCourierEntity.Phase.LANDING;
		Vec3 outward = AllayCourierPortRoute.portOutward(operationOwner, allayPort);
		Vec3 target;
		Vec3 targetLocal;
		double speed;
		double acceleration;
		boolean lockToPath;

		if (operationOwner.portMotion == AllayCourierTask.PortMotion.ARRIVAL_ALIGN) {
			targetLocal = AllayCourierPortRoute.portLineupLocal(operationOwner, allayPort);
			target = AllayCourierPortRoute.projectPortPoint(operationOwner, allayPort, targetLocal);
			speed = AllayCourierTask.PORT_ALIGNMENT_SPEED;
			acceleration = AllayCourierTask.PORT_ALIGNMENT_ACCELERATION;
			lockToPath = false;
			if (AllayCourierPortRoute.reachedPortStage(operationOwner, target)) {
				AllayCourierPortRoute.advancePortMotion(operationOwner, AllayCourierTask.PortMotion.ARRIVAL_MOUTH);
				targetLocal = AllayCourierPortRoute.portMouthLocal(operationOwner, allayPort);
				target = AllayCourierPortRoute.projectPortPoint(operationOwner, allayPort, targetLocal);
				speed = AllayCourierTask.PORT_ENTRY_SPEED;
				acceleration = AllayCourierTask.PORT_ENTRY_ACCELERATION;
				lockToPath = true;
			}
		} else if (operationOwner.portMotion == AllayCourierTask.PortMotion.ARRIVAL_MOUTH) {
			targetLocal = AllayCourierPortRoute.portMouthLocal(operationOwner, allayPort);
			target = AllayCourierPortRoute.projectPortPoint(operationOwner, allayPort, targetLocal);
			speed = AllayCourierTask.PORT_ENTRY_SPEED;
			acceleration = AllayCourierTask.PORT_ENTRY_ACCELERATION;
			lockToPath = true;
			if (AllayCourierPortRoute.reachedPortStage(operationOwner, target)) {
				AllayCourierPortRoute.advancePortMotion(operationOwner, AllayCourierTask.PortMotion.ARRIVAL_INSIDE);
				allayPort.flap(true);
				targetLocal = AllayCourierPortRoute.portInsideLocal(operationOwner, allayPort);
				target = AllayCourierPortRoute.projectPortPoint(operationOwner, allayPort, targetLocal);
			}
		} else {
			targetLocal = AllayCourierPortRoute.portInsideLocal(operationOwner, allayPort);
			target = AllayCourierPortRoute.projectPortPoint(operationOwner, allayPort, targetLocal);
			speed = AllayCourierTask.PORT_ENTRY_SPEED;
			acceleration = AllayCourierTask.PORT_ENTRY_ACCELERATION;
			lockToPath = true;
			if (AllayCourierPortRoute.reachedPortStage(operationOwner, target)) {
				if (hasActiveEntity) {
					entity.clearCourierDestination();
					entity.setDeltaMovement(Vec3.ZERO);
				}
				operationOwner.position = target;
				operationOwner.doFinishDeliveryAt(server, currentLevel);
				return;
			}
		}

		if (hasActiveEntity) {
			entity.guideAlongDockingPath(target, outward.scale(-1), speed, acceleration, lockToPath,
				AllayCourierPortRoute.portVelocityPerTick(operationOwner, allayPort, targetLocal));
		}
		operationOwner.portMotionTicks++;
	}

	static Vec3 landingTarget(AllayCourierTask operationOwner, @Nullable AllayPortBlockEntity allayPort, @Nullable ServerPlayer player) {
		if (allayPort != null) {
			return AllayCourierPortRoute.portInside(operationOwner, allayPort);
		}
		return player != null ? operationOwner.playerDeliveryTarget(player) : operationOwner.position;
	}

	static Vec3 allayPortCenter(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		return AllayCourierPortRoute.projectPortPoint(operationOwner, allayPort, AllayCourierPortRoute.allayPortCenterLocal(operationOwner, allayPort));
	}

	static Vec3 portOutward(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		Vec3 localOutward = AllayCourierPortRoute.portOutwardLocal(operationOwner, allayPort);
		Level level = allayPort.getLevel();
		if (level == null) {
			return localOutward;
		}
		Vec3 worldOutward = SubLevelCompat.localNormalToWorld(level, allayPort.getBlockPos(), localOutward);
		return worldOutward.lengthSqr() < 1.0E-8 ? localOutward : worldOutward.normalize();
	}

	static Vec3 portOutwardLocal(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		Direction facing = allayPort.getBlockState().getValue(AllayPortBlock.FACING);
		return Vec3.atLowerCornerOf(facing.getNormal());
	}

	static Vec3 allayPortCenterLocal(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		return Vec3.atCenterOf(allayPort.getBlockPos()).add(0, -0.25, 0);
	}

	static Vec3 portHighApproach(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		return AllayCourierPortRoute.projectPortPoint(operationOwner, allayPort, AllayCourierPortRoute.portHighApproachLocal(operationOwner, allayPort));
	}

	static Vec3 portHighApproachLocal(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		return AllayCourierPortRoute.allayPortCenterLocal(operationOwner, allayPort)
			.add(AllayCourierPortRoute.portOutwardLocal(operationOwner, allayPort).scale(AllayCourierTask.PORT_HIGH_APPROACH_OFFSET))
			.add(0, AllayCourierTask.PORT_HIGH_APPROACH_HEIGHT, 0);
	}

	static Vec3 portLineup(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		return AllayCourierPortRoute.projectPortPoint(operationOwner, allayPort, AllayCourierPortRoute.portLineupLocal(operationOwner, allayPort));
	}

	static Vec3 portLineupLocal(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		return AllayCourierPortRoute.allayPortCenterLocal(operationOwner, allayPort)
			.add(AllayCourierPortRoute.portOutwardLocal(operationOwner, allayPort).scale(AllayCourierTask.PORT_LINEUP_OFFSET));
	}

	static Vec3 portMouth(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		return AllayCourierPortRoute.projectPortPoint(operationOwner, allayPort, AllayCourierPortRoute.portMouthLocal(operationOwner, allayPort));
	}

	static Vec3 portMouthLocal(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		return AllayCourierPortRoute.allayPortCenterLocal(operationOwner, allayPort)
			.add(AllayCourierPortRoute.portOutwardLocal(operationOwner, allayPort).scale(AllayCourierTask.PORT_MOUTH_OFFSET));
	}

	static Vec3 portInside(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		return AllayCourierPortRoute.projectPortPoint(operationOwner, allayPort, AllayCourierPortRoute.portInsideLocal(operationOwner, allayPort));
	}

	static Vec3 portInsideLocal(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		return AllayCourierPortRoute.allayPortCenterLocal(operationOwner, allayPort)
			.add(AllayCourierPortRoute.portOutwardLocal(operationOwner, allayPort).scale(AllayCourierTask.PORT_INSIDE_OFFSET));
	}

	static Vec3 portDepartureTarget(AllayCourierTask operationOwner, @Nullable AllayPortBlockEntity allayPort) {
		if (allayPort != null) {
			return AllayCourierPortRoute.projectPortPoint(operationOwner, allayPort, AllayCourierPortRoute.portDepartureTargetLocal(operationOwner, allayPort));
		}
		if (operationOwner.portDepartureClearTarget != null) {
			return operationOwner.portDepartureClearTarget;
		}
		return operationOwner.portDepartureOrigin
			.add(operationOwner.launchDirection.scale(AllayCourierTask.PORT_DEPARTURE_CLEAR_OFFSET))
			.add(0, AllayCourierTask.PORT_DEPARTURE_CLEAR_HEIGHT, 0);
	}

	static Vec3 portDepartureTargetLocal(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort) {
		return AllayCourierPortRoute.allayPortCenterLocal(operationOwner, allayPort)
			.add(AllayCourierPortRoute.portOutwardLocal(operationOwner, allayPort).scale(AllayCourierTask.PORT_DEPARTURE_CLEAR_OFFSET))
			.add(0, AllayCourierTask.PORT_DEPARTURE_CLEAR_HEIGHT, 0);
	}

	static Vec3 projectPortPoint(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort, Vec3 localPoint) {
		Level level = allayPort.getLevel();
		return level == null ? localPoint
			: SubLevelCompat.toWorld(level, allayPort.getBlockPos(), localPoint);
	}

	static Vec3 portVelocityPerTick(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort, Vec3 localPoint) {
		Level level = allayPort.getLevel();
		return level == null ? Vec3.ZERO
			: SubLevelCompat.getWorldVelocity(level, allayPort.getBlockPos(), localPoint).scale(1 / 20d);
	}

	static Vec3 safePortCruiseTarget(AllayCourierTask operationOwner, AllayPortBlockEntity allayPort, Vec3 highApproach) {
		Level level = allayPort.getLevel();
		if (level == null) {
			return highApproach;
		}
		BlockPos anchor = allayPort.getBlockPos();
		Vec3 localPosition = SubLevelCompat.toLocal(level, anchor, operationOwner.position);
		Vec3 localHighApproach = AllayCourierPortRoute.portHighApproachLocal(operationOwner, allayPort);
		AABB safetyBounds = new AABB(allayPort.getBlockPos()).inflate(AllayCourierTask.PORT_ROUTE_SAFETY_INFLATION);
		if (safetyBounds.clip(localPosition, localHighApproach).isEmpty()) {
			return highApproach;
		}

		Vec3 localCenter = AllayCourierPortRoute.allayPortCenterLocal(operationOwner, allayPort);
		boolean insideHorizontalFootprint = localPosition.x >= safetyBounds.minX
			&& localPosition.x <= safetyBounds.maxX
			&& localPosition.z >= safetyBounds.minZ && localPosition.z <= safetyBounds.maxZ;
		if (insideHorizontalFootprint && localPosition.y < safetyBounds.maxY) {
			Vec3 escape = localPosition.subtract(localCenter).multiply(1, 0, 1);
			if (escape.lengthSqr() < 1.0E-6) {
				escape = AllayCourierPortRoute.portOutwardLocal(operationOwner, allayPort);
			}
			return SubLevelCompat.toWorld(level, anchor,
				localPosition.add(escape.normalize().scale(AllayCourierTask.PORT_ROUTE_SAFETY_INFLATION + 1.0)));
		}

		double clearanceY = Math.max(localPosition.y, localCenter.y + AllayCourierTask.PORT_ROUTE_CLEARANCE_HEIGHT);
		return SubLevelCompat.toWorld(level, anchor,
			new Vec3(localPosition.x, clearanceY, localPosition.z));
	}

	static boolean reachedPortStage(AllayCourierTask operationOwner, Vec3 target) {
		return operationOwner.position.distanceToSqr(target)
			<= AllayCourierTask.PORT_STAGE_COMPLETION_DISTANCE * AllayCourierTask.PORT_STAGE_COMPLETION_DISTANCE;
	}

	static void advancePortMotion(AllayCourierTask operationOwner, AllayCourierTask.PortMotion nextMotion) {
		operationOwner.portMotion = nextMotion;
		operationOwner.portMotionTicks = 0;
	}

	static boolean isGuidedPortArrival(AllayCourierTask operationOwner) {
		return operationOwner.portMotion == AllayCourierTask.PortMotion.ARRIVAL_ALIGN
			|| operationOwner.portMotion == AllayCourierTask.PortMotion.ARRIVAL_MOUTH
			|| operationOwner.portMotion == AllayCourierTask.PortMotion.ARRIVAL_INSIDE;
	}

	static boolean isGuidedPortDeparture(AllayCourierTask operationOwner) {
		return operationOwner.portMotion == AllayCourierTask.PortMotion.DEPARTURE_PAUSE
			|| operationOwner.portMotion == AllayCourierTask.PortMotion.DEPARTURE_MOUTH
			|| operationOwner.portMotion == AllayCourierTask.PortMotion.DEPARTURE_CLEAR;
	}

	static double segmentDistanceToPointSqr(Vec3 start, Vec3 end, Vec3 point) {
		Vec3 segment = end.subtract(start);
		double lengthSqr = segment.lengthSqr();
		if (lengthSqr <= 1.0E-8) {
			return start.distanceToSqr(point);
		}
		double progress = Mth.clamp(point.subtract(start).dot(segment) / lengthSqr, 0.0, 1.0);
		return start.add(segment.scale(progress)).distanceToSqr(point);
	}
}
