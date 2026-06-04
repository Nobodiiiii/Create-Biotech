package com.nobodiiiii.createbiotech.mixin.client;

import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nobodiiiii.createbiotech.compat.jei.SquidJeiRenderer;
import com.nobodiiiii.createbiotech.compat.jei.SquidPrinterJeiRecipes;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.gui.GuiGraphics;

@Pseudo
@Mixin(targets = "com.simibubi.create.compat.jei.category.ItemApplicationCategory", remap = false)
public abstract class ItemApplicationCategoryMixin {
	private static final String BUTTER_CAT_RECIPE_ID = "create_biotech:item_application/butter_cat_engine_manual_only";

	@Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$drawButterCat(ItemApplicationRecipe recipe, IRecipeSlotsView recipeSlotsView,
		GuiGraphics graphics, double mouseX, double mouseY, CallbackInfo ci) {
		if (!recipe.getId().toString().equals(BUTTER_CAT_RECIPE_ID))
			return;

		AllGuiTextures.JEI_SHADOW.render(graphics, 62, 47);
		AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 74, 10);

		var displayedIngredient = recipeSlotsView.getSlotViews()
			.get(0)
			.getDisplayedIngredient(VanillaTypes.ITEM_STACK);
		if (displayedIngredient.isEmpty())
			return;

		Item item = displayedIngredient.get().getItem();
		if (!(item instanceof BlockItem blockItem))
			return;

		BlockState state = blockItem.getBlock().defaultBlockState();
		PoseStack matrixStack = graphics.pose();
		matrixStack.pushPose();
		matrixStack.translate(74, 51, 100);
		matrixStack.mulPose(Axis.XP.rotationDegrees(-15.5f));
		matrixStack.mulPose(Axis.YP.rotationDegrees(22.5f));

		GuiGameElement.of(state)
			.rotateBlock(90, 0, 0)
			.lighting(AnimatedKinetics.DEFAULT_LIGHTING)
			.scale(20)
			.render(graphics);

		matrixStack.popPose();
		ci.cancel();
	}

	@Inject(method = "draw", at = @At("TAIL"), remap = false)
	private void createBiotech$drawSquid(ItemApplicationRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX,
		double mouseY, CallbackInfo ci) {
		if (!SquidPrinterJeiRecipes.isSquidPrinterItemApplication(recipe.getId()))
			return;
		SquidJeiRenderer.render(graphics, 88, 48, 1.0f);
	}
}
