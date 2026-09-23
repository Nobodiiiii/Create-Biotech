package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.Cache;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.utility.TickBasedCache;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Runtime index of loaded factory gauges. Create indexes stock links by network,
 * but it does not expose a reverse lookup from a network to its factory gauges.
 */
public final class FactoryGaugeCatalog {

	private static final Cache<UUID, Cache<GaugeKey, WeakReference<FactoryPanelBehaviour>>> GAUGES =
		new TickBasedCache<>(20, true);

	private FactoryGaugeCatalog() {}

	public static void keepAlive(FactoryPanelBehaviour behaviour) {
		if (!(behaviour.getWorld() instanceof ServerLevel level) || !isCraftingOutput(behaviour))
			return;

		try {
			Cache<GaugeKey, WeakReference<FactoryPanelBehaviour>> networkGauges =
				GAUGES.get(behaviour.network, () -> new TickBasedCache<>(400, false));
			GaugeKey key = new GaugeKey(GlobalPos.of(level.dimension(), behaviour.getPos()), behaviour.getPanelPosition());
			WeakReference<FactoryPanelBehaviour> reference =
				networkGauges.get(key, () -> new WeakReference<>(behaviour));
			networkGauges.put(key, reference.get() == behaviour ? reference : new WeakReference<>(behaviour));
		} catch (ExecutionException exception) {
			throw new IllegalStateException("Unable to index a factory gauge", exception);
		}
	}

	public static List<ItemStack> getCraftableOutputs(UUID network) {
		Cache<GaugeKey, WeakReference<FactoryPanelBehaviour>> networkGauges = GAUGES.getIfPresent(network);
		if (networkGauges == null)
			return Collections.emptyList();

		List<ItemStack> outputs = new ArrayList<>();
		for (WeakReference<FactoryPanelBehaviour> reference : networkGauges.asMap().values()) {
			FactoryPanelBehaviour behaviour = reference.get();
			if (!isCraftingOutput(behaviour) || !network.equals(behaviour.network))
				continue;

			ItemStack output = behaviour.getFilter();
			boolean alreadyPresent = outputs.stream()
				.anyMatch(existing -> ItemStack.isSameItemSameComponents(existing, output));
			if (!alreadyPresent)
				outputs.add(output.copyWithCount(1));
		}
		return List.copyOf(outputs);
	}

	/** Loaded, live output gauges on a network. Planning always re-reads their current configuration. */
	public static List<FactoryPanelBehaviour> getOutputGauges(UUID network) {
		Cache<GaugeKey, WeakReference<FactoryPanelBehaviour>> networkGauges = GAUGES.getIfPresent(network);
		if (networkGauges == null)
			return List.of();
		List<FactoryPanelBehaviour> result = new ArrayList<>();
		for (WeakReference<FactoryPanelBehaviour> reference : networkGauges.asMap().values()) {
			FactoryPanelBehaviour behaviour = reference.get();
			if (isCraftingOutput(behaviour) && network.equals(behaviour.network))
				result.add(behaviour);
		}
		result.sort((a, b) -> a.getPanelPosition().toString().compareTo(b.getPanelPosition().toString()));
		return result;
	}

	private static boolean isCraftingOutput(FactoryPanelBehaviour behaviour) {
		return behaviour != null
			&& behaviour.isActive()
			&& behaviour.blockEntity != null
			&& !behaviour.blockEntity.isRemoved()
			&& !behaviour.blockEntity.isChunkUnloaded()
			&& !behaviour.getFilter().isEmpty()
			&& !behaviour.targetedBy.isEmpty();
	}

	private record GaugeKey(GlobalPos blockPos, FactoryPanelPosition panel) {}
}
