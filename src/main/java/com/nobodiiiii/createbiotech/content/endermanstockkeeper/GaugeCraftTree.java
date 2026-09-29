package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.ItemStack;

/** A read-only material-flow snapshot of the saved execution plan, independent of loaded gauges. */
public record GaugeCraftTree(String status, boolean terminal, List<Node> nodes, List<Edge> edges) {

	public static final int MAX_NODES = 2048;
	public static final int MAX_EDGES = 4096;

	public enum State { WAITING, CRAFTING, COMPLETE, STOCKED, PAUSED }
	public record Node(ItemStack stack, int count, int sent, int completed, State state) {}
	public record Edge(int from, int to, int count) {}
	record Progress(int dispatchedCrafts, int completed, State state) {}

	public static GaugeCraftTree unavailable() {
		return new GaugeCraftTree("unavailable", true, List.of(), List.of());
	}

	static GaugeCraftTree snapshot(List<GaugeCraftPlan.Step> steps, GaugeCraftFlow flow, int requested,
		List<Progress> progress, String status, boolean terminal) {
		List<Node> nodes = new ArrayList<>();
		List<Edge> edges = new ArrayList<>();
		int[] outputs = new int[steps.size()];
		for (int index = 0; index < steps.size(); index++) {
			GaugeCraftPlan.Step step = steps.get(index);
			Progress current = progress.get(index);
			Map<GaugeCraftPlan.Input, Integer> sentInputs = new IdentityHashMap<>();
			for (GaugeCraftPlan.Input input : step.inputs())
				sentInputs.put(input, input.amount() * current.dispatchedCrafts());
			List<Edge> inputs = new ArrayList<>();
			for (GaugeCraftFlow.Supply supply : flow.inputs(index)) {
				// Attribute dispatched ingredients in the same order as the saved
				// material allocation, including inputs split between output and stock.
				int available = sentInputs.getOrDefault(supply.input(), 0);
				int sent = Math.min(supply.count(), available);
				sentInputs.put(supply.input(), available - sent);
				if (supply.source() >= 0) {
					inputs.add(new Edge(outputs[supply.source()], -1, supply.count()));
				} else {
					inputs.add(new Edge(nodes.size(), -1, supply.count()));
					nodes.add(new Node(supply.input().stack().copyWithCount(1), supply.count(), sent, 0, State.STOCKED));
				}
			}
			int produced = step.crafts() * step.outputPerCraft();
			int count = index == steps.size() - 1 ? requested : produced;
			int output = nodes.size();
			outputs[index] = output;
			nodes.add(new Node(step.output().copyWithCount(1), count,
				Math.min(count, current.dispatchedCrafts() * step.outputPerCraft()),
				Math.min(count, current.completed()), current.state()));
			for (Edge input : inputs)
				edges.add(new Edge(input.from(), output, input.count()));
			if (nodes.size() > MAX_NODES || edges.size() > MAX_EDGES)
				return unavailable();
		}
		// Show the number actually needed by consumers, not unused recipe-rounding surplus.
		int[] demanded = new int[nodes.size()];
		for (Edge edge : edges)
			demanded[edge.from()] += edge.count();
		for (int i = 0; i < nodes.size() - 1; i++) {
			Node node = nodes.get(i);
			if (demanded[i] > 0)
				nodes.set(i, new Node(node.stack(), demanded[i], Math.min(demanded[i], node.sent()),
					Math.min(demanded[i], node.completed()), node.state()));
		}
		return new GaugeCraftTree(status, terminal, List.copyOf(nodes), List.copyOf(edges));
	}
}
