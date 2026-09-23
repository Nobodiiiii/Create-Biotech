package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.List;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class EndermanStockKeeperRequestScreen extends StockKeeperRequestScreen {

	private List<List<BigItemStack>> mergedSnapshot;

	public EndermanStockKeeperRequestScreen(EndermanStockKeeperRequestMenu menu, Inventory inventory,
		Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected void containerTick() {
		super.containerTick();

		List<List<BigItemStack>> snapshot = getMenu().contentHolder.getClientStockSnapshot();
		if (snapshot == null || snapshot == mergedSnapshot || snapshot.isEmpty())
			return;

		EndermanStockKeeperRequestMenu endermanMenu = (EndermanStockKeeperRequestMenu) getMenu();
		List<BigItemStack> unsorted = snapshot.get(snapshot.size() - 1);
		for (ItemStack output : endermanMenu.getGaugeOutputs()) {
			boolean alreadyVisible = snapshot.stream()
				.flatMap(List::stream)
				.anyMatch(entry -> ItemStack.isSameItemSameComponents(entry.stack, output));
			if (!alreadyVisible)
				unsorted.add(new GaugeOutputBigItemStack(output));
		}

		mergedSnapshot = snapshot;
		refreshSearchNextTick = true;
		moveToTopNextTick = false;
	}
}
