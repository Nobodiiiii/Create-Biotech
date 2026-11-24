package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.content.biopackager.BioPackagerLogistics;
import com.nobodiiiii.createbiotech.content.biopackager.CreatureStockItem;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.WiFiEffectPacket;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderRequestPacket;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

@Mixin(PackageOrderRequestPacket.class)
public abstract class PackageOrderRequestPacketMixin {
	@Shadow @Final private PackageOrderWithCrafts order;
	@Shadow @Final private String address;
	@Shadow @Final private boolean encodeRequester;

	@Inject(method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;Lcom/simibubi/create/content/logistics/stockTicker/StockTickerBlockEntity;)V",
		at = @At("HEAD"), cancellable = true)
	private void createBiotech$handleCreatureOrder(ServerPlayer player, StockTickerBlockEntity ticker, CallbackInfo ci) {
		if (!CreatureStockItem.isCreatureOrder(order))
			return;

		ci.cancel();
		if (encodeRequester || !BioPackagerLogistics.mayRequestCreatures(player, ticker))
			return;
		if (!BioPackagerLogistics.placeOrder(ticker.behaviour.freqId, order, address)) {
			player.sendSystemMessage(Component.translatable("create_biotech.gui.stock_keeper.creature_order_unavailable"));
			return;
		}

		ticker.broadcastPackageRequest(RequestType.PLAYER, PackageOrderWithCrafts.empty(), null, address);
		AllSoundEvents.STOCK_TICKER_REQUEST.playOnServer(ticker.getLevel(), ticker.getBlockPos());
		AllAdvancements.STOCK_TICKER.awardTo(player);
		WiFiEffectPacket.send(ticker.getLevel(), ticker.getBlockPos());
	}
}
