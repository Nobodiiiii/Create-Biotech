package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.content.endermanstockkeeper.FactoryGaugeCatalog;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;

@Mixin(FactoryPanelBehaviour.class)
public abstract class FactoryPanelBehaviourMixin {

	@Inject(method = "tick", at = @At("TAIL"))
	private void createBiotech$indexCraftingGauge(CallbackInfo ci) {
		FactoryGaugeCatalog.keepAlive((FactoryPanelBehaviour) (Object) this);
	}
}
