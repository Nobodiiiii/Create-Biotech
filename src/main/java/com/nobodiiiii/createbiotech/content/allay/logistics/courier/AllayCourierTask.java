package com.nobodiiiii.createbiotech.content.allay.logistics.courier;


import com.simibubi.create.content.logistics.box.PackageItem;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortBlockEntity;
import com.nobodiiiii.createbiotech.content.allay.entity.courier.AllayCourierEntity;
import com.nobodiiiii.createbiotech.content.allay.logistics.courier.hud.AllayCourierHudSync;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import com.nobodiiiii.createbiotech.registry.CBConfigs;

public final class AllayCourierTask {

	public static final int DEFAULT_TELEPORT_AFTER_TICKS = 300;
	public static final int DEFAULT_FORCE_ARRIVAL_TICKS = 600;

	static final int TAKEOFF_PHASE_TICKS = 20;
	static final int LOST_TARGET_RESCAN_INTERVAL_TICKS = 20;
	static final double LOST_TARGET_COMPLETION_DISTANCE = 0.75;
	static final double PRECISE_APPROACH_DISTANCE = 4.0;
	static final float VANILLA_ALLAY_CRUISE_SPEED = 2.25f;
	static final double VANILLA_ALLAY_APPROACH_SPEED = 1.5;
	static final double PORT_HIGH_APPROACH_OFFSET = 3.25;
	static final double PORT_HIGH_APPROACH_HEIGHT = 2.25;
	static final double PORT_LINEUP_OFFSET = 2.5;
	static final double PORT_MOUTH_OFFSET = 0.62;
	static final double PORT_INSIDE_OFFSET = -0.15;
	static final double PORT_DEPARTURE_CLEAR_OFFSET = 2.75;
	static final double PORT_DEPARTURE_CLEAR_HEIGHT = 0.55;
	static final double PORT_CAPTURE_RADIUS = 1.15;
	static final double PORT_STAGE_COMPLETION_DISTANCE = 0.055;
	static final double PORT_ALIGNMENT_SPEED = 0.22;
	static final double PORT_ALIGNMENT_ACCELERATION = 0.045;
	static final double PORT_ENTRY_SPEED = 0.16;
	static final double PORT_ENTRY_ACCELERATION = 0.035;
	static final double PORT_DEPARTURE_SPEED = 0.19;
	static final double PORT_DEPARTURE_ACCELERATION = 0.04;
	static final double PORT_ROUTE_CLEARANCE_HEIGHT = 3.0;
	static final double PORT_ROUTE_SAFETY_INFLATION = 0.8;
	static final int PORT_TURNAROUND_PAUSE_TICKS = 8;
	static final int ESTIMATED_GUIDED_PORT_ARRIVAL_TICKS = 34;
	static final int ESTIMATED_GUIDED_PORT_DEPARTURE_TICKS = 24;
	static final double PLAYER_COMPLETION_DISTANCE = 1.5;
	private static final double PLAYER_TARGET_HEIGHT = 1.2;
	private static final double PLAYER_FORWARD_OFFSET = 0.15;
	static final double ESTIMATED_CRUISE_BLOCKS_PER_TICK = 0.55;
	static final double ESTIMATED_APPROACH_BLOCKS_PER_TICK = 0.24;
	static final int ESTIMATED_ACCELERATION_TICKS = 4;
	private static final int GREETING_ETA_TICKS = 60;

	final UUID id;
	ItemStack box;
	ResourceKey<Level> currentDimension;
	ResourceKey<Level> targetDimension;
	@Nullable BlockPos sourceAllayPortPos;
	@Nullable BlockPos targetAllayPortPos;
	@Nullable UUID sourceAllayPortSubLevelId;
	@Nullable UUID targetAllayPortSubLevelId;
	@Nullable UUID targetPlayerId;
	@Nullable UUID sourcePlayerId;
	@Nullable ResourceKey<Level> sourceDimension;
	final String sourceAddress;
	AllayCourierReturnMode returnMode;
	AllayCourierEntity.Mission mission;
	AllayCourierEntity.Phase phase;
	Vec3 position;
	Vec3 launchDirection;
	PortMotion portMotion = PortMotion.NONE;
	@Nullable Vec3 portDepartureOrigin;
	@Nullable Vec3 portDepartureClearTarget;
	@Nullable BlockPos portDepartureAllayPortPos;
	@Nullable UUID portDepartureAllayPortSubLevelId;
	int phaseTicks;
	int portMotionTicks;
	int deliveryElapsedTicks;
	boolean teleportedNearTarget;
	boolean forceArrivalPending;
	@Nullable ResourceKey<Level> lastKnownTargetDimension;
	@Nullable Vec3 lastKnownTargetPosition;
	int lostTargetTicks;
	boolean placeCourierWhenRemoved;
	boolean relocatedThisTick;
	private boolean removed;
	boolean worldCoordinates = true;

	AllayCourierTask(
		UUID id, ItemStack box,
		ResourceKey<Level> currentDimension, ResourceKey<Level> targetDimension,
		@Nullable BlockPos sourceAllayPortPos, @Nullable BlockPos targetAllayPortPos,
		@Nullable UUID sourceAllayPortSubLevelId, @Nullable UUID targetAllayPortSubLevelId,
		@Nullable UUID targetPlayerId,
		@Nullable UUID sourcePlayerId, @Nullable ResourceKey<Level> sourceDimension,
		@Nullable String sourceAddress,
		AllayCourierReturnMode returnMode,
		AllayCourierEntity.Mission mission, Vec3 position, Vec3 launchDirection
	) {
		this.id = id;
		this.box = box.copy();
		this.currentDimension = currentDimension;
		this.targetDimension = targetDimension;
		this.sourceAllayPortPos = sourceAllayPortPos != null ? sourceAllayPortPos.immutable() : null;
		this.targetAllayPortPos = targetAllayPortPos != null ? targetAllayPortPos.immutable() : null;
		this.sourceAllayPortSubLevelId = sourceAllayPortSubLevelId;
		this.targetAllayPortSubLevelId = targetAllayPortSubLevelId;
		this.targetPlayerId = targetPlayerId;
		this.sourcePlayerId = sourcePlayerId;
		this.sourceDimension = sourceDimension;
		this.sourceAddress = sourceAddress == null ? "" : sourceAddress.trim();
		this.returnMode = returnMode == null ? defaultReturnMode(sourceAllayPortPos, sourcePlayerId) : returnMode;
		this.mission = mission;
		this.phase = AllayCourierEntity.Phase.TAKEOFF;
		this.position = position;
		this.launchDirection = AllayCourierEta.normalizedDirection(launchDirection);
		this.lastKnownTargetDimension = targetDimension;
	}

	public static AllayCourierTask forPackageToAllayPort(
		UUID id, ItemStack box,
		ServerLevel spawnLevel, ResourceKey<Level> targetDimension, BlockPos targetAllayPortPos,
		@Nullable UUID targetAllayPortSubLevelId,
		Vec3 spawnPos, Vec3 launchDirection,
		@Nullable ResourceKey<Level> sourceDimension, @Nullable BlockPos sourceAllayPortPos,
		@Nullable UUID sourceAllayPortSubLevelId,
		@Nullable UUID sourcePlayerId,
		AllayCourierReturnMode returnMode
	) {
		return new AllayCourierTask(id, box, spawnLevel.dimension(), targetDimension,
			sourceAllayPortPos, targetAllayPortPos, sourceAllayPortSubLevelId,
			targetAllayPortSubLevelId, null, sourcePlayerId, sourceDimension,
			sourceAddress(spawnLevel, sourceDimension, sourceAllayPortPos, sourceAllayPortSubLevelId),
			returnMode, AllayCourierEntity.Mission.PACKAGE_TO_ALLAY_PORT,
			spawnPos, launchDirection);
	}

	public static AllayCourierTask forPackageToPlayer(
		UUID id, ItemStack box,
		ServerLevel spawnLevel, UUID targetPlayerId, ResourceKey<Level> targetDimension,
		Vec3 spawnPos, Vec3 launchDirection,
		@Nullable ResourceKey<Level> sourceDimension, @Nullable BlockPos sourceAllayPortPos,
		@Nullable UUID sourceAllayPortSubLevelId,
		@Nullable UUID sourcePlayerId,
		AllayCourierReturnMode returnMode
	) {
		return new AllayCourierTask(id, box, spawnLevel.dimension(), targetDimension,
			sourceAllayPortPos, null, sourceAllayPortSubLevelId, null, targetPlayerId,
			sourcePlayerId, sourceDimension,
			sourceAddress(spawnLevel, sourceDimension, sourceAllayPortPos, sourceAllayPortSubLevelId),
			returnMode, AllayCourierEntity.Mission.PACKAGE_TO_PLAYER,
			spawnPos, launchDirection);
	}

	public static AllayCourierTask forCarrierReturn(
		UUID id, ServerLevel spawnLevel,
		ResourceKey<Level> targetDimension, BlockPos targetAllayPortPos,
		@Nullable UUID targetAllayPortSubLevelId,
		Vec3 spawnPos, Vec3 launchDirection
	) {
		return new AllayCourierTask(id, ItemStack.EMPTY, spawnLevel.dimension(), targetDimension,
			null, targetAllayPortPos, null, targetAllayPortSubLevelId, null,
			null, null, "", AllayCourierReturnMode.DEFAULT_FOR_PORT, AllayCourierEntity.Mission.CARRIER_RETURN,
			spawnPos, launchDirection);
	}

	public static AllayCourierTask forCarrierReturnToPlayer(
		UUID id, ServerLevel spawnLevel, UUID targetPlayerId,
		ResourceKey<Level> targetDimension,
		Vec3 spawnPos, Vec3 launchDirection
	) {
		return new AllayCourierTask(id, ItemStack.EMPTY, spawnLevel.dimension(), targetDimension,
			null, null, null, null, targetPlayerId,
			null, null, "", AllayCourierReturnMode.DEFAULT_FOR_PORT,
			AllayCourierEntity.Mission.CARRIER_RETURN_TO_PLAYER, spawnPos, launchDirection);
	}

	public AllayCourierTask departFromAllayPort(AllayPortBlockEntity allayPort) {
		beginPortDeparture(allayPort, false);
		return this;
	}

	/** Migrates pre-Sable tasks before the manager performs any world-position chunk checks. */
	public boolean prepareForTick(MinecraftServer server) {
		if (worldCoordinates) {
			return false;
		}
		ServerLevel currentLevel = server.getLevel(currentDimension);
		if (currentLevel == null) {
			return false;
		}

		if (sourceAllayPortPos != null && sourceDimension != null) {
			ServerLevel sourceLevel = server.getLevel(sourceDimension);
			if (sourceLevel != null) {
				sourceAllayPortSubLevelId = SubLevelCompat.getSpaceId(sourceLevel, sourceAllayPortPos);
			}
		}
		if (targetAllayPortPos != null) {
			ServerLevel targetLevel = server.getLevel(targetDimension);
			if (targetLevel != null) {
				targetAllayPortSubLevelId = SubLevelCompat.getSpaceId(targetLevel, targetAllayPortPos);
			}
		}

		BlockPos legacyPositionBlock = BlockPos.containing(position);
		SubLevelAccess legacyPositionSubLevel = SubLevelCompat.getContaining(currentLevel,
			legacyPositionBlock);
		boolean invalidLegacyPosition = !SubLevelCompat.isValidSpacePosition(currentLevel, legacyPositionBlock);
		if (legacyPositionSubLevel != null) {
			position = SubLevelCompat.toWorld(legacyPositionSubLevel, position);
			invalidLegacyPosition = false;
		}
		AllayPortBlockEntity departurePort = findLegacyDeparturePort(currentLevel);
		boolean legacyDepartureSpaceResolved = false;
		if (departurePort == null && portDepartureOrigin != null) {
			BlockPos legacyDepartureBlock = BlockPos.containing(portDepartureOrigin);
			if (SubLevelCompat.getContaining(currentLevel, legacyDepartureBlock) != null) {
				AllayCourierEta.projectLegacyDepartureFrame(this, currentLevel, legacyDepartureBlock);
				portDepartureAllayPortPos = legacyDepartureBlock.immutable();
				portDepartureAllayPortSubLevelId = SubLevelCompat.getSpaceId(currentLevel,
					legacyDepartureBlock);
				legacyDepartureSpaceResolved = true;
			} else if (isPackageDeliveryMission() && sourceAllayPortPos != null
				&& currentDimension.equals(sourceDimension)
				&& SubLevelCompat.getContaining(currentLevel, sourceAllayPortPos) != null) {
				AllayCourierEta.projectLegacyDepartureFrame(this, currentLevel, sourceAllayPortPos);
				portDepartureAllayPortPos = sourceAllayPortPos.immutable();
				portDepartureAllayPortSubLevelId = SubLevelCompat.getSpaceId(currentLevel,
					sourceAllayPortPos);
				legacyDepartureSpaceResolved = true;
			} else if (!SubLevelCompat.isValidSpacePosition(currentLevel, legacyDepartureBlock)) {
				// The old source sublevel vanished, so no safe transform exists. Skip the stale
				// plot-local docking leg and continue toward the live target in world coordinates.
				clearPortDeparture();
				portMotion = PortMotion.NONE;
				portMotionTicks = 0;
			}
		}
		if (departurePort != null) {
			portDepartureAllayPortPos = departurePort.getBlockPos().immutable();
			portDepartureAllayPortSubLevelId = SubLevelCompat.getSpaceId(currentLevel,
				portDepartureAllayPortPos);
			portDepartureOrigin = AllayCourierPortRoute.allayPortCenter(this, departurePort);
			portDepartureClearTarget = AllayCourierPortRoute.portDepartureTarget(this, departurePort);
			launchDirection = AllayCourierPortRoute.portOutward(this, departurePort);
		} else if (!legacyDepartureSpaceResolved && legacyPositionSubLevel != null) {
			launchDirection = AllayCourierEta.normalizedDirection(SubLevelCompat.localNormalToWorld(
				legacyPositionSubLevel, launchDirection));
		} else if (!legacyDepartureSpaceResolved && isPackageDeliveryMission()
			&& sourceAllayPortPos != null && currentDimension.equals(sourceDimension)
			&& SubLevelCompat.matchesSpace(currentLevel, sourceAllayPortPos,
				sourceAllayPortSubLevelId)) {
			launchDirection = AllayCourierEta.normalizedDirection(SubLevelCompat.localNormalToWorld(
				currentLevel, sourceAllayPortPos, launchDirection));
		}

		ResolvedTarget resolvedTarget = resolveTarget(server);
		if (resolvedTarget != null) {
			if (invalidLegacyPosition) {
				currentDimension = resolvedTarget.level.dimension();
				position = resolvedTarget.allayPort != null
					? AllayCourierPortRoute.portHighApproach(this, resolvedTarget.allayPort)
					: AllayCourierPortRoute.landingTarget(this, null, resolvedTarget.player).add(0, 4.0, 0);
				phase = AllayCourierEntity.Phase.CRUISE;
				phaseTicks = 0;
				portMotion = PortMotion.NONE;
				clearPortDeparture();
				portMotionTicks = 0;
				teleportedNearTarget = true;
				forceArrivalPending = false;
			}
			AllayCourierTargetRecovery.rememberResolvedTarget(this, resolvedTarget);
		} else if (targetAllayPortPos != null) {
			ServerLevel targetLevel = server.getLevel(targetDimension);
			if (targetLevel != null && (!SubLevelCompat.isValidSpacePosition(targetLevel, targetAllayPortPos)
				|| SubLevelCompat.getContaining(targetLevel, targetAllayPortPos) != null)) {
				// Never reinterpret an unresolved plot coordinate as an outer-world fallback.
				lastKnownTargetDimension = null;
				lastKnownTargetPosition = null;
			}
		}
		if (invalidLegacyPosition && resolvedTarget == null) {
			position = Vec3.atBottomCenterOf(currentLevel.getSharedSpawnPos()).add(0, 2.0, 0);
			phase = AllayCourierEntity.Phase.CRUISE;
			phaseTicks = 0;
			portMotion = PortMotion.NONE;
			clearPortDeparture();
			portMotionTicks = 0;
		}
		worldCoordinates = true;
		return true;
	}

	private @Nullable AllayPortBlockEntity findLegacyDeparturePort(ServerLevel currentLevel) {
		if (!AllayCourierPortRoute.isGuidedPortDeparture(this)) {
			return null;
		}
		if (portDepartureOrigin != null
			&& currentLevel.getBlockEntity(BlockPos.containing(portDepartureOrigin))
				instanceof AllayPortBlockEntity departurePort) {
			return departurePort;
		}
		if (isPackageDeliveryMission() && sourceAllayPortPos != null
			&& currentDimension.equals(sourceDimension)
			&& currentLevel.getBlockEntity(sourceAllayPortPos) instanceof AllayPortBlockEntity sourcePort) {
			return sourcePort;
		}
		return null;
	}

	public void tick(MinecraftServer server, @Nullable AllayCourierEntity entity) {
		if (removed) {
			return;
		}
		relocatedThisTick = false;
		Vec3 previousPosition = position;

		ServerLevel currentLevel = server.getLevel(currentDimension);
		if (currentLevel == null) {
			AllayCourierHudSync.onFailed(server, this);
			markRemoved();
			return;
		}

		boolean hasActiveEntity = entity != null && entity.isAlive() && entity.level() == currentLevel;
		if (hasActiveEntity) {
			position = entity.position();
		}
		deliveryElapsedTicks++;
		ResolvedTarget target = resolveTarget(server);
		if (target == null) {
			target = AllayCourierTargetRecovery.tickLostTarget(this, server, currentLevel, previousPosition, entity, hasActiveEntity);
			if (target == null) {
				return;
			}
		}
		AllayCourierTargetRecovery.rememberResolvedTarget(this, target);

		if (forceArrivalPending) {
			forceArrivalPending = false;
			doFinishDeliveryAt(server, currentLevel);
			return;
		}

		if (deliveryElapsedTicks > forceArrivalTicks() && !AllayCourierPortRoute.isGuidedPortArrival(this)) {
			if (hasActiveEntity && AllayCourierTargetRecovery.teleportToForcedArrivalTarget(this, server)) {
				return;
			}
			AllayCourierTargetRecovery.forceArrive(this, server, currentLevel);
			return;
		}

		if (!teleportedNearTarget && deliveryElapsedTicks >= teleportAfterTicks()) {
			AllayCourierTargetRecovery.teleportNearTarget(this, server);
			return;
		}

		startTargetWavingIfArriving(server, target.allayPort);
		if (AllayCourierPortRoute.tickPortDeparture(this, currentLevel, entity, hasActiveEntity)) {
			return;
		}

		if (!target.level.dimension().equals(currentDimension)) {
			AllayCourierPortRoute.tickCrossDimensionExit(this, entity, hasActiveEntity);
			return;
		}

		AllayCourierPortRoute.tickTowardTarget(this, server, currentLevel, target, previousPosition, entity, hasActiveEntity);
	}

	boolean isSafeReplacementAllayPort(AllayCourierTarget.AllayPortTarget candidate) {
		return AllayCourierTargetRecovery.isSafeReplacementAllayPort(this, candidate);
	}

	void doFinishDeliveryAt(MinecraftServer server, @Nullable ServerLevel level) {
		if (level == null) {
			AllayCourierHudSync.onFailed(server, this);
			markRemoved();
			return;
		}

		ResolvedTarget target = resolveTarget(server);
		Vec3 landingTarget = target != null ? AllayCourierPortRoute.landingTarget(this, target.allayPort, target.player) : position;
		setTargetWaving(target != null ? target.allayPort : null, false);

		AllayCourierDeliveryService.DeliveryResult result = AllayCourierDeliveryService.finishDelivery(
			server, box, mission, returnMode,
			targetDimension, targetAllayPortPos, targetAllayPortSubLevelId, targetPlayerId,
			level, position, landingTarget);

		if (result.handled()) {
			AllayCourierDeliveryService.spawnDeliveryParticles(level, position);
			if (PackageItem.isPackage(box)) {
				if (result.packageDelivered()) {
					AllayCourierHudSync.onDelivered(server, this);
				} else {
					AllayCourierHudSync.onFailed(server, this);
				}
			}
			if (result.returnCarrier()) {
				startCarrierReturn(server);
				return;
			}
		} else {
			AllayCourierHudSync.onFailed(server, this);
		}
		markRemoved();
	}

	void doFail(MinecraftServer server, @Nullable ServerLevel currentLevel) {
		AllayCourierHudSync.onFailed(server, this);
		if (currentLevel == null) {
			markRemoved();
			return;
		}
		ResolvedTarget target = resolveTarget(server);
		Vec3 dropTarget = target != null ? AllayCourierPortRoute.landingTarget(this, target.allayPort, null) : position;
		Vec3 dropPos = target != null && target.allayPort != null ? dropTarget : position;
		setTargetWaving(target != null ? target.allayPort : null, false);
		AllayCourierDeliveryService.failAndDrop(box, mission, currentLevel, dropPos);
		markRemoved();
	}

	private void startCarrierReturn(MinecraftServer server) {
		AllayPortBlockEntity departurePort = resolveTargetAllayPort(server.getLevel(currentDimension));
		if (sourceAllayPortPos != null && sourceDimension != null) {
			targetAllayPortPos = sourceAllayPortPos;
			targetAllayPortSubLevelId = sourceAllayPortSubLevelId;
			targetDimension = sourceDimension;
			targetPlayerId = null;
			resetForReturn(AllayCourierEntity.Mission.CARRIER_RETURN);
			beginPortDeparture(departurePort, true);
		} else if (sourcePlayerId != null) {
			ServerPlayer sourcePlayer = server.getPlayerList().getPlayer(sourcePlayerId);
			if (sourcePlayer != null && sourcePlayer.isAlive()) {
				targetAllayPortPos = null;
				targetAllayPortSubLevelId = null;
				targetPlayerId = sourcePlayerId;
				targetDimension = sourcePlayer.serverLevel().dimension();
				resetForReturn(AllayCourierEntity.Mission.CARRIER_RETURN_TO_PLAYER);
				beginPortDeparture(departurePort, true);
			} else {
				AllayCourierDeliveryService.dropCarrierOnly(server.getLevel(currentDimension), position);
				markRemoved();
			}
		} else {
			AllayCourierDeliveryService.dropCarrierOnly(server.getLevel(currentDimension), position);
			markRemoved();
		}
	}

	private void resetForReturn(AllayCourierEntity.Mission nextMission) {
		box = ItemStack.EMPTY;
		mission = nextMission;
		phase = AllayCourierEntity.Phase.TAKEOFF;
		phaseTicks = 0;
		portMotion = PortMotion.NONE;
		clearPortDeparture();
		portMotionTicks = 0;
		deliveryElapsedTicks = 0;
		teleportedNearTarget = false;
		forceArrivalPending = false;
	}

	private void beginPortDeparture(@Nullable AllayPortBlockEntity departurePort, boolean pause) {
		if (departurePort == null) {
			return;
		}
		launchDirection = AllayCourierPortRoute.portOutward(this, departurePort);
		portDepartureOrigin = AllayCourierPortRoute.allayPortCenter(this, departurePort);
		portDepartureClearTarget = AllayCourierPortRoute.portDepartureTarget(this, departurePort);
		portDepartureAllayPortPos = departurePort.getBlockPos().immutable();
		portDepartureAllayPortSubLevelId = departurePort.getLevel() == null ? null
			: SubLevelCompat.getSpaceId(departurePort.getLevel(), portDepartureAllayPortPos);
		portMotion = pause ? PortMotion.DEPARTURE_PAUSE : PortMotion.DEPARTURE_MOUTH;
		portMotionTicks = 0;
	}

	void clearPortDeparture() {
		portDepartureOrigin = null;
		portDepartureClearTarget = null;
		portDepartureAllayPortPos = null;
		portDepartureAllayPortSubLevelId = null;
	}

	private @Nullable ServerLevel resolveTargetLevel(MinecraftServer server) {
		if (targetAllayPortPos != null) {
			return server.getLevel(targetDimension);
		}
		ServerPlayer player = resolveTargetPlayer(server);
		return player != null ? player.serverLevel() : server.getLevel(targetDimension);
	}

	private @Nullable AllayPortBlockEntity resolveTargetAllayPort(@Nullable ServerLevel level) {
		return AllayCourierDeliveryService.resolveTargetAllayPort(level, targetAllayPortPos,
			targetAllayPortSubLevelId);
	}

	@Nullable AllayPortBlockEntity resolveDepartureAllayPort(@Nullable ServerLevel level) {
		return AllayCourierDeliveryService.resolveTargetAllayPort(level, portDepartureAllayPortPos,
			portDepartureAllayPortSubLevelId);
	}

	private @Nullable ServerPlayer resolveTargetPlayer(MinecraftServer server) {
		return AllayCourierDeliveryService.resolvePlayer(server, targetPlayerId);
	}

	Vec3 nearTargetWaypoint(@Nullable AllayPortBlockEntity allayPort, @Nullable ServerPlayer player) {
		if (allayPort != null) {
			return AllayCourierPortRoute.portHighApproach(this, allayPort);
		}
		return player != null ? playerDeliveryTarget(player).add(0, 0.6, 0) : position;
	}

	Vec3 playerDeliveryTarget(ServerPlayer player) {
		Vec3 horizontalLook = player.getLookAngle().multiply(1, 0, 1);
		if (horizontalLook.lengthSqr() > 1.0E-6) {
			horizontalLook = horizontalLook.normalize();
		} else {
			float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
			horizontalLook = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		}
		return player.position().add(0, PLAYER_TARGET_HEIGHT, 0)
			.add(horizontalLook.scale(PLAYER_FORWARD_OFFSET));
	}

	boolean hasReachedPlayer(@Nullable ServerPlayer player, Vec3 landingTarget) {
		return player != null && (player.getBoundingBox().inflate(0.45, 0.6, 0.45).contains(position)
			|| position.distanceTo(landingTarget) <= PLAYER_COMPLETION_DISTANCE);
	}

	private void startTargetWavingIfArriving(MinecraftServer server,
		@Nullable AllayPortBlockEntity allayPort) {
		if (allayPort == null) {
			return;
		}
		int remainingTicks = AllayCourierEta.estimateRemainingTicks(this, server);
		if (remainingTicks >= 0 && remainingTicks < GREETING_ETA_TICKS) {
			setTargetWaving(allayPort, true);
		}
	}

	private void setTargetWaving(@Nullable AllayPortBlockEntity allayPort, boolean waving) {
		if (allayPort != null) {
			allayPort.setCourierWaving(id, waving);
		}
	}

	@Nullable ResolvedTarget resolveTarget(MinecraftServer server) {
		ServerLevel level = resolveTargetLevel(server);
		if (level == null) {
			return null;
		}
		AllayPortBlockEntity allayPort = resolveTargetAllayPort(level);
		ServerPlayer player = allayPort == null ? resolveTargetPlayer(server) : null;
		return allayPort == null && player == null ? null : new ResolvedTarget(level, allayPort, player);
	}

	/**
	 * Estimates the sooner of physical arrival, the near-target relocation, and the hard arrival
	 * deadline. The speed constants match the steady movement of the vanilla Allay flight control
	 * used by this task.
	 */
	public int estimateRemainingTicks(MinecraftServer server) {
		return AllayCourierEta.estimateRemainingTicks(this, server);
	}

	static AllayCourierReturnMode defaultReturnMode(@Nullable BlockPos sourceAllayPortPos,
		@Nullable UUID sourcePlayerId) {
		return sourcePlayerId != null && sourceAllayPortPos == null
			? AllayCourierReturnMode.DEFAULT_FOR_PLAYER_LAUNCH
			: AllayCourierReturnMode.DEFAULT_FOR_PORT;
	}

	private static String sourceAddress(ServerLevel spawnLevel,
		@Nullable ResourceKey<Level> sourceDimension, @Nullable BlockPos sourceAllayPortPos,
		@Nullable UUID sourceAllayPortSubLevelId) {
		if (sourceDimension == null || sourceAllayPortPos == null
			|| !sourceDimension.equals(spawnLevel.dimension())) {
			return "";
		}
		if (!SubLevelCompat.matchesSpace(spawnLevel, sourceAllayPortPos, sourceAllayPortSubLevelId)) {
			return "";
		}
		if (spawnLevel.getBlockEntity(sourceAllayPortPos) instanceof AllayPortBlockEntity sourcePort) {
			return sourcePort.addressFilter == null ? "" : sourcePort.addressFilter.trim();
		}
		return "";
	}

	/**
	 * The two halves of the station animation are deliberately mirrored around the front-facing
	 * axis. Cruise flight may reach the high approach point from any safe direction, but every
	 * courier must align at the front before crossing the mouth.
	 */
	enum PortMotion {
		NONE,
		ARRIVAL_ALIGN,
		ARRIVAL_MOUTH,
		ARRIVAL_INSIDE,
		DEPARTURE_PAUSE,
		DEPARTURE_MOUTH,
		DEPARTURE_CLEAR;

		static PortMotion byName(String name) {
			try {
				return valueOf(name);
			} catch (IllegalArgumentException ignored) {
				return NONE;
			}
		}
	}

	record ResolvedTarget(
		ServerLevel level,
		@Nullable AllayPortBlockEntity allayPort,
		@Nullable ServerPlayer player
	) {}

	public UUID id() { return id; }
	public ItemStack box() { return box; }
	public ResourceKey<Level> currentDimension() { return currentDimension; }
	public AllayCourierEntity.Mission mission() { return mission; }
	public AllayCourierEntity.Phase phase() { return phase; }
	public Vec3 position() { return position; }
	public Vec3 launchDirection() { return launchDirection; }
	public Vec3 velocityOnSpawn(ServerLevel level) {
		AllayPortBlockEntity departurePort = resolveDepartureAllayPort(level);
		if (departurePort == null) {
			return Vec3.ZERO;
		}
		Vec3 localPosition = SubLevelCompat.toLocal(level, departurePort.getBlockPos(), position);
		return AllayCourierPortRoute.portVelocityPerTick(this, departurePort, localPosition);
	}

	private boolean isPackageDeliveryMission() {
		return mission == AllayCourierEntity.Mission.PACKAGE_TO_ALLAY_PORT
			|| mission == AllayCourierEntity.Mission.PACKAGE_TO_PLAYER;
	}
	public boolean relocatedThisTick() { return relocatedThisTick; }
	public boolean isRemoved() { return removed; }
	public boolean placeCourierWhenRemoved() { return placeCourierWhenRemoved; }
	public void markRemoved() { removed = true; }

	public @Nullable UUID hudTrackingPlayerId() {
		if (!PackageItem.isPackage(box)) {
			return null;
		}
		return sourcePlayerId != null ? sourcePlayerId : targetPlayerId;
	}

	public @Nullable UUID sourcePlayerId() {
		return sourcePlayerId;
	}

	static int teleportAfterTicks() {
		return CBConfigs.SERVER.allayCourier.teleportAfterTicks.get();
	}

	static int forceArrivalTicks() {
		return Math.max(teleportAfterTicks(), CBConfigs.SERVER.allayCourier.forceArrivalTicks.get());
	}

	public boolean hudIncoming() {
		return sourcePlayerId == null && targetPlayerId != null;
	}

	public String hudCounterpartyAddress() {
		if (hudIncoming()) {
			return sourceAddress;
		}
		return PackageItem.isPackage(box) ? PackageItem.getAddress(box).trim() : "";
	}

	public CompoundTag save(HolderLookup.Provider registries, CompoundTag tag) {
		return AllayCourierTaskPersistence.save(this, registries, tag);
	}

	public static AllayCourierTask load(HolderLookup.Provider registries, CompoundTag tag) {
		return AllayCourierTaskPersistence.load(registries, tag);
	}

	// Keep dispatch beside the private enum to retain its direct ordinal switch.
	int estimateGuidedPortArrivalTicks(AllayPortBlockEntity allayPort) {
		return switch (this.portMotion) {
			case ARRIVAL_ALIGN -> AllayCourierEta.estimateLinearTicks(this, this.position, AllayCourierPortRoute.portLineup(this, allayPort),
				AllayCourierTask.PORT_STAGE_COMPLETION_DISTANCE, AllayCourierTask.PORT_ALIGNMENT_SPEED * 0.8)
				+ AllayCourierEta.estimateLinearTicks(this, AllayCourierPortRoute.portLineup(this, allayPort), AllayCourierPortRoute.portMouth(this, allayPort),
					AllayCourierTask.PORT_STAGE_COMPLETION_DISTANCE, AllayCourierTask.PORT_ENTRY_SPEED * 0.85)
				+ AllayCourierEta.estimateLinearTicks(this, AllayCourierPortRoute.portMouth(this, allayPort), AllayCourierPortRoute.portInside(this, allayPort),
					AllayCourierTask.PORT_STAGE_COMPLETION_DISTANCE, AllayCourierTask.PORT_ENTRY_SPEED * 0.85);
			case ARRIVAL_MOUTH -> AllayCourierEta.estimateLinearTicks(this, this.position, AllayCourierPortRoute.portMouth(this, allayPort),
				AllayCourierTask.PORT_STAGE_COMPLETION_DISTANCE, AllayCourierTask.PORT_ENTRY_SPEED * 0.85)
				+ AllayCourierEta.estimateLinearTicks(this, AllayCourierPortRoute.portMouth(this, allayPort), AllayCourierPortRoute.portInside(this, allayPort),
					AllayCourierTask.PORT_STAGE_COMPLETION_DISTANCE, AllayCourierTask.PORT_ENTRY_SPEED * 0.85);
			case ARRIVAL_INSIDE -> AllayCourierEta.estimateLinearTicks(this, this.position, AllayCourierPortRoute.portInside(this, allayPort),
				AllayCourierTask.PORT_STAGE_COMPLETION_DISTANCE, AllayCourierTask.PORT_ENTRY_SPEED * 0.85);
			default -> AllayCourierTask.ESTIMATED_GUIDED_PORT_ARRIVAL_TICKS;
		};
	}

	// Keep dispatch beside the private enum to retain its direct ordinal switch.
	int estimateGuidedPortDepartureTicks(@Nullable AllayPortBlockEntity departurePort) {
		return switch (this.portMotion) {
			case DEPARTURE_PAUSE -> Math.max(0, AllayCourierTask.PORT_TURNAROUND_PAUSE_TICKS - this.portMotionTicks)
				+ AllayCourierTask.ESTIMATED_GUIDED_PORT_DEPARTURE_TICKS;
			case DEPARTURE_MOUTH -> AllayCourierTask.ESTIMATED_GUIDED_PORT_DEPARTURE_TICKS;
			case DEPARTURE_CLEAR -> AllayCourierEta.estimateLinearTicks(this, this.position, AllayCourierPortRoute.portDepartureTarget(this, departurePort),
				AllayCourierTask.PORT_STAGE_COMPLETION_DISTANCE, AllayCourierTask.PORT_DEPARTURE_SPEED * 0.85);
			default -> 0;
		};
	}
}
