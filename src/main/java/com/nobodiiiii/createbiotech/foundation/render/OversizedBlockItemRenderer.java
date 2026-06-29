package com.nobodiiiii.createbiotech.foundation.render;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public abstract class OversizedBlockItemRenderer<T extends BlockEntity> extends CustomRenderedItemModelRenderer {

	private static final float ITEM_SCALE = 0.67f;

	@Nullable
	private T cachedBlockEntity;
	@Nullable
	private ClientLevel cachedLevel;

	@Override
	protected final void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		T blockEntity = getOrCreateBlockEntity(Minecraft.getInstance().level);
		if (blockEntity == null)
			return;

		ms.pushPose();
		ms.translate(0, getRenderYOffset(), 0);
		ms.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
		ms.translate(-0.5f, -0.5f, -0.5f);
		renderTransformed(stack, model, renderer, transformType, ms, buffer, light, overlay, blockEntity);
		ms.popPose();
	}

	protected abstract T createBlockEntity();

	protected abstract float getRenderYOffset();

	protected void renderTransformed(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
		ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay,
		T blockEntity) {
		renderBlockEntity(blockEntity, ms, buffer, light, overlay);
	}

	protected void setBlockEntityLevel(T blockEntity, ClientLevel level) {
		blockEntity.setLevel(level);
	}

	protected final void renderBlockEntity(T blockEntity, PoseStack ms, MultiBufferSource buffer, int light,
		int overlay) {
		@SuppressWarnings("unchecked")
		BlockEntityRenderer<T> blockEntityRenderer =
			(BlockEntityRenderer<T>) Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity);
		if (blockEntityRenderer == null)
			return;

		blockEntityRenderer.render(blockEntity, Minecraft.getInstance().getFrameTime(), ms, buffer, light, overlay);
	}

	private @Nullable T getOrCreateBlockEntity(@Nullable Level level) {
		if (!(level instanceof ClientLevel clientLevel))
			return cachedBlockEntity;

		if (cachedBlockEntity == null || cachedLevel != clientLevel) {
			cachedLevel = clientLevel;
			cachedBlockEntity = createBlockEntity();
		}

		setBlockEntityLevel(cachedBlockEntity, clientLevel);
		return cachedBlockEntity;
	}
}
