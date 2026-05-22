package com.nobodiiiii.createbiotech.mixin.client;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.nobodiiiii.createbiotech.client.render.BeltSurfaceRenderScope;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurface;
import com.nobodiiiii.createbiotech.content.beltsurface.BeltSurfaceResolver;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelVisual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

@Mixin(FunnelVisual.class)
public abstract class FunnelVisualMixin {

	@WrapOperation(method = "<init>(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Lcom/simibubi/create/content/logistics/funnel/FunnelBlockEntity;F)V",
		at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/logistics/FlapStuffs;commonTransform(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;F)Lorg/joml/Matrix4f;"),
		remap = false)
	private Matrix4f createBiotech$wrapCommonTransform(BlockPos visualPosition, Direction side, float baseZOffset,
		Operation<Matrix4f> original, @Local FunnelBlockEntity blockEntity) {
		BeltSurface surface = BeltSurfaceResolver.resolve(blockEntity.getLevel(), blockEntity.getBlockPos());
		if (surface == null)
			return original.call(visualPosition, side, baseZOffset);
		return BeltSurfaceRenderScope.tiltedCommonTransform(visualPosition, side, baseZOffset, surface);
	}
}
