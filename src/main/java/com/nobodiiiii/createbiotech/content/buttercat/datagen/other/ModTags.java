package com.nobodiiiii.createbiotech.content.buttercat.datagen.other;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;

public class ModTags {
    public static final TagKey<Item> BUTTER = ItemTags.create(new ResourceLocation("c", "butter"));
    public static final TagKey<Item> BREAD = ItemTags.create(new ResourceLocation("c", "bread"));
    public static final TagKey<Fluid> CREAM = FluidTags.create(new ResourceLocation("c", "cream"));
    public static final TagKey<Item> FOOD_BUTTER = ItemTags.create(new ResourceLocation("c", "foods/butter"));
    public static final TagKey<Item> FOOD_BREAD = ItemTags.create(new ResourceLocation("c", "foods/bread"));

    public static final TagKey<Item> WHEAT = ItemTags.create(new ResourceLocation("c", "wheat"));
    public static final TagKey<Item> FOOD_WHEAT = ItemTags.create(new ResourceLocation("c", "foods/wheat"));


    public static Ingredient getBreads () {
        return Ingredient.fromValues(
                java.util.stream.Stream.of(
                        new Ingredient.TagValue(BREAD),
                        new Ingredient.TagValue(FOOD_BREAD),
                        new Ingredient.TagValue(WHEAT),
                        new Ingredient.TagValue(FOOD_WHEAT),
                        new Ingredient.ItemValue(new ItemStack(Items.BREAD))
                ));
    }
    public static Ingredient getButters () {
        return Ingredient.fromValues(
                java.util.stream.Stream.of(
                        new Ingredient.TagValue(BUTTER),
                        new Ingredient.TagValue(FOOD_BUTTER)
                ));
    }
    public static boolean matchesIngredient(ItemStack stack, Ingredient ingredient){
        return ingredient.test(stack);
    }
}

