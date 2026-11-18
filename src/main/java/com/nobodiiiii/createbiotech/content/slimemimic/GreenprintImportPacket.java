package com.nobodiiiii.createbiotech.content.slimemimic;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Transfers the selected client file without sending its filename or writing it to the server. */
public record GreenprintImportPacket(int containerId, byte[] compressed) {
	static final int MAX_FILE_BYTES = 512 * 1024;
	static final long MAX_NBT_BYTES = 2 * 1024 * 1024;

	public GreenprintImportPacket(FriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readByteArray(MAX_FILE_BYTES));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(containerId);
		buffer.writeByteArray(compressed);
	}

	public void handle(ServerPlayer player) {
		if (!(player.containerMenu instanceof GreenprintTableMenu menu) || menu.containerId != containerId
			|| !menu.canReceive(player))
			return;
		boolean imported;
		try {
			var file = NbtIo.readCompressed(new ByteArrayInputStream(compressed), NbtAccounter.create(MAX_NBT_BYTES));
			imported = menu.importFile(player, file);
		} catch (IOException | RuntimeException exception) {
			imported = false;
		}
		if (!imported)
			player.displayClientMessage(Component.translatable("message.create_biotech.greenprint_table.invalid_file"), false);
	}
}
