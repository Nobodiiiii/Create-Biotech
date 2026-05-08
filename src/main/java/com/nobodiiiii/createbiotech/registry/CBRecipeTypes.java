package com.nobodiiiii.createbiotech.registry;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.processing.basin.BasinEntityProcessingRecipe;
import com.nobodiiiii.createbiotech.content.schrodingerscat.SchrodingersCatRecipe;

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

	public static final RegistryObject<RecipeSerializer<BasinEntityProcessingRecipe>>
		BASIN_ENTITY_PROCESSING_SERIALIZER = RECIPE_SERIALIZERS.register("basin_entity_processing",
			BasinEntityProcessingRecipe.Serializer::new);

	public static final RegistryObject<RecipeSerializer<SchrodingersCatRecipe>>
		SCHRODINGERS_CAT_SERIALIZER = RECIPE_SERIALIZERS.register("schrodingers_cat",
			SchrodingersCatRecipe.Serializer::new);

	public static final RegistryObject<RecipeType<BasinEntityProcessingRecipe>> BASIN_ENTITY_PROCESSING_TYPE =
		RECIPE_TYPES.register("basin_entity_processing",
			() -> RecipeType.simple(CreateBiotech.asResource("basin_entity_processing")));

	private CBRecipeTypes() {}

	public static void register(IEventBus modEventBus) {
		RECIPE_SERIALIZERS.register(modEventBus);
		RECIPE_TYPES.register(modEventBus);
	}
}
