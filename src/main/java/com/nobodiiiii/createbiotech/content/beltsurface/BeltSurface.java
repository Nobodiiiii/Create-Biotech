package com.nobodiiiii.createbiotech.content.beltsurface;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One side of a belt that a funnel can attach to.
 * <p>
 * The surface defines a local right-handed coordinate frame:
 * <ul>
 *   <li>{@code +Y_local} (local up) maps to {@link #outwardNormal} in world — the outward face of the belt.</li>
 *   <li>{@code -Z_local} (local forward) maps to {@link #movementFacing} in world — the direction belt items move along this surface.</li>
 *   <li>{@code +X_local} (local right) is the cross product, derived once and stored as {@link #surfaceToWorld}.</li>
 * </ul>
 * For a horizontal belt with FRONT track, this frame coincides with world; for any other belt geometry,
 * {@link #surfaceToWorld} is the rigid rotation that aligns the local frame with the surface in world.
 * <p>
 * {@link #outwardNormal} is always discretised to one of the six cardinal directions (the funnel attachment side).
 * The continuous tilt — used purely for visual / collision geometry — lives in {@link #surfaceToWorld}.
 * <p>
 * Construction is constrained: {@code outwardNormal} and {@code movementFacing} must be on different axes
 * (otherwise the surface frame is degenerate).
 */
public record BeltSurface(
	BeltSurfaceHost host,
	BlockPos beltPos,
	int segmentIndex,
	Direction outwardNormal,
	Direction movementFacing,
	Quaternionf surfaceToWorld
) {

	public BeltSurface {
		if (outwardNormal.getAxis() == movementFacing.getAxis())
			throw new IllegalArgumentException(
				"outwardNormal (" + outwardNormal + ") and movementFacing (" + movementFacing + ") share an axis");
	}

	public static BeltSurface of(BeltSurfaceHost host, BlockPos beltPos, int segmentIndex,
		Direction outwardNormal, Direction movementFacing) {
		return new BeltSurface(host, beltPos, segmentIndex, outwardNormal, movementFacing,
			computeSurfaceToWorld(outwardNormal, movementFacing));
	}

	/** The block position where a funnel attached to this surface sits. */
	public BlockPos funnelPos() {
		return beltPos.relative(outwardNormal);
	}

	/** Map a surface-local direction to world. */
	public Direction worldize(Direction localDir) {
		return switch (localDir) {
			case UP -> outwardNormal;
			case DOWN -> outwardNormal.getOpposite();
			case NORTH -> movementFacing;
			case SOUTH -> movementFacing.getOpposite();
			case EAST -> worldRight();
			case WEST -> worldRight().getOpposite();
		};
	}

	/** Map a world direction to surface-local frame. Throws on non-axis-aligned input (shouldn't happen with Direction). */
	public Direction localize(Direction worldDir) {
		if (worldDir == outwardNormal)
			return Direction.UP;
		if (worldDir == outwardNormal.getOpposite())
			return Direction.DOWN;
		if (worldDir == movementFacing)
			return Direction.NORTH;
		if (worldDir == movementFacing.getOpposite())
			return Direction.SOUTH;
		Direction right = worldRight();
		if (worldDir == right)
			return Direction.EAST;
		if (worldDir == right.getOpposite())
			return Direction.WEST;
		throw new IllegalStateException(
			"Cannot localize " + worldDir + " (surface up=" + outwardNormal + ", fwd=" + movementFacing + ")");
	}

	/** Surface +X axis in world = {@code movementFacing × outwardNormal} (right-hand rule). */
	public Direction worldRight() {
		Vec3i f = movementFacing.getNormal();
		Vec3i u = outwardNormal.getNormal();
		int x = f.getY() * u.getZ() - f.getZ() * u.getY();
		int y = f.getZ() * u.getX() - f.getX() * u.getZ();
		int z = f.getX() * u.getY() - f.getY() * u.getX();
		for (Direction d : Direction.values()) {
			Vec3i n = d.getNormal();
			if (n.getX() == x && n.getY() == y && n.getZ() == z)
				return d;
		}
		throw new IllegalStateException("worldRight produced non-cardinal vector: (" + x + "," + y + "," + z + ")");
	}

	/** Rotate a position around the block centre {@code (.5, .5, .5)} by {@link #surfaceToWorld}. */
	public Vec3 transformPosition(Vec3 position) {
		Vec3 local = position.subtract(.5d, .5d, .5d);
		Vector3f rotated = new Vector3f((float) local.x, (float) local.y, (float) local.z).rotate(surfaceToWorld);
		return new Vec3(rotated.x() + .5d, rotated.y() + .5d, rotated.z() + .5d);
	}

	/** Rotate a direction vector (no translation) by {@link #surfaceToWorld}. */
	public Vec3 transformDirection(Vec3 direction) {
		Vector3f rotated = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z).rotate(surfaceToWorld);
		return new Vec3(rotated.x(), rotated.y(), rotated.z());
	}

	/** Cardinal direction whose unit normal best aligns with the given vector. */
	public static Direction nearestDirection(Vec3 normal) {
		Direction nearest = Direction.UP;
		double bestAlignment = Double.NEGATIVE_INFINITY;
		for (Direction direction : Direction.values()) {
			double alignment = normal.dot(Vec3.atLowerCornerOf(direction.getNormal()));
			if (alignment > bestAlignment) {
				bestAlignment = alignment;
				nearest = direction;
			}
		}
		return nearest;
	}

	/** Rotate a VoxelShape around the block centre by {@link #surfaceToWorld}, then re-aabb each box. */
	public VoxelShape transformShape(VoxelShape base) {
		if (base.isEmpty())
			return base;
		VoxelShape[] out = { Shapes.empty() };
		base.forAllBoxes((x1, y1, z1, x2, y2, z2) -> out[0] =
			Shapes.or(out[0], rotateBoxAabb(x1, y1, z1, x2, y2, z2, surfaceToWorld)));
		return out[0].optimize();
	}

	private static VoxelShape rotateBoxAabb(double x1, double y1, double z1, double x2, double y2, double z2,
		Quaternionf q) {
		double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
		for (int c = 0; c < 8; c++) {
			double x = (c & 1) != 0 ? x2 : x1;
			double y = (c & 2) != 0 ? y2 : y1;
			double z = (c & 4) != 0 ? z2 : z1;
			Vector3f v = new Vector3f((float) (x - .5d), (float) (y - .5d), (float) (z - .5d)).rotate(q);
			double rx = v.x + .5d;
			double ry = v.y + .5d;
			double rz = v.z + .5d;
			if (rx < minX) minX = rx;
			if (ry < minY) minY = ry;
			if (rz < minZ) minZ = rz;
			if (rx > maxX) maxX = rx;
			if (ry > maxY) maxY = ry;
			if (rz > maxZ) maxZ = rz;
		}
		return Shapes.create(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static Quaternionf computeSurfaceToWorld(Direction outwardNormal, Direction movementFacing) {
		Vec3i u = outwardNormal.getNormal();
		Vec3i f = movementFacing.getNormal();
		// right = forward × up (right-hand rule)
		float rx = f.getY() * u.getZ() - f.getZ() * u.getY();
		float ry = f.getZ() * u.getX() - f.getX() * u.getZ();
		float rz = f.getX() * u.getY() - f.getY() * u.getX();
		// Columns (JOML Matrix3f constructor is column-major): right, up, back (= -forward)
		Matrix3f m = new Matrix3f(
			rx, ry, rz,
			u.getX(), u.getY(), u.getZ(),
			-f.getX(), -f.getY(), -f.getZ()
		);
		return new Quaternionf().setFromUnnormalized(m);
	}
}
