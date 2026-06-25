package com.nobodiiiii.createbiotech.content.squidprinter;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SquidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class SquidPrinterItemRenderer extends CustomRenderedItemModelRenderer {

	private static final float ITEM_ENVELOPE_HEIGHT_BLOCKS = 23.0f / 16.0f;
	private static final float TOTAL_RENDER_HEIGHT_BLOCKS =
		Math.max(ITEM_ENVELOPE_HEIGHT_BLOCKS,
			SquidPrinterSquidVisual.MODEL_HEIGHT_BLOCKS * SquidPrinterSquidVisual.RENDER_SCALE);
	// Keep the taller squid assembly within the same item slot envelope used by Create's spout item.
	private static final float ITEM_ENVELOPE_SCALE = ITEM_ENVELOPE_HEIGHT_BLOCKS / TOTAL_RENDER_HEIGHT_BLOCKS;
	private static final float ITEM_Y_OFFSET = 0.15f;
	private static final float SQUID_ATTACHMENT_Y = (float) SquidPrinterSquidVisual.HEAD_TOP_Y;

	@Nullable
	private SquidModel<Squid> squidModel;

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		ms.pushPose();
		ms.translate(0, ITEM_Y_OFFSET, 0);
		ms.scale(ITEM_ENVELOPE_SCALE, ITEM_ENVELOPE_SCALE, ITEM_ENVELOPE_SCALE);
		renderer.render(model.getOriginalModel(), light);

		boolean guiLighting = transformType == ItemDisplayContext.GUI;
		if (getSquidModel() != null)
			renderSquidAssembly(ms, buffer, light, guiLighting);
		ms.popPose();
	}

	private void renderSquidAssembly(PoseStack ms, MultiBufferSource buffer, int packedLight,
		boolean guiLighting) {
		ms.pushPose();
		if (guiLighting)
			Lighting.setupForEntityInInventory();
		try {
			ms.translate(0, SQUID_ATTACHMENT_Y, 0);
			ms.scale(-SquidPrinterSquidVisual.RENDER_SCALE, -SquidPrinterSquidVisual.RENDER_SCALE,
				SquidPrinterSquidVisual.RENDER_SCALE);
			renderSquid(ms, buffer, packedLight);
		} finally {
			ms.popPose();
			if (guiLighting)
				Lighting.setupFor3DItems();
		}
	}

	private void renderSquid(PoseStack ms, MultiBufferSource buffer, int packedLight) {
		SquidModel<Squid> squidModel = getSquidModel();
		if (squidModel == null)
			return;
		SquidPrinterSquidVisual.prepareIdleModel(squidModel);
		SquidPrinterSquidVisual.renderModel(squidModel, ms, buffer, packedLight);
	}

	private @Nullable SquidModel<Squid> getSquidModel() {
		if (squidModel == null && Minecraft.getInstance().getEntityModels() != null)
			squidModel = new SquidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SQUID));
		return squidModel;
	}
}
