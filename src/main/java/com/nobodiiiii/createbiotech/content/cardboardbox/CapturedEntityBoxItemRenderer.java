package com.nobodiiiii.createbiotech.content.cardboardbox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBConfigs;
import com.nobodiiiii.createbiotech.registry.CBItems;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class CapturedEntityBoxItemRenderer extends CustomRenderedItemModelRenderer {
	private static final ResourceLocation SMALL_BOX_CAPTURED_MODEL =
		CreateBiotech.asResource("item/small_cardboard_box_captured");
	private static final ResourceLocation LARGE_BOX_CAPTURED_MODEL =
		CreateBiotech.asResource("item/large_cardboard_box_captured");

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
		boolean captured = CapturedEntityBoxHelper.hasCapturedEntity(stack);
		boolean renderCapturedEntity = captured && CBConfigs.CLIENT.renderCapturedEntitiesOnBoxes.get();
		renderer.render(getBoxModel(stack, model.getOriginalModel(), transformType, captured, renderCapturedEntity), light);
		if (renderCapturedEntity)
			CapturedEntityBoxIconRenderer.renderOnItem(stack, true, transformType, poseStack, buffer, light);
	}

	private BakedModel getBoxModel(ItemStack stack, BakedModel fallback, ItemDisplayContext transformType,
		boolean captured, boolean renderCapturedEntity) {
		if (transformType == ItemDisplayContext.FIXED)
			return getModel(renderCapturedEntity ? getCapturedModelLocation(stack)
				: CardboardBoxPartials.getLogisticsModelLocation(stack), fallback);
		if (!captured)
			return fallback;

		return getModel(renderCapturedEntity ? getCapturedModelLocation(stack)
			: CardboardBoxPartials.getLogisticsModelLocation(stack), fallback);
	}

	private ResourceLocation getCapturedModelLocation(ItemStack stack) {
		return stack.is(CBItems.LARGE_CARDBOARD_BOX.get()) ? LARGE_BOX_CAPTURED_MODEL : SMALL_BOX_CAPTURED_MODEL;
	}

	private BakedModel getModel(ResourceLocation location, BakedModel fallback) {
		ModelManager modelManager = Minecraft.getInstance()
			.getModelManager();
		BakedModel model = modelManager.getModel(location);
		return model == modelManager.getMissingModel() ? fallback : model;
	}
}
