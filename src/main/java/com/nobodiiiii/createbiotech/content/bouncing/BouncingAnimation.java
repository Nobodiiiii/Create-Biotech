package com.nobodiiiii.createbiotech.content.bouncing;

import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.registry.CBMobEffects;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Client-side, ground-anchored jelly deformation for the bouncing mob effect. */
public final class BouncingAnimation {
	private static final float IDLE_SWAY_KEYFRAME_TICKS = 18.0F;
	private static final float ACTIVE_SWAY_KEYFRAME_TICKS = 6.0F;
	private static final long IDLE_X_NOISE_SALT = 0xA24BAED4963EE407L;
	private static final long IDLE_Z_NOISE_SALT = 0x9FB21C651E98DF25L;
	private static final long ACTIVE_X_NOISE_SALT = 0x632BE59BD9B4E019L;
	private static final long ACTIVE_Z_NOISE_SALT = 0x9E3779B97F4A7C15L;
	private static final long IDLE_STRETCH_A_SALT = 0xC6BC279692B5CC83L;
	private static final long IDLE_STRETCH_B_SALT = 0xDB4F0B9175AE2165L;
	private static final long CROUCH_X_IMPULSE_SALT = 0x8CB92BA72F3D8DD7L;
	private static final long CROUCH_Z_IMPULSE_SALT = 0x58F38DED84B3A73BL;

	private static final float STRETCH_STIFFNESS = 0.32F;
	private static final float STRETCH_DAMPING = 0.38F;
	private static final float SWAY_STIFFNESS = 0.22F;
	private static final float SWAY_DAMPING = 0.34F;
	private static final float HEIGHT_STIFFNESS = 0.48F;
	private static final float HEIGHT_DAMPING = 0.68F;
	private static final float MAX_SIMULATION_STEP = 0.25F;
	private static final float MAX_STRETCH = 0.38F;
	private static final float MAX_SHEAR = 0.14F;
	private static final float MIN_HEIGHT_SCALE = BouncingCrouch.HEIGHT_SCALE - 0.06F;
	private static final float MAX_HEIGHT_SCALE = 1.10F;

	private static final Map<Player, JellyState> STATES = new WeakHashMap<>();
	private static final Map<Player, JellyState> CAMERA_STATES = new WeakHashMap<>();

	private BouncingAnimation() {}

	public static void applyVisualTransform(Player player, float partialTick, PoseStack poseStack) {
		if (!player.isAlive() || player.isSleeping() || !player.hasEffect(CBMobEffects.BOUNCING)) {
			STATES.remove(player);
			return;
		}

		VisualDeformation visual = sample(player, partialTick, STATES, false);
		float horizontalScale = Mth.clamp(1.0F / Mth.sqrt(visual.jellyScale()), 0.82F, 1.28F);

		// The entity render origin is at its feet. This affine transform has no Y
		// translation, and its shear terms are proportional to height, so the bottom
		// stays planted while the body stretches, squashes, widens and sways above it.
		Matrix4f deformationMatrix = new Matrix4f()
			.m00(horizontalScale)
			.m10(visual.shearX())
			.m11(visual.verticalScale())
			.m12(visual.shearZ())
			.m22(horizontalScale);
		poseStack.mulPose(deformationMatrix);
	}

	/** Samples the first-person jelly simulation driven only by crouch transitions. */
	public static VisualDeformation getCameraDeformation(Player player, float partialTick) {
		if (!player.isAlive() || player.isSleeping() || !player.hasEffect(CBMobEffects.BOUNCING)) {
			resetCamera(player);
			return VisualDeformation.IDENTITY;
		}
		return sample(player, partialTick, CAMERA_STATES, true);
	}

	public static void resetCamera(Player player) {
		CAMERA_STATES.remove(player);
	}

	private static VisualDeformation sample(Player player, float partialTick,
		Map<Player, JellyState> states, boolean cameraOnly) {
		double renderTime = player.tickCount + partialTick;
		JellyState state = states.computeIfAbsent(player, ignored -> new JellyState());
		state.update(player, partialTick, renderTime, cameraOnly);
		float jellyScale = Mth.clamp(1.0F + state.stretch, 1.0F - MAX_STRETCH, 1.0F + MAX_STRETCH);
		return new VisualDeformation(state.heightScale * jellyScale, jellyScale,
			state.shearX, state.shearZ);
	}

	private static Targets getTargets(Player player, float partialTick, double renderTime) {
		float walkAmount = Mth.clamp(player.walkAnimation.speed(partialTick) * 1.5F, 0.0F, 1.0F);
		float smoothedWalkAmount = smoothstep(walkAmount);
		Vec3 movement = player.getDeltaMovement();
		float verticalMovement = player.onGround() || player.isPassenger() ? 0.0F : (float) movement.y;
		float airborneEnergy = Mth.clamp(Math.abs(verticalMovement) * 2.5F, 0.0F, 1.0F);
		float activeBlend = smoothstep(Mth.clamp(Math.max(smoothedWalkAmount, airborneEnergy), 0.0F, 1.0F));
		long playerSeed = player.getUUID().getMostSignificantBits()
			^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 23);

		double idlePhaseA = renderTime * Mth.TWO_PI / 46.0D
			+ randomSigned(playerSeed, 0L, IDLE_STRETCH_A_SALT) * Math.PI;
		double idlePhaseB = renderTime * Mth.TWO_PI / 73.0D
			+ randomSigned(playerSeed, 0L, IDLE_STRETCH_B_SALT) * Math.PI;
		float idleWave = (float) (0.68D * Math.sin(idlePhaseA) + 0.32D * Math.sin(idlePhaseB));
		float walkWave = (float) Math.sin(player.walkAnimation.position(partialTick) * 0.6662F);
		float velocityLag = Mth.clamp(-verticalMovement * 0.38F, -0.16F, 0.22F);
		float targetStretch = Mth.clamp(
			0.05F * idleWave + 0.11F * smoothedWalkAmount * walkWave + velocityLag,
			-MAX_STRETCH, MAX_STRETCH);

		double idleSwayTime = renderTime / IDLE_SWAY_KEYFRAME_TICKS;
		double activeSwayTime = renderTime / ACTIVE_SWAY_KEYFRAME_TICKS;
		float randomX = Mth.lerp(activeBlend,
			smoothRandom(playerSeed, idleSwayTime, IDLE_X_NOISE_SALT),
			smoothRandom(playerSeed, activeSwayTime, ACTIVE_X_NOISE_SALT));
		float randomZ = Mth.lerp(activeBlend,
			smoothRandom(playerSeed, idleSwayTime, IDLE_Z_NOISE_SALT),
			smoothRandom(playerSeed, activeSwayTime, ACTIVE_Z_NOISE_SALT));
		float randomLengthSqr = randomX * randomX + randomZ * randomZ;
		if (randomLengthSqr > 1.0F) {
			float inverseLength = Mth.invSqrt(randomLengthSqr);
			randomX *= inverseLength;
			randomZ *= inverseLength;
		}

		float randomSway = 0.018F + 0.05F * smoothedWalkAmount + 0.04F * airborneEnergy;
		float targetShearX = Mth.clamp(randomX * randomSway - (float) movement.x * 0.10F,
			-MAX_SHEAR, MAX_SHEAR);
		float targetShearZ = Mth.clamp(randomZ * randomSway - (float) movement.z * 0.10F,
			-MAX_SHEAR, MAX_SHEAR);
		float targetHeightScale = BouncingCrouch.isActive(player) ? BouncingCrouch.HEIGHT_SCALE : 1.0F;
		return new Targets(targetStretch, targetShearX, targetShearZ, targetHeightScale);
	}

	private static Targets getCameraTargets(Player player) {
		float targetHeightScale = BouncingCrouch.isActive(player) ? BouncingCrouch.HEIGHT_SCALE : 1.0F;
		return new Targets(0.0F, 0.0F, 0.0F, targetHeightScale);
	}

	private static float smoothstep(float value) {
		return value * value * (3.0F - 2.0F * value);
	}

	private static float smoothRandom(long playerSeed, double time, long salt) {
		long keyframe = (long) Math.floor(time);
		float progress = (float) (time - keyframe);
		float smoothedProgress = progress * progress * progress
			* (progress * (progress * 6.0F - 15.0F) + 10.0F);
		return Mth.lerp(smoothedProgress,
			randomSigned(playerSeed, keyframe, salt),
			randomSigned(playerSeed, keyframe + 1L, salt));
	}

	private static float randomSigned(long playerSeed, long keyframe, long salt) {
		long value = playerSeed ^ salt ^ keyframe * 0xD1342543DE82EF95L;
		value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
		value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
		value ^= value >>> 31;
		return ((value >>> 40) / 8388607.5F) - 1.0F;
	}

	public record VisualDeformation(float verticalScale, float jellyScale, float shearX, float shearZ) {
		private static final VisualDeformation IDENTITY = new VisualDeformation(1.0F, 1.0F, 0.0F, 0.0F);
	}

	private record Targets(float stretch, float shearX, float shearZ, float heightScale) {}

	private static final class JellyState {
		private boolean initialized;
		private int lastTick;
		private boolean lastOnGround;
		private boolean lastCrouching;
		private double lastRenderTime;
		private Vec3 lastMovement = Vec3.ZERO;

		private float stretch;
		private float stretchVelocity;
		private float shearX;
		private float shearVelocityX;
		private float shearZ;
		private float shearVelocityZ;
		private float heightScale;
		private float heightVelocity;

		private void update(Player player, float partialTick, double renderTime, boolean cameraOnly) {
			Vec3 movement = player.getDeltaMovement();
			boolean onGround = player.onGround();
			Targets targets = cameraOnly
				? getCameraTargets(player)
				: getTargets(player, partialTick, renderTime);

			if (!initialized || renderTime < lastRenderTime || renderTime - lastRenderTime > 5.0D) {
				initialize(player, movement, onGround, renderTime, targets);
				return;
			}

			boolean crouching = BouncingCrouch.isActive(player);
			if (crouching != lastCrouching) {
				applyCrouchImpulse(player, crouching);
				lastCrouching = crouching;
			}

			if (player.tickCount != lastTick) {
				if (!cameraOnly)
					applyMotionImpulse(movement, onGround);
				lastTick = player.tickCount;
				lastMovement = movement;
				lastOnGround = onGround;
			}

			float remaining = (float) Math.min(renderTime - lastRenderTime, 2.0D);
			lastRenderTime = renderTime;
			while (remaining > 0.0F) {
				float step = Math.min(remaining, MAX_SIMULATION_STEP);
				integrate(targets, step);
				remaining -= step;
			}

			stretch = Mth.clamp(stretch, -MAX_STRETCH, MAX_STRETCH);
			shearX = Mth.clamp(shearX, -MAX_SHEAR, MAX_SHEAR);
			shearZ = Mth.clamp(shearZ, -MAX_SHEAR, MAX_SHEAR);
			heightScale = Mth.clamp(heightScale, MIN_HEIGHT_SCALE, MAX_HEIGHT_SCALE);
		}

		private void initialize(Player player, Vec3 movement, boolean onGround, double renderTime,
			Targets targets) {
			initialized = true;
			lastTick = player.tickCount;
			lastOnGround = onGround;
			lastCrouching = BouncingCrouch.isActive(player);
			lastRenderTime = renderTime;
			lastMovement = movement;
			stretch = targets.stretch;
			shearX = targets.shearX;
			shearZ = targets.shearZ;
			heightScale = targets.heightScale;
			stretchVelocity = 0.0F;
			shearVelocityX = 0.0F;
			shearVelocityZ = 0.0F;
			heightVelocity = 0.0F;
		}

		private void applyCrouchImpulse(Player player, boolean crouching) {
			float direction = crouching ? -1.0F : 1.0F;
			heightVelocity = Mth.clamp(heightVelocity + direction * 0.045F, -0.16F, 0.16F);
			stretchVelocity = Mth.clamp(stretchVelocity + direction * 0.14F, -0.28F, 0.28F);

			long playerSeed = player.getUUID().getMostSignificantBits()
				^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 23);
			float randomX = randomSigned(playerSeed, player.tickCount, CROUCH_X_IMPULSE_SALT);
			float randomZ = randomSigned(playerSeed, player.tickCount, CROUCH_Z_IMPULSE_SALT);
			float inverseLength = Mth.invSqrt(Math.max(randomX * randomX + randomZ * randomZ, 0.001F));
			shearVelocityX = Mth.clamp(shearVelocityX + randomX * inverseLength * 0.045F,
				-0.12F, 0.12F);
			shearVelocityZ = Mth.clamp(shearVelocityZ + randomZ * inverseLength * 0.045F,
				-0.12F, 0.12F);
		}

		private void applyMotionImpulse(Vec3 movement, boolean onGround) {
			float accelerationX = (float) (movement.x - lastMovement.x);
			float accelerationZ = (float) (movement.z - lastMovement.z);
			shearVelocityX = Mth.clamp(shearVelocityX - accelerationX * 0.42F, -0.12F, 0.12F);
			shearVelocityZ = Mth.clamp(shearVelocityZ - accelerationZ * 0.42F, -0.12F, 0.12F);

			if (lastOnGround && !onGround) {
				float takeoffSpeed = Mth.clamp((float) movement.y, 0.0F, 0.7F);
				stretchVelocity -= takeoffSpeed * 0.55F;
			} else if (!lastOnGround && onGround) {
				float impactSpeed = Mth.clamp((float) -lastMovement.y, 0.0F, 1.2F);
				stretchVelocity -= impactSpeed * 0.65F;
			}
			stretchVelocity = Mth.clamp(stretchVelocity, -0.28F, 0.28F);
		}

		private void integrate(Targets targets, float step) {
			stretchVelocity += ((targets.stretch - stretch) * STRETCH_STIFFNESS
				- stretchVelocity * STRETCH_DAMPING) * step;
			stretch += stretchVelocity * step;

			shearVelocityX += ((targets.shearX - shearX) * SWAY_STIFFNESS
				- shearVelocityX * SWAY_DAMPING) * step;
			shearX += shearVelocityX * step;

			shearVelocityZ += ((targets.shearZ - shearZ) * SWAY_STIFFNESS
				- shearVelocityZ * SWAY_DAMPING) * step;
			shearZ += shearVelocityZ * step;

			heightVelocity += ((targets.heightScale - heightScale) * HEIGHT_STIFFNESS
				- heightVelocity * HEIGHT_DAMPING) * step;
			heightScale += heightVelocity * step;
		}
	}
}
