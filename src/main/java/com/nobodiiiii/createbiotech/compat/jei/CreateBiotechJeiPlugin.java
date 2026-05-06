package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class CreateBiotechJeiPlugin implements IModPlugin {

	@Override
	public ResourceLocation getPluginUid() {
		return CreateBiotech.asResource("jei_plugin");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new SlimeTransformationJeiCategory());
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		registration.addRecipes(SlimeTransformationJeiCategory.TYPE, List.of(
			SlimeTransformationJeiRecipe.beltToSlimeBelt(),
			SlimeTransformationJeiRecipe.beltToMagmaBelt()));
	}
}
