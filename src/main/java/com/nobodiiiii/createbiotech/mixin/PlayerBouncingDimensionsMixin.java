package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nobodiiiii.createbiotech.content.bouncing.BouncingCrouch;
import com.nobodiiiii.createbiotech.registry.CBMobEffects;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

@Mixin(Player.class)
public abstract class PlayerBouncingDimensionsMixin {
	@ModifyReturnValue(
		method = "getDefaultDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;",
		at = @At("RETURN"))
	private EntityDimensions createBiotech$useOneBlockCrouchingDimensions(EntityDimensions original,
		Pose pose) {
		Player player = (Player) (Object) this;
		if (!player.isAddedToLevel() || pose != Pose.CROUCHING
			|| !player.hasEffect(CBMobEffects.BOUNCING))
			return original;

		// Return the unscaled base size. LivingEntity#getDimensions applies the player's
		// current entity scale after this virtual method returns.
		return Player.STANDING_DIMENSIONS.scale(1.0F, BouncingCrouch.HEIGHT_SCALE);
	}
}
