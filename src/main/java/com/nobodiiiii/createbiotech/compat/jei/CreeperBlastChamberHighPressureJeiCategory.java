package com.nobodiiiii.createbiotech.compat.jei;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.compat.jei.EmptyBackground;
import com.simibubi.create.compat.jei.ItemIcon;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.PressingCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.recipe.RecipeType;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class CreeperBlastChamberHighPressureJeiCategory extends PressingCategory {
	public static final RecipeType<PressingRecipe> TYPE =
		RecipeType.create(CreateBiotech.MOD_ID, "creeper_blast_chamber_high_pressure", PressingRecipe.class);

	public CreeperBlastChamberHighPressureJeiCategory() {
		super(new CreateRecipeCategory.Info<>(TYPE,
			Component.translatable("create_biotech.recipe.creeper_blast_chamber_high_pressure"),
			new EmptyBackground(177, 70),
			new ItemIcon(() -> new ItemStack(CBBlocks.CREEPER_BLAST_CHAMBER.get())),
			List::of,
			List.of()));
	}

	@Override
	public void draw(PressingRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
		GuiGraphics graphics, double mouseX, double mouseY) {
		AllGuiTextures.JEI_SHADOW.render(graphics, 61, 41);
		AllGuiTextures.JEI_LONG_ARROW.render(graphics, 52, 54);

		PoseStack poseStack = graphics.pose();
		poseStack.pushPose();
		poseStack.translate(getBackground().getWidth() / 2f - 1, 51, 100);
		poseStack.mulPose(Axis.XP.rotationDegrees(-15.5f));
		poseStack.mulPose(Axis.YP.rotationDegrees(22.5f));

		GuiGameElement.of(CBBlocks.CREEPER_BLAST_CHAMBER.get()
			.defaultBlockState())
			.lighting(AnimatedKinetics.DEFAULT_LIGHTING)
			.scale(20)
			.render(graphics);

		poseStack.popPose();
	}
}
