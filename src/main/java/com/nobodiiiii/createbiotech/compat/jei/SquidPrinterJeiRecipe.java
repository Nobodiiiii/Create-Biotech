package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public record SquidPrinterJeiRecipe(ResourceLocation id, ItemStack inputBook, ItemStack templateBook,
	ItemStack outputCopy, FluidStack water, List<Component> notes) {
}
