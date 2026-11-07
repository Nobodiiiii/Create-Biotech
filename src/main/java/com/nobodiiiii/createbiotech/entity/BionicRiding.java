package com.nobodiiiii.createbiotech.entity;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One common-side seat frame for passenger positioning, aiming, rendering and collision. */
public final class BionicRiding {
	private static final Vec3 UP = new Vec3(0, 1, 0);
	private BionicRiding() {}

	@Nullable
	public static RiderPose pose(Entity passenger, float partialTick) {
		return passenger instanceof Player && passenger.getVehicle() instanceof SlimeBionicEntity mount
			? pose(mount, passenger, partialTick, Mth.rotLerp(partialTick, mount.yBodyRotO, mount.yBodyRot)) : null;
	}

	@Nullable
	public static RiderPose pose(SlimeBionicEntity mount, Entity passenger, float partialTick, float yaw) {
		var equipment = mount.getBionicEquipment();
		var assigned = equipment.passengerSeat(passenger);
		if (assigned == null) return null;
		var seat = equipment.ridingSeat(assigned.carrier());
		if (seat == null) return null;
		boolean rear = assigned.slot() == 1;
		Vec3 anchor = rear ? seat.second() : seat.first();
		Vec3 pivot = equipment.carrierOrigin();
		SurgicalCubeRotation body = bodyRotation(mount, partialTick);
		anchor = pivot.add(body.rotate(anchor.subtract(pivot)));
		SurgicalCubeRotation heading = SurgicalCubeRotation.around(UP, -yaw);
		// Vanilla player yaw already includes the mount's heading. Conjugate the seat tilt so
		// applying it to the ordinary player model/look vector does not apply heading twice.
		SurgicalCubeRotation tilt = inverse(heading).then(seat.rotation()).then(body).then(heading);
		return new RiderPose(mount.getPosition(partialTick).add(heading.rotate(anchor)), tilt);
	}

	/** Mounted swimming is sampled on both sides; no renderer-only body tilt can detach the rider. */
	public static SurgicalCubeRotation bodyRotation(SlimeBionicEntity mount, float partialTick) {
		return SurgicalCubeRotation.around(new Vec3(1, 0, 0),
			Mth.lerp(partialTick, mount.xRotO, mount.getXRot()) * mount.getSwimAmount(partialTick));
	}

	/** Recover orientation for bodies packed before seat rotations were persisted. */
	public static SurgicalCubeRotation restRotation(SurgicalAssembly assembly,
		SurgicalAssembly.CombinationMember carrier) {
		var source = assembly.sources().get(carrier.source());
		var rotation = source.cubeRotations().getOrDefault(carrier.cube(), SurgicalCubeRotation.IDENTITY);
		if (!assembly.preservesLayout() && assembly.sources().size() == 1) return rotation;
		Vec3 x = new Vec3(1, 0, 0);
		Vec3 y = UP;
		if (assembly.preservesLayout()) {
			x = source.layPose().rotate(x);
			y = source.layPose().rotate(y);
		}
		x = assembly.layoutLayPose().inverseRotate(x);
		y = assembly.layoutLayPose().inverseRotate(y);
		Vec3 z = x.cross(y);
		Quaternionf base = new Quaternionf().setFromNormalized(new Matrix3f(
			(float) x.x, (float) x.y, (float) x.z, (float) y.x, (float) y.y, (float) y.z,
			(float) z.x, (float) z.y, (float) z.z));
		return new SurgicalCubeRotation(base.x, base.y, base.z, base.w)
			.then(rotation.inverseRotate(assembly.layoutLayPose()));
	}

	public static SurgicalCubeRotation inverse(SurgicalCubeRotation rotation) {
		return new SurgicalCubeRotation(-rotation.x(), -rotation.y(), -rotation.z(), rotation.w());
	}

	public static Quaternionf quaternion(SurgicalCubeRotation rotation) {
		return new Quaternionf((float) rotation.x(), (float) rotation.y(), (float) rotation.z(), (float) rotation.w());
	}

	/** Minecraft's broad-phase boxes remain axis aligned, enclosing all eight rotated corners. */
	public static AABB rotateBounds(AABB bounds, Vec3 pivot, SurgicalCubeRotation rotation) {
		Vec3 minimum = new Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
		Vec3 maximum = new Vec3(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
		for (int corner = 0; corner < 8; corner++) {
			Vec3 point = pivot.add(rotation.rotate(new Vec3(
				(corner & 1) == 0 ? bounds.minX : bounds.maxX,
				(corner & 2) == 0 ? bounds.minY : bounds.maxY,
				(corner & 4) == 0 ? bounds.minZ : bounds.maxZ).subtract(pivot)));
			minimum = new Vec3(Math.min(minimum.x, point.x), Math.min(minimum.y, point.y), Math.min(minimum.z, point.z));
			maximum = new Vec3(Math.max(maximum.x, point.x), Math.max(maximum.y, point.y), Math.max(maximum.z, point.z));
		}
		return new AABB(minimum, maximum);
	}

	public static boolean clear(Entity passenger, AABB bounds) {
		// Block collisions only: the passenger is intentionally touching its own mount and its hit parts.
		return !passenger.level().getBlockCollisions(passenger, bounds.deflate(1.0e-5)).iterator().hasNext()
			&& passenger.level().getWorldBorder().isWithinBounds(bounds);
	}

	/** Clip translation against each rider's swept bounds, including ceilings above a small mount. */
	public static Vec3 clipMovement(SlimeBionicEntity mount, Vec3 movement) {
		Vec3 clipped = movement;
		for (Entity passenger : mount.getPassengers()) {
			RiderPose pose = pose(passenger, 1);
			if (pose == null) continue;
			clipped = Entity.collideBoundingBox(passenger, clipped, pose.bounds(passenger), mount.level(), List.of());
		}
		return clipped;
	}

	/** Substeps prevent a wide, off-center seat sweeping through a wall during a large mouse turn. */
	public static float safeYaw(SlimeBionicEntity mount, float requested) {
		float initial = mount.yBodyRot;
		float delta = Mth.wrapDegrees(requested - initial);
		int steps = Math.max(1, Mth.ceil(Math.abs(delta) / 2));
		float safe = initial;
		for (int step = 1; step <= steps; step++) {
			float candidate = initial + delta * step / steps;
			for (Entity passenger : mount.getPassengers()) {
				RiderPose pose = pose(mount, passenger, 1, candidate);
				RiderPose previous = pose(mount, passenger, 1, safe);
				if (pose != null && previous != null
					&& !clear(passenger, pose.bounds(passenger).minmax(previous.bounds(passenger)))) return safe;
			}
			safe = candidate;
		}
		return safe;
	}

	public static float safePitch(SlimeBionicEntity mount, float requested) {
		float initial = mount.getXRot();
		float safe = initial;
		int steps = Math.max(1, Mth.ceil(Math.abs(requested - initial) / 2));
		try {
			for (int step = 1; step <= steps; step++) {
				float candidate = Mth.lerp((float) step / steps, initial, requested);
				for (Entity passenger : mount.getPassengers()) {
					mount.setXRot(safe);
					RiderPose previous = pose(passenger, 1);
					mount.setXRot(candidate);
					RiderPose next = pose(passenger, 1);
					if (previous != null && next != null
						&& !clear(passenger, previous.bounds(passenger).minmax(next.bounds(passenger)))) return safe;
				}
				safe = candidate;
			}
			return safe;
		} finally {
			mount.setXRot(initial);
		}
	}

	public record RiderPose(Vec3 anchor, SurgicalCubeRotation rotation) {
		public Vec3 feet(Entity passenger) {
			Vec3 attachment = passenger.getAttachments().get(EntityAttachment.VEHICLE, 0, passenger.getYRot());
			return anchor.subtract(rotation.rotate(attachment));
		}

		public Vec3 eyes(Entity passenger) {
			return feet(passenger).add(rotation.rotate(new Vec3(0, passenger.getEyeHeight(), 0)));
		}

		public AABB bounds(Entity passenger) {
			Vec3 feet = feet(passenger);
			return rotateBounds(passenger.getDimensions(passenger.getPose()).makeBoundingBox(feet), feet, rotation);
		}
	}
}
