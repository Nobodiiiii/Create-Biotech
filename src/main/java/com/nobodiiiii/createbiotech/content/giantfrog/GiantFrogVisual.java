package com.nobodiiiii.createbiotech.content.giantfrog;

import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;

import net.minecraft.util.Mth;

/** The machine's fixed resting pose and ten-tick tongue animation, shared with its item model. */
public final class GiantFrogVisual {

	/** The outer toes span nineteen pixels in the neutral model. */
	public static final float REST_MAX_DIMENSION = 19.0f / 16.0f;
	private static final float BELT_OPEN_HEAD_X_ROT = (float) Math.toRadians(-10.0d);
	private static final float MODEL_UNITS_PER_BLOCK = 16.0f;
	private static final float BELT_CONNECTION_FORWARD_BLOCKS = 1.5f;

	private GiantFrogVisual() {}

	public static void prepareModel(MachineCreatureModel model, boolean tongueActive, float animationTicks) {
		model.resetPose();
		if (!tongueActive)
			return;

		float time = Math.max(0, animationTicks) / 20.0f;
		float opening = time < 0.0833f ? fraction(time, 0, 0.0833f)
			: time <= 0.4167f ? 1.0f : 1.0f - fraction(time, 0.4167f, 0.5f);
		MachineCreatureModel.Part body = model.root().getChild("body");
		MachineCreatureModel.Part head = body.getChild("head");
		head.xRot = -60.0f * Mth.DEG_TO_RAD * opening;
		// Preserve the small scale offsets in the original 1.21.1 tongue animation.
		head.xScale = 1.0f + Mth.lerp(opening, 1.0f, 0.998f) * Mth.DEG_TO_RAD;
		head.yScale = head.zScale = 1.0f + Mth.DEG_TO_RAD;
		MachineCreatureModel.Part tongue = body.getChild("tongue");
		float extension = time < 0.1667f ? fraction(time, 0.0833f, 0.1667f)
			: 1.0f - fraction(time, 0.1667f, 0.4167f);
		tongue.xScale = Mth.lerp(extension, 1.0f, 0.5f);
		tongue.zScale = Mth.lerp(extension, 1.0f, 5.0f);
		float bend = time < 0.4167f ? fraction(time, 0.0833f, 0.4167f)
			: 1.0f - fraction(time, 0.4167f, 0.5f);
		tongue.xRot = -18.0f * Mth.DEG_TO_RAD * bend;
	}

	public static void prepareBlockModel(MachineCreatureModel model, boolean tongueActive,
		float animationTicks, boolean beltConnected) {
		prepareModel(model, tongueActive, animationTicks);
		if (!beltConnected)
			return;
		model.part("body/head").xRot += BELT_OPEN_HEAD_X_ROT;
		MachineCreatureModel.Part tongue = model.part("body/tongue");
		tongue.xRot = tongue.yRot = tongue.zRot = 0;
		tongue.xScale = tongue.yScale = 1;
		MachineCreatureModel.Anchor pivot = model.anchor("tongue_pivot");
		MachineCreatureModel.Anchor front = model.anchor("tongue_front");
		float restLength = pivot.z() - front.z();
		if (Math.abs(restLength) < 1.0e-5f)
			return;
		float targetLength = pivot.z()
			+ BELT_CONNECTION_FORWARD_BLOCKS * MODEL_UNITS_PER_BLOCK / GiantFrogBlock.FROG_SCALE;
		tongue.zScale = targetLength / restLength;
	}

	private static float fraction(float value, float start, float end) {
		return Mth.clamp((value - start) / (end - start), 0, 1);
	}
}
