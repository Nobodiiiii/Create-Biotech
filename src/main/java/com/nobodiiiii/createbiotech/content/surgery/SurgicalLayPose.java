package com.nobodiiiii.createbiotech.content.surgery;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * The exact rigid pose used to turn an upright source model into table space.
 *
 * <p>The translation is kept alongside the two recorded rotations because it is geometry-dependent:
 * after choosing a recumbent rotation, the client centers the rendered model and lowers it onto the
 * table. Keeping the complete pose makes glue reframing deterministic on both logical sides.</p>
 */
public record SurgicalLayPose(RotationAxis axis, int yaw, Vec3 translation) {
	private static final double MAX_TRANSLATION = SurgicalTablePlane.MAX_TILES + 2.0d;
	public static final SurgicalLayPose IDENTITY = new SurgicalLayPose(RotationAxis.NONE, 0, Vec3.ZERO);

	public SurgicalLayPose {
		axis = axis == null ? RotationAxis.NONE : axis;
		yaw = Math.floorMod(yaw, 360);
		translation = translation == null ? Vec3.ZERO : translation;
		if (yaw % 90 != 0 || !finiteBounded(translation))
			throw new IllegalArgumentException("Invalid surgical lay pose");
	}

	public boolean valid() {
		return yaw % 90 == 0 && finiteBounded(translation);
	}

	/** Applies only this pose's recorded rotations to an upright-space vector. */
	public Vec3 rotate(Vec3 vector) {
		Vec3 tilted = switch (axis) {
		case NONE -> vector;
		case X -> new Vec3(vector.x, vector.z, -vector.y);
		case Z -> new Vec3(vector.y, -vector.x, vector.z);
		case X_POSITIVE -> new Vec3(vector.x, -vector.z, vector.y);
		case Z_POSITIVE -> new Vec3(-vector.y, vector.x, vector.z);
		case INVERTED -> new Vec3(vector.x, -vector.y, -vector.z);
		};
		return rotateYaw(tilted, yaw / 90);
	}

	/** Reverses only this pose's recorded rotations. */
	public Vec3 inverseRotate(Vec3 vector) {
		Vec3 unyawed = rotateYaw(vector, -(yaw / 90));
		return switch (axis) {
		case NONE -> unyawed;
		case X -> new Vec3(unyawed.x, -unyawed.z, unyawed.y);
		case Z -> new Vec3(-unyawed.y, unyawed.x, unyawed.z);
		case X_POSITIVE -> new Vec3(unyawed.x, unyawed.z, -unyawed.y);
		case Z_POSITIVE -> new Vec3(unyawed.y, -unyawed.x, unyawed.z);
		case INVERTED -> new Vec3(unyawed.x, -unyawed.y, -unyawed.z);
		};
	}

	/** Converts a vector expressed in this pose's table frame into {@code target}'s table frame. */
	public Vec3 rotateInto(SurgicalLayPose target, Vec3 vector) {
		return target.rotate(inverseRotate(vector));
	}

	/** Reframes a complete rigid source pose without changing its geometry. */
	public SurgicalLayPose reframe(SurgicalLayPose source, SurgicalLayPose target) {
		Vec3 x = source.rotateInto(target, rotate(new Vec3(1, 0, 0)));
		Vec3 y = source.rotateInto(target, rotate(new Vec3(0, 1, 0)));
		for (RotationAxis candidateAxis : RotationAxis.values())
			for (int candidateYaw = 0; candidateYaw < 360; candidateYaw += 90) {
				SurgicalLayPose candidate = new SurgicalLayPose(candidateAxis, candidateYaw,
					source.rotateInto(target, translation));
				if (candidate.rotate(new Vec3(1, 0, 0)).distanceToSqr(x) < 1.0e-24d
					&& candidate.rotate(new Vec3(0, 1, 0)).distanceToSqr(y) < 1.0e-24d)
					return candidate;
			}
		throw new IllegalStateException("Non-cardinal surgical pose");
	}

	/** Rotates the complete stored table pose clockwise around the table's Y axis. */
	public SurgicalLayPose rotateClockwise(int turns) {
		int normalized = Math.floorMod(turns, 4);
		return normalized == 0 ? this
			: new SurgicalLayPose(axis, yaw - normalized * 90,
				rotateClockwiseHorizontal(translation, normalized));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeEnum(axis);
		buffer.writeByte(yaw / 90);
		buffer.writeDouble(translation.x);
		buffer.writeDouble(translation.y);
		buffer.writeDouble(translation.z);
	}

	public static SurgicalLayPose read(FriendlyByteBuf buffer) {
		return new SurgicalLayPose(buffer.readEnum(RotationAxis.class), buffer.readByte() * 90,
			new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
	}

	private static Vec3 rotateYaw(Vec3 vector, int quarterTurns) {
		Vec3 rotated = vector;
		for (int turn = Math.floorMod(quarterTurns, 4); turn > 0; turn--)
			rotated = new Vec3(rotated.z, rotated.y, -rotated.x);
		return rotated;
	}

	private static Vec3 rotateClockwiseHorizontal(Vec3 vector, int turns) {
		Vec3 rotated = vector;
		for (int turn = 0; turn < turns; turn++)
			rotated = new Vec3(-rotated.z, rotated.y, rotated.x);
		return rotated;
	}

	private static boolean finiteBounded(Vec3 value) {
		return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
			&& Double.isFinite(value.z) && Math.abs(value.x) <= MAX_TRANSLATION
			&& Math.abs(value.y) <= MAX_TRANSLATION && Math.abs(value.z) <= MAX_TRANSLATION;
	}

	public enum RotationAxis {
		NONE,
		X,
		Z,
		X_POSITIVE,
		Z_POSITIVE,
		INVERTED
	}
}
