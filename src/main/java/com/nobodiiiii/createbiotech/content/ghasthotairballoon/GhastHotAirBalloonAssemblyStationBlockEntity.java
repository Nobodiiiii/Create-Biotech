package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import com.nobodiiiii.createbiotech.registry.CBBlockEntityTypes;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class GhastHotAirBalloonAssemblyStationBlockEntity extends BlockEntity {

	public static final float SPEED = 0.5f; // 256 RPM equivalent (= 256 / 512 blocks/tick)

	private boolean wasPowered;
	private boolean queuedAssemble;

	private boolean running;
	public float offset;
	public float prevOffset;

	public GhastHotAirBalloonAssemblyStationBlockEntity(BlockPos pos, BlockState state) {
		super(CBBlockEntityTypes.GHAST_HOT_AIR_BALLOON_ASSEMBLY_STATION.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state,
		GhastHotAirBalloonAssemblyStationBlockEntity be) {
		be.prevOffset = be.offset;

		if (be.running)
			be.offset += SPEED;

		if (level.isClientSide)
			return;

		if (be.queuedAssemble) {
			be.queuedAssemble = false;
			if (!be.running) {
				be.running = true;
				be.offset = 0;
				be.prevOffset = 0;
				be.sendUpdate();
			}
		}

		if (!be.running)
			return;

		int floorOffset = Mth.floor(be.offset);
		if (floorOffset >= 1) {
			BlockPos checkPos = pos.below(floorOffset);
			if (level.isOutsideBuildHeight(checkPos)) {
				be.stopRunning();
				return;
			}
			BlockState checkState = level.getBlockState(checkPos);
			if (!checkState.isAir()) {
				boolean movable = BlockMovementChecks.isMovementNecessary(checkState, level, checkPos)
					&& !BlockMovementChecks.isBrittle(checkState);
				if (movable)
					be.doAssemble(checkPos, floorOffset);
				be.stopRunning();
				return;
			}
		}

		int maxLength = AllConfigs.server().kinetics.maxRopeLength.get();
		if (be.offset >= maxLength) {
			be.stopRunning();
			return;
		}
	}

	public void onNeighborSignalChanged(boolean powered) {
		if (level == null || level.isClientSide)
			return;
		if (powered && !wasPowered)
			queuedAssemble = true;
		wasPowered = powered;
		setChanged();
	}

	private void stopRunning() {
		running = false;
		offset = 0;
		prevOffset = 0;
		sendUpdate();
	}

	private void sendUpdate() {
		setChanged();
		if (level != null && !level.isClientSide)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	private void doAssemble(BlockPos anchorPos, int ropeLength) {
		if (level == null || level.isClientSide)
			return;

		GhastHotAirBalloonContraption contraption = new GhastHotAirBalloonContraption(ropeLength);
		try {
			if (!contraption.assemble(level, anchorPos))
				return;
		} catch (AssemblyException e) {
			return;
		}
		if (contraption.getBlocks().isEmpty())
			return;

		contraption.removeBlocksFromWorld(level, BlockPos.ZERO);

		Ghast ghast = EntityType.GHAST.create(level);
		if (ghast == null)
			return;
		double gx = worldPosition.getX() + 0.5;
		double gy = worldPosition.getY();
		double gz = worldPosition.getZ() + 0.5;
		ghast.moveTo(gx, gy, gz, 0f, 0f);
		ghast.setPersistenceRequired();
		if (!level.addFreshEntity(ghast))
			return;

		GhastHotAirBalloonEntity contraptionEntity =
			GhastHotAirBalloonEntity.create(level, contraption, Direction.fromYRot(ghast.getYRot()));
		contraptionEntity.setPos(anchorPos.getX() + 0.5, anchorPos.getY(), anchorPos.getZ() + 0.5);
		level.addFreshEntity(contraptionEntity);
		contraptionEntity.startRiding(ghast, true);
	}

	public boolean isRunning() {
		return running;
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
	public void handleUpdateTag(CompoundTag tag) {
		load(tag);
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("WasPowered", wasPowered);
		tag.putBoolean("Running", running);
		tag.putFloat("Offset", offset);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		wasPowered = tag.getBoolean("WasPowered");
		boolean wasRunning = running;
		running = tag.getBoolean("Running");
		offset = tag.getFloat("Offset");
		if (!wasRunning)
			prevOffset = offset;
	}
}
