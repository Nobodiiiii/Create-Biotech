package com.nobodiiiii.createbiotech.entity;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCombatCalibration;
import com.nobodiiiii.createbiotech.entity.animation.SlimeBionicAttackTiming;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server melee rules. Geometry is static; articulated contact timing uses authored curve markers. */
public final class SlimeBionicCombat {
	public static final int MAX_ACTION_TICKS = SlimeBionicAttackTiming.PLAYBACK_TICKS;
	public static final float HORIZONTAL_HALF_ANGLE_DEGREES = 35.0f;
	public static final float VERTICAL_HALF_ANGLE_DEGREES = 45.0f;
	public static final float MAX_AIM_YAW_DEGREES = 55.0f;
	public static final float MIN_AIM_PITCH_DEGREES = -60.0f;
	public static final float MAX_AIM_PITCH_DEGREES = 60.0f;
	public static final float FRONT_HALF_ANGLE_DEGREES = 90.0f;
	private static final int ACTIVE_TICKS = 5;
	private static final double EPSILON = 1.0e-8d;
	private static final AngularRange BODY_RANGE = new AngularRange(-FRONT_HALF_ANGLE_DEGREES,
		FRONT_HALF_ANGLE_DEGREES, -90.0f, 90.0f);

	private SlimeBionicCombat() {}

	/** Logical contact tracking lasts for the same retimed playback duration for every arm type. */
	public static int duration(int attackInterval) {
		return SlimeBionicAttackTiming.playbackTicks(attackInterval);
	}

	/** Rigid arms retain their early contact position; articulated arms follow their authored impact. */
	public static int contactStartTick(int attackInterval, boolean hasElbow, boolean weapon) {
		return hasElbow
			? SlimeBionicAttackTiming.articulatedImpactTick(attackInterval, weapon)
			: Mth.clamp(Math.round(attackInterval * 0.2f), 3, 6);
	}

	public static boolean isContactTick(int elapsed, int contactStart) {
		return elapsed >= contactStart && elapsed < contactStart + ACTIVE_TICKS;
	}

	/** Missing arm geometry gets a short body strike, including old assemblies and armless bodies. */
	public static SurgicalAssembly.ArmAttackGeometry fallbackArm(float bodyWidth, float bodyHeight) {
		float reach = Mth.clamp(bodyWidth * 0.5f + 0.6f, 0.75f, 1.5f);
		float height = Math.max(0.1f, bodyHeight * 0.5f);
		return new SurgicalAssembly.ArmAttackGeometry(new Vec3(0.0d, height, 0.0d), reach,
			height - reach, height + reach, 0.15f, (float) SurgicalCombatCalibration.ZOMBIE_ARM_VOLUME);
	}

	/** Distance only, so navigation can still approach and turn toward a target behind the body. */
	public static boolean withinStartEnvelope(AABB target, Vec3 bodyPosition, float bodyYaw,
		SurgicalAssembly.ArmAttackGeometry arm) {
		return distanceToSqr(target, worldOrigin(bodyPosition, bodyYaw, arm))
			<= (double) arm.reach() * arm.reach() + EPSILON;
	}

	/** A swing can begin only after the body faces the target within the shared anatomical limits. */
	public static boolean canStart(AABB target, Vec3 bodyPosition, float bodyYaw,
		SurgicalAssembly.ArmAttackGeometry arm) {
		if (!withinStartEnvelope(target, bodyPosition, bodyYaw, arm))
			return false;
		Vec3 desired = aimAt(target, worldOrigin(bodyPosition, bodyYaw, arm), bodyYaw);
		if (Math.abs(Mth.wrapDegrees(yaw(desired) - bodyYaw)) > MAX_AIM_YAW_DEGREES)
			return false;
		if (arm.restDirection() == null
			&& (pitch(desired) < MIN_AIM_PITCH_DEGREES || pitch(desired) > MAX_AIM_PITCH_DEGREES))
			return false;
		// Clamp the aim, then test the whole box. A tall/wide target may enter the allowed sector
		// even when its nearest point lies just outside this arm's mounted range.
		return intersects(target, bodyPosition, bodyYaw, constrainAim(desired, bodyYaw, arm), arm);
	}

	/** Preserve the pre-specialization coordination count: rest posture never changes the cadence. */
	public static boolean contributesToCadence(AABB target, Vec3 bodyPosition, float bodyYaw,
		SurgicalAssembly.ArmAttackGeometry arm) {
		if (!withinStartEnvelope(target, bodyPosition, bodyYaw, arm))
			return false;
		Vec3 desired = aimAt(target, worldOrigin(bodyPosition, bodyYaw, arm), bodyYaw);
		return Math.abs(Mth.wrapDegrees(yaw(desired) - bodyYaw)) <= MAX_AIM_YAW_DEGREES
			&& pitch(desired) >= MIN_AIM_PITCH_DEGREES && pitch(desired) <= MAX_AIM_PITCH_DEGREES
			&& intersects(target, bodyPosition, bodyYaw, arm,
				attackRange(BODY_RANGE, bodyYaw, yaw(desired), pitch(desired)));
	}

	/** Body-relative angular limits baked from a mounted arm; negative Minecraft pitch points up. */
	public static AngularRange activityRange(SurgicalAssembly.ArmAttackGeometry arm) {
		Vec3 rest = arm.restDirection();
		if (rest == null)
			return BODY_RANGE;
		float up = (float) Mth.clamp(rest.y, -1.0d, 1.0d);
		float sideBias = (float) (-rest.x * 20.0d);
		float minimumPitch = -50.0f - Math.max(up, 0.0f) * 30.0f + Math.max(-up, 0.0f) * 15.0f;
		float maximumPitch = 50.0f - Math.max(up, 0.0f) * 15.0f + Math.max(-up, 0.0f) * 20.0f;
		return new AngularRange(Math.max(-FRONT_HALF_ANGLE_DEGREES, sideBias - FRONT_HALF_ANGLE_DEGREES),
			Math.min(FRONT_HALF_ANGLE_DEGREES, sideBias + FRONT_HALF_ANGLE_DEGREES), minimumPitch, maximumPitch);
	}

	/** The exact angular intersection consumed by both target collision and the range preview. */
	public static AngularRange attackRange(SurgicalAssembly.ArmAttackGeometry arm, float bodyYaw,
		float aimYaw, float aimPitch) {
		return attackRange(activityRange(arm), bodyYaw, aimYaw, aimPitch);
	}

	private static AngularRange attackRange(AngularRange activity, float bodyYaw, float aimYaw, float aimPitch) {
		float relativeYaw = Mth.wrapDegrees(aimYaw - bodyYaw);
		return new AngularRange(Math.max(activity.minimumYaw(), relativeYaw - HORIZONTAL_HALF_ANGLE_DEGREES),
			Math.min(activity.maximumYaw(), relativeYaw + HORIZONTAL_HALF_ANGLE_DEGREES),
			Math.max(activity.minimumPitch(), aimPitch - VERTICAL_HALF_ANGLE_DEGREES),
			Math.min(activity.maximumPitch(), aimPitch + VERTICAL_HALF_ANGLE_DEGREES));
	}

	/** Used only for choosing an arm, never as a damage, reach or recovery multiplier. */
	public static double posturePreferencePenalty(Vec3 aim, float bodyYaw, SurgicalAssembly.ArmAttackGeometry arm) {
		Vec3 rest = arm.restDirection();
		if (rest == null)
			return 0.0d;
		double preferredYaw = -rest.x * 20.0d;
		double preferredPitch = -rest.y * 20.0d;
		return Math.abs(Mth.wrapDegrees(yaw(aim) - bodyYaw) - preferredYaw) * 0.5d
			+ Math.abs(pitch(aim) - preferredPitch) * 0.75d;
	}

	/**
	 * Intersects the actual target box with a spherical yaw/pitch sector, clipped to the body's front.
	 *
	 * <p>First clip the horizontal rectangle to the yaw sector and front plane. Its attainable radial
	 * distances form an interval. Clipping that interval times the target's height to the pitch sector
	 * reduces the remaining test to a circle. This preserves partial box contacts without turning tall
	 * targets into wide spheres, and every contact remains inside the arm's true shoulder-to-tip reach.</p>
	 */
	public static boolean intersects(AABB target, Vec3 bodyPosition, float bodyYaw,
		Vec3 aimDirection, SurgicalAssembly.ArmAttackGeometry arm) {
		Vec3 aim = normalizedOrForward(aimDirection, bodyYaw);
		return intersects(target, bodyPosition, bodyYaw, arm, attackRange(arm, bodyYaw, yaw(aim), pitch(aim)));
	}

	private static boolean intersects(AABB target, Vec3 bodyPosition, float bodyYaw,
		SurgicalAssembly.ArmAttackGeometry arm, AngularRange range) {
		if (range.isEmpty() || !withinStartEnvelope(target, bodyPosition, bodyYaw, arm))
			return false;
		Vec3 origin = worldOrigin(bodyPosition, bodyYaw, arm);
		List<Point> horizontal = rectangle(target.minX - origin.x, target.minZ - origin.z,
			target.maxX - origin.x, target.maxZ - origin.z);
		Vec3 forward = direction(bodyYaw, 0.0f);
		horizontal = clip(horizontal, forward.x, forward.z, origin.subtract(bodyPosition).dot(forward));
		Vec3 lower = direction(bodyYaw + range.minimumYaw(), 0.0f);
		Vec3 upper = direction(bodyYaw + range.maximumYaw(), 0.0f);
		horizontal = clip(horizontal, -lower.z, lower.x, 0.0d);
		horizontal = clip(horizontal, upper.z, -upper.x, 0.0d);
		if (horizontal.isEmpty())
			return false;

		double minimumRadius = Math.sqrt(minimumDistanceSqr(horizontal));
		double maximumRadius = 0.0d;
		for (Point point : horizontal)
			maximumRadius = Math.max(maximumRadius, Math.hypot(point.x(), point.y()));
		List<Point> vertical = rectangle(minimumRadius, target.minY - origin.y,
			maximumRadius, target.maxY - origin.y);
		double lowerElevation = Math.toRadians(-range.maximumPitch());
		double upperElevation = Math.toRadians(-range.minimumPitch());
		vertical = clip(vertical, -Math.sin(lowerElevation), Math.cos(lowerElevation), 0.0d);
		vertical = clip(vertical, Math.sin(upperElevation), -Math.cos(upperElevation), 0.0d);
		return !vertical.isEmpty()
			&& minimumDistanceSqr(vertical) <= (double) arm.reach() * arm.reach() + EPSILON;
	}

	public static Vec3 worldOrigin(Vec3 bodyPosition, float bodyYaw,
		SurgicalAssembly.ArmAttackGeometry arm) {
		return arm.origin().yRot(-bodyYaw * Mth.DEG_TO_RAD).add(bodyPosition);
	}

	/** Aim at the nearest part of the collision box, so a tall target's centre need not be reachable. */
	public static Vec3 aimAt(AABB target, Vec3 origin, float fallbackYaw) {
		Vec3 point = new Vec3(Mth.clamp(origin.x, target.minX, target.maxX),
			Mth.clamp(origin.y, target.minY, target.maxY), Mth.clamp(origin.z, target.minZ, target.maxZ));
		return normalizedOrForward(point.subtract(origin), fallbackYaw);
	}

	public static Vec3 constrainAim(Vec3 desired, float bodyYaw) {
		return constrainAim(desired, bodyYaw, BODY_RANGE);
	}

	public static Vec3 constrainAim(Vec3 desired, float bodyYaw, SurgicalAssembly.ArmAttackGeometry arm) {
		return constrainAim(desired, bodyYaw, activityRange(arm));
	}

	private static Vec3 constrainAim(Vec3 desired, float bodyYaw, AngularRange activity) {
		float relativeYaw = Mth.clamp(Mth.wrapDegrees(yaw(desired) - bodyYaw),
			Math.max(-MAX_AIM_YAW_DEGREES, activity.minimumYaw()), Math.min(MAX_AIM_YAW_DEGREES, activity.maximumYaw()));
		float pitch = Mth.clamp(pitch(desired), Math.max(MIN_AIM_PITCH_DEGREES, activity.minimumPitch()),
			Math.min(MAX_AIM_PITCH_DEGREES, activity.maximumPitch()));
		return direction(bodyYaw + relativeYaw, pitch);
	}

	public static Vec3 approachAim(Vec3 current, Vec3 desired, float maximumDegrees) {
		float yaw = Mth.approachDegrees(yaw(current), yaw(desired), maximumDegrees);
		float pitch = Mth.approach(pitch(current), pitch(desired), maximumDegrees);
		return direction(yaw, pitch);
	}

	public static Vec3 direction(float yaw, float pitch) {
		double yawRadians = Math.toRadians(yaw);
		double pitchRadians = Math.toRadians(pitch);
		double horizontal = Math.cos(pitchRadians);
		return new Vec3(-Math.sin(yawRadians) * horizontal, -Math.sin(pitchRadians),
			Math.cos(yawRadians) * horizontal);
	}

	public static float yaw(Vec3 direction) {
		return direction == null || direction.horizontalDistanceSqr() < EPSILON ? 0.0f
			: (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
	}

	public static float pitch(Vec3 direction) {
		return direction == null || direction.lengthSqr() < EPSILON ? 0.0f
			: (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
	}

	private static Vec3 normalizedOrForward(Vec3 direction, float bodyYaw) {
		return direction == null || direction.lengthSqr() < EPSILON
			? direction(bodyYaw, 0.0f) : direction.normalize();
	}

	private static double distanceToSqr(AABB bounds, Vec3 point) {
		double dx = point.x - Mth.clamp(point.x, bounds.minX, bounds.maxX);
		double dy = point.y - Mth.clamp(point.y, bounds.minY, bounds.maxY);
		double dz = point.z - Mth.clamp(point.z, bounds.minZ, bounds.maxZ);
		return dx * dx + dy * dy + dz * dz;
	}

	private static List<Point> rectangle(double minX, double minY, double maxX, double maxY) {
		return List.of(new Point(minX, minY), new Point(maxX, minY),
			new Point(maxX, maxY), new Point(minX, maxY));
	}

	/** Clips a counterclockwise convex polygon to nx*x + ny*y + offset >= 0. */
	private static List<Point> clip(List<Point> polygon, double nx, double ny, double offset) {
		if (polygon.isEmpty())
			return polygon;
		List<Point> result = new ArrayList<>(polygon.size() + 1);
		Point previous = polygon.getLast();
		double previousDistance = nx * previous.x() + ny * previous.y() + offset;
		for (Point current : polygon) {
			double currentDistance = nx * current.x() + ny * current.y() + offset;
			boolean previousInside = previousDistance >= -EPSILON;
			boolean currentInside = currentDistance >= -EPSILON;
			if (previousInside != currentInside) {
				double t = Mth.clamp(previousDistance / (previousDistance - currentDistance), 0.0d, 1.0d);
				result.add(new Point(Mth.lerp(t, previous.x(), current.x()), Mth.lerp(t, previous.y(), current.y())));
			}
			if (currentInside)
				result.add(current);
			previous = current;
			previousDistance = currentDistance;
		}
		return result;
	}

	private static double minimumDistanceSqr(List<Point> polygon) {
		double minimum = Double.POSITIVE_INFINITY;
		double area = 0.0d;
		boolean containsOrigin = true;
		Point previous = polygon.getLast();
		for (Point current : polygon) {
			double dx = current.x() - previous.x();
			double dy = current.y() - previous.y();
			double lengthSqr = dx * dx + dy * dy;
			double t = lengthSqr < EPSILON ? 0.0d
				: Mth.clamp(-(previous.x() * dx + previous.y() * dy) / lengthSqr, 0.0d, 1.0d);
			double x = previous.x() + t * dx;
			double y = previous.y() + t * dy;
			minimum = Math.min(minimum, x * x + y * y);
			double cross = previous.x() * current.y() - previous.y() * current.x();
			area += cross;
			containsOrigin &= cross >= -EPSILON;
			previous = current;
		}
		return containsOrigin && area > EPSILON ? 0.0d : minimum;
	}

	/** Yaw is relative to the committed body facing; pitch uses Minecraft's down-positive convention. */
	public record AngularRange(float minimumYaw, float maximumYaw, float minimumPitch, float maximumPitch) {
		public float centerYaw() {
			return (minimumYaw + maximumYaw) * 0.5f;
		}

		public float centerPitch() {
			return (minimumPitch + maximumPitch) * 0.5f;
		}

		public boolean isEmpty() {
			return minimumYaw > maximumYaw || minimumPitch > maximumPitch;
		}
	}

	private record Point(double x, double y) {}
}
