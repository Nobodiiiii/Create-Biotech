package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.client.render.BeltSurfaceRenderScope;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelRenderer;

import net.minecraft.client.renderer.MultiBufferSource;

@Mixin(FunnelRenderer.class)
public abstract class FunnelRendererMixin {

	@Inject(method = "renderSafe(Lcom/simibubi/create/content/logistics/funnel/FunnelBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
		at = @At("HEAD"), remap = false)
	private void createBiotech$pushSurface(FunnelBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
		BeltSurfaceRenderScope.push(be.getLevel(), be.getBlockPos());
	}

	@Inject(method = "renderSafe(Lcom/simibubi/create/content/logistics/funnel/FunnelBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
		at = @At("RETURN"), remap = false)
	private void createBiotech$popSurface(FunnelBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
		BeltSurfaceRenderScope.pop();
	}
}
