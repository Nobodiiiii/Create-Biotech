package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose.RotationAxis;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalTablePlane;
import com.nobodiiiii.createbiotech.foundation.render.EntityGeometry;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Measures the real preview renderer and chooses the lowest deterministic recumbent pose. */
public final class SurgicalTablePoseResolver {
	private static final int MAX_MEASURED_VERTICES = 200_000;
	static final float TABLE_CLEARANCE = 1.0f / 1024.0f;
	private static final float HEIGHT_EPSILON = 1.0e-5f;
	private static final int[] CARDINAL_YAWS = { 0, 90, 180, 270 };
	private static final Map<Object, Map<PoseKey, SurgicalPose>> CACHE = new WeakHashMap<>();

	private SurgicalTablePoseResolver() {}

	public static SurgicalPose resolve(Object owner, MimicProfile profile, LivingEntity preview, Direction facing) {
		Map<PoseKey, SurgicalPose> ownerPoses = CACHE.computeIfAbsent(owner,
			ignored -> new java.util.HashMap<>());
		PoseKey key = new PoseKey(profile, facing);
		SurgicalPose cached = ownerPoses.get(key);
		if (cached != null)
			return cached;

		EntityGeometry.Collector bodyGeometry = EntityGeometry.Collector.caching(MAX_MEASURED_VERTICES);
		EntityGeometry.measureBaseModelWithFallback(preview, bodyGeometry);
		SurgicalPose selected = createPose(bodyGeometry, RotationAxis.NONE, facing);
		SurgicalPose xPose = createPose(bodyGeometry, RotationAxis.X, facing);
		SurgicalPose zPose = createPose(bodyGeometry, RotationAxis.Z, facing);
		if (xPose.height + HEIGHT_EPSILON < selected.height)
			selected = xPose;
		if (zPose.height + HEIGHT_EPSILON < selected.height)
			selected = zPose;

		// Attachments do not choose the orientation, but the final placement still contains and
		// centers everything that will actually be drawn in that already-selected orientation.
		EntityGeometry.Collector renderedGeometry = EntityGeometry.Collector.caching(MAX_MEASURED_VERTICES);
		EntityGeometry.measureWithFallback(preview, renderedGeometry);
		selected = createPose(renderedGeometry, selected.layPose.axis(), facing);
		ownerPoses.put(key, selected);
		return selected;
	}

	/** Uses an already recorded pose verbatim instead of choosing a new recumbent frame. */
	public static SurgicalPose resolve(SurgicalLayPose pose) {
		return new SurgicalPose(pose, 0.0f);
	}

	public static void clear() {
		CACHE.clear();
	}

	private static SurgicalPose createPose(EntityGeometry.Collector geometry, RotationAxis axis,
		Direction facing) {
		int yaw = Math.floorMod(facingYaw(axis, facing) + 180, 360);
		Matrix4f rotation = rotation(axis, yaw);
		EntityGeometry.Bounds bounds = geometry.transformBounds(rotation);
		return new SurgicalPose(new SurgicalLayPose(axis, yaw, new net.minecraft.world.phys.Vec3(
			0.5f - bounds.centerX(), SurgicalTablePlane.SURFACE_HEIGHT + TABLE_CLEARANCE - bounds.minY(),
			0.5f - bounds.centerZ())), bounds.sizeY());
	}

	private static int facingYaw(RotationAxis axis, Direction facing) {
		Vector3f target = new Vector3f(facing.getStepX(), 0.0f, facing.getStepZ());
		int bestYaw = 0;
		float bestDot = Float.NEGATIVE_INFINITY;
		for (int yaw : CARDINAL_YAWS) {
			Vector3f reference = axis == RotationAxis.NONE
				? new Vector3f(0.0f, 0.0f, 1.0f) : new Vector3f(0.0f, -1.0f, 0.0f);
			Vector3f oriented = rotation(axis, yaw).transformDirection(reference);
			float dot = oriented.dot(target);
			if (dot > bestDot + 1.0e-6f) {
				bestDot = dot;
				bestYaw = yaw;
			}
		}
		return bestYaw;
	}

	private static Matrix4f rotation(RotationAxis axis, int yaw) {
		Matrix4f transform = new Matrix4f().rotateY((float) Math.toRadians(yaw));
		return switch (axis) {
		case NONE -> transform;
		case X -> transform.rotateX((float) (-Math.PI / 2.0d));
		case Z -> transform.rotateZ((float) (-Math.PI / 2.0d));
		};
	}

	public record SurgicalPose(SurgicalLayPose layPose, float height) {
		public void apply(PoseStack poseStack) {
			SurgicalTablePoseResolver.apply(poseStack, layPose);
		}
	}

	public static void apply(PoseStack poseStack, SurgicalLayPose pose) {
		poseStack.translate(pose.translation().x, pose.translation().y, pose.translation().z);
		applyRotation(poseStack, pose);
	}

	public static void applyRotation(PoseStack poseStack, SurgicalLayPose pose) {
		poseStack.mulPose(Axis.YP.rotationDegrees(pose.yaw()));
		if (pose.axis() != RotationAxis.NONE)
			poseStack.mulPose((pose.axis() == RotationAxis.X ? Axis.XP : Axis.ZP).rotationDegrees(-90.0f));
	}

	public static void applyInverseRotation(PoseStack poseStack, SurgicalLayPose pose) {
		if (pose.axis() != RotationAxis.NONE)
			poseStack.mulPose((pose.axis() == RotationAxis.X ? Axis.XP : Axis.ZP).rotationDegrees(90.0f));
		poseStack.mulPose(Axis.YP.rotationDegrees(-pose.yaw()));
	}

	private record PoseKey(MimicProfile profile, Direction facing) {}
}
