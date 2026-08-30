package com.nobodiiiii.createbiotech.entity;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;

/** Starts one client attack presentation or updates its direction when the server locks the aim. */
public record SlimeBionicAttackActionPacket(int entityId, int sequence, boolean restart,
	boolean left, int slot, boolean weapon, int duration, float aimYaw, float aimPitch) {

	public SlimeBionicAttackActionPacket(RegistryFriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(),
			buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(), buffer.readFloat(),
			buffer.readFloat());
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
			entity.getAttackActionDuration(), entity.getAttackAimYaw(), entity.getAttackAimPitch());
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(entityId);
		buffer.writeVarInt(sequence);
		buffer.writeBoolean(restart);
		buffer.writeBoolean(left);
		buffer.writeVarInt(slot);
		buffer.writeBoolean(weapon);
		buffer.writeVarInt(duration);
		buffer.writeFloat(aimYaw);
		buffer.writeFloat(aimPitch);
	}

	public void handle(LocalPlayer player) {
		if (player == null || player.level() == null)
			return;
		Entity found = player.level().getEntity(entityId);
		if (found instanceof SlimeBionicEntity bionic)
			bionic.applyAttackAction(sequence, restart, left, slot, weapon, duration, aimYaw, aimPitch);
	}
}
