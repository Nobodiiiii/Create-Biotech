package com.nobodiiiii.createbiotech.content.allay.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.simibubi.create.content.logistics.FlapStuffs;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortBlock;
import com.nobodiiiii.createbiotech.content.allay.block.allayport.AllayPortBlockEntity;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class AllayPortRenderer extends SmartBlockEntityRenderer<AllayPortBlockEntity> {
	public static final int CURTAIN_SEGMENT_COUNT = 4;
	public static final List<ResourceLocation> CURTAIN_MODEL_LOCATIONS = List.of(
		CreateBiotech.asResource("block/allay_port/curtain_0"),
		CreateBiotech.asResource("block/allay_port/curtain_1"),
		CreateBiotech.asResource("block/allay_port/curtain_2"),
		CreateBiotech.asResource("block/allay_port/curtain_3")
	);
	public static final List<PartialModel> CURTAIN_SEGMENTS = CURTAIN_MODEL_LOCATIONS.stream()
		.map(PartialModel::of)
		.toList();
	public static final Vec3 CURTAIN_PIVOT = VecHelper.voxelSpace(0, 12, 3);

	private final GreetingAllayRenderer greetingAllayRenderer;

	public AllayPortRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
		greetingAllayRenderer = new GreetingAllayRenderer();
	}

	@Override
	protected void renderSafe(AllayPortBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {
		super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

		BlockState blockState = be.getBlockState();
		Direction facing = blockState.getValue(AllayPortBlock.FACING);
		float flapness = be.getFlap(partialTicks);
		float flapWaveStrength = Mth.clamp((Math.abs(flapness) - 0.25f) * 4.0f, 0.0f, 1.0f);
		float waveStrength = be.isCourierWaving() ? 1.0f : flapWaveStrength;
		float animationTime;
		if (be.getLevel() instanceof PonderLevel ponderLevel && ponderLevel.scene != null) {
			animationTime = ponderLevel.scene.getCurrentTime() + partialTicks;
		} else {
			animationTime = be.getLevel() == null
				? partialTicks
				: be.getLevel().getGameTime() % 24_000L + partialTicks;
		}
		greetingAllayRenderer.render(ms, buffer, light, facing, animationTime, waveStrength);

		if (VisualizationManager.supportsVisualization(be.getLevel())) {
			return;
		}

		renderCurtain(ms, buffer, blockState, facing, flapness, light, overlay);
	}

	private void renderCurtain(PoseStack ms, MultiBufferSource buffer, BlockState blockState, Direction facing,
		float flapness, int light, int overlay) {
		float horizontalAngle = AngleHelper.horizontalAngle(facing.getOpposite());
		VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.cutoutMipped());
		var transform = TransformStack.of(ms);

		ms.pushPose();
		transform.center()
			.rotateYDegrees(horizontalAngle)
			.uncenter();
		for (int segment = 0; segment < CURTAIN_SEGMENT_COUNT; segment++) {
			ms.pushPose();
			transform.translate(CURTAIN_PIVOT)
				.rotateXDegrees(FlapStuffs.flapAngle(flapness, segment))
				.translateBack(CURTAIN_PIVOT);
			SuperByteBuffer curtainBuffer = CachedBuffers.partial(CURTAIN_SEGMENTS.get(segment), blockState);
			curtainBuffer.light(light)
				.overlay(overlay)
				.renderInto(ms, vertexConsumer);
			ms.popPose();
		}
		ms.popPose();
	}
}
