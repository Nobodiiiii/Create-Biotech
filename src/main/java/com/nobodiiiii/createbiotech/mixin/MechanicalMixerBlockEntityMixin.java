package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessingOperation;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessingRecipe;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;

import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;

@Mixin(MechanicalMixerBlockEntity.class)
public abstract class MechanicalMixerBlockEntityMixin {

	@Inject(method = "matchStaticFilters(Lnet/minecraft/world/item/crafting/Recipe;)Z",
		at = @At("HEAD"), cancellable = true, remap = false)
	private <C extends Container> void createBiotech$matchBasinEntityMixing(Recipe<C> recipe,
		CallbackInfoReturnable<Boolean> cir) {
		if (recipe instanceof BasinEntityProcessingRecipe entityRecipe
			&& entityRecipe.getOperation() == BasinEntityProcessingOperation.MIXING)
			cir.setReturnValue(true);
	}
}
