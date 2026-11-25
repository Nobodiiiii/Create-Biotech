package com.nobodiiiii.createbiotech.content.surgery;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative update for a surgical kit's selected logical tool. */
public record SurgicalKitSelectionPacket(InteractionHand hand, int hotbarSlot, @Nullable BlockPos placedPos,
	@Nullable SurgicalKitItem.Tool tool) {

	public SurgicalKitSelectionPacket(FriendlyByteBuf buffer) {
		this(buffer.readEnum(InteractionHand.class), buffer.readVarInt(),
			buffer.readNullable(buf -> buf.readBlockPos()), buffer.readNullable(buf -> buf.readEnum(SurgicalKitItem.Tool.class)));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeEnum(hand);
		buffer.writeVarInt(hotbarSlot);
		buffer.writeNullable(placedPos, (buf, pos) -> buf.writeBlockPos(pos));
		buffer.writeNullable(tool, (buf, selected) -> buf.writeEnum(selected));
	}

	public void handle(ServerPlayer player) {
		if (player == null || !player.isAlive() || player.isSpectator()
			|| !Inventory.isHotbarSlot(hotbarSlot)
			|| (placedPos != null || hand == InteractionHand.MAIN_HAND) && player.getInventory().selected != hotbarSlot)
			return;
		var level = player.serverLevel();
		if (placedPos != null) {
			if (level.isInWorldBounds(placedPos) && level.isLoaded(placedPos) && level.mayInteract(player, placedPos)
				&& level.getBlockEntity(placedPos) instanceof SurgicalKitBlockEntity kit && kit.inRange(player))
				kit.select(player, hotbarSlot, tool);
			return;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!SurgicalKitItem.isKit(stack))
			return;
		if (tool == null && SurgicalKitItem.isBorrowedTool(stack)) {
			player.setItemInHand(hand, ItemStack.EMPTY);
		} else {
			SurgicalKitItem.setSelectedTool(stack, tool);
		}
	}
}
