package com.nobodiiiii.createbiotech.compat.jei;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.AllItems;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class BiologicalItemApplicationJeiCategory
	extends AbstractRecipeCategory<BiologicalItemApplicationJeiRecipe> {

	public static final RecipeType<BiologicalItemApplicationJeiRecipe> TYPE =
		RecipeType.create(CreateBiotech.MOD_ID, "biological_item_application", BiologicalItemApplicationJeiRecipe.class);

	private static final int WIDTH = 177;
	private static final int HEIGHT = 60;
	private static final int ENTITY_X = 74;
	private static final int ENTITY_Y = 51;

	private final AnimatedBiologicalItemApplication entity = new AnimatedBiologicalItemApplication();

	public BiologicalItemApplicationJeiCategory() {
		super(TYPE, Component.translatable("create_biotech.recipe.biological_item_application"),
			new ItemIconDrawable(AllItems.BRASS_HAND.asStack()), WIDTH, HEIGHT);
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, BiologicalItemApplicationJeiRecipe recipe,
		IFocusGroup focuses) {
		builder.addSlot(RecipeIngredientRole.INPUT, 27, 38)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(recipe.input().copy());

		builder.addSlot(RecipeIngredientRole.INPUT, 51, 5)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(recipe.heldItem().copy());

		IRecipeSlotBuilder outputSlot = builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 38)
			.setBackground(CreateRecipeCategory.getRenderedSlot(), -1, -1)
			.addItemStack(recipe.output().copy());
		if (recipe.voicePackOutput()) {
			outputSlot.addRichTooltipCallback((view, tooltip) -> {
				tooltip.add(CommonComponents.EMPTY);
				tooltip.add(Component.translatable("item.create_biotech.sonic_dog_cannon.upgrades")
					.withStyle(ChatFormatting.DARK_GRAY));
				tooltip.add(CommonComponents.space()
					.append(Component.translatable("item.create_biotech.sonic_dog_cannon.upgrade.voice_pack")
						.withStyle(ChatFormatting.GRAY)));
			});
		}
	}

	@Override
	public void draw(BiologicalItemApplicationJeiRecipe recipe, IRecipeSlotsView recipeSlotsView,
		GuiGraphics graphics, double mouseX, double mouseY) {
		AllGuiTextures.JEI_SHADOW.render(graphics, 62, 47);
		AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 74, 10);

		entity.withEntityType(recipe.displayedEntityType())
			.draw(graphics, ENTITY_X, ENTITY_Y);
	}

	@Override
	public ResourceLocation getRegistryName(BiologicalItemApplicationJeiRecipe recipe) {
		return recipe.id();
	}
}
