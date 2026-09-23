package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Server-side, bounded simulation of the player's connected factory gauges. */
public final class GaugeCraftPlan {
	public static final int MAX_REQUEST = 65536;
	private static final int MAX_DEPTH = 64;
	private static final int MAX_NODES = 512;

	public record Input(FactoryPanelPosition source, UUID network, ItemStack stack, int amount) {}
	public record Step(FactoryPanelPosition gauge, UUID network, ItemStack output, int outputPerCraft,
		int crafts, String address, List<ItemStack> arrangement, List<Input> inputs) {}
	public record Line(int depth, ItemStack stack, int count, boolean missing, boolean stocked) {}

	private final List<Step> steps = new ArrayList<>();
	private final List<Line> lines = new ArrayList<>();
	private final Map<UUID, InventorySummary> simulated = new HashMap<>();
	private final Set<FactoryPanelPosition> path = new HashSet<>();
	private int missing;
	private String error = "";

	private GaugeCraftPlan() {}

	public static GaugeCraftPlan build(Level level, UUID network, ItemStack output, int count) {
		GaugeCraftPlan best = null;
		for (FactoryPanelBehaviour gauge : FactoryGaugeCatalog.getOutputGauges(network)) {
			if (!ItemStack.isSameItemSameComponents(gauge.getFilter(), output))
				continue;
			if (gauge.getWorld() != level)
				continue;
			GaugeCraftPlan candidate = new GaugeCraftPlan();
			candidate.expand(level, gauge, count, 0);
			if (best == null || candidate.rank() < best.rank()
				|| candidate.rank() == best.rank() && candidate.missing < best.missing
				|| candidate.rank() == best.rank() && candidate.missing == best.missing
					&& candidate.steps.size() < best.steps.size())
				best = candidate;
		}
		if (best != null)
			return best;
		GaugeCraftPlan unavailable = new GaugeCraftPlan();
		unavailable.error = "No loaded factory gauge produces this item";
		return unavailable;
	}

	private void expand(Level level, FactoryPanelBehaviour gauge, int count, int depth) {
		ItemStack output = gauge.getFilter();
		if (count <= 0 || count > MAX_REQUEST || depth > MAX_DEPTH || steps.size() >= MAX_NODES
			|| lines.size() >= MAX_NODES) {
			error = "Crafting plan exceeds the size limit";
			return;
		}
		if (output.isEmpty() || gauge.network == null) {
			error = "A gauge has no output or network";
			return;
		}
		int stock = take(gauge.network, output, count);
		if (stock > 0)
			lines.add(new Line(depth, output.copyWithCount(1), stock, false, true));
		int needed = count - stock;
		if (needed == 0)
			return;
		FactoryPanelPosition id = gauge.getPanelPosition();
		if (!path.add(id)) {
			error = "Factory gauge cycle at " + id;
			return;
		}
		try {
			if (gauge.targetedBy.isEmpty() || gauge.recipeOutput <= 0 || gauge.recipeAddress.isBlank()) {
				missing += needed;
				lines.add(new Line(depth, output.copyWithCount(1), needed, true, false));
				return;
			}
			int crafts = (needed + gauge.recipeOutput - 1) / gauge.recipeOutput;
			if ((long) crafts * gauge.recipeOutput > MAX_REQUEST) {
				error = "Crafting plan exceeds the size limit";
				return;
			}
			lines.add(new Line(depth, output.copyWithCount(1), crafts * gauge.recipeOutput, false, false));
			List<Input> inputs = new ArrayList<>();
			List<FactoryPanelConnection> connections = new ArrayList<>(gauge.targetedBy.values());
			connections.sort((a, b) -> a.from.toString().compareTo(b.from.toString()));
			for (FactoryPanelConnection connection : connections) {
				if (!level.getChunkSource().hasChunk(connection.from.pos().getX() >> 4,
					connection.from.pos().getZ() >> 4)) {
					error = "A connected factory gauge is in an unloaded chunk";
					return;
				}
				FactoryPanelBehaviour source = FactoryPanelBehaviour.at(level, connection.from);
				if (source == null || source.network == null || source.getFilter().isEmpty()
					|| connection.amount <= 0 || (long) connection.amount * crafts > MAX_REQUEST) {
					error = "A connected factory gauge is unavailable or invalid";
					return;
				}
				inputs.add(new Input(connection.from, source.network, source.getFilter().copyWithCount(1),
					connection.amount));
				expand(level, source, connection.amount * crafts, depth + 1);
				if (!error.isEmpty())
					return;
			}
			steps.add(new Step(id, gauge.network, output.copyWithCount(1), gauge.recipeOutput, crafts,
				gauge.recipeAddress, gauge.activeCraftingArrangement.stream().map(ItemStack::copy).toList(),
				List.copyOf(inputs)));
			add(gauge.network, output, crafts * gauge.recipeOutput - needed);
		} finally {
			path.remove(id);
		}
	}

	private int take(UUID network, ItemStack stack, int count) {
		InventorySummary summary = simulated.computeIfAbsent(network,
			key -> LogisticsManager.getSummaryOfNetwork(key, true).copy());
		int taken = Math.min(Math.max(0, summary.getCountOf(stack)), count);
		if (taken > 0)
			summary.add(stack, -taken);
		return taken;
	}

	private void add(UUID network, ItemStack stack, int count) {
		if (count > 0)
			simulated.computeIfAbsent(network, key -> LogisticsManager.getSummaryOfNetwork(key, true).copy())
				.add(stack, count);
	}

	public List<Step> steps() { return List.copyOf(steps); }
	public List<Line> lines() { return List.copyOf(lines); }
	public int missing() { return missing; }
	public String error() { return error; }
	public boolean ready() { return error.isEmpty() && missing == 0; }
	private int rank() { return ready() ? 0 : error.isEmpty() ? 1 : 2; }
}
