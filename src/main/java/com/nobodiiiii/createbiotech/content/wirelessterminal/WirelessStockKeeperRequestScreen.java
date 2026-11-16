package com.nobodiiiii.createbiotech.content.wirelessterminal;

import com.nobodiiiii.createbiotech.content.endermanstockkeeper.EndermanStockKeeperRequestScreen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class WirelessStockKeeperRequestScreen extends EndermanStockKeeperRequestScreen {

	private int stockRefreshTicks;

	public WirelessStockKeeperRequestScreen(WirelessStockKeeperRequestMenu container, Inventory inv, Component title) {
		super(container, inv, title);
	}

	@Override
	protected void containerTick() {
		// Remote ticker snapshots are not ticked by the client world.
		if (++stockRefreshTicks >= 16) {
			getMenu().contentHolder.refreshClientStockSnapshot();
			stockRefreshTicks = 0;
		}
		super.containerTick();
	}
}
