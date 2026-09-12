package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nobodiiiii.createbiotech.content.bouncing.BouncingCrouch;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

@Mixin(PlayerModel.class)
public abstract class PlayerModelBouncingMixin {
	@WrapOperation(
		method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;isCrouching()Z"),
		require = 2,
		expect = 2)
	private boolean createBiotech$keepCloakInStandingPose(LivingEntity entity,
		Operation<Boolean> original) {
		if (entity instanceof Player player && BouncingCrouch.isActive(player))
			return false;
		return original.call(entity);
	}
}
