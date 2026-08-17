package com.nobodiiiii.createbiotech.content.sonicdogcannon;

import com.nobodiiiii.createbiotech.client.SonicDogCannonItemRenderer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class SonicDogCannonGearAnimationPacket {

	private final int shooterId;
	private final InteractionHand hand;
	private final int chargeTicks;

	public SonicDogCannonGearAnimationPacket(int shooterId, InteractionHand hand, int chargeTicks) {
		this.shooterId = shooterId;
		this.hand = hand;
		this.chargeTicks = chargeTicks;
	}

	public SonicDogCannonGearAnimationPacket(FriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readEnum(InteractionHand.class), buffer.readVarInt());
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(shooterId);
		buffer.writeEnum(hand);
		buffer.writeVarInt(chargeTicks);
	}

	public void handle(Context context) {
		context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
			() -> () -> SonicDogCannonItemRenderer.onFired(shooterId, hand, chargeTicks)));
	}
}
