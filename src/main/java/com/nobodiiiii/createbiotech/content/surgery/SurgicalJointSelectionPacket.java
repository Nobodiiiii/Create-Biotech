package com.nobodiiiii.createbiotech.content.surgery;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Changes only the selected mode of an existing joint stack, never its item count. */
public record SurgicalJointSelectionPacket(InteractionHand hand, int hotbarSlot, SurgicalLimbType type) {
	public SurgicalJointSelectionPacket(FriendlyByteBuf buffer) {
		this(buffer.readEnum(InteractionHand.class), buffer.readVarInt(), buffer.readEnum(SurgicalLimbType.class));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeEnum(hand);
		buffer.writeVarInt(hotbarSlot);
		buffer.writeEnum(type);
	}

	public void handle(ServerPlayer player) {
		if (player == null || player.isSpectator() || type == null
			|| hand == InteractionHand.MAIN_HAND && player.getInventory().selected != hotbarSlot)
			return;
		ItemStack held = player.getItemInHand(hand);
		if (SurgicalJointItem.canSelect(held))
			SurgicalJointItem.select(held, type);
	}
}
