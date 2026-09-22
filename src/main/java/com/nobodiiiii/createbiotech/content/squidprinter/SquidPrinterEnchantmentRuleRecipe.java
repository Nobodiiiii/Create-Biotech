package com.nobodiiiii.createbiotech.content.squidprinter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/** A synced data-pack switch for all enchantment copying or one enchantment ID. */
public record SquidPrinterEnchantmentRuleRecipe(boolean enabled) implements Recipe<RecipeInput> {
	public static final String RULE_PATH = "squid_printer_enchantment_rules/";
	public static final ResourceLocation GLOBAL_RULE_ID =
		CreateBiotech.asResource(RULE_PATH + "_all");

	public static boolean allows(RecipeManager recipeManager, ItemStack template) {
		if (!isCopyingEnabled(recipeManager))
			return false;
		for (var entry : EnchantmentBookCopyItem.getCopySourceEnchantments(template).entrySet()) {
			Holder<Enchantment> enchantment = entry.getKey();
			if (enchantment.unwrapKey().isPresent()
				&& !isEnchantmentEnabled(recipeManager, enchantment.unwrapKey().get().location()))
				return false;
		}
		return true;
	}

	public static boolean isCopyingEnabled(RecipeManager recipeManager) {
		return isRuleEnabled(recipeManager, GLOBAL_RULE_ID);
	}

	public static boolean isEnchantmentEnabled(RecipeManager recipeManager, ResourceLocation enchantmentId) {
		ResourceLocation ruleId = ResourceLocation.fromNamespaceAndPath(enchantmentId.getNamespace(),
			RULE_PATH + enchantmentId.getPath());
		return isRuleEnabled(recipeManager, ruleId);
	}

	private static boolean isRuleEnabled(RecipeManager recipeManager, ResourceLocation ruleId) {
		return recipeManager.getAllRecipesFor(CBRecipeTypes.SQUID_PRINTER_ENCHANTMENT_RULE_TYPE.get())
			.stream()
			.filter(holder -> holder.id().equals(ruleId))
			.map(holder -> holder.value().enabled())
			.findFirst()
			.orElse(true);
	}

	@Override
	public boolean matches(RecipeInput input, Level level) {
		return false;
	}

	@Override
	public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return false;
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider registries) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return CBRecipeTypes.SQUID_PRINTER_ENCHANTMENT_RULE_SERIALIZER.get();
	}

	@Override
	public RecipeType<?> getType() {
		return CBRecipeTypes.SQUID_PRINTER_ENCHANTMENT_RULE_TYPE.get();
	}

	public static class Serializer implements RecipeSerializer<SquidPrinterEnchantmentRuleRecipe> {
		private static final MapCodec<SquidPrinterEnchantmentRuleRecipe> CODEC =
			Codec.BOOL.optionalFieldOf("enabled", true)
				.xmap(SquidPrinterEnchantmentRuleRecipe::new, SquidPrinterEnchantmentRuleRecipe::enabled);
		private static final StreamCodec<RegistryFriendlyByteBuf, SquidPrinterEnchantmentRuleRecipe> STREAM_CODEC =
			StreamCodec.composite(ByteBufCodecs.BOOL, SquidPrinterEnchantmentRuleRecipe::enabled,
				SquidPrinterEnchantmentRuleRecipe::new);

		@Override
		public MapCodec<SquidPrinterEnchantmentRuleRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, SquidPrinterEnchantmentRuleRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
