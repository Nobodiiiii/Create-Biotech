package com.nobodiiiii.createbiotech.content.surgery.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Phantom;

/**
 * Per-type exceptions to the canonical capture pose used by {@link SurgicalSourceModelRenderer}.
 * <p>
 * Every capture already pins the animation inputs, so a captured creature never changes pose. The pinned
 * pose is simply whatever the model draws at age 0, which for most creatures is a natural rest pose. The
 * types handled here draw an awkward pose at age 0 and instead ask to be captured at another age.
 * <p>
 * Called while the capture's fixed entity id is in place, so id-derived phase offsets are already stable.
 */
final class SurgicalCapturePoseOverrides {
	/**
	 * Vanilla phantom wings and wing tips both roll by {@code cos(phase) * 16deg} and the tail pitches by
	 * {@code -(5 + 5 cos(2 phase))deg}, so at a quarter flap every one of them lies flat along the body.
	 * The pattern repeats every half flap.
	 */
	private static final float PHANTOM_FLAT_PHASE_TICKS = 90.0f / Phantom.FLAP_DEGREES_PER_TICK;
	private static final float PHANTOM_HALF_FLAP_TICKS = 180.0f / Phantom.FLAP_DEGREES_PER_TICK;

	private SurgicalCapturePoseOverrides() {}

	/** Animation age a capture is taken at; 0 unless the type overrides it below. */
	static float captureAgeInTicks(LivingEntity preview) {
		if (preview instanceof Phantom phantom)
			return phantomFlatWingAge(phantom);
		return 0.0f;
	}

	private static float phantomFlatWingAge(Phantom phantom) {
		// Measured against the pinned id's flap offset, and wrapped so a modded offset stays non-negative.
		float age = (PHANTOM_FLAT_PHASE_TICKS - phantom.getUniqueFlapTickOffset()) % PHANTOM_HALF_FLAP_TICKS;
		return age < 0.0f ? age + PHANTOM_HALF_FLAP_TICKS : age;
	}
}
