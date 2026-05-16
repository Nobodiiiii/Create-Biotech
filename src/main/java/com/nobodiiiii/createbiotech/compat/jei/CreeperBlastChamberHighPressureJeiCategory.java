package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.compat.jei.EmptyBackground;
import com.simibubi.create.compat.jei.ItemIcon;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.PressingCategory;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class CreeperBlastChamberHighPressureJeiCategory extends PressingCategory {
	public static final RecipeType<PressingRecipe> TYPE =
		RecipeType.create(CreateBiotech.MOD_ID, "creeper_blast_chamber_high_pressure", PressingRecipe.class);
	private static final HighPressureCreeperDrawable HIGH_PRESSURE_CREEPER =
		new HighPressureCreeperDrawable(46, 42, 18, -0.5625f, 0.2f, 8, 1.2f, 1f / 1.8f, 24);

	public CreeperBlastChamberHighPressureJeiCategory() {
		super(new CreateRecipeCategory.Info<>(TYPE,
			Component.translatable("create_biotech.recipe.creeper_blast_chamber_high_pressure"),
			new EmptyBackground(177, 70),
			new ItemIcon(() -> new ItemStack(CBBlocks.CREEPER_BLAST_CHAMBER.get())),
			List::of,
			List.of()));
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, PressingRecipe recipe, IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.INPUT, 36, 51)
			.setBackground(getRenderedSlot(), -1, -1)
			.addIngredients(recipe.getIngredients().get(0));

		List<ProcessingOutput> results = recipe.getRollableResults();
		int startY = 51 - (results.size() - 1) * 19 / 2;
		for (int i = 0; i < results.size(); i++) {
			ProcessingOutput output = results.get(i);
			builder.addSlot(RecipeIngredientRole.OUTPUT, 142, startY + i * 19)
				.setBackground(getRenderedSlot(output), -1, -1)
				.addItemStack(output.getStack())
				.addRichTooltipCallback(addStochasticTooltip(output));
		}
	}

	@Override
	public void draw(PressingRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
		GuiGraphics graphics, double mouseX, double mouseY) {
		AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 136, 32);
		AllGuiTextures.JEI_SHADOW.render(graphics, 81, 68);
		HIGH_PRESSURE_CREEPER.draw(graphics, getBackground().getWidth() / 2 - HIGH_PRESSURE_CREEPER.getWidth() / 2 + 3,
			8);
	}
}
