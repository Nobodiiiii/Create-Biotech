package com.nobodiiiii.createbiotech.compat.jei;

import java.util.Arrays;
import java.util.List;

import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonUpgrade;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonUpgradeRecipe;
import com.nobodiiiii.createbiotech.registry.CBItems;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.extensions.vanilla.smithing.ISmithingCategoryExtension;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;

public class SonicDogCannonUpgradeJeiExtension
	implements ISmithingCategoryExtension<SonicDogCannonUpgradeRecipe> {

	@Override
	public <T extends IIngredientAcceptor<T>> void setTemplate(SonicDogCannonUpgradeRecipe recipe,
		T ingredientAcceptor) {
		// This upgrade system intentionally does not consume a smithing template.
	}

	@Override
	public <T extends IIngredientAcceptor<T>> void setBase(SonicDogCannonUpgradeRecipe recipe,
		T ingredientAcceptor) {
		ingredientAcceptor.addItemStack(new ItemStack(CBItems.SONIC_DOG_CANNON.get()));
	}

	@Override
	public <T extends IIngredientAcceptor<T>> void setAddition(SonicDogCannonUpgradeRecipe recipe,
		T ingredientAcceptor) {
		if (recipe.upgrade() == SonicDogCannonUpgrade.DOG_COLLAR)
			ingredientAcceptor.addItemStacks(collarDyes());
		else
			ingredientAcceptor.addIngredients(recipe.addition());
	}

	@Override
	public <T extends IIngredientAcceptor<T>> void setOutput(SonicDogCannonUpgradeRecipe recipe,
		T ingredientAcceptor) {
		if (recipe.upgrade() == SonicDogCannonUpgrade.DOG_COLLAR) {
			ingredientAcceptor.addItemStacks(Arrays.stream(DyeColor.values())
				.map(SonicDogCannonUpgradeJeiExtension::collarOutput)
				.toList());
			return;
		}

		ItemStack output = new ItemStack(CBItems.SONIC_DOG_CANNON.get());
		recipe.upgrade().install(output);
		ingredientAcceptor.addItemStack(output);
	}

	@Override
	public void onDisplayedIngredientsUpdate(SonicDogCannonUpgradeRecipe recipe,
		IRecipeSlotDrawable templateSlot, IRecipeSlotDrawable baseSlot,
		IRecipeSlotDrawable additionSlot, IRecipeSlotDrawable outputSlot, IFocusGroup focuses) {
		if (recipe.upgrade() != SonicDogCannonUpgrade.DOG_COLLAR)
			return;

		DyeColor color;
		if (focuses.getFocuses(RecipeIngredientRole.OUTPUT).findAny().isPresent()) {
			color = outputSlot.getDisplayedItemStack()
				.map(SonicDogCannonUpgrade::getCollarColor)
				.orElse(DyeColor.RED);
			additionSlot.createDisplayOverrides()
				.addItemStack(new ItemStack(DyeItem.byColor(color)));
		} else {
			color = additionSlot.getDisplayedItemStack()
				.map(DyeColor::getColor)
				.orElse(DyeColor.RED);
		}

		outputSlot.createDisplayOverrides()
			.addItemStack(collarOutput(color));
	}

	private static ItemStack collarOutput(DyeColor color) {
		ItemStack output = new ItemStack(CBItems.SONIC_DOG_CANNON.get());
		SonicDogCannonUpgrade.setCollarColor(output, color);
		return output;
	}

	private static List<ItemStack> collarDyes() {
		return Arrays.stream(DyeColor.values())
			.map(DyeItem::byColor)
			.map(ItemStack::new)
			.toList();
	}
}
