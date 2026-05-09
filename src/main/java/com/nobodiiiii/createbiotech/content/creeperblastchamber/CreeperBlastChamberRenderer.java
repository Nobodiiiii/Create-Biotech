package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CreeperBlastChamberRenderer implements BlockEntityRenderer<CreeperBlastChamberBlockEntity> {

	public CreeperBlastChamberRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(CreeperBlastChamberBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
					   int light, int overlay) {
		BlockPos origin = be.getStructureOrigin();
		int size = be.getStructureSize();
		if (origin == null || !be.isStructureValid())
			return;

		float progress = be.displayGauge.getValue(partialTicks);
		if (progress <= 0.01f)
			return;

		Level level = be.getLevel();
		if (level == null)
			return;

		BlockState blockState = be.getBlockState();
		VertexConsumer vb = buffer.getBuffer(RenderType.cutout());

		float dialPivotY = 6f / 16;
		float dialPivotZ = 8f / 16;
		int half = size / 2;

		RenderSystem.disableDepthTest();

		for (Direction d : Iterate.horizontalDirections) {
			BlockPos wallPos = origin.offset(
				d.getAxis() == Direction.Axis.X ? (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? size - 1 : 0) : half,
				0,
				d.getAxis() == Direction.Axis.Z ? (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? size - 1 : 0) : half);

			// BlockPos neighborPos = wallPos.relative(d);
			// BlockState neighbor = level.getBlockState(neighborPos);
			// if (neighbor.isSolidRender(level, neighborPos))
			// 	continue;

			double dx = wallPos.getX() - be.getBlockPos().getX();
			double dy = wallPos.getY() - be.getBlockPos().getY();
			double dz = wallPos.getZ() - be.getBlockPos().getZ();

			ms.pushPose();
			ms.translate(dx + 0.5, dy + 0.5, dz + 0.5);

			int displayLight = LevelRenderer.getLightColor(level, wallPos);

			float yRot = -d.toYRot() - 90;
			CachedBuffers.partial(AllPartialModels.BOILER_GAUGE, blockState)
				.rotateYDegrees(yRot)
				.uncenter()
				.translate(1f / 2f - 6f / 16f, 0, 0)
				.light(displayLight)
				.renderInto(ms, vb);
			CachedBuffers.partial(AllPartialModels.BOILER_GAUGE_DIAL, blockState)
				.rotateYDegrees(yRot)
				.uncenter()
				.translate(1f / 2f - 6f / 16f, 0, 0)
				.translate(0, dialPivotY, dialPivotZ)
				.rotateXDegrees(-145 * progress + 90)
				.translate(0, -dialPivotY, -dialPivotZ)
				.light(displayLight)
				.renderInto(ms, vb);

			ms.popPose();
		}

		RenderSystem.enableDepthTest();
	}

	@Override
	public boolean shouldRenderOffScreen(CreeperBlastChamberBlockEntity be) {
		return be.isStructureValid();
	}
}
