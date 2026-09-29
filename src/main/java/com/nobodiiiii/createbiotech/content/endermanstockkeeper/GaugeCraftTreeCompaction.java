package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.ItemStack;

/** Groups warehouse material occurrences for display without changing the execution graph. */
final class GaugeCraftTreeCompaction {

	private record Material(ItemStack stack) {
		@Override
		public boolean equals(Object other) {
			return other instanceof Material material && ItemStack.isSameItemSameComponents(stack, material.stack());
		}

		@Override
		public int hashCode() {
			return ItemStack.hashItemAndComponents(stack);
		}
	}
	private record Connection(int from, int to) {}
	private final int[] targets;
	private final int size;
	private final List<GaugeCraftTree.Edge> edges;

	private GaugeCraftTreeCompaction(int[] targets, int size, List<GaugeCraftTree.Edge> edges) {
		this.targets = targets;
		this.size = size;
		this.edges = edges;
	}

	static GaugeCraftTreeCompaction create(GaugeCraftTree snapshot) {
		int[] targets = new int[snapshot.nodes().size()];
		boolean[] hasInputs = new boolean[targets.length];
		for (GaugeCraftTree.Edge edge : snapshot.edges())
			hasInputs[edge.to()] = true;
		Map<Material, Integer> materials = new HashMap<>();
		int size = 0;
		for (int i = 0; i < targets.length; i++) {
			GaugeCraftTree.Node node = snapshot.nodes().get(i);
			// Finished crafting steps remain distinct even when they produce the
			// same item as a warehouse material or another step.
			if (!hasInputs[i] && node.state() == GaugeCraftTree.State.STOCKED) {
				Material material = new Material(node.stack());
				Integer existing = materials.putIfAbsent(material, size);
				if (existing != null) {
					targets[i] = existing;
					continue;
				}
			}
			targets[i] = size++;
		}
		Map<Connection, Integer> amounts = new LinkedHashMap<>();
		for (GaugeCraftTree.Edge edge : snapshot.edges())
			amounts.merge(new Connection(targets[edge.from()], targets[edge.to()]), edge.count(), Integer::sum);
		List<GaugeCraftTree.Edge> edges = new ArrayList<>(amounts.size());
		amounts.forEach((connection, count) -> edges.add(new GaugeCraftTree.Edge(connection.from(), connection.to(), count)));
		return new GaugeCraftTreeCompaction(targets, size, List.copyOf(edges));
	}

	GaugeCraftTree apply(GaugeCraftTree snapshot) {
		GaugeCraftTree.Node[] nodes = new GaugeCraftTree.Node[size];
		for (int i = 0; i < targets.length; i++) {
			GaugeCraftTree.Node node = snapshot.nodes().get(i);
			int target = targets[i];
			GaugeCraftTree.Node current = nodes[target];
			nodes[target] = current == null ? node : new GaugeCraftTree.Node(current.stack(),
				current.count() + node.count(), current.sent() + node.sent(),
				current.completed() + node.completed(), current.state());
		}
		return new GaugeCraftTree(snapshot.status(), snapshot.terminal(), List.of(nodes), edges);
	}
}
