package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitScope;
import com.nobodiiiii.createbiotech.foundation.render.GuiEntityItemElement;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Fixed anatomical close-ups cut from the vanilla wide player mesh, retaining its original skin UVs. */
public final class SurgicalJointIconModel {
	public static final ResourceLocation TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
	private static final Map<SurgicalLimbType, SurgicalJointIconModel> MODELS = new EnumMap<>(SurgicalLimbType.class);
	private static final Map<BionicTraitScope, SurgicalJointIconModel> SCOPE_MODELS = new EnumMap<>(BionicTraitScope.class);
	private final List<Vertex> vertices;

	private SurgicalJointIconModel(SurgicalLimbType type) {
		// Bake a private, neutral Steve mesh: never mutate the live player's shared model or skin.
		ModelPart player = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
		List<Vertex> mesh = new ArrayList<>();
		switch (type) {
		case NECK -> {
			add(mesh, player, "head", -8, 0, turn(0, 0, 0, 0, -22, 0));
			add(mesh, player, "body", 0, 5, new Matrix4f());
		}
		case SHOULDER -> {
			add(mesh, player, "body", 0, 7, new Matrix4f());
			add(mesh, player, "left_arm", 0, 8, turn(5, 2, 0, 0, 0, -18));
		}
		case ELBOW -> {
			// Extend both mating ends one pixel past the hinge to soften the exposed cut.
			add(mesh, player, "right_arm", 0, 7, new Matrix4f());
			add(mesh, player, "right_arm", 5, 12, turn(-6, 6, -2, -30, 0, 0));
		}
		case HIP -> {
			add(mesh, player, "body", 8, 12, new Matrix4f());
			add(mesh, player, "right_leg", 12, 18, turn(-1.9f, 12, 0, -18, 0, 0));
			add(mesh, player, "left_leg", 12, 18, new Matrix4f());
		}
		case KNEE -> {
			Matrix4f thigh = turn(-1.9f, 12, 0, -15, 0, 0);
			add(mesh, player, "right_leg", 12, 19, thigh);
			// Carry the calf with the forward-leaning thigh, retaining its relative knee bend.
			add(mesh, player, "right_leg", 17, 24,
				new Matrix4f(thigh).mul(turn(-1.9f, 18, 2, 30, 0, 0)));
		}
		}
		vertices = List.copyOf(mesh);
	}

	/** Whole regions use the same mesh capture, skin and item framing as the joint badges. */
	private SurgicalJointIconModel(BionicTraitScope scope) {
		ModelPart player = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
		List<Vertex> mesh = new ArrayList<>();
		List<String> parts = switch (scope) {
		case WHOLE_BODY -> List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");
		case TORSO -> List.of("body");
		case HEAD -> List.of("head");
		case ARM -> List.of("right_arm");
		case LEG -> List.of("right_leg");
		};
		for (String part : parts)
			add(mesh, player, part, -8, 24, new Matrix4f());
		vertices = List.copyOf(mesh);
	}

	public static SurgicalJointIconModel of(SurgicalLimbType type) {
		return MODELS.computeIfAbsent(type, SurgicalJointIconModel::new);
	}

	public static SurgicalJointIconModel of(BionicTraitScope scope) {
		return SCOPE_MODELS.computeIfAbsent(scope, SurgicalJointIconModel::new);
	}

	public GuiEntityItemElement element(ItemDisplayContext context, int light) {
		return GuiEntityItemElement.ofModel(TEXTURE, this::render)
			.blockCentered().autoScale(0.96f, 4.0f).yRotationFor(context).packedLight(light);
	}

	/** Also used by the offline art exporter, so its preview receives the actual runtime vertices. */
	public void render(PoseStack pose, VertexConsumer buffer, int light) {
		for (Vertex vertex : vertices) {
			Vector3f point = pose.last().pose().transformPosition(vertex.position, new Vector3f());
			Vector3f normal = pose.last().transformNormal(vertex.normal, new Vector3f());
			buffer.addVertex(point.x, point.y, point.z, -1, vertex.u, vertex.v,
				OverlayTexture.NO_OVERLAY, light, normal.x, normal.y, normal.z);
		}
	}

	private static Matrix4f turn(float x, float y, float z, float pitch, float yaw, float roll) {
		float radians = (float) Math.PI / 180;
		return new Matrix4f().translation(x, y, z)
			.rotateZYX(roll * radians, yaw * radians, pitch * radians).translate(-x, -y, -z);
	}

	private static void add(List<Vertex> mesh, ModelPart player, String part, float fromY, float toY,
		Matrix4f transform) {
		Capture capture = new Capture();
		player.getChild(part).render(new PoseStack(), capture, 0, OverlayTexture.NO_OVERLAY);
		List<Vertex> source = capture.vertices;
		float minY = source.stream().map(v -> v.position.y).min(Float::compare).orElseThrow();
		float maxY = source.stream().map(v -> v.position.y).max(Float::compare).orElseThrow();
		// The base cuboids are axis aligned. Clamp the two Y planes, interpolating the UVs of
		// every side face instead of rebuilding a shorter box (which would stretch the skin).
		for (int face = 0; face < source.size(); face += 4) {
			for (int corner = 0; corner < 4; corner++) {
				Vertex original = source.get(face + corner);
				float y = Math.clamp(original.position.y, fromY, toY);
				float u = original.u;
				float v = original.v;
				if (Math.abs(original.normal.y) < 0.5f) {
					for (int opposite = 0; opposite < 4; opposite++) {
						Vertex other = source.get(face + opposite);
						if (other.position.x == original.position.x && other.position.z == original.position.z
							&& other.position.y != original.position.y) {
							float fraction = (y - original.position.y) / (other.position.y - original.position.y);
							u += (other.u - u) * fraction;
							v += (other.v - v) * fraction;
							break;
						}
					}
				} else if (y != original.position.y) {
					// Seal a new cut plane using skin/fabric from the adjacent front texel;
					// the original cap might contain hair, a hand or a shoe sole.
					List<Vertex> front = source.stream().filter(p -> p.normal.z < -0.5f).toList();
					float minU = front.stream().map(p -> p.u).min(Float::compare).orElseThrow();
					float maxU = front.stream().map(p -> p.u).max(Float::compare).orElseThrow();
					float minV = front.stream().map(p -> p.v).min(Float::compare).orElseThrow();
					float maxV = front.stream().map(p -> p.v).max(Float::compare).orElseThrow();
					u = (minU + maxU) / 2;
					v = minV + (maxV - minV) * Math.clamp((y - minY) / (maxY - minY), 0.001f, 0.999f);
					// Sample a texel center, not its edge: constant edge UVs can shimmer as
					// interpolation rounds to either neighbor on a large flat cut surface.
					u = ((float) Math.floor(u * 64) + 0.5f) / 64;
					v = ((float) Math.floor(v * 64) + 0.5f) / 64;
				}
				Vector3f point = transform.transformPosition(original.position.x, y, original.position.z, new Vector3f());
				Vector3f normal = transform.transformDirection(original.normal, new Vector3f());
				// Vanilla model coordinates are Y-down and face -Z. Use the living renderer's
				// neutral orientation, keeping the shared item helper's yaw convention intact.
				point.mul(1 / 16f, -1 / 16f, -1 / 16f);
				normal.mul(1, -1, -1);
				mesh.add(new Vertex(point, u, v, normal));
			}
		}
	}

	private record Vertex(Vector3f position, float u, float v, Vector3f normal) {}

	private static final class Capture implements VertexConsumer {
		private final List<Vertex> vertices = new ArrayList<>();
		private Vector3f position;
		private float u;
		private float v;

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			position = new Vector3f(x * 16, y * 16, z * 16);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			this.u = u;
			this.v = v;
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			vertices.add(new Vertex(position, u, v, new Vector3f(x, y, z)));
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
		@Override
		public VertexConsumer setUv1(int u, int v) { return this; }
		@Override
		public VertexConsumer setUv2(int u, int v) { return this; }
	}
}
