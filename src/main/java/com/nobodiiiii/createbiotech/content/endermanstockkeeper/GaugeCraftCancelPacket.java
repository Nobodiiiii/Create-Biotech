package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Cancels one of the player's orders, identified by its persistent ID. */
public record GaugeCraftCancelPacket(int menuId, UUID jobId) {
	public GaugeCraftCancelPacket(RegistryFriendlyByteBuf buffer) { this(buffer.readVarInt(), buffer.readUUID()); }
	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(menuId);
		buffer.writeUUID(jobId);
	}

	public void handle(ServerPlayer player) {
		if (!(player.containerMenu instanceof EndermanStockKeeperRequestMenu menu) || menu.containerId != menuId
			|| !menu.stillValid(player)
			|| !menu.contentHolder.behaviour.mayInteract(player))
			return;
		GaugeCraftRequestPacket.clearSession(player.getUUID());
		UUID network = menu.contentHolder.behaviour.freqId;
		if (network != null)
			GaugeCraftJobs.get(player.server).cancel(network, player.getUUID(), jobId);
		new GaugeCraftJobsRequestPacket(menuId).handle(player);
	}
}
