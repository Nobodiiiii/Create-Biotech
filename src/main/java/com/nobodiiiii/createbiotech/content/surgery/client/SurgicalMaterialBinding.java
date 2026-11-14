package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nobodiiiii.createbiotech.mixin.client.CompositeRenderStateAccessor;
import com.nobodiiiii.createbiotech.mixin.client.CompositeRenderTypeAccessor;
import com.nobodiiiii.createbiotech.mixin.client.TextureStateShardAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Internal SurgicalMaterialBinding implementation; public entry points remain on SurgicalCapturedRenderPlan. */
final class SurgicalMaterialBinding {
	private SurgicalMaterialBinding() {}


	static boolean isHeadEquipment(SurgicalCapturedRenderPlan.Component component) {
		return component.head || component.partNames.stream().anyMatch(name -> name.equals("bridle")
			|| name.equals("reins") || name.equals("head_saddle") || name.contains("saddle_mouth")
			|| name.contains("saddle_line") || name.equals("mouth_saddle_wrap"));
	}

	static float distanceToBoxSquared(Vector3f point, SurgicalCapturedRenderPlan.Component box) {
		Vector3f relative = new Vector3f(point).sub(box.corners.getFirst());
		Vector3f closest = new Vector3f(box.corners.getFirst());
		for (Vector3f edge : List.of(box.a, box.b, box.c)) {
			float lengthSquared = edge.lengthSquared();
			if (lengthSquared <= SurgicalCapturedRenderPlan.POSITION_EPSILON * SurgicalCapturedRenderPlan.POSITION_EPSILON)
				continue;
			float amount = Math.max(0.0f, Math.min(1.0f, relative.dot(edge) / lengthSquared));
			closest.fma(amount, edge);
		}
		return point.distanceSquared(closest);
	}

	@Nullable
	static SurgicalCapturedRenderPlan.ComponentBuilder surfaceOverlayOwner(List<SurgicalRenderCapture.CapturedVertex> vertices,
		List<SurgicalCapturedRenderPlan.ComponentBuilder> components) {
		SurgicalCapturedRenderPlan.ComponentBuilder best = null;
		float bestScore = Float.POSITIVE_INFINITY;
		for (SurgicalCapturedRenderPlan.ComponentBuilder component : components) {
			if (component.batches.isEmpty())
				continue;
			float score = surfaceOverlayScore(vertices, component.cuboid);
			if (score < bestScore) {
				best = component;
				bestScore = score;
			}
		}
		return best;
	}

	static float surfaceOverlayScore(List<SurgicalRenderCapture.CapturedVertex> vertices, SurgicalCapturedRenderPlan.RecoveredCuboid cuboid) {
		Vector3f origin = cuboid.corners().getFirst();
		Vector3f[] edges = { cuboid.a(), cuboid.b(), cuboid.c() };
		Vector3f[] axes = new Vector3f[3];
		float[] lengths = new float[3];
		for (int axis = 0; axis < 3; axis++) {
			lengths[axis] = edges[axis].length();
			if (lengths[axis] <= SurgicalCapturedRenderPlan.POSITION_EPSILON)
				return Float.POSITIVE_INFINITY;
			axes[axis] = new Vector3f(edges[axis]).div(lengths[axis]);
		}

		float score = 0.0f;
		for (int cursor = 0; cursor + 4 <= vertices.size(); cursor += 4) {
			List<SurgicalRenderCapture.CapturedVertex> quad = vertices.subList(cursor, cursor + 4);
			Vector3f normal = SurgicalMeshRecovery.quadNormal(quad);
			if (normal == null)
				return Float.POSITIVE_INFINITY;
			int planeAxis = -1;
			float bestParallel = 0.0f;
			for (int axis = 0; axis < 3; axis++) {
				float parallel = Math.abs(normal.dot(axes[axis]));
				if (parallel > bestParallel) {
					bestParallel = parallel;
					planeAxis = axis;
				}
			}
			if (bestParallel < SurgicalCapturedRenderPlan.SYNTHETIC_PARALLEL_DOT_MIN)
				return Float.POSITIVE_INFINITY;

			float[][] ranges = projectedRanges(quad, origin, axes);
			float planeMin = ranges[planeAxis][0];
			float planeMax = ranges[planeAxis][1];
			if (planeMax - planeMin > SurgicalCapturedRenderPlan.OVERLAY_CENTER_EPSILON)
				return Float.POSITIVE_INFINITY;
			float plane = (planeMin + planeMax) * 0.5f;
			float distance = Math.min(Math.abs(plane), Math.abs(plane - lengths[planeAxis]));
			if (distance > SurgicalCapturedRenderPlan.OVERLAY_CENTER_EPSILON)
				return Float.POSITIVE_INFINITY;
			for (int axis = 0; axis < 3; axis++) {
				if (axis == planeAxis)
					continue;
				if (ranges[axis][0] < -SurgicalCapturedRenderPlan.OVERLAY_EXPANSION_MAX
					|| ranges[axis][1] > lengths[axis] + SurgicalCapturedRenderPlan.OVERLAY_EXPANSION_MAX
					|| ranges[axis][1] < -SurgicalCapturedRenderPlan.POSITION_EPSILON
					|| ranges[axis][0] > lengths[axis] + SurgicalCapturedRenderPlan.POSITION_EPSILON)
					return Float.POSITIVE_INFINITY;
			}
			score += distance;
		}
		return score;
	}

	static float[][] projectedRanges(List<SurgicalRenderCapture.CapturedVertex> vertices, Vector3f origin,
		Vector3f[] axes) {
		float[][] ranges = {
			{ Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY },
			{ Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY },
			{ Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY }
		};
		for (SurgicalRenderCapture.CapturedVertex vertex : vertices) {
			Vector3f relative = SurgicalMeshRecovery.position(vertex).sub(origin);
			for (int axis = 0; axis < 3; axis++) {
				float projection = relative.dot(axes[axis]);
				ranges[axis][0] = Math.min(ranges[axis][0], projection);
				ranges[axis][1] = Math.max(ranges[axis][1], projection);
			}
		}
		return ranges;
	}

	/**
	 * Besides truly flat boxes, keep close-fitting shells in their source material.
	 * Vanilla clothing is usually authored as a normal cuboid inflated by 0.25-0.5
	 * pixels (the villager jacket is additionally extended downwards), so testing only
	 * the shortest edge would incorrectly turn it into a second solid slime body.
	 */
	static boolean shouldPreserveSource(SurgicalCapturedRenderPlan.ComponentBuilder candidate, List<SurgicalCapturedRenderPlan.ComponentBuilder> components) {
		if (candidate.preserveMaterial) return true;
		ResourceLocation texture = candidate.primaryRenderType == null
			? null : renderTypeTexture(candidate.primaryRenderType);
		if (texture != null && SurgicalCapturedRenderPlan.SADDLE_TEXTURES.contains(texture))
			return true;
		SurgicalCapturedRenderPlan.RecoveredCuboid cuboid = candidate.cuboid;
		float shortestEdge = Math.min(cuboid.a().length(), Math.min(cuboid.b().length(), cuboid.c().length()));
		if (shortestEdge <= SurgicalCapturedRenderPlan.THIN_EDGE)
			return true;

		for (SurgicalCapturedRenderPlan.ComponentBuilder base : components) {
			if (base.order >= candidate.order || base == candidate)
				continue;
			if (isCloseFittingOverlay(cuboid, base.cuboid))
				return true;
		}
		return false;
	}

	static boolean isCloseFittingOverlay(SurgicalCapturedRenderPlan.RecoveredCuboid candidate, SurgicalCapturedRenderPlan.RecoveredCuboid base) {
		Vector3f[] candidateEdges = { candidate.a(), candidate.b(), candidate.c() };
		Vector3f[] baseEdges = { base.a(), base.b(), base.c() };
		boolean[] usedBaseEdges = new boolean[3];
		Vector3f centerDelta = center(candidate).sub(center(base));
		float candidateVolume = 1.0f;
		float baseVolume = 1.0f;
		int closeExpandedAxes = 0;

		for (Vector3f candidateEdge : candidateEdges) {
			float candidateLength = candidateEdge.length();
			if (candidateLength <= SurgicalCapturedRenderPlan.POSITION_EPSILON)
				return false;
			Vector3f candidateAxis = new Vector3f(candidateEdge).div(candidateLength);
			int match = -1;
			for (int baseIndex = 0; baseIndex < baseEdges.length; baseIndex++) {
				if (usedBaseEdges[baseIndex])
					continue;
				float baseLength = baseEdges[baseIndex].length();
				if (baseLength <= SurgicalCapturedRenderPlan.POSITION_EPSILON)
					continue;
				float dot = Math.abs(candidateAxis.dot(new Vector3f(baseEdges[baseIndex]).div(baseLength)));
				if (dot >= SurgicalCapturedRenderPlan.PARALLEL_DOT_MIN) {
					match = baseIndex;
					break;
				}
			}
			if (match < 0)
				return false;

			usedBaseEdges[match] = true;
			float baseLength = baseEdges[match].length();
			float axialCenterDelta = Math.abs(centerDelta.dot(candidateAxis));
			if (axialCenterDelta > (candidateLength + baseLength) * 0.5f + SurgicalCapturedRenderPlan.POSITION_EPSILON)
				return false;

			float expansion = candidateLength - baseLength;
			if (expansion > SurgicalCapturedRenderPlan.POSITION_EPSILON && expansion <= SurgicalCapturedRenderPlan.OVERLAY_EXPANSION_MAX
				&& axialCenterDelta <= SurgicalCapturedRenderPlan.OVERLAY_CENTER_EPSILON)
				closeExpandedAxes++;
			candidateVolume *= candidateLength;
			baseVolume *= baseLength;
		}

		return closeExpandedAxes >= 2 && candidateVolume > baseVolume + SurgicalCapturedRenderPlan.POSITION_EPSILON;
	}

	static Vector3f center(SurgicalCapturedRenderPlan.RecoveredCuboid cuboid) {
		return new Vector3f(cuboid.corners().getFirst())
			.add(new Vector3f(cuboid.a()).mul(0.5f))
			.add(new Vector3f(cuboid.b()).mul(0.5f))
			.add(new Vector3f(cuboid.c()).mul(0.5f));
	}

	static boolean hasVisibleQuad(RenderType renderType, List<SurgicalRenderCapture.CapturedVertex> vertices) {
		for (int vertex = 0; vertex + 4 <= vertices.size(); vertex += 4)
			if (quadVisible(renderType, vertices.subList(vertex, vertex + 4)))
				return true;
		return false;
	}

	static boolean quadVisible(RenderType renderType, List<SurgicalRenderCapture.CapturedVertex> quad) {
		boolean vertexVisible = false;
		float minU = Float.POSITIVE_INFINITY;
		float minV = Float.POSITIVE_INFINITY;
		float maxU = Float.NEGATIVE_INFINITY;
		float maxV = Float.NEGATIVE_INFINITY;
		for (SurgicalRenderCapture.CapturedVertex vertex : quad) {
			vertexVisible |= vertex.alpha() > 0;
			minU = Math.min(minU, vertex.u());
			minV = Math.min(minV, vertex.v());
			maxU = Math.max(maxU, vertex.u());
			maxV = Math.max(maxV, vertex.v());
		}
		if (!vertexVisible)
			return false;
		ResourceLocation texture = renderTypeTexture(renderType);
		return texture == null || SurgicalTextureMasks.alphaMask(texture).hasVisiblePixels(minU, minV, maxU, maxV);
	}

	@Nullable
	static ResourceLocation renderTypeTexture(RenderType renderType) {
		if (!(renderType instanceof CompositeRenderTypeAccessor compositeAccessor))
			return null;
		RenderType.CompositeState state = compositeAccessor.createBiotech$getState();
		CompositeRenderStateAccessor stateAccessor = (CompositeRenderStateAccessor) (Object) state;
		Object textureState = stateAccessor.createBiotech$getTextureState();
		TextureStateShardAccessor textureAccessor = (TextureStateShardAccessor) textureState;
		return textureAccessor.createBiotech$getTexture().orElse(null);
	}

	/** MushroomCowMushroomLayer emits block-atlas quads rather than named ModelPart cubes. */
	static boolean isMushroomMaterial(RenderType renderType, List<SurgicalRenderCapture.CapturedVertex> vertices) {
		if (vertices.isEmpty() || !net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS.equals(renderTypeTexture(renderType)))
			return false;
		var atlas = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
		for (String name : List.of("red_mushroom", "brown_mushroom")) {
			var sprite = atlas.apply(ResourceLocation.withDefaultNamespace("block/" + name));
			if (vertices.stream().allMatch(vertex -> vertex.u() >= sprite.getU0() - 1.0e-6f
				&& vertex.u() <= sprite.getU1() + 1.0e-6f && vertex.v() >= sprite.getV0() - 1.0e-6f
				&& vertex.v() <= sprite.getV1() + 1.0e-6f)) return true;
		}
		return false;
	}

	static RenderType translucentPreviewType(RenderType renderType) {
		if (renderType.mode() != VertexFormat.Mode.QUADS)
			return renderType;
		ResourceLocation texture = renderTypeTexture(renderType);
		return texture == null ? renderType : RenderType.entityTranslucent(texture);
	}
}
