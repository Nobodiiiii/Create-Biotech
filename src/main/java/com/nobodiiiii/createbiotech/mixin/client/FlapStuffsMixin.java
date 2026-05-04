package com.nobodiiiii.createbiotech.mixin.client;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.client.render.SlimeBeltFunnelRenderHelper;
import com.nobodiiiii.createbiotech.client.render.SlimeBeltFunnelRenderHelper.SlimeBeltFunnelTransform;
import com.simibubi.create.content.logistics.FlapStuffs;

import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

@Mixin(FlapStuffs.class)
public abstract class FlapStuffsMixin {

	@Inject(method = "renderFlaps(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/createmod/catnip/render/SuperByteBuffer;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/Direction;FFI)V",
		at = @At("HEAD"), remap = false)
	private static void createBiotech$applySlimeBeltTilt(PoseStack ms, VertexConsumer vb, SuperByteBuffer flapBuffer,
		Vec3 pivot, Direction funnelFacing, float flapness, float zOffset, int light, CallbackInfo ci) {
		SlimeBeltFunnelTransform transform = SlimeBeltFunnelRenderHelper.getCurrentTransform();
		if (transform == null)
			return;
		ms.pushPose();
		SlimeBeltFunnelRenderHelper.applyTilt(ms, transform);
	}

	@Inject(method = "renderFlaps(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/createmod/catnip/render/SuperByteBuffer;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/Direction;FFI)V",
		at = @At("RETURN"), remap = false)
	private static void createBiotech$restoreSlimeBeltTilt(PoseStack ms, VertexConsumer vb, SuperByteBuffer flapBuffer,
		Vec3 pivot, Direction funnelFacing, float flapness, float zOffset, int light, CallbackInfo ci) {
		if (SlimeBeltFunnelRenderHelper.getCurrentTransform() != null)
			ms.popPose();
	}

	@Inject(method = "commonTransform(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;F)Lorg/joml/Matrix4f;",
		at = @At("HEAD"), cancellable = true, remap = false)
	private static void createBiotech$tiltedCommonTransform(BlockPos visualPosition, Direction side, float baseZOffset,
		CallbackInfoReturnable<Matrix4f> cir) {
		SlimeBeltFunnelTransform transform = SlimeBeltFunnelRenderHelper.getCurrentTransform();
		if (transform == null)
			return;
		cir.setReturnValue(SlimeBeltFunnelRenderHelper.createTiltedCommonTransform(visualPosition, side, baseZOffset,
			transform));
	}
}
