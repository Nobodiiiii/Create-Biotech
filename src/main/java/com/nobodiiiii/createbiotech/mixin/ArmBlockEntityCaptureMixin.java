package com.nobodiiiii.createbiotech.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nobodiiiii.createbiotech.content.capturenet.ArmCaptureAccess;
import com.nobodiiiii.createbiotech.content.capturenet.ArmCaptureBehaviour;
import com.nobodiiiii.createbiotech.content.capturenet.ArmCapturePose;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity.Phase;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.AngleHelper;

@Mixin(ArmBlockEntity.class)
public abstract class ArmBlockEntityCaptureMixin implements ArmCaptureAccess {

	@Shadow(remap = false) Phase phase;
	@Shadow(remap = false) LerpedFloat baseAngle;
	@Shadow(remap = false) LerpedFloat lowerArmAngle;
	@Shadow(remap = false) LerpedFloat upperArmAngle;
	@Shadow(remap = false) LerpedFloat headAngle;
	@Shadow(remap = false) float previousBaseAngle;

	@Unique private final LerpedFloat createBiotech$netExtension = LerpedFloat.linear();

	@Override
	public boolean createBiotech$isCaptureArmIdle() {
		return phase == Phase.SEARCH_INPUTS;
	}

	@Override
	public ArmCapturePose createBiotech$getCapturePose(float partialTicks) {
		return new ArmCapturePose(baseAngle.getValue(partialTicks), lowerArmAngle.getValue(partialTicks),
			upperArmAngle.getValue(partialTicks), headAngle.getValue(partialTicks),
			createBiotech$netExtension.getValue(partialTicks));
	}

	@Inject(method = "searchForItem", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$reserveClawForNet(CallbackInfo ci) {
		if (ArmCaptureBehaviour.hasNet((ArmBlockEntity) (Object) this))
			ci.cancel();
	}

	@Inject(method = "checkForMusic", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$stopDancingWithNet(CallbackInfo ci) {
		ArmBlockEntity arm = (ArmBlockEntity) (Object) this;
		if (!ArmCaptureBehaviour.hasNet(arm))
			return;
		if (phase == Phase.DANCING) {
			phase = Phase.SEARCH_INPUTS;
			arm.notifyUpdate();
		}
		ci.cancel();
	}

	@Inject(method = "tickMovementProgress", at = @At("HEAD"), cancellable = true, remap = false)
	private void createBiotech$tickCapturePose(CallbackInfoReturnable<Boolean> cir) {
		ArmBlockEntity arm = (ArmBlockEntity) (Object) this;
		if (arm.getLevel() == null || !arm.getLevel().isClientSide || !createBiotech$isCaptureArmIdle())
			return;
		ArmCaptureBehaviour capture = ArmCaptureBehaviour.get(arm);
		if (capture == null || capture.getNet().isEmpty())
			return;

		ArmCapturePose target = capture.getClientPose(baseAngle.getValue());
		float response = capture.isSwinging() ? 1 : capture.getMovementStep();
		previousBaseAngle = target.yaw();
		baseAngle.setValue(previousBaseAngle);
		lowerArmAngle.setValue(AngleHelper.angleLerp(response, lowerArmAngle.getValue(), target.shoulder()));
		upperArmAngle.setValue(AngleHelper.angleLerp(response, upperArmAngle.getValue(), target.elbow()));
		headAngle.setValue(AngleHelper.angleLerp(response, headAngle.getValue(), target.head()));
		createBiotech$netExtension.setValue(target.extension());
		cir.setReturnValue(false);
	}
}
