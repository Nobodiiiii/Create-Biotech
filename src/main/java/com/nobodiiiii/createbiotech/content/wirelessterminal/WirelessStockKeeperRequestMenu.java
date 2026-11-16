package com.nobodiiiii.createbiotech.content.wirelessterminal;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.endermanstockkeeper.EndermanStockKeeperBlock;
import com.nobodiiiii.createbiotech.content.endermanstockkeeper.EndermanStockKeeperRequestMenu;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class WirelessStockKeeperRequestMenu extends EndermanStockKeeperRequestMenu {

	public WirelessStockKeeperRequestMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
		this(CBMenuTypes.WIRELESS_STOCK_KEEPER_REQUEST.get(), id, inv, extraData);
	}

	public WirelessStockKeeperRequestMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
		super(type, id, inv, extraData);
	}

	public WirelessStockKeeperRequestMenu(MenuType<?> type, int id, Inventory inv, StockTickerBlockEntity contentHolder,
		@Nullable BlockPos keeperPos, List<ItemStack> gaugeOutputs) {
		super(type, id, inv, contentHolder, keeperPos, gaugeOutputs);
	}

	public static AbstractContainerMenu create(int containerId, Inventory playerInventory,
		StockTickerBlockEntity stockTickerBlockEntity, @Nullable BlockPos keeperPos, List<ItemStack> gaugeOutputs) {
		return new WirelessStockKeeperRequestMenu(CBMenuTypes.WIRELESS_STOCK_KEEPER_REQUEST.get(), containerId,
			playerInventory, stockTickerBlockEntity, keeperPos, gaugeOutputs);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	protected StockTickerBlockEntity createStockTickerOnClient(BlockPos targetPos, RegistryFriendlyByteBuf extraData) {
		CompoundTag updateTag = extraData.readNbt();
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null)
			return null;

		// The target may be in another dimension at the same coordinates as a local ticker.
		StockTickerBlockEntity stockTicker = new StockTickerBlockEntity(AllBlockEntityTypes.STOCK_TICKER.get(), targetPos,
			AllBlocks.STOCK_TICKER.getDefaultState());
		stockTicker.setLevel(level);

		if (updateTag != null)
			stockTicker.handleUpdateTag(updateTag, level.registryAccess());
		return stockTicker;
	}

	@Override
	public boolean stillValid(Player player) {
		if (contentHolder == null || contentHolder.isRemoved())
			return false;
		if (player.level().isClientSide)
			return true;
		Level targetLevel = contentHolder.getLevel();
		return targetLevel != null && contentHolder.behaviour.mayInteract(player)
			&& (getKeeperPos() == null ? contentHolder.isKeeperPresent()
				: EndermanStockKeeperBlock.isKeeperFor(targetLevel, getKeeperPos(), contentHolder.getBlockPos()));
	}
}
