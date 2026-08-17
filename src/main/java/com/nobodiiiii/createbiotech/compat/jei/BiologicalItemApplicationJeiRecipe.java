package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBEntityTypes;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.AllBlocks;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record BiologicalItemApplicationJeiRecipe(ResourceLocation id, ItemStack input, ItemStack heldItem,
	ItemStack output, EntityType<? extends Chicken> displayedEntityType, boolean voicePackOutput) {

	public static List<BiologicalItemApplicationJeiRecipe> createRecipes() {
		return List.of(chickenToDingDongChicken(), installVoicePack());
	}

	private static BiologicalItemApplicationJeiRecipe chickenToDingDongChicken() {
		return new BiologicalItemApplicationJeiRecipe(
			CreateBiotech.asResource("biological_item_application/ding_dong_chicken"),
			new ItemStack(CBItems.CHICKEN.get()), AllBlocks.DESK_BELL.asStack(),
			new ItemStack(CBItems.DING_DONG_CHICKEN.get()), EntityType.CHICKEN, false);
	}

	private static BiologicalItemApplicationJeiRecipe installVoicePack() {
		return new BiologicalItemApplicationJeiRecipe(
			CreateBiotech.asResource("biological_item_application/ding_dong_chicken_voice_pack"),
			new ItemStack(CBItems.DING_DONG_CHICKEN.get()), new ItemStack(Items.NOTE_BLOCK),
			new ItemStack(CBItems.DING_DONG_CHICKEN.get()), CBEntityTypes.DING_DONG_CHICKEN.get(), true);
	}
}
