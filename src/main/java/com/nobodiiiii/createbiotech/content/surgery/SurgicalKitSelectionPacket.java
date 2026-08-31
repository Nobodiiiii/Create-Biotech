package com.nobodiiiii.createbiotech.content.surgery;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative update for a surgical kit's selected logical tool. */
public record SurgicalKitSelectionPacket(InteractionHand hand, SurgicalKitItem.Tool tool) {

	public SurgicalKitSelectionPacket(FriendlyByteBuf buffer) {
		this(buffer.readEnum(InteractionHand.class), buffer.readEnum(SurgicalKitItem.Tool.class));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeEnum(hand);
		buffer.writeEnum(tool);
	}

	public void handle(ServerPlayer player) {
		if (player == null || player.isSpectator() || tool == null)
			return;
		ItemStack stack = player.getItemInHand(hand);
		if (SurgicalKitItem.isKit(stack))
			SurgicalKitItem.setSelectedTool(stack, tool);
	}
}
