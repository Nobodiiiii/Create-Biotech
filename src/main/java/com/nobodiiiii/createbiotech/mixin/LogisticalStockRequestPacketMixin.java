package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerLogistics;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.LogisticalStockRequestPacket;
import com.simibubi.create.content.logistics.stockTicker.StockCheckingBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;

import net.minecraft.server.level.ServerPlayer;

@Mixin(LogisticalStockRequestPacket.class)
public abstract class LogisticalStockRequestPacketMixin {
	@ModifyExpressionValue(method = "applySettings", at = @At(value = "INVOKE",
		target = "Lcom/simibubi/create/content/logistics/stockTicker/StockCheckingBlockEntity;getRecentSummary()Lcom/simibubi/create/content/logistics/packager/InventorySummary;"))
	private InventorySummary createBiotech$appendCreatureStock(InventorySummary summary, ServerPlayer player,
		StockCheckingBlockEntity blockEntity) {
		if (blockEntity instanceof StockTickerBlockEntity ticker
			&& BioPackagerLogistics.mayRequestCreatures(player, ticker))
			return BioPackagerLogistics.addCreatureStock(summary, ticker.behaviour.freqId);
		return summary;
	}
}
