package com.nobodiiiii.createbiotech.entity;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;

/** Starts one presentation or synchronizes the logical preview's direction and remaining time. */
public record SlimeBionicAttackActionPacket(int entityId, int sequence, boolean restart,
	boolean left, int slot, boolean weapon, int interval, int remainingTicks,
	float aimYaw, float aimPitch, float bodyYaw) {

	public SlimeBionicAttackActionPacket(RegistryFriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(),
			buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat(),
			buffer.readFloat(), buffer.readFloat());
	}

	public static SlimeBionicAttackActionPacket start(SlimeBionicEntity entity) {
		return snapshot(entity, true);
	}

	public static SlimeBionicAttackActionPacket aim(SlimeBionicEntity entity) {
		return snapshot(entity, false);
	}

	private static SlimeBionicAttackActionPacket snapshot(SlimeBionicEntity entity, boolean restart) {
		return new SlimeBionicAttackActionPacket(entity.getId(), entity.getAttackActionSequence(), restart,
			entity.isAttackActionLeft(), entity.getAttackActionArmSlot(), entity.isAttackActionWeapon(),
			entity.getAttackActionInterval(), entity.getAttackActionTick(),
			entity.getAttackAimYaw(), entity.getAttackAimPitch(), entity.getAttackBodyYaw());
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(entityId);
		buffer.writeVarInt(sequence);
		buffer.writeBoolean(restart);
		buffer.writeBoolean(left);
		buffer.writeVarInt(slot);
		buffer.writeBoolean(weapon);
		buffer.writeVarInt(interval);
		buffer.writeVarInt(remainingTicks);
		buffer.writeFloat(aimYaw);
		buffer.writeFloat(aimPitch);
		buffer.writeFloat(bodyYaw);
	}

	public void handle(LocalPlayer player) {
		if (player == null || player.level() == null)
			return;
		Entity found = player.level().getEntity(entityId);
		if (found instanceof SlimeBionicEntity bionic)
			bionic.applyAttackAction(sequence, restart, left, slot, weapon, interval, remainingTicks,
				aimYaw, aimPitch, bodyYaw);
	}
}
