package com.nobodiiiii.createbiotech.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.capturenet.ArmCaptureAccess;
import com.nobodiiiii.createbiotech.content.capturenet.ArmCaptureBehaviour;
import com.nobodiiiii.createbiotech.content.capturenet.ArmCapturePose;
import com.nobodiiiii.createbiotech.content.capturenet.client.CaptureNetRenderer;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlock;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmRenderer;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.minecraft.client.renderer.MultiBufferSource;

@Mixin(ArmRenderer.class)
public abstract class ArmRendererCaptureMixin {

	@Inject(method = "renderSafe(Lcom/simibubi/create/content/kinetics/mechanicalArm/ArmBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
		at = @At("RETURN"))
	private void createBiotech$renderMountedNet(ArmBlockEntity arm, float partialTicks, PoseStack pose,
		MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
		ArmCaptureBehaviour capture = ArmCaptureBehaviour.get(arm);
		ArmCaptureAccess access = (ArmCaptureAccess) arm;
		if (capture == null || capture.getNet().isEmpty() || !access.createBiotech$isCaptureArmIdle())
			return;

		ArmCapturePose angles = access.createBiotech$getCapturePose(partialTicks);
		boolean ceiling = arm.getBlockState().getValue(ArmBlock.CEILING);
		pose.pushPose();
		var transform = TransformStack.of(pose);
		transform.center();
		if (ceiling)
			transform.rotateXDegrees(180);
		ArmRenderer.transformBase(transform, angles.yaw());
		ArmRenderer.transformLowerArm(transform, angles.shoulder() - 135);
		ArmRenderer.transformUpperArm(transform, angles.elbow() - 90);
		ArmRenderer.transformHead(transform, angles.head());
		pose.translate(0, 0, -ArmCapturePose.CLAW_GRIP);
		transform.rotateXDegrees(-90);
		if (ceiling)
			transform.rotateYDegrees(180);
		pose.scale(ArmCapturePose.NET_SCALE, ArmCapturePose.NET_SCALE, ArmCapturePose.NET_SCALE);
		pose.translate(0, -ArmCapturePose.GRIP_Y, 0);
		CaptureNetRenderer.renderMounted(capture.getNet(), pose, buffer, light, overlay);
		pose.popPose();
	}
}
