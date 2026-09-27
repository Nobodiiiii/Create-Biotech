package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.registry.CBItems;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Trait entry with its name beside it, the standard field template below, then the donor grid. */
public final class BionicTraitJeiCategory extends AbstractRecipeCategory<BionicTrait> {
	public static final RecipeType<BionicTrait> TYPE =
		RecipeType.create(CreateBiotech.MOD_ID, "bionic_traits", BionicTrait.class);
	private static final int WIDTH = 170;
	private static final int HEIGHT = 125;
	private static final int SLOT_SIZE = 18;
	private static final int NAME_X = SLOT_SIZE + 4;
	private static final int TEXT_Y = 22;
	private static final int DONORS_Y = HEIGHT - BionicTraitDonorWidget.HEIGHT;
	private static final int TEXT_HEIGHT = DONORS_Y - TEXT_Y - 6;
	/** Matches the line spacing used by JEI's text and scroll-box widgets. */
	private static final int LINE_SPACING = 2;
	private final IDrawable slotBackground;

	public BionicTraitJeiCategory(IGuiHelper guiHelper) {
		super(TYPE, Component.translatable("create_biotech.jei.traits"),
			guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(CBItems.SURGERY_GUIDE.get())),
			WIDTH, HEIGHT);
		slotBackground = guiHelper.getSlotDrawable();
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, BionicTrait trait, IFocusGroup focuses) {
		builder.addInputSlot(1, 1)
			.setStandardSlotBackground()
			.addIngredient(BionicTraitJeiIngredient.TYPE, trait);
		// Both recipe and use lookups on the virtual entry lead to its information page.
		builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
			.addIngredient(BionicTraitJeiIngredient.TYPE, trait);
		builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
			.addItemStack(new ItemStack(CBItems.SURGERY_GUIDE.get()));
		// The server fills these display-only slots as its donor scan progresses.
		for (int slot = 0; slot < BionicTraitDonorWidget.PAGE_SIZE; slot++)
			builder.addSlot(RecipeIngredientRole.RENDER_ONLY).setStandardSlotBackground();
	}

	@Override
	public void createRecipeExtras(IRecipeExtrasBuilder builder, BionicTrait trait, IFocusGroup focuses) {
		builder.addText(BionicTraitJeiIngredient.name(trait).copy().withStyle(ChatFormatting.BOLD),
				WIDTH - NAME_X, SLOT_SIZE)
			.setPosition(NAME_X, 0)
			.setTextAlignment(VerticalAlignment.CENTER);
		List<FormattedText> text = BionicTraitJeiText.description(trait);
		// A permanent scrollbar is noise for the short template; keep it only for overflowing text.
		if (fits(text))
			builder.addText(text, WIDTH, TEXT_HEIGHT).setPosition(0, TEXT_Y);
		else
			builder.addScrollBoxWidget(WIDTH, TEXT_HEIGHT, 0, TEXT_Y).setContents(text);
		var slots = builder.getRecipeSlots().getSlots(RecipeIngredientRole.RENDER_ONLY);
		BionicTraitDonorWidget donors = new BionicTraitDonorWidget(trait, DONORS_Y, slots, slotBackground);
		builder.addSlottedWidget(donors, slots);
		builder.addInputHandler(donors);
	}

	@Override
	public ResourceLocation getRegistryName(BionicTrait trait) {
		return BionicTraitJeiIngredient.id(trait);
	}

	/** Wraps exactly like JEI's text widgets at the full category width. */
	private static boolean fits(List<FormattedText> text) {
		var font = Minecraft.getInstance().font;
		int lines = text.stream().mapToInt(line -> font.getSplitter().splitLines(line, WIDTH, Style.EMPTY).size()).sum();
		return lines * (font.lineHeight + LINE_SPACING) - LINE_SPACING <= TEXT_HEIGHT;
	}
}
