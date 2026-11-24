package com.nobodiiiii.createbiotech.content.capturenet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.capturenet.ArmCapturePose;
import com.nobodiiiii.createbiotech.content.capturenet.PoweredCaptureNetBlock;
import com.nobodiiiii.createbiotech.content.capturenet.PoweredCaptureNetBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class PoweredCaptureNetRenderer extends KineticBlockEntityRenderer<PoweredCaptureNetBlockEntity> {

	public static final PartialModel CLAW_BASE = PartialModel.of(CreateBiotech.asResource("block/powered_capture_net/claw_base"));

	public PoweredCaptureNetRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(PoweredCaptureNetBlockEntity entity, float partialTicks, PoseStack pose,
		MultiBufferSource buffer, int light, int overlay) {
		BlockState state = entity.getBlockState();
		VertexConsumer solid = buffer.getBuffer(RenderType.solid());
		renderRotatingBuffer(entity, CachedBuffers.partial(AllPartialModels.ARM_COG, state), pose, solid, light);

		ArmCapturePose angles = entity.getCapturePose(partialTicks);
		boolean ceiling = state.getValue(PoweredCaptureNetBlock.CEILING);
		PoseStack local = new PoseStack();
		var transform = TransformStack.of(local);
		transform.center();
		if (ceiling)
			transform.rotateXDegrees(180);
		ArmRenderer.transformBase(transform, angles.yaw());
		renderPart(AllPartialModels.ARM_BASE, state, local, pose, solid, light);
		ArmRenderer.transformLowerArm(transform, angles.shoulder() - 135);
		renderPart(AllPartialModels.ARM_LOWER_BODY, state, local, pose, solid, light);
		ArmRenderer.transformUpperArm(transform, angles.elbow() - 90);
		renderPart(AllPartialModels.ARM_UPPER_BODY, state, local, pose, solid, light);
		ArmRenderer.transformHead(transform, angles.head());
		renderPart(CLAW_BASE, state, local, pose, solid, light);
		local.pushPose();
		ArmRenderer.transformClawHalf(transform, false, false, -1);
		renderPart(AllPartialModels.ARM_CLAW_GRIP_UPPER, state, local, pose, solid, light);
		local.popPose();
		local.pushPose();
		ArmRenderer.transformClawHalf(transform, false, false, 1);
		renderPart(AllPartialModels.ARM_CLAW_GRIP_LOWER, state, local, pose, solid, light);
		local.popPose();

		if (entity.getNet().isEmpty())
			return;
		pose.pushPose();
		pose.last().pose().mul(local.last().pose());
		pose.last().normal().mul(local.last().normal());
		var grip = TransformStack.of(pose);
		pose.translate(0, 0, -ArmCapturePose.CLAW_GRIP);
		grip.rotateXDegrees(-90);
		if (ceiling)
			grip.rotateYDegrees(180);
		pose.scale(ArmCapturePose.NET_SCALE, ArmCapturePose.NET_SCALE, ArmCapturePose.NET_SCALE);
		pose.translate(0, -ArmCapturePose.GRIP_Y, 0);
		CaptureNetRenderer.renderMounted(entity.getNet(), pose, buffer, light, overlay, angles.extension());
		pose.popPose();
	}

	private static void renderPart(PartialModel part, BlockState state, PoseStack local, PoseStack pose,
		VertexConsumer buffer, int light) {
		CachedBuffers.partial(part, state).light(light).transform(local).renderInto(pose, buffer);
	}

	@Override
	public boolean shouldRenderOffScreen(PoweredCaptureNetBlockEntity entity) {
		return true;
	}
}
