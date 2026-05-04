package com.nobodiiiii.createbiotech.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltBlockEntity;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper.FunnelSupport;
import com.nobodiiiii.createbiotech.content.slimebelt.SlimeBeltHelper.Track;
import com.simibubi.create.content.logistics.FlapStuffs;

import dev.engine_room.flywheel.lib.transform.Translate;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;

public class SlimeBeltFunnelRenderHelper {

	private static final ThreadLocal<SlimeBeltFunnelTransform> CURRENT_TRANSFORM = new ThreadLocal<>();
	private static final Vec3 BLOCK_CENTER = new Vec3(.5d, .5d, .5d);

	public static void pushTransform(BlockGetter level, BlockPos pos) {
		CURRENT_TRANSFORM.set(getTransform(level, pos));
	}

	public static void clearTransform() {
		CURRENT_TRANSFORM.remove();
	}

	public static SlimeBeltFunnelTransform getCurrentTransform() {
		return CURRENT_TRANSFORM.get();
	}

	public static SlimeBeltFunnelTransform getTransform(BlockGetter world, BlockPos pos) {
		if (world == null)
			return null;
		FunnelSupport support = SlimeBeltHelper.getFunnelSupport(world, pos);
		if (support == null)
			return null;
		return createTransform(support);
	}

	public static void applyTilt(PoseStack poseStack, SlimeBeltFunnelTransform transform) {
		Vec3 pivot = transform.pivot();
		poseStack.translate(pivot.x, pivot.y, pivot.z);
		poseStack.mulPose(transform.rotation());
		poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
	}

	public static Matrix4f createTiltedCommonTransform(BlockPos visualPosition, Direction funnelFacing, float baseZOffset,
		SlimeBeltFunnelTransform transform) {
		float horizontalAngle = AngleHelper.horizontalAngle(funnelFacing.getOpposite());
		Vec3 pivot = transform.pivot();

		return new Matrix4f()
			.translate(visualPosition.getX(), visualPosition.getY(), visualPosition.getZ())
			.translate((float) pivot.x, (float) pivot.y, (float) pivot.z)
			.rotate(transform.rotation())
			.translate((float) -pivot.x, (float) -pivot.y, (float) -pivot.z)
			.translate(Translate.CENTER, Translate.CENTER, Translate.CENTER)
			.rotateY(Mth.DEG_TO_RAD * horizontalAngle)
			.translate(-Translate.CENTER, -Translate.CENTER, -Translate.CENTER)
			.translate(FlapStuffs.X_OFFSET, 0, baseZOffset);
	}

	private static SlimeBeltFunnelTransform createTransform(FunnelSupport support) {
		Vec3 normal = getTrackNormal(support).normalize();
		Quaternionf rotation = new Quaternionf().rotationTo(0, 1, 0, (float) normal.x, (float) normal.y,
			(float) normal.z);
		return new SlimeBeltFunnelTransform(BLOCK_CENTER, rotation);
	}

	private static Vec3 getTrackNormal(FunnelSupport support) {
		SlimeBeltBlockEntity controller = support.controller();
		float segmentCenter = Mth.clamp(support.segment().index + .5f, .5f, Math.max(.5f, controller.beltLength - .5f));
		float loopPosition = support.track() == Track.FRONT ? segmentCenter
			: 2 * controller.beltLength + SlimeBeltHelper.getConnectorLength(controller) - segmentCenter;
		return SlimeBeltHelper.getTrackNormal(controller, loopPosition);
	}

	public record SlimeBeltFunnelTransform(Vec3 pivot, Quaternionf rotation) {

		public SlimeBeltFunnelTransform(Vec3 pivot, Quaternionf rotation) {
			this.pivot = pivot;
			this.rotation = new Quaternionf(rotation);
		}

		public Vec3 transformPosition(Vec3 position) {
			Vec3 local = position.subtract(pivot);
			Vector3f transformed = new Vector3f((float) local.x, (float) local.y, (float) local.z).rotate(rotation);
			return new Vec3(transformed.x() + pivot.x, transformed.y() + pivot.y, transformed.z() + pivot.z);
		}

		public Vec3 transformDirection(Vec3 direction) {
			Vector3f transformed =
				new Vector3f((float) direction.x, (float) direction.y, (float) direction.z).rotate(rotation);
			return new Vec3(transformed.x(), transformed.y(), transformed.z());
		}

		public Direction getNearestDirection(Vec3 normal) {
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
	}
}
