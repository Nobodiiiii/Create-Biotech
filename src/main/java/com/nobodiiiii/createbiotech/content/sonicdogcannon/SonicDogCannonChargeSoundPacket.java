package com.nobodiiiii.createbiotech.content.sonicdogcannon;

import com.nobodiiiii.createbiotech.client.SonicDogCannonChargeSoundHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class SonicDogCannonChargeSoundPacket {

	private final int shooterId;
	private final Action action;

	public SonicDogCannonChargeSoundPacket(int shooterId, Action action) {
		this.shooterId = shooterId;
		this.action = action;
	}

	public SonicDogCannonChargeSoundPacket(FriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readEnum(Action.class));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(shooterId);
		buffer.writeEnum(action);
	}

	public void handle(Context context) {
		context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
			() -> () -> SonicDogCannonChargeSoundHandler.handle(shooterId, action)));
	}

	public enum Action {
		DEFAULT_START,
		VOICE_PACK_START,
		VOICE_PACK_LOOP,
		STOP
	}
}
