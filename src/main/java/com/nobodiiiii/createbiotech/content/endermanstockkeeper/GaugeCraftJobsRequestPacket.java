package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record GaugeCraftJobsRequestPacket(int menuId) {
	public GaugeCraftJobsRequestPacket(RegistryFriendlyByteBuf buffer) { this(buffer.readVarInt()); }
	public void write(RegistryFriendlyByteBuf buffer) { buffer.writeVarInt(menuId); }

	public void handle(ServerPlayer player) {
		if (!(player.containerMenu instanceof EndermanStockKeeperRequestMenu menu) || menu.containerId != menuId
			|| !menu.stillValid(player) || !menu.contentHolder.behaviour.mayInteract(player)
			|| menu.contentHolder.behaviour.freqId == null)
			return;
		CBPackets.sendToPlayer(new GaugeCraftJobsPacket(menuId, GaugeCraftJobs.get(player.server)
			.summaries(menu.contentHolder.behaviour.freqId, player.getUUID(), player.server)), player);
	}
}
