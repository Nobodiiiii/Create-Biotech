package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.List;
import java.util.UUID;

import com.nobodiiiii.createbiotech.network.CBPackets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Cancels only the player's own active tree order on this menu's network. */
public record GaugeCraftCancelPacket() {
	public GaugeCraftCancelPacket(RegistryFriendlyByteBuf buffer) { this(); }
	public void write(RegistryFriendlyByteBuf buffer) {}

	public void handle(ServerPlayer player) {
		if (!(player.containerMenu instanceof EndermanStockKeeperRequestMenu menu) || !menu.stillValid(player)
			|| !menu.contentHolder.behaviour.mayInteract(player))
			return;
		GaugeCraftRequestPacket.clearSession(player.getUUID());
		UUID network = menu.contentHolder.behaviour.freqId;
		if (network != null && GaugeCraftJobs.get(player.server).cancel(network, player.getUUID()))
			CBPackets.sendToPlayer(new GaugeCraftPreviewPacket(UUID.randomUUID(), "cancelled", List.of()), player);
	}
}
