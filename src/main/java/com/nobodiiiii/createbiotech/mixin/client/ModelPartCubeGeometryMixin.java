package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalCapturedRenderPlan;

import net.minecraft.client.model.geom.ModelPart;

@Mixin(ModelPart.Cube.class)
public abstract class ModelPartCubeGeometryMixin {
	@Inject(
		method = "compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
		at = @At("HEAD"),
		require = 1,
		expect = 1)
	private void createBiotech$captureUnscaledPixelBounds(PoseStack.Pose pose, VertexConsumer consumer,
		int packedLight, int packedOverlay, int color, CallbackInfo ci) {
		SurgicalCapturedRenderPlan.observeModelCube((ModelPart.Cube) (Object) this, pose);
	}
}
