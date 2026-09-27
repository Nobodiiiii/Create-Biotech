package com.nobodiiiii.createbiotech.entity.trait;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record BionicTraitDonorsRequestPacket(long generation, int offset) {
	public BionicTraitDonorsRequestPacket(RegistryFriendlyByteBuf buffer) {
		this(buffer.readLong(), buffer.readVarInt());
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeLong(generation);
		buffer.writeVarInt(offset);
	}

	public void handle(ServerPlayer player) {
		BionicTraitDonorCatalog.request(player, generation, offset);
	}
}
