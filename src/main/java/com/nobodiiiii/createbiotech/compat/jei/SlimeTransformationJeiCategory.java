package com.nobodiiiii.createbiotech.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;

public class SlimeTransformationJeiCategory extends AbstractRecipeCategory<SlimeTransformationJeiRecipe> {
	public static final RecipeType<SlimeTransformationJeiRecipe> TYPE =
		RecipeType.create(CreateBiotech.MOD_ID, "slime_transformation", SlimeTransformationJeiRecipe.class);

	private static final int WIDTH = 177;
	private static final int HEIGHT = 60;
	private static final int INPUT_X = 27;
	private static final int INPUT_Y = 27;
	private static final int OUTPUT_X = 132;
	private static final int OUTPUT_Y = 27;
	private static final int ARROW_X = 52;
	private static final int ARROW_Y = 30;
	private static final int SLIME_X = 68;
	private static final int SLIME_Y = 2;
	private static final int HINT_X = 48;
	private static final int HINT_Y = 0;
	private static final int HINT_WIDTH = 82;
	private static final int HINT_HEIGHT = 28;

	private final SlimeEntityDrawable slimeDrawable;
	private final SlimeEntityDrawable magmaDrawable;

	public SlimeTransformationJeiCategory() {
		super(TYPE, Component.literal("史莱姆转化"),
			new SlimeEntityDrawable(16, 16, 10, 2, -0.75f, -0.6f, -1, EntityType.SLIME), WIDTH, HEIGHT);
		this.slimeDrawable = new SlimeEntityDrawable(32, 24, 18, 2, -0.75f, -0.6f, EntityType.SLIME);
		this.magmaDrawable = new SlimeEntityDrawable(32, 24, 18, 2, -0.75f, -0.6f, EntityType.MAGMA_CUBE);
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, SlimeTransformationJeiRecipe recipe, IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, INPUT_Y)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(recipe.input().copy());
		builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(recipe.output().copy());
	}

	@Override
	public void draw(SlimeTransformationJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
		double mouseX, double mouseY) {
		AllGuiTextures.JEI_LONG_ARROW.render(guiGraphics, ARROW_X, ARROW_Y);
		getDrawable(recipe).draw(guiGraphics, SLIME_X, SLIME_Y);
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltip, SlimeTransformationJeiRecipe recipe, IRecipeSlotsView recipeSlotsView,
		double mouseX, double mouseY) {
		if (mouseX >= HINT_X && mouseX < HINT_X + HINT_WIDTH && mouseY >= HINT_Y
			&& mouseY < HINT_Y + HINT_HEIGHT) {
			tooltip.add(recipe.dropConditionText());
		}
	}

	@Override
	public ResourceLocation getRegistryName(SlimeTransformationJeiRecipe recipe) {
		return recipe.id();
	}

	private SlimeEntityDrawable getDrawable(SlimeTransformationJeiRecipe recipe) {
		return recipe.entityType() == EntityType.MAGMA_CUBE ? magmaDrawable : slimeDrawable;
	}
}
