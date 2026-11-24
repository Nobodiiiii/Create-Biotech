package com.nobodiiiii.createbiotech.content.capturenet;

import org.jetbrains.annotations.Nullable;

import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record ArmCapturePose(float yaw, float shoulder, float elbow, float head, float extension) {

	static final ArmCapturePose RESTING = new ArmCapturePose(0, 135, 45, 0, 0);
	public static final float NET_SCALE = 0.85f;
	public static final float CLAW_GRIP = 7 / 16f;
	// The claw grips the wooden shaft at model Y = -2, measured from the item origin at Y = 8.
	public static final float GRIP_Y = -10 / 16f;
	private static final double LOWER_LINK = 14 / 16d;
	private static final double UPPER_LINK = 15 / 16d;
	private static final double MOUTH_REACH = CLAW_GRIP + (6 / 16f - GRIP_Y) * NET_SCALE;
	// Keep at least one pixel of shaft between the claw grip and the socket.
	private static final float MAX_RETRACTION = 6 / 16f * NET_SCALE;
	private static final double MIN_MOUTH_REACH = MOUTH_REACH - MAX_RETRACTION;
	private static final double MOUTH_CLEARANCE = 0.22;
	private static final double MOUTH_HALF_SPAN = 0.30;
	private static final double MOUTH_FRONT = 0.36;
	private static final double MOUTH_BACK = 0.39;
	private static final double ACROSS_SHAFT_WEIGHT = 16;
	private static final double MIN_FORWARD = 0.20;
	private static final int MIN_SHOULDER = -32;
	private static final int MAX_SHOULDER = 148;
	private static final int MIN_ELBOW = 32;
	private static final int MAX_ELBOW = 170;
	private static final int JOINT_STEP = 6;
	private static final float MAX_WRIST = 80;
	private static final float MAX_FOREARM_PITCH = 112;
	private static final float MAX_MOUTH_PITCH = 120;
	static final float LOWERING_AT = 0.25f;
	static final float CONTACT_AT = 0.5f;
	private static final float FOLLOW_THROUGH_AT = 0.75f;
	private static final float SCOOP_WRIST_TURN = 24;
	private static final float FOLLOW_THROUGH_WRIST_TURN = 4;

	// Match Create's ArmBlockEntity.tickMovementProgress.
	static float movementStep(float rpm) {
		return Math.min(256, Math.abs(rpm)) / 1024f;
	}

	public static Vec3 jointOrigin(BlockPos pos, boolean ceiling) {
		return Vec3.atCenterOf(pos).add(0, ceiling ? -6 / 16d : 6 / 16d, 0);
	}

	public static ArmCapturePose ready(BlockPos pos, boolean ceiling, float previousYaw, @Nullable Vec3 trackingPoint) {
		// Create's ArmAngleTarget.NO_TARGET keeps the last base bearing.
		if (trackingPoint == null)
			return new ArmCapturePose(previousYaw, RESTING.shoulder(), RESTING.elbow(), RESTING.head(), RESTING.extension());
		Vec3 offset = trackingPoint.subtract(jointOrigin(pos, ceiling));
		float up = ceiling ? -1 : 1;
		return readyJoints(elevation(offset.y, offset.horizontalDistance()), up).atYaw(yawTowards(offset, up, previousYaw));
	}

	static @Nullable Swing planSwing(BlockPos pos, boolean ceiling, float previousYaw, AABB bounds) {
		Vec3 center = bounds.getCenter();
		Swing swing = planSwing(pos, ceiling, previousYaw, center);
		if (swing.strikeAt(pos).intersects(bounds))
			return swing;

		// A mob close to the pivot can have reachable parts above or below its center.
		double offset = 0.38 * bounds.getYsize();
		for (double height : new double[] {offset, -offset}) {
			swing = planSwing(pos, ceiling, previousYaw, center.add(0, height, 0));
			if (swing.strikeAt(pos).intersects(bounds))
				return swing;
		}
		return null;
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
		Joints raised = raiseNet(contact, up);
		Joints followThrough = finishScoop(raised, contact, up);
		return new Swing(point, ceiling, previousYaw, yaw, ready, raised, contact, followThrough);
	}

	private static Joints raiseNet(Joints contact, float up) {
		float wrist = contact.wrist();
		float turn = SCOOP_WRIST_TURN - FOLLOW_THROUGH_WRIST_TURN;
		float direction = Math.copySign(1, wrist == 0 ? -up : wrist);
		float relaxed = Mth.approach(wrist, 0, turn);
		float opened = Mth.clamp(wrist + direction * turn, -MAX_WRIST, MAX_WRIST);
		float preferred = Math.abs(wrist - relaxed) >= 0.75f * turn ? relaxed : opened;
		// Preposition the wrist so the main turn happens on the way to contact.
		return moveMouth(contact, up, -0.54, JOINT_STEP, 0.0012, preferred, Mth.lerp(0.75f, wrist, preferred),
			Mth.lerp(0.5f, wrist, preferred), Mth.lerp(0.25f, wrist, preferred), wrist);
	}

	private static Joints finishScoop(Joints raised, Joints contact, float up) {
		float wrist = contact.wrist();
		float limit = Math.abs(wrist) > Math.abs(raised.wrist()) ? Math.copySign(MAX_WRIST, wrist) : 0;
		float turn = Math.min(FOLLOW_THROUGH_WRIST_TURN, Math.abs(wrist - raised.wrist()) / 4);
		float preferred = Mth.approach(wrist, limit, turn);
		return moveMouth(contact, up, 0.14, JOINT_STEP / 2, 0.0005, preferred, Mth.lerp(0.5f, wrist, preferred), wrist);
	}

	private static Joints planContact(double targetForward, double targetHeight, float up, float elevation, Joints ready) {
		// Choose the allowed wrist arc in world coordinates before solving either arm link.
		float preferred = Mth.clamp(elevation * 0.9f, -75, 75);
		float minimum = Math.abs(preferred) * 0.42f;
		float closeReach = (float) Mth.clamp(1 - Math.abs(targetForward) / 1.4, 0, 1);
		float maximum = Math.min(MAX_WRIST, Math.abs(preferred) * (1.55f + closeReach));
		float minWrist = preferred < 0 ? -maximum : minimum;
		float maxWrist = preferred < 0 ? -minimum : maximum;
		Joints best = new Joints(ready.shoulder(), ready.elbow(), preferred * up, 0);
		double bestCost = Double.POSITIVE_INFINITY;
		float[] wristChoices = new float[7];
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
				double distance = Math.hypot(dx, dy);
				double bearing = Mth.atan2(dy, dx);
				// The target lies on the opening ray when its projection along the shaft is MOUTH_REACH.
				float aimed = (float) Math.toDegrees(bearing + Math.acos(MOUTH_REACH / Math.max(MOUTH_REACH, distance)));
				float close = (float) Math.toDegrees(bearing + Math.acos(MIN_MOUTH_REACH / Math.max(MIN_MOUTH_REACH, distance)));
				float face = (float) Math.toDegrees(bearing) + 90;
				// Also try a shallow opening angle that lets the shaft reach the front of the target.
				float extended = (float) Math.toDegrees(bearing + Math.asin(MOUTH_CLEARANCE / Math.max(MOUTH_REACH, distance)));
				wristChoices[0] = Mth.clamp(-forearmWorld, lower, upper);
				wristChoices[1] = Mth.clamp(Mth.wrapDegrees(aimed - forearmWorld), lower, upper);
				wristChoices[2] = Mth.clamp(Mth.wrapDegrees(face - forearmWorld), lower, upper);
				wristChoices[3] = lower;
				wristChoices[4] = upper;
				wristChoices[5] = Mth.clamp(Mth.wrapDegrees(extended - forearmWorld), lower, upper);
				wristChoices[6] = Mth.clamp(Mth.wrapDegrees(close - forearmWorld), lower, upper);
				for (float wrist : wristChoices) {
					float pitch = forearmWorld + wrist;
					double radians = Math.toRadians(pitch);
					double sin = Math.sin(radians);
					double cos = Math.cos(radians);
					double alongShaft = dx * cos + dy * sin - MOUTH_REACH;
					float extension = (float) Math.max(-MAX_RETRACTION, alongShaft);
					double shaftCost = ACROSS_SHAFT_WEIGHT * Mth.square(alongShaft - extension)
						+ 0.12 * Mth.square(extension) + 0.35 * Math.abs(extension);
					double rigidCost = ACROSS_SHAFT_WEIGHT * Mth.square(alongShaft);
					if (rigidCost <= shaftCost) {
						extension = 0;
						shaftCost = rigidCost;
					}
					double depth = dx * sin - dy * cos;
					double cost = shaftCost + contactCost(depth, pitch, wrist, preferred);
					if (cost < bestCost) {
						bestCost = cost;
						best = new Joints(shoulder, elbow, up * wrist, extension);
					}
				}
			}
		}
		return best;
	}

	private static double contactCost(double depth, float mouthPitch, float wrist, float preferredWrist) {
		if (Math.abs(mouthPitch) > MAX_MOUTH_PITCH)
			return Double.POSITIVE_INFINITY;
		return 12 * Mth.square(depth - MOUTH_CLEARANCE) + 12 * Mth.square(Math.min(0, depth))
			+ 18 * Mth.square(Math.max(0, depth - MOUTH_FRONT))
			+ 0.000045 * Mth.square(mouthPitch) + 0.00006 * Mth.square(wrist - preferredWrist);
	}

	private static Joints moveMouth(Joints contact, float up, double travel, int jointStep, double wristWeight,
		float... wristAngles) {
		Mouth mouth = contact.mouth(up);
		double direction = Math.toRadians(mouth.pitch());
		double openForward = Math.sin(direction);
		double openHeight = -Math.cos(direction);
		Joints best = contact;
		double bestCost = Double.POSITIVE_INFINITY;
		boolean followThrough = travel > 0;
		int jointRange = followThrough ? 30 : 24;
		float minShoulder = Math.max(MIN_SHOULDER, contact.shoulder() - jointRange);
		float maxShoulder = Math.min(MAX_SHOULDER, contact.shoulder() + jointRange);
		for (float wrist : wristAngles) {
			for (int elbowOffset = -jointRange; elbowOffset <= jointRange; elbowOffset += jointStep) {
				float elbow = Mth.clamp(contact.elbow() + elbowOffset, MIN_ELBOW, MAX_ELBOW);
				// Counter the wrist and elbow rotations with the shoulder to guide the mouth.
				float shoulderCenter = contact.shoulder() + contact.elbow() + contact.wrist() - elbow - wrist;
				for (int shoulderOffset = -jointRange; shoulderOffset <= jointRange; shoulderOffset += jointStep) {
					float shoulder = Mth.clamp(shoulderCenter + shoulderOffset, minShoulder, maxShoulder);
					Joints joints = new Joints(shoulder, elbow, wrist, contact.extension());
					if (Math.abs(joints.forearmPitch()) > MAX_FOREARM_PITCH)
						continue;
					Mouth candidate = joints.mouth(up);
					if (candidate.wristForward() < MIN_FORWARD || Math.abs(candidate.pitch()) > MAX_MOUTH_PITCH)
						continue;
					double forward = candidate.forward() - mouth.forward();
					double height = candidate.height() - mouth.height();
					if (contact.extension() != 0) {
						double shaft = Math.toRadians(candidate.pitch());
						double shaftForward = Math.cos(shaft);
						double shaftHeight = Math.sin(shaft);
						double correction = (travel * openForward - forward) * shaftForward
							+ (travel * openHeight - height) * shaftHeight;
						float extension = (float) Mth.clamp(contact.extension() + correction,
							Math.max(-MAX_RETRACTION, contact.extension() - 0.55), contact.extension() + 0.55);
						joints = new Joints(shoulder, elbow, wrist, extension);
						forward += (extension - contact.extension()) * shaftForward;
						height += (extension - contact.extension()) * shaftHeight;
					}
					double advance = forward * openForward + height * openHeight;
					if (followThrough ? advance < -0.0005 : advance >= 0)
						continue;
					double cost = Mth.square(forward - travel * openForward) + Mth.square(height - travel * openHeight)
						+ 0.00012 * (Mth.square(shoulder - contact.shoulder()) + Mth.square(elbow - contact.elbow()))
						+ wristWeight * Mth.square(wrist - wristAngles[0]);
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
		return new Joints(105, 75 + forearm, 0, 0);
	}

	private static float elevation(double height, double forward) {
		return (float) Math.toDegrees(Mth.atan2(height, Math.max(0.40, Math.abs(forward))));
	}

	private static float yawTowards(Vec3 offset, float up, float fallback) {
		return offset.horizontalDistanceSqr() < MIN_FORWARD * MIN_FORWARD ? fallback
			: (float) Math.toDegrees(Mth.atan2(offset.x, offset.z * up)) + 180;
	}

	private record Joints(float shoulder, float elbow, float wrist, float extension) {
		ArmCapturePose atYaw(float yaw) {
			// Create applies a fixed -45 degrees after the head angle.
			return new ArmCapturePose(yaw, shoulder, elbow, 45 + wrist, extension);
		}

		Mouth mouth(float up) {
			double shoulderRadians = Math.toRadians(shoulder);
			float forearm = forearmPitch();
			double forearmRadians = Math.toRadians(forearm);
			double wristForward = LOWER_LINK * Math.cos(shoulderRadians) + UPPER_LINK * Math.cos(forearmRadians);
			double wristHeight = up * (LOWER_LINK * Math.sin(shoulderRadians) + UPPER_LINK * Math.sin(forearmRadians));
			double pitch = up * (forearm + wrist);
			double radians = Math.toRadians(pitch);
			double reach = MOUTH_REACH + extension;
			return new Mouth(wristForward + reach * Math.cos(radians), wristHeight + reach * Math.sin(radians),
				pitch, wristForward);
		}

		float forearmPitch() {
			return shoulder + elbow - 180;
		}

		Joints easeTo(Joints end, float progress) {
			float t = Mth.clamp(progress, 0, 1);
			float blend = t * t * (3 - 2 * t);
			return new Joints(Mth.lerp(blend, shoulder, end.shoulder), Mth.lerp(blend, elbow, end.elbow),
				Mth.lerp(blend, wrist, end.wrist), Mth.lerp(blend, extension, end.extension));
		}
	}

	private record Mouth(double forward, double height, double pitch, double wristForward) {}

	record Strike(Vec3 center, Vec3 side, Vec3 shaft, Vec3 opening) {
		AABB searchArea() {
			return new AABB(center, center).inflate(0.65);
		}

		boolean intersects(AABB box) {
			if (!searchArea().intersects(box))
				return false;
			Vec3 offset = box.getCenter().subtract(center);
			return overlaps(box, offset, side, -MOUTH_HALF_SPAN, MOUTH_HALF_SPAN)
				&& overlaps(box, offset, shaft, -MOUTH_HALF_SPAN, MOUTH_HALF_SPAN)
				&& overlaps(box, offset, opening, -MOUTH_BACK, MOUTH_FRONT);
		}

		private static boolean overlaps(AABB box, Vec3 offset, Vec3 axis, double min, double max) {
			double projection = offset.dot(axis);
			double radius = 0.5 * (Math.abs(axis.x) * box.getXsize() + Math.abs(axis.y) * box.getYsize()
				+ Math.abs(axis.z) * box.getZsize());
			return projection + radius >= min && projection - radius <= max;
		}
	}

	static final class Swing {
		private final Vec3 point;
		private final boolean ceiling;
		private final float startYaw;
		private final float yaw;
		private final float turnDuration;
		private final float initialProgress;
		private final Joints ready;
		private final Joints raised;
		private final Joints contact;
		private final Joints followThrough;

		private Swing(Vec3 point, boolean ceiling, float startYaw, float yaw, Joints ready, Joints raised, Joints contact,
			Joints followThrough) {
			this.point = point;
			this.ceiling = ceiling;
			this.startYaw = startYaw;
			this.yaw = yaw;
			this.turnDuration = Math.abs(AngleHelper.getShortestAngleDiff(startYaw, yaw)) / 180;
			// Give wide turns a head start so the base is aligned before lowering the net.
			this.initialProgress = Math.min(0, LOWERING_AT - turnDuration);
			this.ready = ready;
			this.raised = raised;
			this.contact = contact;
			this.followThrough = followThrough;
		}

		Vec3 point() {
			return point;
		}

		float startYaw() {
			return startYaw;
		}

		float initialProgress() {
			return initialProgress;
		}

		Strike strikeAt(BlockPos pos) {
			float up = ceiling ? -1 : 1;
			Mouth mouth = contact.mouth(up);
			double radians = Math.toRadians(yaw);
			Vec3 forward = new Vec3(-Math.sin(radians), 0, -up * Math.cos(radians));
			Vec3 side = new Vec3(Math.cos(radians), 0, -up * Math.sin(radians));
			double pitch = Math.toRadians(mouth.pitch());
			Vec3 center = jointOrigin(pos, ceiling).add(forward.scale(mouth.forward())).add(0, mouth.height(), 0);
			return new Strike(center, side, forward.scale(Math.cos(pitch)).add(0, Math.sin(pitch), 0),
				forward.scale(Math.sin(pitch)).add(0, -Math.cos(pitch), 0));
		}

		float yawAt(float progress) {
			float turn = turnDuration == 0 ? 1 : Mth.clamp((progress - initialProgress) / turnDuration, 0, 1);
			return AngleHelper.angleLerp(turn, startYaw, yaw);
		}

		ArmCapturePose sample(float progress) {
			float facing = yawAt(progress);
			if (progress < LOWERING_AT)
				return ready.easeTo(raised, progress / LOWERING_AT).atYaw(facing);
			if (progress < CONTACT_AT)
				return raised.easeTo(contact, (progress - LOWERING_AT) / (CONTACT_AT - LOWERING_AT)).atYaw(facing);
			if (progress < FOLLOW_THROUGH_AT)
				return contact.easeTo(followThrough, (progress - CONTACT_AT) / (FOLLOW_THROUGH_AT - CONTACT_AT)).atYaw(facing);
			return followThrough.easeTo(ready, (progress - FOLLOW_THROUGH_AT) / (1 - FOLLOW_THROUGH_AT)).atYaw(facing);
		}
	}
}
