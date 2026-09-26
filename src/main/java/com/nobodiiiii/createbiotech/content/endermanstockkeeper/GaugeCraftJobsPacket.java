package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record GaugeCraftJobsPacket(int menuId, List<GaugeCraftJobs.Summary> jobs) {
	public GaugeCraftJobsPacket(RegistryFriendlyByteBuf buffer) {
		this(buffer.readVarInt(), readJobs(buffer));
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(menuId);
		buffer.writeVarInt(jobs.size());
		for (GaugeCraftJobs.Summary job : jobs) {
			buffer.writeUUID(job.id());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, job.output());
			buffer.writeVarInt(job.count());
			buffer.writeUtf(job.status(), 64);
			buffer.writeVarInt(job.completed());
			buffer.writeVarInt(job.total());
			buffer.writeUtf(job.address(), 64);
			buffer.writeBoolean(job.canCancel());
		}
	}

	private static List<GaugeCraftJobs.Summary> readJobs(RegistryFriendlyByteBuf buffer) {
		int size = buffer.readVarInt();
		if (size < 0 || size > GaugeCraftJobs.MAX_ACTIVE_JOBS)
			throw new IllegalArgumentException("Invalid gauge crafting job list");
		List<GaugeCraftJobs.Summary> jobs = new ArrayList<>(size);
		for (int i = 0; i < size; i++)
			jobs.add(new GaugeCraftJobs.Summary(buffer.readUUID(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
				buffer.readVarInt(), buffer.readUtf(64), buffer.readVarInt(), buffer.readVarInt(),
				buffer.readUtf(64), buffer.readBoolean()));
		return List.copyOf(jobs);
	}

	public void handle(LocalPlayer player) {
		if (Minecraft.getInstance().screen instanceof EndermanStockKeeperRequestScreen screen
			&& screen.getMenu().containerId == menuId)
			screen.acceptJobs(this);
	}
}
