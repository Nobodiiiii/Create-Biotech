package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Client display occurrences; expanding a branch never creates another execution step. */
final class GaugeCraftTreeExpansion {

	// A small DAG can unfold exponentially. Refuse an oversized expansion rather
	// than silently omitting dependencies or freezing the client.
	private static final int MAX_OCCURRENCES = 16384;
	private record Occurrence(int source, int count) {}
	private record Pending(int source, int parent, int count) {}
	private final List<Occurrence> occurrences;
	private final List<GaugeCraftTree.Edge> edges;
	private final int[] copies;

	private GaugeCraftTreeExpansion(List<Occurrence> occurrences, List<GaugeCraftTree.Edge> edges, int[] copies) {
		this.occurrences = occurrences;
		this.edges = edges;
		this.copies = copies;
	}

	static GaugeCraftTreeExpansion create(GaugeCraftTree tree) {
		int size = tree.nodes().size();
		List<List<GaugeCraftTree.Edge>> inputs = new ArrayList<>(size);
		for (int i = 0; i < size; i++)
			inputs.add(new ArrayList<>());
		boolean[] consumed = new boolean[size];
		for (GaugeCraftTree.Edge edge : tree.edges()) {
			inputs.get(edge.to()).add(edge);
			consumed[edge.from()] = true;
		}
		int[] branchSizes = new int[size];
		int total = 0;
		for (int i = 0; i < size; i++) {
			branchSizes[i] = 1;
			for (GaugeCraftTree.Edge edge : inputs.get(i))
				branchSizes[i] = Math.min(MAX_OCCURRENCES + 1, branchSizes[i] + branchSizes[edge.from()]);
			if (!consumed[i]) {
				total += branchSizes[i];
				if (total > MAX_OCCURRENCES)
					return null;
			}
		}

		List<Occurrence> occurrences = new ArrayList<>(total);
		List<GaugeCraftTree.Edge> edges = new ArrayList<>();
		int[] copies = new int[size];
		ArrayDeque<Pending> pending = new ArrayDeque<>();
		for (int i = 0; i < size; i++)
			if (!consumed[i])
				pending.push(new Pending(i, -1, tree.nodes().get(i).count()));
		while (!pending.isEmpty()) {
			Pending next = pending.pop();
			int index = occurrences.size();
			occurrences.add(new Occurrence(next.source(), next.count()));
			copies[next.source()]++;
			if (next.parent() >= 0)
				edges.add(new GaugeCraftTree.Edge(index, next.parent(), next.count()));
			int originalCount = tree.nodes().get(next.source()).count();
			for (GaugeCraftTree.Edge input : inputs.get(next.source()))
				pending.push(new Pending(input.from(), index, scaleUp(input.count(), next.count(), originalCount)));
		}
		// Keep the packet's source-before-consumer convention, with siblings in
		// input order. An explicit stack also supports very deep plans safely.
		Collections.reverse(occurrences);
		List<GaugeCraftTree.Edge> orderedEdges = new ArrayList<>(edges.size());
		for (int i = edges.size() - 1; i >= 0; i--) {
			GaugeCraftTree.Edge edge = edges.get(i);
			orderedEdges.add(new GaugeCraftTree.Edge(total - 1 - edge.from(), total - 1 - edge.to(), edge.count()));
		}
		return new GaugeCraftTreeExpansion(List.copyOf(occurrences), List.copyOf(orderedEdges), copies);
	}

	GaugeCraftTree apply(GaugeCraftTree snapshot) {
		List<GaugeCraftTree.Node> nodes = new ArrayList<>(occurrences.size());
		for (Occurrence occurrence : occurrences) {
			GaugeCraftTree.Node original = snapshot.nodes().get(occurrence.source());
			int completed = (int) ((long) original.completed() * occurrence.count() / original.count());
			nodes.add(new GaugeCraftTree.Node(original.stack(), occurrence.count(), completed, original.state()));
		}
		return new GaugeCraftTree(snapshot.status(), snapshot.terminal(), List.copyOf(nodes), edges);
	}

	GaugeCraftTree.Node source(GaugeCraftTree snapshot, int index) {
		return snapshot.nodes().get(occurrences.get(index).source());
	}

	boolean shared(int index) {
		return copies[occurrences.get(index).source()] > 1;
	}

	private static int scaleUp(int count, int portion, int total) {
		// Branch-local requirements are rounded to whole items. Shared batches
		// can use fewer items in total; their actual progress stays on the source.
		return (int) (((long) count * portion + total - 1) / total);
	}
}
