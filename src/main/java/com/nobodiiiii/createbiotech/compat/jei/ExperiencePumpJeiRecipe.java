package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public record ExperiencePumpJeiRecipe(ResourceLocation id, ItemStack input, FluidStack output, List<Component> notes) {
}
