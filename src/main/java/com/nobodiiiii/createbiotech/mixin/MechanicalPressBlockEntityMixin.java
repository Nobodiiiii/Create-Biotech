package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessingOperation;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessingRecipe;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;

import net.minecraft.world.item.crafting.Recipe;

@Mixin(MechanicalPressBlockEntity.class)
public abstract class MechanicalPressBlockEntityMixin {

	@Inject(method = "matchStaticFilters(Lnet/minecraft/world/item/crafting/Recipe;)Z",
		at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$matchBasinEntityPressing(Recipe<?> recipe, CallbackInfoReturnable<Boolean> cir) {
		if (recipe instanceof BasinEntityProcessingRecipe entityRecipe
			&& entityRecipe.getOperation() == BasinEntityProcessingOperation.PRESSING)
			cir.setReturnValue(true);
	}
}
