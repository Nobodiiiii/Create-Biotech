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
	private final int[] edgeTargets;
	private final List<GaugeCraftTree.Edge> edges;

	private GaugeCraftTreeCompaction(int[] targets, int size, int[] edgeTargets, List<GaugeCraftTree.Edge> edges) {
		this.targets = targets;
		this.size = size;
		this.edgeTargets = edgeTargets;
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
		Map<Connection, Integer> connections = new LinkedHashMap<>();
		List<GaugeCraftTree.Edge> edges = new ArrayList<>();
		int[] edgeTargets = new int[snapshot.edges().size()];
		for (int i = 0; i < edgeTargets.length; i++) {
			GaugeCraftTree.Edge edge = snapshot.edges().get(i);
			Connection connection = new Connection(targets[edge.from()], targets[edge.to()]);
			Integer target = connections.get(connection);
			if (target == null) {
				target = edges.size();
				connections.put(connection, target);
				edges.add(new GaugeCraftTree.Edge(connection.from(), connection.to(), edge.count(), 0));
			} else {
				GaugeCraftTree.Edge current = edges.get(target);
				edges.set(target, new GaugeCraftTree.Edge(current.from(), current.to(), current.count() + edge.count(), 0));
			}
			edgeTargets[i] = target;
		}
		return new GaugeCraftTreeCompaction(targets, size, edgeTargets, List.copyOf(edges));
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
		int[] sent = new int[edges.size()];
		for (int i = 0; i < edgeTargets.length; i++)
			sent[edgeTargets[i]] += snapshot.edges().get(i).sent();
		List<GaugeCraftTree.Edge> currentEdges = new ArrayList<>(edges.size());
		for (int i = 0; i < edges.size(); i++) {
			GaugeCraftTree.Edge edge = edges.get(i);
			currentEdges.add(new GaugeCraftTree.Edge(edge.from(), edge.to(), edge.count(), sent[i]));
		}
		return new GaugeCraftTree(snapshot.status(), snapshot.terminal(), List.of(nodes), List.copyOf(currentEdges));
	}
}
