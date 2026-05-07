package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class CreateBiotechJeiPlugin implements IModPlugin {
	private static final RecipeType<ItemApplicationRecipe> CREATE_ITEM_APPLICATION =
		new RecipeType<>(Create.asResource("item_application"), ItemApplicationRecipe.class);

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
		registration.addRecipes(CREATE_ITEM_APPLICATION, List.of(powerBeltConversion()));
	}

	private static ItemApplicationRecipe powerBeltConversion() {
		return new ProcessingRecipeBuilder<>(ManualApplicationRecipe::new,
			CreateBiotech.asResource("item_application/power_belt"))
				.require(AllItems.BELT_CONNECTOR.get())
				.require(AllItems.ANDESITE_ALLOY.get())
				.output(CBItems.POWER_BELT_CONNECTOR.get())
				.build();
	}
}
