package com.nobodiiiii.createbiotech.content.buttercat.item;

import javax.annotation.Nullable;

import com.nobodiiiii.createbiotech.content.buttercat.item.ButterFoodProperties.Variant;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

public class ConfigurableButterSequencedAssemblyItem extends SequencedAssemblyItem {
	private final Variant variant;

	public ConfigurableButterSequencedAssemblyItem(Properties properties, Variant variant) {
		super(properties);
		this.variant = variant;
	}

	@Override
	public FoodProperties getFoodProperties(ItemStack stack, @Nullable LivingEntity entity) {
		return ButterFoodProperties.create(variant);
	}
}
