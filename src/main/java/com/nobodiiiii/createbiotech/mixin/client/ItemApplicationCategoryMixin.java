package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.compat.jei.SquidJeiRenderer;
import com.nobodiiiii.createbiotech.compat.jei.SquidPrinterJeiRecipes;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.client.gui.GuiGraphics;

@Pseudo
@Mixin(targets = "com.simibubi.create.compat.jei.category.ItemApplicationCategory", remap = false)
public abstract class ItemApplicationCategoryMixin {
	@Inject(method = "draw", at = @At("TAIL"), remap = false)
	private void createBiotech$drawSquid(ItemApplicationRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX,
		double mouseY, CallbackInfo ci) {
		if (!SquidPrinterJeiRecipes.isSquidPrinterItemApplication(recipe.getId()))
			return;
		SquidJeiRenderer.render(graphics, 88, 48, 1.0f);
	}
}
