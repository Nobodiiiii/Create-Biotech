package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.compat.jei.CapturedEntityBoxJeiRenderer;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

@Pseudo
@Mixin(targets = "mezz.jei.library.gui.ingredients.RecipeSlot", remap = false)
public abstract class JeiRecipeSlotMixin {
	@Inject(method = "drawIngredient", at = @At("HEAD"), cancellable = true, remap = false)
	private <T> void createBiotech$drawCapturedEntityBox(GuiGraphics graphics, ITypedIngredient<T> typedIngredient,
		int x, int y, CallbackInfo ci) {
		ItemStack stack = typedIngredient.getIngredient(VanillaTypes.ITEM_STACK)
			.orElse(ItemStack.EMPTY);
		if (CapturedEntityBoxJeiRenderer.renderCapturedEntityBox(graphics, stack, x, y))
			ci.cancel();
	}
}
