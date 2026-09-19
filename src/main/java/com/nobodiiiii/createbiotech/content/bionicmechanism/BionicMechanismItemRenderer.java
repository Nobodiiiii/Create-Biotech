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
		return isHeld(transformType)
			|| transformType == ItemDisplayContext.GUI && BionicMechanismHoverTracker.isHovered(stack);
	}

	private static boolean isHeld(ItemDisplayContext transformType) {
		return switch (transformType) {
			case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
				THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> true;
			default -> false;
		};
	}

	private static BakedModel getAnimatedModel(BakedModel fallback) {
		ModelManager modelManager = Minecraft.getInstance()
			.getModelManager();
		BakedModel heldModel = modelManager.getModel(
			new ModelResourceLocation(HELD_MODEL_LOCATION, ModelResourceLocation.STANDALONE_VARIANT));
		return heldModel == modelManager.getMissingModel() ? fallback : heldModel;
	}
}
