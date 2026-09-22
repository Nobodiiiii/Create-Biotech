package com.nobodiiiii.createbiotech.compat.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;
import java.util.Optional;

public record SquidPrinterJeiRecipe(ResourceLocation id, List<ItemStack> inputItems,
	Optional<SizedFluidIngredient> requiredFluid,
	List<ItemStack> templateBooks, List<ItemStack> outputCopies) {
}
