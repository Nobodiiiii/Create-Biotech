package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessing;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessingRecipe;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;

import net.minecraft.world.item.crafting.Recipe;

@Mixin(BasinRecipe.class)
public abstract class BasinRecipeMixin {

	@Inject(method = "match(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;)Z",
		at = @At("HEAD"), cancellable = true, remap = false)
	private static void createBiotech$matchEntityProcessing(BasinBlockEntity basin, Recipe<?> recipe,
		CallbackInfoReturnable<Boolean> cir) {
		if (!(recipe instanceof BasinEntityProcessingRecipe entityRecipe))
			return;

		FilteringBehaviour filter = basin.getFilter();
		if (filter == null) {
			cir.setReturnValue(false);
			return;
		}
		if (!filter.test(recipe.getResultItem(basin.getLevel()
			.registryAccess()))) {
			cir.setReturnValue(false);
			return;
		}

		cir.setReturnValue(BasinEntityProcessing.apply(basin, entityRecipe, true));
	}

	@Inject(method = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;)Z",
		at = @At("HEAD"), cancellable = true, remap = false)
	private static void createBiotech$applyEntityProcessing(BasinBlockEntity basin, Recipe<?> recipe,
		CallbackInfoReturnable<Boolean> cir) {
		if (recipe instanceof BasinEntityProcessingRecipe entityRecipe)
			cir.setReturnValue(BasinEntityProcessing.apply(basin, entityRecipe, false));
	}

	@Inject(method = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z",
		at = @At("HEAD"), cancellable = true, remap = false)
	private static void createBiotech$applyEntityProcessingInternal(BasinBlockEntity basin, Recipe<?> recipe, boolean test,
		CallbackInfoReturnable<Boolean> cir) {
		if (recipe instanceof BasinEntityProcessingRecipe entityRecipe)
			cir.setReturnValue(BasinEntityProcessing.apply(basin, entityRecipe, test));
	}
}
