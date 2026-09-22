package com.nobodiiiii.createbiotech.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterEnchantmentRuleRecipe;
import com.nobodiiiii.createbiotech.content.squidprinter.SquidPrinterRecipe;
import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

public final class SquidPrinterJeiRecipes {

	private static final String ITEM_APPLICATION_PREFIX = "item_application/squid_printer/";
	private static final String SPOUT_FILLING_PREFIX = "spout_filling/squid_printer/";

	private SquidPrinterJeiRecipes() {}

	public static List<SquidPrinterJeiRecipe> create() {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		Level level = Minecraft.getInstance().level;
		if (connection == null || level == null)
			return List.of();

		RecipeManager recipeManager = connection.getRecipeManager();
		List<RecipeHolder<SquidPrinterRecipe>> recipes =
			recipeManager.getAllRecipesFor(CBRecipeTypes.SQUID_PRINTER_TYPE.get());
		List<EnchantmentEntry> enchantments = createEnchantmentEntries(level, recipeManager);
		List<SquidPrinterJeiRecipe> displays = new ArrayList<>();

		for (RecipeHolder<SquidPrinterRecipe> holder : recipes) {
			SquidPrinterRecipe recipe = holder.value();
			if (recipe.getIngredients().isEmpty())
				continue;
			List<ItemStack> inputs = List.of(recipe.getIngredients().getFirst().getItems()).stream()
				.map(ItemStack::copy)
				.toList();
			if (!recipe.copiesEnchantments()) {
				List<ItemStack> templates = recipe.getTemplateIngredient()
					.map(ingredient -> List.of(ingredient.getItems()).stream()
						.map(ItemStack::copy)
						.toList())
					.orElse(List.of());
				ItemStack output = recipe.createDisplayResult(ItemStack.EMPTY);
				displays.add(new SquidPrinterJeiRecipe(holder.id(), inputs, recipe.getRequiredFluid(), templates,
					output.isEmpty() ? List.of() : List.of(output)));
				continue;
			}

			for (EnchantmentEntry enchantment : enchantments) {
				List<ItemStack> templates = createEnchantedTemplates(recipe, enchantment).stream()
					.filter(template -> recipe.matchesTemplate(template, level))
					.toList();
				if (templates.isEmpty())
					continue;
				ResourceLocation id = ResourceLocation.fromNamespaceAndPath(holder.id().getNamespace(),
					holder.id().getPath() + "/" + enchantment.idSegment());
				List<ItemStack> outputs = templates.stream()
					.map(recipe::createDisplayResult)
					.toList();
				displays.add(new SquidPrinterJeiRecipe(id, inputs, recipe.getRequiredFluid(), templates, outputs));
			}
		}
		return displays;
	}

	public static boolean isSquidPrinterItemApplication(ResourceLocation id) {
		return id.getNamespace().equals(CreateBiotech.MOD_ID)
			&& id.getPath().startsWith(ITEM_APPLICATION_PREFIX);
	}

	public static boolean isSquidPrinterSpoutFilling(ResourceLocation id) {
		return id.getNamespace().equals(CreateBiotech.MOD_ID)
			&& id.getPath().startsWith(SPOUT_FILLING_PREFIX);
	}

	private static List<EnchantmentEntry> createEnchantmentEntries(Level level, RecipeManager recipeManager) {
		if (!SquidPrinterEnchantmentRuleRecipe.isCopyingEnabled(recipeManager))
			return List.of();

		List<EnchantmentEntry> entries = new ArrayList<>();
		Registry<Enchantment> enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		for (Holder.Reference<Enchantment> enchantmentHolder : enchantments.holders()
			.sorted((left, right) -> left.getKey().location().compareTo(right.getKey().location()))
			.toList()) {
			ResourceLocation enchantmentId = enchantmentHolder.getKey().location();
			if (!SquidPrinterEnchantmentRuleRecipe.isEnchantmentEnabled(recipeManager, enchantmentId))
				continue;
			entries.add(new EnchantmentEntry(
				enchantmentId.getNamespace() + "_" + enchantmentId.getPath(), enchantmentHolder,
				Math.max(1, enchantmentHolder.value().getMaxLevel())));
		}
		return entries;
	}

	private static List<ItemStack> createEnchantedTemplates(SquidPrinterRecipe recipe, EnchantmentEntry entry) {
		List<ItemStack> bases = recipe.getTemplateIngredient()
			.map(ingredient -> List.of(ingredient.getItems()))
			.orElse(List.of(new ItemStack(Items.ENCHANTED_BOOK)));
		List<ItemStack> templates = new ArrayList<>(bases.size() * entry.maxLevel());
		for (ItemStack base : bases) {
			for (int level = 1; level <= entry.maxLevel(); level++) {
				ItemStack template = base.copyWithCount(1);
				ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
				mutable.set(entry.enchantment(), level);
				template.set(template.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS
					: DataComponents.ENCHANTMENTS, mutable.toImmutable());
				templates.add(template);
			}
		}
		return templates;
	}

	private record EnchantmentEntry(String idSegment, Holder.Reference<Enchantment> enchantment, int maxLevel) {}
}
