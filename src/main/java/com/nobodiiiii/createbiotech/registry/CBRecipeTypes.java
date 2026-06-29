package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberHighPressureRecipe;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CBRecipeTypes {
	private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
		DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, CreateBiotech.MOD_ID);
	private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
		DeferredRegister.create(Registries.RECIPE_TYPE, CreateBiotech.MOD_ID);

	public static final RegistryObject<RecipeSerializer<CreeperBlastChamberHighPressureRecipe>>
		CREEPER_BLAST_CHAMBER_HIGH_PRESSURE_SERIALIZER =
			RECIPE_SERIALIZERS.register("creeper_blast_chamber_high_pressure",
				CreeperBlastChamberHighPressureRecipe.Serializer::new);

	public static final RegistryObject<RecipeSerializer<SquidPrinterRecipe>> SQUID_PRINTER_SERIALIZER =
		RECIPE_SERIALIZERS.register("squid_printer",
			() -> new ProcessingRecipeSerializer<>(SquidPrinterRecipe::new));

	public static final RegistryObject<RecipeType<CreeperBlastChamberHighPressureRecipe>>
		CREEPER_BLAST_CHAMBER_HIGH_PRESSURE_TYPE =
			RECIPE_TYPES.register("creeper_blast_chamber_high_pressure",
				() -> RecipeType.simple(CreateBiotech.asResource("creeper_blast_chamber_high_pressure")));

	public static final RegistryObject<RecipeType<SquidPrinterRecipe>> SQUID_PRINTER_TYPE =
		RECIPE_TYPES.register("squid_printer",
			() -> RecipeType.simple(CreateBiotech.asResource("squid_printer")));

	private CBRecipeTypes() {}

	public static void register(IEventBus modEventBus) {
		RECIPE_SERIALIZERS.register(modEventBus);
		RECIPE_TYPES.register(modEventBus);
	}
}
