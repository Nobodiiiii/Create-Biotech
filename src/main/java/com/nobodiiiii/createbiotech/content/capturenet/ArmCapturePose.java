package com.nobodiiiii.createbiotech.content.capturenet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record ArmCapturePose(float yaw, float shoulder, float elbow, float head) {

	public static final float NET_SCALE = 0.85f;
	public static final float CLAW_GRIP = 7 / 16f;
	// The claw grips the wooden shaft at model Y = -2, measured from the item origin at Y = 8.
	public static final float GRIP_Y = -10 / 16f;
	private static final double LOWER_LINK = 14 / 16d;
	private static final double UPPER_LINK = 15 / 16d;
	private static final double MOUTH_REACH = CLAW_GRIP + (6 / 16f - GRIP_Y) * NET_SCALE;
	private static final double MOUTH_CLEARANCE = 0.22;
	private static final double MIN_FORWARD = 0.20;
	private static final int MIN_SHOULDER = -32;
	private static final int MAX_SHOULDER = 148;
	private static final int MIN_ELBOW = 32;
	private static final int MAX_ELBOW = 170;
	private static final int JOINT_STEP = 6;
	private static final float MAX_WRIST = 80;
	private static final float MAX_FOREARM_PITCH = 112;
	private static final float MAX_MOUTH_PITCH = 120;
	private static final float MAX_YAW_STEP = 38;
	private static final float RAISED_AT = 0.24f;
	private static final float FOLLOW_THROUGH_AT = 0.66f;
	private static final float[] WINDUP_WRIST_FRACTIONS = {0.74f, 0.88f, 1};
	private static final float[] FOLLOW_THROUGH_WRIST_FRACTIONS = {1};

	public static Vec3 jointOrigin(BlockPos pos, boolean ceiling) {
		return Vec3.atCenterOf(pos).add(0, ceiling ? -6 / 16d : 6 / 16d, 0);
	}

	public static ArmCapturePose ready(BlockPos pos, boolean ceiling, float previousYaw, @Nullable Vec3 trackingPoint) {
		Vec3 offset = trackingPoint == null ? Vec3.ZERO : trackingPoint.subtract(jointOrigin(pos, ceiling));
		float up = ceiling ? -1 : 1;
		return readyJoints(elevation(offset.y, offset.horizontalDistance()), up).atYaw(yawTowards(offset, up, previousYaw));
	}

	static Swing planSwing(BlockPos pos, boolean ceiling, float previousYaw, Vec3 point) {
		Vec3 offset = point.subtract(jointOrigin(pos, ceiling));
		float up = ceiling ? -1 : 1;
		float yaw = yawTowards(offset, up, previousYaw);
		double forwards = offset.horizontalDistance();
		if (forwards < MIN_FORWARD) {
			// Keep the current base direction for targets nearly on the rotation axis.
			double radians = Math.toRadians(yaw);
			forwards = -offset.x * Math.sin(radians) - up * offset.z * Math.cos(radians);
		}

		float elevation = elevation(offset.y, forwards);
		Joints ready = readyJoints(elevation, up);
		Joints contact = planContact(forwards, offset.y, up, elevation, ready);
		Joints raised = moveMouth(contact, up, -0.54, WINDUP_WRIST_FRACTIONS);
		Joints followThrough = moveMouth(contact, up, 0.14, FOLLOW_THROUGH_WRIST_FRACTIONS);
		return new Swing(point, ceiling, yaw, ready, raised, contact, followThrough);
	}

	private static Joints planContact(double targetForward, double targetHeight, float up, float elevation, Joints ready) {
		// Choose the allowed wrist arc in world coordinates before solving either arm link.
		float preferred = Mth.clamp(elevation * 0.9f, -75, 75);
		float minimum = Math.abs(preferred) * 0.42f;
		float closeReach = (float) Mth.clamp(1 - Math.abs(targetForward) / 1.4, 0, 1);
		float maximum = Math.min(MAX_WRIST, Math.abs(preferred) * (1.55f + closeReach));
		float minWrist = preferred < 0 ? -maximum : minimum;
		float maxWrist = preferred < 0 ? -minimum : maximum;
		Joints best = new Joints(ready.shoulder(), ready.elbow(), preferred * up);
		double bestCost = Double.POSITIVE_INFINITY;
		float[] wristChoices = new float[5];
		for (int shoulder = MIN_SHOULDER; shoulder <= MAX_SHOULDER; shoulder += JOINT_STEP) {
			double shoulderRadians = Math.toRadians(shoulder);
			double elbowForward = LOWER_LINK * Math.cos(shoulderRadians);
			double elbowHeight = LOWER_LINK * Math.sin(shoulderRadians);
			for (int elbow = MIN_ELBOW; elbow <= MAX_ELBOW; elbow += JOINT_STEP) {
				float forearm = shoulder + elbow - 180;
				if (Math.abs(forearm) > MAX_FOREARM_PITCH)
					continue;
				double forearmRadians = Math.toRadians(forearm);
				double wristForward = elbowForward + UPPER_LINK * Math.cos(forearmRadians);
				if (wristForward < MIN_FORWARD)
					continue;
				double wristHeight = up * (elbowHeight + UPPER_LINK * Math.sin(forearmRadians));
				double dx = targetForward - wristForward;
				double dy = targetHeight - wristHeight;
				float forearmWorld = forearm * up;
				float lower = Math.max(minWrist, -MAX_MOUTH_PITCH - forearmWorld);
				float upper = Math.min(maxWrist, MAX_MOUTH_PITCH - forearmWorld);
				if (lower > upper)
					continue;
				double distance = Math.max(MOUTH_REACH, Math.hypot(dx, dy));
				double bearing = Mth.atan2(dy, dx);
				// The target lies on the opening ray when its projection along the shaft is MOUTH_REACH.
				float aimed = (float) Math.toDegrees(bearing + Math.acos(MOUTH_REACH / distance));
				float face = (float) Math.toDegrees(bearing) + 90;
				wristChoices[0] = Mth.clamp(-forearmWorld, lower, upper);
				wristChoices[1] = Mth.clamp(Mth.wrapDegrees(aimed - forearmWorld), lower, upper);
				wristChoices[2] = Mth.clamp(Mth.wrapDegrees(face - forearmWorld), lower, upper);
				wristChoices[3] = lower;
				wristChoices[4] = upper;
				for (float wrist : wristChoices) {
					double cost = contactCost(dx, dy, forearmWorld + wrist, wrist, preferred);
					if (cost < bestCost) {
						bestCost = cost;
						best = new Joints(shoulder, elbow, up * wrist);
					}
				}
			}
		}
		return best;
	}

	private static double contactCost(double dx, double dy, float mouthPitch, float wrist, float preferredWrist) {
		if (Math.abs(mouthPitch) > MAX_MOUTH_PITCH)
			return Double.POSITIVE_INFINITY;
		double pitch = Math.toRadians(mouthPitch);
		double sin = Math.sin(pitch);
		double cos = Math.cos(pitch);
		double across = dx * cos + dy * sin - MOUTH_REACH;
		double depth = dx * sin - dy * cos;
		double gap = depth - MOUTH_CLEARANCE;
		double shortfall = Math.min(0, gap);
		return 6 * Mth.square(across) + 24 * Mth.square(shortfall) + 0.24 * Mth.square(gap)
			+ 20 * Mth.square(Math.min(0, depth)) + (depth < 0 ? 2 : 0)
			+ 0.000045 * Mth.square(mouthPitch) + 0.00006 * Mth.square(wrist - preferredWrist);
	}

	private static Joints moveMouth(Joints contact, float up, double travel, float[] wristFractions) {
		Mouth mouth = contact.mouth(up);
		double direction = Math.toRadians(mouth.pitch());
		double openForward = Math.sin(direction);
		double openHeight = -Math.cos(direction);
		Joints best = contact;
		double bestCost = Double.POSITIVE_INFINITY;
		for (int shoulderOffset = -24; shoulderOffset <= 24; shoulderOffset += JOINT_STEP) {
			float shoulder = Mth.clamp(contact.shoulder() + shoulderOffset, MIN_SHOULDER, MAX_SHOULDER);
			for (int elbowOffset = -24; elbowOffset <= 24; elbowOffset += JOINT_STEP) {
				float elbow = Mth.clamp(contact.elbow() + elbowOffset, MIN_ELBOW, MAX_ELBOW);
				for (float wristFraction : wristFractions) {
					Joints joints = new Joints(shoulder, elbow, contact.wrist() * wristFraction);
					if (Math.abs(joints.forearmPitch()) > MAX_FOREARM_PITCH)
						continue;
					Mouth candidate = joints.mouth(up);
					if (candidate.wristForward() < MIN_FORWARD || Math.abs(candidate.pitch()) > MAX_MOUTH_PITCH)
						continue;
					double forward = candidate.forward() - mouth.forward();
					double height = candidate.height() - mouth.height();
					if ((forward * openForward + height * openHeight) * travel <= 0)
						continue;
					double cost = Mth.square(forward - travel * openForward) + Mth.square(height - travel * openHeight)
						+ 0.00012 * (Mth.square(shoulderOffset) + Mth.square(elbowOffset))
						+ 0.8 * Mth.square(wristFraction - wristFractions[0]);
					if (cost < bestCost) {
						bestCost = cost;
						best = joints;
					}
				}
			}
		}
		return best;
	}

	private static Joints readyJoints(float elevation, float up) {
		float forearm = up * Mth.clamp(elevation * 0.26f + 10, -14, 28);
		return new Joints(105, 75 + forearm, 0);
	}

	private static float elevation(double height, double forward) {
		return (float) Math.toDegrees(Mth.atan2(height, Math.max(0.40, Math.abs(forward))));
	}

	private static float yawTowards(Vec3 offset, float up, float fallback) {
		return offset.horizontalDistanceSqr() < MIN_FORWARD * MIN_FORWARD ? fallback
			: (float) Math.toDegrees(Mth.atan2(offset.x, offset.z * up)) + 180;
	}

	private record Joints(float shoulder, float elbow, float wrist) {
		ArmCapturePose atYaw(float yaw) {
			// Create applies a fixed -45 degrees after the head angle.
			return new ArmCapturePose(yaw, shoulder, elbow, 45 + wrist);
		}

		Mouth mouth(float up) {
			double shoulderRadians = Math.toRadians(shoulder);
			float forearm = forearmPitch();
			double forearmRadians = Math.toRadians(forearm);
			double wristForward = LOWER_LINK * Math.cos(shoulderRadians) + UPPER_LINK * Math.cos(forearmRadians);
			double wristHeight = up * (LOWER_LINK * Math.sin(shoulderRadians) + UPPER_LINK * Math.sin(forearmRadians));
			double pitch = up * (forearm + wrist);
			double radians = Math.toRadians(pitch);
			return new Mouth(wristForward + MOUTH_REACH * Math.cos(radians), wristHeight + MOUTH_REACH * Math.sin(radians),
				pitch, wristForward);
		}

		float forearmPitch() {
			return shoulder + elbow - 180;
		}

		Joints easeTo(Joints end, float progress) {
			float t = Mth.clamp(progress, 0, 1);
			float blend = t * t * (3 - 2 * t);
			return new Joints(Mth.lerp(blend, shoulder, end.shoulder), Mth.lerp(blend, elbow, end.elbow),
				Mth.lerp(blend, wrist, end.wrist));
		}
	}

	private record Mouth(double forward, double height, double pitch, double wristForward) {}

	static final class Swing {
		private final Vec3 point;
		private final boolean ceiling;
		private final float yaw;
		private final Joints ready;
		private final Joints raised;
		private final Joints contact;
		private final Joints followThrough;

		private Swing(Vec3 point, boolean ceiling, float yaw, Joints ready, Joints raised, Joints contact, Joints followThrough) {
			this.point = point;
			this.ceiling = ceiling;
			this.yaw = yaw;
			this.ready = ready;
			this.raised = raised;
			this.contact = contact;
			this.followThrough = followThrough;
		}

		boolean matches(Vec3 target, boolean mountedOnCeiling) {
			return ceiling == mountedOnCeiling && point.equals(target);
		}

		ArmCapturePose sample(float previousYaw, float progress) {
			float facing = Mth.approachDegrees(previousYaw, yaw, MAX_YAW_STEP);
			if (progress < RAISED_AT)
				return ready.easeTo(raised, progress / RAISED_AT).atYaw(facing);
			if (progress < CaptureNetScoop.CONTACT)
				return raised.easeTo(contact, (progress - RAISED_AT) / (CaptureNetScoop.CONTACT - RAISED_AT)).atYaw(facing);
			if (progress < FOLLOW_THROUGH_AT)
				return contact.easeTo(followThrough, (progress - CaptureNetScoop.CONTACT)
					/ (FOLLOW_THROUGH_AT - CaptureNetScoop.CONTACT)).atYaw(facing);
			return followThrough.easeTo(ready, (progress - FOLLOW_THROUGH_AT) / (1 - FOLLOW_THROUGH_AT)).atYaw(facing);
		}
	}
}
