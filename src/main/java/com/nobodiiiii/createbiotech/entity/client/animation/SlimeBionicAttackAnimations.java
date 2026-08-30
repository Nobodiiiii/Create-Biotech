package com.nobodiiiii.createbiotech.entity.client.animation;

import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Rotation;
import com.nobodiiiii.createbiotech.entity.animation.SlimeBionicAttackTiming;

import net.minecraft.util.Mth;

/** Authored attack catalogue for the bionic slime's articulated limbs. */
final class SlimeBionicAttackAnimations {
	private static final float MALEDICTUS_SWING_DURATION_SECONDS = 1.125f;
	private static final float ENDER_GOLEM_ATTACK_TICKS =
		SlimeBionicAttackTiming.ENDER_GOLEM_SOURCE_TICKS;
	private static final float DEEPLING_BRUTE_ATTACK_TICKS =
		SlimeBionicAttackTiming.DEEPLING_BRUTE_SOURCE_TICKS;
	private static final AttackPose DEEPLING_BRUTE_WINDUP = new AttackPose(
		Rotation.degrees(-12.5f, 10.0f, -12.5f),
		Rotation.degrees(0.0f, 0.0f, 75.0f), Rotation.IDENTITY,
		Rotation.degrees(12.5f, 0.0f, -10.0f), Rotation.IDENTITY);
	private static final AttackPose DEEPLING_BRUTE_STRIKE = new AttackPose(
		Rotation.degrees(30.0f, -30.0f, -7.5f),
		Rotation.degrees(-107.5f, -12.5f, 77.5f), Rotation.IDENTITY,
		Rotation.degrees(15.0f, 0.0f, -10.0f), Rotation.IDENTITY);
	private static final Rotation ENDER_GOLEM_WINDUP_BODY = Rotation.degrees(0.0f, 50.0f, 0.0f);
	private static final Rotation ENDER_GOLEM_WINDUP_SHOULDER = Rotation.degrees(40.0f, 20.0f, 0.0f);
	private static final Rotation ENDER_GOLEM_WINDUP_ELBOW = Rotation.degrees(-80.0f, 0.0f, 0.0f);
	private static final Rotation ENDER_GOLEM_WINDUP_OPPOSITE_SHOULDER =
		Rotation.degrees(35.0f, 0.0f, -10.0f);
	private static final Rotation ENDER_GOLEM_WINDUP_OPPOSITE_ELBOW =
		Rotation.degrees(-30.0f, 0.0f, 0.0f);
	// lowerbody -20 degrees + its child upperbody -40 degrees in the source hierarchy.
	private static final Rotation ENDER_GOLEM_STRIKE_BODY = Rotation.degrees(0.0f, -60.0f, 0.0f);
	private static final Rotation ENDER_GOLEM_STRIKE_SHOULDER = Rotation.degrees(-20.0f, 20.0f, 20.0f);
	private static final Rotation ENDER_GOLEM_STRIKE_ELBOW = Rotation.degrees(-20.0f, 0.0f, 0.0f);
	private static final Rotation ENDER_GOLEM_STRIKE_OPPOSITE_ELBOW =
		Rotation.degrees(-40.0f, 0.0f, 0.0f);

	/**
	 * The attacking-arm subset of Maledictus's {@code swing_attack_right} animation.
	 *
	 * <p>Source:
	 * {@code ref/1.21.1/Cataclysm/src/main/java/com/github/L_Ender/cataclysm/client/animation/Maledictus_Animation.java}.
	 * The body, right attacking arm and left balancing arm rotations are retained. The source's
	 * baked weapon, root and translation tracks remain omitted so an equipped item can follow the
	 * entity's own attacking forearm without importing Maledictus-specific geometry.</p>
	 */
	private static final RotationTrack WEAPON_SWING_BODY = new RotationTrack(
		new RotationKeyframe(0.0f, Rotation.degrees(0.0f, 0.0f, 0.0f)),
		new RotationKeyframe(0.3333f, Rotation.degrees(30.92f, -18.9477f, 2.2213f)),
		new RotationKeyframe(0.5833f, Rotation.degrees(-18.0307f, 17.6929f, -7.484f)),
		new RotationKeyframe(0.9583f, Rotation.degrees(0.0f, 0.0f, 0.0f)));
	private static final RotationTrack WEAPON_SWING_SHOULDER = new RotationTrack(
		new RotationKeyframe(0.0f, Rotation.degrees(0.0f, 0.0f, 0.0f)),
		new RotationKeyframe(0.3333f, Rotation.degrees(-84.9218f, -21.8243f, 44.1778f)),
		new RotationKeyframe(0.5833f, Rotation.degrees(26.4907f, -18.339f, 42.6343f)),
		new RotationKeyframe(0.9583f, Rotation.degrees(0.0f, 0.0f, 0.0f)));
	private static final RotationTrack WEAPON_SWING_ELBOW = new RotationTrack(
		new RotationKeyframe(0.0f, Rotation.degrees(0.0f, 0.0f, 0.0f)),
		new RotationKeyframe(0.3333f, Rotation.degrees(-40.3483f, -20.4366f, 29.0527f)),
		new RotationKeyframe(0.5833f, Rotation.degrees(-7.14f, 0.0f, 0.0f)),
		new RotationKeyframe(0.9583f, Rotation.degrees(0.0f, 0.0f, 0.0f)));
	private static final RotationTrack WEAPON_SWING_OPPOSITE_SHOULDER = new RotationTrack(
		new RotationKeyframe(0.0f, Rotation.degrees(0.0f, 0.0f, 0.0f)),
		new RotationKeyframe(0.3333f, Rotation.degrees(33.5119f, 6.743f, -27.941f)),
		new RotationKeyframe(0.5833f, Rotation.degrees(4.2643f, -9.2743f, -26.4761f)),
		new RotationKeyframe(0.9583f, Rotation.degrees(0.0f, 0.0f, 0.0f)));
	private static final RotationTrack WEAPON_SWING_OPPOSITE_ELBOW = new RotationTrack(
		new RotationKeyframe(0.0f, Rotation.degrees(0.0f, 0.0f, 0.0f)),
		new RotationKeyframe(0.3333f, Rotation.degrees(-22.1665f, 13.4716f, -18.1914f)),
		new RotationKeyframe(0.5833f, Rotation.degrees(2.1921f, 9.3762f, -12.6612f)),
		new RotationKeyframe(0.9583f, Rotation.degrees(0.0f, 0.0f, 0.0f)));

	private SlimeBionicAttackAnimations() {}

	/**
	 * Deepling Brute's complete right-handed melee curve for a rigid, elbowless pair of arms.
	 *
	 * <p>Source:
	 * {@code ref/1.21.1/Cataclysm/src/main/java/com/github/L_Ender/cataclysm/client/model/entity/Deepling_Brute_Model.java}.
	 * The attacking hand, balancing hand and body channels retain the source's 4-tick wind-up,
	 * 2-tick strike and 14-tick reset. Left-handed attacks are mirrored by the caller.</p>
	 */
	static AttackPose elbowlessBruteSwing(float progress) {
		float tick = Mth.clamp(progress, 0.0f, 1.0f) * DEEPLING_BRUTE_ATTACK_TICKS;
		if (tick < 4.0f)
			return interpolate(AttackPose.IDENTITY, DEEPLING_BRUTE_WINDUP, tick / 4.0f);
		if (tick < 6.0f)
			return interpolate(DEEPLING_BRUTE_WINDUP, DEEPLING_BRUTE_STRIKE,
				(tick - 4.0f) / 2.0f);
		return interpolate(DEEPLING_BRUTE_STRIKE, AttackPose.IDENTITY,
			(tick - 6.0f) / 14.0f);
	}

	static AttackPose weaponSwing(float progress) {
		float sourceTime = Mth.clamp(progress, 0.0f, 1.0f)
			* MALEDICTUS_SWING_DURATION_SECONDS;
		return new AttackPose(WEAPON_SWING_BODY.sample(sourceTime),
			WEAPON_SWING_SHOULDER.sample(sourceTime),
			WEAPON_SWING_ELBOW.sample(sourceTime),
			WEAPON_SWING_OPPOSITE_SHOULDER.sample(sourceTime),
			WEAPON_SWING_OPPOSITE_ELBOW.sample(sourceTime));
	}

	/**
	 * Ender Golem's Attack 1 curve, reduced to its torso and both articulated arms, then retimed
	 * externally. Attack 2 is the exact left/right mirror and is produced by the caller.
	 *
	 * <p>Pose source:
	 * {@code ref/1.21.1/Cataclysm/src/main/java/com/github/L_Ender/cataclysm/client/model/entity/Ender_Golem_Model.java}.
	 * The torso channel uses the source hierarchy's effective upper-body yaw: {@code +50} degrees
	 * during wind-up and {@code -60} during the strike ({@code -20} lower plus {@code -40} upper).
	 * The complete source curve remains intact: 10 ticks into wind-up, 5 into strike, 5 held, then
	 * 5 back to rest. The common timing contract maps it into the normal 15-tick playback window and
	 * compresses that window further only when the real attack interval is faster.</p>
	 */
	static AttackPose emptyHandGolemSwing(float progress) {
		float tick = Mth.clamp(progress, 0.0f, 1.0f) * ENDER_GOLEM_ATTACK_TICKS;
		AttackPose pose;
		if (tick < 10.0f)
			pose = interpolate(AttackPose.IDENTITY,
				new AttackPose(ENDER_GOLEM_WINDUP_BODY, ENDER_GOLEM_WINDUP_SHOULDER,
					ENDER_GOLEM_WINDUP_ELBOW, ENDER_GOLEM_WINDUP_OPPOSITE_SHOULDER,
					ENDER_GOLEM_WINDUP_OPPOSITE_ELBOW), tick / 10.0f);
		else if (tick < 15.0f)
			pose = interpolate(new AttackPose(ENDER_GOLEM_WINDUP_BODY, ENDER_GOLEM_WINDUP_SHOULDER,
					ENDER_GOLEM_WINDUP_ELBOW, ENDER_GOLEM_WINDUP_OPPOSITE_SHOULDER,
					ENDER_GOLEM_WINDUP_OPPOSITE_ELBOW),
				new AttackPose(ENDER_GOLEM_STRIKE_BODY, ENDER_GOLEM_STRIKE_SHOULDER,
					ENDER_GOLEM_STRIKE_ELBOW, Rotation.IDENTITY,
					ENDER_GOLEM_STRIKE_OPPOSITE_ELBOW),
				(tick - 10.0f) / 5.0f);
		else if (tick < 20.0f)
			pose = new AttackPose(ENDER_GOLEM_STRIKE_BODY, ENDER_GOLEM_STRIKE_SHOULDER,
				ENDER_GOLEM_STRIKE_ELBOW, Rotation.IDENTITY,
				ENDER_GOLEM_STRIKE_OPPOSITE_ELBOW);
		else
			pose = interpolate(new AttackPose(ENDER_GOLEM_STRIKE_BODY, ENDER_GOLEM_STRIKE_SHOULDER,
				ENDER_GOLEM_STRIKE_ELBOW, Rotation.IDENTITY, ENDER_GOLEM_STRIKE_OPPOSITE_ELBOW),
				AttackPose.IDENTITY, (tick - 20.0f) / 5.0f);
		return pose.withOppositeShoulder(emptyHandOppositeShoulder(tick));
	}

	/** Moves the balancing upper arm back early, holds it through impact, then joins recovery. */
	private static Rotation emptyHandOppositeShoulder(float tick) {
		if (tick < 6.0f)
			return interpolateEased(Rotation.IDENTITY, ENDER_GOLEM_WINDUP_OPPOSITE_SHOULDER,
				tick / 6.0f);
		if (tick < 20.0f)
			return ENDER_GOLEM_WINDUP_OPPOSITE_SHOULDER;
		return interpolateEased(ENDER_GOLEM_WINDUP_OPPOSITE_SHOULDER, Rotation.IDENTITY,
			(tick - 20.0f) / 5.0f);
	}

	private static AttackPose interpolate(AttackPose start, AttackPose end, float progress) {
		float eased = Mth.sin(Mth.clamp(progress, 0.0f, 1.0f) * Mth.HALF_PI);
		return new AttackPose(interpolate(start.body(), end.body(), eased),
			interpolate(start.attackingShoulder(), end.attackingShoulder(), eased),
			interpolate(start.attackingElbow(), end.attackingElbow(), eased),
			interpolate(start.oppositeShoulder(), end.oppositeShoulder(), eased),
			interpolate(start.oppositeElbow(), end.oppositeElbow(), eased));
	}

	private static Rotation interpolate(Rotation start, Rotation end, float progress) {
		return new Rotation(Mth.lerp(progress, start.x(), end.x()),
			Mth.lerp(progress, start.y(), end.y()), Mth.lerp(progress, start.z(), end.z()));
	}

	private static Rotation interpolateEased(Rotation start, Rotation end, float progress) {
		float eased = Mth.sin(Mth.clamp(progress, 0.0f, 1.0f) * Mth.HALF_PI);
		return interpolate(start, end, eased);
	}

	record AttackPose(Rotation body, Rotation attackingShoulder, Rotation attackingElbow,
		Rotation oppositeShoulder, Rotation oppositeElbow) {
		private static final AttackPose IDENTITY =
			new AttackPose(Rotation.IDENTITY, Rotation.IDENTITY, Rotation.IDENTITY,
				Rotation.IDENTITY, Rotation.IDENTITY);

		private AttackPose withOppositeShoulder(Rotation rotation) {
			return new AttackPose(body, attackingShoulder, attackingElbow, rotation, oppositeElbow);
		}
	}

	private record RotationKeyframe(float time, Rotation rotation) {}

	/** Catmull-Rom rotation track matching the interpolation used by the source animation. */
	private static final class RotationTrack {
		private final RotationKeyframe[] keyframes;

		private RotationTrack(RotationKeyframe... keyframes) {
			this.keyframes = keyframes;
		}

		private Rotation sample(float time) {
			if (keyframes.length == 0)
				return Rotation.IDENTITY;
			if (time <= keyframes[0].time())
				return keyframes[0].rotation();
			int upper = 1;
			while (upper < keyframes.length && time > keyframes[upper].time())
				upper++;
			if (upper >= keyframes.length)
				return keyframes[keyframes.length - 1].rotation();
			RotationKeyframe start = keyframes[upper - 1];
			RotationKeyframe end = keyframes[upper];
			Rotation previous = keyframes[Math.max(0, upper - 2)].rotation();
			Rotation next = keyframes[Math.min(keyframes.length - 1, upper + 1)].rotation();
			float span = end.time() - start.time();
			float progress = span <= 0.0f ? 0.0f : (time - start.time()) / span;
			return new Rotation(
				catmullRom(previous.x(), start.rotation().x(), end.rotation().x(), next.x(), progress),
				catmullRom(previous.y(), start.rotation().y(), end.rotation().y(), next.y(), progress),
				catmullRom(previous.z(), start.rotation().z(), end.rotation().z(), next.z(), progress));
		}

		private static float catmullRom(float previous, float start, float end, float next,
			float progress) {
			float squared = progress * progress;
			float cubed = squared * progress;
			return 0.5f * (2.0f * start + (end - previous) * progress
				+ (2.0f * previous - 5.0f * start + 4.0f * end - next) * squared
				+ (-previous + 3.0f * start - 3.0f * end + next) * cubed);
		}
	}
}
