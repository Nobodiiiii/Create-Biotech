package com.nobodiiiii.createbiotech.compat.jei;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class ExperiencePumpJeiCategory extends AbstractRecipeCategory<ExperiencePumpJeiRecipe> {
	public static final RecipeType<ExperiencePumpJeiRecipe> TYPE =
		RecipeType.create(CreateBiotech.MOD_ID, "experience_pump", ExperiencePumpJeiRecipe.class);

	private static final int WIDTH = 177;
	private static final int HEIGHT = 62;

	public ExperiencePumpJeiCategory() {
		super(TYPE, Component.translatable("create_biotech.recipe.experience_pumping"),
			new ItemIconDrawable(new ItemStack(CBBlocks.EXPERIENCE_PUMP.get())), WIDTH, HEIGHT);
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, ExperiencePumpJeiRecipe recipe, IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.CATALYST, 78, 4)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(new ItemStack(CBBlocks.EXPERIENCE_PUMP.get()));

		builder.addSlot(RecipeIngredientRole.INPUT, 38, 34)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(recipe.input().copy());

		CreateRecipeCategory.addFluidSlot(builder, 121, 34, recipe.output().copy());
	}

	@Override
	public void draw(ExperiencePumpJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
		double mouseX, double mouseY) {
		AllGuiTextures.JEI_LONG_ARROW.render(graphics, 63, 37);
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltip, ExperiencePumpJeiRecipe recipe, IRecipeSlotsView recipeSlotsView,
		double mouseX, double mouseY) {
		for (Component note : recipe.notes())
			tooltip.add(note.copy()
				.withStyle(ChatFormatting.GRAY));
	}

	@Override
	public ResourceLocation getRegistryName(ExperiencePumpJeiRecipe recipe) {
		return recipe.id();
	}
}
