package com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class FixedCarrotFishingRodRenderer implements BlockEntityRenderer<FixedCarrotFishingRodBlockEntity> {

	private static final float ITEM_XZ_OFFSET = 6.5f / 16f;
	private static final float ITEM_CENTER_Y = 0;
	private static final float ITEM_FACE_SCALE = 0.5f;
	private static final float ITEM_THICKNESS_SCALE = 0.5f;

	public FixedCarrotFishingRodRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(FixedCarrotFishingRodBlockEntity blockEntity, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay) {
		ItemStack baitItem = blockEntity.getBaitItem();
		if (baitItem.isEmpty())
			return;

		BlockState state = blockEntity.getBlockState();
		if (!state.hasProperty(FixedCarrotFishingRodBlock.FACING))
			return;

		Direction facing = state.getValue(FixedCarrotFishingRodBlock.FACING);
		float x = 0.5f + facing.getStepX() * ITEM_XZ_OFFSET;
		float z = 0.5f + facing.getStepZ() * ITEM_XZ_OFFSET;

		poseStack.pushPose();
		poseStack.translate(x, ITEM_CENTER_Y, z);
		poseStack.mulPose(Axis.YP.rotationDegrees(itemYRotation(facing)));
		poseStack.scale(ITEM_FACE_SCALE, ITEM_FACE_SCALE, ITEM_THICKNESS_SCALE);

		ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
		BakedModel bakedModel = itemRenderer.getModel(baitItem, blockEntity.getLevel(), null, 0);
		itemRenderer.render(baitItem, ItemDisplayContext.NONE, false, poseStack, buffer, packedLight, packedOverlay,
			bakedModel);

		poseStack.popPose();
	}

	private static float itemYRotation(Direction facing) {
		return switch (facing) {
		case EAST -> 90;
		case SOUTH -> 180;
		case WEST -> 270;
		default -> 0;
		};
	}
}
