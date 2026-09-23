package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.endermanstockkeeper.EndermanStockKeeperBlock;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

@Mixin(StockTickerBlockEntity.class)
public abstract class StockTickerBlockEntityMixin {

	@Inject(method = "isKeeperPresent", at = @At("RETURN"), cancellable = true)
	private void createBiotech$recognizeEndermanStockKeeper(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue())
			return;

		StockTickerBlockEntity stockTicker = (StockTickerBlockEntity) (Object) this;
		for (int yOffset = 0; yOffset <= 1; yOffset++) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos keeperPos = stockTicker.getBlockPos().below(yOffset).relative(direction);
				if (EndermanStockKeeperBlock.isKeeperFor(stockTicker.getLevel(), keeperPos,
					stockTicker.getBlockPos())) {
					cir.setReturnValue(true);
					return;
				}
			}
		}
	}
}
