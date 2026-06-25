package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberHighPressureRecipe;
import com.nobodiiiii.createbiotech.content.creeperblastchamber.CreeperBlastChamberHighPressureRecipe.ResultCountRange;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.compat.jei.EmptyBackground;
import com.simibubi.create.compat.jei.ItemIcon;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class CreeperBlastChamberHighPressureJeiCategory
	extends CreateRecipeCategory<CreeperBlastChamberHighPressureRecipe> {
	private static final int OUTPUT_X = 132;
	private static final int OUTPUT_Y = 51;
	public static final RecipeType<CreeperBlastChamberHighPressureRecipe> TYPE =
		RecipeType.create(CreateBiotech.MOD_ID, "creeper_blast_chamber_high_pressure",
			CreeperBlastChamberHighPressureRecipe.class);
	private static final HighPressureCreeperDrawable HIGH_PRESSURE_CREEPER =
		new HighPressureCreeperDrawable(46, 42, 1.2f, 1f / 1.8f, 24);

	public CreeperBlastChamberHighPressureJeiCategory() {
		super(new CreateRecipeCategory.Info<>(TYPE,
			Component.translatable("create_biotech.recipe.creeper_blast_chamber_high_pressure"),
			new EmptyBackground(177, 70),
			new ItemIcon(() -> new ItemStack(CBBlocks.CREEPER_BLAST_CHAMBER.get())),
			List::of,
			List.of()));
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, CreeperBlastChamberHighPressureRecipe recipe,
		IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.CATALYST, 51, 5)
			.setBackground(getRenderedSlot(), -1, -1)
			.addItemStack(new ItemStack(CBBlocks.CREEPER_BLAST_CHAMBER.get()));

		builder.addSlot(RecipeIngredientRole.INPUT, 27, 51)
			.setBackground(getRenderedSlot(), -1, -1)
			.addIngredients(recipe.getIngredients().get(0));

		List<ProcessingOutput> results = recipe.getRollableResults();
		if (recipe.hasExclusiveResults()) {
			builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
				.setBackground(hasGuaranteedExclusiveResult(results) ? getRenderedSlot() : getRenderedSlot(0), -1, -1)
				.addItemStacks(results.stream()
					.map(ProcessingOutput::getStack)
					.map(ItemStack::copy)
					.toList())
				.addRichTooltipCallback((view, tooltip) -> addExclusiveResultTooltip(recipe, results, view, tooltip));
			return;
		}

		boolean single = results.size() == 1;
		for (int i = 0; i < results.size(); i++) {
			ProcessingOutput output = results.get(i);
			int outputIndex = i;
			int xOffset = i % 2 == 0 ? 0 : 19;
			int yOffset = (i / 2) * -19;
			builder.addSlot(RecipeIngredientRole.OUTPUT, single ? OUTPUT_X : OUTPUT_X + xOffset, OUTPUT_Y + yOffset)
				.setBackground(getRenderedSlot(output), -1, -1)
				.addItemStack(output.getStack())
				.addRichTooltipCallback((view, tooltip) -> {
					addStochasticTooltip(output).onRichTooltip(view, tooltip);
					ResultCountRange countRange = recipe.getResultCountRange(outputIndex);
					if (countRange != null) {
						tooltip.add(Component.translatable("create_biotech.recipe.processing.random_amount",
							countRange.min(), countRange.max())
							.withStyle(ChatFormatting.GOLD));
					}
				});
		}
	}

	@Override
	public void draw(CreeperBlastChamberHighPressureRecipe recipe, IRecipeSlotsView recipeSlotsView,
		GuiGraphics graphics, double mouseX, double mouseY) {
		AllGuiTextures.JEI_SHADOW.render(graphics, 62, 57);
		AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 126,
			29 + (!recipe.hasExclusiveResults() && recipe.getRollableResults().size() > 2 ? -19 : 0));
		HIGH_PRESSURE_CREEPER.draw(graphics, getBackground().getWidth() / 2 - 13, 22);
	}

	private static boolean hasGuaranteedExclusiveResult(List<ProcessingOutput> results) {
		return results.size() == 1 && results.get(0)
			.getChance() == 1;
	}

	private static void addExclusiveResultTooltip(CreeperBlastChamberHighPressureRecipe recipe,
		List<ProcessingOutput> results, IRecipeSlotView view, ITooltipBuilder tooltip) {
		ItemStack displayed = view.getDisplayedItemStack()
			.orElse(ItemStack.EMPTY);
		if (displayed.isEmpty())
			return;

		int outputIndex = findDisplayedExclusiveResultIndex(results, displayed);
		if (outputIndex < 0)
			return;

		ProcessingOutput output = results.get(outputIndex);
		addStochasticTooltip(output).onRichTooltip(view, tooltip);
		ResultCountRange countRange = recipe.getResultCountRange(outputIndex);
		if (countRange != null) {
			tooltip.add(Component.translatable("create_biotech.recipe.processing.random_amount",
				countRange.min(), countRange.max())
				.withStyle(ChatFormatting.GOLD));
		}
	}

	private static int findDisplayedExclusiveResultIndex(List<ProcessingOutput> results, ItemStack displayed) {
		for (int i = 0; i < results.size(); i++) {
			ItemStack candidate = results.get(i)
				.getStack();
			if (ItemStack.isSameItemSameTags(displayed, candidate) && displayed.getCount() == candidate.getCount())
				return i;
		}
		for (int i = 0; i < results.size(); i++) {
			if (ItemStack.isSameItemSameTags(displayed, results.get(i)
				.getStack()))
				return i;
		}
		return -1;
	}
}
