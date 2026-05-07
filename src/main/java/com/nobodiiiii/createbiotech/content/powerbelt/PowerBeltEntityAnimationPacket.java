package com.nobodiiiii.createbiotech.content.powerbelt;

import com.nobodiiiii.createbiotech.client.PowerBeltClientAnimationHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class PowerBeltEntityAnimationPacket {

	private static final float MAX_SURFACE_MOVEMENT = 1.0f;

	private final int entityId;
	private final float distance;

	public PowerBeltEntityAnimationPacket(int entityId, float distance) {
		this.entityId = entityId;
		this.distance = distance;
	}

	public PowerBeltEntityAnimationPacket(FriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readFloat());
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(entityId);
		buffer.writeFloat(distance);
	}

	public boolean handle(Context context) {
		if (!Float.isFinite(distance))
			return true;

		context.enqueueWork(() -> {
			float clampedDistance = Mth.clamp(distance, 0, MAX_SURFACE_MOVEMENT);
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> PowerBeltClientAnimationHandler.handleSurfaceMovement(entityId, clampedDistance));
		});
		return true;
	}
}
