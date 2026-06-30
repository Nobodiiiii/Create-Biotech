package com.nobodiiiii.createbiotech.compat.jei;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.experience.ExperienceFluidHelper;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class EvokerEnchantingChamberJeiCategory extends AbstractRecipeCategory<EvokerEnchantingChamberJeiRecipe> {

	public static final RecipeType<EvokerEnchantingChamberJeiRecipe> TYPE =
		RecipeType.create(CreateBiotech.MOD_ID, "evoker_enchanting_chamber", EvokerEnchantingChamberJeiRecipe.class);

	private static final String INPUT_SLOT_NAME = "input";
	private static final String OUTPUT_SLOT_NAME = "output";

	private static final int WIDTH = 177;
	private static final int HEIGHT = 70;
	private static final int INPUT_X = 27;
	private static final int INPUT_Y = 51;
	private static final int OUTPUT_X = 132;
	private static final int OUTPUT_Y = 51;
	private static final int FLUID_X = 51;
	private static final int FLUID_Y = 5;

	private final AnimatedEvokerEnchanting enchanting = new AnimatedEvokerEnchanting();

	public EvokerEnchantingChamberJeiCategory() {
		super(TYPE, Component.translatable("block.create_biotech.evoker_enchanting_chamber"),
			new ItemIconDrawable(new ItemStack(CBBlocks.EVOKER_ENCHANTING_CHAMBER.get())), WIDTH, HEIGHT);
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, EvokerEnchantingChamberJeiRecipe recipe, IFocusGroup focuses) {
		IRecipeSlotBuilder fluidSlot = CreateRecipeCategory.addFluidSlot(builder, FLUID_X, FLUID_Y,
				RecipeIngredientRole.INPUT)
			.addIngredients(ForgeTypes.FLUID_STACK, recipe.fluidAmounts()
				.stream()
				.map(ExperienceFluidHelper::experienceStack)
				.filter(stack -> !stack.isEmpty())
				.toList());

		IRecipeSlotBuilder inputSlot = builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, INPUT_Y)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.setSlotName(INPUT_SLOT_NAME)
			.addItemStacks(recipe.inputCopies());

		IRecipeSlotBuilder outputSlot = builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.setSlotName(OUTPUT_SLOT_NAME)
			.addItemStacks(recipe.outputBooks());

		if (recipe.inputCopies().size() == recipe.outputBooks().size() && recipe.inputCopies().size() > 1)
			builder.createFocusLink(inputSlot, outputSlot, fluidSlot);
	}

	@Override
	public void draw(EvokerEnchantingChamberJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
		double mouseX, double mouseY) {
		int level = displayedLevel(recipeSlotsView, recipe);
		ItemStack currentInput = recipe.inputCopies().get(level);
		ItemStack currentOutput = recipe.outputBooks().get(level);

		AllGuiTextures.JEI_SHADOW.render(graphics, 62, 57);
		AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 126, 29);
		enchanting.withItems(currentInput, currentOutput)
			.draw(graphics, WIDTH / 2 - 13, 22);
	}

	private static int displayedLevel(IRecipeSlotsView slotsView, EvokerEnchantingChamberJeiRecipe recipe) {
		ItemStack shown = slotsView.findSlotByName(OUTPUT_SLOT_NAME)
			.flatMap(IRecipeSlotView::getDisplayedItemStack)
			.orElse(ItemStack.EMPTY);
		if (shown.isEmpty())
			return recipe.outputBooks().size() - 1;
		for (int i = 0; i < recipe.outputBooks().size(); i++) {
			if (ItemStack.isSameItemSameTags(shown, recipe.outputBooks().get(i)))
				return i;
		}
		return recipe.outputBooks().size() - 1;
	}

	@Override
	public ResourceLocation getRegistryName(EvokerEnchantingChamberJeiRecipe recipe) {
		return recipe.id();
	}
}
