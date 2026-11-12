package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;
import java.util.Optional;

import com.nobodiiiii.createbiotech.client.BionicTraitDonorIndex;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxHelper;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxItem;
import com.nobodiiiii.createbiotech.content.surgery.BionicDonorPreviewItem;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/** Previews, spawn eggs and filled boxes share the server's live species-to-trait index. */
final class BionicTraitRecipeManagerPlugin implements ISimpleRecipeManagerPlugin<BionicTrait> {

	@Override
	public boolean isHandledInput(ITypedIngredient<?> input) {
		return donorType(input).isPresent();
	}

	@Override
	public boolean isHandledOutput(ITypedIngredient<?> output) {
		return isHandledInput(output);
	}

	@Override
	public List<BionicTrait> getRecipesForInput(ITypedIngredient<?> input) {
		BionicTraitDonorIndex.requestIfNeeded();
		return donorType(input)
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

	private static Optional<EntityType<?>> donorType(ITypedIngredient<?> ingredient) {
		return ingredient.getIngredient(VanillaTypes.ITEM_STACK).map(BionicTraitRecipeManagerPlugin::donorType);
	}

	private static EntityType<?> donorType(ItemStack stack) {
		if (stack.getItem() instanceof SpawnEggItem egg)
			return egg.getType(stack);
		if (CapturedEntityBoxItem.isBox(stack))
			return CapturedEntityBoxHelper.getCapturedEntityType(stack);
		return BionicDonorPreviewItem.entityType(stack);
	}
}
