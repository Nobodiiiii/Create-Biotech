package com.nobodiiiii.createbiotech.mixin.client;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.nobodiiiii.createbiotech.client.render.SlimeBeltFunnelRenderHelper;
import com.nobodiiiii.createbiotech.client.render.SlimeBeltFunnelRenderHelper.SlimeBeltFunnelTransform;
import com.simibubi.create.content.logistics.FlapStuffs;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelVisual;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

@Mixin(FunnelVisual.class)
public abstract class FunnelVisualMixin {

	@Redirect(method = "<init>(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Lcom/simibubi/create/content/logistics/funnel/FunnelBlockEntity;F)V",
		at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/logistics/FlapStuffs;commonTransform(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;F)Lorg/joml/Matrix4f;"),
		remap = false)
	private Matrix4f createBiotech$redirectCommonTransform(BlockPos visualPosition, Direction side, float baseZOffset,
		VisualizationContext context, FunnelBlockEntity blockEntity, float partialTick) {
		SlimeBeltFunnelTransform transform =
			SlimeBeltFunnelRenderHelper.getTransform(blockEntity.getLevel(), blockEntity.getBlockPos());
		if (transform == null)
			return FlapStuffs.commonTransform(visualPosition, side, baseZOffset);
		return SlimeBeltFunnelRenderHelper.createTiltedCommonTransform(visualPosition, side, baseZOffset, transform);
	}
}
