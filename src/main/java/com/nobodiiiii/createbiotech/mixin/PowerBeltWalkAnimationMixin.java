package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.nobodiiiii.createbiotech.content.powerbelt.PowerBeltWalkAnimation;

import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntity.class)
public abstract class PowerBeltWalkAnimationMixin {

	@ModifyArg(method = "calculateEntityAnimation(Z)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;updateWalkAnimation(F)V"))
	private float createBiotech$includePowerBeltSurfaceMovement(float movementDistance) {
		return PowerBeltWalkAnimation.consumeAdjustedMovement((LivingEntity) (Object) this, movementDistance);
	}
}
