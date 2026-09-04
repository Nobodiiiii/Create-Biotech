package com.nobodiiiii.createbiotech.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.experience.ExperienceFluidHelper;
import com.nobodiiiii.createbiotech.content.experience.FluidTankExperienceOrbRenderer;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankRenderer;

import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FluidTankRenderer.class)
public abstract class FluidTankRendererMixin {
	@WrapOperation(
		method = "renderSafe(Lcom/simibubi/create/content/fluids/tank/FluidTankBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
		at = @At(value = "INVOKE",
			target = "Lnet/createmod/catnip/render/FluidRenderHelper;renderFluidBox(Ljava/lang/Object;FFFFFFLnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;IZZ)V"),
		require = 1,
		expect = 1)
	private void createBiotech$renderExperienceAsOrbs(FluidRenderHelper fluidRenderer, Object fluidStackObject,
		float xMin, float yMin, float zMin, float xMax, float yMax, float zMax, MultiBufferSource buffer,
		PoseStack poseStack, int packedLight, boolean renderBottom, boolean invertGases, Operation<Void> original,
		@Local(argsOnly = true) FluidTankBlockEntity be, @Local(argsOnly = true) float partialTicks) {
		if (!CBConfigs.CLIENT.renderExperienceAsFluid.get()
			&& fluidStackObject instanceof FluidStack fluidStack
			&& ExperienceFluidHelper.isExperience(fluidStack)
			&& FluidTankExperienceOrbRenderer.render(be, fluidStack, partialTicks, poseStack, buffer, packedLight,
				xMin, yMin, zMin, xMax, yMax, zMax))
			return;

		// Create/Catnip owns the fallback path. Its failures must remain visible instead of being swallowed here.
		original.call(fluidRenderer, fluidStackObject, xMin, yMin, zMin, xMax, yMax, zMax, buffer, poseStack,
			packedLight, renderBottom, invertGases);
	}
}
