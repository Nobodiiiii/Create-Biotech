package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nobodiiiii.createbiotech.content.bouncing.BouncingCrouch;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererBouncingMixin {
	@Inject(
		method = "setModelProperties(Lnet/minecraft/client/player/AbstractClientPlayer;)V",
		at = @At("TAIL"))
	private void createBiotech$keepStandingPoseWhenBouncingCrouched(AbstractClientPlayer player,
		CallbackInfo ci) {
		if (BouncingCrouch.isActive(player))
			((PlayerRenderer) (Object) this).getModel().crouching = false;
	}

	@ModifyReturnValue(
		method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;",
		at = @At("RETURN"))
	private Vec3 createBiotech$keepBouncingFeetGrounded(Vec3 original, AbstractClientPlayer player,
		float partialTick) {
		if (!BouncingCrouch.isActive(player))
			return original;

		// PlayerRenderer normally lowers every crouching model by 2/16 of its scale. The jelly
		// deformation is already anchored at the feet, so cancel only that crouch-only offset.
		return original.add(0.0D, player.getScale() * 2.0F / 16.0F, 0.0D);
	}
}
