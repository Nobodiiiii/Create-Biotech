package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.simibubi.create.content.kinetics.belt.transport.BeltMovementHandler;

import net.minecraft.world.entity.Entity;

@Mixin(BeltMovementHandler.class)
public abstract class BeltMovementHandlerMixin {

	@Inject(method = "canBeTransported(Lnet/minecraft/world/entity/Entity;)Z",
		at = @At("HEAD"), cancellable = true, remap = false)
	private static void createBiotech$ignoreCapturedSmallSlimes(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (BasinEntityProcessing.isCapturedSmallSlime(entity))
			cir.setReturnValue(false);
	}
}
