package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent.Context;

public class ShulkerTeleporterConfigPacket {

	private final BlockPos pos;
	private final String ownAddress;
	private final String targetAddress;

	public ShulkerTeleporterConfigPacket(BlockPos pos, String ownAddress, String targetAddress) {
		this.pos = pos;
		this.ownAddress = ownAddress;
		this.targetAddress = targetAddress;
	}

	public ShulkerTeleporterConfigPacket(FriendlyByteBuf buffer) {
		pos = buffer.readBlockPos();
		ownAddress = buffer.readUtf(32);
		targetAddress = buffer.readUtf(32);
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeUtf(ownAddress, 32);
		buffer.writeUtf(targetAddress, 32);
	}

	public void handle(Context context) {
		ServerPlayer player = context.getSender();
		if (player == null)
			return;
		context.enqueueWork(() -> {
			if (player.distanceToSqr(pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d) > 64.0d)
				return;
			BlockEntity blockEntity = player.level().getBlockEntity(pos);
			if (blockEntity instanceof ShulkerTeleporterBlockEntity teleporter)
				teleporter.setAddresses(ownAddress, targetAddress);
		});
	}
}
