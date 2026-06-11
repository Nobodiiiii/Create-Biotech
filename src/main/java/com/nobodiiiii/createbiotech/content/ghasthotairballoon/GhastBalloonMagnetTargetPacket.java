package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import com.nobodiiiii.createbiotech.registry.CBConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent.Context;

public class GhastBalloonMagnetTargetPacket {

	private static final long NO_TARGET = Long.MIN_VALUE;

	private final int entityId;
	private final long targetPacked;

	public GhastBalloonMagnetTargetPacket(int entityId, BlockPos target) {
		this.entityId = entityId;
		this.targetPacked = target == null ? NO_TARGET : target.asLong();
	}

	public GhastBalloonMagnetTargetPacket(FriendlyByteBuf buffer) {
		this.entityId = buffer.readVarInt();
		this.targetPacked = buffer.readLong();
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(entityId);
		buffer.writeLong(targetPacked);
	}

	public void handle(Context context) {
		context.enqueueWork(() -> apply(context.getSender()));
	}

	private void apply(ServerPlayer player) {
		if (player == null || player.isSpectator())
			return;
		Level level = player.level();
		if (level == null)
			return;
		Entity entity = level.getEntity(entityId);
		if (!(entity instanceof GhastHotAirBalloonEntity balloon))
			return;
		if (balloon.getControllingPlayer().filter(player.getUUID()::equals).isEmpty())
			return;
		if (!(balloon.getVehicle() instanceof Ghast ghast) || !ghast.isAlive())
			return;

		if (targetPacked == NO_TARGET) {
			balloon.clearMagnetTarget();
			return;
		}

		BlockPos targetPos = BlockPos.of(targetPacked);
		if (!level.isLoaded(targetPos))
			return;
		if (ghast.position().distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 1, targetPos.getZ() + 0.5)
			> getMaxDistanceSqr())
			return;
		BlockEntity be = level.getBlockEntity(targetPos);
		if (!(be instanceof GhastHotAirBalloonAssemblyStationBlockEntity station))
			return;
		if (!station.isReadyToAccept())
			return;

		balloon.setMagnetTarget(targetPos);
	}

	private static double getMaxDistanceSqr() {
		double distance = CBConfigs.COMMON.ghastHotAirBalloon.magnetMaxDistance.get();
		return distance * distance;
	}
}
