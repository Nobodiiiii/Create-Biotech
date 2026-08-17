package com.nobodiiiii.createbiotech.content.sonicdogcannon;

import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;

/**
 * Installs one independent upgrade while preserving the cannon's custom data and all previous
 * upgrades. The smithing template slot is deliberately left empty.
 */
public class SonicDogCannonUpgradeRecipe implements SmithingRecipe {

	private static final int TEMPLATE_SLOT = 0;
	private static final int BASE_SLOT = 1;
	private static final int ADDITION_SLOT = 2;

	private final ResourceLocation id;
	private final Ingredient addition;
	private final SonicDogCannonUpgrade upgrade;

	public SonicDogCannonUpgradeRecipe(ResourceLocation id, Ingredient addition, SonicDogCannonUpgrade upgrade) {
		this.id = id;
		this.addition = addition;
		this.upgrade = upgrade;
	}

	@Override
	public boolean matches(Container container, Level level) {
		return matchesIngredients(container);
	}

	@Override
	public ItemStack assemble(Container container, RegistryAccess registries) {
		if (!matchesIngredients(container))
			return ItemStack.EMPTY;

		ItemStack result = container.getItem(BASE_SLOT).copyWithCount(1);
		if (upgrade == SonicDogCannonUpgrade.DOG_COLLAR) {
			DyeColor color = DyeColor.getColor(container.getItem(ADDITION_SLOT));
			if (color == null)
				return ItemStack.EMPTY;
			SonicDogCannonUpgrade.setCollarColor(result, color);
		} else {
			upgrade.install(result);
		}
		return result;
	}

	private boolean matchesIngredients(Container container) {
		ItemStack base = container.getItem(BASE_SLOT);
		ItemStack additionStack = container.getItem(ADDITION_SLOT);
		if (!container.getItem(TEMPLATE_SLOT).isEmpty() || !isBaseIngredient(base)
			|| !addition.test(additionStack))
			return false;

		if (upgrade != SonicDogCannonUpgrade.DOG_COLLAR)
			return !upgrade.isInstalled(base);

		DyeColor color = DyeColor.getColor(additionStack);
		return color != null && (!upgrade.isInstalled(base)
			|| SonicDogCannonUpgrade.getCollarColor(base) != color);
	}

	@Override
	public ItemStack getResultItem(RegistryAccess registries) {
		ItemStack result = new ItemStack(CBItems.SONIC_DOG_CANNON.get());
		if (upgrade == SonicDogCannonUpgrade.DOG_COLLAR)
			SonicDogCannonUpgrade.setCollarColor(result, DyeColor.RED);
		else
			upgrade.install(result);
		return result;
	}

	@Override
	public boolean isTemplateIngredient(ItemStack stack) {
		return false;
	}

	@Override
	public boolean isBaseIngredient(ItemStack stack) {
		return stack.is(CBItems.SONIC_DOG_CANNON.get());
	}

	@Override
	public boolean isAdditionIngredient(ItemStack stack) {
		return addition.test(stack);
	}

	@Override
	public ResourceLocation getId() {
		return id;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return CBRecipeTypes.SONIC_DOG_CANNON_UPGRADE_SERIALIZER.get();
	}

	@Override
	public boolean isIncomplete() {
		return Stream.of(addition).anyMatch(ForgeHooks::hasNoElements);
	}

	public Ingredient addition() {
		return addition;
	}

	public SonicDogCannonUpgrade upgrade() {
		return upgrade;
	}

	public static class Serializer implements RecipeSerializer<SonicDogCannonUpgradeRecipe> {

		@Override
		public SonicDogCannonUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
			return new SonicDogCannonUpgradeRecipe(id,
				Ingredient.fromJson(GsonHelper.getNonNull(json, "addition")),
				SonicDogCannonUpgrade.fromSerializedName(GsonHelper.getAsString(json, "upgrade")));
		}

		@Override
		public SonicDogCannonUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
			return new SonicDogCannonUpgradeRecipe(id, Ingredient.fromNetwork(buffer),
				buffer.readEnum(SonicDogCannonUpgrade.class));
		}

		@Override
		public void toNetwork(FriendlyByteBuf buffer, SonicDogCannonUpgradeRecipe recipe) {
			recipe.addition.toNetwork(buffer);
			buffer.writeEnum(recipe.upgrade);
		}
	}
}
