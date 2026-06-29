package com.nobodiiiii.createbiotech.content.shulkerteleporter;

import java.util.ArrayList;
import java.util.List;

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
	private final List<String> candidateAddresses;

	public ShulkerTeleporterConfigPacket(BlockPos pos, String ownAddress, String targetAddress,
		List<String> candidateAddresses) {
		this.pos = pos;
		this.ownAddress = ownAddress;
		this.targetAddress = targetAddress;
		this.candidateAddresses = ShulkerTeleporterBlockEntity.normalizeCandidateAddresses(candidateAddresses);
	}

	public ShulkerTeleporterConfigPacket(FriendlyByteBuf buffer) {
		pos = buffer.readBlockPos();
		ownAddress = buffer.readUtf(ShulkerTeleporterBlockEntity.MAX_ADDRESS_LENGTH);
		targetAddress = buffer.readUtf(ShulkerTeleporterBlockEntity.MAX_ADDRESS_LENGTH);
		int size = buffer.readVarInt();
		List<String> addresses = new ArrayList<>(size);
		for (int i = 0; i < size; i++)
			addresses.add(buffer.readUtf(ShulkerTeleporterBlockEntity.MAX_ADDRESS_LENGTH));
		candidateAddresses = ShulkerTeleporterBlockEntity.normalizeCandidateAddresses(addresses);
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeUtf(ownAddress, ShulkerTeleporterBlockEntity.MAX_ADDRESS_LENGTH);
		buffer.writeUtf(targetAddress, ShulkerTeleporterBlockEntity.MAX_ADDRESS_LENGTH);
		buffer.writeVarInt(candidateAddresses.size());
		for (String candidateAddress : candidateAddresses)
			buffer.writeUtf(candidateAddress, ShulkerTeleporterBlockEntity.MAX_ADDRESS_LENGTH);
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
				teleporter.setConfiguration(ownAddress, targetAddress, candidateAddresses);
		});
	}
}
