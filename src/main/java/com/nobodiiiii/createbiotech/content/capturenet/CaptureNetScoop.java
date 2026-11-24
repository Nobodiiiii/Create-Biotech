package com.nobodiiiii.createbiotech.content.capturenet;

import net.minecraft.util.Mth;

public final class CaptureNetScoop {

	public static final float CONTACT = (float) CaptureNetItem.CAPTURE_CONTACT_TICK / CaptureNetItem.ANIMATION_TICKS;
	// Model Y = -8, relative to the item origin at Y = 8.
	public static final float HANDLE_BOTTOM = -1;
	private static final float RAISE_END = 0.22f;
	private static final float FOLLOW_THROUGH_END = 0.65f;
	private static final float RECOVER_START = 0.72f;

	private CaptureNetScoop() {}

	public static float mouthHeight(float progress) {
		if (progress < RAISE_END)
			return 0.64f;
		if (progress < CONTACT)
			return Mth.lerp(ease((progress - RAISE_END) / (CONTACT - RAISE_END)), 0.64f, 0);
		return Mth.lerp(ease((progress - CONTACT) / (FOLLOW_THROUGH_END - CONTACT)), 0, -0.20f);
	}

	public static float weight(float progress) {
		if (progress < RAISE_END)
			return ease(progress / RAISE_END);
		return progress > RECOVER_START ? ease((1 - progress) / (1 - RECOVER_START)) : 1;
	}

	private static float ease(float value) {
		float clamped = Mth.clamp(value, 0, 1);
		return clamped * clamped * (3 - 2 * clamped);
	}
}
