package com.nobodiiiii.createbiotech.content.processing.basin;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

public class BasinEntityProcessingRecipe implements Recipe<Container> {
	private final ResourceLocation id;
	private final BasinEntityProcessingOperation operation;
	private final NonNullList<Ingredient> ingredients;
	private final BasinEntityIngredient entityIngredient;
	private final NonNullList<ItemStack> results;

	public BasinEntityProcessingRecipe(ResourceLocation id, BasinEntityProcessingOperation operation,
		NonNullList<Ingredient> ingredients, BasinEntityIngredient entityIngredient, NonNullList<ItemStack> results) {
		this.id = id;
		this.operation = operation;
		this.ingredients = ingredients;
		this.entityIngredient = entityIngredient;
		this.results = results;
	}

	@Override
	public boolean matches(Container container, Level level) {
		return false;
	}

	@Override
	public ItemStack assemble(Container container, RegistryAccess registryAccess) {
		return getResultItem(registryAccess);
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return true;
	}

	@Override
	public ItemStack getResultItem(RegistryAccess registryAccess) {
		return results.isEmpty() ? ItemStack.EMPTY : results.get(0)
			.copy();
	}

	@Override
	public ResourceLocation getId() {
		return id;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return CBRecipeTypes.BASIN_ENTITY_PROCESSING_SERIALIZER.get();
	}

	@Override
	public RecipeType<?> getType() {
		return CBRecipeTypes.BASIN_ENTITY_PROCESSING_TYPE.get();
	}

	@Override
	public NonNullList<Ingredient> getIngredients() {
		return ingredients;
	}

	public BasinEntityProcessingOperation getOperation() {
		return operation;
	}

	public BasinEntityIngredient getEntityIngredient() {
		return entityIngredient;
	}

	public List<ItemStack> getRollableResults() {
		return results;
	}

	public static class Serializer implements RecipeSerializer<BasinEntityProcessingRecipe> {

		@Override
		public BasinEntityProcessingRecipe fromJson(ResourceLocation id, JsonObject json) {
			BasinEntityProcessingOperation operation = BasinEntityProcessingOperation.fromSerializedName(
				GsonHelper.getAsString(json, "operation"));

			NonNullList<Ingredient> ingredients = NonNullList.create();
			for (var element : GsonHelper.getAsJsonArray(json, "ingredients"))
				ingredients.add(Ingredient.fromJson(element));
			if (ingredients.isEmpty())
				throw new JsonSyntaxException("Basin entity processing recipes require at least one item ingredient");

			BasinEntityIngredient entityIngredient =
				BasinEntityIngredient.fromJson(GsonHelper.getAsJsonObject(json, "entity"));

			NonNullList<ItemStack> results = NonNullList.create();
			JsonArray resultsJson = GsonHelper.getAsJsonArray(json, "results");
			for (var element : resultsJson) {
				ItemStack stack = ShapedRecipe.itemStackFromJson(GsonHelper.convertToJsonObject(element, "result"));
				if (!stack.isEmpty())
					results.add(stack);
			}
			if (results.isEmpty())
				throw new JsonSyntaxException("Basin entity processing recipes require at least one item result");

			return new BasinEntityProcessingRecipe(id, operation, ingredients, entityIngredient, results);
		}

		@Override
		public BasinEntityProcessingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
			BasinEntityProcessingOperation operation = BasinEntityProcessingOperation.read(buffer);

			NonNullList<Ingredient> ingredients = NonNullList.create();
			int ingredientCount = buffer.readVarInt();
			for (int i = 0; i < ingredientCount; i++)
				ingredients.add(Ingredient.fromNetwork(buffer));

			BasinEntityIngredient entityIngredient = BasinEntityIngredient.fromNetwork(buffer);

			NonNullList<ItemStack> results = NonNullList.create();
			int resultCount = buffer.readVarInt();
			for (int i = 0; i < resultCount; i++)
				results.add(buffer.readItem());

			return new BasinEntityProcessingRecipe(id, operation, ingredients, entityIngredient, results);
		}

		@Override
		public void toNetwork(FriendlyByteBuf buffer, BasinEntityProcessingRecipe recipe) {
			recipe.operation.write(buffer);

			buffer.writeVarInt(recipe.ingredients.size());
			for (Ingredient ingredient : recipe.ingredients)
				ingredient.toNetwork(buffer);

			recipe.entityIngredient.write(buffer);

			buffer.writeVarInt(recipe.results.size());
			for (ItemStack stack : recipe.results)
				buffer.writeItem(stack);
		}
	}
}
