package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessingRecipe;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import com.simibubi.create.content.kinetics.mixer.CompactingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

@JeiPlugin
public class CreateBiotechJeiPlugin implements IModPlugin {
	private static final RecipeType<BasinRecipe> CREATE_PACKING =
		new RecipeType<>(Create.asResource("packing"), BasinRecipe.class);
	private static final RecipeType<AbstractCrushingRecipe> CREATE_CRUSHING =
		new RecipeType<>(Create.asResource("crushing"), AbstractCrushingRecipe.class);
	private static final RecipeType<ItemApplicationRecipe> CREATE_ITEM_APPLICATION =
		new RecipeType<>(Create.asResource("item_application"), ItemApplicationRecipe.class);

	@Override
	public ResourceLocation getPluginUid() {
		return CreateBiotech.asResource("jei_plugin");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new SlimeTransformationJeiCategory());
		registration.addRecipeCategories(new CreeperBlastChamberHighPressureJeiCategory());
		registration.addRecipeCategories(new SquidPrinterJeiCategory());
		registration.addRecipeCategories(new EvokerEnchantingChamberJeiCategory());
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		registration.addRecipes(SlimeTransformationJeiCategory.TYPE, List.of(
			SlimeTransformationJeiRecipe.beltToSlimeBelt(),
			SlimeTransformationJeiRecipe.beltToMagmaBelt()));
		registration.addRecipes(CreeperBlastChamberHighPressureJeiCategory.TYPE,
			CreeperBlastChamberHighPressureJeiRecipes.create());
		registration.addRecipes(SquidPrinterJeiCategory.TYPE, SquidPrinterJeiRecipes.create());
		registration.addRecipes(EvokerEnchantingChamberJeiCategory.TYPE, EvokerEnchantingChamberJeiRecipes.create());
		registration.addRecipes(CREATE_PACKING, basinEntityProcessingPackingRecipes());
		registration.addRecipes(CREATE_ITEM_APPLICATION, List.of(powerBeltConversion()));
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addRecipeCatalyst(CBBlocks.CREEPER_BLAST_CHAMBER.get(), CREATE_CRUSHING);
		registration.addRecipeCatalyst(CBBlocks.CREEPER_BLAST_CHAMBER.get(),
			CreeperBlastChamberHighPressureJeiCategory.TYPE);
		registration.addRecipeCatalyst(new ItemStack(CBBlocks.SQUID_PRINTER.get()), SquidPrinterJeiCategory.TYPE);
		registration.addRecipeCatalyst(new ItemStack(CBBlocks.EVOKER_ENCHANTING_CHAMBER.get()),
			EvokerEnchantingChamberJeiCategory.TYPE);
	}

	private static List<BasinRecipe> basinEntityProcessingPackingRecipes() {
		ClientPacketListener connection = Minecraft.getInstance()
			.getConnection();
		if (connection == null)
			return List.of();

		return connection.getRecipeManager()
			.getAllRecipesFor(CBRecipeTypes.BASIN_ENTITY_PROCESSING_TYPE.get())
			.stream()
			.map(CreateBiotechJeiPlugin::asPackingRecipe)
			.toList();
	}

	private static BasinRecipe asPackingRecipe(BasinEntityProcessingRecipe recipe) {
		NonNullList<Ingredient> ingredients = NonNullList.create();
		ingredients.addAll(recipe.getIngredients());
		ingredients.add(Ingredient.of(CBItems.CAPTURED_SMALL_SLIME.get()));

		ProcessingRecipeBuilder<CompactingRecipe> builder =
			new ProcessingRecipeBuilder<>(CompactingRecipe::new, recipe.getId())
				.withItemIngredients(ingredients);
		for (ItemStack result : recipe.getRollableResults())
			builder.output(result.copy());
		return builder.build();
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
