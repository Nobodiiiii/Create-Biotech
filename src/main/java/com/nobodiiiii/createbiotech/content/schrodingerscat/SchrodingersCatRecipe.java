package com.nobodiiiii.createbiotech.content.schrodingerscat;

import com.nobodiiiii.createbiotech.content.cardboardbox.CardboardBoxItem;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import com.google.gson.JsonObject;

public class SchrodingersCatRecipe extends ShapedRecipe {

	private static final Ingredient CAT_BOX_DISPLAY = createCatBoxDisplay();

	public SchrodingersCatRecipe(ResourceLocation id, String group, int width, int height,
		NonNullList<Ingredient> recipeItems, ItemStack result) {
		super(id, group, CraftingBookCategory.MISC, width, height, recipeItems, result);
	}

	@Override
	public NonNullList<Ingredient> getIngredients() {
		NonNullList<Ingredient> original = super.getIngredients();
		NonNullList<Ingredient> display = NonNullList.create();
		for (Ingredient ing : original) {
			ItemStack[] items = ing.getItems();
			if (items.length > 0 && items[0].is(CBItems.CARDBOARD_BOX.get()))
				display.add(CAT_BOX_DISPLAY);
			else
				display.add(ing);
		}
		return display;
	}

	private static Ingredient createCatBoxDisplay() {
		ItemStack catBox = new ItemStack(CBItems.CARDBOARD_BOX.get());
		CompoundTag tag = catBox.getOrCreateTag();
		CompoundTag entityData = new CompoundTag();
		entityData.putString("id", "minecraft:cat");
		tag.put("CapturedEntity", entityData);
		tag.putString("CapturedEntityDescId", "entity.minecraft.cat");
		return Ingredient.of(catBox);
	}

	@Override
	public boolean matches(CraftingContainer inv, Level level) {
		if (!super.matches(inv, level))
			return false;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(CBItems.CARDBOARD_BOX.get()) && hasCatInBox(stack))
				return true;
		}
		return false;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return CBRecipeTypes.SCHRODINGERS_CAT_SERIALIZER.get();
	}

	private static boolean hasCatInBox(ItemStack stack) {
		if (!CardboardBoxItem.hasCapturedEntity(stack))
			return false;
		CompoundTag tag = stack.getTag();
		if (tag == null)
			return false;
		return "minecraft:cat".equals(tag.getCompound("CapturedEntity").getString("id"));
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
