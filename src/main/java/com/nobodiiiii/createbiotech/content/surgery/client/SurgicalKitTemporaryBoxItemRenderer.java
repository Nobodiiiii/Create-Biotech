package com.nobodiiiii.createbiotech.content.surgery.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.cardboardbox.CapturedEntityBoxIconRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Adds the ordinary large cardboard-box contents rendering to a filled temporary surgical box. */
public class SurgicalKitTemporaryBoxItemRenderer extends BlockEntityWithoutLevelRenderer {
	private static final ResourceLocation CLOSED_BOX_MODEL =
		CreateBiotech.asResource("item/large_cardboard_box_captured");

	public SurgicalKitTemporaryBoxItemRenderer() {
		super(null, null);
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
		MultiBufferSource buffer, int light, int overlay) {
		BakedModel boxModel = Minecraft.getInstance().getModelManager().getModel(
			new ModelResourceLocation(CLOSED_BOX_MODEL, ModelResourceLocation.STANDALONE_VARIANT));
		PartialItemModelRenderer renderer = PartialItemModelRenderer.of(stack, transformType,
			poseStack, buffer, overlay);

		poseStack.pushPose();
		poseStack.translate(0.5f, 0.5f, 0.5f);
		renderer.render(boxModel, light);
		CapturedEntityBoxIconRenderer.renderOnLargeItem(stack, transformType, poseStack, buffer, light);
		poseStack.popPose();
	}
}
