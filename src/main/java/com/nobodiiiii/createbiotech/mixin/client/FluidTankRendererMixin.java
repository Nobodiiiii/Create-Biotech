package com.nobodiiiii.createbiotech.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.nobodiiiii.createbiotech.content.experience.ExperienceFluidHelper;
import com.nobodiiiii.createbiotech.content.experience.FluidTankExperienceOrbRenderer;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankRenderer;

import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.fluids.FluidStack;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FluidTankRenderer.class, remap = false)
public abstract class FluidTankRendererMixin {
	@Unique
	private static final Logger createBiotech$LOGGER = LogUtils.getLogger();
	@Unique
	private static boolean createBiotech$warnedExperienceRenderFailure;
	@Unique
	private static boolean createBiotech$warnedFallbackRenderFailure;

	@WrapOperation(
		method = "renderSafe",
		at = @At(value = "INVOKE",
			target = "Lnet/createmod/catnip/render/FluidRenderHelper;renderFluidBox",
			remap = false),
		remap = false)
	private void createBiotech$renderExperienceAsOrbs(FluidRenderHelper fluidRenderer, Object fluidStackObject,
		float xMin, float yMin, float zMin, float xMax, float yMax, float zMax, MultiBufferSource buffer,
		PoseStack poseStack, int packedLight, boolean renderBottom, boolean invertGases, Operation<Void> original,
		@Local(argsOnly = true) FluidTankBlockEntity be, @Local(argsOnly = true) float partialTicks) {
		if (fluidStackObject instanceof FluidStack fluidStack && ExperienceFluidHelper.isExperience(fluidStack)) {
			try {
				if (FluidTankExperienceOrbRenderer.render(be, fluidStack, partialTicks, poseStack, buffer, packedLight,
					xMin, yMin, zMin, xMax, yMax, zMax))
					return;
			} catch (Throwable throwable) {
				createBiotech$throwIfFatal(throwable);
				createBiotech$warnExperienceRenderFailure(throwable);
			}
		}

		try {
			original.call(fluidRenderer, fluidStackObject, xMin, yMin, zMin, xMax, yMax, zMax, buffer, poseStack,
				packedLight, renderBottom, invertGases);
		} catch (Throwable throwable) {
			createBiotech$throwIfFatal(throwable);
			createBiotech$warnFallbackRenderFailure(throwable);
		}
	}

	@Unique
	private static void createBiotech$throwIfFatal(Throwable throwable) {
		if (throwable instanceof ThreadDeath threadDeath)
			throw threadDeath;
		if (throwable instanceof VirtualMachineError error)
			throw error;
	}

	@Unique
	private static void createBiotech$warnExperienceRenderFailure(Throwable throwable) {
		if (createBiotech$warnedExperienceRenderFailure)
			return;
		createBiotech$warnedExperienceRenderFailure = true;
		createBiotech$LOGGER.warn(
			"Failed to render experience orbs in a Create fluid tank; falling back to normal fluid rendering.",
			throwable);
	}

	@Unique
	private static void createBiotech$warnFallbackRenderFailure(Throwable throwable) {
		if (createBiotech$warnedFallbackRenderFailure)
			return;
		createBiotech$warnedFallbackRenderFailure = true;
		createBiotech$LOGGER.warn(
			"Failed to render the fallback fluid in a Create fluid tank; suppressing this frame to avoid a client crash.",
			throwable);
	}
}
