package com.nobodiiiii.createbiotech.content.slimebelt;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper.FunnelSupport;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SlimeBeltFunnelShapeHelper {

	private static final Vec3 BLOCK_CENTER = new Vec3(.5d, .5d, .5d);

	public static VoxelShape transformIfSupported(BlockGetter world, BlockPos pos, VoxelShape baseShape) {
		if (world == null || baseShape.isEmpty())
			return baseShape;

		FunnelSupport support = SlimeBeltHelper.getFunnelSupport(world, pos);
		if (support == null)
			return baseShape;

		Quaternionf rotation = getTiltRotation(support);
		VoxelShape[] transformed = new VoxelShape[] { Shapes.empty() };
		baseShape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> transformed[0] =
			Shapes.or(transformed[0], transformBox(x1, y1, z1, x2, y2, z2, rotation)));
		return transformed[0].optimize();
	}

	private static Quaternionf getTiltRotation(FunnelSupport support) {
		Vec3 normal = SlimeBeltHelper.getTrackNormal(support).normalize();
		return new Quaternionf().rotationTo(0, 1, 0, (float) normal.x, (float) normal.y, (float) normal.z);
	}

	private static VoxelShape transformBox(double x1, double y1, double z1, double x2, double y2, double z2,
		Quaternionf rotation) {
		double[] bounds = new double[] {
			Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
			Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY
		};

		includeCorner(bounds, x1, y1, z1, rotation);
		includeCorner(bounds, x1, y1, z2, rotation);
		includeCorner(bounds, x1, y2, z1, rotation);
		includeCorner(bounds, x1, y2, z2, rotation);
		includeCorner(bounds, x2, y1, z1, rotation);
		includeCorner(bounds, x2, y1, z2, rotation);
		includeCorner(bounds, x2, y2, z1, rotation);
		includeCorner(bounds, x2, y2, z2, rotation);

		return Shapes.create(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
	}

	private static void includeCorner(double[] bounds, double x, double y, double z, Quaternionf rotation) {
		Vec3 transformed = transformPosition(new Vec3(x, y, z), rotation);
		bounds[0] = Math.min(bounds[0], transformed.x);
		bounds[1] = Math.min(bounds[1], transformed.y);
		bounds[2] = Math.min(bounds[2], transformed.z);
		bounds[3] = Math.max(bounds[3], transformed.x);
		bounds[4] = Math.max(bounds[4], transformed.y);
		bounds[5] = Math.max(bounds[5], transformed.z);
	}

	private static Vec3 transformPosition(Vec3 position, Quaternionf rotation) {
		Vec3 local = position.subtract(BLOCK_CENTER);
		Vector3f transformed = new Vector3f((float) local.x, (float) local.y, (float) local.z).rotate(rotation);
		return new Vec3(transformed.x() + BLOCK_CENTER.x, transformed.y() + BLOCK_CENTER.y,
			transformed.z() + BLOCK_CENTER.z);
	}
}
