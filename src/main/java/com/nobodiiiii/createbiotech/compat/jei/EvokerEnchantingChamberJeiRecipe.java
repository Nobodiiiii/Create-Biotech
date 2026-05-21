package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record EvokerEnchantingChamberJeiRecipe(ResourceLocation id, List<ItemStack> inputCopies,
	List<ItemStack> outputBooks, List<Integer> xpCosts) {
}
