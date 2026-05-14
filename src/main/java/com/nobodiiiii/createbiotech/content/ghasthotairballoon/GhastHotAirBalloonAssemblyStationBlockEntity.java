package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import java.util.List;

import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
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
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class GhastHotAirBalloonAssemblyStationBlockEntity extends BlockEntity {

	public static final float SPEED = 0.5f; // 256 RPM equivalent (= 256 / 512 blocks/tick)
	private static final int ATTRACT_PERIOD_TICKS = 20;

	private boolean wasPowered;

	private boolean extending;
	private boolean retracting;
	private boolean assemblyConsumed;
	private boolean queuedStartExtend;

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
				be.offset += SPEED;
			else if (be.retracting)
				be.offset = Math.max(0, be.offset - SPEED);
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

		if (be.extending) {
			be.offset += SPEED;
			int nextDepth = Mth.floor(be.offset) + 1;
			BlockPos checkPos = pos.below(nextDepth);

			if (level.isOutsideBuildHeight(checkPos)) {
				be.holdHere();
			} else {
				BlockState checkState = level.getBlockState(checkPos);
				if (!checkState.isAir()) {
					boolean movable = BlockMovementChecks.isMovementNecessary(checkState, level, checkPos)
						&& !BlockMovementChecks.isBrittle(checkState);
					if (movable && be.tryAssemble(checkPos, nextDepth)) {
						be.onAssembledSuccessfully();
					} else {
						be.holdHere();
					}
				} else {
					int maxLength = AllConfigs.server().kinetics.maxRopeLength.get();
					if (be.offset >= maxLength)
						be.holdHere();
				}
			}
		} else if (be.retracting) {
			be.offset -= SPEED;
			if (be.offset <= 0) {
				be.offset = 0;
				be.retracting = false;
				be.sendUpdate();
			}
		}

		if (level.getGameTime() % ATTRACT_PERIOD_TICKS == 0)
			be.tickAutoAttract(level, pos, state);
	}

	private void tickAutoAttract(Level level, BlockPos pos, BlockState state) {
		if (GhastHotAirBalloonAssemblyStationBlock.isSeatOccupied(level, pos))
			return;
		boolean stationIdle = !wasPowered && offset == 0 && !extending && !retracting;
		for (Ghast ghast : GhastHotAirBalloonAssemblyStationBlock.findGhastsToSeat(level, pos)) {
			if (!GhastHotAirBalloonAssemblyStationBlock.canBePickedUp(ghast, stationIdle))
				continue;
			GhastHotAirBalloonAssemblyStationBlock.sitDown(level, pos, state, ghast);
			return;
		}
	}

	public void onNeighborSignalChanged(boolean powered) {
		if (level == null || level.isClientSide)
			return;

		if (powered && !wasPowered) {
			if (!assemblyConsumed && !extending && !retracting && offset == 0) {
				queuedStartExtend = true;
			} else if (retracting && !assemblyConsumed) {
				retracting = false;
				extending = true;
				sendUpdate();
			}
		} else if (!powered && wasPowered) {
			assemblyConsumed = false;
			if (extending || offset > 0) {
				extending = false;
				retracting = true;
				sendUpdate();
			}
		}

		wasPowered = powered;
		setChanged();
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
		if (!wasExtending && !wasRetracting)
			prevOffset = offset;
	}
}
