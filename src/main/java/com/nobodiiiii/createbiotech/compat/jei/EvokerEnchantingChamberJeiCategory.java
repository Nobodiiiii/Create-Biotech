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

public class EvokerEnchantingChamberJeiCategory extends AbstractRecipeCategory<EvokerEnchantingChamberJeiRecipe> {

	public static final RecipeType<EvokerEnchantingChamberJeiRecipe> TYPE =
		RecipeType.create(CreateBiotech.MOD_ID, "evoker_enchanting_chamber", EvokerEnchantingChamberJeiRecipe.class);

	private static final int WIDTH = 177;
	private static final int HEIGHT = 70;
	private static final int INPUT_X = 27;
	private static final int INPUT_Y = 28;
	private static final int OUTPUT_X = 132;
	private static final int OUTPUT_Y = 28;
	private static final int ARROW_X = 52;
	private static final int ARROW_Y = 31;
	private static final int CATALYST_X = 76;
	private static final int CATALYST_Y = 4;

	public EvokerEnchantingChamberJeiCategory() {
		super(TYPE, Component.translatable("block.create_biotech.evoker_enchanting_chamber"),
			new ItemIconDrawable(new ItemStack(CBBlocks.EVOKER_ENCHANTING_CHAMBER.get())), WIDTH, HEIGHT);
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, EvokerEnchantingChamberJeiRecipe recipe, IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.CATALYST, CATALYST_X, CATALYST_Y)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(new ItemStack(CBBlocks.EVOKER_ENCHANTING_CHAMBER.get()));

		builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, INPUT_Y)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(recipe.inputCopy().copy());

		builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(recipe.outputBook().copy());
	}

	@Override
	public void draw(EvokerEnchantingChamberJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
		double mouseX, double mouseY) {
		AllGuiTextures.JEI_LONG_ARROW.render(graphics, ARROW_X, ARROW_Y);
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltip, EvokerEnchantingChamberJeiRecipe recipe,
		IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
		for (Component note : recipe.notes())
			tooltip.add(note.copy().withStyle(ChatFormatting.GRAY));
	}

	@Override
	public ResourceLocation getRegistryName(EvokerEnchantingChamberJeiRecipe recipe) {
		return recipe.id();
	}
}
