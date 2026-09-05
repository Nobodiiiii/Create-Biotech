package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nobodiiiii.createbiotech.compat.jei.CapturedEntityBoxJeiRenderer;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import net.minecraft.client.gui.GuiGraphics;

@Pseudo
@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false)
public abstract class JeiRecipeLayoutMixin {

	@WrapOperation(
		method = "drawRecipe(Lnet/minecraft/client/gui/GuiGraphics;II)V",
		at = @At(value = "INVOKE",
			target = "Lmezz/jei/api/gui/ingredient/IRecipeSlotDrawable;draw(Lnet/minecraft/client/gui/GuiGraphics;Z)V",
			remap = true),
		remap = true,
		require = 1,
		expect = 1)
	private void createBiotech$drawSlotWithHoverContext(IRecipeSlotDrawable slot, GuiGraphics slotGraphics,
		boolean hovered, Operation<Void> original) {
		CapturedEntityBoxJeiRenderer.beginSlotDraw(slot, hovered);
		try {
			original.call(slot, slotGraphics, hovered);
		} finally {
			CapturedEntityBoxJeiRenderer.endSlotDraw();
		}
	}
}
