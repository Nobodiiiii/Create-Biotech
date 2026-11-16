package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import net.minecraft.core.HolderLookup;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.advancement.PlacedByPlayerAdvancementTracker;
import com.nobodiiiii.createbiotech.foundation.utility.SubLevelCompat;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.infrastructure.config.AllConfigs;

import dev.ryanhcode.sable.companion.SubLevelAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class GhastHotAirBalloonAssemblyStationBlockEntity extends BlockEntity {

	private boolean wasPowered;

	private boolean extending;
	private boolean retracting;
	private boolean assemblyConsumed;
	private boolean queuedStartExtend;
	@Nullable
	private UUID advancementOwner;

	public float offset;
	public float prevOffset;

	public GhastHotAirBalloonAssemblyStationBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if (level != null)
			GhastHotAirBalloonAssemblyStationBlock.updatePoweredState(level, worldPosition);
	}

	public static void tick(Level level, BlockPos pos, BlockState state,
		GhastHotAirBalloonAssemblyStationBlockEntity be) {
		be.prevOffset = be.offset;

		if (level.isClientSide) {
			if (be.extending)
				be.offset += getAssemblyStationSpeed();
			else if (be.retracting)
				be.offset = Math.max(0, be.offset - getAssemblyStationSpeed());
			return;
		}

		if (be.queuedStartExtend) {
			be.queuedStartExtend = false;
			be.extending = true;
			be.retracting = false;
			be.offset = 0;
			be.prevOffset = 0;
			be.sendUpdate();
		}

		if (be.extending)
			be.tickExtension(level, pos);
		else if (be.retracting)
			be.tickRetraction();

		if (level.getGameTime() % getAttractPeriodTicks() == 0)
			be.tickAutoAttract(level, pos, state);

		be.tryAssembleIfReady(level, pos);
	}

	private void tickExtension(Level level, BlockPos pos) {
		offset += getAssemblyStationSpeed();
		int nextDepth = Mth.floor(offset) + 1;
		BlockPos checkPos = pos.below(nextDepth);

		if (level.isOutsideBuildHeight(checkPos)) {
			holdHere();
			return;
		}
		if (!level.getBlockState(checkPos).isAir()) {
			holdHere();
			return;
		}
		if (offset >= AllConfigs.server().kinetics.maxRopeLength.get())
			holdHere();
	}

	private void tickRetraction() {
		offset -= getAssemblyStationSpeed();
		if (offset <= 0) {
			offset = 0;
			retracting = false;
			sendUpdate();
		}
	}

	private void tryAssembleIfReady(Level level, BlockPos pos) {
		if (assemblyConsumed || retracting || offset <= 0f)
			return;

		int depth = Mth.floor(offset) + 1;
		BlockPos anchorPos = pos.below(depth);
		if (level.isOutsideBuildHeight(anchorPos))
			return;

		BlockState anchorState = level.getBlockState(anchorPos);
		if (anchorState.isAir())
			return;
		if (!BlockMovementChecks.isMovementNecessary(anchorState, level, anchorPos))
			return;
		if (BlockMovementChecks.isBrittle(anchorState))
			return;

		if (tryAssemble(anchorPos, depth))
			onAssembledSuccessfully();
	}

	private void tickAutoAttract(Level level, BlockPos pos, BlockState state) {
		if (GhastHotAirBalloonAssemblyStationBlock.isSeatOccupied(level, pos))
			return;
		boolean stationIdle = !wasPowered && offset == 0 && !extending && !retracting;
		Vec3 stationVelocity = GhastHotAirBalloonAssemblyStationBlock.getDockingVelocityPerTick(level, pos);
		for (Ghast ghast : GhastHotAirBalloonAssemblyStationBlock.findGhastsToSeat(level, pos)) {
			if (!GhastHotAirBalloonAssemblyStationBlock.canBePickedUp(ghast, stationIdle))
				continue;
			if (ghast.getDeltaMovement().subtract(stationVelocity).lengthSqr()
				> getMaxVelocityForAttractSqr())
				continue;
			GhastHotAirBalloonAssemblyStationBlock.sitDown(level, pos, state, ghast);
			return;
		}
	}

	public void onNeighborSignalChanged(boolean powered) {
		if (level == null || level.isClientSide)
			return;
		if (powered == wasPowered)
			return;

		if (powered) {
			if (!assemblyConsumed && !extending && !retracting && offset == 0) {
				queuedStartExtend = true;
			} else if (retracting && !assemblyConsumed) {
				retracting = false;
				extending = true;
			}
		} else {
			assemblyConsumed = false;
			if (extending || offset > 0) {
				extending = false;
				retracting = true;
			}
		}

		wasPowered = powered;
		setChanged();
		sendUpdate();
	}

	private void holdHere() {
		extending = false;
		retracting = false;
		sendUpdate();
	}

	private void onAssembledSuccessfully() {
		extending = false;
		retracting = false;
		assemblyConsumed = true;
		offset = 0;
		prevOffset = 0;
		PlacedByPlayerAdvancementTracker.awardPlacedBy(level, advancementOwner, CBAdvancements.GHAST_HOT_AIR_BALLOON);
		sendUpdate();
	}

	private void sendUpdate() {
		setChanged();
		if (level != null && !level.isClientSide)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	public void snapToDisassembleOffset(float depth) {
		if (level == null || level.isClientSide)
			return;
		int maxLength = AllConfigs.server().kinetics.maxRopeLength.get();
		float clamped = Mth.clamp(depth, 0f, maxLength);
		if (clamped <= 0f) {
			extending = false;
			retracting = false;
			offset = 0f;
			prevOffset = 0f;
			sendUpdate();
			return;
		}
		offset = clamped;
		prevOffset = clamped;
		extending = false;
		retracting = true;
		assemblyConsumed = false;
		sendUpdate();
	}

	public void dockContraption(GhastHotAirBalloonEntity contraption, float localYaw) {
		if (level == null || level.isClientSide)
			return;
		if (!contraption.isAlive())
			return;
		if (!(contraption.getContraption() instanceof GhastHotAirBalloonContraption balloonContraption))
			return;

		int initialOffset = balloonContraption.getInitialOffset();
		if (initialOffset < 2)
			return;
		BlockPos localAnchor = worldPosition.below(initialOffset - 1);
		if (level.isOutsideBuildHeight(localAnchor))
			return;

		// Supply a plot-local StructureTransform without ever moving the live entity to the
		// plot. This keeps the dynamic structure outside even during synchronous disassembly.
		if (contraption.disassembleAt(localAnchor, localYaw))
			snapToDisassembleOffset(initialOffset - 2);
	}

	private boolean tryAssemble(BlockPos anchorPos, int ropeLength) {
		if (level == null || level.isClientSide)
			return false;
		if (!SubLevelCompat.isValidSpacePosition(level, anchorPos))
			return false;

		List<GhastHotAirBalloonSeatEntity> seats =
			GhastHotAirBalloonAssemblyStationBlock.findSeatsAtStation(level, worldPosition);
		if (seats.isEmpty())
			return false;
		GhastHotAirBalloonSeatEntity seat = seats.get(0);
		List<Entity> passengers = seat.getPassengers();
		if (passengers.isEmpty() || !(passengers.get(0) instanceof Ghast ghast))
			return false;
		if (SubLevelCompat.getTrackingOrVehicleSubLevel(ghast) != null)
			return false;

		SubLevelAccess sourceSubLevel = SubLevelCompat.getContaining(level, anchorPos);
		float localYaw = GhastHotAirBalloonAssemblyStationBlock.getFacingYaw(getBlockState());
		float worldYaw = SubLevelCompat.localYawToWorld(sourceSubLevel, localYaw);
		Vec3 worldAnchor = SubLevelCompat.toWorld(sourceSubLevel, Vec3.atBottomCenterOf(anchorPos));

		int initialOffset = ropeLength + 1;
		Vec3 worldGhastPosition = worldAnchor.add(0,
			initialOffset + GhastHotAirBalloonSeatEntity.GHAST_PASSENGER_Y_OFFSET, 0);
		GhastHotAirBalloonContraption contraption = new GhastHotAirBalloonContraption(initialOffset);
		try {
			if (!contraption.assemble(level, anchorPos))
				return false;
		} catch (AssemblyException e) {
			return false;
		}
		if (contraption.getBlocks().isEmpty()) {
			contraption.stop(level);
			return false;
		}

		GhastHotAirBalloonEntity contraptionEntity =
			GhastHotAirBalloonEntity.create(level, contraption, Direction.fromYRot(localYaw));
		contraptionEntity.startAtYaw(worldYaw);
		setInitialPosition(contraptionEntity, worldAnchor);
		if (!level.addFreshEntity(contraptionEntity)) {
			// AbstractContraptionEntity.remove() pairs the successful startMoving() with stop().
			contraptionEntity.discard();
			return false;
		}
		if (!contraptionEntity.startRiding(ghast, true)) {
			contraptionEntity.discard();
			return false;
		}

		// Move both entities out before removing plot blocks. Removing the source can delete the
		// sublevel synchronously, so neither the ghast nor its balloon may still occupy plot space.
		ghast.stopRiding();
		ghast.setNoAi(true);
		CapturedEntityBoxHelper.markAiDisabledByMod(ghast);
		ghast.setPersistenceRequired();
		ghast.setDeltaMovement(Vec3.ZERO);
		setInitialPositionAndRotation(ghast, worldGhastPosition, worldYaw);
		// Keep the passenger at the exact projected anchor in this same tick. The normal rider
		// update on subsequent ticks produces the same position from worldGhastPosition.
		setInitialPosition(contraptionEntity, worldAnchor);

		contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
		seat.discard();
		return true;
	}

	private static void setInitialPosition(Entity entity, Vec3 position) {
		entity.setPos(position.x, position.y, position.z);
		entity.xo = position.x;
		entity.yo = position.y;
		entity.zo = position.z;
		entity.xOld = position.x;
		entity.yOld = position.y;
		entity.zOld = position.z;
	}

	private static void setInitialPositionAndRotation(Ghast ghast, Vec3 position, float yaw) {
		ghast.moveTo(position.x, position.y, position.z, yaw, 0);
		setInitialPosition(ghast, position);
		ghast.setYRot(yaw);
		ghast.setYBodyRot(yaw);
		ghast.setYHeadRot(yaw);
		ghast.setXRot(0);
		ghast.yRotO = yaw;
		ghast.yBodyRotO = yaw;
		ghast.yHeadRotO = yaw;
		ghast.xRotO = 0;
	}

	public boolean isRunning() {
		return extending || retracting || offset > 0;
	}

	public boolean isReadyToAccept() {
		if (level == null)
			return false;
		if (wasPowered || extending || retracting || offset != 0f)
			return false;
		return !GhastHotAirBalloonAssemblyStationBlock.isSeatOccupied(level, worldPosition);
	}

	public float getInterpolatedOffset(float partialTicks) {
		return Mth.lerp(partialTicks, prevOffset, offset);
	}

	public void setAdvancementOwner(@Nullable LivingEntity placer) {
		advancementOwner = PlacedByPlayerAdvancementTracker.ownerFrom(placer);
		setChanged();
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		saveAdditional(tag, registries);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
		super.handleUpdateTag(tag, registries);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putBoolean("WasPowered", wasPowered);
		tag.putBoolean("Extending", extending);
		tag.putBoolean("Retracting", retracting);
		tag.putBoolean("AssemblyConsumed", assemblyConsumed);
		tag.putFloat("Offset", offset);
		PlacedByPlayerAdvancementTracker.writeOwner(tag, advancementOwner);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		wasPowered = tag.getBoolean("WasPowered");
		boolean wasExtending = extending;
		boolean wasRetracting = retracting;
		extending = tag.getBoolean("Extending");
		retracting = tag.getBoolean("Retracting");
		assemblyConsumed = tag.getBoolean("AssemblyConsumed");
		offset = tag.getFloat("Offset");
		advancementOwner = PlacedByPlayerAdvancementTracker.readOwner(tag);
		if (!wasExtending && !wasRetracting)
			prevOffset = offset;
	}

	private static float getAssemblyStationSpeed() {
		return CBConfigs.SERVER.ghastHotAirBalloon.assemblyStationSpeed.get().floatValue();
	}

	private static int getAttractPeriodTicks() {
		return CBConfigs.SERVER.ghastHotAirBalloon.attractPeriodTicks.get();
	}

	private static double getMaxVelocityForAttractSqr() {
		return CBConfigs.SERVER.ghastHotAirBalloon.maxVelocityForAttractSqr.get();
	}
}
