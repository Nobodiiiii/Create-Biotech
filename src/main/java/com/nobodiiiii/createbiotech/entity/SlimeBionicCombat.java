package com.nobodiiiii.createbiotech.entity;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative melee timing and volume, deliberately independent from animation curves. */
public final class SlimeBionicCombat {
	public static final int NORMAL_ATTACK_TICKS = 15;
	public static final float CONE_HALF_ANGLE_DEGREES = 50.0f;
	public static final float MAX_AIM_YAW_DEGREES = 100.0f;
	public static final float MIN_AIM_PITCH_DEGREES = -89.0f;
	public static final float MAX_AIM_PITCH_DEGREES = 80.0f;
	private static final int NORMAL_WINDUP_TICKS = 5;
	private static final int NORMAL_ACTIVE_TICKS = 4;
	private static final int MIN_ACTIVE_TICKS = 2;
	private static final double CONE_SLOPE = Math.tan(CONE_HALF_ANGLE_DEGREES * Mth.DEG_TO_RAD);
	private static final double EPSILON = 1.0e-8d;

	private SlimeBionicCombat() {}

	public static int duration(int attackInterval) {
		return Math.max(1, Math.min(NORMAL_ATTACK_TICKS, attackInterval));
	}

	public static int activeStartTick(int duration) {
		int activeTicks = activeTicks(duration);
		int scaledWindup = Math.round((float) duration * NORMAL_WINDUP_TICKS
			/ NORMAL_ATTACK_TICKS);
		return Mth.clamp(scaledWindup, 0, duration - activeTicks);
	}

	public static int activeEndTick(int duration) {
		return activeStartTick(duration) + activeTicks(duration);
	}

	public static boolean isActiveTick(int elapsed, int duration) {
		return elapsed >= activeStartTick(duration) && elapsed < activeEndTick(duration);
	}

	private static int activeTicks(int duration) {
		int scaled = Math.round((float) duration * NORMAL_ACTIVE_TICKS / NORMAL_ATTACK_TICKS);
		return Math.min(duration, Math.max(MIN_ACTIVE_TICKS, scaled));
	}

	/** Broad-phase spherical reach from one shoulder; facing is intentionally checked at attack start. */
	public static boolean withinStartEnvelope(AABB target, Vec3 bodyPosition, float bodyYaw,
		SurgicalAssembly.ArmAttackGeometry arm) {
		Vec3 origin = worldOrigin(bodyPosition, bodyYaw, arm);
		return distanceToSqr(target, origin) <= arm.reach() * arm.reach();
	}

	/**
	 * Tests one target against a finite three-dimensional attack cone.
	 *
	 * <p>The target AABB is conservatively represented by its bounding sphere. Gameplay therefore
	 * remains forgiving around large and irregular entities, while the cone axis, reach and shoulder
	 * origin remain completely independent from the rendered hand path.</p>
	 */
	public static boolean intersects(AABB target, Vec3 bodyPosition, float bodyYaw,
		Vec3 aimDirection, SurgicalAssembly.ArmAttackGeometry arm) {
		Vec3 origin = worldOrigin(bodyPosition, bodyYaw, arm);
		Vec3 aim = normalizedOrForward(aimDirection, bodyYaw);
		Vec3 center = target.getCenter();
		double halfX = target.getXsize() * 0.5d;
		double halfY = target.getYsize() * 0.5d;
		double halfZ = target.getZsize() * 0.5d;
		double targetRadius = Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);
		Vec3 relative = center.subtract(origin);
		double axial = relative.dot(aim);
		if (axial < -targetRadius || axial > arm.reach() + targetRadius)
			return false;
		double radialSqr = Math.max(0.0d, relative.lengthSqr() - axial * axial);
		double coneRadius = arm.radius()
			+ Mth.clamp(axial, 0.0d, arm.reach()) * CONE_SLOPE;
		double allowed = coneRadius + targetRadius;
		return radialSqr <= allowed * allowed;
	}

	public static Vec3 worldOrigin(Vec3 bodyPosition, float bodyYaw,
		SurgicalAssembly.ArmAttackGeometry arm) {
		return arm.origin().yRot(-bodyYaw * Mth.DEG_TO_RAD).add(bodyPosition);
	}

	/** A stable target direction using the centre of the entity's full collision bounds. */
	public static Vec3 aimAt(AABB target, Vec3 origin, float fallbackYaw) {
		Vec3 direction = target.getCenter().subtract(origin);
		return normalizedOrForward(direction, fallbackYaw);
	}

	/** Restricts an intended world-space direction to the arm system's body-relative aim limits. */
	public static Vec3 constrainAim(Vec3 desired, float bodyYaw) {
		float relativeYaw = Mth.clamp(Mth.wrapDegrees(yaw(desired) - bodyYaw),
			-MAX_AIM_YAW_DEGREES, MAX_AIM_YAW_DEGREES);
		float pitch = Mth.clamp(pitch(desired), MIN_AIM_PITCH_DEGREES, MAX_AIM_PITCH_DEGREES);
		return direction(bodyYaw + relativeYaw, pitch);
	}

	/** Approaches a world-space direction by angular yaw/pitch limits without changing its length. */
	public static Vec3 approachAim(Vec3 current, Vec3 desired, float maximumDegrees) {
		float yaw = Mth.approachDegrees(yaw(current), yaw(desired), maximumDegrees);
		float pitch = Mth.approach(pitch(current), pitch(desired), maximumDegrees);
		return direction(yaw, pitch);
	}

	public static Vec3 direction(float yaw, float pitch) {
		float pitchRadians = -pitch * Mth.DEG_TO_RAD;
		float yawRadians = (yaw + 90.0f) * Mth.DEG_TO_RAD;
		float horizontal = Mth.cos(pitchRadians);
		return new Vec3(Mth.cos(yawRadians) * horizontal, Mth.sin(pitchRadians),
			Mth.sin(yawRadians) * horizontal).normalize();
	}

	public static float yaw(Vec3 direction) {
		if (direction == null || direction.lengthSqr() < EPSILON)
			return 0.0f;
		return (float) Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG - 90.0f;
	}

	public static float pitch(Vec3 direction) {
		if (direction == null || direction.lengthSqr() < EPSILON)
			return 0.0f;
		double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
		return -(float) Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG;
	}

	private static Vec3 normalizedOrForward(Vec3 direction, float bodyYaw) {
		return direction == null || direction.lengthSqr() < EPSILON
			? direction(bodyYaw, 0.0f) : direction.normalize();
	}

	private static double distanceToSqr(AABB bounds, Vec3 point) {
		double dx = point.x < bounds.minX ? bounds.minX - point.x
			: point.x > bounds.maxX ? point.x - bounds.maxX : 0.0d;
		double dy = point.y < bounds.minY ? bounds.minY - point.y
			: point.y > bounds.maxY ? point.y - bounds.maxY : 0.0d;
		double dz = point.z < bounds.minZ ? bounds.minZ - point.z
			: point.z > bounds.maxZ ? point.z - bounds.maxZ : 0.0d;
		return dx * dx + dy * dy + dz * dz;
	}
}
