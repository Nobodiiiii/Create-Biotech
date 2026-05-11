package com.nobodiiiii.createbiotech.content.ghasthotairballoon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.contraptions.pulley.AbstractPulleyRenderer;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class GhastHotAirBalloonAssemblyStationRenderer
	implements BlockEntityRenderer<GhastHotAirBalloonAssemblyStationBlockEntity> {

	public GhastHotAirBalloonAssemblyStationRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(GhastHotAirBalloonAssemblyStationBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		VertexConsumer vb = buffer.getBuffer(RenderType.solid());
		BlockState blockState = be.getBlockState();
		Level level = be.getLevel();
		BlockPos pos = be.getBlockPos();

		float offset = be.getInterpolatedOffset(partialTicks);
		boolean running = be.isRunning();

		Direction.Axis axis = blockState.hasProperty(GhastHotAirBalloonAssemblyStationBlock.HORIZONTAL_AXIS)
			? blockState.getValue(GhastHotAirBalloonAssemblyStationBlock.HORIZONTAL_AXIS)
			: Direction.Axis.Z;
		Direction shaftFacing = Direction.get(Direction.AxisDirection.POSITIVE, axis);

		SuperByteBuffer coil = AbstractPulleyRenderer.scrollCoil(
			CachedBuffers.partialFacing(AllPartialModels.ROPE_COIL, blockState, shaftFacing),
			AllSpriteShifts.ROPE_PULLEY_COIL, offset, 1f);
		coil.light(light)
			.renderInto(ms, vb);

		SuperByteBuffer halfMagnet = CachedBuffers.partial(AllPartialModels.ROPE_HALF_MAGNET, blockState);
		SuperByteBuffer fullMagnet = CachedBuffers.block(AllBlocks.PULLEY_MAGNET.getDefaultState());

		if (running || offset == 0)
			AbstractPulleyRenderer.renderAt(level, offset > 0.25f ? fullMagnet : halfMagnet, offset, pos, ms, vb);

		SuperByteBuffer halfRope = CachedBuffers.partial(AllPartialModels.ROPE_HALF, blockState);
		float frac = offset % 1f;
		if (offset > 0.75f && (frac < 0.25f || frac > 0.75f))
			AbstractPulleyRenderer.renderAt(level, halfRope, frac > 0.75f ? frac - 1f : frac, pos, ms, vb);

		if (!running)
			return;

		SuperByteBuffer rope = CachedBuffers.block(AllBlocks.ROPE.getDefaultState());
		for (int i = 0; i < offset - 1.25f; i++)
			AbstractPulleyRenderer.renderAt(level, rope, offset - i - 1f, pos, ms, vb);
	}

	@Override
	public boolean shouldRenderOffScreen(GhastHotAirBalloonAssemblyStationBlockEntity be) {
		return true;
	}

	@Override
	public int getViewDistance() {
		return AllConfigs.server().kinetics.maxRopeLength.get();
	}
}
