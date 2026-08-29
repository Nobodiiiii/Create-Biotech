package com.nobodiiiii.createbiotech.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
	private static final double AXIS_EPSILON = 1.0e-9d;
	private static final ResourceLocation SLIME_TEXTURE =
		ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png");
	private static final List<Basis> AXIS_ALIGNED_BASES = axisAlignedBases();
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
		SlimeMimicCubeEntity.CubeFrame initialFrame = entity.initialFrame();
		if (cached == null || !cached.profile.equals(profile) || cached.cube != entity.cube()
			|| !cached.initialFrame.equals(initialFrame)) {
			SurgicalModelRenderContext.CubeGeometry geometry =
				SurgicalSourceModelRenderer.singleCubeGeometry(profile, entity.cube());
			if (geometry == null)
				return;
			cached = new CachedCube(profile, entity.cube(), initialFrame,
				sourceFrame(geometry.corners()), MorphBasis.of(initialFrame));
			CUBES.put(entity, cached);
		}

		SlimeMimicCubeEntity.CubeFrame visual = cached.morph.frame(entity, partialTick);
		Matrix4f target = frame(visual.origin(), visual.a(), visual.b(), visual.c());
		Matrix4f transform = new Matrix4f(target).mul(new Matrix4f(cached.sourceFrame).invert());

		poseStack.pushPose();
		poseStack.mulPose(transform);
		SurgicalSourceModelRenderer.renderSingleCube(profile, entity.cube(), poseStack,
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
		Vec3 normal = a.cross(b);
		if (normal.lengthSqr() > AXIS_EPSILON * AXIS_EPSILON)
			return frame(origin, a, b, normal.normalize().scale(1.0e-4d));

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

	private static List<Basis> axisAlignedBases() {
		List<Vec3> directions = List.of(
			new Vec3(1.0d, 0.0d, 0.0d), new Vec3(-1.0d, 0.0d, 0.0d),
			new Vec3(0.0d, 1.0d, 0.0d), new Vec3(0.0d, -1.0d, 0.0d),
			new Vec3(0.0d, 0.0d, 1.0d), new Vec3(0.0d, 0.0d, -1.0d));
		List<Basis> bases = new ArrayList<>(24);
		for (Vec3 a : directions)
			for (Vec3 b : directions) {
				if (Math.abs(a.dot(b)) > AXIS_EPSILON)
					continue;
				bases.add(new Basis(a, b, a.cross(b)));
			}
		return List.copyOf(bases);
	}

	private static Basis closestAxisAligned(Basis source) {
		Basis closest = AXIS_ALIGNED_BASES.getFirst();
		double bestScore = Double.NEGATIVE_INFINITY;
		for (Basis candidate : AXIS_ALIGNED_BASES) {
			double score = source.a.dot(candidate.a) + source.b.dot(candidate.b)
				+ source.c.dot(candidate.c);
			if (score > bestScore) {
				bestScore = score;
				closest = candidate;
			}
		}
		return closest;
	}

	private static Quaternionf rotation(Basis basis) {
		return frame(Vec3.ZERO, basis.a, basis.b, basis.c)
			.getUnnormalizedRotation(new Quaternionf()).normalize();
	}

	private static Basis basis(Quaternionf rotation) {
		Vector3f a = rotation.transform(new Vector3f(1.0f, 0.0f, 0.0f));
		Vector3f b = rotation.transform(new Vector3f(0.0f, 1.0f, 0.0f));
		Vector3f c = rotation.transform(new Vector3f(0.0f, 0.0f, 1.0f));
		return new Basis(new Vec3(a.x, a.y, a.z), new Vec3(b.x, b.y, b.z),
			new Vec3(c.x, c.y, c.z));
	}

	private static Vec3 perpendicular(Vec3 axis) {
		Vec3 reference = Math.abs(axis.y) < 0.9d
			? new Vec3(0.0d, 1.0d, 0.0d) : new Vec3(1.0d, 0.0d, 0.0d);
		return reference.subtract(axis.scale(reference.dot(axis))).normalize();
	}

	private static double lerp(double start, double end, double progress) {
		return start + (end - start) * progress;
	}

	@Override
	protected float getShadowRadius(SlimeMimicCubeEntity entity) {
		return Math.max(0.0f, Math.max(entity.visualWidth(0.0f), entity.visualDepth(0.0f)) * 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(SlimeMimicCubeEntity entity) {
		return SLIME_TEXTURE;
	}

	private record CachedCube(MimicProfile profile, int cube,
		SlimeMimicCubeEntity.CubeFrame initialFrame, Matrix4f sourceFrame, MorphBasis morph) {
		private CachedCube {
			sourceFrame = new Matrix4f(sourceFrame);
		}
	}

	private record Basis(Vec3 a, Vec3 b, Vec3 c) {}

	/** Interpolates a true cuboid: independent edge lengths plus the shortest quaternion rotation. */
	private record MorphBasis(Vec3 center, Quaternionf startRotation, Quaternionf targetRotation,
		double lengthA, double lengthB, double lengthC) {
		private MorphBasis {
			startRotation = new Quaternionf(startRotation);
			targetRotation = new Quaternionf(targetRotation);
		}

		private static MorphBasis of(SlimeMimicCubeEntity.CubeFrame frame) {
			double lengthA = frame.a().length();
			double lengthB = frame.b().length();
			double lengthC = frame.c().length();
			Vec3 a = lengthA > AXIS_EPSILON ? frame.a().scale(1.0d / lengthA)
				: new Vec3(1.0d, 0.0d, 0.0d);
			Vec3 orthogonalB = frame.b().subtract(a.scale(frame.b().dot(a)));
			Vec3 b = orthogonalB.lengthSqr() > AXIS_EPSILON * AXIS_EPSILON
				? orthogonalB.normalize() : perpendicular(a);
			Vec3 c = a.cross(b).normalize();
			double signedLengthC = lengthC > AXIS_EPSILON && frame.c().dot(c) < 0.0d
				? -lengthC : lengthC;
			Basis source = new Basis(a, b, c);
			Basis target = closestAxisAligned(source);
			Vec3 center = frame.origin().add(frame.a().add(frame.b()).add(frame.c()).scale(0.5d));
			return new MorphBasis(center, rotation(source), rotation(target),
				lengthA, lengthB, signedLengthC);
		}

		private SlimeMimicCubeEntity.CubeFrame frame(SlimeMimicCubeEntity entity, float partialTick) {
			float rawProgress = entity.morphProgress(partialTick);
			if (rawProgress <= 0.0f)
				return entity.initialFrame();
			double progress = rawProgress * rawProgress * (3.0d - 2.0d * rawProgress);
			double targetSide = entity.slimeSize() > 0 ? entity.slimeSize() * 0.5d : 0.0d;
			Vec3 targetCenter = entity.slimeSize() > 0
				? new Vec3(0.0d, targetSide * 0.5d, 0.0d) : Vec3.ZERO;
			Vec3 visualCenter = center.lerp(targetCenter, progress);
			double visualA = lerp(lengthA, targetSide, progress);
			double visualB = lerp(lengthB, targetSide, progress);
			double targetC = Math.copySign(targetSide, lengthC == 0.0d ? 1.0d : lengthC);
			double visualC = lerp(lengthC, targetC, progress);
			Quaternionf visualRotation = new Quaternionf(startRotation)
				.slerp(targetRotation, (float) progress).normalize();
			Basis visualBasis = basis(visualRotation);
			Vec3 a = visualBasis.a.scale(visualA);
			Vec3 b = visualBasis.b.scale(visualB);
			Vec3 c = visualBasis.c.scale(visualC);
			Vec3 origin = visualCenter.subtract(a.add(b).add(c).scale(0.5d));
			double minimumY = origin.y + Math.min(0.0d, a.y)
				+ Math.min(0.0d, b.y) + Math.min(0.0d, c.y);
			if (entity.slimeSize() == 0 || minimumY < 0.0d)
				origin = origin.add(0.0d, -minimumY, 0.0d);
			return new SlimeMimicCubeEntity.CubeFrame(origin, a, b, c);
		}
	}
}
