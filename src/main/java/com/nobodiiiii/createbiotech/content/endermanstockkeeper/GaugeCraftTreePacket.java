package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record GaugeCraftTreePacket(int menuId, UUID jobId, GaugeCraftTree tree) {
	public GaugeCraftTreePacket(RegistryFriendlyByteBuf buffer) {
		this(buffer.readVarInt(), buffer.readUUID(), readTree(buffer));
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(menuId);
		buffer.writeUUID(jobId);
		buffer.writeUtf(tree.status(), 64);
		buffer.writeBoolean(tree.terminal());
		buffer.writeVarInt(tree.nodes().size());
		for (GaugeCraftTree.Node node : tree.nodes()) {
			ItemStack.STREAM_CODEC.encode(buffer, node.stack());
			buffer.writeVarInt(node.count());
			buffer.writeVarInt(node.completed());
			buffer.writeEnum(node.state());
		}
		buffer.writeVarInt(tree.edges().size());
		for (GaugeCraftTree.Edge edge : tree.edges()) {
			buffer.writeVarInt(edge.from());
			buffer.writeVarInt(edge.to());
			buffer.writeVarInt(edge.count());
		}
	}

	private static GaugeCraftTree readTree(RegistryFriendlyByteBuf buffer) {
		String status = buffer.readUtf(64);
		boolean terminal = buffer.readBoolean();
		int size = bounded(buffer, 0, GaugeCraftTree.MAX_NODES);
		List<GaugeCraftTree.Node> nodes = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			ItemStack stack = ItemStack.STREAM_CODEC.decode(buffer);
			int count = bounded(buffer, 1, GaugeCraftPlan.MAX_REQUEST);
			int completed = bounded(buffer, 0, count);
			nodes.add(new GaugeCraftTree.Node(stack, count, completed, buffer.readEnum(GaugeCraftTree.State.class)));
		}
		int edgeCount = bounded(buffer, 0, GaugeCraftTree.MAX_EDGES);
		List<GaugeCraftTree.Edge> edges = new ArrayList<>(edgeCount);
		for (int i = 0; i < edgeCount; i++) {
			int from = bounded(buffer, 0, size - 1);
			int to = bounded(buffer, from + 1, size - 1);
			edges.add(new GaugeCraftTree.Edge(from, to, bounded(buffer, 1, GaugeCraftPlan.MAX_REQUEST)));
		}
		return new GaugeCraftTree(status, terminal, List.copyOf(nodes), List.copyOf(edges));
	}

	private static int bounded(RegistryFriendlyByteBuf buffer, int min, int max) {
		int value = buffer.readVarInt();
		if (value < min || value > max)
			throw new IllegalArgumentException("Invalid gauge crafting tree");
		return value;
	}

	public void handle(LocalPlayer player) {
		if (Minecraft.getInstance().screen instanceof EndermanStockKeeperRequestScreen screen
			&& screen.getMenu().containerId == menuId)
			screen.acceptTree(this);
	}
}
