package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.UUID;

import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record GaugeCraftTreeRequestPacket(int menuId, UUID jobId) {
	public GaugeCraftTreeRequestPacket(RegistryFriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readUUID());
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(menuId);
		buffer.writeUUID(jobId);
	}

	public void handle(ServerPlayer player) {
		if (!(player.containerMenu instanceof EndermanStockKeeperRequestMenu menu) || menu.containerId != menuId
			|| !menu.stillValid(player) || !menu.contentHolder.behaviour.mayInteract(player)
			|| menu.contentHolder.behaviour.freqId == null)
			return;
		CBPackets.sendToPlayer(new GaugeCraftTreePacket(menuId, jobId, GaugeCraftJobs.get(player.server)
			.tree(menu.contentHolder.behaviour.freqId, jobId, player.server)), player);
	}
}
