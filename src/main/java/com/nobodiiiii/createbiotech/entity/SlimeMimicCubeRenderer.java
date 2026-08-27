package com.nobodiiiii.createbiotech.entity;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalModelRenderContext;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalSourceModelRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Draws one released source cuboid and its shrink-or-slime morph. */
public class SlimeMimicCubeRenderer extends EntityRenderer<SlimeMimicCubeEntity> {
	private static final ResourceLocation SLIME_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png");
	private static final Map<SlimeMimicCubeEntity, CachedCube> CUBES = new WeakHashMap<>();

	public SlimeMimicCubeRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.25f;
	}

	@Override
	public void render(SlimeMimicCubeEntity entity, float yaw, float partialTick, PoseStack poseStack,
		MultiBufferSource buffer, int packedLight) {
		MimicProfile profile = entity.profile();
		if (profile == null)
			return;
		CachedCube cached = CUBES.get(entity);
		if (cached == null || !cached.profile.equals(profile) || cached.cube != entity.cube()) {
			SurgicalModelRenderContext.CubeGeometry geometry =
				SurgicalSourceModelRenderer.singleCubeGeometry(entity, profile, entity.cube());
			if (geometry == null)
				return;
			cached = new CachedCube(profile, entity.cube(), sourceFrame(geometry.corners()));
			CUBES.put(entity, cached);
		}

		SlimeMimicCubeEntity.CubeFrame visual = entity.visualFrame(partialTick);
		Matrix4f target = frame(visual.origin(), visual.a(), visual.b(), visual.c());
		Matrix4f transform = new Matrix4f(target).mul(new Matrix4f(cached.sourceFrame).invert());

		poseStack.pushPose();
		poseStack.mulPose(transform);
		SurgicalSourceModelRenderer.renderSingleCube(entity, profile, entity.cube(), poseStack,
			buffer, packedLight);
		poseStack.popPose();
		super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
	}

	private static Matrix4f sourceFrame(List<Vec3> corners) {
		Vec3 origin = corners.getFirst();
		Vec3 a = corners.get(1).subtract(origin);
		Vec3 b = corners.get(2).subtract(origin);
		Vec3 c = corners.get(4).subtract(origin);
		double determinant = a.dot(b.cross(c));
		if (Math.abs(determinant) > 1.0e-9d)
			return frame(origin, a, b, c);

		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (Vec3 corner : corners) {
			minX = Math.min(minX, corner.x);
			minY = Math.min(minY, corner.y);
			minZ = Math.min(minZ, corner.z);
			maxX = Math.max(maxX, corner.x);
			maxY = Math.max(maxY, corner.y);
			maxZ = Math.max(maxZ, corner.z);
		}
		return frame(new Vec3(minX, minY, minZ),
			new Vec3(Math.max(1.0e-4d, maxX - minX), 0.0d, 0.0d),
			new Vec3(0.0d, Math.max(1.0e-4d, maxY - minY), 0.0d),
			new Vec3(0.0d, 0.0d, Math.max(1.0e-4d, maxZ - minZ)));
	}

	private static Matrix4f frame(Vec3 origin, Vec3 a, Vec3 b, Vec3 c) {
		Matrix4f frame = new Matrix4f().identity();
		frame.m00((float) a.x).m01((float) a.y).m02((float) a.z);
		frame.m10((float) b.x).m11((float) b.y).m12((float) b.z);
		frame.m20((float) c.x).m21((float) c.y).m22((float) c.z);
		frame.m30((float) origin.x).m31((float) origin.y).m32((float) origin.z);
		return frame;
	}

	@Override
	protected float getShadowRadius(SlimeMimicCubeEntity entity) {
		return Math.max(0.0f, Math.max(entity.visualWidth(0.0f), entity.visualDepth(0.0f)) * 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(SlimeMimicCubeEntity entity) {
		return SLIME_TEXTURE;
	}

	private record CachedCube(MimicProfile profile, int cube, Matrix4f sourceFrame) {
		private CachedCube {
			sourceFrame = new Matrix4f(sourceFrame);
		}
	}
}
