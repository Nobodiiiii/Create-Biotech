package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedSpout;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.forge.ForgeTypes;
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

public class SquidPrinterJeiCategory extends AbstractRecipeCategory<SquidPrinterJeiRecipe> {

	public static final RecipeType<SquidPrinterJeiRecipe> TYPE =
		RecipeType.create(CreateBiotech.MOD_ID, "squid_printer", SquidPrinterJeiRecipe.class);

	private static final int WIDTH = 177;
	private static final int HEIGHT = 85;

	private final AnimatedSpout spout = new AnimatedSpout();

	public SquidPrinterJeiCategory() {
		super(TYPE, Component.translatable("block.create_biotech.squid_printer"),
			new ItemIconDrawable(new ItemStack(CBBlocks.SQUID_PRINTER.get())), WIDTH, HEIGHT);
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, SquidPrinterJeiRecipe recipe, IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.CATALYST, 6, 6)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(new ItemStack(CBBlocks.SQUID_PRINTER.get()));

		builder.addSlot(RecipeIngredientRole.INPUT, 27, 51)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(recipe.inputBook().copy());

		builder.addSlot(RecipeIngredientRole.INPUT, 27, 32)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.setFluidRenderer(1, false, 16, 16)
			.addIngredient(ForgeTypes.FLUID_STACK, recipe.water().copy());

		builder.addSlot(RecipeIngredientRole.CATALYST, 152, 6)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(recipe.templateBook().copy());

		builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 51)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(recipe.outputCopy().copy());
	}

	@Override
	public void draw(SquidPrinterJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
		double mouseX, double mouseY) {
		AllGuiTextures.JEI_SHADOW.render(graphics, 62, 57);
		AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 126, 29);
		PoseStack ms = graphics.pose();
		ms.pushPose();
		spout.withFluids(List.of(recipe.water())).draw(graphics, WIDTH / 2 - 13, 22);
		ms.popPose();
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltip, SquidPrinterJeiRecipe recipe, IRecipeSlotsView recipeSlotsView,
		double mouseX, double mouseY) {
		if (mouseY < 22 || mouseY > 60)
			return;
		for (Component note : recipe.notes()) {
			tooltip.add(note.copy().withStyle(ChatFormatting.GRAY));
		}
	}

	@Override
	public ResourceLocation getRegistryName(SquidPrinterJeiRecipe recipe) {
		return recipe.id();
	}

	public static ItemStack copyItemForCatalyst() {
		return new ItemStack(CBItems.SQUID_PRINTER.get());
	}
}
