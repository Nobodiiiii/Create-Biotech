package com.nobodiiiii.createbiotech.content.giantfrog;

import com.nobodiiiii.createbiotech.foundation.render.MachineCreatureModel;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The machine's fixed resting pose and ten-tick tongue animation, shared with its item model. */
public final class GiantFrogVisual {

	public static final ResourceLocation TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/frog/temperate_frog.png");
	/** The outer toes span nineteen pixels in the neutral model. */
	public static final float REST_MAX_DIMENSION = 19.0f / 16.0f;

	private GiantFrogVisual() {}

	public static void prepareModel(MachineCreatureModel model, boolean tongueActive, float animationTicks) {
		model.resetPose();
		if (!tongueActive)
			return;

		float time = Math.max(0, animationTicks) / 20.0f;
		float opening = time < 0.0833f ? fraction(time, 0, 0.0833f)
			: time <= 0.4167f ? 1.0f : 1.0f - fraction(time, 0.4167f, 0.5f);
		ModelPart body = model.root().getChild("body");
		ModelPart head = body.getChild("head");
		head.xRot = -60.0f * Mth.DEG_TO_RAD * opening;
		// Preserve the small scale offsets in the original 1.21.1 tongue animation.
		head.xScale = 1.0f + Mth.lerp(opening, 1.0f, 0.998f) * Mth.DEG_TO_RAD;
		head.yScale = head.zScale = 1.0f + Mth.DEG_TO_RAD;
		ModelPart tongue = body.getChild("tongue");
		float extension = time < 0.1667f ? fraction(time, 0.0833f, 0.1667f)
			: 1.0f - fraction(time, 0.1667f, 0.4167f);
		tongue.xScale = Mth.lerp(extension, 1.0f, 0.5f);
		tongue.zScale = Mth.lerp(extension, 1.0f, 5.0f);
		float bend = time < 0.4167f ? fraction(time, 0.0833f, 0.4167f)
			: 1.0f - fraction(time, 0.4167f, 0.5f);
		tongue.xRot = -18.0f * Mth.DEG_TO_RAD * bend;
	}

	private static float fraction(float value, float start, float end) {
		return Mth.clamp((value - start) / (end - start), 0, 1);
	}
}
