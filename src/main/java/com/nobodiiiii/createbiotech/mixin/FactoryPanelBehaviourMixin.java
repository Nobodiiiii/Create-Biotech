package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.endermanstockkeeper.FactoryGaugeCatalog;
import com.nobodiiiii.createbiotech.content.endermanstockkeeper.GaugeCraftJobs;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import net.minecraft.server.level.ServerLevel;

@Mixin(FactoryPanelBehaviour.class)
public abstract class FactoryPanelBehaviourMixin {

	@Inject(method = "tick", at = @At("TAIL"))
	private void createBiotech$indexCraftingGauge(CallbackInfo ci) {
		FactoryGaugeCatalog.keepAlive((FactoryPanelBehaviour) (Object) this);
	}

	@Inject(method = "getPromised", at = @At("RETURN"), cancellable = true)
	private void createBiotech$includeTreeCraftingHolds(CallbackInfoReturnable<Integer> cir) {
		FactoryPanelBehaviour gauge = (FactoryPanelBehaviour) (Object) this;
		if (!(gauge.getWorld() instanceof ServerLevel level) || gauge.network == null
			|| gauge.getFilter().isEmpty() || gauge.targetedBy.isEmpty())
			return;
		int held = GaugeCraftJobs.get(level.getServer()).heldAmount(gauge.network, gauge.getFilter());
		if (held > 0)
			cir.setReturnValue((int) Math.min(Integer.MAX_VALUE, (long) cir.getReturnValue() + held));
	}
}
