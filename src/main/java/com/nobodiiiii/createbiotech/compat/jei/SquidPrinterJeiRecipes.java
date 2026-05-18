package com.nobodiiiii.createbiotech.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.squidprinter.EnchantmentBookCopyItem;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterBlockEntity;
import com.nobodiiiii.createbiotech.registry.CBItems;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class SquidPrinterJeiRecipes {

	private SquidPrinterJeiRecipes() {
	}

	public static List<SquidPrinterJeiRecipe> create() {
		List<SquidPrinterJeiRecipe> recipes = new ArrayList<>();
		FluidStack water = new FluidStack(Fluids.WATER, SquidPrinterBlockEntity.CYCLE_WATER_COST);

		List<Component> notes = List.of(
			Component.translatable("create_biotech.jei.squid_printer.note.template"),
			Component.translatable("create_biotech.jei.squid_printer.note.water_cost",
				SquidPrinterBlockEntity.CYCLE_WATER_COST, SquidPrinterBlockEntity.CYCLE_TICKS));

		for (Enchantment enchantment : ForgeRegistries.ENCHANTMENTS.getValues()) {
			if (enchantment == null)
				continue;
			ResourceLocation enchId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
			if (enchId == null)
				continue;
			int level = Math.max(1, enchantment.getMaxLevel());

			ItemStack template = new ItemStack(Items.ENCHANTED_BOOK);
			EnchantedBookItem.addEnchantment(template, new EnchantmentInstance(enchantment, level));

			ItemStack output = EnchantmentBookCopyItem.fromEnchantedBook(template, CBItems.ENCHANTMENT_BOOK_COPY.get());
			ResourceLocation id = CreateBiotech.asResource(
				"squid_printer/" + enchId.getNamespace() + "_" + enchId.getPath() + "_" + level);
			recipes.add(new SquidPrinterJeiRecipe(id, new ItemStack(Items.BOOK), template, output, water, notes));
		}
		return recipes;
	}
}
