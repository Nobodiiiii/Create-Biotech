package com.nobodiiiii.createbiotech.content.surgery;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** Immutable normalized quaternion used for one surgical cube's table-space tilt. */
public record SurgicalCubeRotation(double x, double y, double z, double w) {
	private static final String X_TAG = "X";
	private static final String Y_TAG = "Y";
	private static final String Z_TAG = "Z";
	private static final String W_TAG = "W";
	private static final double MIN_LENGTH_SQUARED = 1.0e-12d;
	private static final double IDENTITY_EPSILON = 1.0e-12d;
	public static final SurgicalCubeRotation IDENTITY = new SurgicalCubeRotation(0.0d, 0.0d, 0.0d, 1.0d);

	public SurgicalCubeRotation {
		double lengthSquared = x * x + y * y + z * z + w * w;
		if (!Double.isFinite(lengthSquared) || lengthSquared < MIN_LENGTH_SQUARED)
			throw new IllegalArgumentException("Invalid surgical cube rotation");
		double inverseLength = 1.0d / Math.sqrt(lengthSquared);
		x *= inverseLength;
		y *= inverseLength;
		z *= inverseLength;
		w *= inverseLength;
		// q and -q encode the same rotation. Canonicalizing the sign keeps equality and NBT stable.
		if (w < 0.0d || w == 0.0d && (x < 0.0d || x == 0.0d && (y < 0.0d || y == 0.0d && z < 0.0d))) {
			x = -x;
			y = -y;
			z = -z;
			w = -w;
		}
	}

	public static SurgicalCubeRotation around(Direction direction, double degrees) {
		if (direction == null || !Double.isFinite(degrees))
			return IDENTITY;
		return around(Vec3.atLowerCornerOf(direction.getNormal()), degrees);
	}

	public static SurgicalCubeRotation around(Vec3 axis, double degrees) {
		if (axis == null || !Double.isFinite(degrees) || axis.lengthSqr() < MIN_LENGTH_SQUARED)
			return IDENTITY;
		Vec3 normalizedAxis = axis.normalize();
		double radians = Math.toRadians(degrees) * 0.5d;
		double scale = Math.sin(radians);
		return new SurgicalCubeRotation(normalizedAxis.x * scale, normalizedAxis.y * scale,
			normalizedAxis.z * scale, Math.cos(radians));
	}

	/** Applies this rotation and then {@code next}, both in table/world axes. */
	public SurgicalCubeRotation then(SurgicalCubeRotation next) {
		if (next == null || next.isIdentity())
			return this;
		if (isIdentity())
			return next;
		return new SurgicalCubeRotation(
			next.w * x + next.x * w + next.y * z - next.z * y,
			next.w * y - next.x * z + next.y * w + next.z * x,
			next.w * z + next.x * y - next.y * x + next.z * w,
			next.w * w - next.x * x - next.y * y - next.z * z);
	}

	/** Smallest physical rotation taking an orthogonal cube frame to any of the 24 axial frames. */
	public static SurgicalCubeRotation nearestAxisAlignment(Vec3 first, Vec3 second) {
		Vec3 xAxis = first.normalize();
		Vec3 yAxis = second.subtract(xAxis.scale(second.dot(xAxis))).normalize();
		if (xAxis.lengthSqr() < 0.5d || yAxis.lengthSqr() < 0.5d)
			return IDENTITY;
		SurgicalCubeRotation best = null;
		for (Direction xDirection : Direction.values()) {
			Vec3 targetX = Vec3.atLowerCornerOf(xDirection.getNormal());
			double dot = Math.clamp(xAxis.dot(targetX), -1.0d, 1.0d);
			Vec3 cross = xAxis.cross(targetX);
			SurgicalCubeRotation alignX = dot < -1.0d + 1.0e-12d
				? around(yAxis, 180.0d)
				: new SurgicalCubeRotation(cross.x, cross.y, cross.z, 1.0d + dot);
			Vec3 movedY = alignX.rotate(yAxis);
			for (Direction yDirection : Direction.values()) {
				if (xDirection.getAxis() == yDirection.getAxis())
					continue;
				Vec3 targetY = Vec3.atLowerCornerOf(yDirection.getNormal());
				double angle = Math.toDegrees(Math.atan2(targetX.dot(movedY.cross(targetY)),
					movedY.dot(targetY)));
				SurgicalCubeRotation candidate = alignX.then(around(targetX, angle));
				// abs(dot(q, identity)) = abs(w); maximizing it minimizes angular distance.
				if (best == null || Math.abs(candidate.w) > Math.abs(best.w) + 1.0e-12d)
					best = candidate;
			}
		}
		return best == null ? IDENTITY : best;
	}

	public Vec3 rotate(Vec3 vector) {
		if (vector == null || isIdentity())
			return vector == null ? Vec3.ZERO : vector;
		// v + w * t + q x t with t = 2 * (q x v), written out in the same operation order as the
		// Vec3 chain it replaces: this runs per animated cube and per rotated vertex every frame.
		double tx = (y * vector.z - z * vector.y) * 2.0d;
		double ty = (z * vector.x - x * vector.z) * 2.0d;
		double tz = (x * vector.y - y * vector.x) * 2.0d;
		return new Vec3(vector.x + tx * w + (y * tz - z * ty),
			vector.y + ty * w + (z * tx - x * tz),
			vector.z + tz * w + (x * ty - y * tx));
	}

	/** Changes the coordinate frame from one laid source pose to another. */
	public SurgicalCubeRotation reframe(SurgicalLayPose source, SurgicalLayPose target) {
		if (isIdentity())
			return this;
		Vec3 reframed = source.rotateInto(target, new Vec3(x, y, z));
		return new SurgicalCubeRotation(reframed.x, reframed.y, reframed.z, w);
	}

	/** Rotates the stored table-space axes clockwise with a packed assembly. */
	public SurgicalCubeRotation rotateClockwise(int turns) {
		Vec3 rotated = new Vec3(x, y, z);
		for (int step = Math.floorMod(turns, 4); step > 0; step--)
			rotated = new Vec3(-rotated.z, rotated.y, rotated.x);
		return new SurgicalCubeRotation(rotated.x, rotated.y, rotated.z, w);
	}

	/** Converts a table-space rotation back through the supplied laid pose. */
	public SurgicalCubeRotation inverseRotate(SurgicalLayPose pose) {
		if (isIdentity())
			return this;
		Vec3 rotated = pose.inverseRotate(new Vec3(x, y, z));
		return new SurgicalCubeRotation(rotated.x, rotated.y, rotated.z, w);
	}

	public boolean isIdentity() {
		return Math.abs(x) <= IDENTITY_EPSILON && Math.abs(y) <= IDENTITY_EPSILON
			&& Math.abs(z) <= IDENTITY_EPSILON && Math.abs(w - 1.0d) <= IDENTITY_EPSILON;
	}

	public boolean approximatelyEquals(SurgicalCubeRotation other, double epsilon) {
		if (other == null)
			return false;
		boolean sameSign = Math.abs(x - other.x) <= epsilon && Math.abs(y - other.y) <= epsilon
			&& Math.abs(z - other.z) <= epsilon && Math.abs(w - other.w) <= epsilon;
		// q and -q are the same physical rotation. Near 180 degrees, tiny rounding differences
		// can make otherwise equivalent normalized quaternions choose opposite canonical signs.
		return sameSign || Math.abs(x + other.x) <= epsilon && Math.abs(y + other.y) <= epsilon
			&& Math.abs(z + other.z) <= epsilon && Math.abs(w + other.w) <= epsilon;
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeDouble(x);
		buffer.writeDouble(y);
		buffer.writeDouble(z);
		buffer.writeDouble(w);
	}

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putDouble(X_TAG, x);
		tag.putDouble(Y_TAG, y);
		tag.putDouble(Z_TAG, z);
		tag.putDouble(W_TAG, w);
		return tag;
	}

	public static SurgicalCubeRotation load(CompoundTag tag) {
		if (tag == null || !tag.contains(X_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(Y_TAG, Tag.TAG_ANY_NUMERIC) || !tag.contains(Z_TAG, Tag.TAG_ANY_NUMERIC)
			|| !tag.contains(W_TAG, Tag.TAG_ANY_NUMERIC))
			return null;
		try {
			return new SurgicalCubeRotation(tag.getDouble(X_TAG), tag.getDouble(Y_TAG),
				tag.getDouble(Z_TAG), tag.getDouble(W_TAG));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public static SurgicalCubeRotation read(FriendlyByteBuf buffer) {
		return new SurgicalCubeRotation(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
			buffer.readDouble());
	}
}
