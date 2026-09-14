package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;
import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModels;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class SpiderAssemblyTableItemRenderer extends CustomRenderedItemModelRenderer {

	private static final ResourceLocation SPIDER_TEXTURE =
		CreateBiotech.asResource("textures/entity/spider_assembly_table/spider.png");
	private static final ResourceLocation SPIDER_EYES_TEXTURE =
		CreateBiotech.asResource("textures/entity/spider_assembly_table/spider_eyes.png");
	private static final int EYES_LIGHT = 15728640;
	private static final float HALF_BLOCK_OFFSET = 0.5f;
	private static final float SPIDER_Y_OFFSET = 15f / 16f;
	private static final float SPIDER_SCALE = 1f;
	private static final BlockState COG_STATE = CBBlocks.SPIDER_ASSEMBLY_TABLE_COG.get()
		.defaultBlockState()
		.setValue(SpiderAssemblyTableCogBlock.FACING, Direction.NORTH);

	private final MachineCreatureModel spiderModel = MachineCreatureModels.spider();

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		BakedModel cogModel = Minecraft.getInstance()
			.getBlockRenderer()
			.getBlockModel(COG_STATE);

		ms.pushPose();
		ms.translate(0, 0, HALF_BLOCK_OFFSET);
		renderer.renderSolid(cogModel, light);
		ms.popPose();

		renderSpiderAssembly(ms, buffer, light, transformType == ItemDisplayContext.GUI);
	}

	private void renderSpiderAssembly(PoseStack ms, MultiBufferSource buffer, int packedLight,
		boolean guiLighting) {
		ms.pushPose();
		if (guiLighting)
			Lighting.setupForEntityInInventory();
		try {
			ms.translate(0, SPIDER_Y_OFFSET, -HALF_BLOCK_OFFSET);
			ms.scale(-SPIDER_SCALE, -SPIDER_SCALE, SPIDER_SCALE);
			renderSpiderModel(ms, buffer, packedLight);
		} finally {
			ms.popPose();
			if (guiLighting)
				Lighting.setupFor3DItems();
		}
	}

	private void renderSpiderModel(PoseStack ms, MultiBufferSource buffer, int packedLight) {
		spiderModel.resetPose();
		VertexConsumer spiderBuffer = buffer.getBuffer(spiderModel.renderType(SPIDER_TEXTURE));
		spiderModel.renderToBuffer(ms, spiderBuffer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		VertexConsumer spiderEyesBuffer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.eyes(SPIDER_EYES_TEXTURE));
		spiderModel.renderToBuffer(ms, spiderEyesBuffer, EYES_LIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
	}
}
