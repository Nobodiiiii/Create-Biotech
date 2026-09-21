package com.nobodiiiii.createbiotech.compat.jei;

import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterRecipe;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class SquidPrinterJeiRecipes {

	private static final String ITEM_APPLICATION_PREFIX = "item_application/squid_printer/";
	private static final String SPOUT_FILLING_PREFIX = "spout_filling/squid_printer/";

	private SquidPrinterJeiRecipes() {
	}

	public static List<SquidPrinterJeiRecipe> create() {
		List<RecipeHolder<SquidPrinterRecipe>> recipes = getRecipes();
		if (recipes.isEmpty())
			return List.of();

		List<EnchantmentEntry> entries = createEnchantmentEntries();
		List<SquidPrinterJeiRecipe> displays = new ArrayList<>(recipes.size() * entries.size());
		for (RecipeHolder<SquidPrinterRecipe> holder : recipes) {
			SquidPrinterRecipe recipe = holder.value();
			List<ItemStack> inputs = List.of(recipe.getIngredients().getFirst().getItems())
				.stream()
				.map(ItemStack::copy)
				.toList();
			for (EnchantmentEntry entry : entries) {
				ResourceLocation id = ResourceLocation.fromNamespaceAndPath(holder.id().getNamespace(),
					holder.id().getPath() + "/" + entry.idSegment());
				List<ItemStack> outputs = entry.templateBooks().stream()
					.map(recipe::createResult)
					.toList();
				displays.add(new SquidPrinterJeiRecipe(id, inputs, recipe.getRequiredFluid(),
					entry.templateBooks(), outputs));
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

	private static List<RecipeHolder<SquidPrinterRecipe>> getRecipes() {
		ClientPacketListener connection = Minecraft.getInstance()
			.getConnection();
		if (connection == null)
			return List.of();
		return connection.getRecipeManager()
			.getAllRecipesFor(CBRecipeTypes.SQUID_PRINTER_TYPE.get());
	}

	private static List<EnchantmentEntry> createEnchantmentEntries() {
		List<EnchantmentEntry> entries = new ArrayList<>();
		if (Minecraft.getInstance().level == null)
			return entries;
		Registry<Enchantment> enchantments = Minecraft.getInstance().level.registryAccess()
			.registryOrThrow(Registries.ENCHANTMENT);
		for (Holder.Reference<Enchantment> enchantmentHolder : enchantments.holders()
			.sorted((left, right) -> left.getKey().location().compareTo(right.getKey().location()))
			.toList()) {
			ResourceLocation enchantmentId = enchantmentHolder.getKey().location();
			Enchantment enchantment = enchantmentHolder.value();
			int maxLevel = Math.max(1, enchantment.getMaxLevel());
			List<ItemStack> templates = new ArrayList<>(maxLevel);
			for (int level = 1; level <= maxLevel; level++) {
				ItemStack template = new ItemStack(Items.ENCHANTED_BOOK);
				var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
					net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
				mutable.set(enchantmentHolder, level);
				template.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
				templates.add(template);
			}
			entries.add(new EnchantmentEntry(
				enchantmentId.getNamespace() + "_" + enchantmentId.getPath(), templates));
		}
		if (entries.isEmpty()) {
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			entries.add(new EnchantmentEntry("empty", List.of(book)));
		}
		return entries;
	}

	private record EnchantmentEntry(String idSegment, List<ItemStack> templateBooks) {
	}
}
