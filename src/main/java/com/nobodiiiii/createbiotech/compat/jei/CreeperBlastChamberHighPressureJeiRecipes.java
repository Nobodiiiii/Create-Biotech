package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;

public final class CreeperBlastChamberHighPressureJeiRecipes {
	private CreeperBlastChamberHighPressureJeiRecipes() {}

	public static List<PressingRecipe> create() {
		return List.of(
			coalToDiamond(),
			capturedEntityToHead("wither_skeleton", EntityType.WITHER_SKELETON, Items.WITHER_SKELETON_SKULL),
			capturedEntityToHead("skeleton", EntityType.SKELETON, Items.SKELETON_SKULL),
			capturedEntityToHead("zombie", EntityType.ZOMBIE, Items.ZOMBIE_HEAD),
			capturedEntityToHead("piglin", EntityType.PIGLIN, Items.PIGLIN_HEAD),
			capturedEntityToHead("creeper", EntityType.CREEPER, Items.CREEPER_HEAD),
			capturedEntityToHead("ender_dragon", EntityType.ENDER_DRAGON, Items.DRAGON_HEAD));
	}

	private static PressingRecipe coalToDiamond() {
		return builder("coal_to_diamond")
			.require(Items.COAL)
			.output(0.25f, Items.DIAMOND)
			.build();
	}

	private static PressingRecipe capturedEntityToHead(String name, EntityType<?> entityType, ItemLike output) {
		return builder(name + "_to_head")
			.require(createCapturedEntityBoxIngredient(entityType))
			.output(output)
			.build();
	}

	private static Ingredient createCapturedEntityBoxIngredient(EntityType<?> entityType) {
		return Ingredient.of(
			createCapturedEntityBoxDisplay(CBItems.CARDBOARD_BOX.get(), entityType),
			createCapturedEntityBoxDisplay(CBItems.LARGE_CARDBOARD_BOX.get(), entityType));
	}

	private static ItemStack createCapturedEntityBoxDisplay(ItemLike boxItem, EntityType<?> entityType) {
		ItemStack box = new ItemStack(boxItem);
		ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
		if (entityId == null)
			return box;

		CompoundTag tag = box.getOrCreateTag();
		CompoundTag entityData = new CompoundTag();
		entityData.putString("id", entityId.toString());
		tag.put("CapturedEntity", entityData);
		tag.putString("CapturedEntityDescId", entityType.getDescriptionId());
		return box;
	}

	private static ProcessingRecipeBuilder<PressingRecipe> builder(String path) {
		return new ProcessingRecipeBuilder<>(PressingRecipe::new,
			CreateBiotech.asResource("creeper_blast_chamber/high_pressure/" + path));
	}
}
