package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.compat.jei.CuteCatOnShaftJeiRenderer;
import com.nobodiiiii.createbiotech.compat.jei.MechanicalArmJeiRenderer;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.client.gui.GuiGraphics;

@Pseudo
@Mixin(targets = "com.simibubi.create.compat.jei.category.ItemApplicationCategory", remap = false)
public abstract class ItemApplicationCategoryMixin {
	@Inject(method = "draw(Lcom/simibubi/create/content/kinetics/deployer/ItemApplicationRecipe;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/client/gui/GuiGraphics;DD)V",
		at = @At("HEAD"), cancellable = true, remap = true)
	private void createBiotech$drawCustomPreview(ItemApplicationRecipe recipe, IRecipeSlotsView recipeSlotsView,
		GuiGraphics graphics, double mouseX, double mouseY, CallbackInfo ci) {
		if (MechanicalArmJeiRenderer.render(graphics, recipeSlotsView)) {
			ci.cancel();
			return;
		}

		CuteCatOnShaftJeiRenderer.PreviewKind previewKind =
			CuteCatOnShaftJeiRenderer.getPreviewKind(recipe, recipeSlotsView);
		if (previewKind == null)
			return;

		if (CuteCatOnShaftJeiRenderer.render(graphics, previewKind))
			ci.cancel();
	}
}
