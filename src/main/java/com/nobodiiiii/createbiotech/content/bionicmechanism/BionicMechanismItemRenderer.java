package com.nobodiiiii.createbiotech.content.bionicmechanism;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Keeps the living mechanism still outside hand-rendering contexts. */
public class BionicMechanismItemRenderer extends CustomRenderedItemModelRenderer {
	public static final ResourceLocation HELD_MODEL_LOCATION =
		CreateBiotech.asResource("item/bionic_mechanism_held");

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		renderer.render(shouldAnimate(stack, transformType)
			? getAnimatedModel(model.getOriginalModel())
			: model.getOriginalModel(), light);
	}

	private static boolean shouldAnimate(ItemStack stack, ItemDisplayContext transformType) {
		return isHeld(stack, transformType)
			|| transformType == ItemDisplayContext.GUI && BionicMechanismHoverTracker.isHovered(stack);
	}

	private static boolean isHeld(ItemStack stack, ItemDisplayContext transformType) {
		boolean handContext = switch (transformType) {
			case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
				THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> true;
			default -> false;
		};
		if (handContext || BionicMechanismHoverTracker.isRenderingFirstPersonHand(stack))
			return true;
		if (transformType == ItemDisplayContext.GUI || Minecraft.getInstance().player == null)
			return false;

		ItemStack mainHand = Minecraft.getInstance().player.getMainHandItem();
		ItemStack offHand = Minecraft.getInstance().player.getOffhandItem();
		if (stack == mainHand || stack == offHand)
			return true;

		// Some render bridges redraw an equipped stack with NONE instead of preserving the hand context.
		return transformType == ItemDisplayContext.NONE
			&& (ItemStack.isSameItemSameComponents(stack, mainHand)
				|| ItemStack.isSameItemSameComponents(stack, offHand));
	}

	private static BakedModel getAnimatedModel(BakedModel fallback) {
		ModelManager modelManager = Minecraft.getInstance()
			.getModelManager();
		BakedModel heldModel = modelManager.getModel(
			new ModelResourceLocation(HELD_MODEL_LOCATION, ModelResourceLocation.STANDALONE_VARIANT));
		return heldModel == modelManager.getMissingModel() ? fallback : heldModel;
	}
}
