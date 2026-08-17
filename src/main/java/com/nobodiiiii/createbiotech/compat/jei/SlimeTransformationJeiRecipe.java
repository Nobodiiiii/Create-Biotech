package com.nobodiiiii.createbiotech.compat.jei;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllItems;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;

public record SlimeTransformationJeiRecipe(ResourceLocation id, ItemStack input, ItemStack output,
	EntityType<? extends Slime> entityType, Component dropConditionText) {

	public static SlimeTransformationJeiRecipe beltToSlimeBelt() {
		return new SlimeTransformationJeiRecipe(CreateBiotech.asResource("slime_transformation/belt_connector"),
			AllItems.BELT_CONNECTOR.asStack(), new ItemStack(CBItems.SLIME_BELT_CONNECTOR.get()), EntityType.SLIME,
			Component.translatable("create_biotech.jei.biological_transformation.slime"));
	}

	public static SlimeTransformationJeiRecipe beltToMagmaBelt() {
		return new SlimeTransformationJeiRecipe(CreateBiotech.asResource("slime_transformation/magma_belt_connector"),
			AllItems.BELT_CONNECTOR.asStack(), new ItemStack(CBItems.MAGMA_BELT_CONNECTOR.get()),
			EntityType.MAGMA_CUBE,
			Component.translatable("create_biotech.jei.biological_transformation.magma_cube"));
	}
}
