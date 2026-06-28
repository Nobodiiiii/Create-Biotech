package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberHighPressureRecipe;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessingOperation;
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
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
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
import net.minecraftforge.fluids.FluidStack;

@JeiPlugin
public class CreateBiotechJeiPlugin implements IModPlugin {
	private static final RecipeType<BasinRecipe> CREATE_MIXING =
		new RecipeType<>(Create.asResource("mixing"), BasinRecipe.class);
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
		registration.addRecipeCategories(new ExperiencePumpJeiCategory());
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		registration.addRecipes(SlimeTransformationJeiCategory.TYPE, List.of(
			SlimeTransformationJeiRecipe.beltToSlimeBelt(),
			SlimeTransformationJeiRecipe.beltToMagmaBelt()));
		registration.addRecipes(CreeperBlastChamberHighPressureJeiCategory.TYPE,
			creeperBlastChamberHighPressureRecipes());
		registration.addRecipes(SquidPrinterJeiCategory.TYPE, SquidPrinterJeiRecipes.create());
		registration.addRecipes(EvokerEnchantingChamberJeiCategory.TYPE, EvokerEnchantingChamberJeiRecipes.create());
		registration.addRecipes(ExperiencePumpJeiCategory.TYPE, ExperienceJeiRecipes.pump());
		registration.addRecipes(CREATE_MIXING, basinEntityProcessingRecipes(BasinEntityProcessingOperation.MIXING));
		registration.addRecipes(CREATE_PACKING, basinEntityProcessingRecipes(BasinEntityProcessingOperation.PRESSING));
		registration.addRecipes(CREATE_ITEM_APPLICATION, itemApplicationRecipes());
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addRecipeCatalyst(CBBlocks.CREEPER_BLAST_CHAMBER.get(), CREATE_CRUSHING);
		registration.addRecipeCatalyst(CBBlocks.CREEPER_BLAST_CHAMBER.get(),
			CreeperBlastChamberHighPressureJeiCategory.TYPE);
		registration.addRecipeCatalyst(new ItemStack(CBBlocks.SQUID_PRINTER.get()), SquidPrinterJeiCategory.TYPE);
		registration.addRecipeCatalyst(new ItemStack(CBBlocks.EVOKER_ENCHANTING_CHAMBER.get()),
			EvokerEnchantingChamberJeiCategory.TYPE);
		registration.addRecipeCatalyst(new ItemStack(CBBlocks.EXPERIENCE_PUMP.get()), ExperiencePumpJeiCategory.TYPE);
	}

	private static List<BasinRecipe> basinEntityProcessingRecipes(BasinEntityProcessingOperation operation) {
		ClientPacketListener connection = Minecraft.getInstance()
			.getConnection();
		if (connection == null)
			return List.of();

		return connection.getRecipeManager()
			.getAllRecipesFor(CBRecipeTypes.BASIN_ENTITY_PROCESSING_TYPE.get())
			.stream()
			.filter(recipe -> recipe.getOperation() == operation)
			.map(CreateBiotechJeiPlugin::asCreateBasinRecipe)
			.toList();
	}

	private static List<CreeperBlastChamberHighPressureRecipe> creeperBlastChamberHighPressureRecipes() {
		ClientPacketListener connection = Minecraft.getInstance()
			.getConnection();
		if (connection == null)
			return List.of();

		return connection.getRecipeManager()
			.getAllRecipesFor(CBRecipeTypes.CREEPER_BLAST_CHAMBER_HIGH_PRESSURE_TYPE.get());
	}

	private static BasinRecipe asCreateBasinRecipe(BasinEntityProcessingRecipe recipe) {
		NonNullList<Ingredient> ingredients = NonNullList.create();
		ingredients.addAll(recipe.getIngredients());
		ingredients.add(Ingredient.of(CBItems.CAPTURED_SMALL_SLIME.get()));

		return switch (recipe.getOperation()) {
			case MIXING -> buildCreateBasinRecipe(new ProcessingRecipeBuilder<>(MixingRecipe::new, recipe.getId()),
				ingredients, recipe);
			case PRESSING -> buildCreateBasinRecipe(new ProcessingRecipeBuilder<>(CompactingRecipe::new, recipe.getId()),
				ingredients, recipe);
		};
	}

	private static <T extends BasinRecipe> T buildCreateBasinRecipe(ProcessingRecipeBuilder<T> builder,
		NonNullList<Ingredient> ingredients, BasinEntityProcessingRecipe recipe) {
		builder.withItemIngredients(ingredients);
		for (ItemStack result : recipe.getRollableResults())
			builder.output(result.copy());
		for (FluidStack result : recipe.getFluidResults())
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

	private static List<ItemApplicationRecipe> itemApplicationRecipes() {
		return List.of(powerBeltConversion());
	}
}
