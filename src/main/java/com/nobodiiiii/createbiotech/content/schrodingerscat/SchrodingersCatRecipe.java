package com.nobodiiiii.createbiotech.content.schrodingerscat;

import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

import com.google.gson.JsonObject;

public class SchrodingersCatRecipe extends ShapedRecipe {

	public SchrodingersCatRecipe(ResourceLocation id, String group, int width, int height,
		NonNullList<Ingredient> recipeItems, ItemStack result) {
		super(id, group, CraftingBookCategory.MISC, width, height, recipeItems, result);
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return CBRecipeTypes.SCHRODINGERS_CAT_SERIALIZER.get();
	}

	public static class Serializer implements RecipeSerializer<SchrodingersCatRecipe> {

		@Override
		public SchrodingersCatRecipe fromJson(ResourceLocation id, JsonObject json) {
			ShapedRecipe vanilla = RecipeSerializer.SHAPED_RECIPE.fromJson(id, json);
			return new SchrodingersCatRecipe(id, vanilla.getGroup(), vanilla.getWidth(), vanilla.getHeight(),
				vanilla.getIngredients(), vanilla.getResultItem(RegistryAccess.EMPTY));
		}

		@Override
		public SchrodingersCatRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
			String group = buf.readUtf();
			int width = buf.readVarInt();
			int height = buf.readVarInt();
			NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
			for (int i = 0; i < ingredients.size(); i++)
				ingredients.set(i, Ingredient.fromNetwork(buf));
			ItemStack result = buf.readItem();
			return new SchrodingersCatRecipe(id, group, width, height, ingredients, result);
		}

		@Override
		public void toNetwork(FriendlyByteBuf buf, SchrodingersCatRecipe recipe) {
			buf.writeUtf(recipe.getGroup());
			buf.writeVarInt(recipe.getWidth());
			buf.writeVarInt(recipe.getHeight());
			for (Ingredient ingredient : recipe.getIngredients())
				ingredient.toNetwork(buf);
			buf.writeItem(recipe.getResultItem(RegistryAccess.EMPTY));
		}
	}
}
