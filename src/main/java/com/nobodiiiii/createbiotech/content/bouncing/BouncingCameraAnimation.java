package com.nobodiiiii.createbiotech.content.bouncing;

import java.util.Map;
import java.util.WeakHashMap;

import com.nobodiiiii.createbiotech.registry.CBMobEffects;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** A subtle first-person spring response to vertical and crouching motion. */
public final class BouncingCameraAnimation {
	private static final long ROLL_IMPULSE_SALT = 0xD6E8FEB86659FD93L;

	private static final float POSITION_STIFFNESS = 0.32F;
	private static final float POSITION_DAMPING = 0.40F;
	private static final float PITCH_STIFFNESS = 0.32F;
	private static final float PITCH_DAMPING = 0.40F;
	private static final float ROLL_STIFFNESS = 0.22F;
	private static final float ROLL_DAMPING = 0.36F;
	private static final float MAX_SIMULATION_STEP = 0.25F;

	private static final float MAX_VERTICAL_OFFSET = 0.07F;
	private static final float MAX_PITCH = 1.5F;
	private static final float MAX_ROLL = 0.85F;

	private static final Map<Player, CameraState> STATES = new WeakHashMap<>();

	private BouncingCameraAnimation() {}

	public static CameraMotion update(Player player, float partialTick) {
		if (!player.isAlive() || player.isSleeping() || !player.hasEffect(CBMobEffects.BOUNCING)) {
			reset(player);
			return CameraMotion.NONE;
		}

		double renderTime = player.tickCount + partialTick;
		CameraState state = STATES.computeIfAbsent(player, ignored -> new CameraState());
		state.update(player, renderTime);
		return new CameraMotion(state.verticalOffset, state.pitch, state.roll);
	}

	public static void reset(Player player) {
		STATES.remove(player);
	}

	public record CameraMotion(float verticalOffset, float pitch, float roll) {
		private static final CameraMotion NONE = new CameraMotion(0.0F, 0.0F, 0.0F);
	}

	private static final class CameraState {
		private boolean initialized;
		private int lastTick;
		private boolean lastOnGround;
		private boolean lastCrouching;
		private double lastVerticalMovement;
		private double lastRenderTime;

		private float verticalOffset;
		private float verticalVelocity;
		private float pitch;
		private float pitchVelocity;
		private float roll;
		private float rollVelocity;

		private void update(Player player, double renderTime) {
			boolean onGround = player.onGround();
			boolean crouching = BouncingCrouch.isActive(player);
			double verticalMovement = player.getDeltaMovement().y;

			if (!initialized || renderTime < lastRenderTime || renderTime - lastRenderTime > 5.0D) {
				initialize(player, onGround, crouching, verticalMovement, renderTime);
				return;
			}

			if (crouching != lastCrouching) {
				applyCrouchImpulse(player, crouching);
				lastCrouching = crouching;
			}

			if (player.tickCount != lastTick) {
				applyVerticalMotionImpulse(player, onGround, verticalMovement);
				lastTick = player.tickCount;
				lastOnGround = onGround;
				lastVerticalMovement = verticalMovement;
			}

			float targetVerticalOffset = 0.0F;
			float targetPitch = 0.0F;
			if (!onGround && !player.isPassenger()) {
				targetVerticalOffset = Mth.clamp((float) -verticalMovement * 0.045F, -0.04F, 0.047F);
				targetPitch = Mth.clamp((float) -verticalMovement * 1.25F, -0.75F, 1.05F);
			}

			float remaining = (float) Math.min(renderTime - lastRenderTime, 2.0D);
			lastRenderTime = renderTime;
			while (remaining > 0.0F) {
				float step = Math.min(remaining, MAX_SIMULATION_STEP);
				integrate(targetVerticalOffset, targetPitch, step);
				remaining -= step;
			}

			verticalOffset = Mth.clamp(verticalOffset, -MAX_VERTICAL_OFFSET, MAX_VERTICAL_OFFSET);
			pitch = Mth.clamp(pitch, -MAX_PITCH, MAX_PITCH);
			roll = Mth.clamp(roll, -MAX_ROLL, MAX_ROLL);
		}

		private void initialize(Player player, boolean onGround, boolean crouching,
			double verticalMovement, double renderTime) {
			initialized = true;
			lastTick = player.tickCount;
			lastOnGround = onGround;
			lastCrouching = crouching;
			lastVerticalMovement = verticalMovement;
			lastRenderTime = renderTime;
		}

		private void applyCrouchImpulse(Player player, boolean crouching) {
			float direction = crouching ? -1.0F : 1.0F;
			verticalVelocity = Mth.clamp(verticalVelocity + direction * 0.032F, -0.09F, 0.09F);
			pitchVelocity = Mth.clamp(pitchVelocity - direction * 0.28F, -1.1F, 1.1F);
			rollVelocity = Mth.clamp(rollVelocity + randomRoll(player, 0L) * 0.24F, -0.75F, 0.75F);
		}

		private void applyVerticalMotionImpulse(Player player, boolean onGround, double verticalMovement) {
			if (lastOnGround && !onGround) {
				float takeoffSpeed = Mth.clamp((float) verticalMovement, 0.0F, 0.7F);
				verticalVelocity -= takeoffSpeed * 0.075F;
				pitchVelocity -= takeoffSpeed * 0.9F;
				rollVelocity += randomRoll(player, 1L) * takeoffSpeed * 0.36F;
			} else if (!lastOnGround && onGround) {
				float impactSpeed = Mth.clamp((float) -lastVerticalMovement, 0.0F, 1.2F);
				verticalVelocity -= impactSpeed * 0.09F;
				pitchVelocity += impactSpeed * 1.0F;
				rollVelocity += randomRoll(player, 2L) * impactSpeed * 0.46F;
			}

			verticalVelocity = Mth.clamp(verticalVelocity, -0.11F, 0.11F);
			pitchVelocity = Mth.clamp(pitchVelocity, -1.25F, 1.25F);
			rollVelocity = Mth.clamp(rollVelocity, -0.85F, 0.85F);
		}

		private float randomRoll(Player player, long saltOffset) {
			long seed = player.getUUID().getMostSignificantBits()
				^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 19)
				^ ROLL_IMPULSE_SALT
				^ (player.tickCount + saltOffset) * 0x9E3779B97F4A7C15L;
			seed = (seed ^ seed >>> 30) * 0xBF58476D1CE4E5B9L;
			seed = (seed ^ seed >>> 27) * 0x94D049BB133111EBL;
			seed ^= seed >>> 31;
			return ((seed >>> 40) / 8388607.5F) - 1.0F;
		}

		private void integrate(float targetVerticalOffset, float targetPitch, float step) {
			verticalVelocity += ((targetVerticalOffset - verticalOffset) * POSITION_STIFFNESS
				- verticalVelocity * POSITION_DAMPING) * step;
			verticalOffset += verticalVelocity * step;

			pitchVelocity += ((targetPitch - pitch) * PITCH_STIFFNESS
				- pitchVelocity * PITCH_DAMPING) * step;
			pitch += pitchVelocity * step;

			rollVelocity += (-roll * ROLL_STIFFNESS - rollVelocity * ROLL_DAMPING) * step;
			roll += rollVelocity * step;
		}
	}
}
