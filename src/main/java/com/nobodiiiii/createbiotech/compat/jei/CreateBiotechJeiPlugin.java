package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberHighPressureRecipe;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class CreateBiotechJeiPlugin implements IModPlugin {
	private static final RecipeType<AbstractCrushingRecipe> CREATE_CRUSHING =
		new RecipeType<>(Create.asResource("crushing"), AbstractCrushingRecipe.class);

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

	private static List<CreeperBlastChamberHighPressureRecipe> creeperBlastChamberHighPressureRecipes() {
		ClientPacketListener connection = Minecraft.getInstance()
			.getConnection();
		if (connection == null)
			return List.of();

		return connection.getRecipeManager()
			.getAllRecipesFor(CBRecipeTypes.CREEPER_BLAST_CHAMBER_HIGH_PRESSURE_TYPE.get());
	}
}
