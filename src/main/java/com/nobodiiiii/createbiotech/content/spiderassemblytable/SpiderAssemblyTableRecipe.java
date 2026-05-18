package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.google.gson.JsonObject;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxRecipeHelper;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

public class SpiderAssemblyTableRecipe extends ShapedRecipe {

	public SpiderAssemblyTableRecipe(ResourceLocation id, String group, int width, int height,
		NonNullList<Ingredient> recipeItems, ItemStack result) {
		super(id, group, CraftingBookCategory.MISC, width, height,
			CapturedEntityBoxRecipeHelper.normalizeBoxIngredients(recipeItems), result);
	}

	@Override
	public NonNullList<Ingredient> getIngredients() {
		return CapturedEntityBoxRecipeHelper.displayIngredients(super.getIngredients(), EntityType.SPIDER);
	}

	@Override
	public boolean matches(CraftingContainer inv, Level level) {
		return super.matches(inv, level)
			&& CapturedEntityBoxRecipeHelper.inventoryHasCapturedEntity(inv, EntityType.SPIDER);
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return CBRecipeTypes.SPIDER_ASSEMBLY_TABLE_SERIALIZER.get();
	}

	public static class Serializer implements RecipeSerializer<SpiderAssemblyTableRecipe> {

		@Override
		public SpiderAssemblyTableRecipe fromJson(ResourceLocation id, JsonObject json) {
			ShapedRecipe vanilla = RecipeSerializer.SHAPED_RECIPE.fromJson(id, json);
			return new SpiderAssemblyTableRecipe(id, vanilla.getGroup(), vanilla.getWidth(), vanilla.getHeight(),
				vanilla.getIngredients(), vanilla.getResultItem(RegistryAccess.EMPTY));
		}

		@Override
		public SpiderAssemblyTableRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
			String group = buf.readUtf();
			int width = buf.readVarInt();
			int height = buf.readVarInt();
			NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
			for (int i = 0; i < ingredients.size(); i++)
				ingredients.set(i, Ingredient.fromNetwork(buf));
			ItemStack result = buf.readItem();
			return new SpiderAssemblyTableRecipe(id, group, width, height, ingredients, result);
		}

		@Override
		public void toNetwork(FriendlyByteBuf buf, SpiderAssemblyTableRecipe recipe) {
			buf.writeUtf(recipe.getGroup());
			buf.writeVarInt(recipe.getWidth());
			buf.writeVarInt(recipe.getHeight());
			for (Ingredient ingredient : recipe.getIngredients())
				ingredient.toNetwork(buf);
			buf.writeItem(recipe.getResultItem(RegistryAccess.EMPTY));
		}
	}
}
