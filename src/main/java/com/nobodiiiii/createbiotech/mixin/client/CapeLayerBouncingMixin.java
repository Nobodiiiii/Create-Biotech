package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nobodiiiii.createbiotech.content.bouncing.BouncingCrouch;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;

@Mixin(CapeLayer.class)
public abstract class CapeLayerBouncingMixin {
	@WrapOperation(
		method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/AbstractClientPlayer;isCrouching()Z"),
		require = 1,
		expect = 1)
	private boolean createBiotech$keepCapeInStandingPose(AbstractClientPlayer player,
		Operation<Boolean> original) {
		return BouncingCrouch.isActive(player) ? false : original.call(player);
	}
}
