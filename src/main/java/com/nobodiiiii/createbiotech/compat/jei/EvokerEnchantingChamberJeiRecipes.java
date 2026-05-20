package com.nobodiiiii.createbiotech.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.evokerenchantingchamber.EvokerEnchantingChamberBlockEntity;
import com.nobodiiiii.createbiotech.content.squidprinter.EnchantmentBookCopyItem;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.network.chat.Component;
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

		List<Component> notes = List.of(
			Component.translatable("create_biotech.jei.evoker_enchanting_chamber.note.ritual",
				EvokerEnchantingChamberBlockEntity.CAST_DURATION_TICKS_PER_LEVEL),
			Component.translatable("create_biotech.jei.evoker_enchanting_chamber.note.experience"),
			Component.translatable("create_biotech.jei.evoker_enchanting_chamber.note.segmented"),
			Component.translatable("create_biotech.jei.evoker_enchanting_chamber.note.blocked_until_extracted"));

		for (Enchantment enchantment : ForgeRegistries.ENCHANTMENTS.getValues()) {
			if (enchantment == null)
				continue;
			ResourceLocation enchId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
			if (enchId == null)
				continue;
			for (int level = 1; level <= Math.max(1, enchantment.getMaxLevel()); level++) {
				ItemStack templateBook = new ItemStack(Items.ENCHANTED_BOOK);
				EnchantedBookItem.addEnchantment(templateBook, new EnchantmentInstance(enchantment, level));

				ItemStack inputCopy =
					EnchantmentBookCopyItem.fromEnchantedBook(templateBook, CBItems.ENCHANTMENT_BOOK_COPY.get());

				ResourceLocation id = CreateBiotech.asResource(
					"evoker_enchanting_chamber/" + enchId.getNamespace() + "_" + enchId.getPath() + "_" + level);
				recipes.add(new EvokerEnchantingChamberJeiRecipe(id, inputCopy, templateBook, notes));
			}
		}
		return recipes;
	}
}
