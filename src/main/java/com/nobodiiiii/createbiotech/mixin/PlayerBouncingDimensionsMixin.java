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

		// Return a half-height standing base size rather than an absolute 0.9-block size.
		// LivingEntity#getDimensions applies the player's current scale afterwards, so
		// other mods' player scaling also scales the height, eye height and attachments.
		return Player.STANDING_DIMENSIONS.scale(1.0F, BouncingCrouch.HEIGHT_SCALE);
	}
}
