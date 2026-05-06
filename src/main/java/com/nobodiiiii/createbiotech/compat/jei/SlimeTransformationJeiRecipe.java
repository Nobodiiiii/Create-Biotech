package com.nobodiiiii.createbiotech.compat.jei;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllItems;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SlimeTransformationJeiRecipe(ResourceLocation id, ItemStack input, ItemStack output) {

	public static SlimeTransformationJeiRecipe beltToSlimeBelt() {
		return new SlimeTransformationJeiRecipe(CreateBiotech.asResource("slime_transformation/belt_connector"),
			AllItems.BELT_CONNECTOR.asStack(), new ItemStack(CBItems.SLIME_BELT_CONNECTOR.get()));
	}
}
