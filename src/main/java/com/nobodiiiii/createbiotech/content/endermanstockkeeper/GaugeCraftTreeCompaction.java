package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.world.item.ItemStack;

/** Groups material occurrences for display without changing the execution graph. */
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
	private final int[] productionCounts;
	private final boolean[] craftingEdges;

	private GaugeCraftTreeCompaction(int[] targets, int size, int[] edgeTargets, List<GaugeCraftTree.Edge> edges,
		int[] productionCounts) {
		this.targets = targets;
		this.size = size;
		this.edgeTargets = edgeTargets;
		this.edges = edges;
		this.productionCounts = productionCounts;
		this.craftingEdges = new boolean[edges.size()];
	}

	static GaugeCraftTreeCompaction create(GaugeCraftTree snapshot) {
		int[] targets = new int[snapshot.nodes().size()];
		List<List<Integer>> consumers = new ArrayList<>(targets.length);
		BitSet[] reachable = new BitSet[targets.length];
		for (int i = 0; i < targets.length; i++) {
			consumers.add(new ArrayList<>());
			reachable[i] = new BitSet(targets.length);
			reachable[i].set(i);
		}
		for (GaugeCraftTree.Edge edge : snapshot.edges())
			consumers.get(edge.from()).add(edge.to());
		// Snapshots are topologically ordered. Include each node itself so a
		// merge can update all of its upstream dependencies with the same rule.
		for (int i = targets.length - 1; i >= 0; i--)
			for (int consumer : consumers.get(i))
				reachable[i].or(reachable[consumer]);
		Map<Material, List<Integer>> materials = new HashMap<>();
		for (int i = 0; i < targets.length; i++) {
			targets[i] = i;
			// Keep final products distinct from ingredients of the same type.
			if (consumers.get(i).isEmpty())
				continue;
			Material material = new Material(snapshot.nodes().get(i).stack());
			List<Integer> candidates = materials.computeIfAbsent(material, ignored -> new ArrayList<>());
			for (int candidate : candidates) {
				// Stock and crafted ingredients can share a gauge, unless doing
				// so would fold a dependency path back onto itself. Reachability
				// includes previous merges, which can also introduce new paths.
				if (reachable[candidate].get(i) || reachable[i].get(candidate))
					continue;
				BitSet merged = (BitSet) reachable[candidate].clone();
				merged.or(reachable[i]);
				for (BitSet upstream : reachable)
					if (upstream.get(candidate) || upstream.get(i))
						upstream.or(merged);
				targets[i] = candidate;
				break;
			}
			if (targets[i] == i)
				candidates.add(i);
		}
		int size = orderTargets(targets, snapshot.edges());
		int[] productionCounts = new int[size];
		for (int i = 0; i < targets.length; i++)
			if (snapshot.nodes().get(i).state() != GaugeCraftTree.State.STOCKED)
				productionCounts[targets[i]] += snapshot.nodes().get(i).count();
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
		return new GaugeCraftTreeCompaction(targets, size, edgeTargets, List.copyOf(edges), productionCounts);
	}

	private static int orderTargets(int[] targets, List<GaugeCraftTree.Edge> edges) {
		// Merging a later producer into an earlier gauge can move its inputs
		// after that gauge. Restore source-before-consumer order for layout.
		List<List<Integer>> consumers = new ArrayList<>(targets.length);
		for (int i = 0; i < targets.length; i++)
			consumers.add(new ArrayList<>());
		int[] incoming = new int[targets.length];
		for (GaugeCraftTree.Edge edge : edges) {
			consumers.get(targets[edge.from()]).add(targets[edge.to()]);
			incoming[targets[edge.to()]]++;
		}
		PriorityQueue<Integer> ready = new PriorityQueue<>();
		for (int i = 0; i < targets.length; i++)
			if (targets[i] == i && incoming[i] == 0)
				ready.add(i);
		int[] order = new int[targets.length];
		int size = 0;
		while (!ready.isEmpty()) {
			int next = ready.remove();
			order[next] = size++;
			for (int consumer : consumers.get(next))
				if (--incoming[consumer] == 0)
					ready.add(consumer);
		}
		for (int i = 0; i < targets.length; i++)
			targets[i] = order[targets[i]];
		return size;
	}

	GaugeCraftTree apply(GaugeCraftTree snapshot) {
		GaugeCraftTree.Node[] nodes = new GaugeCraftTree.Node[size];
		for (int i = 0; i < targets.length; i++) {
			GaugeCraftTree.Node node = snapshot.nodes().get(i);
			int target = targets[i];
			GaugeCraftTree.Node current = nodes[target];
			nodes[target] = current == null ? node : new GaugeCraftTree.Node(current.stack(),
				current.count() + node.count(), current.sent() + node.sent(),
				current.completed() + node.completed(), mergeState(current.state(), node.state()));
		}
		int[] sent = new int[edges.size()];
		Arrays.fill(craftingEdges, false);
		for (int i = 0; i < edgeTargets.length; i++) {
			GaugeCraftTree.Edge edge = snapshot.edges().get(i);
			sent[edgeTargets[i]] += edge.sent();
			craftingEdges[edgeTargets[i]] |= snapshot.nodes().get(edge.to()).state() == GaugeCraftTree.State.CRAFTING;
		}
		List<GaugeCraftTree.Edge> currentEdges = new ArrayList<>(edges.size());
		for (int i = 0; i < edges.size(); i++) {
			GaugeCraftTree.Edge edge = edges.get(i);
			currentEdges.add(new GaugeCraftTree.Edge(edge.from(), edge.to(), edge.count(), sent[i]));
		}
		return new GaugeCraftTree(snapshot.status(), snapshot.terminal(), List.of(nodes), List.copyOf(currentEdges));
	}

	int productionCount(int node) {
		// Existing stock is sent downstream, but does not need to be crafted.
		return productionCounts[node];
	}

	boolean isCrafting(int edge) {
		return craftingEdges[edge];
	}

	private static GaugeCraftTree.State mergeState(GaugeCraftTree.State first, GaugeCraftTree.State second) {
		if (first == second)
			return first;
		if (first == GaugeCraftTree.State.PAUSED || second == GaugeCraftTree.State.PAUSED)
			return GaugeCraftTree.State.PAUSED;
		if (first == GaugeCraftTree.State.CRAFTING || second == GaugeCraftTree.State.CRAFTING)
			return GaugeCraftTree.State.CRAFTING;
		if (first == GaugeCraftTree.State.WAITING || second == GaugeCraftTree.State.WAITING)
			return GaugeCraftTree.State.WAITING;
		return GaugeCraftTree.State.COMPLETE;
	}
}
