package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.client.BionicTraitDonorIndex;
import com.nobodiiiii.createbiotech.content.surgery.BionicDonorPreviewItem;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;

/** Reverse lookups use the server's live donor index, including display-only preview stacks. */
final class BionicTraitRecipeManagerPlugin implements ISimpleRecipeManagerPlugin<BionicTrait> {

	@Override
	public boolean isHandledInput(ITypedIngredient<?> input) {
		return input.getIngredient(VanillaTypes.ITEM_STACK)
			.map(BionicDonorPreviewItem::entityType)
			.isPresent();
	}

	@Override
	public boolean isHandledOutput(ITypedIngredient<?> output) {
		return isHandledInput(output);
	}

	@Override
	public List<BionicTrait> getRecipesForInput(ITypedIngredient<?> input) {
		return input.getIngredient(VanillaTypes.ITEM_STACK)
			.map(BionicDonorPreviewItem::entityType)
			.map(BionicTraitDonorIndex::traits)
			.orElseGet(List::of);
	}

	@Override
	public List<BionicTrait> getRecipesForOutput(ITypedIngredient<?> output) {
		return getRecipesForInput(output);
	}

	@Override
	public List<BionicTrait> getAllRecipes() {
		// Trait pages are already registered normally; this plugin only adds donor lookups.
		return List.of();
	}
}
