package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;

/**
 * A solid edge network whose tubes meet one shared joint mesh at every vertex.
 * Create's ordinary {@code LineOutline} closes and extends every individual tube;
 * those overlapping caps fight in the depth buffer at a wireframe corner.
 */
final class SurgicalEdgeOutline extends Outline {
	private static final double POINT_QUANTUM = 1.0e-7d;
	private static final double DEGENERATE_EPSILON = 1.0e-18d;
	private static final Vector3f FLAT_NORMAL = new Vector3f(0.0f, 1.0f, 0.0f);

	private final Vector3f[] corners = {
		new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(),
		new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()
	};
	private List<SurgicalClientTopology.Edge> edges = List.of();
	private List<Tube> tubes = List.of();
	private List<Joint> joints = List.of();

	void setEdges(List<SurgicalClientTopology.Edge> nextEdges) {
		Map<EdgeKey, SurgicalClientTopology.Edge> distinct = new LinkedHashMap<>();
		for (SurgicalClientTopology.Edge edge : nextEdges) {
			if (edge == null || edge.start().distanceToSqr(edge.end()) <= DEGENERATE_EPSILON)
				continue;
			distinct.putIfAbsent(EdgeKey.of(edge), edge);
		}
		edges = List.copyOf(distinct.values());

		Map<PointKey, JointBuilder> builders = new LinkedHashMap<>();
		for (SurgicalClientTopology.Edge edge : edges) {
			Vec3 direction = edge.end().subtract(edge.start()).normalize();
			builders.computeIfAbsent(PointKey.of(edge.start()), ignored -> new JointBuilder(edge.start()))
				.add(direction);
			builders.computeIfAbsent(PointKey.of(edge.end()), ignored -> new JointBuilder(edge.end()))
				.add(direction.scale(-1.0d));
		}
		Map<PointKey, Joint> builtJoints = new LinkedHashMap<>();
		builders.forEach((key, builder) -> builtJoints.put(key, builder.build()));
		joints = List.copyOf(builtJoints.values());
		List<Tube> builtTubes = new ArrayList<>(edges.size());
		for (SurgicalClientTopology.Edge edge : edges) {
			Vec3 direction = edge.end().subtract(edge.start()).normalize();
			Joint startJoint = builtJoints.get(PointKey.of(edge.start()));
			Vec3 crossAxis = startJoint == null
				? perpendicularAxis(direction) : startJoint.crossAxis(direction);
			builtTubes.add(new Tube(edge, crossAxis));
		}
		tubes = List.copyOf(builtTubes);
	}

	@Override
	public void render(PoseStack poseStack, SuperRenderTypeBuffer buffer, Vec3 camera, float partialTick) {
		float width = params.getLineWidth();
		if (width <= 0.0f || edges.isEmpty())
			return;

		VertexConsumer consumer = buffer.getBuffer(PonderRenderTypes.outlineSolid());
		params.loadColor(colorTemp);
		Vector4f color = colorTemp;
		for (Tube tube : tubes)
			renderOpenTube(poseStack, consumer, camera, tube, width, color);
		for (Joint joint : joints)
			renderJoint(poseStack, consumer, camera, joint, width, color);
	}

	private void renderOpenTube(PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
		Tube tube, float width, Vector4f color) {
		SurgicalClientTopology.Edge edge = tube.edge;
		Vec3 delta = edge.end().subtract(edge.start());
		double length = delta.length();
		if (length <= width)
			return;
		Vec3 direction = delta.scale(1.0d / length);
		Vec3 right = tube.crossAxis.subtract(direction.scale(direction.dot(tube.crossAxis)));
		if (right.lengthSqr() <= DEGENERATE_EPSILON)
			right = perpendicularAxis(direction);
		else
			right = right.normalize();
		Vec3 up = direction.cross(right).normalize();
		double halfWidth = width * 0.5d;
		Vec3 rightOffset = right.scale(halfWidth);
		Vec3 upOffset = up.scale(halfWidth);
		Vec3 body = direction.scale(length - width);
		Vec3 start = edge.start().add(direction.scale(halfWidth));

		setTubeCorners(rightOffset, upOffset, body);
		poseStack.pushPose();
		poseStack.translate(start.x - camera.x, start.y - camera.y, start.z - camera.z);
		PoseStack.Pose pose = poseStack.last();
		// Four side faces only. The shared joint supplies both end faces without coplanar overlap.
		bufferQuad(pose, consumer, corners[0], corners[1], corners[2], corners[3], color,
			LightTexture.FULL_BRIGHT, FLAT_NORMAL);
		bufferQuad(pose, consumer, corners[4], corners[5], corners[6], corners[7], color,
			LightTexture.FULL_BRIGHT, FLAT_NORMAL);
		bufferQuad(pose, consumer, corners[4], corners[1], corners[0], corners[5], color,
			LightTexture.FULL_BRIGHT, FLAT_NORMAL);
		bufferQuad(pose, consumer, corners[6], corners[3], corners[2], corners[7], color,
			LightTexture.FULL_BRIGHT, FLAT_NORMAL);
		poseStack.popPose();
	}

	private void renderJoint(PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
		Joint joint, float width, Vector4f color) {
		double halfWidth = width * 0.5d;
		setJointCorners(joint.first.scale(halfWidth), joint.second.scale(halfWidth),
			joint.third.scale(halfWidth));
		poseStack.pushPose();
		poseStack.translate(joint.point.x - camera.x, joint.point.y - camera.y,
			joint.point.z - camera.z);
		PoseStack.Pose pose = poseStack.last();
		bufferQuad(pose, consumer, corners[0], corners[1], corners[2], corners[3], color,
			LightTexture.FULL_BRIGHT, FLAT_NORMAL);
		bufferQuad(pose, consumer, corners[4], corners[5], corners[6], corners[7], color,
			LightTexture.FULL_BRIGHT, FLAT_NORMAL);
		bufferQuad(pose, consumer, corners[7], corners[2], corners[1], corners[4], color,
			LightTexture.FULL_BRIGHT, FLAT_NORMAL);
		bufferQuad(pose, consumer, corners[5], corners[0], corners[3], corners[6], color,
			LightTexture.FULL_BRIGHT, FLAT_NORMAL);
		bufferQuad(pose, consumer, corners[4], corners[1], corners[0], corners[5], color,
			LightTexture.FULL_BRIGHT, FLAT_NORMAL);
		bufferQuad(pose, consumer, corners[6], corners[3], corners[2], corners[7], color,
			LightTexture.FULL_BRIGHT, FLAT_NORMAL);
		poseStack.popPose();
	}

	private void setTubeCorners(Vec3 right, Vec3 up, Vec3 body) {
		set(corners[0], right.scale(-1.0d).subtract(up).add(body));
		set(corners[1], right.scale(-1.0d).subtract(up));
		set(corners[2], right.subtract(up));
		set(corners[3], right.subtract(up).add(body));
		set(corners[4], right.scale(-1.0d).add(up));
		set(corners[5], right.scale(-1.0d).add(up).add(body));
		set(corners[6], right.add(up).add(body));
		set(corners[7], right.add(up));
	}

	private void setJointCorners(Vec3 first, Vec3 second, Vec3 third) {
		set(corners[0], first.scale(-1.0d).subtract(second).add(third));
		set(corners[1], first.scale(-1.0d).subtract(second).subtract(third));
		set(corners[2], first.subtract(second).subtract(third));
		set(corners[3], first.subtract(second).add(third));
		set(corners[4], first.scale(-1.0d).add(second).subtract(third));
		set(corners[5], first.scale(-1.0d).add(second).add(third));
		set(corners[6], first.add(second).add(third));
		set(corners[7], first.add(second).subtract(third));
	}

	private static void set(Vector3f target, Vec3 value) {
		target.set((float) value.x, (float) value.y, (float) value.z);
	}

	private static Vec3 leastAlignedAxis(Vec3 direction) {
		double x = Math.abs(direction.x);
		double y = Math.abs(direction.y);
		double z = Math.abs(direction.z);
		return x <= y && x <= z ? new Vec3(1.0d, 0.0d, 0.0d)
			: y <= z ? new Vec3(0.0d, 1.0d, 0.0d) : new Vec3(0.0d, 0.0d, 1.0d);
	}

	private static Vec3 perpendicularAxis(Vec3 direction) {
		Vec3 helper = leastAlignedAxis(direction);
		return helper.subtract(direction.scale(direction.dot(helper))).normalize();
	}

	private static final class JointBuilder {
		private final Vec3 point;
		private final List<Vec3> directions = new ArrayList<>();

		private JointBuilder(Vec3 point) {
			this.point = point;
		}

		private void add(Vec3 direction) {
			for (Vec3 existing : directions)
				if (Math.abs(existing.dot(direction)) >= 1.0d - 1.0e-7d)
					return;
			directions.add(direction);
		}

		private Joint build() {
			Vec3 first = directions.getFirst().normalize();
			Vec3 second = null;
			double bestAlignment = Double.POSITIVE_INFINITY;
			for (int index = 1; index < directions.size(); index++) {
				Vec3 candidate = directions.get(index);
				double alignment = Math.abs(first.dot(candidate));
				if (alignment < bestAlignment && alignment < 1.0d - 1.0e-7d) {
					bestAlignment = alignment;
					second = candidate;
				}
			}
			if (second == null)
				second = leastAlignedAxis(first);
			second = second.subtract(first.scale(first.dot(second))).normalize();
			Vec3 third = first.cross(second).normalize();
			second = third.cross(first).normalize();
			return new Joint(point, first, second, third);
		}
	}

	private record Tube(SurgicalClientTopology.Edge edge, Vec3 crossAxis) {}

	private record Joint(Vec3 point, Vec3 first, Vec3 second, Vec3 third) {
		private Vec3 crossAxis(Vec3 direction) {
			Vec3 result = first;
			double alignment = Math.abs(direction.dot(first));
			double secondAlignment = Math.abs(direction.dot(second));
			if (secondAlignment < alignment) {
				result = second;
				alignment = secondAlignment;
			}
			if (Math.abs(direction.dot(third)) < alignment)
				result = third;
			return result;
		}
	}

	private record PointKey(long x, long y, long z) implements Comparable<PointKey> {
		private static PointKey of(Vec3 point) {
			return new PointKey(Math.round(point.x / POINT_QUANTUM),
				Math.round(point.y / POINT_QUANTUM), Math.round(point.z / POINT_QUANTUM));
		}

		@Override
		public int compareTo(PointKey other) {
			int comparison = Long.compare(x, other.x);
			if (comparison != 0)
				return comparison;
			comparison = Long.compare(y, other.y);
			return comparison != 0 ? comparison : Long.compare(z, other.z);
		}
	}

	private record EdgeKey(PointKey first, PointKey second) {
		private static EdgeKey of(SurgicalClientTopology.Edge edge) {
			PointKey start = PointKey.of(edge.start());
			PointKey end = PointKey.of(edge.end());
			return start.compareTo(end) <= 0 ? new EdgeKey(start, end) : new EdgeKey(end, start);
		}
	}
}
