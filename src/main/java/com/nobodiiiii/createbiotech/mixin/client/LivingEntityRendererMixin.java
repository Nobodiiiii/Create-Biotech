package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.client.BioPackagerReleaseAnimationHandler;
import com.nobodiiiii.createbiotech.content.buttercat.ButterRotation;
import com.nobodiiiii.createbiotech.foundation.render.EntityGeometry;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@WrapMethod(
		method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
	private void createBiotech$applyBioPackagerReleaseAnimation(LivingEntity entity, float entityYaw,
		float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, Operation<Void> original) {
		float progress = BioPackagerReleaseAnimationHandler.getProgress(entity, partialTick);
		if (progress < 0) {
			original.call(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
			return;
		}

		float scale = Mth.lerp(progress, .35f, 1f);
		poseStack.pushPose();
		poseStack.translate(0, Mth.lerp(progress, -1f, 0f), 0);
		poseStack.scale(scale, scale, scale);
		try {
			original.call(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
		} finally {
			poseStack.popPose();
		}
	}

	@Inject(
		method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
		at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V",
			ordinal = 0,
			shift = At.Shift.AFTER))
	private void createBiotech$applyButterRotation(LivingEntity entity, float entityYaw, float partialTick,
		PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		float rotation = ButterRotation.getVisualRotationDegrees(entity, partialTick);
		if (rotation != 0.0F)
			poseStack.mulPose(Axis.YP.rotationDegrees(-rotation));
	}

	@WrapOperation(
		method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
	private void createBiotech$bindIndependentLayerModel(RenderLayer<?, ?> layer, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, Entity entity, float limbSwing, float limbSwingAmount,
		float partialTick, float ageInTicks, float netHeadYaw, float headPitch, Operation<Void> original) {
		if (EntityGeometry.isBaseModelMeasurement())
			return;
		original.call(layer, poseStack, buffer, packedLight, entity, limbSwing, limbSwingAmount,
			partialTick, ageInTicks, netHeadYaw, headPitch);
	}
}
