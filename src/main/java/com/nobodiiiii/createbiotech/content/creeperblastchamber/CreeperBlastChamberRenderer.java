package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class CreeperBlastChamberRenderer implements BlockEntityRenderer<CreeperBlastChamberBlockEntity> {

	public CreeperBlastChamberRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(CreeperBlastChamberBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
					   int light, int overlay) {
		float progress = be.gauge.getValue(partialTicks);
		if (progress <= 0.01f)
			return;

		BlockState blockState = be.getBlockState();
		VertexConsumer vb = buffer.getBuffer(RenderType.cutout());

		ms.pushPose();
		ms.translate(0.5, 0.5, 0.5);

		float dialPivotY = 6f / 16;
		float dialPivotZ = 8f / 16;

		for (Direction d : Iterate.horizontalDirections) {
			ms.pushPose();
			float yRot = -d.toYRot() - 90;
			CachedBuffers.partial(AllPartialModels.BOILER_GAUGE, blockState)
				.rotateYDegrees(yRot)
				.uncenter()
				.translate(1f / 2f - 6f / 16f, 0, 0)
				.light(light)
				.renderInto(ms, vb);
			CachedBuffers.partial(AllPartialModels.BOILER_GAUGE_DIAL, blockState)
				.rotateYDegrees(yRot)
				.uncenter()
				.translate(1f / 2f - 6f / 16f, 0, 0)
				.translate(0, dialPivotY, dialPivotZ)
				.rotateXDegrees(-145 * progress + 90)
				.translate(0, -dialPivotY, -dialPivotZ)
				.light(light)
				.renderInto(ms, vb);
			ms.popPose();
		}

		ms.popPose();
	}
}
