package com.nobodiiiii.createbiotech.compat.jei;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
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

public class SquidPrinterJeiCategory extends AbstractRecipeCategory<SquidPrinterJeiRecipe> {

	public static final RecipeType<SquidPrinterJeiRecipe> TYPE =
		RecipeType.create(CreateBiotech.MOD_ID, "squid_printer", SquidPrinterJeiRecipe.class);

	private static final int WIDTH = 177;
	private static final int HEIGHT = 70;
	private static final int TEMPLATE_SLOT_X = 51;
	private static final int TEMPLATE_SLOT_Y = 5;
	private static final Component NOT_CONSUMED = Component.translatable("create.recipe.deploying.not_consumed");

	private final AnimatedSquidSpout spout = new AnimatedSquidSpout();

	public SquidPrinterJeiCategory() {
		super(TYPE, Component.translatable("create_biotech.recipe.printing"),
			new ItemIconDrawable(new ItemStack(CBBlocks.SQUID_PRINTER.get())), WIDTH, HEIGHT);
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, SquidPrinterJeiRecipe recipe, IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.INPUT, 27, 51)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(recipe.inputBook().copy());

		IRecipeSlotBuilder templateSlot = builder.addSlot(RecipeIngredientRole.INPUT, TEMPLATE_SLOT_X, TEMPLATE_SLOT_Y)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStacks(recipe.templateBooks());
		templateSlot.addRichTooltipCallback((view, tooltip) -> tooltip.add(
			NOT_CONSUMED.copy().withStyle(ChatFormatting.GOLD)));

		builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 51)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStacks(recipe.outputCopies());
	}

	@Override
	public void draw(SquidPrinterJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
		double mouseX, double mouseY) {
		AllGuiTextures.JEI_SHADOW.render(graphics, 62, 57);
		AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 126, 29);
		spout.withFluids(recipe.requiredFluid()
			.getMatchingFluidStacks())
			.draw(graphics, WIDTH / 2 - 13, 22);
	}

	@Override
	public ResourceLocation getRegistryName(SquidPrinterJeiRecipe recipe) {
		return recipe.id();
	}
}
