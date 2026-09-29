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
	private record Occurrence(int source, int outgoingEdge, int count) {}
	private record Pending(int source, int parent, int outgoingEdge, int count) {}
	private final List<Occurrence> occurrences;
	private final List<GaugeCraftTree.Edge> edges;

	private GaugeCraftTreeExpansion(List<Occurrence> occurrences, List<GaugeCraftTree.Edge> edges) {
		this.occurrences = occurrences;
		this.edges = edges;
	}

	static GaugeCraftTreeExpansion create(GaugeCraftTree tree) {
		int size = tree.nodes().size();
		List<List<Integer>> inputs = new ArrayList<>(size);
		for (int i = 0; i < size; i++)
			inputs.add(new ArrayList<>());
		boolean[] consumed = new boolean[size];
		for (int i = 0; i < tree.edges().size(); i++) {
			GaugeCraftTree.Edge edge = tree.edges().get(i);
			inputs.get(edge.to()).add(i);
			consumed[edge.from()] = true;
		}
		int[] branchSizes = new int[size];
		int total = 0;
		for (int i = 0; i < size; i++) {
			branchSizes[i] = 1;
			for (int edge : inputs.get(i))
				branchSizes[i] = Math.min(MAX_OCCURRENCES + 1, branchSizes[i] + branchSizes[tree.edges().get(edge).from()]);
			if (!consumed[i]) {
				total += branchSizes[i];
				if (total > MAX_OCCURRENCES)
					return null;
			}
		}

		List<Occurrence> occurrences = new ArrayList<>(total);
		List<GaugeCraftTree.Edge> edges = new ArrayList<>();
		ArrayDeque<Pending> pending = new ArrayDeque<>();
		for (int i = 0; i < size; i++)
			if (!consumed[i])
				pending.push(new Pending(i, -1, -1, tree.nodes().get(i).count()));
		while (!pending.isEmpty()) {
			Pending next = pending.pop();
			int index = occurrences.size();
			occurrences.add(new Occurrence(next.source(), next.outgoingEdge(), next.count()));
			if (next.parent() >= 0)
				edges.add(new GaugeCraftTree.Edge(index, next.parent(), next.count(), 0));
			int originalCount = tree.nodes().get(next.source()).count();
			for (int edge : inputs.get(next.source())) {
				GaugeCraftTree.Edge input = tree.edges().get(edge);
				pending.push(new Pending(input.from(), index, edge, scaleUp(input.count(), next.count(), originalCount)));
			}
		}
		// Keep the packet's source-before-consumer convention, with siblings in
		// input order. An explicit stack also supports very deep plans safely.
		Collections.reverse(occurrences);
		List<GaugeCraftTree.Edge> orderedEdges = new ArrayList<>(edges.size());
		for (int i = edges.size() - 1; i >= 0; i--) {
			GaugeCraftTree.Edge edge = edges.get(i);
			orderedEdges.add(new GaugeCraftTree.Edge(total - 1 - edge.from(), total - 1 - edge.to(), edge.count(), 0));
		}
		return new GaugeCraftTreeExpansion(List.copyOf(occurrences), List.copyOf(orderedEdges));
	}

	GaugeCraftTree apply(GaugeCraftTree snapshot) {
		List<GaugeCraftTree.Node> nodes = new ArrayList<>(occurrences.size());
		for (Occurrence occurrence : occurrences) {
			GaugeCraftTree.Node original = snapshot.nodes().get(occurrence.source());
			int sent = 0;
			if (occurrence.outgoingEdge() >= 0) {
				GaugeCraftTree.Edge outgoing = snapshot.edges().get(occurrence.outgoingEdge());
				// Use this branch's actual dispatch, never a share of the source's
				// total: another consumer may not have requested any material yet.
				sent = (int) ((long) outgoing.sent() * occurrence.count() / outgoing.count());
			}
			int completed = (int) ((long) original.completed() * occurrence.count() / original.count());
			nodes.add(new GaugeCraftTree.Node(original.stack(), occurrence.count(), sent, completed, original.state()));
		}
		List<GaugeCraftTree.Edge> currentEdges = new ArrayList<>(edges.size());
		for (GaugeCraftTree.Edge edge : edges)
			currentEdges.add(new GaugeCraftTree.Edge(edge.from(), edge.to(), edge.count(), nodes.get(edge.from()).sent()));
		return new GaugeCraftTree(snapshot.status(), snapshot.terminal(), List.copyOf(nodes), List.copyOf(currentEdges));
	}

	private static int scaleUp(int count, int portion, int total) {
		// Branch-local requirements are rounded to whole items. Shared batches
		// can use fewer items in total; their actual progress stays on the source.
		return (int) (((long) count * portion + total - 1) / total);
	}
}
