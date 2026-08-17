package com.nobodiiiii.createbiotech.content.sonicdogcannon;

import com.nobodiiiii.createbiotech.client.SonicDogCannonClientEffects;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class SonicDogCannonFirePacket {

	private final int shooterId;
	private final InteractionHand hand;
	private final Vec3 direction;
	private final float range;

	public SonicDogCannonFirePacket(int shooterId, InteractionHand hand, Vec3 direction, float range) {
		this.shooterId = shooterId;
		this.hand = hand;
		this.direction = direction;
		this.range = range;
	}

	public SonicDogCannonFirePacket(FriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readEnum(InteractionHand.class),
			new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()), buffer.readFloat());
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(shooterId);
		buffer.writeEnum(hand);
		buffer.writeFloat((float) direction.x);
		buffer.writeFloat((float) direction.y);
		buffer.writeFloat((float) direction.z);
		buffer.writeFloat(range);
	}

	public void handle(Context context) {
		context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
			() -> () -> SonicDogCannonClientEffects.fire(shooterId, hand, direction, range)));
	}
}
