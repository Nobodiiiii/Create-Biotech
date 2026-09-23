package com.nobodiiiii.createbiotech.content.endermanstockkeeper;

import java.util.List;

import com.nobodiiiii.createbiotech.mixin.StockKeeperRequestMenuAccessor;
import com.nobodiiiii.createbiotech.registry.CBMenuTypes;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class EndermanStockKeeperRequestMenu extends StockKeeperRequestMenu {

	private BlockPos keeperPos;
	private List<ItemStack> gaugeOutputs;

	public EndermanStockKeeperRequestMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extraData) {
		this(CBMenuTypes.ENDERMAN_STOCK_KEEPER_REQUEST.get(), id, inventory, extraData);
	}

	public EndermanStockKeeperRequestMenu(MenuType<?> type, int id, Inventory inventory,
		RegistryFriendlyByteBuf extraData) {
		super(type, id, inventory, extraData);
	}

	public EndermanStockKeeperRequestMenu(int id, Inventory inventory, StockTickerBlockEntity stockTicker,
		BlockPos keeperPos, List<ItemStack> gaugeOutputs) {
		super(CBMenuTypes.ENDERMAN_STOCK_KEEPER_REQUEST.get(), id, inventory, stockTicker);
		this.keeperPos = keeperPos.immutable();
		this.gaugeOutputs = copyOutputs(gaugeOutputs);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	protected StockTickerBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
		StockKeeperRequestMenuAccessor accessor = (StockKeeperRequestMenuAccessor) this;
		accessor.createBiotech$setAdmin(extraData.readBoolean());
		accessor.createBiotech$setLocked(extraData.readBoolean());

		BlockPos stockTickerPos = extraData.readBlockPos();
		keeperPos = extraData.readBlockPos();
		gaugeOutputs = copyOutputs(ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(extraData));

		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && level.getBlockEntity(stockTickerPos) instanceof StockTickerBlockEntity stockTicker)
			return stockTicker;
		return null;
	}

	@Override
	public boolean stillValid(Player player) {
		return super.stillValid(player)
			&& keeperPos != null
			&& EndermanStockKeeperBlock.isKeeperFor(player.level(), keeperPos, contentHolder.getBlockPos());
	}

	public BlockPos getKeeperPos() {
		return keeperPos;
	}

	public List<ItemStack> getGaugeOutputs() {
		return gaugeOutputs == null ? List.of() : gaugeOutputs;
	}

	private static List<ItemStack> copyOutputs(List<ItemStack> outputs) {
		return outputs.stream()
			.filter(stack -> !stack.isEmpty())
			.map(stack -> stack.copyWithCount(1))
			.toList();
	}
}
