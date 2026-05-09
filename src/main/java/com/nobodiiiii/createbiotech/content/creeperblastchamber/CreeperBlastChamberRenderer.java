package com.nobodiiiii.createbiotech.content.creeperblastchamber;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CreeperBlastChamberRenderer implements BlockEntityRenderer<CreeperBlastChamberBlockEntity> {

	private static final float CREEPER_ANIMATION_Y_OFFSET = 0.12f;
	private static final float CREEPER_ANIMATION_START_SCALE = 0.92f;
	private final EntityRenderDispatcher entityRenderDispatcher;

	public CreeperBlastChamberRenderer(BlockEntityRendererProvider.Context context) {
		entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
	}

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

			int displayLight = LevelRenderer.getLightColor(level, wallPos.relative(d));

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
		renderAnimatedCreepers(be, partialTicks, ms, buffer);
	}

	private void renderAnimatedCreepers(CreeperBlastChamberBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer) {
		Level level = be.getLevel();
		if (level == null)
			return;

		for (CreeperBlastChamberBlockEntity.RenderCreeperAnimation animation : be.getRenderAnimations()) {
			Creeper creeper = be.getAnimatedCreeper(animation.creeperUuid(), animation.packagerPos());
			if (creeper == null)
				continue;

			float linearProgress = Mth.clamp(
				(animation.totalTicks() - animation.ticksRemaining() + partialTicks) / animation.totalTicks(), 0f, 1f);
			float progress = linearProgress * linearProgress * (3f - 2f * linearProgress);
			float scale = animation.exiting()
				? Mth.lerp(progress, 1f, CREEPER_ANIMATION_START_SCALE)
				: Mth.lerp(progress, CREEPER_ANIMATION_START_SCALE, 1f);
			float yOffset = animation.exiting()
				? Mth.lerp(progress, 0f, -CREEPER_ANIMATION_Y_OFFSET)
				: Mth.lerp(progress, -CREEPER_ANIMATION_Y_OFFSET, 0f);

			ms.pushPose();
			ms.translate(
				animation.packagerPos().getX() - be.getBlockPos().getX() + 0.5d,
				animation.packagerPos().getY() - be.getBlockPos().getY() + 1d + yOffset,
				animation.packagerPos().getZ() - be.getBlockPos().getZ() + 0.5d);
			ms.scale(scale, scale, scale);

			boolean wasInvisible = creeper.isInvisible();
			creeper.setInvisible(false);
			entityRenderDispatcher.setRenderShadow(false);
			RenderSystem.runAsFancy(() -> entityRenderDispatcher.render(creeper, 0, 0, 0,
				Mth.rotLerp(partialTicks, creeper.yRotO, creeper.getYRot()), partialTicks, ms, buffer,
				LevelRenderer.getLightColor(level, animation.packagerPos().above())));
			entityRenderDispatcher.setRenderShadow(true);
			creeper.setInvisible(wasInvisible);

			ms.popPose();
		}
	}

	@Override
	public boolean shouldRenderOffScreen(CreeperBlastChamberBlockEntity be) {
		return be.isStructureValid();
	}
}
