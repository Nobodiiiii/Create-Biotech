package com.nobodiiiii.createbiotech.content.squidprinter;

import com.nobodiiiii.createbiotech.registry.CBRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

public class SquidPrinterRecipe extends StandardProcessingRecipe<RecipeWrapper> {

	private static final IRecipeTypeInfo TYPE_INFO = new IRecipeTypeInfo() {
		@Override
		public ResourceLocation getId() {
			return CBRecipeTypes.SQUID_PRINTER_TYPE.getId();
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T extends net.minecraft.world.item.crafting.RecipeSerializer<?>> T getSerializer() {
			return (T) CBRecipeTypes.SQUID_PRINTER_SERIALIZER.get();
		}

		@Override
		@SuppressWarnings("unchecked")
		public <I extends net.minecraft.world.item.crafting.RecipeInput,
			R extends net.minecraft.world.item.crafting.Recipe<I>>
			net.minecraft.world.item.crafting.RecipeType<R> getType() {
			return (net.minecraft.world.item.crafting.RecipeType<R>) CBRecipeTypes.SQUID_PRINTER_TYPE.get();
		}
	};

	public SquidPrinterRecipe(ProcessingRecipeParams params) {
		super(TYPE_INFO, params);
	}

	@Override
	public boolean matches(RecipeWrapper inv, Level level) {
		return !ingredients.isEmpty() && ingredients.get(0)
			.test(inv.getItem(0));
	}

	@Override
	protected int getMaxInputCount() {
		return 1;
	}

	@Override
	protected int getMaxOutputCount() {
		return 1;
	}

	@Override
	protected int getMaxFluidInputCount() {
		return 1;
	}

	@Override
	protected boolean canSpecifyDuration() {
		return true;
	}

	public SizedFluidIngredient getRequiredFluid() {
		if (fluidIngredients.isEmpty())
			throw new IllegalStateException("Squid Printer recipe has no fluid ingredient");
		return fluidIngredients.get(0);
	}

	public boolean matchesTemplate(ItemStack template) {
		return EnchantmentBookCopyItem.hasCopyableEnchantments(template);
	}

	public int getTicksPerLevel() {
		return Math.max(1, getProcessingDuration());
	}

	public int getWaterPerLevel() {
		return Math.max(1, getRequiredFluid().amount());
	}

	public int getTemplateLevelTotal(ItemStack template) {
		return Math.max(1, EnchantmentBookCopyItem.sumCopySourceEnchantmentLevels(template));
	}

	public int getRequiredTicks(ItemStack template) {
		return getTicksPerLevel() * getTemplateLevelTotal(template);
	}

	public int getRequiredWater(ItemStack template) {
		return getWaterPerLevel() * getTemplateLevelTotal(template);
	}

	public ItemStack createResult(ItemStack template) {
		if (getRollableResults().isEmpty())
			return ItemStack.EMPTY;
		return EnchantmentBookCopyItem.fromTemplate(template,
			getRollableResults().getFirst().getStack());
	}
}
