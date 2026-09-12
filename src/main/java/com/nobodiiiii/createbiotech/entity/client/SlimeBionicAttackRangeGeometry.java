package com.nobodiiiii.createbiotech.entity.client;

import java.util.ArrayList;
import java.util.List;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.SlimeBionicCombat;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A tessellated view of the gameplay sector, in world axes relative to the body position. */
public final class SlimeBionicAttackRangeGeometry {
	private static final int YAW_STEPS = 24;
	private static final int PITCH_STEPS = 30;
	private static final double EPSILON = 1.0e-8d;

	private SlimeBionicAttackRangeGeometry() {}

	public static Mesh build(SurgicalAssembly.ArmAttackGeometry arm, float bodyYaw,
		float aimYaw, float aimPitch) {
		SlimeBionicCombat.AngularRange range = SlimeBionicCombat.attackRange(arm);
		if (range.isEmpty())
			return new Mesh(List.of(), List.of(), List.of());
		Vec3 origin = SlimeBionicCombat.worldOrigin(Vec3.ZERO, bodyYaw, arm);
		Builder builder = new Builder(origin, SlimeBionicCombat.direction(bodyYaw, 0.0f));
		float minYaw = bodyYaw + range.minimumYaw();
		float maxYaw = bodyYaw + range.maximumYaw();
		float minPitch = range.minimumPitch();
		float maxPitch = range.maximumPitch();
		float guideYaw = bodyYaw + Mth.clamp(Mth.wrapDegrees(aimYaw - bodyYaw), range.minimumYaw(), range.maximumYaw());
		float guidePitch = Mth.clamp(aimPitch, minPitch, maxPitch);
		Vec3[][] rim = new Vec3[YAW_STEPS + 1][PITCH_STEPS + 1];
		for (int yaw = 0; yaw <= YAW_STEPS; yaw++) {
			for (int pitch = 0; pitch <= PITCH_STEPS; pitch++) {
				rim[yaw][pitch] = point(origin, arm.reach(),
					Mth.lerp((float) yaw / YAW_STEPS, minYaw, maxYaw),
					Mth.lerp((float) pitch / PITCH_STEPS, minPitch, maxPitch));
			}
		}

		for (int yaw = 0; yaw < YAW_STEPS; yaw++) {
			for (int pitch = 0; pitch < PITCH_STEPS; pitch++) {
				List<Vec3> patch = List.of(rim[yaw][pitch], rim[yaw + 1][pitch],
					rim[yaw + 1][pitch + 1], rim[yaw][pitch + 1]);
				builder.surface(patch);
				builder.frontCap(patch);
			}
			for (int pitch : new int[] {0, PITCH_STEPS}) {
				builder.surface(List.of(origin, rim[yaw][pitch], rim[yaw + 1][pitch]));
				builder.line(builder.outline, rim[yaw][pitch], rim[yaw + 1][pitch]);
			}
		}
		for (int pitch = 0; pitch < PITCH_STEPS; pitch++) {
			for (int yaw : new int[] {0, YAW_STEPS}) {
				builder.surface(List.of(origin, rim[yaw][pitch], rim[yaw][pitch + 1]));
				builder.line(builder.outline, rim[yaw][pitch], rim[yaw][pitch + 1]);
			}
		}
		for (int yaw : new int[] {0, YAW_STEPS})
			for (int pitch : new int[] {0, PITCH_STEPS})
				builder.line(builder.outline, origin, rim[yaw][pitch]);

		// Sparse cross-sections show the spherical depth without a distracting wireframe grid.
		for (int yaw = 0; yaw < YAW_STEPS; yaw++)
			builder.line(builder.guides,
				point(origin, arm.reach(), Mth.lerp((float) yaw / YAW_STEPS, minYaw, maxYaw), guidePitch),
				point(origin, arm.reach(), Mth.lerp((float) (yaw + 1) / YAW_STEPS, minYaw, maxYaw), guidePitch));
		for (int pitch = 0; pitch < PITCH_STEPS; pitch++)
			builder.line(builder.guides,
				point(origin, arm.reach(), guideYaw, Mth.lerp((float) pitch / PITCH_STEPS, minPitch, maxPitch)),
				point(origin, arm.reach(), guideYaw, Mth.lerp((float) (pitch + 1) / PITCH_STEPS, minPitch, maxPitch)));
		Vec3 tip = point(origin, arm.reach(), guideYaw, guidePitch);
		builder.line(builder.guides, origin, tip);
		builder.line(builder.outline, point(origin, arm.reach() * 0.82d, Math.max(minYaw, guideYaw - 7.0f), guidePitch), tip);
		builder.line(builder.outline, point(origin, arm.reach() * 0.82d, Math.min(maxYaw, guideYaw + 7.0f), guidePitch), tip);
		return new Mesh(List.copyOf(builder.faces), List.copyOf(builder.outline), List.copyOf(builder.guides));
	}

	private static Vec3 point(Vec3 origin, double reach, float yaw, float pitch) {
		return origin.add(SlimeBionicCombat.direction(yaw, pitch).scale(reach));
	}

	public record Mesh(List<Triangle> faces, List<Segment> outline, List<Segment> guides) {}

	public record Triangle(Vec3 a, Vec3 b, Vec3 c) {}

	public record Segment(Vec3 from, Vec3 to) {}

	private static final class Builder {
		private final Vec3 origin;
		private final Vec3 forward;
		private final List<Triangle> faces = new ArrayList<>();
		private final List<Segment> outline = new ArrayList<>();
		private final List<Segment> guides = new ArrayList<>();

		private Builder(Vec3 origin, Vec3 forward) {
			this.origin = origin;
			this.forward = forward;
		}

		private void surface(List<Vec3> polygon) {
			List<Vec3> clipped = clip(polygon, forward);
			triangulate(clipped);
			if (clipped.size() < 3 || polygon.stream().noneMatch(point -> point.dot(forward) < -EPSILON))
				return;
			// The cut itself is a visible boundary, including cuts through the spherical outer face.
			Vec3 previous = clipped.getLast();
			for (Vec3 current : clipped) {
				if (Math.abs(previous.dot(forward)) < EPSILON && Math.abs(current.dot(forward)) < EPSILON)
					line(outline, previous, current);
				previous = current;
			}
		}

		private void frontCap(List<Vec3> outerPatch) {
			double originDistance = origin.dot(forward);
			if (Math.abs(originDistance) < EPSILON)
				return;
			// Each outer patch and the shoulder form a convex pyramid. Project its opposite-side
			// face onto the clipping plane, so even raised/lowered sectors get a closed, concave cap.
			List<Vec3> opposite = clip(outerPatch, originDistance < 0.0d ? forward : forward.scale(-1.0d));
			if (opposite.size() < 3)
				return;
			List<Vec3> cap = new ArrayList<>(opposite.size());
			for (Vec3 point : opposite)
				cap.add(intersection(origin, point, originDistance, point.dot(forward)));
			triangulate(cap);
		}

		private void triangulate(List<Vec3> polygon) {
			for (int index = 1; index + 1 < polygon.size(); index++) {
				Vec3 a = polygon.getFirst();
				Vec3 b = polygon.get(index);
				Vec3 c = polygon.get(index + 1);
				if (b.subtract(a).cross(c.subtract(a)).lengthSqr() > EPSILON * EPSILON)
					faces.add(new Triangle(a, b, c));
			}
		}

		private void line(List<Segment> destination, Vec3 from, Vec3 to) {
			double fromDistance = from.dot(forward);
			double toDistance = to.dot(forward);
			if (fromDistance < -EPSILON && toDistance < -EPSILON)
				return;
			if (fromDistance < -EPSILON)
				from = intersection(from, to, fromDistance, toDistance);
			else if (toDistance < -EPSILON)
				to = intersection(from, to, fromDistance, toDistance);
			if (from.distanceToSqr(to) > EPSILON * EPSILON)
				destination.add(new Segment(from, to));
		}
	}

	private static List<Vec3> clip(List<Vec3> polygon, Vec3 normal) {
		List<Vec3> clipped = new ArrayList<>(polygon.size() + 1);
		Vec3 previous = polygon.getLast();
		double previousDistance = previous.dot(normal);
		for (Vec3 current : polygon) {
			double currentDistance = current.dot(normal);
			if ((previousDistance >= -EPSILON) != (currentDistance >= -EPSILON))
				clipped.add(intersection(previous, current, previousDistance, currentDistance));
			if (currentDistance >= -EPSILON)
				clipped.add(current);
			previous = current;
			previousDistance = currentDistance;
		}
		return clipped;
	}

	private static Vec3 intersection(Vec3 from, Vec3 to, double fromDistance, double toDistance) {
		return from.lerp(to, Mth.clamp(fromDistance / (fromDistance - toDistance), 0.0d, 1.0d));
	}
}
