package com.nobodiiiii.createbiotech.content.biopackager;

import net.minecraft.network.FriendlyByteBuf;

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

	public int entityId() {
		return entityId;
	}
}
