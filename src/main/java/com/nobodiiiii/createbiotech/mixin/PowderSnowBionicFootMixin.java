package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.PowderSnowBlock;

/** Per-entity powder-snow support cannot be represented by a shared EntityType tag. */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBionicFootMixin {
	@Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
	private static void createBiotech$walkOnPowderSnow(Entity entity,
		CallbackInfoReturnable<Boolean> callback) {
		if (entity instanceof SlimeBionicEntity bionic
			&& bionic.getBionicTraits().has(BionicTrait.POWDER_SNOW_WALK))
			callback.setReturnValue(true);
	}
}
