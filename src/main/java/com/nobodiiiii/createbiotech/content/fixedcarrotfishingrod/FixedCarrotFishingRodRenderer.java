package com.nobodiiiii.createbiotech.content.fixedcarrotfishingrod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class FixedCarrotFishingRodRenderer implements BlockEntityRenderer<FixedCarrotFishingRodBlockEntity> {

	private static final float STRING_XZ_OFFSET = 7 / 16f;
	private static final float STRING_TOP_Y = 7 / 16f;
	private static final float STRING_BOTTOM_Y = -1 / 16f;

	public FixedCarrotFishingRodRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(FixedCarrotFishingRodBlockEntity blockEntity, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight, int packedOverlay) {
		BlockState state = blockEntity.getBlockState();
		if (!state.hasProperty(FixedCarrotFishingRodBlock.FACING))
			return;

		Direction facing = state.getValue(FixedCarrotFishingRodBlock.FACING);
		float x = 0.5f + facing.getStepX() * STRING_XZ_OFFSET;
		float z = 0.5f + facing.getStepZ() * STRING_XZ_OFFSET;

		VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
		PoseStack.Pose pose = poseStack.last();
		consumer.vertex(pose.pose(), x, STRING_TOP_Y, z)
			.color(0.16f, 0.14f, 0.1f, 1.0f)
			.normal(pose.normal(), 0.0f, -1.0f, 0.0f)
			.endVertex();
		consumer.vertex(pose.pose(), x, STRING_BOTTOM_Y, z)
			.color(0.16f, 0.14f, 0.1f, 1.0f)
			.normal(pose.normal(), 0.0f, -1.0f, 0.0f)
			.endVertex();
	}
}
