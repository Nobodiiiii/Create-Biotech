package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.possession.client.PossessionClientRenderer;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererPossessionMixin {

	@WrapOperation(
		method = { "renderMapHand", "renderPlayerArm" },
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/player/PlayerRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V"))
	private void createBiotech$renderPossessedRightArm(PlayerRenderer renderer, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, Operation<Void> original) {
		if (!PossessionClientRenderer.tryRenderFirstPersonArm(player, HumanoidArm.RIGHT,
			poseStack, buffer, packedLight))
			original.call(renderer, poseStack, buffer, packedLight, player);
	}

	@WrapOperation(
		method = { "renderMapHand", "renderPlayerArm" },
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/player/PlayerRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V"))
	private void createBiotech$renderPossessedLeftArm(PlayerRenderer renderer, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, Operation<Void> original) {
		if (!PossessionClientRenderer.tryRenderFirstPersonArm(player, HumanoidArm.LEFT,
			poseStack, buffer, packedLight))
			original.call(renderer, poseStack, buffer, packedLight, player);
	}
}
