package com.nobodiiiii.createbiotech.entity.ai;

import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/** Switches a body with fewer than two grounded legs from continuous walking to slime-like hops. */
public final class SlimeBionicMoveControl extends MoveControl {
	private static final double MIN_WANTED_DISTANCE_SQR = 2.5e-7d;
	private static final int MIN_JUMP_DELAY = 10;
	private static final int RANDOM_JUMP_DELAY = 20;

	private final SlimeBionicEntity bionic;
	private int jumpDelay;

	public SlimeBionicMoveControl(SlimeBionicEntity bionic) {
		super(bionic);
		this.bionic = bionic;
	}

	@Override
	public void tick() {
		if (bionic.getLocomotionLegCount() >= 2) {
			super.tick();
			return;
		}
		tickHopping();
	}

	private void tickHopping() {
		if (operation == Operation.MOVE_TO) {
			double dx = wantedX - bionic.getX();
			double dz = wantedZ - bionic.getZ();
			double dy = wantedY - bionic.getY();
			if (dx * dx + dy * dy + dz * dz < MIN_WANTED_DISTANCE_SQR) {
				stopOnGround();
				return;
			}

			float wantedYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
			bionic.setYRot(rotlerp(bionic.getYRot(), wantedYaw, 90.0f));
			if (!bionic.onGround()) {
				operation = Operation.JUMPING;
				applyWantedSpeed();
				return;
			}

			operation = Operation.WAIT;
			if (jumpDelay-- <= 0) {
				jumpDelay = MIN_JUMP_DELAY + bionic.getRandom().nextInt(RANDOM_JUMP_DELAY);
				if (bionic.isAggressive())
					jumpDelay = Math.max(1, jumpDelay / 3);
				applyWantedSpeed();
				bionic.getJumpControl().jump();
				operation = Operation.JUMPING;
			} else {
				stopOnGround();
			}
			return;
		}

		if (operation == Operation.JUMPING) {
			applyWantedSpeed();
			if (bionic.onGround()) {
				operation = Operation.WAIT;
				stopOnGround();
			}
			return;
		}

		// Path navigation does not normally strafe this mob. Suppress a one-tick ground slide if
		// another goal requests it while the body is using hop locomotion.
		operation = Operation.WAIT;
		if (bionic.onGround())
			stopOnGround();
	}

	private void applyWantedSpeed() {
		bionic.setSpeed((float) (speedModifier
			* bionic.getAttributeValue(Attributes.MOVEMENT_SPEED)));
	}

	private void stopOnGround() {
		bionic.setSpeed(0.0f);
		bionic.setXxa(0.0f);
		bionic.setZza(0.0f);
	}
}
