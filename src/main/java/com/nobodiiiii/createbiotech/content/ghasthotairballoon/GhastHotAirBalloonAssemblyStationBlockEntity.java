package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.foundation.advancement.CBAdvancements;
import com.nobodiiiii.createbiotech.foundation.advancement.PlacedByPlayerAdvancementTracker;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.infrastructure.config.AllConfigs;

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
import net.minecraft.world.phys.AABB;

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
		for (Ghast ghast : GhastHotAirBalloonAssemblyStationBlock.findGhastsToSeat(level, pos)) {
			if (!GhastHotAirBalloonAssemblyStationBlock.canBePickedUp(ghast, stationIdle))
				continue;
			if (ghast.getDeltaMovement().lengthSqr() > getMaxVelocityForAttractSqr())
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

	public void dockContraption(GhastHotAirBalloonEntity contraption, float yaw) {
		if (level == null || level.isClientSide)
			return;
		if (!contraption.isAlive())
			return;

		float magnetY = (float) (contraption.getY() + 1.0);
		float depth = worldPosition.getY() - magnetY;
		snapToDisassembleOffset(depth);

		double targetX = worldPosition.getX() + 0.5;
		double targetZ = worldPosition.getZ() + 0.5;
		contraption.setPos(targetX, contraption.getY(), targetZ);
		contraption.xo = targetX;
		contraption.zo = targetZ;
		contraption.xOld = targetX;
		contraption.zOld = targetZ;
		contraption.setDeltaMovement(0, 0, 0);
		contraption.yaw = yaw;
		contraption.prevYaw = yaw;
		contraption.targetYaw = yaw;

		contraption.disassemble();
	}

	private boolean tryAssemble(BlockPos anchorPos, int ropeLength) {
		if (level == null || level.isClientSide)
			return false;

		BlockPos seatPos = worldPosition.above();
		AABB seatBox = new AABB(seatPos).inflate(0.1);
		List<GhastHotAirBalloonSeatEntity> seats =
			level.getEntitiesOfClass(GhastHotAirBalloonSeatEntity.class, seatBox);
		if (seats.isEmpty())
			return false;
		GhastHotAirBalloonSeatEntity seat = seats.get(0);
		List<Entity> passengers = seat.getPassengers();
		if (passengers.isEmpty() || !(passengers.get(0) instanceof Ghast ghast))
			return false;

		int initialOffset = ropeLength + 1;
		GhastHotAirBalloonContraption contraption = new GhastHotAirBalloonContraption(initialOffset);
		try {
			if (!contraption.assemble(level, anchorPos))
				return false;
		} catch (AssemblyException e) {
			return false;
		}
		if (contraption.getBlocks().isEmpty())
			return false;

		contraption.removeBlocksFromWorld(level, BlockPos.ZERO);

		ghast.stopRiding();
		ghast.setNoAi(true);
		CapturedEntityBoxHelper.markAiDisabledByMod(ghast);
		ghast.setPersistenceRequired();
		seat.discard();

		GhastHotAirBalloonEntity contraptionEntity =
			GhastHotAirBalloonEntity.create(level, contraption, Direction.fromYRot(ghast.getYRot()));
		contraptionEntity.setPos(anchorPos.getX() + 0.5, anchorPos.getY(), anchorPos.getZ() + 0.5);
		level.addFreshEntity(contraptionEntity);
		contraptionEntity.startRiding(ghast, true);
		contraption.onEntityInitialize(level, contraptionEntity);
		return true;
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
	public CompoundTag getUpdateTag() {
		CompoundTag tag = new CompoundTag();
		saveAdditional(tag);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
		super.onDataPacket(net, packet);
		CompoundTag tag = packet.getTag();
		if (tag != null)
			load(tag);
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		load(tag);
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("WasPowered", wasPowered);
		tag.putBoolean("Extending", extending);
		tag.putBoolean("Retracting", retracting);
		tag.putBoolean("AssemblyConsumed", assemblyConsumed);
		tag.putFloat("Offset", offset);
		PlacedByPlayerAdvancementTracker.writeOwner(tag, advancementOwner);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
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
		return CBConfigs.COMMON.ghastHotAirBalloon.assemblyStationSpeed.get().floatValue();
	}

	private static int getAttractPeriodTicks() {
		return CBConfigs.COMMON.ghastHotAirBalloon.attractPeriodTicks.get();
	}

	private static double getMaxVelocityForAttractSqr() {
		return CBConfigs.COMMON.ghastHotAirBalloon.maxVelocityForAttractSqr.get();
	}
}
