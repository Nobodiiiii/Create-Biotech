package com.nobodiiiii.createbiotech.content.dingdongchicken;

import com.nobodiiiii.createbiotech.client.DingDongChickenVoiceSoundHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class DingDongChickenVoiceSoundPacket {

	private final int entityId;

	public DingDongChickenVoiceSoundPacket(int entityId) {
		this.entityId = entityId;
	}

	public DingDongChickenVoiceSoundPacket(FriendlyByteBuf buffer) {
		this(buffer.readVarInt());
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(entityId);
	}

	public void handle(Context context) {
		context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
			() -> () -> DingDongChickenVoiceSoundHandler.play(entityId)));
	}
}
