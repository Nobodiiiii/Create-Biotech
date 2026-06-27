package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.nobodiiiii.createbiotech.compat.jei.CapturedEntityBoxJeiRenderer;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

@Pseudo
@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false)
public abstract class JeiRecipeLayoutMixin {
	@Redirect(method = "drawRecipe", at = @At(value = "INVOKE",
		target = "Lmezz/jei/api/gui/ingredient/IRecipeSlotDrawable;draw(Lnet/minecraft/client/gui/GuiGraphics;)V"),
		remap = false)
	private void createBiotech$drawSlotWithHoverContext(IRecipeSlotDrawable slot, GuiGraphics slotGraphics,
		GuiGraphics methodGraphics, int mouseX, int mouseY) {
		Rect2i recipeArea = ((IRecipeLayoutDrawable<?>) (Object) this).getRect();
		CapturedEntityBoxJeiRenderer.drawSlotWithHoverContext(slot, slotGraphics, mouseX - recipeArea.getX(),
			mouseY - recipeArea.getY());
	}
}
