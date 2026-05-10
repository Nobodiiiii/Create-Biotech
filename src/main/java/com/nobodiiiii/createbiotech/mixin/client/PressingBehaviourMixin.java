package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

@Mixin(PressingBehaviour.class)
public abstract class PressingBehaviourMixin {

	@Inject(method = "getRenderedHeadOffset(F)F", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$useSharedChamberPressPhase(float partialTicks, CallbackInfoReturnable<Float> cir) {
		BlockEntityBehaviour behaviour = (BlockEntityBehaviour) (Object) this;
		if (!(behaviour.blockEntity instanceof MechanicalPressBlockEntity press))
			return;
		cir.setReturnValue(CreeperBlastChamberBlockEntity.getSynchronizedPressHeadProgress(press, partialTicks));
	}
}
