package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.world.item.ItemStack;

/** A read-only material-flow snapshot of the saved execution plan, independent of loaded gauges. */
public record GaugeCraftTree(String status, boolean terminal, List<Node> nodes, List<Edge> edges) {

	public static final int MAX_NODES = 2048;
	public static final int MAX_EDGES = 4096;

	public enum State { WAITING, CRAFTING, COMPLETE, STOCKED, PAUSED }
	public record Node(ItemStack stack, int count, int completed, State state) {}
	public record Edge(int from, int to, int count) {}

	public static GaugeCraftTree unavailable() {
		return new GaugeCraftTree("unavailable", true, List.of(), List.of());
	}

	public static GaugeCraftTree snapshot(List<GaugeCraftPlan.Step> steps, int requested, int current,
		int remaining, int received, int inFlight, String status, boolean terminal) {
		List<Node> nodes = new ArrayList<>();
		List<Edge> edges = new ArrayList<>();
		List<Supply> supplies = new ArrayList<>();
		for (int index = 0; index < steps.size(); index++) {
			GaugeCraftPlan.Step step = steps.get(index);
			List<Edge> inputs = new ArrayList<>();
			for (GaugeCraftPlan.Input input : step.inputs()) {
				int needed = input.amount() * step.crafts();
				// Earlier batches can supply several later branches, including their surplus.
				// Match the same network + components used by the planner's simulated inventory.
				for (Supply supply : supplies) {
					if (needed == 0)
						break;
					if (supply.remaining == 0 || !supply.network.equals(input.network())
						|| !ItemStack.isSameItemSameComponents(nodes.get(supply.node).stack(), input.stack()))
						continue;
					int taken = Math.min(needed, supply.remaining);
					supply.remaining -= taken;
					needed -= taken;
					inputs.add(new Edge(supply.node, -1, taken));
				}
				if (needed > 0) {
					inputs.add(new Edge(nodes.size(), -1, needed));
					nodes.add(new Node(input.stack().copyWithCount(1), needed, needed, State.STOCKED));
				}
			}
			int produced = step.crafts() * step.outputPerCraft();
			int count = index == steps.size() - 1 ? requested : produced;
			int completed = index < current ? count : index == current
				? Math.min(count, (step.crafts() - remaining) * step.outputPerCraft() + received) : 0;
			State state = index < current ? State.COMPLETE : State.WAITING;
			if (index == current) {
				if (status.startsWith("paused_") || terminal)
					state = State.PAUSED;
				else if (inFlight > 0)
					state = State.CRAFTING;
			}
			int output = nodes.size();
			nodes.add(new Node(step.output().copyWithCount(1), count, completed, state));
			for (Edge input : inputs)
				edges.add(new Edge(input.from(), output, input.count()));
			supplies.add(new Supply(output, step.network(), produced));
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
				nodes.set(i, new Node(node.stack(), demanded[i], Math.min(demanded[i], node.completed()), node.state()));
		}
		return new GaugeCraftTree(status, terminal, List.copyOf(nodes), List.copyOf(edges));
	}

	private static final class Supply {
		final int node;
		final UUID network;
		int remaining;

		Supply(int node, UUID network, int remaining) {
			this.node = node;
			this.network = network;
			this.remaining = remaining;
		}
	}
}
