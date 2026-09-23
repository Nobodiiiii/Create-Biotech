package com.nobodiiiii.createbiotech.content.honeycombgauge;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public class HoneycombGaugeClusterRenderer implements BlockEntityRenderer<HoneycombGaugeClusterBlockEntity> {
	public HoneycombGaugeClusterRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(HoneycombGaugeClusterBlockEntity cluster, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int light, int overlay) {
		Level level = cluster.getLevel();
		if (level == null)
			return;
		BlockPos origin = cluster.getBlockPos();
		Direction face = cluster.getBlockState().getValue(HoneycombGaugeClusterBlock.FACING);
		for (BlockPos pos : cluster.visibleHoneycombs()) {
			if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
				|| !level.getBlockState(pos).is(Blocks.HONEYCOMB_BLOCK))
				continue;
			poseStack.pushPose();
			poseStack.translate(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
			HoneycombGaugeFaceOverlay.render(poseStack, buffer, face, LightTexture.FULL_BRIGHT);
			poseStack.popPose();
		}
	}

	@Override
	public AABB getRenderBoundingBox(HoneycombGaugeClusterBlockEntity cluster) {
		return new AABB(cluster.getBlockPos()).inflate(65);
	}

	@Override
	public int getViewDistance() {
		return 128;
	}
}
