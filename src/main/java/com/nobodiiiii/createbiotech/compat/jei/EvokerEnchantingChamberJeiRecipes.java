package com.nobodiiiii.createbiotech.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.experience.ExperienceConstants;
import com.nobodiiiii.createbiotech.content.squidprinter.EnchantmentBookCopyItem;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.ForgeRegistries;

public final class EvokerEnchantingChamberJeiRecipes {

	private EvokerEnchantingChamberJeiRecipes() {
	}

	public static List<EvokerEnchantingChamberJeiRecipe> create() {
		List<EvokerEnchantingChamberJeiRecipe> recipes = new ArrayList<>();
		for (ResourceLocation enchId : ForgeRegistries.ENCHANTMENTS.getKeys()
			.stream()
			.sorted()
			.toList()) {
			Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(enchId);
			if (enchantment == null)
				continue;
			int maxLevel = Math.max(1, enchantment.getMaxLevel());

			List<ItemStack> outputBooks = IntStream.rangeClosed(1, maxLevel)
				.mapToObj(level -> {
					ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
					EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(enchantment, level));
					return book;
				})
				.toList();
			List<ItemStack> inputCopies = outputBooks.stream()
				.map(book -> EnchantmentBookCopyItem.fromTemplate(book, CBItems.ENCHANTMENT_BOOK_COPY.get()))
				.toList();
			List<Integer> fluidAmounts = IntStream.rangeClosed(1, maxLevel)
				.map(level -> level * ExperienceConstants.chamberFluidPerLevel())
				.boxed()
				.toList();

			ResourceLocation id = CreateBiotech.asResource(
				"evoker_enchanting_chamber/" + enchId.getNamespace() + "_" + enchId.getPath());
			recipes.add(new EvokerEnchantingChamberJeiRecipe(id, inputCopies, outputBooks, fluidAmounts));
		}
		return recipes;
	}
}
