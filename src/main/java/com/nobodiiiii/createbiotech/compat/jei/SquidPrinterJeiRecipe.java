package com.nobodiiiii.createbiotech.compat.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;

public record SquidPrinterJeiRecipe(ResourceLocation id, List<ItemStack> inputBooks, SizedFluidIngredient requiredFluid,
	List<ItemStack> templateBooks, List<ItemStack> outputCopies) {
}
