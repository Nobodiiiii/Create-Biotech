package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

public final class CapturedEntityBoxRecipeHelper {

	private CapturedEntityBoxRecipeHelper() {}

	public static boolean matchesCapturedEntity(ItemStack stack, EntityType<?> entityType) {
		return stack.getItem() instanceof CapturedEntityBoxItem
			&& CapturedEntityBoxHelper.containsEntityType(stack, entityType);
	}

	public static Ingredient anyBoxIngredient() {
		return Ingredient.of(CBItems.CARDBOARD_BOX.get(), CBItems.LARGE_CARDBOARD_BOX.get());
	}

	public static Ingredient displayIngredient(EntityType<?> entityType) {
		return Ingredient.of(createDisplayBox(CBItems.CARDBOARD_BOX.get().getDefaultInstance(), entityType),
			createDisplayBox(CBItems.LARGE_CARDBOARD_BOX.get().getDefaultInstance(), entityType));
	}

	public static NonNullList<Ingredient> normalizeBoxIngredients(NonNullList<Ingredient> ingredients) {
		NonNullList<Ingredient> normalized = NonNullList.create();
		Ingredient anyBox = anyBoxIngredient();
		for (Ingredient ingredient : ingredients)
			normalized.add(isBoxIngredient(ingredient) ? anyBox : ingredient);
		return normalized;
	}

	public static NonNullList<Ingredient> displayIngredients(NonNullList<Ingredient> ingredients,
		EntityType<?> entityType) {
		NonNullList<Ingredient> display = NonNullList.create();
		Ingredient entityBox = displayIngredient(entityType);
		for (Ingredient ingredient : ingredients)
			display.add(isBoxIngredient(ingredient) ? entityBox : ingredient);
		return display;
	}

	public static boolean inventoryHasCapturedEntity(CraftingContainer inv, EntityType<?> entityType) {
		for (int i = 0; i < inv.getContainerSize(); i++)
			if (matchesCapturedEntity(inv.getItem(i), entityType))
				return true;
		return false;
	}

	public static boolean isBoxIngredient(Ingredient ingredient) {
		ItemStack[] items = ingredient.getItems();
		if (items.length == 0)
			return false;
		for (ItemStack item : items)
			if (!(item.getItem() instanceof CapturedEntityBoxItem))
				return false;
		return true;
	}

	private static ItemStack createDisplayBox(ItemStack box, EntityType<?> entityType) {
		ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
		if (entityId == null)
			return box;

		CompoundTag tag = box.getOrCreateTag();
		CompoundTag entityData = new CompoundTag();
		entityData.putString("id", entityId.toString());
		tag.put("CapturedEntity", entityData);
		tag.putString("CapturedEntityDescId", entityType.getDescriptionId());
		return box;
	}
}
