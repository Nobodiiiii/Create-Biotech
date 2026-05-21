package com.nobodiiiii.createbiotech.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.squidprinter.EnchantmentBookCopyItem;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterRecipe;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.ForgeRegistries;

public final class SquidPrinterJeiRecipes {

	private static final String ITEM_APPLICATION_PREFIX = "item_application/squid_printer/";
	private static final String SPOUT_FILLING_PREFIX = "spout_filling/squid_printer/";

	private SquidPrinterJeiRecipes() {
	}

	public static List<SquidPrinterJeiRecipe> create() {
		List<SquidPrinterRecipe> recipes = getRecipes();
		if (recipes.isEmpty())
			return List.of();

		List<EnchantmentEntry> entries = createEnchantmentEntries();
		List<SquidPrinterJeiRecipe> displays = new ArrayList<>(recipes.size() * entries.size());
		for (SquidPrinterRecipe recipe : recipes) {
			for (EnchantmentEntry entry : entries) {
				ResourceLocation id = new ResourceLocation(recipe.getId().getNamespace(),
					recipe.getId().getPath() + "/" + entry.idSegment());
				displays.add(new SquidPrinterJeiRecipe(id, new ItemStack(Items.BOOK), recipe.getRequiredFluid(),
					entry.templateBooks(), entry.outputCopies()));
			}
		}
		return displays;
	}

	public static boolean isSquidPrinterItemApplication(ResourceLocation id) {
		return id.getNamespace()
			.equals(CreateBiotech.MOD_ID) && id.getPath()
				.startsWith(ITEM_APPLICATION_PREFIX);
	}

	public static boolean isSquidPrinterSpoutFilling(ResourceLocation id) {
		return id.getNamespace()
			.equals(CreateBiotech.MOD_ID) && id.getPath()
				.startsWith(SPOUT_FILLING_PREFIX);
	}

	private static List<SquidPrinterRecipe> getRecipes() {
		ClientPacketListener connection = Minecraft.getInstance()
			.getConnection();
		if (connection == null)
			return List.of();
		return connection.getRecipeManager()
			.getAllRecipesFor(CBRecipeTypes.SQUID_PRINTER_TYPE.get());
	}

	private static List<EnchantmentEntry> createEnchantmentEntries() {
		List<EnchantmentEntry> entries = new ArrayList<>();
		for (ResourceLocation enchantmentId : ForgeRegistries.ENCHANTMENTS.getKeys()
			.stream()
			.sorted()
			.toList()) {
			Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(enchantmentId);
			if (enchantment == null)
				continue;
			int maxLevel = Math.max(1, enchantment.getMaxLevel());
			List<ItemStack> templates = new ArrayList<>(maxLevel);
			for (int level = 1; level <= maxLevel; level++) {
				ItemStack template = new ItemStack(Items.ENCHANTED_BOOK);
				EnchantedBookItem.addEnchantment(template, new EnchantmentInstance(enchantment, level));
				templates.add(template);
			}
			List<ItemStack> outputs = templates.stream()
				.map(template -> EnchantmentBookCopyItem.fromTemplate(template, CBItems.ENCHANTMENT_BOOK_COPY.get()))
				.toList();
			entries.add(new EnchantmentEntry(
				enchantmentId.getNamespace() + "_" + enchantmentId.getPath(), templates, outputs));
		}
		if (entries.isEmpty()) {
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			ItemStack copy = new ItemStack(CBItems.ENCHANTMENT_BOOK_COPY.get());
			entries.add(new EnchantmentEntry("empty", List.of(book), List.of(copy)));
		}
		return entries;
	}

	private record EnchantmentEntry(String idSegment, List<ItemStack> templateBooks, List<ItemStack> outputCopies) {
	}
}
