package com.nobodiiiii.createbiotech.compat.jei;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.registry.CBItems;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Uses the information category's slot and scroll-box layout with its own surgery catalyst. */
public final class BionicTraitJeiCategory extends AbstractRecipeCategory<BionicTrait> {
	public static final RecipeType<BionicTrait> TYPE =
		RecipeType.create(CreateBiotech.MOD_ID, "bionic_traits", BionicTrait.class);
	private static final int WIDTH = 170;
	private static final int HEIGHT = 125;
	private static final int TEXT_Y = 22;
	private static final int DONORS_Y = HEIGHT - BionicTraitDonorWidget.HEIGHT;

	public BionicTraitJeiCategory(IGuiHelper guiHelper) {
		super(TYPE, Component.translatable("create_biotech.jei.traits"),
			guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(CBItems.SURGERY_GUIDE.get())),
			WIDTH, HEIGHT);
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, BionicTrait trait, IFocusGroup focuses) {
		builder.addInputSlot((WIDTH - 16) / 2, 1)
			.setStandardSlotBackground()
			.addIngredient(BionicTraitJeiIngredient.TYPE, trait);
		// Both recipe and use lookups on the virtual entry lead to its information page.
		builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
			.addIngredient(BionicTraitJeiIngredient.TYPE, trait);
		builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
			.addItemStack(new ItemStack(CBItems.SURGERY_GUIDE.get()));
	}

	@Override
	public void createRecipeExtras(IRecipeExtrasBuilder builder, BionicTrait trait, IFocusGroup focuses) {
		builder.addScrollBoxWidget(WIDTH, DONORS_Y - TEXT_Y - 6, 0, TEXT_Y)
			.setContents(BionicTraitJeiText.description(trait));
		BionicTraitDonorWidget donors = new BionicTraitDonorWidget(trait, DONORS_Y);
		builder.addWidget(donors);
		builder.addInputHandler(donors);
	}

	@Override
	public ResourceLocation getRegistryName(BionicTrait trait) {
		return BionicTraitJeiIngredient.id(trait);
	}
}
