package com.nobodiiiii.createbiotech.content.spiderassemblytable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.registry.CBBlocks;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
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

	private @Nullable SpiderModel<RenderSpider> spiderModel;
	private @Nullable RenderSpider cachedSpider;
	private @Nullable ClientLevel cachedLevel;

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		BakedModel cogModel = Minecraft.getInstance()
			.getBlockRenderer()
			.getBlockModel(COG_STATE);
		RenderSpider spider = getOrCreateSpider(Minecraft.getInstance().level);

		ms.pushPose();
		ms.translate(0, 0, HALF_BLOCK_OFFSET);
		renderer.renderSolid(cogModel, light);
		ms.popPose();

		if (spider == null || getSpiderModel() == null)
			return;

		renderSpiderAssembly(spider, ms, buffer, light, transformType == ItemDisplayContext.GUI);
	}

	private void renderSpiderAssembly(RenderSpider spider, PoseStack ms, MultiBufferSource buffer, int packedLight,
		boolean guiLighting) {
		ms.pushPose();
		if (guiLighting)
			Lighting.setupForEntityInInventory();
		try {
			ms.translate(0, SPIDER_Y_OFFSET, -HALF_BLOCK_OFFSET);
			ms.scale(-SPIDER_SCALE, -SPIDER_SCALE, SPIDER_SCALE);
			renderSpiderModel(spider, ms, buffer, packedLight);
		} finally {
			ms.popPose();
			if (guiLighting)
				Lighting.setupFor3DItems();
		}
	}

	private void prepareSpiderModel(RenderSpider spider) {
		if (spiderModel == null)
			return;
		ModelPart root = spiderModel.root();
		root.getAllParts().forEach(ModelPart::resetPose);
		spiderModel.setupAnim(spider, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
	}

	private void renderSpiderModel(RenderSpider spider, PoseStack ms, MultiBufferSource buffer, int packedLight) {
		SpiderModel<RenderSpider> spiderModel = getSpiderModel();
		if (spiderModel == null)
			return;
		prepareSpiderModel(spider);
		VertexConsumer spiderBuffer = buffer.getBuffer(spiderModel.renderType(SPIDER_TEXTURE));
		spiderModel.renderToBuffer(ms, spiderBuffer, packedLight, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
		VertexConsumer spiderEyesBuffer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.eyes(SPIDER_EYES_TEXTURE));
		spiderModel.renderToBuffer(ms, spiderEyesBuffer, EYES_LIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
	}

	private @Nullable SpiderModel<RenderSpider> getSpiderModel() {
		if (spiderModel == null && Minecraft.getInstance().getEntityModels() != null)
			spiderModel = new SpiderModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SPIDER));
		return spiderModel;
	}

	private @Nullable RenderSpider getOrCreateSpider(@Nullable Level level) {
		if (!(level instanceof ClientLevel clientLevel))
			return null;

		if (cachedSpider == null || cachedLevel != clientLevel) {
			cachedLevel = clientLevel;
			cachedSpider = new RenderSpider(clientLevel);
			cachedSpider.setNoAi(true);
			cachedSpider.setSilent(true);
		}

		return cachedSpider;
	}

	private static class RenderSpider extends Spider {

		private RenderSpider(ClientLevel level) {
			super(EntityType.SPIDER, level);
		}
	}
}
