package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.experience.ExperienceConstants;
import com.nobodiiiii.createbiotech.content.experience.ExperienceFluidHelper;
import com.simibubi.create.AllItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ExperienceJeiRecipes {

	private ExperienceJeiRecipes() {
	}

	public static List<ExperiencePumpJeiRecipe> pump() {
		return List.of(
			new ExperiencePumpJeiRecipe(CreateBiotech.asResource("experience_pump/nugget_extraction"),
				new ItemStack(AllItems.EXP_NUGGET.get()), ExperienceFluidHelper.experienceStack(ExperienceConstants.xpPerNugget()),
				List.of(Component.translatable("create_biotech.jei.experience_pump.note.nuggets"))));
	}
}
