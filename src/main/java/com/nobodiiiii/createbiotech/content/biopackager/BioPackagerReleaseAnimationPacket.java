package com.nobodiiiii.createbiotech.content.biopackager;

import com.nobodiiiii.createbiotech.client.BioPackagerReleaseAnimationHandler;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Starts the visual emerge animation after a stationary bio-packager releases an entity. */
public final class BioPackagerReleaseAnimationPacket {
	private final int entityId;

	public BioPackagerReleaseAnimationPacket(int entityId) {
		this.entityId = entityId;
	}

	public BioPackagerReleaseAnimationPacket(FriendlyByteBuf buffer) {
		this(buffer.readVarInt());
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(entityId);
	}

	@OnlyIn(Dist.CLIENT)
	public void handle(LocalPlayer player) {
		BioPackagerReleaseAnimationHandler.start(entityId, player.clientLevel);
	}
}
