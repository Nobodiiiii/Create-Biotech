package com.nobodiiiii.createbiotech.content.wirelessterminal;

import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.phys.Vec3;

public class WirelessStockKeeperRequestMenu extends StockKeeperRequestMenu {

	public WirelessStockKeeperRequestMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
		this(CBMenuTypes.WIRELESS_STOCK_KEEPER_REQUEST.get(), id, inv, extraData);
	}

	public WirelessStockKeeperRequestMenu(MenuType<?> type, int id, Inventory inv, FriendlyByteBuf extraData) {
		super(type, id, inv, extraData);
	}

	public WirelessStockKeeperRequestMenu(MenuType<?> type, int id, Inventory inv, StockTickerBlockEntity contentHolder) {
		super(type, id, inv, contentHolder);
	}

	public static AbstractContainerMenu create(int containerId, Inventory playerInventory,
		StockTickerBlockEntity stockTickerBlockEntity) {
		return new WirelessStockKeeperRequestMenu(CBMenuTypes.WIRELESS_STOCK_KEEPER_REQUEST.get(), containerId,
			playerInventory, stockTickerBlockEntity);
	}

	@Override
	public boolean stillValid(Player player) {
		if (contentHolder == null || contentHolder.isRemoved())
			return false;
		if (player.level() != contentHolder.getLevel())
			return CBConfigs.SERVER.wireless.allowCrossDimensionTerminal.get();
		return player.position().closerThan(Vec3.atCenterOf(contentHolder.getBlockPos()), getAccessRange() + 0.5d);
	}

	public static int getAccessRange() {
		return CBConfigs.SERVER.wireless.terminalAccessRange.get();
	}
}
