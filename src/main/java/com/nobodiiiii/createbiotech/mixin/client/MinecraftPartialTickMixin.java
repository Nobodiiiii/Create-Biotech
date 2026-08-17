package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityRenderTime;

import net.minecraft.client.Minecraft;

/**
 * Pins the global partial tick while a captured entity icon is being baked.
 *
 * <p>1.21 overrides {@code DeltaTracker.Timer#getGameTimeDeltaPartialTick}; the
 * 1.20.1 equivalents are the two accessors that expose {@code Timer#partialTick}
 * to renderers, so entity models that read the global timer instead of the
 * partial tick handed to them still bake at a fixed frame.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftPartialTickMixin {

	@ModifyReturnValue(method = "getFrameTime", at = @At("RETURN"))
	private float createBiotech$freezeCapturedEntityFrameTime(float original) {
		return CapturedEntityRenderTime.overridePartialTick(original);
	}

	@ModifyReturnValue(method = "getPartialTick", at = @At("RETURN"), remap = false)
	private float createBiotech$freezeCapturedEntityPartialTick(float original) {
		return CapturedEntityRenderTime.overridePartialTick(original);
	}
}
