package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record GaugeCraftPreviewPacket(UUID token, String status, List<GaugeCraftPlan.Line> lines) {
	public GaugeCraftPreviewPacket(RegistryFriendlyByteBuf buffer) {
		this(buffer.readUUID(), buffer.readUtf(256), readLines(buffer));
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(token);
		buffer.writeUtf(status, 256);
		buffer.writeVarInt(lines.size());
		for (GaugeCraftPlan.Line line : lines) {
			buffer.writeVarInt(line.depth());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, line.stack());
			buffer.writeVarInt(line.count());
			buffer.writeBoolean(line.missing());
			buffer.writeBoolean(line.stocked());
		}
	}

	private static List<GaugeCraftPlan.Line> readLines(RegistryFriendlyByteBuf buffer) {
		int size = buffer.readVarInt();
		if (size < 0 || size > 1024)
			throw new IllegalArgumentException("Invalid gauge craft preview");
		List<GaugeCraftPlan.Line> lines = new ArrayList<>(size);
		for (int i = 0; i < size; i++)
			lines.add(new GaugeCraftPlan.Line(buffer.readVarInt(),
				ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarInt(),
				buffer.readBoolean(), buffer.readBoolean()));
		return List.copyOf(lines);
	}

	public void handle(LocalPlayer player) {
		if (Minecraft.getInstance().screen instanceof EndermanStockKeeperRequestScreen screen)
			screen.acceptPreview(this);
	}
}
