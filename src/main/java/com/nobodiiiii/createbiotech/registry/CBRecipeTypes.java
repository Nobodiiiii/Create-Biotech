package com.nobodiiiii.createbiotech.registry;

import net.minecraft.core.registries.BuiltInRegistries;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberHighPressureRecipe;
import com.nobodiiiii.createbiotech.content.giantfrog.FrogContentsPackagingRecipe;
import com.nobodiiiii.createbiotech.content.sonicdogcannon.SonicDogCannonUpgradeRecipe;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.neoforged.neoforge.registries.DeferredHolder;

public class CBRecipeTypes {
	private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
		DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, CreateBiotech.MOD_ID);
	private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
		DeferredRegister.create(Registries.RECIPE_TYPE, CreateBiotech.MOD_ID);

	public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CreeperBlastChamberHighPressureRecipe>>
		CREEPER_BLAST_CHAMBER_HIGH_PRESSURE_SERIALIZER =
			RECIPE_SERIALIZERS.register("creeper_blast_chamber_high_pressure",
				CreeperBlastChamberHighPressureRecipe.Serializer::new);

	public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SquidPrinterRecipe>> SQUID_PRINTER_SERIALIZER =
		RECIPE_SERIALIZERS.register("squid_printer",
			() -> new StandardProcessingRecipe.Serializer<>(SquidPrinterRecipe::new));

	public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SonicDogCannonUpgradeRecipe>>
		SONIC_DOG_CANNON_UPGRADE_SERIALIZER =
			RECIPE_SERIALIZERS.register("sonic_dog_cannon_upgrade", SonicDogCannonUpgradeRecipe.Serializer::new);

	public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FrogContentsPackagingRecipe>>
		FROG_CONTENTS_PACKAGING_SERIALIZER =
			RECIPE_SERIALIZERS.register("frog_contents_packaging",
				() -> new ItemApplicationRecipe.Serializer<>(FrogContentsPackagingRecipe::new));

	public static final DeferredHolder<RecipeType<?>, RecipeType<CreeperBlastChamberHighPressureRecipe>>
		CREEPER_BLAST_CHAMBER_HIGH_PRESSURE_TYPE =
			RECIPE_TYPES.register("creeper_blast_chamber_high_pressure",
				() -> RecipeType.simple(CreateBiotech.asResource("creeper_blast_chamber_high_pressure")));

	public static final DeferredHolder<RecipeType<?>, RecipeType<SquidPrinterRecipe>> SQUID_PRINTER_TYPE =
		RECIPE_TYPES.register("squid_printer",
			() -> RecipeType.simple(CreateBiotech.asResource("squid_printer")));

	private CBRecipeTypes() {}

	public static void register(IEventBus modEventBus) {
		RECIPE_SERIALIZERS.register(modEventBus);
		RECIPE_TYPES.register(modEventBus);
	}
}
